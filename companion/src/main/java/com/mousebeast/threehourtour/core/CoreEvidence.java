package com.mousebeast.threehourtour.core;

import com.mousebeast.threehourtour.ThreeHourTour;
import com.mousebeast.threehourtour.vessel.VesselLookup;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/**
 * The two observations both one-core decisions turn on.
 *
 * <p>{@link ReissueRule} and {@link ActivationRule} are pure and identical in
 * shape, and they need the same two facts read out of a live world: can that
 * position be read at all, and is a ship core standing there. Those readings
 * used to live inside {@code CoreCommands} as private methods, which was fine
 * while the command was the only thing that could mint a core. Activation can
 * too, and two copies of this reasoning drifting apart is a duplicate core
 * nobody notices.
 *
 * <p>The distinction the whole design rests on: <b>a false from
 * {@link #coreStandsAt} means "no core seen", which covers both "read it, and
 * nothing was there" and "could not read it".</b> {@link #verifiable} is what
 * separates those, and the rules refuse rather than guess when it is false.
 */
public final class CoreEvidence {
    private CoreEvidence() {}

    /**
     * Whether this position can be read right now: its chunk is loaded, or the
     * mod resolves it as a vessel plot.
     *
     * <p>The vessel clause is not redundant. A ship's plot sits twenty million
     * blocks out and reports "not loaded" through the ordinary chunk path even
     * while the mod resolves it perfectly well, which is what once made
     * `/3ht core reissue` refuse on a live ship.
     */
    public static boolean chunkIsAvailable(ServerLevel level, BlockPos pos) {
        try {
            ChunkAccess chunk = level.getChunk(
                    SectionPos.blockToSectionCoord(pos.getX()),
                    SectionPos.blockToSectionCoord(pos.getZ()),
                    ChunkStatus.FULL, false);
            if (chunk != null) return true;
            return VesselLookup.isOnVessel(level, pos);
        } catch (Exception e) {
            ThreeHourTour.LOG.error("core evidence: chunk availability check at {} in {} failed: {}",
                    pos, level.dimension().location(), e.toString());
            return false;
        }
    }

    /** Positive observation only. False also means "could not look". */
    public static boolean coreStandsAt(ServerLevel level, BlockPos pos) {
        try {
            if (!chunkIsAvailable(level, pos)) return false;
            return level.getBlockState(pos).is(CoreRegistry.SHIP_CORE.get());
        } catch (Exception e) {
            ThreeHourTour.LOG.error("core evidence: read at {} in {} failed: {}",
                    pos, level.dimension().location(), e.toString());
            return false;
        }
    }

    /**
     * Whether the recorded position proves anything either way.
     *
     * <p>Either signal is enough: an ordinarily loaded chunk, or a position the
     * vessel lookup resolves.
     */
    public static boolean verifiable(ServerLevel home, BlockPos pos) {
        if (home == null || pos == null) return false;
        return home.isLoaded(pos) || VesselLookup.isOnVessel(home, pos);
    }
}
