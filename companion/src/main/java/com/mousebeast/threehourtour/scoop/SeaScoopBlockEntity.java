package com.mousebeast.threehourtour.scoop;

import com.mousebeast.threehourtour.ThreeHourTour;
import com.mousebeast.threehourtour.vessel.VesselLookup;
import com.mousebeast.threehourtour.vessel.VesselMotion;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import java.util.List;

/**
 * Server-side driver for the sea scoop.
 *
 * Deliberately thin: every decision is made by a pure function (VesselMotion,
 * UnderWayRule, ScoopYield, MeshTier) so it can be unit-tested. This class only
 * reads the vessel, persists progress/underWay/the mesh slot, and moves items.
 *
 * COORDINATE SPACE: this block lives on a vessel, so its BlockPos is a PLOT
 * position. VesselLookup and SubLevelContainer expect exactly that. Do not call
 * vanilla Level.isLoaded or Level.setBlock with it - both answer wrongly and
 * silently (lessons-learned.md, 2026-08-21).
 *
 * meshHandler(), installedTier(), emitYield(), handlerAt() and outputFaces()
 * are package-private rather than private so SeaScoopGameTest can drive them
 * directly, and so ScoopRegistry.registerCapabilities() (same package) can
 * wrap meshHandler() as the exposed capability. GameTest cannot assemble a
 * moving Sable vessel, so underWay is always false in a GameTest world and
 * serverTick() never reaches these on its own - calling them straight is the
 * only way to exercise the output scan and the output-container contract
 * under a real server.
 */
public class SeaScoopBlockEntity extends BlockEntity {

    private int progress = 0;
    private boolean underWay = false;

    // Deliberately NOT persisted to NBT: a fresh instance re-warning after a
    // reload is fine, and arguably useful (a table fixed by a datapack
    // reload should be able to warn again if it later breaks). Latches the
    // empty-loot-roll WARN below to once per stretch of emptiness instead of
    // once per tick - without it a scoop under way with an emptied table
    // logs 20 times a second, indefinitely, because a failed emit pins
    // progress one tick below threshold (ScoopCommit), so tick() reports
    // emit=true on every subsequent tick.
    private boolean warnedEmptyLoot = false;

    /**
     * onContentsChanged is overridden here rather than in MeshSlot itself:
     * MeshSlot has no BlockEntity to call setChanged() on, and the plain
     * ItemStackHandler default (a no-op) means neither the hand-insert/
     * extract path in SeaScoopBlock nor a hopper loading through the
     * exposed capability view marked the chunk unsaved - both go through
     * this same handler instance, so one override closes both gaps.
     *
     * On a real vessel this loss window can't actually occur today: Sable
     * serialises a plot's whole sub-level on ServerLevel.save with no dirty
     * check, sidestepping vanilla's chunk-save skip for plot chunks
     * entirely. Fixing it anyway - a scoop's correctness should not depend
     * on a third-party mod's save strategy staying exactly as it is now.
     * setChanged() also drives the comparator update and is what any future
     * client sync will need.
     */
    private final MeshSlot mesh = new MeshSlot() {
        @Override
        protected void onContentsChanged(int slot) {
            super.onContentsChanged(slot);
            setChanged();
        }
    };

    public SeaScoopBlockEntity(BlockPos pos, BlockState state) {
        super(ScoopRegistry.SEA_SCOOP_BE.get(), pos, state);
    }

    /**
     * The scoop's own mesh slot.
     *
     * Package-private: the block's interaction code, the GameTest, and
     * ScoopRegistry.registerCapabilities() (which wraps it as the exposed,
     * insert-only capability view - Step 3) are all in this package.
     */
    MeshSlot meshHandler() {
        return mesh;
    }

