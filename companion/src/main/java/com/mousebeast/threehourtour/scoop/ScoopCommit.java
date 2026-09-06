package com.mousebeast.threehourtour.scoop;

/**
 * The commit rule for a scoop tick: when does banked progress actually move?
 *
 * Pure, so the single most important invariant in SeaScoopBlockEntity - spend
 * progress (and, via the caller, mesh durability) only when a deposit
 * actually happened - is a unit test instead of a GameTest that structurally
 * cannot reach it: underWay never goes true in a GameTest world, so
 * serverTick() never gets far enough to exercise this rule there (see
 * SeaScoopGameTest and the class javadoc on SeaScoopBlockEntity).
 *
 * This was the exact Task 6 bug: committing step.progress() before checking
 * whether emitYield() actually deposited anything meant a blocked
 * destination still consumed progress, silently destroying the yield instead
 * of backing up and retrying (spec §8).
 */
public final class ScoopCommit {

    private ScoopCommit() {}

    /**
     * @param currentProgress ticks banked before this tick's step was computed
     * @param step            this tick's ScoopYield.Step
     * @param emitted         whether emitYield() actually deposited the roll
     * @return the progress value to persist for this tick
     */
    public static int progressAfter(int currentProgress, ScoopYield.Step step, boolean emitted) {
        if (!step.emit()) {
            return step.progress();
        }
        if (emitted) {
            // The deposit actually happened - this is the ONLY place progress
            // is reset to 0. step.progress() on an emit tick is deliberately
            // the pre-deposit value, not 0 (see ScoopYield.Step javadoc), so
            // it must not be used here.
            return 0;
        }
        // A yield was rolled up (step.emit() == true) but nothing was
        // deposited - no output container, a full destination, or a
        // rejected insert. Nothing was spent, so nothing is committed: the
        // scoop must retry next tick with the same banked progress instead
        // of silently losing it. Regression guard for the Task 6 bug.
        return currentProgress;
    }
}
