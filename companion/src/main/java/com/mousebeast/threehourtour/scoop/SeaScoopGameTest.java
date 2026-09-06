package com.mousebeast.threehourtour.scoop;

import com.mousebeast.threehourtour.ThreeHourTour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.List;
import java.util.Optional;

/**
 * In-world coverage for the sea scoop.
 *
 * These assert the things unit tests cannot: that the block and every mesh
 * tier register, that the block gets a block entity, that the output-container
 * contract holds (a chest below is found and wired, and a missing container
 * degrades safely instead of destroying yields), and that the mesh slot
 * accepts a mesh, rejects everything else, and is insert-only to automation.
 *
 * The movement side is deliberately NOT covered here. GameTest cannot
 * assemble and sail a Sable vessel, so a scoop in a GameTest structure is
 * stationary by definition and must read as not under way - that case is
 * covered instead by the in-game cases in Task 11 on a live server. Because
 * underWay never goes true here, serverTick() never reaches emitYield() or
 * handlerAt() on its own; several tests below call those package-private
 * methods directly (see SeaScoopBlockEntity's class javadoc) to exercise the
 * real registry and capability lookups a unit test cannot reach.
 */
@GameTestHolder(ThreeHourTour.MOD_ID)
@PrefixGameTestTemplate(false)
public class SeaScoopGameTest {

