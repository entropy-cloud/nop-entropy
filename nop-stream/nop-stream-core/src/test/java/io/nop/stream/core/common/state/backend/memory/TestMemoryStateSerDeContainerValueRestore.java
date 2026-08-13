package io.nop.stream.core.common.state.backend.memory;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.core.lang.json.JsonTool;
import io.nop.stream.core.common.state.MapState;
import io.nop.stream.core.common.state.MapStateDescriptor;
import io.nop.stream.core.common.state.backend.StateSnapshot;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P1-21-01 regression tests: MapState container values (List/Map, nested
 * included) must survive the JSON checkpoint storage layer with inner element
 * types intact.
 *
 * <p>JSON persistence round-trips a {@code List<Event>} value as a JSON array
 * whose elements come back as {@code LinkedHashMap}s. Pre-fix, restore kept them
 * (the raw {@code List.class} declared type short-circuits
 * {@code deserializeValue}) and CEP crashed on the first typed access. The
 * restore side has no element-type source (raw value class, type-less JSON, null
 * input serializer), so the type source is established at the SNAPSHOT side
 * (Decision B: recursive type-aware container wrapper) and the restore side
 * re-materializes inner elements from it.
 */
class TestMemoryStateSerDeContainerValueRestore {

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

    @SuppressWarnings("unchecked")
    private static MapStateDescriptor<Long, List<Event>> queueDescriptor() {
        return new MapStateDescriptor<>("queue", Long.class, (Class) List.class);
    }

    @Test
    void testListContainerValueSurvivesJsonCheckpointRestore() throws Exception {
        MemoryKeyedStateBackend<Long> backend = new MemoryKeyedStateBackend<>(Long.class);
        List<Event> events = new ArrayList<>();
        events.add(new Event(42, "start42"));
        events.add(new Event(43, "mid"));
        backend.setCurrentKey(123L);
        backend.getMapState(queueDescriptor()).put(1000L, events);

        StateSnapshot snapshot = jsonRoundTrip(backend.snapshotState());

        MemoryKeyedStateBackend<Long> restored = new MemoryKeyedStateBackend<>(Long.class);
        restored.restoreState(snapshot);

        restored.setCurrentKey(123L);
        List<Event> restoredEvents = restored.getMapState(queueDescriptor()).get(1000L);
        assertNotNull(restoredEvents, "Queue value must restore");
        assertEquals(2, restoredEvents.size());
        assertEquals(Event.class, restoredEvents.get(0).getClass(),
                "inner element type must be Event after JSON restore, not LinkedHashMap");
        assertEquals("start42", restoredEvents.get(0).getName());
        assertEquals(43, restoredEvents.get(1).getId());

        backend.close();
        restored.close();
    }

    @Test
    void testMapValueContainerSurvivesJsonCheckpointRestore() throws Exception {
        MemoryKeyedStateBackend<Long> backend = new MemoryKeyedStateBackend<>(Long.class);
        MapStateDescriptor<Long, Map<String, Event>> desc =
                new MapStateDescriptor<>("map-value", Long.class, (Class) Map.class);

        Map<String, Event> map = new LinkedHashMap<>();
        map.put("k1", new Event(1, "a"));
        map.put("k2", new Event(2, "b"));
        backend.setCurrentKey(123L);
        backend.getMapState(desc).put(1000L, map);

        StateSnapshot snapshot = jsonRoundTrip(backend.snapshotState());

        MemoryKeyedStateBackend<Long> restored = new MemoryKeyedStateBackend<>(Long.class);
        restored.restoreState(snapshot);

        restored.setCurrentKey(123L);
        Map<String, Event> restoredMap = restored.getMapState(desc).get(1000L);
        assertNotNull(restoredMap);
        assertEquals(Event.class, restoredMap.get("k1").getClass(),
                "Map value's inner element type must be Event after JSON restore");
        assertEquals("a", restoredMap.get("k1").getName());
        assertEquals(2, restoredMap.get("k2").getId());

        backend.close();
        restored.close();
    }

