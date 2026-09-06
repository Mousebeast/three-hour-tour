package com.mousebeast.threehourtour.core;

import com.mousebeast.threehourtour.ThreeHourTour;
import com.mousebeast.threehourtour.vessel.VesselLookup;
import com.mousebeast.threehourtour.vessel.VesselPosition;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerRespawnPositionEvent;
import net.neoforged.neoforge.event.entity.player.PlayerSetSpawnEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The ship is home, and death proves it (spec §5). Every respawn puts the
 * player back on their own deck.
 *
 * The primary mechanism is {@link #onRespawnPosition}, which rewrites the
 * vanilla {@code DimensionTransition} before the player is actually placed.
 * {@link #onRespawn}'s post-respawn teleport is kept as a fallback - see its
 * doc for why deleting it would be dangerous.
 *
 * Beneath both sits {@link #interceptPlotSpaceRespawn}, a coordinate-space
 * floor that does not depend on a core resolving, or even existing.
 */
@EventBusSubscriber(modid = ThreeHourTour.MOD_ID)
public final class CoreRespawn {
    private CoreRespawn() {}

    /** How the respawn position was arrived at. Ordered best to worst. */
    public enum Source { LIVE, SNAPSHOT, WORLD_SPAWN }

    /** How often the snapshot is re-observed, in ticks. 200 = ten seconds. */
    private static final int REFRESH_INTERVAL = 200;

    /**
     * The live pose is the truth; the snapshot is a memory of it; world spawn
     * means we have never once seen this core and the save is broken.
     */
    public static Source chooseSource(boolean liveResolved, boolean hasSnapshot) {
        if (liveResolved) return Source.LIVE;
        return hasSnapshot ? Source.SNAPSHOT : Source.WORLD_SPAWN;
    }

    /**
     * The live position of this core, resolved the right way for how it is
     * anchored. A bound core's stored pos is a Sable plot position and must go
     * through the vessel transform. A grounded core's stored pos is already an
     * ordinary world position - CoreRecord's own doc says so - so running it
     * through VesselPosition.toWorld would ask "is this inside a vessel plot?"
     * about a position that never is, which always answers empty and strands
     * the owner of a grounded core (reachable via /3ht core claim and reissue)
     * at world spawn forever, on top of a perfectly good core.
     *
     * That branch is only sound while the record itself is consistent: a
     * plot-space position with a null vessel id would be read as grounded and
     * teleported to verbatim, ~20.4M blocks out. PlotBindingRule is what stops
     * such a record ever being written; {@link #interceptPlotSpaceRespawn} is
     * what catches one already sitting in an older save.
     *
     * Shared by onRespawn, ShipCoreIndex.refreshWorldPos, and
     * CoreCommands.where (the /3ht core where operator command, which reports
     * this same resolved position for the operator to check by eye) so this
     * branch lives in exactly one place - do not duplicate the isBound() check
     * at any call site.
     */
    static Optional<Vec3> resolveLive(ServerLevel level, CoreRecord record) {
        if (record.isBound()) {
            return VesselPosition.toWorld(level, record.pos().above());
        }
        return Optional.of(Vec3.atBottomCenterOf(record.pos().above()));
    }

    /**
     * A bed never takes the spawn point from a player who has a ship (spec
     * L1-13). The bed still works as a bed - this cancels only the spawn being
     * stored, not the sleep.
     *
     * A forced spawn is an operator acting deliberately (/spawnpoint, respawn
     * anchor) and always wins, so a stuck player can still be rescued.
     */
    public static boolean shouldOverrideBedSpawn(boolean hasCore, boolean forced) {
        return hasCore && !forced;
    }

    /**
     * Where a player's core would put them.
     *
     * Empty means "do nothing, vanilla is correct": no core record, or the
     * player's respawn was forced by an operator (/spawnpoint, respawn
     * anchor), which always outranks the ship.
     *
     * A present result with a null {@code level} means the core's dimension is
     * not currently loaded. A present result with a null {@code target} (level
     * non-null) means the vessel has never once been observed - Source.WORLD_SPAWN.
     * Both are "cannot resolve" for {@link #onRespawnPosition}'s purposes; only
     * {@link #onRespawn} needs to tell them apart, for its two distinct log
     * messages.
     */
    private record CoreDestination(CoreRecord record, ServerLevel level, Source source, Vec3 target) {}

    /**
     * The destination {@link #onRespawnPosition} resolved, handed forward to
     * {@link #onRespawn} so the two handlers cannot disagree about where the
     * player was supposed to go.
     *
     * They see the player at different moments - one before the respawn, one
     * after - so resolving twice would let them differ on facts like
     * isRespawnForced(). Computing it once and passing it makes agreement
     * structural rather than incidental.
     *
     * Keyed by player UUID and removed on consumption. An entry is only left
     * behind if the position event fired and the respawn event did not, in
     * which case it is one small object per player, overwritten on their next
     * death. The fallback still recomputes when nothing was handed forward,
     * because that is exactly the case the fallback exists for.
     */
    private static final Map<UUID, Optional<CoreDestination>> PENDING = new ConcurrentHashMap<>();

    private static Optional<CoreDestination> resolveCoreDestination(ServerPlayer player) {
        ServerLevel overworld = player.server.overworld();
        ShipCoreIndex index = ShipCoreIndex.get(overworld);
        CoreRecord record = index.forPlayer(player.getUUID()).orElse(null);
        if (record == null) return Optional.empty();   // No core yet - vanilla respawn is correct.

        // An operator setting a spawn deliberately (/spawnpoint, respawn anchor)
        // outranks the ship. Without this the forced-spawn exemption in
        // shouldOverrideBedSpawn is decorative: vanilla would honour the forced
        // spawn and this handler would immediately drag the player off it again.
        if (player.isRespawnForced()) return Optional.empty();

        ServerLevel level = player.server.getLevel(
                ResourceKey.create(Registries.DIMENSION, record.dimension()));
        if (level == null) {
            return Optional.of(new CoreDestination(record, null, null, null));
        }

        Optional<Vec3> live = resolveLive(level, record);
        Source source = chooseSource(live.isPresent(), record.hasWorldPos());
        Vec3 target = switch (source) {
            case LIVE -> live.get();
            case SNAPSHOT -> record.lastWorldPos();
            case WORLD_SPAWN -> null;
        };
        return Optional.of(new CoreDestination(record, level, source, target));
    }

    /**
     * Best-effort nicety, not the mechanism. PlayerSetSpawnEvent fires only
     * intermittently in this pack - confirmed live, on one sleep and not the
     * next, with a HIGHEST-priority receiveCanceled diagnostic listener seeing
     * nothing on the miss - so it cannot be relied on to stop a bed from
     * storing a plot-space spawn point. {@link #onRespawnPosition} is what
     * actually guards against that now. When this handler DOES fire, it still
     * correctly cancels the vanilla "Respawn point set" message from being
     * misleading, which is worth keeping even though it cannot be trusted to
     * run.
     */
    @SubscribeEvent
    public static void onSetSpawn(PlayerSetSpawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        boolean hasCore = ShipCoreIndex.get(player.server.overworld()).hasCore(player.getUUID());
        if (!shouldOverrideBedSpawn(hasCore, event.isForced())) return;

        event.setCanceled(true);
        ThreeHourTour.LOG.debug("set spawn: cancelled for player={} (hasCore={} forced={})",
                player.getGameProfile().getName(), hasCore, event.isForced());
        // Clearing a spawn point is also a set, with a null position. Telling a
        // player "your ship is your spawn" as they climb out of a bed they just
        // broke would be noise, so only the real case speaks.
        if (event.getNewSpawn() != null) {
            player.displayClientMessage(
                    Component.translatable("message.threehourtour.spawn_is_your_ship"), true);
        }
    }

    /**
     * The primary mechanism. Rewrites the vanilla respawn destination before
     * the player is actually placed, so a bed on a Sable vessel (whose blocks
     * live at plot coordinates ~20.4M out) can never strand a core owner
     * there. Unlike PlayerSetSpawnEvent, this event is not cancellable and, on
     * every death observed so far, fires reliably.
     *
     * Two passes, in this order and only this order: the core destination is
     * the DESIRED outcome, and {@link #interceptPlotSpaceRespawn} is the FLOOR
     * beneath it. The floor runs afterwards no matter what the first pass did -
     * whether it placed the player, declined, or threw - and it runs for
     * players with no core record at all, who nothing else in this class
     * touches.
     *
     * Nothing here may throw: an exception during respawn is how a player
     * gets stuck on a loading screen, so any failure is caught and logged
     * instead. The two passes have separate try/catch blocks precisely so a
     * failure in the first cannot skip the second.
     */
    @SubscribeEvent
    public static void onRespawnPosition(PlayerRespawnPositionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        try {
            Optional<CoreDestination> resolved = resolveCoreDestination(player);
            PENDING.put(player.getUUID(), resolved);

            CoreDestination dest = resolved.orElse(null);
            // dest == null: no core, or forced spawn - vanilla is correct.
            // null level/target: unresolvable - onRespawn will message the player.
            if (dest != null && dest.level() != null && dest.target() != null) {
                DimensionTransition original = event.getDimensionTransition();
                Vec3 target = dest.target();
                DimensionTransition updated = new DimensionTransition(dest.level(), target, original.speed(),
                        original.yRot(), original.xRot(), original.postDimensionTransition());
                event.setDimensionTransition(updated);

                ThreeHourTour.LOG.info(
                        "respawn position: player={} source={} original=({}, {}, {}) set=({}, {}, {})",
                        player.getGameProfile().getName(), dest.source(),
                        original.pos().x, original.pos().y, original.pos().z,
                        target.x, target.y, target.z);
            }
        } catch (Exception e) {
            ThreeHourTour.LOG.error("Failed to set respawn position for {}; leaving vanilla transition in place",
                    player.getGameProfile().getName(), e);
        }

        interceptPlotSpaceRespawn(event, player);
    }

    /**
     * Is this world-space point actually a Sable plot coordinate?
     *
     * The single predicate behind every plot-space guard in this class. It has
     * two callers - {@link #interceptPlotSpaceRespawn}, which rewrites the
     * transition, and {@link #onRespawn}, which declines to teleport - and they
     * MUST agree: the fallback teleport runs after the interceptor and would
     * otherwise silently undo it, putting the player back on the coordinate the
     * interceptor just rescued them from. One copy of the check is what makes
     * that impossible.
     *
     * Not unit-testable, and not extracted further: the whole question is
     * "does Sable's SubLevelContainer claim this chunk", which needs a live
     * Level and a live container. There is no boolean decision left to pull out
     * once VesselLookup.isOnVessel has answered. VesselLookupTest covers the
     * predicate's own null-safety.
     */
    private static boolean isPlotSpace(ServerLevel level, Vec3 pos) {
        if (level == null || pos == null) return false;
        return VesselLookup.isOnVessel(level, BlockPos.containing(pos));
    }

    /**
     * The floor: no player is ever left standing in Sable's plot region.
     *
     * A bed on a deck is a ship block, so vanilla records the respawn point at
     * PLOT coordinates - measured, SpawnX=20481033. Any player whose core did
     * not resolve (freshly claimed or re-issued, so no snapshot yet) or who has
     * no core record at all would then be placed ~20.4M blocks out, where there
     * is no way back. This runs on the position the transition is ACTUALLY
     * about to use, after the core logic has had its chance, so it catches that
     * case regardless of how it arose.
     *
     * It is deliberately unconditional rather than skipped when the core logic
     * succeeded. A destination we chose on purpose is always a WORLD position -
     * VesselPosition.toWorld returns one by construction, the snapshot is a
     * previously-returned one, and a grounded record's position is documented
     * as walkable - and isOnVessel answers false for world positions, because
     * SubLevelContainer.inBounds tests the plot region a normal overworld
     * coordinate is nowhere near. So a player we placed on their own deck is
     * never bounced. What the unconditional form DOES catch is a record already
     * in an old save that pairs a plot position with a null vessel id, which
     * resolveLive would happily hand to a teleport.
     *
     * Its own try/catch: this is the last thing standing between a player and
     * being stranded, and it must not be able to throw out of the respawn event
     * and leave them on a loading screen instead.
     */
    private static void interceptPlotSpaceRespawn(PlayerRespawnPositionEvent event, ServerPlayer player) {
        try {
            DimensionTransition current = event.getDimensionTransition();
            if (current == null) return;

            ServerLevel level = current.newLevel();
            Vec3 pos = current.pos();
            if (!isPlotSpace(level, pos)) return;

            Vec3 worldSpawn = Vec3.atBottomCenterOf(level.getSharedSpawnPos());
            event.setDimensionTransition(new DimensionTransition(level, worldSpawn, current.speed(),
                    current.yRot(), current.xRot(), current.missingRespawnBlock(),
                    current.postDimensionTransition()));

            ThreeHourTour.LOG.warn(
                    "respawn safety net: intercepted a plot-space respawn for player={} at "
                            + "({}, {}, {}) in {}; sent to world spawn ({}, {}, {}) instead",
                    player.getGameProfile().getName(), pos.x, pos.y, pos.z,
                    level.dimension().location(), worldSpawn.x, worldSpawn.y, worldSpawn.z);
        } catch (Exception e) {
            ThreeHourTour.LOG.error(
                    "respawn safety net failed for {}; leaving the transition as it stands",
                    player.getGameProfile().getName(), e);
        }
    }

    /**
     * Fallback. {@link #onRespawnPosition} is the primary mechanism now, but
     * it is new and this teleport has already reliably placed every observed
     * respawn correctly - it stays as belt-and-braces on the one path that can
     * strand a player ~20.4M blocks out in Sable's plot region. When the
     * position handler already did its job, {@code alreadyThere} makes this a
     * no-op rather than a second, visible jump.
     *
     * The destination is the one the position handler already resolved, taken
     * from {@link #PENDING}. It only resolves for itself when nothing was
     * handed forward, which means the position handler did not run - the very
     * situation this fallback exists for.
     */
    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        try {
            Optional<CoreDestination> resolved = PENDING.remove(player.getUUID());
            if (resolved == null) resolved = resolveCoreDestination(player);

            CoreDestination dest = resolved.orElse(null);
            if (dest == null) return;   // No core, or forced spawn - vanilla respawn is correct.

            if (dest.level() == null) {
                // Told, not silently dumped. Spec L1-13 and the no-silent-failure
                // rule - a recorded core whose dimension is not loaded is a broken
                // save, the same class of problem as WORLD_SPAWN below, so it gets
                // the same player-facing message.
                player.displayClientMessage(
                        Component.translatable("message.threehourtour.respawn_no_ship"), false);
                ThreeHourTour.LOG.error("Core dimension {} is not loaded; could not respawn {} on their ship",
                        dest.record().dimension(), player.getGameProfile().getName());
                return;
            }

            if (dest.target() == null) {
                // Told, not silently dumped. Spec L1-13 and the no-silent-failure rule.
                player.displayClientMessage(
                        Component.translatable("message.threehourtour.respawn_no_ship"), false);
                ThreeHourTour.LOG.error("Could not resolve a respawn position for {}: core at {} in {}, "
                        + "vessel {}, never observed in the world",
                        player.getGameProfile().getName(), dest.record().pos(), dest.record().dimension(),
                        dest.record().vesselId());
                return;
            }

            ServerLevel level = dest.level();
            Vec3 target = dest.target();

            // The same floor the interceptor applies, applied again here -
            // because this handler runs AFTER it and would otherwise undo it.
            // A legacy record pairing a plot position with a null vessel id
            // reads as grounded, so resolveLive hands back that plot
            // coordinate; the interceptor moves the player to world spawn,
            // which makes alreadyThere false, which used to send them straight
            // back out to 20.4M blocks. Declining to teleport leaves them where
            // the interceptor put them, which is the safe answer.
            if (isPlotSpace(level, target)) {
                ThreeHourTour.LOG.warn(
                        "respawn safety net: refused the fallback teleport for player={} to "
                                + "plot-space target ({}, {}, {}) in {}; left at the safe position "
                                + "the respawn interceptor chose. Core record {} in {} pairs a plot "
                                + "position with vessel {} - fix it with /3ht core reissue.",
                        player.getGameProfile().getName(), target.x, target.y, target.z,
                        level.dimension().location(), dest.record().pos(), dest.record().dimension(),
                        dest.record().isBound() ? dest.record().vesselId() : "none (grounded)");
                player.displayClientMessage(
                        Component.translatable("message.threehourtour.respawn_no_ship"), false);
                return;
            }

            boolean alreadyThere = player.level() == level && player.position().distanceToSqr(target) < 0.01;
            if (!alreadyThere) {
                player.teleportTo(level, target.x, target.y, target.z, player.getYRot(), player.getXRot());
            }
            // One line, not two. SNAPSHOT is the ordinary outcome at respawn -
            // measured, not the exception - so it does not warrant a second
            // "did not resolve" line that reads like a fault. The vessel id
            // that line used to carry lives here instead, so nothing is lost.
            ThreeHourTour.LOG.info(
                    "respawn: player={} source={} target=({}, {}, {}) corePlotPos={} vessel={} alreadyPlaced={}",
                    player.getGameProfile().getName(), dest.source(), target.x, target.y, target.z,
                    dest.record().pos(),
                    dest.record().isBound() ? dest.record().vesselId() : "grounded",
                    alreadyThere);
        } catch (Exception e) {
            ThreeHourTour.LOG.error("Respawn fallback teleport failed for {}",
                    player.getGameProfile().getName(), e);
        }
    }

    /**
     * Keep the snapshot fresh. Any player online near their own ship keeps it
     * current, and being aboard means the vessel is loaded by definition.
     */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % REFRESH_INTERVAL != 0) return;
        ServerLevel overworld = event.getServer().overworld();
        ShipCoreIndex index = ShipCoreIndex.get(overworld);
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            CoreRecord record = index.forPlayer(player.getUUID()).orElse(null);
            if (record == null) continue;
            ServerLevel level = event.getServer().getLevel(
                    ResourceKey.create(Registries.DIMENSION, record.dimension()));
            if (level != null) index.refreshWorldPos(level, player.getUUID());
        }
    }
}
