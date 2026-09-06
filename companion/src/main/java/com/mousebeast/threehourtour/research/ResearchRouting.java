package com.mousebeast.threehourtour.research;

import java.util.List;
import java.util.OptionalInt;

/**
 * Chooses which of a research node's tasks a arriving stack should pay into.
 *
 * Deliberately knows nothing about FTB Quests or Minecraft. The terminal
 * accepts six research pack types through one pipe, so *something* has to
 * decide where a given stack lands, and that decision is the only part of the
 * terminal worth unit-testing. Wrapping it in {@link Sink} keeps the rule
 * testable without booting a quest file: the adapter in
 * {@code QuestBridge} is what knows that a Sink is really an ItemTask.
 *
 * The rule is first-match-by-task-order, which matters when a node happens to
 * carry two tasks that accept the same pack. Order is stable across reloads
 * because it comes from the quest's own task list, so a player who watches a
 * pack land in the first of two identical tasks sees the same thing tomorrow.
 * Anything cleverer (fill the emptiest, spread evenly) would be less
 * predictable for no gain - the node is not complete until every task is, so
 * the order packs arrive in cannot change the total paid.
 */
public final class ResearchRouting {

    private ResearchRouting() {}

    /** One task on the targeted node, as far as routing is concerned. */
    public interface Sink {
        /** Does this task accept this kind of item at all, right now? */
        boolean accepts();

        /** How many more items it will take before it is satisfied. */
        long remaining();
    }

    /**
     * The index of the sink that should receive the stack, or empty if none
     * will take it.
     *
     * Empty is a normal outcome, not an error: it is what happens when a
     * player pipes a pack the current node does not want. The caller must
     * return the stack to the inserter rather than voiding it, which is also
     * what FTB Quests' own task screen does.
     */
    public static OptionalInt choose(List<? extends Sink> sinks) {
        if (sinks == null) return OptionalInt.empty();
        for (int i = 0; i < sinks.size(); i++) {
            Sink s = sinks.get(i);
            if (s != null && s.accepts() && s.remaining() > 0) {
                return OptionalInt.of(i);
            }
        }
        return OptionalInt.empty();
    }

    /**
     * How much of a stack of {@code count} the chosen sink can actually take.
     *
     * Split out from {@link #choose} because a partially-accepted stack is the
     * normal case at the end of a node: 40 packs arrive, 12 are still owed,
     * 12 are consumed and 28 must go back up the pipe.
     */
    public static int acceptedAmount(Sink sink, int count) {
        if (sink == null || count <= 0 || !sink.accepts()) return 0;
        long room = sink.remaining();
        if (room <= 0) return 0;
        return (int) Math.min(count, room);
    }
}
