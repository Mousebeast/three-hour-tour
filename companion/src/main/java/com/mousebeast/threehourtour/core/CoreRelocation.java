package com.mousebeast.threehourtour.core;

import com.mousebeast.threehourtour.ThreeHourTour;
import com.mousebeast.threehourtour.vessel.VesselLookup;
import com.mousebeast.threehourtour.vessel.VesselRef;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Sneak-use the core with an empty hand to arm it, then use a block aboard the
 * same vessel to move it there. The core never enters an inventory at any point.
 *
 * Armed state is transient by design: it is a two-click gesture, not a saved
 * mode, and a player who logs out mid-gesture simply starts again.
 */
@EventBusSubscriber(modid = ThreeHourTour.MOD_ID)
public final class CoreRelocation {
    private CoreRelocation() {}

    private static final Map<UUID, BlockPos> ARMED = new HashMap<>();

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        // RightClickBlock fires once per hand. Without this, the main hand arms
        // the gesture and the off hand instantly cancels it two milliseconds
        // later, so it can never succeed - and the log reads as if the player
        // changed their mind every single time.
        if (event.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND) return;

        BlockPos clicked = event.getPos();
        UUID playerId = player.getUUID();
        boolean emptyHand = event.getItemStack().isEmpty();
        boolean onCore = level.getBlockState(clicked).is(CoreRegistry.SHIP_CORE.get());

        // Poking the core with an empty hand is the one gesture that costs a
        // player nothing and currently did nothing at all. Nothing else in the
        // game says the core can be moved -- it has no item, so it has no item
        // tooltip, and every other line the pack writes about it says it cannot
        // be picked up, pushed or carried off (all true of machinery and of
        // other players, and all read by a reader as "never moves"). This is
        // where a player finds out otherwise.
        //
        // Deliberately not fired with something in hand: right-clicking the
        // core's face while holding a block is how you build around it, and
        // swallowing that to print a hint would be worse than never printing
        // one. Deliberately not fired while armed either, so that clicking the
        // core's own face still moves it one block over.
        if (onCore && emptyHand && !player.isShiftKeyDown()
                && !ARMED.containsKey(playerId)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            player.displayClientMessage(
                    Component.translatable("message.threehourtour.core_move_hint"), true);
            return;
        }

        if (!emptyHand) return;

        // Sneak-use on the core itself: arm, or disarm if already armed.
        if (player.isShiftKeyDown() && onCore) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);

            ShipCoreBlockEntity core = ShipCoreBlockEntity.at(level, clicked).orElse(null);
            if (core == null || !CoreAccess.mayUse(core.getBinding(), playerId, FtbTeamResolver.INSTANCE)) {
                player.displayClientMessage(
                        Component.translatable("message.threehourtour.core_not_yours"), true);
                ThreeHourTour.LOG.info("{} tried to move a core they do not own at {}", playerId, clicked);
                return;
            }
            if (clicked.equals(ARMED.get(playerId))) {
                ARMED.remove(playerId);
                player.displayClientMessage(
                        Component.translatable("message.threehourtour.core_hold_cancelled"), true);
                ThreeHourTour.LOG.info("{} cancelled a core move at {}", playerId, clicked);
            } else {
                ARMED.put(playerId, clicked.immutable());
                player.displayClientMessage(
                        Component.translatable("message.threehourtour.core_held"), true);
                ThreeHourTour.LOG.info("{} began moving the core at {}", playerId, clicked);
            }
            return;
        }

        BlockPos from = ARMED.get(playerId);
        if (from == null) return;

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);

        BlockPos to = clicked.relative(event.getFace());
        ShipCoreBlockEntity core = ShipCoreBlockEntity.at(level, from).orElse(null);
        if (core == null) {
            ARMED.remove(playerId);
            player.displayClientMessage(
                    Component.translatable("message.threehourtour.core_gone"), true);
            ThreeHourTour.LOG.warn("Core being moved by {} vanished from {}", playerId, from);
            return;
        }

        VesselRef coreVessel   = VesselLookup.vesselAt(level, from);
        VesselRef targetVessel = VesselLookup.vesselAt(level, to);
        RelocateRule.Result verdict = RelocateRule.check(
                coreVessel == null ? null : coreVessel.id(),
                targetVessel == null ? null : targetVessel.id(),
                level.getBlockState(to).canBeReplaced());

        if (verdict != RelocateRule.Result.OK) {
            player.displayClientMessage(Component.translatable(verdict.reason()), true);
            ThreeHourTour.LOG.info("Refused core move {} -> {} for {}: {} (core vessel {}, target vessel {})",
                    from, to, playerId, verdict,
                    coreVessel == null ? "none" : coreVessel.id(),
                    targetVessel == null ? "none" : targetVessel.id());
            return;
        }

        CoreBinding binding = core.getBinding();
        level.removeBlock(from, false);
        level.setBlockAndUpdate(to, CoreRegistry.SHIP_CORE.get().defaultBlockState());
        ShipCoreBlockEntity moved = ShipCoreBlockEntity.at(level, to).orElse(null);
        if (moved == null) {
            ThreeHourTour.LOG.error("Relocated core {} -> {} but the new block entity is missing", from, to);
            player.displayClientMessage(
                    Component.translatable("message.threehourtour.core_move_failed"), true);
            ARMED.remove(playerId);
            return;
        }
        moved.setBinding(binding);
        if (binding != null) {
            ShipCoreIndex.get(level).put(new CoreRecord(
                    binding.owner(), level.dimension().location(), to, binding.vesselId()));
        }
        ARMED.remove(playerId);
        player.displayClientMessage(
                Component.translatable("message.threehourtour.core_set_down"), true);
        ThreeHourTour.LOG.info("{} moved their core {} -> {} on vessel {}",
                playerId, from, to, coreVessel.id());
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        ARMED.remove(event.getEntity().getUUID());
    }
}
