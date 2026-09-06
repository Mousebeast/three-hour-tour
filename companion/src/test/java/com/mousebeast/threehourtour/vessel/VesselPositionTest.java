package com.mousebeast.threehourtour.vessel;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers only the null-argument branches of VesselPosition. Both toWorld and
 * toPlot return before touching any Sable class, so these run with no Sable
 * infrastructure on the classpath - unlike a full round-trip test, they
 * execute during a plain `./gradlew build`.
 *
 * toPlot is the exact inverse of toWorld (see its doc): a player's F3 position
 * is world space, a ship block's stored position is plot space, and this is
 * what goes from the former to the latter. The transform itself - iterating
 * live sub-levels, inverse-posing, and testing plot containment - is only
 * reachable through live Sable state and is not faked here.
 */
class VesselPositionTest {
    @Test
    void toWorldReturnsEmptyForNullLevelAndPos() {
        assertTrue(VesselPosition.toWorld(null, null).isEmpty());
    }

    @Test
    void toPlotReturnsEmptyForNullLevelAndWorldPos() {
        assertTrue(VesselPosition.toPlot(null, null).isEmpty());
    }
}
