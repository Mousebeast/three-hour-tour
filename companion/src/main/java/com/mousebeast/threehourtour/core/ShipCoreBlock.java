package com.mousebeast.threehourtour.core;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/**
 * The ship core: a player's vessel identity, expressed as a block.
 *
 * Deliberately has no BlockItem (see CoreRegistry). It cannot be broken,
 * dropped, pushed, or blown up, by its owner or by anyone else. A tag that
 * cannot travel cannot be planted on a hull its owner did not build.
 */
public class ShipCoreBlock extends Block implements
        net.minecraft.world.level.block.EntityBlock,
        dev.ryanhcode.sable.api.block.BlockSubLevelAssemblyListener {

    @Override
    public net.minecraft.world.level.block.entity.BlockEntity newBlockEntity(
            net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        return new ShipCoreBlockEntity(pos, state);
    }

    /**
     * Sable moved this core - on first assembly, or because the vessel split
     * and this fragment was re-assembled into a new sub-level with a new UUID.
     * Re-read the vessel at the new position and overwrite the cache.
     */
    @Override
    public void afterMove(net.minecraft.server.level.ServerLevel from,
                          net.minecraft.server.level.ServerLevel to,
                          net.minecraft.world.level.block.state.BlockState state,
                          net.minecraft.core.BlockPos fromPos,
                          net.minecraft.core.BlockPos toPos) {

        java.util.Optional<ShipCoreBlockEntity> maybeCore = ShipCoreBlockEntity.at(to, toPos);
        if (maybeCore.isEmpty()) {
            // The block moved but its block entity did not follow. Never silent:
            // a core without its binding is the one failure that loses a ship.
            com.mousebeast.threehourtour.ThreeHourTour.LOG.warn(
                    "Ship core moved {} -> {} without its block entity", fromPos, toPos);
            return;
        }

        ShipCoreBlockEntity core = maybeCore.get();
        com.mousebeast.threehourtour.vessel.VesselRef ref =
                com.mousebeast.threehourtour.vessel.VesselLookup.vesselAt(to, toPos);
        java.util.UUID vesselNow = ref == null ? null : ref.id();

        CoreBinding before = core.getBinding();
        CoreBinding after  = CoreRebind.after(before, vesselNow);

        if (after != null && !after.equals(before)) {
            core.setBinding(after);
            ShipCoreIndex.get(to).put(new CoreRecord(
                    after.owner(), to.dimension().location(), toPos, after.vesselId()));
        }

        // Activation no longer happens here: ActivationWatcher swaps the
        // assembler for the core when a vessel is first assembled, so a core
        // can never coexist with the assembler that built its ship. This hook
        // now serves splits only - the core following whichever fragment it
        // rides.
        com.mousebeast.threehourtour.ThreeHourTour.LOG.info(
                "Ship core moved {} -> {}; vessel {} -> {}",
                fromPos, toPos,
                before == null ? "unclaimed" : before.vesselId(),
                vesselNow);
    }

    public static BlockBehaviour.Properties coreProperties() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_BLACK)
                // -1 hardness is vanilla's "unbreakable in survival" (bedrock).
                .strength(-1.0F, 3_600_000.0F)
                .noLootTable()
                .pushReaction(PushReaction.BLOCK)
                .lightLevel(state -> 7)
                .sound(SoundType.NETHERITE_BLOCK);
    }

    public ShipCoreBlock(BlockBehaviour.Properties props) {
        super(props);
    }
}
