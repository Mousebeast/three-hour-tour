package com.mousebeast.threehourtour.vessel;

import java.util.Objects;
import java.util.UUID;

/**
 * A reference to a Sable vessel, deliberately holding no Sable types so
 * callers never depend on Sable's API surface directly.
 *
 * Equality is by id alone: a vessel keeps its identity across renames.
 */
public final class VesselRef {
    private final UUID id;
    private final String name;

    public VesselRef(UUID id, String name) {
        this.id = Objects.requireNonNull(id, "vessel id");
        this.name = name == null ? "" : name;
    }

    public UUID id() { return id; }
    public String name() { return name; }

    @Override public boolean equals(Object o) {
        return o instanceof VesselRef other && id.equals(other.id);
    }
    @Override public int hashCode() { return id.hashCode(); }
    @Override public String toString() {
        return "VesselRef[" + id + (name.isEmpty() ? "" : " '" + name + "'") + "]";
    }
}
