package com.mousebeast.threehourtour.core;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class CoreBindingTest {
    private static final UUID OWNER  = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final UUID VESSEL = UUID.fromString("00000000-0000-0000-0000-0000000000bb");
    private static final UUID OTHER  = UUID.fromString("00000000-0000-0000-0000-0000000000cc");

    @Test void ownerIsRequired() {
        assertThrows(NullPointerException.class, () -> new CoreBinding(null, VESSEL));
    }

    @Test void aBindingWithAVesselIsBound() {
        CoreBinding b = new CoreBinding(OWNER, VESSEL);
        assertTrue(b.isBound());
        assertEquals(VESSEL, b.vesselId());
    }

    @Test void aBindingWithoutAVesselIsGrounded() {
        CoreBinding b = new CoreBinding(OWNER, null);
        assertFalse(b.isBound());
        assertNull(b.vesselId());
    }

    @Test void reboundKeepsTheOwnerAndReplacesTheVessel() {
        CoreBinding before = new CoreBinding(OWNER, VESSEL);
        CoreBinding after  = before.boundTo(OTHER);
        assertEquals(OWNER, after.owner());
        assertEquals(OTHER, after.vesselId());
        assertEquals(VESSEL, before.vesselId(), "boundTo must not mutate the receiver");
    }

    @Test void groundingClearsTheVesselOnly() {
        CoreBinding grounded = new CoreBinding(OWNER, VESSEL).grounded();
        assertEquals(OWNER, grounded.owner());
        assertFalse(grounded.isBound());
    }

    @Test void equalityIsByOwnerAndVessel() {
        assertEquals(new CoreBinding(OWNER, VESSEL), new CoreBinding(OWNER, VESSEL));
        assertNotEquals(new CoreBinding(OWNER, VESSEL), new CoreBinding(OWNER, OTHER));
        assertNotEquals(new CoreBinding(OWNER, null), new CoreBinding(OTHER, null));
    }
}
