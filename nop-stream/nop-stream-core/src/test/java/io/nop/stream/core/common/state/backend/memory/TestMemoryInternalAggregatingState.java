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
