package io.nop.stream.core.common.state.backend.memory;

import io.nop.stream.core.common.accumulators.LongCounter;
import io.nop.stream.core.common.state.InternalAppendingState;
import io.nop.stream.core.common.state.InternalListState;
import io.nop.stream.core.common.state.ListState;
import io.nop.stream.core.common.state.ListStateDescriptor;
import io.nop.stream.core.common.state.MapState;
import io.nop.stream.core.common.state.MapStateDescriptor;
import io.nop.stream.core.common.state.ReducingStateDescriptor;
import io.nop.stream.core.common.state.ValueState;
import io.nop.stream.core.common.state.ValueStateDescriptor;
import io.nop.stream.core.common.state.backend.StateSnapshot;
import io.nop.stream.core.windowing.windows.TimeWindow;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestStateSnapshotRoundTrip {

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
        backend.getState(desc).update(100L);

        StateSnapshot snapshot = backend.snapshotState();

        MemoryKeyedStateBackend<String> restored = createBackend();
        restored.restoreState(snapshot);

        restored.setCurrentKey("key1");
        assertEquals(Long.valueOf(42L), restored.getState(desc).value());

        restored.setCurrentKey("key2");
        assertEquals(Long.valueOf(100L), restored.getState(desc).value());
    }

    @Test
    void testMapStateRoundTrip() throws Exception {
        MemoryKeyedStateBackend<String> backend = createBackend();
        MapStateDescriptor<String, Integer> desc = new MapStateDescriptor<>("map", String.class, Integer.class);

        backend.setCurrentKey("key1");
        MapState<String, Integer> state = backend.getMapState(desc);
        state.put("a", 1);
        state.put("b", 2);

        backend.setCurrentKey("key2");
        backend.getMapState(desc).put("x", 10);

        StateSnapshot snapshot = backend.snapshotState();

        MemoryKeyedStateBackend<String> restored = createBackend();
        restored.restoreState(snapshot);

        restored.setCurrentKey("key1");
        MapState<String, Integer> restoredState = restored.getMapState(desc);
        assertEquals(Integer.valueOf(1), restoredState.get("a"));
        assertEquals(Integer.valueOf(2), restoredState.get("b"));
        assertNull(restoredState.get("c"));

        restored.setCurrentKey("key2");
        assertEquals(Integer.valueOf(10), restored.getMapState(desc).get("x"));
    }

    @Test
    void testAppendingStateRoundTrip() throws Exception {
        MemoryKeyedStateBackend<String> backend = createBackend();
        ReducingStateDescriptor<Long> desc = new ReducingStateDescriptor<>("append", Long.class, LongCounter.class);

        InternalAppendingState<String, TimeWindow, Long, Long, Long> state =
                backend.getInternalAppendingState(desc);
        state.setCurrentNamespace(new TimeWindow(0, 100));

        backend.setCurrentKey("key1");
        state.add(10L);
        state.add(20L);

        backend.setCurrentKey("key2");
        state.add(5L);

        StateSnapshot snapshot = backend.snapshotState();

        MemoryKeyedStateBackend<String> restored = createBackend();
        restored.restoreState(snapshot);

        InternalAppendingState<String, TimeWindow, Long, Long, Long> restoredState =
                restored.getInternalAppendingState(desc);

        restored.setCurrentKey("key1");
        restoredState.setCurrentNamespace(new TimeWindow(0, 100));
        assertEquals(Long.valueOf(30L), restoredState.get());

        restored.setCurrentKey("key2");
        restoredState.setCurrentNamespace(new TimeWindow(0, 100));
        assertEquals(Long.valueOf(5L), restoredState.get());
    }

    @Test
    void testListStateRoundTrip() throws Exception {
        MemoryKeyedStateBackend<String> backend = createBackend();
        ListStateDescriptor<String> desc = new ListStateDescriptor<>("list", String.class);

        InternalListState<String, TimeWindow, String> state =
                backend.getInternalListState(desc);
        state.setCurrentNamespace(new TimeWindow(0, 100));

        backend.setCurrentKey("key1");
        state.add("a");
        state.add("b");
        state.add("c");

        backend.setCurrentKey("key2");
        state.add("x");

        StateSnapshot snapshot = backend.snapshotState();

        MemoryKeyedStateBackend<String> restored = createBackend();
        restored.restoreState(snapshot);

        InternalListState<String, TimeWindow, String> restoredState =
                restored.getInternalListState(desc);

        restored.setCurrentKey("key1");
        restoredState.setCurrentNamespace(new TimeWindow(0, 100));
        List<String> list1 = new ArrayList<>();
        restoredState.get().forEach(list1::add);
        assertEquals(Arrays.asList("a", "b", "c"), list1);

        restored.setCurrentKey("key2");
        restoredState.setCurrentNamespace(new TimeWindow(0, 100));
        List<String> list2 = new ArrayList<>();
        restoredState.get().forEach(list2::add);
        assertEquals(Arrays.asList("x"), list2);
    }

    @Test
    void testNullSnapshotRestore() throws Exception {
        MemoryKeyedStateBackend<String> backend = createBackend();
        assertNull(backend.snapshotState());
        backend.restoreState(null);
        backend.restoreState(new StateSnapshot());
    }

    @Test
    void testSnapshotAfterClear() throws Exception {
        MemoryKeyedStateBackend<String> backend = createBackend();
        ValueStateDescriptor<Long> desc = new ValueStateDescriptor<>("v", Long.class, 0L);

        backend.setCurrentKey("k");
        backend.getState(desc).update(1L);

        backend.getState(desc).clear();

        StateSnapshot snapshot = backend.snapshotState();

        MemoryKeyedStateBackend<String> restored = createBackend();
        restored.restoreState(snapshot);

        restored.setCurrentKey("k");
        assertNull(restored.getState(desc).value());
    }

    @Test
    void testPartialRoundTrip() throws Exception {
        MemoryKeyedStateBackend<String> backend = createBackend();

        ValueStateDescriptor<Long> vDesc = new ValueStateDescriptor<>("v", Long.class, 0L);
        MapStateDescriptor<String, Integer> mDesc = new MapStateDescriptor<>("m", String.class, Integer.class);

        backend.setCurrentKey("k");
        backend.getState(vDesc).update(42L);
        backend.getMapState(mDesc).put("x", 1);

        StateSnapshot snapshot = backend.snapshotState();

        MemoryKeyedStateBackend<String> restored = createBackend();
        restored.setCurrentKey("k");
        restored.getState(vDesc).update(999L);
        restored.restoreState(snapshot);

        assertEquals(Long.valueOf(42L), restored.getState(vDesc).value());
        assertEquals(Integer.valueOf(1), restored.getMapState(mDesc).get("x"));
    }

    @Test
    void testShardedValueStateRoundTrip() throws Exception {
        MemoryKeyedStateBackend<String> backend = new MemoryKeyedStateBackend<>(String.class, 4);
        ValueStateDescriptor<Long> desc = new ValueStateDescriptor<>("v", Long.class, 0L);

        backend.setCurrentKey("k1");
        backend.getState(desc).update(10L);

        backend.setCurrentKey("k2");
        backend.getState(desc).update(20L);

        backend.setCurrentKey("k3");
        backend.getState(desc).update(30L);

        backend.setCurrentKey("k4");
        backend.getState(desc).update(40L);

        StateSnapshot snapshot = backend.snapshotState();

        MemoryKeyedStateBackend<String> restored = new MemoryKeyedStateBackend<>(String.class, 4);
        restored.restoreState(snapshot);

        restored.setCurrentKey("k1");
        assertEquals(Long.valueOf(10L), restored.getState(desc).value());

        restored.setCurrentKey("k2");
        assertEquals(Long.valueOf(20L), restored.getState(desc).value());

        restored.setCurrentKey("k3");
        assertEquals(Long.valueOf(30L), restored.getState(desc).value());

        restored.setCurrentKey("k4");
        assertEquals(Long.valueOf(40L), restored.getState(desc).value());
    }

    @Test
    void testShardedMapStateRoundTrip() throws Exception {
        MemoryKeyedStateBackend<String> backend = new MemoryKeyedStateBackend<>(String.class, 2);
        MapStateDescriptor<String, Integer> desc = new MapStateDescriptor<>("m", String.class, Integer.class);

        backend.setCurrentKey("k1");
        backend.getMapState(desc).put("a", 1);

        backend.setCurrentKey("k2");
        backend.getMapState(desc).put("b", 2);

        StateSnapshot snapshot = backend.snapshotState();

        MemoryKeyedStateBackend<String> restored = new MemoryKeyedStateBackend<>(String.class, 2);
        restored.restoreState(snapshot);

        restored.setCurrentKey("k1");
        assertEquals(Integer.valueOf(1), restored.getMapState(desc).get("a"));

        restored.setCurrentKey("k2");
        assertEquals(Integer.valueOf(2), restored.getMapState(desc).get("b"));
    }

    @Test
    void testListStateRoundTripPublic() throws Exception {
        MemoryKeyedStateBackend<String> backend = createBackend();
        ListStateDescriptor<String> desc = new ListStateDescriptor<>("list", String.class);

        backend.setCurrentKey("k1");
        ListState<String> state = backend.getListState(desc);
        state.add("x");
        state.add("y");

        backend.setCurrentKey("k2");
        backend.getListState(desc).add("z");

        StateSnapshot snapshot = backend.snapshotState();

        MemoryKeyedStateBackend<String> restored = createBackend();
        restored.restoreState(snapshot);

        restored.setCurrentKey("k1");
        List<String> list1 = new ArrayList<>();
        restored.getListState(desc).get().forEach(list1::add);
        assertEquals(Arrays.asList("x", "y"), list1);

        restored.setCurrentKey("k2");
        List<String> list2 = new ArrayList<>();
        restored.getListState(desc).get().forEach(list2::add);
        assertEquals(Arrays.asList("z"), list2);
    }

    @Test
    void testMultipleStateTypesRoundTrip() throws Exception {
        MemoryKeyedStateBackend<String> backend = createBackend();

        ValueStateDescriptor<Long> vDesc = new ValueStateDescriptor<>("v", Long.class, 0L);
        MapStateDescriptor<String, Integer> mDesc = new MapStateDescriptor<>("m", String.class, Integer.class);
        ListStateDescriptor<String> lDesc = new ListStateDescriptor<>("l", String.class);

        backend.setCurrentKey("k");
        backend.getState(vDesc).update(99L);
        backend.getMapState(mDesc).put("key1", 42);
        backend.getListState(lDesc).add("item1");
        backend.getListState(lDesc).add("item2");

        StateSnapshot snapshot = backend.snapshotState();

        MemoryKeyedStateBackend<String> restored = createBackend();
        restored.restoreState(snapshot);

        restored.setCurrentKey("k");
        assertEquals(Long.valueOf(99L), restored.getState(vDesc).value());
        assertEquals(Integer.valueOf(42), restored.getMapState(mDesc).get("key1"));

        List<String> items = new ArrayList<>();
        restored.getListState(lDesc).get().forEach(items::add);
        assertEquals(Arrays.asList("item1", "item2"), items);
    }
}
