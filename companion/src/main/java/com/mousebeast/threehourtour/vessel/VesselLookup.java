package com.mousebeast.threehourtour.vessel;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

/**
 * The on-ship predicate.
 *
 * A Sable vessel is NEITHER a dimension NOR a Level. Measured on a live
 * vessel: a block on a ship and a block on shore both report dimension
 * minecraft:overworld and level ServerLevel[world]. SubLevel does not extend
 * Level - it holds one, and getLevel() returns the PARENT.
 *
 * So this is a POSITION lookup. Any implementation comparing dimensions or
 * level identity returns the same answer everywhere, silently.
 */
public final class VesselLookup {
    private VesselLookup() {}

    /** The vessel at this position, or null if the position is not on one. */
    public static VesselRef vesselAt(Level level, BlockPos pos) {
        SubLevel sub = subLevelAt(level, pos);
        if (sub == null) return null;

        // getUniqueId() is a plain un-annotated Sable getter with no documented
        // non-null guarantee. This method runs on every block placement for every
        // player (via isOnVessel) - it must never throw, so a null id degrades to
        // "not on a vessel" instead of an NPE that would break placement server-wide.
        java.util.UUID id = sub.getUniqueId();
        if (id == null) return null;
        return new VesselRef(id, sub.getName());
    }

    public static boolean isOnVessel(Level level, BlockPos pos) {
        return vesselAt(level, pos) != null;
    }

    /**
     * The canonical Sable lookup chain: plot position to sub-level, or null if
     * the position is not on a vessel.
     *
     * This is THE lookup chain for this repo - the one place known to do it
     * correctly (see the class javadoc above). This extraction exists
     * specifically to prevent copies of it drifting out of sync; callers
     * elsewhere in the codebase must call this method rather than re-write
     * their own version of it.
     */
    public static SubLevel subLevelAt(Level level, BlockPos pos) {
        if (level == null || pos == null) return null;

        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) return null;

        ChunkPos chunk = new ChunkPos(pos);
        if (!container.inBounds(chunk)) return null;

        LevelPlot plot = container.getPlot(chunk);
        if (plot == null) return null;

        SubLevel sub = plot.getSubLevel();
        return (sub == null || sub.isRemoved()) ? null : sub;
    }
}
