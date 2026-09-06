package com.mousebeast.threehourtour.core;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class RelocateRuleTest {
    private static final UUID VESSEL = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final UUID OTHER  = UUID.fromString("00000000-0000-0000-0000-0000000000bb");

    @Test void moveWithinTheSameVesselIsAllowed() {
        assertEquals(RelocateRule.Result.OK, RelocateRule.check(VESSEL, VESSEL, true));
    }

    @Test void moveToAnotherVesselIsRefused() {
        assertEquals(RelocateRule.Result.DIFFERENT_VESSEL, RelocateRule.check(VESSEL, OTHER, true));
    }

    @Test void moveOffTheVesselIsRefused() {
        assertEquals(RelocateRule.Result.DIFFERENT_VESSEL, RelocateRule.check(VESSEL, null, true));
    }

    @Test void aCoreNotAboardAnyVesselCannotBeRelocated() {
        assertEquals(RelocateRule.Result.NOT_ABOARD, RelocateRule.check(null, VESSEL, true));
    }

    @Test void anOccupiedDestinationIsRefused() {
        assertEquals(RelocateRule.Result.OCCUPIED, RelocateRule.check(VESSEL, VESSEL, false));
    }

    @Test void everyRefusalStatesAReason() {
        for (RelocateRule.Result r : RelocateRule.Result.values()) {
            assertFalse(r.reason().isBlank(), r + " has no reason text");
        }
    }

    /**
     * Until 2026-09-02 these were literal English strings handed straight to
     * Component.literal, which made them the only player-facing text in the
     * mod that no lang file could reach and no missing-key check could see.
     * Now they are keys, and a key with no entry renders as the raw key at a
     * player -- so the lang file is the thing worth asserting against.
     */
    @Test void everyReasonHasALangEntry() throws Exception {
        String lang;
        try (var in = RelocateRuleTest.class.getResourceAsStream(
                "/assets/threehourtour/lang/en_us.json")) {
            assertNotNull(in, "en_us.json is not on the test classpath");
            lang = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        for (RelocateRule.Result r : RelocateRule.Result.values()) {
            assertTrue(lang.contains('"' + r.reason() + '"'),
                    r + " uses key " + r.reason() + ", which en_us.json does not define");
        }
    }
}