    /**
     * The installed mesh's tier, or null if the slot is empty or holds
     * something that is not a mesh.
     *
     * One slot, one mesh - so unlike L1-P5's container-above design there is
     * no best-tier scan and no double-chest blind spot to document.
     *
     * Gated on MeshSlot.isMesh() (namespace + path), matching isItemValid()
     * exactly. insertItem() can never hand this a foreign item with a
     * colliding path - isItemValid() already rejects it - but
     * deserializeNBT() (a foreign or hand-edited save) and setStackInSlot()
     * (this class's own damageMesh()) both bypass that gate, so resolving on
     * path alone would let another mod's "twine_mesh" drive the scoop.
     */
    MeshTier installedTier() {
        ItemStack stack = mesh.getStackInSlot(0);
        if (stack.isEmpty()) {
            return null;
        }
        var id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id == null || !MeshSlot.isMesh(id.getNamespace(), id.getPath())) {
            return null;
        }
        return MeshTier.byItemPath(id.getPath()).orElse(null);
    }

    public boolean isUnderWay() {
        return underWay;
    }

    public void serverTick() {
        if (!(level instanceof ServerLevel server)) {
            return;
        }

        SubLevel sub = VesselLookup.subLevelAt(server, worldPosition);
        double speed = VesselMotion.speedOf(sub);
        boolean wasUnderWay = underWay;
        underWay = UnderWayRule.next(speed, underWay);

        // ScoopYield.tick ignores tier entirely while not under way, so don't
        // pay for the registry lookup during what is most of the pack's
        // playtime - a moored scoop with no mesh installed.
        MeshTier tier = underWay ? installedTier() : null;
        ScoopYield.Step step = ScoopYield.tick(progress, underWay, tier);

        boolean emitted = false;
        if (step.emit()) {
            // tier is guaranteed non-null here: ScoopYield.tick only emits when
            // tier != null.
            if (emitYield(server, tier)) {
                damageMesh(server);
                emitted = true;
            }
            // else: the destination couldn't take the whole roll. ScoopCommit
            // (below) leaves progress untouched so this retries next tick
            // instead of losing the yield - the self-limiting "back up"
            // behaviour spec §8 asks for.
        }
        // The commit rule - spend progress only when a deposit actually
        // happened - is pulled into ScoopCommit and unit-tested there
        // (ScoopCommitTest) rather than inlined here, because it is the
        // single most important invariant in this class and a GameTest
        // structurally cannot reach it: underWay never goes true in a
        // GameTest world, so this branch never runs under test.
        progress = ScoopCommit.progressAfter(progress, step, emitted);

        // setChanged() walks neighbours for the redstone comparator update, so
        // only pay for it when something observable actually changed this tick.
        // Trade-off: a tick that only advances progress no longer marks the
        // chunk unsaved, so an unclean reload can roll banked progress back to
        // the last emit - bounded at one yield interval, at most 200 ticks
        // with twine, against a redstone poke 20 times a second forever.
        if (underWay != wasUnderWay || emitted) {
            setChanged();
        }
    }

    /**
     * Roll the tier's loot table and push it into the destination beneath the
     * scoop's five non-front neighbours, in OutputScan order. Returns whether
     * the roll was fully deposited into one of them.
     *
     * A false result means nothing was changed - the caller must not consume
     * progress or mesh durability for a failed attempt, since a partial
     * deposit followed by a retry would duplicate items. A discarded roll
     * costs nothing observable.
     *
     * A cheap pre-roll gate still applies before anything is rolled: walk
     * every candidate face, and if not one of them has ANY room at all - not
     * "empty", but genuinely no capacity for one more item of what it already
     * holds - the roll is skipped entirely. This is deliberate: with progress
     * preserved one tick below threshold on every failed emit (ScoopCommit),
     * a blocked destination is emit-eligible on EVERY subsequent tick, so
     * without a pre-roll gate a scoop boxed in on all five faces would roll
     * the loot table 20 times a second, indefinitely, allocating a list and
     * advancing the level's loot random sequence each time - a cost an
     * earlier review round already removed once (the Task 6 round).
     * hasAnyRoom() is what makes that gate correct for the chute-under-scoop
     * idiom this task exists to fix: a slot holding one kelp with room for 63
     * more passes it, where the older hasEmptySlot() predicate wrongly failed
     * it.
     *
     * The roll itself happens exactly once, regardless of how many faces are
     * live - rolling once per candidate would advance the level's loot random
     * sequence once per face per tick, which is observable (and wasteful)
     * even for players who never notice a scoop is faster or slower than
     * intended.
     *
     * Past the gate and the roll, each candidate face is tried in turn until
     * one takes the whole roll. For a given face, the empty-slot checks below
     * decide the real insert for a MULTI-stack roll, cheapest first:
     * 1. At least one empty slot exists at all.
     * 2. Empty slot COUNT >= rolled stack count - getRandomItems already
     *    splits each rolled stack to at most its own max stack size, so this
     *    proves everything fits by count.
     * A single-stack roll skips both: a destination with zero literally-empty
     * slots can still accept one more stack by merging into a matching
     * partial stack, which is exactly what the pre-roll gate above already
     * let through. The empty-slot count only exists to make a MULTI-stack
     * batch safe against the simulation pass below not modelling interaction
     * between stacks in the same batch; for one stack there is nothing to
     * interact with, so the simulation is sufficient on its own.
     *
     * Either way, the simulated insert of every rolled stack is what actually
     * proves ACCEPTANCE, not just room. A handler can have empty slots that
     * still reject a stack (isItemValid - a filtered Create funnel, a
     * machine's typed input) or cap it below its max stack size
     * (getSlotLimit). A face that fails either check is skipped in favour of
     * the next one; the deposit itself is all-or-nothing per face, never
     * split across two neighbours.
     *
     * A second, known-limitation case has the same shape as the filtered-
     * funnel one above: an adjacent scoop's own empty MeshSlot passes the
     * pre-roll gate above (hasAnyRoom sees room) but then rejects every
     * candidate item in the simulate pass below (isItemValid only accepts
     * meshes), so two scoops placed beside each other reintroduce the
     * per-tick roll the pre-roll gate exists to avoid. No item loss either
     * way - accepted for the same reason the filtered-funnel case is.
     */
    boolean emitYield(ServerLevel server, MeshTier tier) {
        List<Direction> faces = outputFaces();

        // Resolved once per face here and reused by the deposit loop below -
        // a blocked scoop would otherwise pay two Level.getCapability calls
        // per face per tick (up to ten on an emit tick) for the same answer.
        // Safe to reuse within one call: everything here runs on the server
        // thread inside a single tick, so nothing about the world can change
        // between the gate and the deposit.
        IItemHandler[] handlers = new IItemHandler[faces.size()];
        boolean anyRoom = false;
        for (int i = 0; i < faces.size(); i++) {
            IItemHandler candidate = handlerAt(server, faces.get(i));
            handlers[i] = candidate;
            if (candidate != null && hasAnyRoom(candidate)) {
                anyRoom = true;
            }
        }
        if (!anyRoom) {
            return false; // No candidate has room for anything - don't even roll the loot table. Retry next tick.
        }

        ResourceKey<LootTable> key =
                ResourceKey.create(net.minecraft.core.registries.Registries.LOOT_TABLE, tier.lootTable());
        LootTable table = server.getServer().reloadableRegistries().getLootTable(key);
        LootParams params = new LootParams.Builder(server)
                .create(LootContextParamSets.EMPTY);
        List<ItemStack> rolled = table.getRandomItems(params);
        if (rolled.isEmpty()) {
            // A missing/unparseable table resolves to LootTable.EMPTY, and any
            // datapack retune that conditions out every entry does the same.
            // Without this check the empty-slot count below compares against
            // zero, both insert loops iterate zero times, and this method
            // returns true - so the caller damages the mesh and commits
            // progress for a deposit that never happened. Stall instead
            // (spec §8): retry next tick, spend nothing.
            //
            // warnedEmptyLoot latches this to once: a failed emit leaves
            // progress pinned one tick below threshold (ScoopCommit), so
            // tick() reports emit=true again on the very next tick and every
            // one after it - without the latch this would log 20 times a
            // second for as long as the table stays empty.
            if (!warnedEmptyLoot) {
                warnedEmptyLoot = true;
                ThreeHourTour.LOG.warn(
                        "Sea scoop at {} rolled an empty {} loot table - stalling instead of consuming mesh "
                                + "durability for nothing",
                        worldPosition, tier);
            }
            return false;
        }
        // A non-empty roll means the table is (again) producing items -
        // clear the latch so a table that breaks again later can re-warn.
        warnedEmptyLoot = false;

        // Try each face in scan order; the first one that can take the whole
        // roll gets it. A face that fails is left completely untouched -
        // depositAt() only ever mutates the handler it's given once it has
        // already proven the whole roll fits. Same handlers array as the
        // gate above - one resolution per face per emit, not two.
        for (int i = 0; i < faces.size(); i++) {
            IItemHandler candidate = handlers[i];
            if (candidate == null) {
                continue;
            }
            if (depositAt(candidate, rolled)) {
                return true;
            }
        }
        return false; // No face could take the whole roll - discard it, retry next tick.
    }

    /**
     * Deposits the whole rolled batch into a single candidate handler, or
     * changes nothing and returns false. See emitYield's javadoc for why the
     * empty-slot count only matters for a multi-stack roll.
     */
    private boolean depositAt(IItemHandler handler, List<ItemStack> rolled) {
        if (rolled.size() > 1) {
            if (!hasEmptySlot(handler)) {
                return false; // Fully blocked - try the next face.
            }
            int empty = 0;
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                if (handler.getStackInSlot(slot).isEmpty()) {
                    empty++;
                }
            }
            if (empty < rolled.size()) {
                return false; // Not enough room for the whole roll - try the next face.
            }
        }
        // else: a single rolled stack relies on the simulation pass below
        // alone, which is what actually proves it fits (including by merge).

        for (ItemStack stack : rolled) {
            if (!ItemHandlerHelper.insertItem(handler, stack, true).isEmpty()) {
                return false; // A slot rejects or caps this stack - try the next face.
            }
        }

        List<ItemStack> lost = null;
        for (ItemStack stack : rolled) {
            ItemStack leftover = ItemHandlerHelper.insertItem(handler, stack, false);
            if (!leftover.isEmpty()) {
                // The handler accepted this stack in simulation and then
                // refused it for real - a violation of its own simulate
                // contract. The deposit has partly happened by this point, so
                // returning false here would duplicate the batch on retry;
                // report success and log instead of letting the item vanish
                // without a trace.
                if (lost == null) {
                    lost = new java.util.ArrayList<>();
                }
                lost.add(leftover);
            }
        }
        if (lost != null) {
            ThreeHourTour.LOG.warn(
                    "Sea scoop at {} lost {} depositing a yield: item handler {} accepted a simulated insert "
                            + "then rejected the real one",
                    worldPosition, lost, handler.getClass().getName());
        }
        return true;
    }

    private static boolean hasEmptySlot(IItemHandler handler) {
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            if (handler.getStackInSlot(slot).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Cheap pre-roll gate: does ANY slot have room for one more item, empty
     * or not? A slot qualifies if it's empty, or if its current stack count
     * is below the lesser of the slot's own limit and that stack's own max
     * stack size - i.e. a Create chute or funnel holding one kelp qualifies,
     * because that slot has 63 more room. This is deliberately looser than
     * hasEmptySlot(): the predicate, not the existence of a pre-roll gate,
     * was the bug (see emitYield's javadoc) - a genuinely full destination
     * must still fail this and skip the roll, or a fully-blocked chest would
     * roll the loot table every tick forever.
     *
     * What this does NOT guarantee: acceptance. It can only answer "is there
     * room for more of what is already there", never "will the rolled item
     * be accepted" - it has no idea what the roll will contain. A filtered
     * Create funnel with an empty slot passes this gate every time and is
     * then rejected by the simulate pass below, so a filtered destination
     * still costs a roll per tick; this gate only ever short-circuits a
     * FULL destination, not a filtered one. Answering the filtered case
     * would mean rolling first, which is the exact cost this gate exists to
     * avoid - so the code is right and cannot do better without giving up
     * the thing it's for.
     */
    private static boolean hasAnyRoom(IItemHandler handler) {
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (stack.isEmpty()) {
                return true;
            }
            int cap = Math.min(handler.getSlotLimit(slot), stack.getMaxStackSize());
            if (stack.getCount() < cap) {
                return true;
            }
        }
        return false;
    }

    /**
     * The handler on the neighbour in this direction, or null.
     *
     * Queries the neighbour's position, passing the face of the neighbour
     * that touches us - the opposite of the direction we travelled. That
     * generalises the old below()+UP call exactly, and matches what vanilla's
     * own SidedInvWrapper bridge and Create's Mechanical Arm both do.
     */
    IItemHandler handlerAt(ServerLevel server, Direction dir) {
        return server.getCapability(Capabilities.ItemHandler.BLOCK,
                worldPosition.relative(dir), dir.getOpposite());
    }

    /**
     * The faces this scoop will try, front excluded.
     *
     * Package-private: SeaScoopGameTest asserts on this list directly (the
     * front-exclusion invariant), and emitYield() walks it both for the
     * pre-roll gate and the deposit itself.
     */
    List<Direction> outputFaces() {
        BlockState state = getBlockState();
        return OutputScan.targets(
                state.hasProperty(SeaScoopBlock.FACING) ? state.getValue(SeaScoopBlock.FACING) : null);
    }

    /**
     * Consume 1 durability from the installed mesh. Called on a successful
     * emit only (L1-25 / case 10): when the mesh breaks its stack empties, so
     * the next tick's installedTier() returns null and the scoop stops on
     * its own - no extra stop logic needed.
     *
     * hurtAndBreak mutates the ItemStack it's called on in place. setStackInSlot
     * is the write-back L1-P5's review added for containers that hand out
     * copies; ItemStackHandler hands out the live stack, but keeping the
     * write explicit costs nothing and survives the field's type changing.
     */
    private void damageMesh(ServerLevel server) {
        ItemStack stack = mesh.getStackInSlot(0);
        if (stack.isEmpty()) {
            return;
        }
        stack.hurtAndBreak(1, server, (ServerPlayer) null, brokenItem -> {});
        mesh.setStackInSlot(0, stack);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Progress", progress);
        // Diagnostic only - nothing depends on this surviving a reload. A
        // freshly loaded SubLevel reports a zero pose delta on its first
        // tick, so a restored `true` is immediately overwritten by
        // UnderWayRule.next() and has to re-cross the START threshold from
        // scratch anyway. Kept because it's already a save-format
        // commitment, not because a stored `true` does anything useful.
        tag.putBoolean("UnderWay", underWay);
        tag.put("Mesh", mesh.serializeNBT(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        progress = tag.getInt("Progress");
        underWay = tag.getBoolean("UnderWay");
        if (tag.contains("Mesh")) {
            mesh.deserializeNBT(registries, tag.getCompound("Mesh"));
        }
    }
}
