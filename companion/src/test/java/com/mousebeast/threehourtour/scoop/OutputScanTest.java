package com.mousebeast.threehourtour.scoop;

import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OutputScanTest {

    @Test
    void theFrontFaceIsNeverAnOutputTarget() {
        // The whole point of the facing: a container in front of the scoop is
        // decoration, not a hopper. If this ever fails, the block quietly
        // starts feeding whatever the player parked in front of it.
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            assertFalse(OutputScan.targets(facing).contains(facing),
                    facing + " must not be an output target when the scoop faces it");
        }
    }

    @Test
    void everyFacingLeavesExactlyFiveTargets() {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            assertEquals(5, OutputScan.targets(facing).size(), "facing " + facing);
        }
    }

    @Test
    void downIsTriedFirstForEveryFacingTheBlockCanHave() {
        // Migration property, not an aesthetic one: every scoop installed
        // during L1-P5 has its chest directly below, and the OUTPUT side of
        // those installations must keep working unchanged after this plan.
        // The mesh side does not migrate for free - see OutputScan's class
        // javadoc: an old scoop loads with an empty mesh slot and produces
        // nothing until the mesh is moved into the block by hand, and its
        // old mesh chest above becomes a second output target once that
        // happens.
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            assertEquals(Direction.DOWN, OutputScan.targets(facing).get(0), "facing " + facing);
        }
    }

    @Test
    void theOrderIsExactlyThis() {
        assertEquals(List.of(Direction.DOWN, Direction.UP, Direction.EAST,
                        Direction.SOUTH, Direction.WEST),
                OutputScan.targets(Direction.NORTH));
        assertEquals(List.of(Direction.DOWN, Direction.UP, Direction.NORTH,
                        Direction.SOUTH, Direction.WEST),
                OutputScan.targets(Direction.EAST));
    }

    @Test
    void aNullFacingFallsBackToAllSixRatherThanThrowing() {
        // A block state missing its property is a corrupt-world case, not a
        // crash case. Answer with every face and let the caller find a home.
        assertEquals(6, OutputScan.targets(null).size());
        assertEquals(Direction.DOWN, OutputScan.targets(null).get(0));
    }

    @Test
    void theReturnedListCannotBeMutatedByACaller() {
        assertThrows(UnsupportedOperationException.class,
                () -> OutputScan.targets(Direction.NORTH).add(Direction.NORTH));
    }

    @Test
    void aVerticalFacingStillExcludesItsOwnFace() {
        // Unreachable in practice - the block uses HORIZONTAL_FACING, whose
        // values are the four horizontals - but the map is built over
        // Direction.values(), so these entries exist and should be correct
        // rather than accidental. Front-exclusion wins over DOWN-first here:
        // when the block faces down, DOWN *is* the front.
        assertFalse(OutputScan.targets(Direction.DOWN).contains(Direction.DOWN));
        assertEquals(Direction.UP, OutputScan.targets(Direction.DOWN).get(0));
        assertEquals(5, OutputScan.targets(Direction.DOWN).size());

        assertFalse(OutputScan.targets(Direction.UP).contains(Direction.UP));
        assertEquals(Direction.DOWN, OutputScan.targets(Direction.UP).get(0));
        assertEquals(5, OutputScan.targets(Direction.UP).size());
    }
}
