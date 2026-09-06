package com.mousebeast.threehourtour.core;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class CoreRebindTest {
    private static final UUID OWNER   = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final UUID VESSEL  = UUID.fromString("00000000-0000-0000-0000-0000000000bb");
    private static final UUID SPLIT   = UUID.fromString("00000000-0000-0000-0000-0000000000cc");

    @Test void anUnclaimedCoreStaysUnclaimed() {
        assertNull(CoreRebind.after(null, VESSEL));
    }

    @Test void movingIntoANewVesselRebinds() {
        CoreBinding out = CoreRebind.after(new CoreBinding(OWNER, VESSEL), SPLIT);
        assertEquals(OWNER, out.owner());
        assertEquals(SPLIT, out.vesselId(), "the fragment carrying the core is now the ship");
    }

    @Test void movingOutOfEveryVesselGrounds() {
        CoreBinding out = CoreRebind.after(new CoreBinding(OWNER, VESSEL), null);
        assertEquals(OWNER, out.owner());
        assertFalse(out.isBound());
    }

    @Test void stayingInTheSameVesselIsUnchanged() {
        CoreBinding before = new CoreBinding(OWNER, VESSEL);
        assertEquals(before, CoreRebind.after(before, VESSEL));
    }

    @Test void aGroundedCoreRebindsWhenItFindsAVesselAgain() {
        CoreBinding out = CoreRebind.after(new CoreBinding(OWNER, null), VESSEL);
        assertEquals(VESSEL, out.vesselId());
    }
}
