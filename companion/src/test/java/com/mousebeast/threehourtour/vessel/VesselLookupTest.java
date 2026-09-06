package com.mousebeast.threehourtour.vessel;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers only the null-argument branches of VesselLookup. Both return before
 * touching any Sable class, so these run with no Sable infrastructure on the
 * classpath - unlike a full vesselAt() test, they execute during a plain
 * `./gradlew build` and are the only thing in CI locking the never-throws
 * contract that isOnVessel() depends on for every block placement.
 */
class VesselLookupTest {
    @Test
    void vesselAtReturnsNullForNullLevelAndPos() {
        assertNull(VesselLookup.vesselAt(null, null));
    }

    @Test
    void isOnVesselReturnsFalseForNullLevelAndPos() {
        assertFalse(VesselLookup.isOnVessel(null, null));
    }
}
