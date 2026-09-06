package com.mousebeast.threehourtour.scoop;

import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Which neighbours the scoop will try to deposit into, and in what order.
 *
 * Pure, so the one rule that matters - never the front face - is a unit test
 * instead of an in-game observation nobody will repeat.
 *
 * DOWN is deliberately first. Every scoop installed under L1-P5 has its output
 * chest directly below it, and putting DOWN at the head of the list means the
 * OUTPUT side of those installations behaves identically after this plan.
 *
 * The mesh side does not migrate for free. L1-P5's arrangement read the mesh
 * from a container placed above the scoop; this plan moved the mesh into the
 * block's own slot (see MeshSlot) and reads nothing from neighbours anymore.
 * A scoop built the old way loads with FACING defaulted to NORTH (no saved
 * blockstate property survives a plan that didn't have one) and an empty
 * mesh slot (nothing populates it from the old mesh chest), so
 * installedTier() is null and the scoop produces nothing until a player
 * physically moves the mesh out of that chest and into the block. Worse,
 * with FACING == NORTH, targets(NORTH) puts UP second in the scan order -
 * so that same old mesh chest is now a live output target, and once the
 * chest below fills, yields spill into it and into whatever else happens to
 * sit beside the scoop.
 *
 * The lists are precomputed and immutable: this runs on the emit path of a
 * block entity that ticks 20 times a second, and there is no reason to
 * allocate a list per tick per scoop.
 */
public final class OutputScan {

    /** Front excluded; DOWN first, then UP, then the horizontals clockwise from north. */
    private static final List<Direction> ALL = List.of(
            Direction.DOWN, Direction.UP,
            Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST);

    private static final Map<Direction, List<Direction>> BY_FACING =
            new EnumMap<>(Direction.class);

    static {
        for (Direction facing : Direction.values()) {
            List<Direction> targets = new ArrayList<>(ALL);
            targets.remove(facing);
            BY_FACING.put(facing, List.copyOf(targets));
        }
    }

    private OutputScan() {}

    /**
     * @param facing the block's front face, or null if the state somehow has none
     * @return the faces to try, in order; never null, never contains the front
     *
     * The front face is never a target (the invariant the facing exists to enforce).
     * DOWN is first whenever it is a target (migration property: old installations
     * have their chest below). When the block faces DOWN or UP, those entries in
     * the map still follow the same front-exclusion rule — DOWN is excluded when
     * facing DOWN (making UP first), UP is excluded when facing UP (making DOWN first).
     * These vertical entries are unreachable in practice — the block uses
     * BlockStateProperties.HORIZONTAL_FACING, whose values are the four horizontals —
     * but the map is built over Direction.values(), so they exist and behave correctly.
     */
    public static List<Direction> targets(Direction facing) {
        if (facing == null) {
            return ALL;
        }
        return BY_FACING.get(facing);
    }
}
