package com.mousebeast.threehourtour.scoop;

/**
 * Is this vessel under way?
 *
 * Pure, so the answer is testable without a server and the thresholds are
 * visible in one place.
 *
 * Hysteresis is the point. A single threshold flickers, because a moored hull
 * bobs on water and crosses any small bar constantly - which would make the
 * scoop stutter at anchor and make the KubeJS binding untrustworthy. Starting
 * requires more speed than continuing.
 *
 * Units are blocks per tick: 0.05 b/t is 1 block/second.
 */
public final class UnderWayRule {

    /** Must exceed this to BEGIN being under way. ~0.4 blocks/second. */
    public static final double START_THRESHOLD = 0.02;

    /** Must fall below this to STOP being under way. ~0.16 blocks/second. */
    public static final double STOP_THRESHOLD = 0.008;

    private UnderWayRule() {}

    public static boolean next(double speed, boolean wasUnderWay) {
        if (Double.isNaN(speed) || speed < 0.0) {
            return false;
        }
        if (wasUnderWay) {
            return speed >= STOP_THRESHOLD;
        }
        return speed > START_THRESHOLD;
    }
}
