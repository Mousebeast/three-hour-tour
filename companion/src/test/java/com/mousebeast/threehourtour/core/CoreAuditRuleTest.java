package com.mousebeast.threehourtour.core;

import com.mousebeast.threehourtour.core.CoreAuditRule.Owner;
import com.mousebeast.threehourtour.core.CoreAuditRule.Verdict;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The full truth table for `/3ht core audit`, plus the three invariants that
 * matter more than any individual row.
 *
 * <p>This decides whether an operator command deletes a player's only record of
 * owning a ship, or stamps an owner onto a block that can never be broken. Both
 * are unrecoverable, so the table is exhaustive rather than representative -
 * the same standard ReissueRuleTest holds.
 */
class CoreAuditRuleTest {

    private static Verdict decide(boolean verifiable, boolean stands, Owner owner, boolean vessel) {
        return CoreAuditRule.decide(verifiable, stands, owner, vessel);
    }

    private static List<Object[]> everyRow() {
        List<Object[]> rows = new ArrayList<>();
        for (boolean verifiable : new boolean[]{false, true}) {
            for (boolean stands : new boolean[]{false, true}) {
                for (Owner owner : Owner.values()) {
                    for (boolean vessel : new boolean[]{false, true}) {
                        rows.add(new Object[]{verifiable, stands, owner, vessel});
                    }
                }
            }
        }
        return rows;
    }

    // --- No core seen. --------------------------------------------------------

    @Test void unreadablePositionProvesNothing() {
        for (Owner owner : Owner.values()) {
            assertEquals(Verdict.UNKNOWN, decide(false, false, owner, false));
            assertEquals(Verdict.UNKNOWN, decide(false, false, owner, true));
        }
    }

    @Test void readablePositionWithNoCoreIsAStaleRecord() {
        for (Owner owner : Owner.values()) {
            assertEquals(Verdict.STALE_RECORD, decide(true, false, owner, false));
            assertEquals(Verdict.STALE_RECORD, decide(true, false, owner, true));
        }
    }

    // --- A core is standing. --------------------------------------------------

    @Test void someoneElsesCoreIsAConflictWhateverTheVesselSays() {
        assertEquals(Verdict.OWNER_CONFLICT, decide(true, true, Owner.OTHER, true));
        assertEquals(Verdict.OWNER_CONFLICT, decide(true, true, Owner.OTHER, false));
    }

    @Test void anUnboundCoreIsUnclaimedWhateverTheVesselSays() {
        assertEquals(Verdict.UNCLAIMED_CORE, decide(true, true, Owner.NONE, true));
        assertEquals(Verdict.UNCLAIMED_CORE, decide(true, true, Owner.NONE, false));
    }

    @Test void sameOwnerDifferentVesselIsDrift() {
        assertEquals(Verdict.VESSEL_DRIFT, decide(true, true, Owner.SAME, false));
    }

    @Test void sameOwnerSameVesselAgrees() {
        assertEquals(Verdict.AGREES, decide(true, true, Owner.SAME, true));
    }

    // --- The invariants. ------------------------------------------------------

    /**
     * A contested core is never re-stamped. Whichever way it were resolved
     * would take a ship off one of two players, and no automatic reading of
     * four booleans is entitled to make that call.
     */
    @Test void ownerConflictIsNeverRepairable() {
        for (Object[] row : everyRow()) {
            Verdict v = decide((boolean) row[0], (boolean) row[1], (Owner) row[2], (boolean) row[3]);
            if (v == Verdict.OWNER_CONFLICT) {
                assertFalse(v.repairable(),
                        "a contested core must never be repaired automatically");
            }
        }
    }

    /**
     * Nothing is ever repaired on a position that could not be read. This is
     * the same distinction ReissueRule turns on, and the reason a background
     * sweep was refused in favour of a command: an unloaded ship has not
     * stopped existing, and forgetting its record would lock its owner out.
     */
    @Test void nothingIsRepairedWithoutAReadablePosition() {
        for (Object[] row : everyRow()) {
            boolean verifiable = (boolean) row[0];
            boolean stands = (boolean) row[1];
            Verdict v = decide(verifiable, stands, (Owner) row[2], (boolean) row[3]);
            if (!verifiable && !stands) {
                assertEquals(Verdict.UNKNOWN, v);
                assertFalse(v.repairable());
            }
        }
    }

    /** Every combination lands on exactly one verdict, and none is missed. */
    @Test void theTableIsTotalAndEveryVerdictIsReachable() {
        List<Verdict> seen = new ArrayList<>();
        for (Object[] row : everyRow()) {
            Verdict v = decide((boolean) row[0], (boolean) row[1], (Owner) row[2], (boolean) row[3]);
            assertNotNull(v);
            if (!seen.contains(v)) seen.add(v);
        }
        assertEquals(24, everyRow().size(), "2 x 2 x 3 x 2 inputs");
        for (Verdict v : Verdict.values()) {
            assertTrue(seen.contains(v), v + " is unreachable - it is dead code or a missing branch");
        }
    }

    @Test void onlyAgreesIsNotAFinding() {
        for (Verdict v : Verdict.values()) {
            assertEquals(v != Verdict.AGREES, v.isFinding());
        }
    }
}
