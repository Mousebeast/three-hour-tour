package com.mousebeast.threehourtour.vessel;

import com.mousebeast.threehourtour.scoop.UnderWayRule;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * Vessel movement state, for scripts.
 *
 * A one-shot query, so it has no hysteresis state to carry - it answers with
 * the START threshold. The scoop's own per-tick state (SeaScoopBlockEntity)
 * IS hysteretic; the two can disagree for a tick or two around the threshold,
 * which is correct and not worth reconciling.
 *
 * COORDINATE SPACE, read this before calling anything below: {@link #speedAt}
 * and {@link #isUnderWay} take a PLOT position - the position of a block as
 * it is actually stored on the vessel, the same space SeaScoopBlockEntity
 * works in. Handing either one a player's or a block's ordinary world
 * position returns false/0.0 every time, silently - this codebase's
 * documented worst failure mode (lessons-learned.md, 2026-08-23).
 *
 * A script almost never has a plot position lying around; it has a world
 * position (a player's location, a block a script is looking at). For that,
 * use {@link #isUnderWayAt} / {@link #speedAtWorld} instead - they take a
 * world position and do the plot conversion (via {@link VesselPosition#toPlot})
 * internally. The plot-space methods stay because SeaScoopBlockEntity's own
 * semantics are plot-space and that call site should not pay for a
 * pointless world round-trip.
 */
public final class VesselState {

    private VesselState() {}

    /** Plot-space. See the class javadoc - a script almost certainly wants {@link #speedAtWorld} instead. */
    public static double speedAt(Level level, BlockPos plotPos) {
        SubLevel sub = VesselLookup.subLevelAt(level, plotPos);
        return VesselMotion.speedOf(sub);
    }

    /** Plot-space. See the class javadoc - a script almost certainly wants {@link #isUnderWayAt} instead. */
    public static boolean isUnderWay(Level level, BlockPos plotPos) {
        return UnderWayRule.next(speedAt(level, plotPos), false);
    }

    /**
     * World-space. The entry point a script should reach for: pass an
     * ordinary world position (a player's location, a block's position in
     * the world they can see) and this converts it to plot space internally
     * via {@link VesselPosition#toPlot} before answering. Returns 0.0 for a
     * null level/pos or a position that isn't currently on any resolvable
     * vessel - never throws into Rhino.
     */
    public static double speedAtWorld(Level level, BlockPos worldPos) {
        if (level == null || worldPos == null) {
            return 0.0;
        }
        Optional<BlockPos> plotPos = VesselPosition.toPlot(level, Vec3.atCenterOf(worldPos));
        return plotPos.map(pos -> speedAt(level, pos)).orElse(0.0);
    }

    /**
     * World-space. The entry point a script should reach for: pass an
     * ordinary world position (a player's location, a block's position in
     * the world they can see) and this converts it to plot space internally
     * via {@link VesselPosition#toPlot} before answering. Returns false for a
     * null level/pos or a position that isn't currently on any resolvable
     * vessel - never throws into Rhino.
     */
    public static boolean isUnderWayAt(Level level, BlockPos worldPos) {
        if (level == null || worldPos == null) {
            return false;
        }
        Optional<BlockPos> plotPos = VesselPosition.toPlot(level, Vec3.atCenterOf(worldPos));
        return plotPos.map(pos -> isUnderWay(level, pos)).orElse(false);
    }
}
