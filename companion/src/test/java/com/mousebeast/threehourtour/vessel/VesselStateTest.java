package com.mousebeast.threehourtour.vessel;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VesselStateTest {

    @Test
    void nullLevelIsNotUnderWay() {
        // KubeJS hands us whatever the script had. Never throw into Rhino.
        assertFalse(VesselState.isUnderWay(null, null));
        assertEquals(0.0, VesselState.speedAt(null, null), 1e-9);
    }

    @Test
    void nullInputsToTheWorldSpaceEntryPointsNeverThrow() {
        // Same contract as the plot-space methods, for the new world-space
        // wrappers a script should actually be calling.
        assertFalse(VesselState.isUnderWayAt(null, null));
        assertEquals(0.0, VesselState.speedAtWorld(null, null), 1e-9);
    }

    @Test
    void nullLevelWithARealWorldPosDoesNotThrow() {
        assertFalse(VesselState.isUnderWayAt(null, new net.minecraft.core.BlockPos(0, 0, 0)));
        assertEquals(0.0, VesselState.speedAtWorld(null, new net.minecraft.core.BlockPos(0, 0, 0)), 1e-9);
    }
}
