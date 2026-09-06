package com.mousebeast.threehourtour.core;

/**
 * Whether `/3ht core reissue` may place a core, and where.
 *
 * Pure by design. The re-issue command is the one place in the mod that can
 * mint a SECOND indestructible core for a player who already has one, and a
 * duplicate is permanent - there is no way to break a ship core. That decision
 * therefore has to be testable on its own, without a world, a level, or a Sable
 * vessel in the way. Nothing in this file may reference Minecraft or Sable.
 *
 * The four inputs are the only facts the decision turns on:
 *   recordExists      - the index still knows about a core for this player
 *   verifiable        - the recorded position can actually be read right now
 *                       (its chunk is loaded, or its vessel plot resolves)
 *   coreStillStanding - a ship core block was SEEN at the recorded position
 *   here              - the operator passed the `here` override
 *
 * {@code coreStillStanding} is a positive observation, never an inference: it
 * is false both when the position was read and held no core AND when the
 * position could not be read at all. That is what {@code verifiable} is for -
 * it separates "proved gone" from "could not look".
 */
public final class ReissueRule {
    private ReissueRule() {}

    /** What the command should do. Exactly two of these place a block. */
    public enum Decision {
        /**
         * A ship core is still standing at the recorded position. Refuse
         * unconditionally - this is the one-core pillar and even the `here`
         * override does not lift it.
         */
        REFUSE_HAS_CORE,
        /**
         * A record exists, its position cannot be read, and the operator did
         * not override. An unverifiable position proves nothing, and guessing
         * here is how a player ends up with two cores.
         */
        REFUSE_UNVERIFIABLE,
        /**
         * The recorded core is proved gone. Put a replacement back at the
         * recorded position - no coordinate guessing needed.
         */
        RESTORE,
        /**
         * Place at the operator's own position: either there is no record at
         * all (a coreless player) or the operator overrode with `here`,
         * discarding a recorded position that can never be recovered.
         */
        ISSUE_FRESH;

        /** True for the two decisions that write a ship core block into the world. */
        public boolean placesACore() {
            return this == RESTORE || this == ISSUE_FRESH;
        }
    }

    /**
     * The whole decision, in order of precedence.
     *
     * {@code coreStillStanding} is tested first and alone, so that no other
     * combination of inputs - `here` included - can ever reach a placement
     * while a real core is standing. That ordering is the invariant, not an
     * implementation detail; ReissueRuleTest asserts it over the full table.
     */
    public static Decision decide(boolean recordExists, boolean verifiable,
                                  boolean coreStillStanding, boolean here) {
        if (coreStillStanding) return Decision.REFUSE_HAS_CORE;
        if (!recordExists) return Decision.ISSUE_FRESH;
        if (here) return Decision.ISSUE_FRESH;
        if (!verifiable) return Decision.REFUSE_UNVERIFIABLE;
        return Decision.RESTORE;
    }
}
