package com.mousebeast.threehourtour.core;

import com.mousebeast.threehourtour.ThreeHourTour;
import com.mousebeast.threehourtour.vessel.VesselLookup;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/**
 * Blocks in #threehourtour:ship_only may only be placed aboard a vessel.
 *
 * This is the strongest single restriction in the pack (spec §5): farming is
 * the main reason a player would settle land, so the pots go to sea with them.
 * Normal farmland is deliberately left alone and merely weak - the wall is soft
 * and discovered, not hard and resented.
 *
 * Note the direction. AssemblerPlacement blocks its block ON a vessel; this
 * blocks its blocks OFF one. The two rules are mirror images and a swapped
 * boolean is invisible in review, so ShipOnlyPlacementTest asserts the
 * asymmetry directly.
 */
@EventBusSubscriber(modid = ThreeHourTour.MOD_ID)
public final class ShipOnlyPlacement {
    private ShipOnlyPlacement() {}

    /** The extension point: add blocks by editing the tag JSON, not this file. */
    public static final TagKey<Block> SHIP_ONLY = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(ThreeHourTour.MOD_ID, "ship_only"));

    /** The whole rule, with no Minecraft types, so it tests without a game. */
    public static boolean isBlocked(boolean shipOnly, boolean onVessel) {
        return shipOnly && !onVessel;
    }

    /**
     * The tag must actually resolve to blocks on the running server. An empty
     * tag - a misspelled directory, a bad namespace, or a datapack that failed
     * to load - silently disables the entire restriction while every unit test
     * stays green. A GameTest can't catch this: the test runtime deliberately
     * omits Botany Pots and Bonsai Trees (see build.gradle), so the tag always
     * resolves empty there regardless of whether the real pack is healthy. This
     * is the real check, against the real registry, at the moment it matters.
     */
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        long count = BuiltInRegistries.BLOCK.getTag(SHIP_ONLY)
                .map(holders -> holders.stream().count()).orElse(0L);
        if (count == 0) {
            ThreeHourTour.LOG.error("#threehourtour:ship_only resolved to no blocks - "
                    + "the ship-only placement restriction is inert; nothing will be blocked "
                    + "from being placed off-vessel");
        }
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof Level level)) return;
        if (level.isClientSide()) return;

        boolean shipOnly = event.getPlacedBlock().is(SHIP_ONLY);
        if (!shipOnly) return;

        BlockPos pos = event.getPos();
        if (!isBlocked(true, VesselLookup.isOnVessel(level, pos))) return;

        event.setCanceled(true);
        if (event.getEntity() instanceof Player player) {
            // Never a silent cancel - the player must be told why (spec L1-12).
            player.displayClientMessage(
                    Component.translatable("message.threehourtour.ship_only_placement"), true);
        }
        // info, not debug: the verification plan for this feature greps the
        // server log for this exact line to reconcile how many placements were
        // refused. At debug level the line is never emitted and the check is
        // dead. Volume is not a concern on a small co-op server.
        ThreeHourTour.LOG.info("Refused a ship-only block placement off-vessel at {}", pos);
    }
}
