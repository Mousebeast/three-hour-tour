package com.mousebeast.threehourtour.scoop;

/**
 * When does the scoop pay out?
 *
 * Pure, so "does it pay out at anchor?" (L1-23, the movement gate) is answered
 * by a unit test instead of by watching a chest for a minute.
 *
 * Progress is PRESERVED across a stop rather than reset. The pack's loop is
 * sail, stop, work the hold, sail again; wiping accumulated progress every
 * time the player moors would punish exactly the behaviour the design wants.
 */
public final class ScoopYield {

    /**
     * @param progress ticks banked so far. On an emit tick this is the
     *                 progress AS IT STOOD before the deposit was attempted,
     *                 not the post-reset value - tick() has no way to know
     *                 whether the deposit will actually succeed. ScoopCommit
     *                 is what decides the real post-tick progress: 0 if the
     *                 deposit succeeded, this value unchanged if it didn't.
     *                 Treating this field as "the new progress" on an emit
     *                 tick is wrong; it is only ever correct once ScoopCommit
     *                 has resolved it.
     * @param emit     whether to pull a yield now.
     */
    public record Step(int progress, boolean emit) {}

    private ScoopYield() {}

    public static Step tick(int progress, boolean underWay, MeshTier tier) {
        int current = Math.max(0, progress);

        if (tier == null || !underWay) {
            return new Step(current, false);
        }

        int next = current + 1;
        if (next >= tier.ticksPerYield()) {
            // The reset-to-0 happens in ScoopCommit, and only when the
            // deposit actually succeeds. Reporting 0 here regardless of
            // outcome would make this field a lie on a failed deposit -
            // exactly the bug this record's javadoc now calls out.
            return new Step(current, true);
        }
        return new Step(next, false);
    }
}
