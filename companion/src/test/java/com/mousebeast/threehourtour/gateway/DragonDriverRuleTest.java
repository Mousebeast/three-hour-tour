package com.mousebeast.threehourtour.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DragonDriverRuleTest {

    private static final double LEASH_SQR = 64.0 * 64.0;

    @Test
    void aDragonAlreadyChargingInsideTheLeashIsLeftAlone() {
        assertFalse(DragonDriverRule.shouldRecharge(true, 100.0, LEASH_SQR));
    }

    @Test
    void aDragonThatHasStoppedChargingIsSentBackIn() {
        assertTrue(DragonDriverRule.shouldRecharge(false, 100.0, LEASH_SQR));
    }

    @Test
    void aDragonDriftingOutOfLeashIsRechargedEvenWhileCharging() {
        // The whole reason this class exists: HOLDING_PATTERN paths on a node
        // ring centred on world (0,0), so an unattended dragon leaves for
        // origin and Gateways fails the gate when it passes the leash.
        assertTrue(DragonDriverRule.shouldRecharge(true, LEASH_SQR + 1.0, LEASH_SQR));
    }

    @Test
    void theMarkerKeyIsNamespacedAndStable() {
        // Written into entity persistent data, which survives save and load.
        // Renaming it orphans every dragon already in flight on the server.
        assertEquals("3ht_gate_dragon", DragonDriverRule.MARKER);
    }
}
