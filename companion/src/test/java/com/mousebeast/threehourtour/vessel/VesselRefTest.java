package com.mousebeast.threehourtour.vessel;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class VesselRefTest {
    @Test
    void nameIsNeverNull() {
        UUID id = UUID.fromString("00000000-0000-0000-0000-000000000001");
        assertEquals("", new VesselRef(id, null).name());
    }

    @Test
    void idIsRequired() {
        assertThrows(NullPointerException.class, () -> new VesselRef(null, "x"));
    }

    @Test
    void equalityIsByIdOnly() {
        UUID id = UUID.fromString("00000000-0000-0000-0000-000000000002");
        assertEquals(new VesselRef(id, "Alpha"), new VesselRef(id, "Beta"));
    }
}
