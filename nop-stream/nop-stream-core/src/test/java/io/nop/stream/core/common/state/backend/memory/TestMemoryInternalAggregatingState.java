package io.nop.stream.core.common.state.backend.memory;

import io.nop.stream.core.common.functions.AggregateFunction;
import io.nop.stream.core.common.state.AggregatingStateDescriptor;
import io.nop.stream.core.common.state.InternalAppendingState;
import io.nop.stream.core.common.state.backend.StateSnapshot;
import io.nop.stream.core.windowing.windows.TimeWindow;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestMemoryInternalAggregatingState {

    static class SumAggregateFunction implements AggregateFunction<Long, Long, Long> {
        private static final long serialVersionUID = 1L;

        @Override
        public Long createAccumulator() {
            return 0L;
        }

        @Override
        public Long add(Long value, Long accumulator) {
            return accumulator + value;
        }

        @Override
        public Long getResult(Long accumulator) {
            return accumulator;
        }

        @Override
        public Long merge(Long a, Long b) {
            return a + b;
        }
    }

    private AggregatingStateDescriptor<Long, Long, Long> descriptor =
            new AggregatingStateDescriptor<>("sum-state", new SumAggregateFunction(), Long.class);

    @Test
    void testAddAndGet() throws Exception {
        MemoryKeyedStateBackend<String> backend = new MemoryKeyedStateBackend<>(String.class);
        InternalAppendingState<String, Object, Long, Long, Long> state =
                backend.getInternalAppendingState(descriptor);

        backend.setCurrentKey("key1");
        state.setCurrentNamespace("ns1");

        state.add(10L);
        state.add(20L);
        state.add(30L);

        Long result = state.get();
        assertEquals(60L, result);
    }

    @Test
    void testNamespaceIsolation() throws Exception {
        MemoryKeyedStateBackend<String> backend = new MemoryKeyedStateBackend<>(String.class);
        InternalAppendingState<String, Object, Long, Long, Long> state =
                backend.getInternalAppendingState(descriptor);

        backend.setCurrentKey("key1");

        state.setCurrentNamespace("ns1");
        state.add(10L);
        state.add(20L);

        state.setCurrentNamespace("ns2");
        state.add(100L);
        state.add(200L);

        state.setCurrentNamespace("ns1");
        assertEquals(30L, state.get());

        state.setCurrentNamespace("ns2");
        assertEquals(300L, state.get());
    }

    @Test
    void testNamespaceIsolationWithTimeWindow() throws Exception {
        MemoryKeyedStateBackend<String> backend = new MemoryKeyedStateBackend<>(String.class);
        InternalAppendingState<String, Object, Long, Long, Long> state =
                backend.getInternalAppendingState(descriptor);

        backend.setCurrentKey("key1");

        TimeWindow w1 = new TimeWindow(1000, 2000);
        TimeWindow w2 = new TimeWindow(2000, 3000);

        state.setCurrentNamespace(w1);
        state.add(5L);

        state.setCurrentNamespace(w2);
        state.add(50L);

        state.setCurrentNamespace(w1);
        assertEquals(5L, state.get());

        state.setCurrentNamespace(w2);
        assertEquals(50L, state.get());
    }

    @Test
    void testGetReturnsNullWhenEmpty() throws Exception {
        MemoryKeyedStateBackend<String> backend = new MemoryKeyedStateBackend<>(String.class);
        InternalAppendingState<String, Object, Long, Long, Long> state =
                backend.getInternalAppendingState(descriptor);

        backend.setCurrentKey("key1");
        state.setCurrentNamespace("ns1");

        assertNull(state.get());
    }

    @Test
    void testGetAccumulatorReturnsRaw() throws Exception {
        MemoryKeyedStateBackend<String> backend = new MemoryKeyedStateBackend<>(String.class);
        InternalAppendingState<String, Object, Long, Long, Long> state =
                backend.getInternalAppendingState(descriptor);

        backend.setCurrentKey("key1");
        state.setCurrentNamespace("ns1");

        state.add(10L);
        state.add(20L);

        Long acc = state.getAccumulator();
        assertEquals(30L, acc);
    }

    /**
     * Verifies that setAccumulator() directly stores the value and does NOT
     * re-apply the AggregateFunction.add() transformation. This is the contract
     * WindowOperator.mergeWindowContents() relies on when writing back a
     * pre-merged accumulator during session-window merge.
     */
    @Test
    void testSetAccumulatorStoresRawValueWithoutReAggregating() throws Exception {
        MemoryKeyedStateBackend<String> backend = new MemoryKeyedStateBackend<>(String.class);
        InternalAppendingState<String, Object, Long, Long, Long> state =
                backend.getInternalAppendingState(descriptor);

        backend.setCurrentKey("key1");
        state.setCurrentNamespace("ns1");

        state.setAccumulator(777L);

        Long acc = state.getAccumulator();
        assertEquals(777L, acc);

        Long out = state.get();
        assertEquals(777L, out);
    }

    /**
     * Reproduces the WindowOperator.mergeWindowContents() merge pattern at the
     * state level: accumulate into two namespaces (source windows), read their
     * raw accumulators, merge via the AggregateFunction.merge(), then write the
     * merged accumulator back via setAccumulator() (NOT clear()+add()). Verifies
     * the result equals the sum of all elements, not double-counted.
     *
     * <p>This is the regression test for the P0 bug where {@code clear()+add()}
     * applied {@code AggregateFunction.add()} to an already-merged ACC value,
     * producing wrong results (or a type-mismatch exception).
     */
    @Test
    void testAggregatingStateMergeProducesCorrectResult() throws Exception {
        MemoryKeyedStateBackend<String> backend = new MemoryKeyedStateBackend<>(String.class);
        InternalAppendingState<String, Object, Long, Long, Long> state =
                backend.getInternalAppendingState(descriptor);
        SumAggregateFunction aggFn = new SumAggregateFunction();

        backend.setCurrentKey("key1");

        String targetWindow = "window-target";
        String sourceWindow = "window-source";

        state.setCurrentNamespace(sourceWindow);
        state.add(10L);
        state.add(20L);

        state.setCurrentNamespace(targetWindow);
        state.add(5L);

        Long targetAcc = state.getAccumulator();
        state.setCurrentNamespace(sourceWindow);
        Long sourceAcc = state.getAccumulator();

        Long merged = aggFn.merge(targetAcc, sourceAcc);

        state.setCurrentNamespace(targetWindow);
        state.setAccumulator(merged);

        state.setCurrentNamespace(sourceWindow);
        state.clear();

        state.setCurrentNamespace(targetWindow);
        assertEquals(35L, state.get(), "Merged sum should be 5+10+20=35, not double-counted");
    }

    /**
     * Edge case: setAccumulator(null) followed by getAccumulator() returns null,
     * mirroring the semantics of an empty window. This documents the contract
     * that mergeWindowContents() relies on when targetValue is null it skips the
     * write entirely.
     */
    @Test
    void testSetAccumulatorNullClearsExistingValue() throws Exception {
        MemoryKeyedStateBackend<String> backend = new MemoryKeyedStateBackend<>(String.class);
        InternalAppendingState<String, Object, Long, Long, Long> state =
                backend.getInternalAppendingState(descriptor);

        backend.setCurrentKey("key1");
        state.setCurrentNamespace("ns1");

        state.add(10L);
        assertNotNull(state.getAccumulator());

        state.setAccumulator(null);
        assertNull(state.getAccumulator());
        assertNull(state.get());
    }

    @Test
    void testClear() throws Exception {
        MemoryKeyedStateBackend<String> backend = new MemoryKeyedStateBackend<>(String.class);
        InternalAppendingState<String, Object, Long, Long, Long> state =
                backend.getInternalAppendingState(descriptor);

        backend.setCurrentKey("key1");
        state.setCurrentNamespace("ns1");

        state.add(10L);
        assertNotNull(state.get());

        state.clear();
        assertNull(state.get());
    }

    @Test
    void testSnapshotRestoreRoundTrip() throws Exception {
        MemoryKeyedStateBackend<String> backend = new MemoryKeyedStateBackend<>(String.class);
        InternalAppendingState<String, Object, Long, Long, Long> state =
                backend.getInternalAppendingState(descriptor);

        backend.setCurrentKey("key1");
        state.setCurrentNamespace("ns1");
        state.add(10L);
        state.add(20L);

        state.setCurrentNamespace("ns2");
        state.add(100L);

        StateSnapshot snapshot = backend.snapshotState();

        MemoryKeyedStateBackend<String> restoredBackend = new MemoryKeyedStateBackend<>(String.class);
        restoredBackend.restoreState(snapshot);

        InternalAppendingState<String, Object, Long, Long, Long> restoredState =
                restoredBackend.getInternalAppendingState(
                        new AggregatingStateDescriptor<>("sum-state", new SumAggregateFunction(), Long.class));

        restoredBackend.setCurrentKey("key1");

        restoredState.setCurrentNamespace("ns1");
        assertEquals(30L, restoredState.get());

        restoredState.setCurrentNamespace("ns2");
        assertEquals(100L, restoredState.get());
    }

    @Test
    void testRebind() throws Exception {
        MemoryKeyedStateBackend<String> backend = new MemoryKeyedStateBackend<>(String.class);
        @SuppressWarnings("unchecked")
        MemoryInternalAggregatingState<String, Object, Long, Long, Long> concreteState =
                (MemoryInternalAggregatingState<String, Object, Long, Long, Long>)
                        backend.getInternalAppendingState(descriptor);

        backend.setCurrentKey("key1");
        concreteState.setCurrentNamespace("ns1");
        concreteState.add(10L);

        MemoryKeyedStateBackend<String> newBackend = new MemoryKeyedStateBackend<>(String.class);
        concreteState.rebind(newBackend);

        newBackend.setCurrentKey("key1");
        concreteState.setCurrentNamespace("ns1");
        concreteState.add(20L);

        assertEquals(30L, concreteState.get());
    }
}
