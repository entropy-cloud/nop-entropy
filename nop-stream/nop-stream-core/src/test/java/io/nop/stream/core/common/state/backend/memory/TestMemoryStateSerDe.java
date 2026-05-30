/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.state.backend.memory;

import io.nop.stream.core.common.functions.AggregateFunction;
import io.nop.stream.core.common.state.*;
import io.nop.stream.core.common.state.backend.StateSnapshot;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for MemoryStateSerDe round-trip with AggregatingState, ReducingState, and MapState.
 * These complement TestMemoryStateBackendSnapshotRestore and the existing tests in this file
 * that cover ValueState and ReducingState round-trips.
 */
class TestMemoryStateSerDe {

    // ==================== AggregatingState ====================

    /**
     * 16-04: AggregatingState snapshot/restore round-trip.
     * Uses a simple SumAggregateFunction that accumulates Long values.
     */
    @Test
    void testAggregatingStateRoundTrip() throws Exception {
        MemoryKeyedStateBackend<String> backend = new MemoryKeyedStateBackend<>(String.class);
        AggregateFunction<Long, long[], Long> sumFn = new SumAggregateFunction();
        AggregatingStateDescriptor<Long, long[], Long> desc =
                new AggregatingStateDescriptor<>("agg-sum", sumFn, long[].class);

        backend.setCurrentKey("key1");
        AggregatingState<Long, Long> state = backend.getAggregatingState(desc);
        state.add(10L);
        state.add(20L);
        state.add(30L);

        backend.setCurrentKey("key2");
        AggregatingState<Long, Long> state2 = backend.getAggregatingState(desc);
        state2.add(5L);

        StateSnapshot snapshot = backend.snapshotState();

        // Restore into a fresh backend
        MemoryKeyedStateBackend<String> restored = new MemoryKeyedStateBackend<>(String.class);
        restored.restoreState(snapshot);

        // Use a new descriptor instance with same name and function
        AggregateFunction<Long, long[], Long> sumFn2 = new SumAggregateFunction();
        AggregatingStateDescriptor<Long, long[], Long> restoredDesc =
                new AggregatingStateDescriptor<>("agg-sum", sumFn2, long[].class);

        restored.setCurrentKey("key1");
        assertEquals(Long.valueOf(60L), restored.getAggregatingState(restoredDesc).get());

        restored.setCurrentKey("key2");
        assertEquals(Long.valueOf(5L), restored.getAggregatingState(restoredDesc).get());
    }

    // ==================== ReducingState ====================

    /**
     * 16-04: ReducingState snapshot/restore round-trip (additional coverage beyond existing test).
     * Tests with multiple keys to verify cross-key integrity.
     */
    @Test
    void testReducingStateRoundTripMultipleKeys() throws Exception {
        MemoryKeyedStateBackend<String> backend = new MemoryKeyedStateBackend<>(String.class);
        ReducingStateDescriptor<Long> desc = new ReducingStateDescriptor<>(
                "reduce-max", Long.class, io.nop.stream.core.common.accumulators.LongCounter.class);

        backend.setCurrentKey("alpha");
        ReducingState<Long> state1 = backend.getReducingState(desc);
        state1.add(10L);
        state1.add(50L);
        state1.add(30L);

        backend.setCurrentKey("beta");
        ReducingState<Long> state2 = backend.getReducingState(desc);
        state2.add(100L);
        state2.add(200L);

        StateSnapshot snapshot = backend.snapshotState();

        MemoryKeyedStateBackend<String> restored = new MemoryKeyedStateBackend<>(String.class);
        restored.restoreState(snapshot);

        restored.setCurrentKey("alpha");
        // LongCounter accumulates sums: 10+50+30 = 90
        assertEquals(Long.valueOf(90L), restored.getReducingState(desc).get());

        restored.setCurrentKey("beta");
        // LongCounter accumulates sums: 100+200 = 300
        assertEquals(Long.valueOf(300L), restored.getReducingState(desc).get());
    }

    // ==================== MapState ====================

    /**
     * 16-04: MapState snapshot/restore round-trip.
     */
    @Test
    void testMapStateRoundTrip() throws Exception {
        MemoryKeyedStateBackend<String> backend = new MemoryKeyedStateBackend<>(String.class);
        MapStateDescriptor<String, Long> desc = new MapStateDescriptor<>("map-state", String.class, Long.class);

        backend.setCurrentKey("key1");
        MapState<String, Long> state1 = backend.getMapState(desc);
        state1.put("field-a", 100L);
        state1.put("field-b", 200L);
        state1.put("field-c", 300L);

        backend.setCurrentKey("key2");
        MapState<String, Long> state2 = backend.getMapState(desc);
        state2.put("field-x", 999L);

        StateSnapshot snapshot = backend.snapshotState();

        MemoryKeyedStateBackend<String> restored = new MemoryKeyedStateBackend<>(String.class);
        restored.restoreState(snapshot);

        restored.setCurrentKey("key1");
        MapState<String, Long> restoredState1 = restored.getMapState(desc);
        assertEquals(Long.valueOf(100L), restoredState1.get("field-a"));
        assertEquals(Long.valueOf(200L), restoredState1.get("field-b"));
        assertEquals(Long.valueOf(300L), restoredState1.get("field-c"));
        assertNull(restoredState1.get("field-d"));

        // Verify entries iteration
        int count = 0;
        for (Map.Entry<String, Long> entry : restoredState1.entries()) {
            count++;
            assertTrue(entry.getValue() != null);
        }
        assertEquals(3, count);

        restored.setCurrentKey("key2");
        MapState<String, Long> restoredState2 = restored.getMapState(desc);
        assertEquals(Long.valueOf(999L), restoredState2.get("field-x"));
        assertFalse(restoredState2.contains("field-y"));
    }

    // ==================== Helper ====================

    /**
     * Simple sum aggregate function for testing.
     * Uses a long[1] array as accumulator to hold the running sum.
     */
    static class SumAggregateFunction implements AggregateFunction<Long, long[], Long> {
        private static final long serialVersionUID = 1L;

        @Override
        public long[] createAccumulator() {
            return new long[]{0L};
        }

        @Override
        public long[] add(Long value, long[] accumulator) {
            accumulator[0] += value;
            return accumulator;
        }

        @Override
        public Long getResult(long[] accumulator) {
            return accumulator[0];
        }

        @Override
        public long[] merge(long[] a, long[] b) {
            a[0] += b[0];
            return a;
        }
    }
}
