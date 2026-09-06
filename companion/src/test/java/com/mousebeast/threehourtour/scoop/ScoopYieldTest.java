package com.mousebeast.threehourtour.scoop;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ScoopYieldTest {

    @Test
    void mooredScoopNeverProgressesAndNeverEmits() {
        // The whole movement gate lives or dies here (L1-23).
        ScoopYield.Step s = ScoopYield.tick(0, false, MeshTier.TWINE);
        assertEquals(0, s.progress());
        assertFalse(s.emit());
    }

    @Test
    void mooredScoopDoesNotBankProgressItAlreadyHad() {
        // Progress is preserved across a stop, but does not grow. Resetting it
        // would punish the stop-and-work loop the pack is built around.
        ScoopYield.Step s = ScoopYield.tick(50, false, MeshTier.TWINE);
        assertEquals(50, s.progress());
        assertFalse(s.emit());
    }

    @Test
    void underWayScoopAccumulatesOneTickAtATime() {
        ScoopYield.Step s = ScoopYield.tick(0, true, MeshTier.TWINE);
        assertEquals(1, s.progress());
        assertFalse(s.emit());
    }

    @Test
    void emittingReportsThePreDepositProgressNotAReset() {
        // tick() cannot know whether the deposit will succeed, so it must not
        // claim the reset happened - that decision, and the 0, belongs to
        // ScoopCommit alone (see ScoopYield.Step javadoc).
        int period = MeshTier.TWINE.ticksPerYield();
        ScoopYield.Step s = ScoopYield.tick(period - 1, true, MeshTier.TWINE);
        assertTrue(s.emit());
        assertEquals(period - 1, s.progress());
    }

    @Test
    void aBetterMeshEmitsSoonerFromTheSameProgress() {
        int p = MeshTier.PRISMARINE.ticksPerYield() - 1;
        assertTrue(ScoopYield.tick(p, true, MeshTier.PRISMARINE).emit());
        assertFalse(ScoopYield.tick(p, true, MeshTier.TWINE).emit());
    }

    @Test
    void progressBeyondThresholdStillEmitsRatherThanStalling() {
        // Can happen if a mesh is swapped for a slower one mid-voyage.
        ScoopYield.Step s = ScoopYield.tick(5000, true, MeshTier.TWINE);
        assertTrue(s.emit());
        assertEquals(5000, s.progress());
    }

    @Test
    void noMeshMeansNoProgressAndNoEmission() {
        ScoopYield.Step s = ScoopYield.tick(10, true, null);
        assertEquals(10, s.progress());
        assertFalse(s.emit());
    }

    @Test
    void negativeProgressIsClampedRatherThanTrusted() {
        // Was `assertTrue(s.progress() >= 0)`, which passes for any
        // non-negative value and so would not catch a clamp that landed on
        // the wrong number. Clamped to 0, then advanced one tick under way.
        ScoopYield.Step s = ScoopYield.tick(-5, true, MeshTier.TWINE);
        assertEquals(1, s.progress());
    }
}
