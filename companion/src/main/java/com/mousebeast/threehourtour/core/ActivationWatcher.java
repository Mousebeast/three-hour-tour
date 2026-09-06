package com.mousebeast.threehourtour.core;

import com.mousebeast.threehourtour.ThreeHourTour;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelObserver;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Activation: the Physics Assembler becomes the ship core.
 *
 * This is what the spec has always described (§9) - the raft ships with an
 * assembler and nothing else, and pulling the lever turns that block into the
 * core. Doing it as a swap rather than as "place a core first, then assemble"
 * removes the claim step, removes the 24-block consumption scan, and makes it
 * impossible for a ship to end up with a live assembler still bolted to it.
 *
 * Detection is Sable's own observer API: a raft becoming a vessel IS a new
 * sub-level. The scan is deferred a few ticks because a sub-level is created
 * before the blocks are moved into it.
 */
@EventBusSubscriber(modid = ThreeHourTour.MOD_ID)
public final class ActivationWatcher implements SubLevelObserver {
    private ActivationWatcher() {}

    private static final ActivationWatcher INSTANCE = new ActivationWatcher();

    /** A sub-level is allocated before its blocks arrive; give them time to land. */
    private static final int SETTLE_TICKS = 5;

    /** Rafts are small; the plot centre is where an assembled structure lands. */
    private static final int SEARCH_RADIUS = 48;

    private static boolean registered;
    private static final Deque<Pending> PENDING = new ArrayDeque<>();

    private record Pending(UUID subLevelId, int dueTick) {}

    private static int tick;

    @Override
    public void onSubLevelAdded(SubLevel subLevel) {
        UUID id = subLevel.getUniqueId();
        if (id == null) return;
        PENDING.add(new Pending(id, tick + SETTLE_TICKS));
        ThreeHourTour.LOG.info("New sub-level {} - watching for a Physics Assembler to convert", id);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        tick++;
        ServerLevel overworld = event.getServer().overworld();

        if (!registered) {
            SubLevelContainer container = SubLevelContainer.getContainer(overworld);
            if (container == null) return;
            container.addObserver(INSTANCE);
            registered = true;
            ThreeHourTour.LOG.info("Watching for vessel assembly");
        }

        while (!PENDING.isEmpty() && PENDING.peek().dueTick() <= tick) {
            convert(overworld, PENDING.poll().subLevelId());
        }
    }

    private static void convert(ServerLevel level, UUID subLevelId) {
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) return;

        SubLevel subLevel = null;
        for (SubLevel candidate : container.getAllSubLevels()) {
            if (subLevelId.equals(candidate.getUniqueId())) { subLevel = candidate; break; }
        }
        if (subLevel == null || subLevel.isRemoved()) return;

        Optional<Block> assemblerBlock = BuiltInRegistries.BLOCK
                .getOptional(AssemblerConsumer.PHYSICS_ASSEMBLER);
        if (assemblerBlock.isEmpty()) return;

        // Every vessel loading from disk fires onSubLevelAdded too. Without this
        // guard the server scans 48 blocks in every direction around each of
        // them on every restart, for an assembler that only a pending placement
        // could have put there.
        if (AssemblerOwners.get(level).positions().isEmpty()) return;

        LevelPlot plot = subLevel.getPlot();
        if (plot == null) return;

        BlockPos found = findBlock(level, plot.getCenterBlock(), assemblerBlock.get());
        if (found == null) {
            // Not every sub-level is a player activation - a split fragment is
            // one too, and has no assembler. Quiet, but say which.
            ThreeHourTour.LOG.debug("Sub-level {} has no Physics Assembler; not an activation", subLevelId);
            return;
        }

        UUID owner = resolveOwner(level);
        if (owner == null) {
            ThreeHourTour.LOG.warn(
                    "Assembler found in sub-level {} at {} but no recorded placer - leaving it alone "
                    + "rather than creating an unowned core", subLevelId, found);
            return;
        }

