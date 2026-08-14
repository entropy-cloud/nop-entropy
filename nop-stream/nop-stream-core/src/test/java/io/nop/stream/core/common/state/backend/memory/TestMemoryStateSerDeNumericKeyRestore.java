package io.nop.stream.core.common.state.backend.memory;

import io.nop.core.lang.json.JsonTool;
import io.nop.stream.core.common.state.ValueState;
import io.nop.stream.core.common.state.ValueStateDescriptor;
import io.nop.stream.core.common.state.backend.StateSnapshot;
import io.nop.stream.core.exceptions.StreamException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AR-01 (P0) regression tests: JSON-persisted checkpoints round-trip numeric
 * keys through {@code TextScanner}, which parses {@code "123"} as
 * {@code Integer} even when the backend key type is {@code Long}. Because
 * {@link TypedNamespaceAndKey#equals} is class-sensitive, the restored
 * {@code Integer} key never matched live {@code Long} lookups — the keyed
 * state silently restarted from empty on the Memory backend while the RocksDB
 * backend (which re-materializes keys by keyType) restored the same checkpoint
 * correctly.
 *
 * <p>The fix mirrors {@code RocksDBKeyEncoder.jsonToKey}: restore re-materializes
 * each key via the backend's declared keyType. These tests simulate the exact
 * storage-layer round trip ({@code JsonTool.serialize} → {@code JsonTool.parseMap})
 * that {@code CheckpointSerDe} performs for {@code storageType="local"}.
 */
class TestMemoryStateSerDeNumericKeyRestore {

    /**
     * The storage-layer round trip: what {@code CheckpointSerDe} does when a
     * {@code StateSnapshot} data map is persisted as JSON and parsed back.
     */
    @SuppressWarnings("unchecked")
    private StateSnapshot jsonRoundTrip(StateSnapshot snapshot) {
        String json = JsonTool.serialize(snapshot.getStateData(), false);
        Map<String, Object> parsed = JsonTool.parseMap(json);
        return new StateSnapshot(parsed);
    }

    private MemoryKeyedStateBackend<Long> newLongBackend() {
        return new MemoryKeyedStateBackend<>(Long.class);
    }

    private MemoryKeyedStateBackend<Integer> newIntegerBackend() {
        return new MemoryKeyedStateBackend<>(Integer.class);
    }

    private MemoryKeyedStateBackend<String> newStringBackend() {
        return new MemoryKeyedStateBackend<>(String.class);
    }

    @Test
    void testLongKeyValueStateSurvivesJsonCheckpointRestore() throws Exception {
        MemoryKeyedStateBackend<Long> backend = newLongBackend();
        ValueStateDescriptor<Long> desc = new ValueStateDescriptor<>("counter", Long.class, 0L);

        long smallLongKey = 123L;          // < 2^31 → JSON parses back as Integer
        long bigLongKey = 3_000_000_000L;  // > 2^31 → JSON parses back as Long

        backend.setCurrentKey(smallLongKey);
        backend.getState(desc).update(100L);
        backend.setCurrentKey(bigLongKey);
        backend.getState(desc).update(200L);

        StateSnapshot snapshot = jsonRoundTrip(backend.snapshotState());

        // Pre-fix (red): the restored small Long key became Integer(123) and the
        // lookup with Long(123) missed → value() returned the default 0L.
        MemoryKeyedStateBackend<Long> restored = newLongBackend();
        restored.restoreState(snapshot);

        restored.setCurrentKey(smallLongKey);
        assertEquals(Long.valueOf(100L), restored.getState(desc).value(),
                "Long key < 2^31 must hit after JSON checkpoint restore");
        restored.setCurrentKey(bigLongKey);
        assertEquals(Long.valueOf(200L), restored.getState(desc).value(),
                "Long key > 2^31 must hit after JSON checkpoint restore");

        backend.close();
        restored.close();
    }

    @Test
    void testIntegerKeyValueStateSurvivesJsonCheckpointRestore() throws Exception {
        MemoryKeyedStateBackend<Integer> backend = newIntegerBackend();
        ValueStateDescriptor<Long> desc = new ValueStateDescriptor<>("counter", Long.class, 0L);

        backend.setCurrentKey(42);
        backend.getState(desc).update(7L);
        backend.setCurrentKey(12345);
        backend.getState(desc).update(8L);

        StateSnapshot snapshot = jsonRoundTrip(backend.snapshotState());

        MemoryKeyedStateBackend<Integer> restored = newIntegerBackend();
        restored.restoreState(snapshot);

        restored.setCurrentKey(42);
        assertEquals(Long.valueOf(7L), restored.getState(desc).value(),
                "Integer key must hit after JSON checkpoint restore");
        restored.setCurrentKey(12345);
        assertEquals(Long.valueOf(8L), restored.getState(desc).value());

        backend.close();
        restored.close();
    }

    @Test
    void testStringKeyRegressionAfterJsonCheckpointRestore() throws Exception {
        MemoryKeyedStateBackend<String> backend = newStringBackend();
        ValueStateDescriptor<Long> desc = new ValueStateDescriptor<>("counter", Long.class, 0L);

        backend.setCurrentKey("user-1");
        backend.getState(desc).update(11L);
        backend.setCurrentKey("user-2");
        backend.getState(desc).update(22L);

        StateSnapshot snapshot = jsonRoundTrip(backend.snapshotState());

        MemoryKeyedStateBackend<String> restored = newStringBackend();
        restored.restoreState(snapshot);

        restored.setCurrentKey("user-1");
        assertEquals(Long.valueOf(11L), restored.getState(desc).value(),
                "String keys must keep round-tripping unchanged");
        restored.setCurrentKey("user-2");
        assertEquals(Long.valueOf(22L), restored.getState(desc).value());

        backend.close();
        restored.close();
    }

    @Test
    void testMapAndListStateWithNumericKeysSurviveJsonCheckpointRestore() throws Exception {
        MemoryKeyedStateBackend<Long> backend = newLongBackend();
        io.nop.stream.core.common.state.MapStateDescriptor<String, Integer> mapDesc =
                new io.nop.stream.core.common.state.MapStateDescriptor<>("map", String.class, Integer.class);
        io.nop.stream.core.common.state.ListStateDescriptor<String> listDesc =
                new io.nop.stream.core.common.state.ListStateDescriptor<>("list", String.class);

        backend.setCurrentKey(123L);
        backend.getMapState(mapDesc).put("a", 1);
        backend.getListState(listDesc).add("x");
        backend.getListState(listDesc).add("y");

        StateSnapshot snapshot = jsonRoundTrip(backend.snapshotState());

        MemoryKeyedStateBackend<Long> restored = newLongBackend();
        restored.restoreState(snapshot);

        restored.setCurrentKey(123L);
        assertEquals(Integer.valueOf(1), restored.getMapState(mapDesc).get("a"),
                "MapState under a Long key must hit after JSON checkpoint restore");
        int count = 0;
        for (String s : restored.getListState(listDesc).get()) {
            count++;
        }
        assertEquals(2, count, "ListState under a Long key must hit after JSON checkpoint restore");

        backend.close();
        restored.close();
    }

    /**
     * No-silent-skip (guide rule #24): when a snapshot key cannot be
     * re-materialized to the backend's keyType (a String-keyed snapshot restored
     * into a Long-keyed backend), restore fails fast instead of silently keeping
     * the mismatched key (which {@link TypedNamespaceAndKey#equals} would drop).
     */
    @Test
    void testRematerializationFailureFailsFast() throws Exception {
        MemoryKeyedStateBackend<String> stringBackend = newStringBackend();
        ValueStateDescriptor<Long> desc = new ValueStateDescriptor<>("counter", Long.class, 0L);

        stringBackend.setCurrentKey("not-a-number");
        stringBackend.getState(desc).update(5L);

        StateSnapshot snapshot = jsonRoundTrip(stringBackend.snapshotState());

        MemoryKeyedStateBackend<Long> restored = newLongBackend();
        assertThrows(StreamException.class, () -> restored.restoreState(snapshot),
                "Restoring a non-Long key into a Long-keyed backend must fail fast, not silently drop state");

        stringBackend.close();
        restored.close();
    }
}
