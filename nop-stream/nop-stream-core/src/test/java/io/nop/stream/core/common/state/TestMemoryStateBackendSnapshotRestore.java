package io.nop.stream.core.common.state;

import io.nop.stream.core.common.state.backend.StateSnapshot;
import io.nop.stream.core.common.state.backend.memory.MemoryKeyedStateBackend;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestMemoryStateBackendSnapshotRestore {

    private MemoryKeyedStateBackend<String> createBackend() {
        return new MemoryKeyedStateBackend<>(String.class);
    }

    @Test
    void testValueStateRoundTrip() throws Exception {
        MemoryKeyedStateBackend<String> backend = createBackend();
        ValueStateDescriptor<Long> desc = new ValueStateDescriptor<>("value", Long.class, 0L);

        backend.setCurrentKey("key1");
        ValueState<Long> state = backend.getState(desc);
        state.update(42L);

        backend.setCurrentKey("key2");
        backend.getState(desc).update(99L);

        StateSnapshot snapshot = backend.snapshotState();

        MemoryKeyedStateBackend<String> restored = createBackend();
        restored.restoreState(snapshot);

        restored.setCurrentKey("key1");
        assertEquals(Long.valueOf(42L), restored.getState(desc).value());

        restored.setCurrentKey("key2");
        assertEquals(Long.valueOf(99L), restored.getState(desc).value());
    }

    @Test
    void testListStateRoundTrip() throws Exception {
        MemoryKeyedStateBackend<String> backend = createBackend();
        ListStateDescriptor<String> desc = new ListStateDescriptor<>("items", String.class);

        backend.setCurrentKey("key1");
        ListState<String> state = backend.getListState(desc);
        state.add("a");
        state.add("b");
        state.add("c");

        backend.setCurrentKey("key2");
        backend.getListState(desc).add("x");

        StateSnapshot snapshot = backend.snapshotState();

        MemoryKeyedStateBackend<String> restored = createBackend();
        restored.restoreState(snapshot);

        restored.setCurrentKey("key1");
        assertIterableEquals(List.of("a", "b", "c"), restored.getListState(desc).get());

        restored.setCurrentKey("key2");
        assertIterableEquals(List.of("x"), restored.getListState(desc).get());
    }
}
