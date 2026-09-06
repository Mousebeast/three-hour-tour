package com.mousebeast.threehourtour.research;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.*;

class ResearchRoutingTest {

    /** A task that accepts (or does not) and has some room left. */
    private record Fake(boolean accepts, long remaining) implements ResearchRouting.Sink {}

    @Test
    @DisplayName("routes to the only task that accepts the item")
    void routesToTheAcceptingTask() {
        List<Fake> sinks = List.of(
                new Fake(false, 100),
                new Fake(true, 100),
                new Fake(false, 100));
        assertEquals(OptionalInt.of(1), ResearchRouting.choose(sinks));
    }

    @Test
    @DisplayName("skips a task that accepts but is already satisfied")
    void skipsSatisfiedTasks() {
        List<Fake> sinks = List.of(
                new Fake(true, 0),
                new Fake(true, 50));
        assertEquals(OptionalInt.of(1), ResearchRouting.choose(sinks),
                "a completed task must not swallow packs the next task still needs");
    }

    @Test
    @DisplayName("returns empty when the node wants nothing of this kind")
    void emptyWhenNothingAccepts() {
        assertEquals(OptionalInt.empty(),
                ResearchRouting.choose(List.of(new Fake(false, 100), new Fake(false, 20))));
    }

    @Test
    @DisplayName("empty and null sink lists are survivable, not exceptions")
    void degenerateInputs() {
        assertEquals(OptionalInt.empty(), ResearchRouting.choose(List.of()));
        assertEquals(OptionalInt.empty(), ResearchRouting.choose(null));
    }

    @Test
    @DisplayName("first match wins when two tasks both accept")
    void firstMatchWinsAndIsStable() {
        List<Fake> sinks = List.of(new Fake(true, 10), new Fake(true, 10));
        assertEquals(OptionalInt.of(0), ResearchRouting.choose(sinks));
        // Stability is the point: same list, same answer, every time.
        assertEquals(OptionalInt.of(0), ResearchRouting.choose(sinks));
    }

    @Test
    @DisplayName("takes the whole stack when there is room for it")
    void acceptsWholeStack() {
        assertEquals(32, ResearchRouting.acceptedAmount(new Fake(true, 100), 32));
    }

    @Test
    @DisplayName("takes only what is still owed, leaving the rest for the pipe")
    void acceptsPartialStack() {
        assertEquals(12, ResearchRouting.acceptedAmount(new Fake(true, 12), 40),
                "the other 28 must go back up the pipe, not be voided");
    }

    @Test
    @DisplayName("takes nothing from a task that is full, refusing, or given an empty stack")
    void acceptsNothing() {
        assertEquals(0, ResearchRouting.acceptedAmount(new Fake(true, 0), 40));
        assertEquals(0, ResearchRouting.acceptedAmount(new Fake(false, 40), 40));
        assertEquals(0, ResearchRouting.acceptedAmount(new Fake(true, 40), 0));
        assertEquals(0, ResearchRouting.acceptedAmount(null, 40));
    }

    @Test
    @DisplayName("a remaining count larger than a stack never overflows the int return")
    void hugeRemainingDoesNotOverflow() {
        assertEquals(64, ResearchRouting.acceptedAmount(new Fake(true, Long.MAX_VALUE), 64));
    }
}
