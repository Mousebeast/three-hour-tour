package com.mousebeast.threehourtour.core;

/**
 * Whether an assembly may become a ship core.
 *
 * <p>Pure by design, and for the same reason as {@link ReissueRule}: this is
 * the only other place in the mod that can mint a second indestructible core,
 * and a duplicate is permanent. Nothing in this file may reference Minecraft or
 * Sable.
 *
 * <p><b>Until 2026-09-04 there was no rule here at all.</b>
 * {@code ActivationWatcher} found the assembler, swapped it for a core and
 * called {@code ShipCoreIndex.put(...)}, which is a plain overwrite keyed on
 * the owner. Assembling a second raft therefore produced a second core *and*
 * made the first one invisible: the index forgot it, so `/3ht core status`
 * reported one core and `/3ht core reissue` could not see the duplicate it
 * would have refused. The one-vessel pillar was enforced everywhere except the
 * place a vessel is actually created — the recipe was removed, assemblers could
 * not be placed aboard a vessel, disassembly was locked to the primary
 * assembler — and none of that matters if a second assembler reaching a lever
 * is simply honoured.
 *
 * <p>Gating here rather than at each route in is deliberate. Creative mode, an
 * operator `/give`, a mis-fired re-issue and a second raft are four ways to
 * hold an assembler and one way to use it.
 *
 * <p>The three inputs mirror {@code ReissueRule} exactly, because the same
 * distinction decides both: {@code coreStillStanding} is a positive
 * observation, false both when the position was read and held no core and when
 * the position could not be read at all. {@code verifiable} separates "proved
 * gone" from "could not look" — a core aboard an unloaded vessel twenty million
 * blocks out is normally the latter.
 */
public final class ActivationRule {
    private ActivationRule() {}

    public enum Decision {
        /**
         * No record, or a record whose core is proved gone. Create the core.
         */
        ALLOW,
        /**
         * A core is still standing for this player. Refuse: leave the
         * assembler where it is and tell them they get one ship.
         */
        REFUSE_HAS_CORE,
        /**
         * A record exists and its position cannot be read. Refuse, and route
         * to an operator. Guessing here is exactly how a player ends up with
         * two permanent cores, and `/3ht core reissue ... here` exists for the
         * save that really is wrong.
         */
        REFUSE_UNVERIFIABLE;

        public boolean allowed() {
            return this == ALLOW;
        }
    }

    /**
     * The whole decision, in order of precedence.
     *
     * <p>{@code coreStillStanding} is tested first and alone, so no other
     * combination can reach an ALLOW while a real core is standing. That
     * ordering is the invariant; ActivationRuleTest asserts it over the full
     * table of eight.
     */
    public static Decision decide(boolean recordExists, boolean verifiable,
                                  boolean coreStillStanding) {
        if (coreStillStanding) return Decision.REFUSE_HAS_CORE;
        if (!recordExists) return Decision.ALLOW;
        if (!verifiable) return Decision.REFUSE_UNVERIFIABLE;
        return Decision.ALLOW;
    }
}
