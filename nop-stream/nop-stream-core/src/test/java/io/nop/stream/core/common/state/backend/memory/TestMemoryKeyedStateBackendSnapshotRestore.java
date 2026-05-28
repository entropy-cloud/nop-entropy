package io.nop.stream.core.common.state.backend.memory;

import io.nop.stream.core.common.accumulators.LongCounter;
import io.nop.stream.core.common.functions.AggregateFunction;
import io.nop.stream.core.common.state.AggregatingState;
import io.nop.stream.core.common.state.AggregatingStateDescriptor;
import io.nop.stream.core.common.state.ReducingState;
import io.nop.stream.core.common.state.ReducingStateDescriptor;
import io.nop.stream.core.common.state.ValueState;
import io.nop.stream.core.common.state.ValueStateDescriptor;
import io.nop.stream.core.common.state.backend.StateSnapshot;
import io.nop.stream.core.exceptions.StreamException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestMemoryKeyedStateBackendSnapshotRestore {

    private MemoryKeyedStateBackend<String> createBackend() {
        return new MemoryKeyedStateBackend<>(String.class);
    }

    @Test
    void testReducingStateRoundTrip() throws Exception {
        MemoryKeyedStateBackend<String> backend = createBackend();
        ReducingStateDescriptor<Long> desc = new ReducingStateDescriptor<>("sum", Long.class, LongCounter.class);

        backend.setCurrentKey("key1");
        ReducingState<Long> state = backend.getReducingState(desc);
        state.add(10L);
        state.add(20L);

        backend.setCurrentKey("key2");
        backend.getReducingState(desc).add(5L);

        StateSnapshot snapshot = backend.snapshotState();

        MemoryKeyedStateBackend<String> restored = createBackend();
        restored.restoreState(snapshot);

        restored.setCurrentKey("key1");
        assertEquals(Long.valueOf(30L), restored.getReducingState(desc).get());

        restored.setCurrentKey("key2");
        assertEquals(Long.valueOf(5L), restored.getReducingState(desc).get());
    }

    @Test
    void testReducingStateMultiNamespace() throws Exception {
        MemoryKeyedStateBackend<String> backend = createBackend();
        ReducingStateDescriptor<Long> desc = new ReducingStateDescriptor<>("counter", Long.class, LongCounter.class);

        backend.setCurrentKey("k1");
        backend.setCurrentNamespace("ns1");
        backend.getReducingState(desc).add(10L);

        backend.setCurrentNamespace("ns2");
        backend.getReducingState(desc).add(20L);

        StateSnapshot snapshot = backend.snapshotState();

        MemoryKeyedStateBackend<String> restored = createBackend();
        restored.restoreState(snapshot);

        restored.setCurrentKey("k1");
        restored.setCurrentNamespace("ns1");
        assertEquals(Long.valueOf(10L), restored.getReducingState(desc).get());

        restored.setCurrentNamespace("ns2");
        assertEquals(Long.valueOf(20L), restored.getReducingState(desc).get());
    }

    @Test
    void testAggregatingStateRoundTrip() throws Exception {
        MemoryKeyedStateBackend<String> backend = createBackend();
        AvgAggregateFunction avgFn = new AvgAggregateFunction();
        AggregatingStateDescriptor<Integer, int[], Double> desc =
                new AggregatingStateDescriptor<>("avg", avgFn, int[].class);

        backend.setCurrentKey("key1");
        AggregatingState<Integer, Double> state = backend.getAggregatingState(desc);
        state.add(10);
        state.add(20);
        state.add(30);

        backend.setCurrentKey("key2");
        backend.getAggregatingState(desc).add(100);

        StateSnapshot snapshot = backend.snapshotState();

        MemoryKeyedStateBackend<String> restored = createBackend();
        restored.restoreState(snapshot);

        AvgAggregateFunction restoredFn = new AvgAggregateFunction();
        AggregatingStateDescriptor<Integer, int[], Double> restoredDesc =
                new AggregatingStateDescriptor<>("avg", restoredFn, int[].class);

        restored.setCurrentKey("key1");
        assertEquals(20.0, restored.getAggregatingState(restoredDesc).get(), 0.001);

        restored.setCurrentKey("key2");
        assertEquals(100.0, restored.getAggregatingState(restoredDesc).get(), 0.001);
    }

    @Test
    void testMixedStateTypesRoundTrip() throws Exception {
        MemoryKeyedStateBackend<String> backend = createBackend();

        ValueStateDescriptor<Long> vDesc = new ValueStateDescriptor<>("v", Long.class, 0L);
        ReducingStateDescriptor<Long> rDesc = new ReducingStateDescriptor<>("r", Long.class, LongCounter.class);

        backend.setCurrentKey("k");
        backend.getState(vDesc).update(42L);
        backend.getReducingState(rDesc).add(10L);
        backend.getReducingState(rDesc).add(5L);

        StateSnapshot snapshot = backend.snapshotState();

        MemoryKeyedStateBackend<String> restored = createBackend();
        restored.restoreState(snapshot);

        restored.setCurrentKey("k");
        assertEquals(Long.valueOf(42L), restored.getState(vDesc).value());
        assertEquals(Long.valueOf(15L), restored.getReducingState(rDesc).get());
    }

    @Test
    void testEmptyReducingStateRoundTrip() throws Exception {
        MemoryKeyedStateBackend<String> backend = createBackend();
        ReducingStateDescriptor<Long> desc = new ReducingStateDescriptor<>("sum", Long.class, LongCounter.class);
        backend.getReducingState(desc);

        StateSnapshot snapshot = backend.snapshotState();

        MemoryKeyedStateBackend<String> restored = createBackend();
        restored.restoreState(snapshot);

        restored.setCurrentKey("k");
        assertNull(restored.getReducingState(desc).get());
    }

    static class AvgAggregateFunction implements AggregateFunction<Integer, int[], Double> {
        @Override
        public int[] createAccumulator() {
            return new int[]{0, 0};
        }

        @Override
        public int[] add(Integer value, int[] accumulator) {
            accumulator[0] += value;
            accumulator[1]++;
            return accumulator;
        }

        @Override
        public Double getResult(int[] accumulator) {
            return accumulator[1] == 0 ? 0.0 : (double) accumulator[0] / accumulator[1];
        }

        @Override
        public int[] merge(int[] a, int[] b) {
            a[0] += b[0];
            a[1] += b[1];
            return a;
        }
    }
}
