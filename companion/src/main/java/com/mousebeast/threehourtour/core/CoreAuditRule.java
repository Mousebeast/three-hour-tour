package com.mousebeast.threehourtour.core;

/**
 * What one index record and the world it claims to describe have to say about
 * each other.
 *
 * Pure by design, for the same reason {@link ReissueRule} and
 * {@link ActivationRule} are: this decides whether an operator command deletes
 * a record or stamps ownership onto an indestructible block, and both are
 * unrecoverable if decided wrongly. Nothing in this file may reference
 * Minecraft or Sable.
 *
 * <p><b>The index is a cache. The core block is the truth.</b> The block
 * carries its own {@link CoreBinding}, survives independently of the saved
 * data, and is what a player is actually standing on. The index exists so
 * "does this player have a core?" can be answered without finding the block,
 * and the activation gate reads only the index - so when the two disagree, the
 * index is what is wrong, and the player is locked out of assembling a ship
 * they can see.
 *
 * The four inputs are the only facts the decision turns on:
 *   verifiable   - the recorded position can actually be read right now
 *                  (its chunk is loaded, or its vessel plot resolves)
 *   coreStands   - a ship core block was SEEN at the recorded position
 *   owner        - who the block itself says it belongs to
 *   vesselAgrees - the record and the block name the same vessel
 *
 * {@code coreStands} is a positive observation and never an inference: it is
 * false both when the position was read and held no core AND when it could not
 * be read at all. {@code verifiable} is what separates "proved gone" from
 * "could not look", and nothing here repairs anything on the second.
 */
public final class CoreAuditRule {
    private CoreAuditRule() {}

    /** Who the core block says it belongs to, relative to the record. */
    public enum Owner {
        /** The block has no binding: placed but never claimed. */
        NONE,
        /** The block names the same player the record does. */
        SAME,
        /** The block names somebody else. */
        OTHER
    }

    public enum Verdict {
        /**
         * The position could not be read. Proves nothing in either direction,
         * so nothing is touched - an unloaded ship has not stopped existing.
         */
        UNKNOWN,
        /**
         * The position was read and held no core. The record is a ghost, and
         * it is what the activation gate is refusing on. Repair: forget it.
         */
        STALE_RECORD,
        /**
         * A core stands there carrying no binding at all. The index is the only
         * surviving evidence of who it belongs to. Repair: stamp the record's
         * owner onto the block.
         */
        UNCLAIMED_CORE,
        /**
         * A core stands there and names somebody else. Two claims on one
         * indestructible block. <b>Never repaired automatically</b> - whichever
         * way it were resolved would take a ship off one of them.
         */
        OWNER_CONFLICT,
        /**
         * Right core, right owner, wrong ship. Sable hands the original id to
         * the largest fragment of a split and mints new ones for the rest, so
         * this is ordinary drift rather than corruption. Repair: take the
         * block's id, because the block is the truth.
         */
        VESSEL_DRIFT,
        /** The record and the world agree. */
        AGREES;

        /** True for the verdicts {@code /3ht core audit fix} may act on. */
        public boolean repairable() {
            return this == STALE_RECORD || this == UNCLAIMED_CORE || this == VESSEL_DRIFT;
        }

        /** True for anything an operator needs to read. */
        public boolean isFinding() {
            return this != AGREES;
        }
    }

    /**
     * The whole decision, in order of precedence.
     *
     * <p>Reading order matters and is asserted over the full table by
     * CoreAuditRuleTest. {@code coreStands} is settled before anything else so
     * that no combination of the remaining inputs can reach a repair while the
     * position is unreadable, and {@code OWNER_CONFLICT} is tested before the
     * two repairs so that a contested core can never be quietly re-stamped.
     */
    public static Verdict decide(boolean verifiable, boolean coreStands,
                                 Owner owner, boolean vesselAgrees) {
        if (!coreStands) return verifiable ? Verdict.STALE_RECORD : Verdict.UNKNOWN;
        if (owner == Owner.OTHER) return Verdict.OWNER_CONFLICT;
        if (owner == Owner.NONE) return Verdict.UNCLAIMED_CORE;
        if (!vesselAgrees) return Verdict.VESSEL_DRIFT;
        return Verdict.AGREES;
    }
}
