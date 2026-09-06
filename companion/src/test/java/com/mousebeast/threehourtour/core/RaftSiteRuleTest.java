package com.mousebeast.threehourtour.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * What counts as somewhere to strand a new player.
 *
 * <p>Four conditions, all required, and the test worth having is the one that
 * proves each is load-bearing on its own — a site that fails any single check
 * must be rejected even when the other three are perfect, because the failure
 * modes are individually fatal: land in sight is the wrong opening, an air gap
 * under the hull drops the raft on first tick, an occupied volume mangles
 * whatever was there, and the wrong biome is not this game.
 */
class RaftSiteRuleTest {

    @Test
    void aGoodSiteIsAccepted() {
        assertTrue(RaftSiteRule.acceptable(true, true, true, 0));
    }

    @Test
    void everyConditionIsLoadBearingOnItsOwn() {
        assertFalse(RaftSiteRule.acceptable(false, true, true, 0), "not an ocean biome");
        assertFalse(RaftSiteRule.acceptable(true, false, true, 0), "an air gap under the hull");
        assertFalse(RaftSiteRule.acceptable(true, true, false, 0), "something already there");
        assertFalse(RaftSiteRule.acceptable(true, true, true, 1), "land within the clearance");
    }

    @Test
    void oneColumnOfLandIsEnoughToReject() {
        // Deliberately zero-tolerance rather than a threshold. A single column
        // above sea level inside the radius is an island edge, and "mostly open
        // ocean" is the kind of nearly-right that produces a bad first hour.
        assertFalse(RaftSiteRule.acceptable(true, true, true, 1));
        assertFalse(RaftSiteRule.acceptable(true, true, true, 40));
    }
}
