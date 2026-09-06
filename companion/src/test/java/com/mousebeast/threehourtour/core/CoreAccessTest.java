package com.mousebeast.threehourtour.core;

import org.junit.jupiter.api.Test;
import java.util.Set;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class CoreAccessTest {
    private static final UUID OWNER    = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final UUID TEAMMATE = UUID.fromString("00000000-0000-0000-0000-0000000000bb");
    private static final UUID STRANGER = UUID.fromString("00000000-0000-0000-0000-0000000000cc");
    private static final UUID VESSEL   = UUID.fromString("00000000-0000-0000-0000-0000000000dd");

    /** OWNER and TEAMMATE share a team; STRANGER is alone. */
    private static final TeamResolver TEAMS = (a, b) -> {
        Set<UUID> crew = Set.of(OWNER, TEAMMATE);
        return crew.contains(a) && crew.contains(b);
    };

    @Test void theOwnerMayUseTheirOwnCore() {
        assertTrue(CoreAccess.mayUse(new CoreBinding(OWNER, VESSEL), OWNER, TEAMS));
    }

    @Test void aTeammateMayUseIt() {
        assertTrue(CoreAccess.mayUse(new CoreBinding(OWNER, VESSEL), TEAMMATE, TEAMS));
    }

    @Test void aStrangerMayNot() {
        assertFalse(CoreAccess.mayUse(new CoreBinding(OWNER, VESSEL), STRANGER, TEAMS));
    }

    @Test void anUnclaimedCoreIsUsableByNobody() {
        assertFalse(CoreAccess.mayUse(null, OWNER, TEAMS));
    }

    @Test void aGroundedCoreIsStillItsOwnersAndTheirTeams() {
        CoreBinding grounded = new CoreBinding(OWNER, null);
        assertTrue(CoreAccess.mayUse(grounded, OWNER, TEAMS));
        assertTrue(CoreAccess.mayUse(grounded, TEAMMATE, TEAMS));
        assertFalse(CoreAccess.mayUse(grounded, STRANGER, TEAMS));
    }

    @Test void aNullPlayerMayNot() {
        assertFalse(CoreAccess.mayUse(new CoreBinding(OWNER, VESSEL), null, TEAMS));
    }
}
