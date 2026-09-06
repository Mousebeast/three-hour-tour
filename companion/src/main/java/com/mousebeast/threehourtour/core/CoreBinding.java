package com.mousebeast.threehourtour.core;

import java.util.Objects;
import java.util.UUID;

/**
 * Who owns a ship core, and which Sable vessel that core is currently inside.
 *
 * The vessel id is a CACHE, not the identity. Sable gives the original UUID to
 * the largest fragment of a split and mints new ones for the rest, so a core
 * that never updated this field would end up naming the half of the ship it is
 * not on. See the spec, §3.
 *
 * Holds no Minecraft and no Sable types, so it is testable without a game.
 */
public record CoreBinding(UUID owner, UUID vesselId) {

    public CoreBinding {
        Objects.requireNonNull(owner, "core owner");
    }

    /** False when the vessel is gone and the core has been grounded. */
    public boolean isBound() {
        return vesselId != null;
    }

    public CoreBinding boundTo(UUID newVesselId) {
        return new CoreBinding(owner, newVesselId);
    }

    public CoreBinding grounded() {
        return new CoreBinding(owner, null);
    }
}
