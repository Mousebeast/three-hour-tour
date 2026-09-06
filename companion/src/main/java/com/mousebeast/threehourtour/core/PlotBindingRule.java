package com.mousebeast.threehourtour.core;

/**
 * A core position in plot space must carry a vessel reference, or the record
 * is a trap.
 *
 * Pure by design - no Minecraft, no Sable. The rule exists because two
 * different Sable lookups answer "is this on a vessel?" by two different
 * tests. VesselPosition.toPlot asks whether the point falls inside a plot's
 * bounds; VesselLookup.vesselAt asks whether the CHUNK is in the container and
 * additionally degrades a null Sable id to "not on a vessel". They can
 * disagree, and a `.below()` step can carry a position out of the plot after
 * the first test passed.
 *
 * When they disagree the written record has a position ~20.4 million blocks
 * out and a null vessel id. CoreRecord's contract says a null vessel id means
 * GROUNDED, and grounded means the position is an ordinary walkable world
 * position - so CoreRespawn.resolveLive skips the vessel transform and hands
 * that plot coordinate straight to a teleport. The player is then stranded
 * 20.4M blocks from the world with no way back.
 *
 * The rule is therefore absolute: a position that came out of the plot
 * transform is only allowed into a record together with a resolved vessel.
 * Refusing the operator's command is cheap; an inconsistent record is not
 * recoverable by the player it strands.
 */
public final class PlotBindingRule {
    private PlotBindingRule() {}

    /**
     * True when the pair may be written to a CoreRecord.
     *
     * A world-space position is consistent either way: grounded cores are
     * legitimate, and a world position that happens to sit on a vessel is
     * still a walkable position.
     */
    public static boolean isConsistent(boolean positionCameFromPlotTransform, boolean vesselResolved) {
        return !positionCameFromPlotTransform || vesselResolved;
    }

    /** The same rule stated the way the command uses it: refuse, do not write. */
    public static boolean mustRefuse(boolean positionCameFromPlotTransform, boolean vesselResolved) {
        return !isConsistent(positionCameFromPlotTransform, vesselResolved);
    }
}
