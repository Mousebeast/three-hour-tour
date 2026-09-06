package com.mousebeast.threehourtour.scoop;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ScoopCommitTest {

    @Test
    void notEmittingCommitsTheIncrementedProgress() {
        ScoopYield.Step step = new ScoopYield.Step(7, false);
        assertEquals(7, ScoopCommit.progressAfter(6, step, false));
    }

    @Test
    void aSuccessfulDepositCommitsZeroRegardlessOfStepProgress() {
        // step.progress() on an emit tick is the pre-deposit value now (see
        // the whole-branch review fix), not a pre-baked 0 - ScoopCommit is
        // the only place that produces the reset, and only on success.
        ScoopYield.Step step = new ScoopYield.Step(199, true);
        assertEquals(0, ScoopCommit.progressAfter(199, step, true));
    }

    @Test
    void aFailedDepositLeavesBankedProgressUntouched() {
        // Regression guard for the Task 6 bug: committing step.progress()
        // before checking whether emitYield() actually deposited anything
        // meant a blocked destination still consumed progress, silently
        // destroying the yield instead of backing up and retrying (spec §8).
        // A failed deposit must not consume banked progress.
        //
        // step.progress() is deliberately given a DIFFERENT value than
        // currentProgress here (999 vs 199). Since the whole-branch review
        // fix, ScoopYield.tick() reports the pre-deposit progress on an emit
        // tick, which in real use equals currentProgress - so a version of
        // this method that wrongly returns step.progress() instead of
        // currentProgress would pass this test with matching values even
        // though it commits the wrong field. Divergent values are what make
        // this test still fail if that rule breaks.
        ScoopYield.Step step = new ScoopYield.Step(999, true);
        assertEquals(199, ScoopCommit.progressAfter(199, step, false));
    }
}
