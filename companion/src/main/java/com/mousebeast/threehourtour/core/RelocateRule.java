package com.mousebeast.threehourtour.core;

import java.util.Objects;
import java.util.UUID;

/**
 * Whether a core may move from where it is to where the player clicked.
 *
 * Same vessel only. A core that could cross to another hull would be a
 * carryable identity tag with extra steps - which is the exploit the design
 * closes by never making it an item (spec §3).
 */
public final class RelocateRule {
    private RelocateRule() {}

    public enum Result {
        OK("message.threehourtour.core_set_down"),
        NOT_ABOARD("message.threehourtour.core_not_aboard"),
        DIFFERENT_VESSEL("message.threehourtour.core_different_vessel"),
        OCCUPIED("message.threehourtour.core_occupied");

        private final String key;
        Result(String key) { this.key = key; }

        /**
         * The translation key for what to tell the player. Every result has
         * one, refusals included: a refusal a player cannot read is a bug
         * report waiting to happen. These were literal English until
         * 2026-09-02, which made them the only player-facing strings in the
         * mod that no lang file could reach.
         */
        public String reason() { return key; }
    }

    public static Result check(UUID coreVessel, UUID targetVessel, boolean targetIsReplaceable) {
        if (coreVessel == null) return Result.NOT_ABOARD;
        if (!Objects.equals(coreVessel, targetVessel)) return Result.DIFFERENT_VESSEL;
        if (!targetIsReplaceable) return Result.OCCUPIED;
        return Result.OK;
    }
}
