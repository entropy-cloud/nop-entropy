package io.nop.stream.cep.nfa;

import org.junit.jupiter.api.Test;

import java.util.PriorityQueue;
import java.util.Queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class TestNFAState {

    private ComputationState makeState(String name, DeweyNumber version, long startTs) {
        return ComputationState.createState(name, null, version, startTs, -1L, null);
    }

    @Test
    void testEqualsWithNonEmptyPartialMatchesDoesNotThrow() {
        Queue<ComputationState> partial1 = new PriorityQueue<>(NFAState.COMPUTATION_STATE_COMPARATOR);
        partial1.add(makeState("start", new DeweyNumber(1), 100L));
        partial1.add(makeState("middle", DeweyNumber.fromString("1.0"), 200L));
        partial1.add(makeState("end", new DeweyNumber(2), 50L));

        Queue<ComputationState> partial2 = new PriorityQueue<>(NFAState.COMPUTATION_STATE_COMPARATOR);
        partial2.add(makeState("start", new DeweyNumber(1), 100L));
        partial2.add(makeState("middle", DeweyNumber.fromString("1.0"), 200L));
        partial2.add(makeState("end", new DeweyNumber(2), 50L));

        Queue<ComputationState> completed = new PriorityQueue<>(NFAState.COMPUTATION_STATE_COMPARATOR);
        NFAState state1 = new NFAState(partial1, completed);
        NFAState state2 = new NFAState(partial2, completed);

        assertEquals(state1, state2);
        assertEquals(state1.hashCode(), state2.hashCode());
    }

    @Test
    void testEqualsWithNonEmptyCompletedMatchesDoesNotThrow() {
        Queue<ComputationState> partial = new PriorityQueue<>(NFAState.COMPUTATION_STATE_COMPARATOR);
        Queue<ComputationState> completed1 = new PriorityQueue<>(NFAState.COMPUTATION_STATE_COMPARATOR);
        completed1.add(makeState("done", new DeweyNumber(3), 300L));

        Queue<ComputationState> completed2 = new PriorityQueue<>(NFAState.COMPUTATION_STATE_COMPARATOR);
        completed2.add(makeState("done", new DeweyNumber(3), 300L));

        NFAState state1 = new NFAState(partial, completed1);
        NFAState state2 = new NFAState(partial, completed2);

        assertEquals(state1, state2);
    }

    @Test
    void testNotEqualWhenMatchesDiffer() {
        Queue<ComputationState> partial1 = new PriorityQueue<>(NFAState.COMPUTATION_STATE_COMPARATOR);
        partial1.add(makeState("start", new DeweyNumber(1), 100L));

        Queue<ComputationState> partial2 = new PriorityQueue<>(NFAState.COMPUTATION_STATE_COMPARATOR);
        partial2.add(makeState("start", new DeweyNumber(2), 100L));

        Queue<ComputationState> completed = new PriorityQueue<>(NFAState.COMPUTATION_STATE_COMPARATOR);
        NFAState state1 = new NFAState(partial1, completed);
        NFAState state2 = new NFAState(partial2, completed);

        assertNotEquals(state1, state2);
    }

    @Test
    void testHashCodeConsistencyWithMultipleElements() {
        Queue<ComputationState> partial = new PriorityQueue<>(NFAState.COMPUTATION_STATE_COMPARATOR);
        partial.add(makeState("a", new DeweyNumber(1), 10L));
        partial.add(makeState("b", new DeweyNumber(2), 20L));
        partial.add(makeState("c", new DeweyNumber(3), 30L));

        Queue<ComputationState> completed = new PriorityQueue<>(NFAState.COMPUTATION_STATE_COMPARATOR);
        NFAState state = new NFAState(partial, completed);

        int h1 = state.hashCode();
        int h2 = state.hashCode();
        assertEquals(h1, h2);
    }
}