        // One ship per player, enforced where a ship is actually created.
        //
        // Every other guard in the pack stops a player OBTAINING a second
        // assembler -- the recipe is removed, one cannot be placed aboard a
        // vessel, disassembly is locked to the primary. None of that helps if a
        // second assembler that exists anyway reaches a lever, and until
        // 2026-09-04 this method simply honoured it: a second core, and an
        // index overwrite that made the first one invisible to `/3ht core
        // status` and to the re-issue command that would have refused it.
        //
        // Refusing leaves the assembler standing. The sub-level exists either
        // way -- Sable has already assembled it -- but without a core it is a
        // floating structure rather than a second ship, and an operator can
        // sort it out with the assembler still in place to work from.
        ShipCoreIndex index = ShipCoreIndex.get(level);
        CoreRecord existing = index.forPlayer(owner).orElse(null);
        ServerLevel home = existing == null ? null : level.getServer().getLevel(
                ResourceKey.create(Registries.DIMENSION, existing.dimension()));
        boolean verifiable = existing != null && CoreEvidence.verifiable(home, existing.pos());
        boolean standing = existing != null && CoreEvidence.coreStandsAt(home, existing.pos());

        ActivationRule.Decision decision =
                ActivationRule.decide(existing != null, verifiable, standing);
        if (!decision.allowed()) {
            String key = decision == ActivationRule.Decision.REFUSE_HAS_CORE
                    ? "message.threehourtour.one_ship_only"
                    : "message.threehourtour.core_unverifiable";
            ThreeHourTour.LOG.warn(
                    "Refusing to create a core for {} in sub-level {}: {} (recorded core at {})",
                    owner, subLevelId, decision, existing == null ? "none" : existing.pos());
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(owner);
            if (player != null) player.displayClientMessage(Component.translatable(key), false);
            return;
        }

        BlockState core = CoreRegistry.SHIP_CORE.get().defaultBlockState();
        level.setBlock(found, core, 3);

        ShipCoreBlockEntity be = ShipCoreBlockEntity.at(level, found).orElse(null);
        if (be == null) {
            ThreeHourTour.LOG.error("Swapped assembler for a core at {} but it has no block entity", found);
            return;
        }
        CoreBinding binding = new CoreBinding(owner, subLevelId);
        be.setBinding(binding);
        ShipCoreIndex.get(level).put(
                new CoreRecord(owner, level.dimension().location(), found, subLevelId));

        ThreeHourTour.LOG.info("Activation: assembler at {} became {}'s ship core, bound to vessel {}",
                found, owner, subLevelId);
    }

    /**
     * The recorded placement whose assembler block is no longer in the world -
     * it just became part of a vessel. Exactly one candidate is the normal case.
     */
    private static UUID resolveOwner(ServerLevel level) {
        AssemblerOwners owners = AssemblerOwners.get(level);
        Optional<Block> assembler = BuiltInRegistries.BLOCK.getOptional(AssemblerConsumer.PHYSICS_ASSEMBLER);
        if (assembler.isEmpty()) return null;

        Set<BlockPos> gone = new HashSet<>();
        for (BlockPos pos : owners.positions()) {
            if (!level.isLoaded(pos)) continue;
            if (!level.getBlockState(pos).is(assembler.get())) gone.add(pos);
        }
        if (gone.size() != 1) {
            if (gone.size() > 1) {
                ThreeHourTour.LOG.warn("{} assembler placements went missing at once; cannot tell which "
                        + "belongs to this vessel", gone.size());
            }
            return null;
        }
        BlockPos claimed = gone.iterator().next();
        UUID owner = owners.ownerOf(claimed);
        owners.forget(claimed);
        return owner;
    }

    private static BlockPos findBlock(ServerLevel level, BlockPos centre, Block target) {
        if (centre == null) return null;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dy = -SEARCH_RADIUS; dy <= SEARCH_RADIUS; dy++) {
            for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx++) {
                for (int dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; dz++) {
                    cursor.set(centre.getX() + dx, centre.getY() + dy, centre.getZ() + dz);
                    if (!level.isLoaded(cursor)) continue;
                    if (level.getBlockState(cursor).is(target)) return cursor.immutable();
                }
            }
        }
        return null;
    }
}
