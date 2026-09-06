package com.mousebeast.threehourtour.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ShipOnlyPlacementTest {

    @Test void aRestrictedBlockOffVesselIsBlocked() {
        assertTrue(ShipOnlyPlacement.isBlocked(true, false));
    }

    @Test void aRestrictedBlockOnAVesselIsAllowed() {
        assertFalse(ShipOnlyPlacement.isBlocked(true, true));
    }

    @Test void anUnrestrictedBlockOffVesselIsAllowed() {
        // The guardrail: ordinary land building is not banned. Spec L1-15.
        assertFalse(ShipOnlyPlacement.isBlocked(false, false));
    }

    @Test void anUnrestrictedBlockOnAVesselIsAllowed() {
        assertFalse(ShipOnlyPlacement.isBlocked(false, true));
    }

    @Test void theRuleIsTheExactInverseOfTheAssemblerRule() {
        // Documents the symmetry deliberately: an assembler is blocked ON a
        // vessel, a pot is blocked OFF one. Getting these backwards compiles,
        // passes a smoke test, and produces a pack nobody can farm in.
        assertNotEquals(AssemblerPlacement.isBlocked(true, true),
                        ShipOnlyPlacement.isBlocked(true, true));
    }
}
