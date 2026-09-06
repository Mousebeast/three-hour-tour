package com.mousebeast.threehourtour.vessel;

import com.mousebeast.threehourtour.ThreeHourTour;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Standing on a vessel heals you, slowly. It is the only passive healing in
 * the pack.
 *
 * <p><b>Why this exists.</b> "The ship is your home and islands are places you
 * visit" is the pack's central claim, and until now nothing but respawn made it
 * true in play -- cargo space was the only reason to sail back. Vanilla natural
 * regeneration is switched off (see {@code kubejs/server_scripts}), so healing
 * has three tiers and each one already existed: the ship is free and reliable,
 * Farmer's Delight's Comfort drinks are the portable route you carry ashore,
 * and potions are the emergency. Going ashore now costs something, and coming
 * home is the relief. That is the loop the whole pack is shaped around.
 *
 * <p><b>The rate is half of Regeneration I</b>, which heals 1 HP every 50
 * ticks; this heals 1 HP every 100. At the opening ten hearts that is a hundred
 * seconds from near-death to full, and at Spice of Life's twenty-heart ceiling
 * it is two hundred. The rate is flat rather than proportional on purpose
 * (owner, 2026-09-03): by the time a player has twenty hearts they have
 * potions, gear and the Magic track, and the ship should not still be doing all
 * the work. It is deliberately slower than any consumable -- a trickle you
 * benefit from by living aboard, never a thing you stand still for.
 *
 * <p><b>Any vessel, not only your own.</b> Teams are fleets and teammates board
 * each other's ships; refusing to heal a visitor bleeding on your deck buys no
 * design and costs a bad moment. The pillar is about where your home is, not
 * about who may stand on it.
 *
 * <p>No hunger condition, unlike vanilla regeneration. A starving player who
 * reaches their ship is exactly the player this mechanic is for, and gating the
 * safe harbour behind food adds a failure state with nothing to recommend it.
 */
@EventBusSubscriber(modid = ThreeHourTour.MOD_ID)
public final class AboardRegeneration {

    private AboardRegeneration() {}

    /**
     * Ticks between heals. Vanilla Regeneration I is 50; this is half its rate.
     */
    public static final int PERIOD_TICKS = 100;

    /** Health restored each time, in half-hearts. */
    public static final float AMOUNT = 1.0f;

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        // One shared phase rather than a counter per player: every player on a
        // vessel heals on the same tick, which is both cheaper and easier to
        // reason about than staggering. The cost of the alternative is a map
        // keyed on player that has to be cleaned up on disconnect.
        if (event.getServer().getTickCount() % PERIOD_TICKS != 0) return;

        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            if (!player.isAlive() || player.isSpectator() || player.isCreative()) continue;
            if (player.getHealth() >= player.getMaxHealth()) continue;

            // blockPosition() is the block a player's feet are in. A player
            // standing on a vessel's deck is inside that vessel's chunk bounds,
            // which is what the lookup asks -- and a player swimming beside the
            // hull is not, which is the answer we want.
            BlockPos pos = player.blockPosition();
            if (!VesselLookup.isOnVessel(player.level(), pos)) continue;

            player.heal(AMOUNT);
        }
    }

    /**
     * Says so in the log if vanilla regeneration is still on.
     *
     * The two halves of this design live in different places -- the healing
     * here, the gamerule in a KubeJS script -- and a gamerule lives in the
     * world's data rather than in anything the pack ships, so a world created
     * before the script existed, or one where an operator turned it back on,
     * would quietly have both kinds of healing at once. That reads as "the ship
     * bonus does nothing", because vanilla regeneration is twice as fast and
     * hides it entirely.
     */
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        if (event.getServer().getGameRules().getBoolean(GameRules.RULE_NATURAL_REGENERATION)) {
            ThreeHourTour.LOG.warn(
                    "naturalRegeneration is ON, so vanilla healing (1 HP / 50 ticks when fed) "
                    + "runs alongside the aboard trickle (1 HP / {} ticks) and hides it. "
                    + "kubejs/server_scripts should be switching it off at load.",
                    PERIOD_TICKS);
        }
    }
}
