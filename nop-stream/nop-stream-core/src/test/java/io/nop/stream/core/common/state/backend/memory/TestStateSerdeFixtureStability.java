package io.nop.stream.core.common.state.backend.memory;

import io.nop.core.lang.json.JsonTool;
import io.nop.stream.core.common.functions.AggregateFunction;
import io.nop.stream.core.common.state.AggregatingState;
import io.nop.stream.core.common.state.AggregatingStateDescriptor;
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
import io.nop.stream.core.common.accumulators.LongCounter;
import io.nop.stream.core.windowing.windows.TimeWindow;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 0830-3 Phase 1: pre-refactor snapshot fixture (two-way proof).
 *
 * <p>The frozen JSON resources under {@code state-serde-fixtures/} were produced
 * by the CURRENT (pre-convergence) MemoryStateSerDe write path. They pin the
 * snapshot byte format in both directions:
 * <ol>
 *   <li><b>read-compat</b>: the fixture restores successfully into a fresh
 *       backend (legacy snapshots keep working after the refactor);</li>
 *   <li><b>write-stability</b>: re-snapshotting the same state graph with the
 *       current code reproduces the fixture byte-for-byte (key order / field
 *       order drift on the write side cannot escape).</li>
 * </ol>
 *
 * <p>Capture-layer decision (Phase 1): the fixture serializes
 * {@code StateSnapshot.getStateData()} with {@code JsonTool.serialize(..., false)}
 * — the same JSON family the local-storage checkpoint persist uses. The artifact
 * is canonicalized before comparison because exactly two ordering sources are
 * JVM-run-dependent and therefore NOT part of the format contract: (a) the
 * per-state {@code entries} list order (HashMap iteration over
 * {@code TypedNamespaceAndKey}, whose hashCode mixes the namespace class's
 * identity hash) and (b) the state-name map order. Both sides of the comparison
 * run through the same {@link #canonicalJson(Map)} helper. Everything else
 * (field order within info/entry maps — insertion-ordered LinkedHashMaps,
 * mapValue pair order — String-keyed user maps) is stable and compared raw, so
 * field-order drift on the write side cannot escape. The incremental SST binary
 * path does not go through this SerDe and is covered by the four existing
 * incremental tests (out of fixture scope).
 */
class TestStateSerdeFixtureStability {

    /** No-arg-constructible aggregate function referenced by name inside the fixture. */
    public static class SumAggregate implements AggregateFunction<Long, Long, Long> {
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

    /** Deterministic state graph covering every keyed-state family. */
    static MemoryKeyedStateBackend<String> buildAllFamiliesBackend() throws Exception {
        MemoryKeyedStateBackend<String> backend = new MemoryKeyedStateBackend<>(String.class, 4);

        backend.setCurrentKey("k1");
        backend.getState(new ValueStateDescriptor<>("value-long", Long.class)).update(10L);
        backend.setCurrentKey("k2");
        backend.getState(new ValueStateDescriptor<>("value-long", Long.class)).update(20L);

        backend.setCurrentKey("k1");
        MapState<String, Long> ms = backend.getMapState(
                new MapStateDescriptor<>("map-str-long", String.class, Long.class));
        ms.put("a", 1L);
        ms.put("b", 2L);

        backend.setCurrentKey("k1");
        ListState<Long> ls = backend.getListState(new ListStateDescriptor<>("list-long", Long.class));
        ls.add(1L);
        ls.add(2L);
        ls.add(3L);

        InternalListState<String, TimeWindow, Long> ils =
                backend.getInternalListState(new ListStateDescriptor<>("internal-list", Long.class));
        ils.setCurrentNamespace(new TimeWindow(1000, 2000));
        ils.add(4L);
        ils.add(5L);
        ils.setCurrentNamespace(new TimeWindow(3000, 4000));
        ils.add(6L);

        backend.setCurrentKey("k1");
        backend.getReducingState(
                new ReducingStateDescriptor<>("reducing-long", Long.class, LongCounter.class)).add(12L);

        backend.setCurrentKey("k1");
        AggregatingState<Long, Long> as = backend.getAggregatingState(
                new AggregatingStateDescriptor<>("aggregating-sum", new SumAggregate(), Long.class));
        as.add(3L);
        as.add(4L);

        InternalAppendingState<String, TimeWindow, Long, Long, Long> ias =
                backend.getInternalAppendingState(new AggregatingStateDescriptor<>(
                        "internal-aggregating", new SumAggregate(), Long.class));
        ias.setCurrentNamespace(new TimeWindow(0, 100));
        ias.add(5L);

        InternalAppendingState<String, TimeWindow, Long, Long, Long> aps =
                backend.getInternalAppendingState(
                        new ReducingStateDescriptor<>("appending-counter", Long.class, LongCounter.class));
        aps.setCurrentNamespace(new TimeWindow(0, 100));
        aps.add(5L);
        aps.add(7L);

        return backend;
    }

    private static StateSnapshot snapshotOfAllFamilies() throws Exception {
        return buildAllFamiliesBackend().snapshotState();
    }

    @SuppressWarnings("unchecked")
    private static StateSnapshot readFixture(String name) {
        try {
            String json = Files.readString(fixturePath(name));
            Map<String, Object> data = (Map<String, Object>) JsonTool.parseNonStrict(json);
            return new StateSnapshot(data);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load fixture " + name, e);
        }
    }

    private static Path fixturePath(String name) {
        return Paths.get("src", "test", "resources", "state-serde-fixtures", name);
    }

    // ==================== read-compat (legacy snapshots keep restoring) ====================

    @Test
    void allFamiliesFixtureRestoresWithValues() throws Exception {
        MemoryKeyedStateBackend<String> backend = new MemoryKeyedStateBackend<>(String.class, 4);
        backend.restoreState(readFixture("all-families-memory.json"));

        backend.setCurrentKey("k1");
        assertEquals(Long.valueOf(10L), backend.getState(new ValueStateDescriptor<>("value-long", Long.class)).value());
        backend.setCurrentKey("k2");
        assertEquals(Long.valueOf(20L), backend.getState(new ValueStateDescriptor<>("value-long", Long.class)).value());

        backend.setCurrentKey("k1");
        MapState<String, Long> ms = backend.getMapState(
                new MapStateDescriptor<>("map-str-long", String.class, Long.class));
        assertEquals(Long.valueOf(1L), ms.get("a"));
        assertEquals(Long.valueOf(2L), ms.get("b"));

        ListState<Long> ls = backend.getListState(new ListStateDescriptor<>("list-long", Long.class));
        int i = 0;
        for (Long v : ls.get()) {
            assertEquals(Long.valueOf(i + 1), v);
            i++;
        }
        assertEquals(3, i);

        InternalListState<String, TimeWindow, Long> ils =
                backend.getInternalListState(new ListStateDescriptor<>("internal-list", Long.class));
        ils.setCurrentNamespace(new TimeWindow(1000, 2000));
        assertEquals(2, count(ils.get()));
        ils.setCurrentNamespace(new TimeWindow(3000, 4000));
        assertEquals(1, count(ils.get()));

        backend.setCurrentKey("k1");
        assertEquals(Long.valueOf(12L), backend.getReducingState(
                new ReducingStateDescriptor<>("reducing-long", Long.class, LongCounter.class)).get());

        AggregatingState<Long, Long> as = backend.getAggregatingState(
                new AggregatingStateDescriptor<>("aggregating-sum", new SumAggregate(), Long.class));
        assertEquals(Long.valueOf(7L), as.get());

        InternalAppendingState<String, TimeWindow, Long, Long, Long> ias =
                backend.getInternalAppendingState(new AggregatingStateDescriptor<>(
                        "internal-aggregating", new SumAggregate(), Long.class));
        ias.setCurrentNamespace(new TimeWindow(0, 100));
        assertEquals(Long.valueOf(5L), ias.get());

        InternalAppendingState<String, TimeWindow, Long, Long, Long> aps =
                backend.getInternalAppendingState(
                        new ReducingStateDescriptor<>("appending-counter", Long.class, LongCounter.class));
        aps.setCurrentNamespace(new TimeWindow(0, 100));
        assertEquals(Long.valueOf(12L), aps.get());
    }

    /**
     * S-12 semantics pinned by a frozen artifact: the legacy {@code *TypeName}
     * key spellings ({@code valueTypeName}/{@code accumulatorTypeName}/{@code
     * mapKeyTypeName}) restore successfully (fixture rewritten from a real
     * pre-refactor snapshot, not hand-written).
     */
    @Test
    void legacyTypeNameKeyFixtureRestores() throws Exception {
        MemoryKeyedStateBackend<String> backend = new MemoryKeyedStateBackend<>(String.class, 4);
        backend.restoreState(readFixture("legacy-type-name-keys.json"));

        backend.setCurrentKey("k1");
        assertEquals(Long.valueOf(10L), backend.getState(new ValueStateDescriptor<>("value-long", Long.class)).value());
        assertEquals(Long.valueOf(12L), backend.getReducingState(
                new ReducingStateDescriptor<>("reducing-long", Long.class, LongCounter.class)).get());

        MapState<String, Long> ms = backend.getMapState(
                new MapStateDescriptor<>("map-str-long", String.class, Long.class));
        assertEquals(Long.valueOf(1L), ms.get("a"));

        InternalAppendingState<String, TimeWindow, Long, Long, Long> aps =
                backend.getInternalAppendingState(
                        new ReducingStateDescriptor<>("appending-counter", Long.class, LongCounter.class));
        aps.setCurrentNamespace(new TimeWindow(0, 100));
        assertEquals(Long.valueOf(12L), aps.get());
    }

    // ==================== write-stability (byte-format drift cannot escape) ====================

    @Test
    void resnapshotIsByteIdenticalToFrozenFixture() throws Exception {
        String fresh = canonicalJson(snapshotOfAllFamilies().getStateData());
        String frozen = canonicalJson(readFixture("all-families-memory.json").getStateData());
        assertEquals(frozen, fresh,
                "re-snapshotting the same state graph must reproduce the frozen fixture byte-for-byte "
                        + "(key/field order drift on the snapshot write path is a format change)");
    }

    /**
     * Canonicalizes the two JVM-run-dependent orderings (entry list order,
     * state-name map order) while leaving every insertion-ordered map untouched.
     */
    @SuppressWarnings("unchecked")
    static String canonicalJson(Map<String, Object> stateData) {
        Map<String, Object> data = (Map<String, Object>) JsonTool.parseNonStrict(
                JsonTool.serialize(stateData, false));
        Map<String, Object> states = (Map<String, Object>) data.get("states");
        if (states != null) {
            data.put("states", new java.util.TreeMap<>(states));
            for (Object stateInfo : states.values()) {
                if (!(stateInfo instanceof Map)) {
                    continue;
                }
                Map<String, Object> info = (Map<String, Object>) stateInfo;
                Object entries = info.get("entries");
                if (entries instanceof List) {
                    List<Object> list = new java.util.ArrayList<>((List<Object>) entries);
                    list.sort(java.util.Comparator.comparing(e -> JsonTool.serialize(e, false)));
                    info.put("entries", list);
                }
            }
        }
        return JsonTool.serialize(data, false);
    }

    @Test
    void fixtureFileIsWellFormedAndCoversAllEightFamilies() throws Exception {
        Map<String, Object> data = readFixture("all-families-memory.json").getStateData();
        Map<String, Object> states = (Map<String, Object>) data.get("states");
        assertNotNull(states);
        assertEquals(8, states.size());
        assertTrue(states.containsKey("value-long"));
        assertTrue(states.containsKey("map-str-long"));
        assertTrue(states.containsKey("list-long"));
        assertTrue(states.containsKey("internal-list"));
        assertTrue(states.containsKey("reducing-long"));
        assertTrue(states.containsKey("aggregating-sum"));
        assertTrue(states.containsKey("internal-aggregating"));
        assertTrue(states.containsKey("appending-counter"));
    }

    private static int count(Iterable<?> iterable) {
        int n = 0;
        for (Object ignored : iterable) {
            n++;
        }
        return n;
    }
}