    @Test
    void testNestedContainerValuesSurviveJsonCheckpointRestore() throws Exception {
        MemoryKeyedStateBackend<Long> backend = new MemoryKeyedStateBackend<>(Long.class);
        List<List<Event>> nested = new ArrayList<>();
        List<Event> inner = new ArrayList<>();
        inner.add(new Event(7, "inner"));
        nested.add(inner);

        backend.setCurrentKey(123L);
        backend.getMapState(queueDescriptor()).put(1000L, (List<Event>) (List<?>) nested);

        StateSnapshot snapshot = jsonRoundTrip(backend.snapshotState());

        MemoryKeyedStateBackend<Long> restored = new MemoryKeyedStateBackend<>(Long.class);
        restored.restoreState(snapshot);

        restored.setCurrentKey(123L);
        List<List<Event>> restoredNested =
                (List<List<Event>>) (List<?>) restored.getMapState(queueDescriptor()).get(1000L);
        assertNotNull(restoredNested);
        assertEquals(Event.class, restoredNested.get(0).get(0).getClass(),
                "nested container inner element type must survive JSON restore");
        assertEquals("inner", restoredNested.get(0).get(0).getName());

        backend.close();
        restored.close();
    }

    @Test
    void testScalarListElementsAreRematerializedToRecordedType() throws Exception {
        MemoryKeyedStateBackend<Long> backend = new MemoryKeyedStateBackend<>(Long.class);
        MapStateDescriptor<Long, List<Long>> desc =
                new MapStateDescriptor<>("long-list", Long.class, (Class) List.class);

        List<Long> values = new ArrayList<>();
        values.add(123L); // < 2^31 → JSON round-trip yields Integer
        values.add(3_000_000_000L);
        backend.setCurrentKey(123L);
        backend.getMapState(desc).put(1000L, values);

        StateSnapshot snapshot = jsonRoundTrip(backend.snapshotState());

        MemoryKeyedStateBackend<Long> restored = new MemoryKeyedStateBackend<>(Long.class);
        restored.restoreState(snapshot);

        restored.setCurrentKey(123L);
        List<Long> restoredValues = restored.getMapState(desc).get(1000L);
        assertNotNull(restoredValues);
        assertEquals(2, restoredValues.size());
        assertEquals(Long.class, restoredValues.get(0).getClass(),
                "small Long element must be re-materialized to Long after JSON restore");
        assertEquals(Long.valueOf(123L), restoredValues.get(0));
        assertEquals(Long.valueOf(3_000_000_000L), restoredValues.get(1));

        backend.close();
        restored.close();
    }

    /**
     * Legacy snapshot (raw JSON array without element-type wrapper): restore must
     * not crash; the degraded container is preserved as-is and the degradation is
     * observable via LOG.warn (No-Silent-No-Op rule #24 — never silently return a
     * corrupt container without a trace).
     */
    @Test
    void testLegacyRawContainerRestoresWithoutCrash() throws Exception {
        Map<String, Object> stateData = new LinkedHashMap<>();
        stateData.put("keyType", Long.class.getName());

        Map<String, Object> info = new LinkedHashMap<>();
        info.put("stateType", "MapState");
        info.put("valueType", List.class.getName());
        info.put("mapKeyType", Long.class.getName());

        Map<String, Object> rawEvent = new LinkedHashMap<>();
        rawEvent.put("id", 42);
        rawEvent.put("name", "legacy");
        List<Object> rawList = new ArrayList<>();
        rawList.add(rawEvent);
        List<List<Object>> mapEntries = new ArrayList<>();
        mapEntries.add(java.util.Arrays.asList(1000L, rawList));

        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("namespace", "_default_");
        entry.put("key", 123L);
        entry.put("mapValue", mapEntries);

        List<Map<String, Object>> entries = new ArrayList<>();
        entries.add(entry);
        info.put("entries", entries);

        Map<String, Object> states = new LinkedHashMap<>();
        states.put("queue", info);
        stateData.put("states", states);

        MemoryKeyedStateBackend<Long> restored = new MemoryKeyedStateBackend<>(Long.class);
        restored.restoreState(new StateSnapshot(stateData));

        restored.setCurrentKey(123L);
        List<Event> restoredEvents = restored.getMapState(queueDescriptor()).get(1000L);
        assertNotNull(restoredEvents, "Legacy raw container must still restore (degraded)");
        assertEquals(1, restoredEvents.size());
        assertTrue(restoredEvents.get(0) instanceof Map,
                "Legacy container without element type info keeps JSON-native form (observed via LOG.warn)");

        restored.close();
    }

    /** @DataBean POJO element — required by the JsonTool serialization guard. */
    @DataBean
    public static class Event {
        private int id;
        private String name;

        public Event() {
        }

        public Event(int id, String name) {
            this.id = id;
            this.name = name;
        }

        public int getId() {
            return id;
        }

        public void setId(int id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            Event event = (Event) o;
            return id == event.id && Objects.equals(name, event.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, name);
        }
    }
}
