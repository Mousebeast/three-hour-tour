package com.mousebeast.threehourtour.scoop;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UnderWayRuleTest {

    @Test
    void stationaryVesselIsNotUnderWay() {
        assertFalse(UnderWayRule.next(0.0, false));
    }

    @Test
    void clearlyMovingVesselBecomesUnderWay() {
        assertTrue(UnderWayRule.next(0.5, false));
    }

    @Test
    void bobbingAtAnchorNeverStarts() {
        // A moored hull rocking on water must not read as under way, or the
        // scoop pays out forever at the dock and the movement gate is a lie.
        double bob = UnderWayRule.START_THRESHOLD * 0.5;
        assertFalse(UnderWayRule.next(bob, false));
    }

    @Test
    void hysteresisKeepsAMovingVesselUnderWayThroughASlowPatch() {
        // Between the two thresholds, state is preserved rather than flipped.
        double between = (UnderWayRule.START_THRESHOLD + UnderWayRule.STOP_THRESHOLD) / 2.0;
        assertTrue(UnderWayRule.next(between, true));
        assertFalse(UnderWayRule.next(between, false));
    }

    @Test
    void droppingBelowStopThresholdEndsUnderWay() {
        assertFalse(UnderWayRule.next(UnderWayRule.STOP_THRESHOLD * 0.5, true));
    }

    @Test
    void atTheExactStartThresholdAVesselHasNotStartedYet() {
        // The > / >= asymmetry is the entire anti-flicker property. A single
        // threshold (with > both ways) would start and stop constantly as a
        // moored hull bobs. START uses > (must exceed), STOP uses >=
        // (may equal). This boundary test is the only thing standing between
        // correct logic and a silent operator-swap bug.
        assertFalse(UnderWayRule.next(UnderWayRule.START_THRESHOLD, false));
    }

    @Test
    void atTheExactStopThresholdAVesselIsStillUnderWay() {
        // The >= boundary: a vessel under way stays under way at exactly STOP.
        // Paired with START's > boundary, this is the hysteresis that keeps
        // the scoop stable.
        assertTrue(UnderWayRule.next(UnderWayRule.STOP_THRESHOLD, true));
    }

    @Test
    void startThresholdIsStrictlyAboveStopThreshold() {
        // If these ever invert, hysteresis becomes oscillation.
        assertTrue(UnderWayRule.START_THRESHOLD > UnderWayRule.STOP_THRESHOLD);
    }

    @Test
    void negativeOrNaNSpeedIsNotUnderWay() {
        assertFalse(UnderWayRule.next(Double.NaN, true));
        assertFalse(UnderWayRule.next(-1.0, true));
    }
}
