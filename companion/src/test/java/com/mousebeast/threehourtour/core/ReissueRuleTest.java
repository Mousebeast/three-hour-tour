package com.mousebeast.threehourtour.core;

import com.mousebeast.threehourtour.core.ReissueRule.Decision;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The full truth table for `/3ht core reissue`, plus the one invariant that
 * matters more than any individual row: no combination of inputs places a core
 * while a core is standing. A duplicate ship core is permanent - the block
 * cannot be broken - so this is the test that has to be exhaustive rather than
 * representative.
 */
class ReissueRuleTest {

    private static Decision decide(boolean record, boolean verifiable, boolean standing, boolean here) {
        return ReissueRule.decide(record, verifiable, standing, here);
    }

    // --- No record at all: a coreless player. ---------------------------------

    @Test void noRecordIssuesFresh() {
        assertEquals(Decision.ISSUE_FRESH, decide(false, false, false, false));
    }

    @Test void noRecordWithHereIssuesFresh() {
        assertEquals(Decision.ISSUE_FRESH, decide(false, false, false, true));
    }

    @Test void noRecordIgnoresTheVerifiableFlagEntirely() {
        assertEquals(Decision.ISSUE_FRESH, decide(false, true, false, false));
        assertEquals(Decision.ISSUE_FRESH, decide(false, true, false, true));
    }

    // --- A record, position readable, core proved gone. -----------------------

    @Test void aVerifiedGoneCoreIsRestoredAtItsRecordedPosition() {
        assertEquals(Decision.RESTORE, decide(true, true, false, false));
    }

    @Test void hereOverridesEvenAVerifiedGonePositionAndPlacesAtTheOperator() {
        assertEquals(Decision.ISSUE_FRESH, decide(true, true, false, true));
    }

    // --- A record whose position cannot be read. ------------------------------

    @Test void anUnverifiablePositionRefusesWithoutTheOverride() {
        assertEquals(Decision.REFUSE_UNVERIFIABLE, decide(true, false, false, false));
    }

    @Test void hereIsExactlyWhatLiftsTheUnverifiableRefusal() {
        assertEquals(Decision.ISSUE_FRESH, decide(true, false, false, true));
    }

    // --- A core is still standing. --------------------------------------------

    @Test void aStandingCoreRefuses() {
        assertEquals(Decision.REFUSE_HAS_CORE, decide(true, true, true, false));
    }

    @Test void hereDoesNotLiftTheOneCoreRule() {
        assertEquals(Decision.REFUSE_HAS_CORE, decide(true, true, true, true));
        assertEquals(Decision.REFUSE_HAS_CORE, decide(true, false, true, true));
    }

    /**
     * The invariant, over every one of the sixteen inputs. If this ever fails,
     * some path can mint a second indestructible core.
     */
    @Test void noInputAtAllPlacesACoreWhileOneIsStanding() {
        List<String> offenders = new ArrayList<>();
        for (boolean record : new boolean[]{false, true}) {
            for (boolean verifiable : new boolean[]{false, true}) {
                for (boolean here : new boolean[]{false, true}) {
                    Decision d = decide(record, verifiable, true, here);
                    if (d.placesACore()) {
                        offenders.add("record=" + record + " verifiable=" + verifiable
                                + " here=" + here + " -> " + d);
                    }
                }
            }
        }
        assertTrue(offenders.isEmpty(),
                "these inputs would place a second core over a standing one: " + offenders);
    }

    /** A standing core always refuses for the same stated reason, never some other refusal. */
    @Test void aStandingCoreAlwaysRefusesAsHasCore() {
        for (boolean record : new boolean[]{false, true}) {
            for (boolean verifiable : new boolean[]{false, true}) {
                for (boolean here : new boolean[]{false, true}) {
                    assertEquals(Decision.REFUSE_HAS_CORE, decide(record, verifiable, true, here),
                            "record=" + record + " verifiable=" + verifiable + " here=" + here);
                }
            }
        }
    }

    /** The whole table, spelled out, so a change to the ordering has to be deliberate. */
    @Test void theFullTruthTable() {
        // record, verifiable, standing, here -> expected
        assertEquals(Decision.ISSUE_FRESH,         decide(false, false, false, false));
        assertEquals(Decision.ISSUE_FRESH,         decide(false, false, false, true));
        assertEquals(Decision.REFUSE_HAS_CORE,     decide(false, false, true,  false));
        assertEquals(Decision.REFUSE_HAS_CORE,     decide(false, false, true,  true));
        assertEquals(Decision.ISSUE_FRESH,         decide(false, true,  false, false));
        assertEquals(Decision.ISSUE_FRESH,         decide(false, true,  false, true));
        assertEquals(Decision.REFUSE_HAS_CORE,     decide(false, true,  true,  false));
        assertEquals(Decision.REFUSE_HAS_CORE,     decide(false, true,  true,  true));
        assertEquals(Decision.REFUSE_UNVERIFIABLE, decide(true,  false, false, false));
        assertEquals(Decision.ISSUE_FRESH,         decide(true,  false, false, true));
        assertEquals(Decision.REFUSE_HAS_CORE,     decide(true,  false, true,  false));
        assertEquals(Decision.REFUSE_HAS_CORE,     decide(true,  false, true,  true));
        assertEquals(Decision.RESTORE,             decide(true,  true,  false, false));
        assertEquals(Decision.ISSUE_FRESH,         decide(true,  true,  false, true));
        assertEquals(Decision.REFUSE_HAS_CORE,     decide(true,  true,  true,  false));
        assertEquals(Decision.REFUSE_HAS_CORE,     decide(true,  true,  true,  true));
    }

    @Test void onlyRestoreAndIssueFreshPlaceABlock() {
        assertTrue(Decision.RESTORE.placesACore());
        assertTrue(Decision.ISSUE_FRESH.placesACore());
        assertFalse(Decision.REFUSE_HAS_CORE.placesACore());
        assertFalse(Decision.REFUSE_UNVERIFIABLE.placesACore());
    }
}
