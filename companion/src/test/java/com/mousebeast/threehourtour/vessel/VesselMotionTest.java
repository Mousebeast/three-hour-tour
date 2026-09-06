package com.mousebeast.threehourtour.vessel;

import org.joml.Vector3d;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VesselMotionTest {

    @Test
    void stationaryVesselHasZeroSpeed() {
        Vector3d p = new Vector3d(20481031.5, 130.0, 20481030.5);
        assertEquals(0.0, VesselMotion.speedBetween(p, new Vector3d(p)), 1e-9);
    }

    @Test
    void speedIsEuclideanDistancePerTick() {
        Vector3d last = new Vector3d(0.0, 64.0, 0.0);
        Vector3d now = new Vector3d(3.0, 64.0, 4.0);
        assertEquals(5.0, VesselMotion.speedBetween(now, last), 1e-9);
    }

    @Test
    void verticalMotionCounts() {
        // A ship rising on a swell is moving. Whether that should count as
        // "under way" is UnderWayRule's problem, not this function's.
        Vector3d last = new Vector3d(0.0, 64.0, 0.0);
        Vector3d now = new Vector3d(0.0, 64.25, 0.0);
        assertEquals(0.25, VesselMotion.speedBetween(now, last), 1e-9);
    }

    @Test
    void speedIsNeverNegative() {
        Vector3d last = new Vector3d(10.0, 64.0, 10.0);
        Vector3d now = new Vector3d(0.0, 64.0, 0.0);
        assertTrue(VesselMotion.speedBetween(now, last) > 0.0);
    }

    @Test
    void nullPoseIsTreatedAsStationary() {
        // Sable populates lastPose lazily; a freshly assembled vessel can
        // report null for one tick. That must read as stationary, not crash.
        assertEquals(0.0, VesselMotion.speedBetween(null, new Vector3d()), 1e-9);
        assertEquals(0.0, VesselMotion.speedBetween(new Vector3d(), null), 1e-9);
    }

    @Test
    void speedOfReturnsZeroForNullSub() {
        // This runs on a block tick. A null or removed SubLevel must read as
        // stationary rather than throwing, or the exception breaks the block
        // every tick. No Mockito on the classpath to fake a removed SubLevel,
        // so only the null branch is covered here - see VesselLookupTest for
        // the same convention.
        assertEquals(0.0, VesselMotion.speedOf(null), 1e-9);
    }
}
