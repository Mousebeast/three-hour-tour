package com.mousebeast.threehourtour.core;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;
import java.util.UUID;

/**
 * Where a player's core is, and which vessel it currently belongs to.
 *
 * The position is a WORLD position - for a core aboard a vessel that is a
 * position inside Sable's plot region, roughly 20.4M out, not a position you
 * could walk to. A null vessel id means grounded.
 *
 * lastWorldPos is the last place that core was OBSERVED in the world, through
 * the vessel's pose (see VesselPosition). It is a cache with no authority: the
 * live pose always wins. It exists so a player who dies far from an unloaded
 * ship still respawns on the deck instead of at world spawn. Null means the
 * core has never once been resolved.
 */
public record CoreRecord(UUID owner, ResourceLocation dimension, BlockPos pos,
                         UUID vesselId, Vec3 lastWorldPos) {

    public CoreRecord {
        Objects.requireNonNull(owner, "core owner");
        Objects.requireNonNull(dimension, "core dimension");
        Objects.requireNonNull(pos, "core pos");
    }

    /** Every existing call site: a new record has never been observed yet. */
    public CoreRecord(UUID owner, ResourceLocation dimension, BlockPos pos, UUID vesselId) {
        this(owner, dimension, pos, vesselId, null);
    }

    public boolean isBound() {
        return vesselId != null;
    }

    public boolean hasWorldPos() {
        return lastWorldPos != null;
    }

    public CoreRecord withWorldPos(Vec3 worldPos) {
        return new CoreRecord(owner, dimension, pos, vesselId, worldPos);
    }
}
