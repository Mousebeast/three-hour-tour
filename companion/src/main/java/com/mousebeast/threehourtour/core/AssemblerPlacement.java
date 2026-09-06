package com.mousebeast.threehourtour.core;

import com.mousebeast.threehourtour.ThreeHourTour;
import com.mousebeast.threehourtour.vessel.VesselLookup;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * A Physics Assembler may never be placed on a vessel.
 *
 * This is what makes an activated ship undisassemblable, and it is NOT what
 * `simulated`'s Primary Disassembly option does. That check allows disassembly
 * when the block at the recorded primary position is no longer an assembler -
 * which is exactly the state activation leaves behind. Consuming the assembler
 * therefore opens the ship rather than sealing it.
 *
 * Disassembly is driven by an assembler that is part of the contraption
 * (PhysicsAssemblerBlockEntity passes its own getBlockPos into that check), so
 * denying placement removes the actor entirely.
 *
 * Placing one on ordinary ground stays legal - that is how a raft is assembled
 * in the first place, before it is a sub-level.
 */
@EventBusSubscriber(modid = ThreeHourTour.MOD_ID)
public final class AssemblerPlacement {
    private AssemblerPlacement() {}

    /** The whole rule, with no Minecraft types, so it tests without a game. */
    public static boolean isBlocked(boolean isAssembler, boolean onVessel) {
        return isAssembler && onVessel;
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof Level level)) return;
        if (level.isClientSide()) return;

        boolean isAssembler = AssemblerConsumer.PHYSICS_ASSEMBLER.equals(
                BuiltInRegistries.BLOCK.getKey(event.getPlacedBlock().getBlock()));
        if (!isAssembler) return;

        BlockPos pos = event.getPos();
        if (!isBlocked(true, VesselLookup.isOnVessel(level, pos))) {
            // Legal placement, on ordinary ground. Remember who put it there:
            // when this raft is assembled, that player owns the resulting ship.
            // Sable's hook knows which block moved, never who pulled the lever.
            if (event.getEntity() instanceof Player placer
                    && level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                AssemblerOwners.get(serverLevel).record(pos, placer.getUUID());
                ThreeHourTour.LOG.debug("Recorded assembler placement at {} by {}", pos, placer.getUUID());
            }
            return;
        }

        event.setCanceled(true);
        if (event.getEntity() instanceof Player player) {
            // Never a silent cancel - the player must be told why (spec L1-12).
            player.displayClientMessage(
                    Component.translatable("message.threehourtour.assembler_on_vessel"), true);
        }
        ThreeHourTour.LOG.info("Refused a Physics Assembler placement aboard a vessel at {}", pos);
    }
}
