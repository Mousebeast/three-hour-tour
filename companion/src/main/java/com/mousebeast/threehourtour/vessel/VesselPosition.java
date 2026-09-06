package com.mousebeast.threehourtour.vessel;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * Where a block that lives on a vessel actually is in the world (spec L1-10).
 *
 * Blocks on a Sable vessel are stored in a plot region about 20.4M blocks out.
 * The ship you can see is that plot rendered through the sub-level's pose, so a
 * stored BlockPos is NOT a place a player can stand. Anything that positions an
 * entity relative to a block on a ship - respawn, teleport, an admin command -
 * has to go through this.
 *
 * The transform is Sable's own: SubLevelHelper.popEntityLocal does
 * logicalPose().transformPosition(pos), and Pose3dc.transformPosition is
 * orientation * ((local - rotationPoint) * scale) + position. The plot offset
 * lives in rotationPoint, so raw plot coordinates are the correct input.
 */
public final class VesselPosition {
    private VesselPosition() {}

    /**
     * The world position of a plot position, or empty when the vessel cannot be
     * resolved right now - unloaded, removed, or not a vessel position at all.
     *
     * Empty is a real answer and callers must handle it. It must never be
     * substituted with the input position: that would place a player 20 million
     * blocks from the world, which the server will happily do.
     */
    public static Optional<Vec3> toWorld(Level level, BlockPos pos) {
        if (level == null || pos == null) return Optional.empty();

        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) return Optional.empty();

        ChunkPos chunk = new ChunkPos(pos);
        if (!container.inBounds(chunk)) return Optional.empty();

        LevelPlot plot = container.getPlot(chunk);
        if (plot == null) return Optional.empty();

        SubLevel sub = plot.getSubLevel();
        if (sub == null || sub.isRemoved()) return Optional.empty();

        // Bottom-centre of the block, so the returned point is where something
        // standing on that block would have its feet, not the block's corner.
        Vec3 local = Vec3.atBottomCenterOf(pos);
        Vec3 world = sub.logicalPose().transformPosition(local);
        if (!Double.isFinite(world.x) || !Double.isFinite(world.y) || !Double.isFinite(world.z)) {
            return Optional.empty();
        }
        return Optional.of(world);
    }

    /**
     * The plot position a world position falls inside, or empty when that
     * world position is not on any vessel right now.
     *
     * The exact inverse of {@link #toWorld}. It exists for the same reason
     * that method does, mirrored: a player standing on a ship's deck reads
     * ordinary world coordinates in F3, but the block underfoot lives in
     * Sable's plot space, ~20.4M blocks out. Anything that has to go from
     * "where the player is" to "which block that corresponds to on the ship" -
     * an admin command reading BlockPos.containing(source.getPosition()),
     * chiefly - has to go through this instead of using the player's raw
     * position as a plot position.
     *
     * Empty is a real answer: it means the position is not currently inside
     * any resolvable vessel plot (on land, vessel unloaded/removed, or the
     * pose is garbage). Callers must handle it rather than falling back to
     * treating the world position as a plot position.
     */
    public static Optional<BlockPos> toPlot(Level level, Vec3 worldPos) {
        if (level == null || worldPos == null) return Optional.empty();

        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) return Optional.empty();

        for (SubLevel sub : container.getAllSubLevels()) {
            if (sub == null || sub.isRemoved()) continue;

            Vec3 local = sub.logicalPose().transformPositionInverse(worldPos);
            if (local == null
                    || !Double.isFinite(local.x) || !Double.isFinite(local.y) || !Double.isFinite(local.z)) {
                continue;
            }

            LevelPlot plot = sub.getPlot();
            if (plot == null) continue;

            if (plot.contains(local)) {
                return Optional.of(BlockPos.containing(local));
            }
        }
        return Optional.empty();
    }
}
