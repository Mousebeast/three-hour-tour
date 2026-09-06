package com.mousebeast.threehourtour.core;

import com.mousebeast.threehourtour.core.ActivationRule.Decision;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The full truth table for activation, and the invariant that outranks every
 * row: nothing reaches ALLOW while a core is standing.
 *
 * <p>Exhaustive rather than representative, for the same reason
 * {@link ReissueRuleTest} is — a duplicate ship core cannot be broken, so a
 * wrong row here is a permanent fault in somebody's save. Eight combinations
 * is small enough to simply enumerate, and enumerating them is how the
 * ordering stays an invariant rather than an accident of the ifs.
 */
class ActivationRuleTest {

    private static Decision decide(boolean record, boolean verifiable, boolean standing) {
        return ActivationRule.decide(record, verifiable, standing);
    }

    // --- No record: a player who has never had a ship. ------------------------

    @Test
    void noRecordAllows() {
        assertEquals(Decision.ALLOW, decide(false, false, false));
        assertEquals(Decision.ALLOW, decide(false, true, false));
    }

    // --- A record whose core is proved gone. ----------------------------------

    @Test
    void aRecordProvedGoneAllows() {
        // Read the position, nothing there. Ship cores are indestructible, so
        // this means something outside the rules happened -- but the honest
        // reading is that they have no ship, and refusing would strand them.
        assertEquals(Decision.ALLOW, decide(true, true, false));
    }

    // --- A record that cannot be read. ----------------------------------------

    @Test
    void aRecordThatCannotBeReadRefuses() {
        // "Could not look" is not "it is gone". A core aboard an unloaded
        // vessel twenty million blocks out reads exactly like a missing one,
        // and guessing here mints the duplicate this rule exists to prevent.
        assertEquals(Decision.REFUSE_UNVERIFIABLE, decide(true, false, false));
    }

    // --- A core still standing. ------------------------------------------------

    @Test
    void aStandingCoreAlwaysRefuses() {
        assertEquals(Decision.REFUSE_HAS_CORE, decide(true, true, true));
        assertEquals(Decision.REFUSE_HAS_CORE, decide(true, false, true));
        // Even with no record at all: if a core was seen, the index is simply
        // wrong, and the block is the truth. This is not hypothetical -- the
        // index overwrite bug fixed on 2026-09-04 produced exactly this state,
        // a standing core the index had forgotten.
        assertEquals(Decision.REFUSE_HAS_CORE, decide(false, true, true));
        assertEquals(Decision.REFUSE_HAS_CORE, decide(false, false, true));
    }

    // --- The invariant, over the whole table. ---------------------------------

    @Test
    void nothingAllowsWhileACoreStands() {
        for (boolean record : new boolean[]{false, true}) {
            for (boolean verifiable : new boolean[]{false, true}) {
                assertFalse(decide(record, verifiable, true).allowed(),
                            "a standing core must refuse for every other combination: record="
                            + record + " verifiable=" + verifiable);
            }
        }
    }

    @Test
    void everyCombinationDecidesSomething() {
        for (boolean record : new boolean[]{false, true}) {
            for (boolean verifiable : new boolean[]{false, true}) {
                for (boolean standing : new boolean[]{false, true}) {
                    assertNotNull(decide(record, verifiable, standing));
                }
            }
        }
    }
}
