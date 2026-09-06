package com.mousebeast.threehourtour.core;

/**
 * What makes a patch of sea fit to put a starting raft on.
 *
 * <p>Pure, so the policy can be read and tested without a world. The world
 * reading lives in {@link RaftPlacement}; the decision lives here, because
 * "good enough to strand a new player on" is a design statement and deserves to
 * be one line rather than an ordering of ifs buried in a search loop.
 *
 * <p>The four conditions come from the owner (2026-09-04): an ocean biome, no
 * land within a decent radius, the hull resting directly on the water with no
 * air gap beneath it, and the volume it occupies clear.
 */
public final class RaftSiteRule {
    private RaftSiteRule() {}

    /**
     * @param oceanBiome     the biome at the site is in the ocean tag
     * @param restsOnWater   every column under the hull is water at the layer
     *                       immediately below it -- the raft sits ON the sea,
     *                       not one block above it
     * @param volumeClear    nothing solid stands where the raft goes
     * @param landColumns    how many sampled columns within the clearance
     *                       radius rise above sea level
     */
    public static boolean acceptable(boolean oceanBiome, boolean restsOnWater,
                                     boolean volumeClear, int landColumns) {
        return oceanBiome && restsOnWater && volumeClear && landColumns == 0;
    }
}
