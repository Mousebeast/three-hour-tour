package com.mousebeast.threehourtour.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AssemblerPlacementTest {

    @Test void anAssemblerOnAVesselIsBlocked() {
        assertTrue(AssemblerPlacement.isBlocked(true, true));
    }

    @Test void anAssemblerOnOrdinaryGroundIsAllowed() {
        // This is how a raft gets assembled in the first place: at placement
        // time the raft is not a sub-level yet.
        assertFalse(AssemblerPlacement.isBlocked(true, false));
    }

    @Test void anyOtherBlockOnAVesselIsAllowed() {
        assertFalse(AssemblerPlacement.isBlocked(false, true));
    }

    @Test void anyOtherBlockOnGroundIsAllowed() {
        assertFalse(AssemblerPlacement.isBlocked(false, false));
    }
}