    @GameTest(template = "empty")
    public static void scoopBlockResolvesToItsRegisteredId(GameTestHelper helper) {
        if (BuiltInRegistries.BLOCK.getOptional(ScoopRegistry.SEA_SCOOP_ID).isEmpty()) {
            helper.fail("sea_scoop is not in the block registry at " + ScoopRegistry.SEA_SCOOP_ID);
            return;
        }
        if (BuiltInRegistries.BLOCK.get(ScoopRegistry.SEA_SCOOP_ID) != ScoopRegistry.SEA_SCOOP.get()) {
            helper.fail("registry entry for " + ScoopRegistry.SEA_SCOOP_ID
                    + " does not match ScoopRegistry.SEA_SCOOP");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void scoopPlacesAndGetsABlockEntity(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, ScoopRegistry.SEA_SCOOP.get());
        BlockEntity be = helper.getBlockEntity(pos);
        if (!(be instanceof SeaScoopBlockEntity)) {
            helper.fail("sea scoop did not create a SeaScoopBlockEntity at " + pos);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void stationaryScoopIsNotUnderWay(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, ScoopRegistry.SEA_SCOOP.get());
        helper.runAfterDelay(40, () -> {
            BlockEntity be = helper.getBlockEntity(pos);
            // Inverted guard, not `be instanceof SeaScoopBlockEntity scoop &&
            // scoop.isUnderWay()`: that form is false whenever the block
            // entity is missing or the wrong type, which would let a scoop
            // that vanished during the 40 ticks read as a pass instead of a
            // failure that can't tell "not under way" from "not there".
            if (!(be instanceof SeaScoopBlockEntity scoop)) {
                helper.fail("sea scoop block entity is missing at " + pos + " after ticking, found " + be);
                return;
            }
            if (scoop.isUnderWay()) {
                helper.fail("a scoop on dry land reported itself under way");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void everyMeshTierResolvesToARegisteredItem(GameTestHelper helper) {
        for (MeshTier tier : MeshTier.values()) {
            // meshItem(tier) == null is a real EnumMap miss. The second half
            // is deliberately asOptional().isEmpty(), not .get() == null:
            // DeferredHolder.get() throws NullPointerException for an
            // unbound id rather than returning null, so an unregistered mesh
            // must be caught before .get() is ever called - otherwise it
            // fails as an uncaught exception instead of this message.
            if (ScoopRegistry.meshItem(tier) == null || ScoopRegistry.meshItem(tier).asOptional().isEmpty()) {
                helper.fail("mesh tier " + tier + " has no registered item at runtime");
            }
        }
        helper.succeed();
    }

    /**
     * The Task 6 review's worst bug: a chest below the scoop must be found
     * and wired through exactly the path SeaScoopBlockEntity itself uses
     * (handlerAt), not through a re-implementation of the capability lookup
     * that could pass while the real one is broken.
     */
    @GameTest(template = "empty")
    public static void outputContainerIsWiredTheWayTheBlockEntityExpects(GameTestHelper helper) {
        BlockPos scoopPos = new BlockPos(1, 1, 1);
        BlockPos chestPos = new BlockPos(1, 0, 1);

        helper.setBlock(chestPos, Blocks.CHEST);
        helper.setBlock(scoopPos, ScoopRegistry.SEA_SCOOP.get());

        BlockEntity scoopBe = helper.getBlockEntity(scoopPos);
        if (!(scoopBe instanceof SeaScoopBlockEntity scoop)) {
            helper.fail("sea scoop did not create a SeaScoopBlockEntity at " + scoopPos);
            return;
        }
        if (!(helper.getBlockEntity(chestPos) instanceof Container)) {
            helper.fail("chest below the scoop did not produce a Container block entity at " + chestPos);
            return;
        }

        ServerLevel server = helper.getLevel();
        if (scoop.handlerAt(server, Direction.DOWN) == null) {
            helper.fail("scoop.handlerAt(DOWN) found nothing above the chest at " + chestPos
                    + " - the output-container contract is broken");
        }
        helper.succeed();
    }

    /**
     * No container below and no mesh above: the scoop must tick harmlessly
     * for real, not just refrain from crashing on one call. The pre-review
     * code silently destroyed yields in exactly this situation instead of
     * backing up (spec §8).
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void scoopWithNoContainerAndNoMeshNeverThrowsOrEmits(GameTestHelper helper) {
        BlockPos scoopPos = new BlockPos(1, 1, 1);
        helper.setBlock(scoopPos, ScoopRegistry.SEA_SCOOP.get());

        BlockEntity be = helper.getBlockEntity(scoopPos);
        if (!(be instanceof SeaScoopBlockEntity scoop)) {
            helper.fail("sea scoop did not create a SeaScoopBlockEntity at " + scoopPos);
            return;
        }

        ServerLevel server = helper.getLevel();
        for (Direction dir : scoop.outputFaces()) {
            if (scoop.handlerAt(server, dir) != null) {
                helper.fail("expected no handler on face " + dir + " with nothing placed around " + scoopPos);
                return;
            }
        }
        // Drive the exact bug scenario directly: emitYield() must decline
        // cleanly (false, no throw) with no destination, for every tier -
        // not silently consume the roll.
        for (MeshTier tier : MeshTier.values()) {
            if (scoop.emitYield(server, tier)) {
                helper.fail("emitYield() reported success with no output container for tier " + tier);
                return;
            }
        }

        // And the ordinary tick path - natural ticking on a stationary,
        // meshless, container-less scoop - must survive a couple of real
        // seconds without throwing.
        helper.runAfterDelay(40, () -> {
            if (!(helper.getBlockEntity(scoopPos) instanceof SeaScoopBlockEntity stillScoop)) {
                helper.fail("scoop block entity disappeared after ticking");
                return;
            }
            for (Direction dir : stillScoop.outputFaces()) {
                if (stillScoop.handlerAt(server, dir) != null) {
                    helper.fail("a handler appeared on face " + dir + " with nothing placed around " + scoopPos);
                    return;
                }
            }
            helper.succeed();
        });
    }


    /**
     * Permanent in-world coverage for the block-loot-table Critical (final
     * whole-branch review, item 1): breaking a placed scoop must return
     * exactly the crafted item, not destroy it.
     *
     * ScoopDataFilesTest can only assert the loot table file exists on disk
     * - it cannot prove what breaking the block actually produces. During
     * this review a typo'd item id inside the table (threehourtour:sea_scoopp)
     * left ScoopDataFilesTest green while the block dropped nothing, the
     * exact failure the fix was meant to close. This test drives the real
     * break and checks the real drop.
     *
     * GameTestHelper.destroyBlock() does NOT drop items - it calls
     * Level.destroyBlock(pos, false, null), always with dropBlock=false.
     * Calling Level.destroyBlock(pos, true) directly is what makes it drop;
     * verified against a vanilla oak-planks control before trusting it here.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void breakingAPlacedScoopDropsExactlyOneScoopItem(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, ScoopRegistry.SEA_SCOOP.get());

        ServerLevel server = helper.getLevel();
        server.destroyBlock(helper.absolutePos(pos), true);

        List<ItemEntity> drops = helper.getEntities(EntityType.ITEM);
        if (drops.size() != 1) {
            helper.fail("expected exactly one item entity to drop breaking the sea scoop, found "
                    + drops.size() + ": " + drops);
            return;
        }
        if (!drops.get(0).getItem().is(ScoopRegistry.SEA_SCOOP_ITEM.get())) {
            helper.fail("dropped item was " + drops.get(0).getItem() + ", expected the sea scoop item");
            return;
        }
        helper.succeed();
    }

    /**
     * Replaces the Task 3 test placedScoopCarriesAHorizontalFacing, which
     * review found tautological: once FACING is registered, every BlockState
     * has it by construction, and HORIZONTAL_FACING cannot hold a vertical
     * value by type, so neither of its assertions could ever fail - the real
     * guard was mod loading crashing outright if the property went
     * unregistered. helper.setBlock() also bypasses placement entirely by
     * using the default state, so nothing exercised getStateForPlacement().
     *
     * This drives the real placement path instead: a genuine BlockPlaceContext
     * built from a mock player with a controlled yaw, fed straight into
     * SeaScoopBlock.getStateForPlacement(). The expected facing (opposite the
     * player's horizontal direction) only holds if that override is doing its
     * job - flip getOpposite() to identity, or break getHorizontalDirection(),
     * and this fails.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void placedScoopFacesOppositeThePlacingPlayer(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setYRot(180.0F);
        if (player.getDirection() != Direction.NORTH) {
            helper.fail("test setup: mock player should face north after setYRot(180), faced "
                    + player.getDirection());
            return;
        }

        BlockHitResult hit = new BlockHitResult(
                Vec3.atCenterOf(helper.absolutePos(pos)), Direction.UP,
                helper.absolutePos(pos), false);
        BlockPlaceContext ctx = new BlockPlaceContext(player, InteractionHand.MAIN_HAND,
                new ItemStack(ScoopRegistry.SEA_SCOOP_ITEM.get()), hit);

        BlockState placed = ScoopRegistry.SEA_SCOOP.get().getStateForPlacement(ctx);
        if (placed == null) {
            helper.fail("getStateForPlacement returned null for a north-facing placer");
            return;
        }
        Direction expected = player.getDirection().getOpposite();
        Direction facing = placed.getValue(SeaScoopBlock.FACING);
        if (facing != expected) {
            helper.fail("a player facing " + player.getDirection() + " should place a scoop facing "
                    + expected + ", got " + facing);
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void rotatingTheStateMovesTheFacing(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, ScoopRegistry.SEA_SCOOP.get()
                .defaultBlockState().setValue(SeaScoopBlock.FACING, Direction.NORTH));
        BlockState rotated = helper.getBlockState(pos).rotate(Rotation.CLOCKWISE_90);
        if (rotated.getValue(SeaScoopBlock.FACING) != Direction.EAST) {
            helper.fail("rotating north 90 degrees clockwise should give east, gave "
                    + rotated.getValue(SeaScoopBlock.FACING));
            return;
        }
        helper.succeed();
    }

    /**
     * Copes with an environment that may contain no wrench at all:
     * runGameTestServer loads this mod and Sable, not Create. The negative
     * case (a non-wrench must not turn the block) and the tag id are always
     * asserted; the positive rotation is asserted only if the runtime's
     * c:tools/wrench tag actually resolves to an item.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void onlyAWrenchTurnsTheScoop(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        BlockState north = ScoopRegistry.SEA_SCOOP.get()
                .defaultBlockState().setValue(SeaScoopBlock.FACING, Direction.NORTH);

        // The tag must resolve to the exact id Create populates. A typo here
        // would silently mean "no item is ever a wrench" and the block would
        // simply never turn, with nothing failing.
        if (!SeaScoopBlock.WRENCH.location().toString().equals("c:tools/wrench")) {
            helper.fail("wrench tag must be c:tools/wrench, was " + SeaScoopBlock.WRENCH.location());
            return;
        }

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockHitResult hit = new BlockHitResult(
                Vec3.atCenterOf(helper.absolutePos(pos)), Direction.UP,
                helper.absolutePos(pos), false);

        // A non-wrench must not turn it.
        helper.setBlock(pos, north);
        helper.getBlockState(pos).useItemOn(new ItemStack(Items.STICK),
                helper.getLevel(), player, InteractionHand.MAIN_HAND, hit);
        if (helper.getBlockState(pos).getValue(SeaScoopBlock.FACING) != Direction.NORTH) {
            helper.fail("a stick turned the scoop; only items in " + SeaScoopBlock.WRENCH.location()
                    + " may turn it");
            return;
        }

        // If this environment has any wrench, prove the positive case too.
        Optional<Item> wrench = BuiltInRegistries.ITEM.getTag(SeaScoopBlock.WRENCH)
                .flatMap(tag -> tag.stream().findFirst())
                .map(Holder::value);
        if (wrench.isPresent()) {
            helper.setBlock(pos, north);
            helper.getBlockState(pos).useItemOn(new ItemStack(wrench.get()),
                    helper.getLevel(), player, InteractionHand.MAIN_HAND, hit);
            Direction after = helper.getBlockState(pos).getValue(SeaScoopBlock.FACING);
            if (after != Direction.EAST) {
                helper.fail("a wrench (" + wrench.get() + ") should turn north to east, gave " + after);
                return;
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void theMeshSlotAcceptsAMeshAndRejectsOtherItems(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, ScoopRegistry.SEA_SCOOP.get());
        if (!(helper.getBlockEntity(pos) instanceof SeaScoopBlockEntity scoop)) {
            helper.fail("no SeaScoopBlockEntity at " + pos);
            return;
        }
        ItemStack twine = new ItemStack(ScoopRegistry.meshItem(MeshTier.TWINE).get());
        if (!scoop.meshHandler().insertItem(0, twine.copy(), false).isEmpty()) {
            helper.fail("the mesh slot refused a twine mesh");
            return;
        }
        if (scoop.installedTier() != MeshTier.TWINE) {
            helper.fail("expected TWINE installed, got " + scoop.installedTier());
            return;
        }
        ItemStack stick = new ItemStack(Items.STICK);
        if (scoop.meshHandler().insertItem(0, stick.copy(), false).isEmpty()) {
            helper.fail("the mesh slot accepted a stick");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void automationCannotPullTheMeshBackOut(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, ScoopRegistry.SEA_SCOOP.get());
        if (!(helper.getBlockEntity(pos) instanceof SeaScoopBlockEntity scoop)) {
            helper.fail("no SeaScoopBlockEntity at " + pos);
            return;
        }

        IItemHandler exposed = helper.getLevel().getCapability(
                Capabilities.ItemHandler.BLOCK, helper.absolutePos(pos), Direction.UP);
        if (exposed == null) {
            helper.fail("the scoop exposes no item handler capability");
            return;
        }
        // Load the mesh through the exposed wrapper itself, not through
        // meshHandler() directly - a hopper or Create chute aimed at the
        // scoop only ever sees this capability, so if the wrapper's insert
        // path were broken the scoop would be uninstallable with nothing
        // failing anywhere else.
        ItemStack remainder = exposed.insertItem(0,
                new ItemStack(ScoopRegistry.meshItem(MeshTier.TWINE).get()), false);
        if (!remainder.isEmpty()) {
            helper.fail("automation could not load a mesh through the exposed handler");
            return;
        }
        if (scoop.installedTier() != MeshTier.TWINE) {
            helper.fail("expected TWINE installed after loading through the exposed handler, got "
                    + scoop.installedTier());
            return;
        }
        // A hopper beneath the scoop must not be able to steal the mesh the
        // scoop is running on, in simulate or real mode.
        if (!exposed.extractItem(0, 1, true).isEmpty()) {
            helper.fail("automation simulated a successful extraction; the exposed view must be insert-only");
            return;
        }
        if (!exposed.extractItem(0, 1, false).isEmpty()) {
            helper.fail("automation extracted the mesh; the exposed view must be insert-only");
            return;
        }
        if (scoop.installedTier() != MeshTier.TWINE) {
            helper.fail("the mesh went missing after a failed extraction");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void theFrontFaceIsNotAnOutputCandidate(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, ScoopRegistry.SEA_SCOOP.get()
                .defaultBlockState().setValue(SeaScoopBlock.FACING, Direction.NORTH));
        BlockPos front = pos.north();
        helper.setBlock(front, Blocks.CHEST);

        if (!(helper.getBlockEntity(pos) instanceof SeaScoopBlockEntity scoop)) {
            helper.fail("no SeaScoopBlockEntity at " + pos);
            return;
        }
        // Not a behavioural assertion about emitYield - underWay is never true
        // in a GameTest world - but a direct assertion that the face the scoop
        // points at is absent from the candidate list it would use.
        if (scoop.outputFaces().contains(Direction.NORTH)) {
            helper.fail("the front face is a candidate output target");
            return;
        }
        if (scoop.outputFaces().size() != 5) {
            helper.fail("expected 5 candidate faces, got " + scoop.outputFaces().size());
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void breakingAScoopDropsItsInstalledMeshExactlyOnce(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, ScoopRegistry.SEA_SCOOP.get());
        if (!(helper.getBlockEntity(pos) instanceof SeaScoopBlockEntity scoop)) {
            helper.fail("no SeaScoopBlockEntity at " + pos);
            return;
        }
        scoop.meshHandler().insertItem(0,
                new ItemStack(ScoopRegistry.meshItem(MeshTier.CHAIN).get()), false);

        // Level.destroyBlock(pos, true) - GameTestHelper.destroyBlock() passes
        // dropBlock=false and would prove nothing here.
        helper.getLevel().destroyBlock(helper.absolutePos(pos), true);

        // Wide enough that a mesh which flew further than expected still gets
        // counted rather than silently missed as "zero found" - a physics
        // fling that clears a tight 3-block box would otherwise read as a
        // failure of the wrong kind (mesh count 0) instead of what it is
        // (mesh count 1, just further out).
        List<ItemEntity> allDrops = helper.getLevel()
                .getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(pos)).inflate(8.0));
        long meshes = allDrops.stream()
                .filter(e -> e.getItem().is(ScoopRegistry.meshItem(MeshTier.CHAIN).get()))
                .count();
        if (meshes != 1) {
            helper.fail("expected exactly one chain mesh to drop, found " + meshes + " among " + allDrops);
            return;
        }
        // Exactly two item entities total: the scoop block itself and the
        // mesh. Anything else means something is drop-duplicating.
        if (allDrops.size() != 2) {
            helper.fail("expected exactly 2 item entities total (scoop + mesh), found "
                    + allDrops.size() + ": " + allDrops);
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void turningTheScoopDoesNotDropTheMesh(GameTestHelper helper) {
        // The duplication trap: onRemove fires on every state change, so a
        // wrench turn without the block-identity guard drops the mesh while
        // leaving it installed.
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, ScoopRegistry.SEA_SCOOP.get()
                .defaultBlockState().setValue(SeaScoopBlock.FACING, Direction.NORTH));
        if (!(helper.getBlockEntity(pos) instanceof SeaScoopBlockEntity scoop)) {
            helper.fail("no SeaScoopBlockEntity at " + pos);
            return;
        }
        scoop.meshHandler().insertItem(0,
                new ItemStack(ScoopRegistry.meshItem(MeshTier.TWINE).get()), false);

        helper.setBlock(pos, helper.getBlockState(pos).setValue(SeaScoopBlock.FACING, Direction.EAST));

        long dropped = helper.getLevel()
                .getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(pos)).inflate(3.0))
                .size();
        if (dropped != 0) {
            helper.fail("turning the scoop dropped " + dropped + " item(s); the mesh must stay installed");
            return;
        }
        if (!(helper.getBlockEntity(pos) instanceof SeaScoopBlockEntity turned)
                || turned.installedTier() != MeshTier.TWINE) {
            helper.fail("the mesh did not survive the turn");
            return;
        }
        helper.succeed();
    }

    /**
     * Review round 1, probe 1: a part-worn mesh must drop part-worn, not
     * reset to full durability. hurtAndBreak mutates the live ItemStack in
     * place (SeaScoopBlockEntity.damageMesh's javadoc), so the dropped item
     * should carry whatever damage was already on the installed stack.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void breakingAScoopDropsAPartWornMeshWithItsDamageIntact(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, ScoopRegistry.SEA_SCOOP.get());
        if (!(helper.getBlockEntity(pos) instanceof SeaScoopBlockEntity scoop)) {
            helper.fail("no SeaScoopBlockEntity at " + pos);
            return;
        }
        ItemStack worn = new ItemStack(ScoopRegistry.meshItem(MeshTier.CHAIN).get());
        worn.setDamageValue(500);
        scoop.meshHandler().insertItem(0, worn, false);

        helper.getLevel().destroyBlock(helper.absolutePos(pos), true);

        List<ItemEntity> chainDrops = helper.getLevel()
                .getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(pos)).inflate(8.0))
                .stream()
                .filter(e -> e.getItem().is(ScoopRegistry.meshItem(MeshTier.CHAIN).get()))
                .toList();
        if (chainDrops.size() != 1) {
            helper.fail("expected exactly one dropped chain mesh, found " + chainDrops.size());
            return;
        }
        int damage = chainDrops.get(0).getItem().getDamageValue();
        if (damage != 500) {
            helper.fail("expected the dropped mesh to carry damage 500, got " + damage);
            return;
        }
        helper.succeed();
    }

    /**
     * Review round 1, probe 2: a survival hand-install shrinks the held
     * stack by exactly one and fills the slot. Drives the real useItemOn
     * path (not meshHandler() directly) so held.consume(1, player) is
     * actually exercised.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void survivalHandInsertShrinksHeldStackByExactlyOneAndFillsTheSlot(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, ScoopRegistry.SEA_SCOOP.get());
        if (!(helper.getBlockEntity(pos) instanceof SeaScoopBlockEntity scoop)) {
            helper.fail("no SeaScoopBlockEntity at " + pos);
            return;
        }

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockHitResult hit = new BlockHitResult(
                Vec3.atCenterOf(helper.absolutePos(pos)), Direction.UP,
                helper.absolutePos(pos), false);
        ItemStack held = new ItemStack(ScoopRegistry.meshItem(MeshTier.TWINE).get(), 2);

        ItemInteractionResult result = helper.getBlockState(pos).useItemOn(
                held, helper.getLevel(), player, InteractionHand.MAIN_HAND, hit);

        if (result != ItemInteractionResult.CONSUME) {
            helper.fail("expected CONSUME from a survival insert on the server, got " + result);
            return;
        }
        if (held.getCount() != 1) {
            helper.fail("expected the held mesh stack to shrink from 2 to 1, got " + held.getCount());
            return;
        }
        if (scoop.installedTier() != MeshTier.TWINE) {
            helper.fail("expected TWINE installed after a hand insert, got " + scoop.installedTier());
            return;
        }
        helper.succeed();
    }

    /**
     * Review round 1, probe 3 (superseded, L1-P7 mesh-swap change): a hand
     * insert against an already-occupied slot used to be refused with a
     * deny sound. It now swaps: the installed mesh comes out and into the
     * player's inventory, damage intact, and the held mesh goes in, one
     * consumed from the held stack via the same held.consume(1, player) the
     * plain install path uses.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void handInsertAgainstAnOccupiedSlotSwapsAndReturnsTheOldMeshWithItsDamage(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, ScoopRegistry.SEA_SCOOP.get());
        if (!(helper.getBlockEntity(pos) instanceof SeaScoopBlockEntity scoop)) {
            helper.fail("no SeaScoopBlockEntity at " + pos);
            return;
        }
        ItemStack worn = new ItemStack(ScoopRegistry.meshItem(MeshTier.TWINE).get());
        worn.setDamageValue(37);
        scoop.meshHandler().insertItem(0, worn, false);

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockHitResult hit = new BlockHitResult(
                Vec3.atCenterOf(helper.absolutePos(pos)), Direction.UP,
                helper.absolutePos(pos), false);
        ItemStack held = new ItemStack(ScoopRegistry.meshItem(MeshTier.CHAIN).get(), 2);

        ItemInteractionResult result = helper.getBlockState(pos).useItemOn(
                held, helper.getLevel(), player, InteractionHand.MAIN_HAND, hit);

        if (result != ItemInteractionResult.CONSUME) {
            helper.fail("expected CONSUME from a swap, got " + result);
            return;
        }
        if (held.getCount() != 1) {
            helper.fail("expected the held stack to shrink from 2 to 1, got " + held.getCount());
            return;
        }
        if (scoop.installedTier() != MeshTier.CHAIN) {
            helper.fail("expected CHAIN installed after the swap, got " + scoop.installedTier());
            return;
        }
        Optional<ItemStack> returned = player.getInventory().items.stream()
                .filter(stack -> stack.is(ScoopRegistry.meshItem(MeshTier.TWINE).get()))
                .findFirst();
        if (returned.isEmpty()) {
            helper.fail("expected the swapped-out TWINE mesh in the player's inventory");
            return;
        }
        if (returned.get().getDamageValue() != 37) {
            helper.fail("expected the returned mesh to carry damage 37, got " + returned.get().getDamageValue());
            return;
        }
        helper.succeed();
    }

    /**
     * The swap's full-inventory case: when the player has no room, the
     * swapped-out mesh must land on the ground as an item entity rather
     * than being silently destroyed - the same drop-on-full-inventory
     * fallback the empty-hand extract path uses.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void handInsertAgainstAnOccupiedSlotWithAFullInventoryDropsTheOldMesh(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, ScoopRegistry.SEA_SCOOP.get());
        if (!(helper.getBlockEntity(pos) instanceof SeaScoopBlockEntity scoop)) {
            helper.fail("no SeaScoopBlockEntity at " + pos);
            return;
        }
        ItemStack worn = new ItemStack(ScoopRegistry.meshItem(MeshTier.TWINE).get());
        worn.setDamageValue(12);
        scoop.meshHandler().insertItem(0, worn, false);

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        // makeMockPlayer spawns the player at world BlockPos.ZERO, nowhere
        // near the structure - move it to the scoop first so player.drop()
        // lands within the entity search radius below, the same as a real
        // player standing at the block would.
        player.setPos(Vec3.atCenterOf(helper.absolutePos(pos)));
        for (int i = 0; i < player.getInventory().items.size(); i++) {
            player.getInventory().items.set(i, new ItemStack(Items.STICK, 64));
        }

        BlockHitResult hit = new BlockHitResult(
                Vec3.atCenterOf(helper.absolutePos(pos)), Direction.UP,
                helper.absolutePos(pos), false);
        ItemStack held = new ItemStack(ScoopRegistry.meshItem(MeshTier.CHAIN).get(), 1);

        ItemInteractionResult result = helper.getBlockState(pos).useItemOn(
                held, helper.getLevel(), player, InteractionHand.MAIN_HAND, hit);

        if (result != ItemInteractionResult.CONSUME) {
            helper.fail("expected CONSUME from a swap with a full inventory, got " + result);
            return;
        }
        if (scoop.installedTier() != MeshTier.CHAIN) {
            helper.fail("expected CHAIN installed after the swap, got " + scoop.installedTier());
            return;
        }
        boolean playerHasMesh = player.getInventory().items.stream()
                .anyMatch(stack -> stack.is(ScoopRegistry.meshItem(MeshTier.TWINE).get()));
        if (playerHasMesh) {
            helper.fail("expected a full inventory to refuse the old mesh, not accept it");
            return;
        }
        List<ItemEntity> twineDrops = helper.getLevel()
                .getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(pos)).inflate(8.0))
                .stream()
                .filter(e -> e.getItem().is(ScoopRegistry.meshItem(MeshTier.TWINE).get()))
                .toList();
        if (twineDrops.size() != 1) {
            helper.fail("expected exactly one dropped TWINE mesh, found " + twineDrops.size());
            return;
        }
        if (twineDrops.get(0).getItem().getDamageValue() != 12) {
            helper.fail("expected the dropped mesh to carry damage 12, got "
                    + twineDrops.get(0).getItem().getDamageValue());
            return;
        }
        if (!held.isEmpty()) {
            helper.fail("expected the held CHAIN mesh to be fully consumed, got count " + held.getCount());
            return;
        }
        helper.succeed();
    }

    /**
     * Companion to the full-inventory case above, but with a damage-0 mesh.
     * Inventory.add takes a different branch for a damaged stack
     * (isDamaged(), straight to a free slot) than for an undamaged one
     * (addResource, which first tries to merge into a compatible stack
     * before falling back to a free slot) - a full inventory only proves
     * both branches safe if both get exercised.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void handInsertAgainstAnOccupiedSlotWithAFullInventoryDropsAnUndamagedOldMeshToo(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, ScoopRegistry.SEA_SCOOP.get());
        if (!(helper.getBlockEntity(pos) instanceof SeaScoopBlockEntity scoop)) {
            helper.fail("no SeaScoopBlockEntity at " + pos);
            return;
        }
        scoop.meshHandler().insertItem(0,
                new ItemStack(ScoopRegistry.meshItem(MeshTier.TWINE).get()), false);

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(Vec3.atCenterOf(helper.absolutePos(pos)));
        for (int i = 0; i < player.getInventory().items.size(); i++) {
            player.getInventory().items.set(i, new ItemStack(Items.STICK, 64));
        }

        BlockHitResult hit = new BlockHitResult(
                Vec3.atCenterOf(helper.absolutePos(pos)), Direction.UP,
                helper.absolutePos(pos), false);
        ItemStack held = new ItemStack(ScoopRegistry.meshItem(MeshTier.CHAIN).get(), 1);

        ItemInteractionResult result = helper.getBlockState(pos).useItemOn(
                held, helper.getLevel(), player, InteractionHand.MAIN_HAND, hit);

        if (result != ItemInteractionResult.CONSUME) {
            helper.fail("expected CONSUME from a swap with a full inventory, got " + result);
            return;
        }
        if (scoop.installedTier() != MeshTier.CHAIN) {
            helper.fail("expected CHAIN installed after the swap, got " + scoop.installedTier());
            return;
        }
        if (!held.isEmpty()) {
            helper.fail("expected the held CHAIN mesh to be fully consumed, got count " + held.getCount());
            return;
        }
        boolean playerHasMesh = player.getInventory().items.stream()
                .anyMatch(stack -> stack.is(ScoopRegistry.meshItem(MeshTier.TWINE).get()));
        if (playerHasMesh) {
            helper.fail("expected a full inventory to refuse the old mesh, not accept it");
            return;
        }
        List<ItemEntity> twineDrops = helper.getLevel()
                .getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(pos)).inflate(8.0))
                .stream()
                .filter(e -> e.getItem().is(ScoopRegistry.meshItem(MeshTier.TWINE).get()))
                .toList();
        if (twineDrops.size() != 1) {
            helper.fail("expected exactly one dropped TWINE mesh, found " + twineDrops.size());
            return;
        }
        if (twineDrops.get(0).getItem().getDamageValue() != 0) {
            helper.fail("expected the dropped mesh to carry damage 0, got "
                    + twineDrops.get(0).getItem().getDamageValue());
            return;
        }
        helper.succeed();
    }

    /**
     * Review round 1, probe 4: an empty-hand interaction empties the slot
     * and delivers the mesh into the player's inventory, driven through the
     * real useItemOn path.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void emptyHandExtractEmptiesTheSlotAndDeliversTheMeshToThePlayer(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, ScoopRegistry.SEA_SCOOP.get());
        if (!(helper.getBlockEntity(pos) instanceof SeaScoopBlockEntity scoop)) {
            helper.fail("no SeaScoopBlockEntity at " + pos);
            return;
        }
        scoop.meshHandler().insertItem(0,
                new ItemStack(ScoopRegistry.meshItem(MeshTier.TWINE).get()), false);

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockHitResult hit = new BlockHitResult(
                Vec3.atCenterOf(helper.absolutePos(pos)), Direction.UP,
                helper.absolutePos(pos), false);

        ItemInteractionResult result = helper.getBlockState(pos).useItemOn(
                ItemStack.EMPTY, helper.getLevel(), player, InteractionHand.MAIN_HAND, hit);

        if (result != ItemInteractionResult.CONSUME) {
            helper.fail("expected CONSUME from a server-side empty-hand extract, got " + result);
            return;
        }
        if (scoop.installedTier() != null) {
            helper.fail("expected the slot to be empty after extraction, still holds " + scoop.installedTier());
            return;
        }
        boolean playerHasMesh = player.getInventory().items.stream()
                .anyMatch(stack -> stack.is(ScoopRegistry.meshItem(MeshTier.TWINE).get()));
        if (!playerHasMesh) {
            helper.fail("expected the extracted mesh to land in the player's inventory");
            return;
        }
        helper.succeed();
    }

    /**
     * Review round 2: the first version of this test resolved
     * c:tools/wrench, found nothing (this harness loads Sable, not Create),
     * and called helper.succeed() having asserted nothing at all - coverage
     * that reports as a pass without ever exercising the code under test,
     * which is worse than no test because it reads as coverage to the next
     * person. The unconditional half below is a real regression check: a
     * non-wrench item run through the exact same useItemOn dispatch (and
     * the exact same onRemove guard, since useItemOn's wrench branch calls
     * setBlockAndUpdate, which fires onRemove) must never turn the block,
     * drop anything, or touch the installed mesh. That holds regardless of
     * whether this environment has a wrench item at all. Only once that is
     * proven does the test go on to the positive wrench turn, gated on the
     * tag actually resolving - matching onlyAWrenchTurnsTheScoop's own
     * guard - so this test asserts something real on every run and gets
     * strictly stronger the day Create is on the test classpath.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void wrenchTurningWithAMeshInstalledKeepsItAndDropsNothing(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, ScoopRegistry.SEA_SCOOP.get()
                .defaultBlockState().setValue(SeaScoopBlock.FACING, Direction.NORTH));
        if (!(helper.getBlockEntity(pos) instanceof SeaScoopBlockEntity scoop)) {
            helper.fail("no SeaScoopBlockEntity at " + pos);
            return;
        }
        scoop.meshHandler().insertItem(0,
                new ItemStack(ScoopRegistry.meshItem(MeshTier.CHAIN).get()), false);

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockHitResult hit = new BlockHitResult(
                Vec3.atCenterOf(helper.absolutePos(pos)), Direction.UP,
                helper.absolutePos(pos), false);

        // Unconditional: a non-wrench must not turn the block, drop the
        // mesh, or disturb it, no matter what this environment has
        // registered under c:tools/wrench.
        ItemStack stick = new ItemStack(Items.STICK, 1);
        helper.getBlockState(pos).useItemOn(stick, helper.getLevel(), player, InteractionHand.MAIN_HAND, hit);

        Direction afterStick = helper.getBlockState(pos).getValue(SeaScoopBlock.FACING);
        if (afterStick != Direction.NORTH) {
            helper.fail("a stick turned the scoop from NORTH to " + afterStick);
            return;
        }
        if (!(helper.getBlockEntity(pos) instanceof SeaScoopBlockEntity afterStickScoop)
                || afterStickScoop.installedTier() != MeshTier.CHAIN) {
            helper.fail("the mesh did not survive a non-wrench interaction");
            return;
        }
        long droppedAfterStick = helper.getLevel()
                .getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(pos)).inflate(8.0))
                .size();
        if (droppedAfterStick != 0) {
            helper.fail("a non-wrench interaction dropped " + droppedAfterStick + " item(s)");
            return;
        }

        // Conditional: only if this runtime actually resolves
        // c:tools/wrench to an item, prove the positive turn too - matching
        // onlyAWrenchTurnsTheScoop's own guard against an environment with
        // no wrench mod loaded.
        Optional<Item> wrench = BuiltInRegistries.ITEM.getTag(SeaScoopBlock.WRENCH)
                .flatMap(tag -> tag.stream().findFirst())
                .map(Holder::value);
        if (wrench.isEmpty()) {
            helper.succeed();
            return;
        }

        ItemStack wrenchStack = new ItemStack(wrench.get(), 1);
        helper.getBlockState(pos).useItemOn(wrenchStack, helper.getLevel(), player, InteractionHand.MAIN_HAND, hit);

        Direction after = helper.getBlockState(pos).getValue(SeaScoopBlock.FACING);
        if (after != Direction.EAST) {
            helper.fail("expected the wrench to turn north to east, got " + after);
            return;
        }
        if (!(helper.getBlockEntity(pos) instanceof SeaScoopBlockEntity turned)
                || turned.installedTier() != MeshTier.CHAIN) {
            helper.fail("the mesh did not survive a wrench turn");
            return;
        }
        long dropped = helper.getLevel()
                .getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(pos)).inflate(8.0))
                .size();
        if (dropped != 0) {
            helper.fail("turning with a wrench dropped " + dropped + " item(s)");
            return;
        }
        if (wrenchStack.getCount() != 1) {
            helper.fail("the wrench branch must never consume the wrench itself, count is now "
                    + wrenchStack.getCount());
            return;
        }
        helper.succeed();
    }
}
