package io.nop.stream.core.common.state.backend.memory;

import io.nop.stream.core.common.accumulators.LongCounter;
import io.nop.stream.core.common.state.ReducingState;
import io.nop.stream.core.common.state.ReducingStateDescriptor;
import io.nop.stream.core.common.state.ValueState;
import io.nop.stream.core.common.state.ValueStateDescriptor;
import io.nop.stream.core.common.state.backend.StateSnapshot;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for MemoryStateSerDe type safety during restore.
 * The type mismatch check in restoreAppendingState/restoreReducingState is a defensive
 * guard that catches cases where deserializeValue returns a value not matching valueClass.
 * Since deserializeValue converts via JSON to the target type, this test verifies that
 * valid round-trips still work correctly after adding the defensive checks.
 */
class TestMemoryStateSerDe {

    /**
     * Verify that reducing state round-trip still works after adding type checks.
     */
    @Test
    void testReducingStateRoundTripPreserved() throws Exception {
        MemoryKeyedStateBackend<String> backend = new MemoryKeyedStateBackend<>(String.class);
        ReducingStateDescriptor<Long> desc = new ReducingStateDescriptor<>("sum", Long.class, LongCounter.class);

        backend.setCurrentKey("key1");
        ReducingState<Long> state = backend.getReducingState(desc);
        state.add(10L);
        state.add(20L);

        StateSnapshot snapshot = backend.snapshotState();

        MemoryKeyedStateBackend<String> restored = new MemoryKeyedStateBackend<>(String.class);
        restored.restoreState(snapshot);

        restored.setCurrentKey("key1");
        assertEquals(Long.valueOf(30L), restored.getReducingState(desc).get());
    }

    /**
     * Verify value state round-trip still works after adding defensive checks.
     */
    @Test
    void testValueStateRoundTripPreserved() throws Exception {
        MemoryKeyedStateBackend<String> backend = new MemoryKeyedStateBackend<>(String.class);
        ValueStateDescriptor<Long> desc = new ValueStateDescriptor<>("v", Long.class);

        backend.setCurrentKey("key1");
        ValueState<Long> state = backend.getState(desc);
        state.update(42L);

        StateSnapshot snapshot = backend.snapshotState();

        MemoryKeyedStateBackend<String> restored = new MemoryKeyedStateBackend<>(String.class);
        restored.restoreState(snapshot);

        restored.setCurrentKey("key1");
        assertEquals(Long.valueOf(42L), restored.getState(desc).value());
    }
}
