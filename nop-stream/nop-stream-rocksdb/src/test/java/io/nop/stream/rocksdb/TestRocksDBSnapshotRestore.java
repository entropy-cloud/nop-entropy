/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.rocksdb;

import io.nop.stream.core.common.accumulators.LongCounter;
import io.nop.stream.core.common.functions.AggregateFunction;
import io.nop.stream.core.common.state.AggregatingState;
import io.nop.stream.core.common.state.AggregatingStateDescriptor;
import io.nop.stream.core.common.state.InternalAppendingState;
import io.nop.stream.core.common.state.InternalListState;
import io.nop.stream.core.common.state.ListStateDescriptor;
import io.nop.stream.core.common.state.MapState;
import io.nop.stream.core.common.state.MapStateDescriptor;
import io.nop.stream.core.common.state.ReducingState;
import io.nop.stream.core.common.state.ReducingStateDescriptor;
import io.nop.stream.core.common.state.ValueState;
import io.nop.stream.core.common.state.ValueStateDescriptor;
import io.nop.stream.core.common.state.backend.IKeyedStateBackend;
import io.nop.stream.core.common.state.backend.StateSnapshot;
import io.nop.stream.core.common.state.backend.memory.MemoryStateBackend;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 3 tests: RocksDB snapshot/restore round-trip, cross-backend
 * interchange (Memory ↔ RocksDB), schemaChecksum parity, and shardCount > 1
 * cross-backend compatibility.
 */
class TestRocksDBSnapshotRestore {

    @TempDir
    File tempDir;

    private RocksDBKeyedStateBackend<String> newRocksBackend() {
        return new RocksDBKeyedStateBackend<>(tempDir.getAbsolutePath(), String.class, 1, null);
    }

    private RocksDBKeyedStateBackend<String> newRocksBackend(int shardCount, String subPath) {
        File sub = new File(tempDir, subPath);
        sub.mkdirs();
        return new RocksDBKeyedStateBackend<>(sub.getAbsolutePath(), String.class, shardCount, null);
    }

    @SuppressWarnings("unchecked")
    private IKeyedStateBackend<String> newMemoryBackend() {
        return (IKeyedStateBackend<String>) new MemoryStateBackend().createKeyedStateBackend(String.class);
    }

    @SuppressWarnings("unchecked")
    private IKeyedStateBackend<String> newMemoryBackend(int shardCount) {
        return (IKeyedStateBackend<String>) new MemoryStateBackend(shardCount).createKeyedStateBackend(String.class);
    }

    // ==================== ValueState ====================

    @Test
    void testValueStateRoundTrip() throws Exception {
        RocksDBKeyedStateBackend<String> backend = newRocksBackend();
        backend.setCurrentKey("k1");
        backend.getState(new ValueStateDescriptor<>("vs", Long.class)).update(10L);
        backend.setCurrentKey("k2");
        backend.getState(new ValueStateDescriptor<>("vs", Long.class)).update(20L);
        StateSnapshot snapshot = backend.snapshotState();
        backend.close();

        RocksDBKeyedStateBackend<String> restored = newRocksBackend();
        restored.restoreState(snapshot);
        restored.setCurrentKey("k1");
        assertEquals(10L, restored.getState(new ValueStateDescriptor<>("vs", Long.class)).value());
        restored.setCurrentKey("k2");
        assertEquals(20L, restored.getState(new ValueStateDescriptor<>("vs", Long.class)).value());
        restored.close();
    }

    @Test
    void testValueStateMemoryToRocks() throws Exception {
        IKeyedStateBackend<String> mem = newMemoryBackend();
        mem.setCurrentKey("k1");
        mem.getState(new ValueStateDescriptor<>("vs", String.class)).update("hello");
        StateSnapshot snapshot = mem.snapshotState();

        RocksDBKeyedStateBackend<String> rocks = newRocksBackend();
        rocks.restoreState(snapshot);
        rocks.setCurrentKey("k1");
        assertEquals("hello", rocks.getState(new ValueStateDescriptor<>("vs", String.class)).value());
        rocks.close();
    }

    @Test
    void testValueStateRocksToMemory() throws Exception {
        RocksDBKeyedStateBackend<String> rocks = newRocksBackend();
        rocks.setCurrentKey("k1");
        rocks.getState(new ValueStateDescriptor<>("vs", Integer.class)).update(42);
        StateSnapshot snapshot = rocks.snapshotState();
        rocks.close();

        IKeyedStateBackend<String> mem = newMemoryBackend();
        mem.restoreState(snapshot);
        mem.setCurrentKey("k1");
        assertEquals(42, mem.getState(new ValueStateDescriptor<>("vs", Integer.class)).value());
    }

    // ==================== MapState ====================

    @Test
    void testMapStateRoundTrip() throws Exception {
        RocksDBKeyedStateBackend<String> backend = newRocksBackend();
        backend.setCurrentKey("k1");
        MapState<String, Long> ms = backend.getMapState(
                new MapStateDescriptor<>("ms", String.class, Long.class));
        ms.put("a", 1L);
        ms.put("b", 2L);
        StateSnapshot snapshot = backend.snapshotState();
        backend.close();

        RocksDBKeyedStateBackend<String> restored = newRocksBackend();
        restored.restoreState(snapshot);
        restored.setCurrentKey("k1");
        MapState<String, Long> ms2 = restored.getMapState(
                new MapStateDescriptor<>("ms", String.class, Long.class));
        assertEquals(1L, ms2.get("a"));
        assertEquals(2L, ms2.get("b"));
        assertNull(ms2.get("c"));
        restored.close();
    }

    @Test
    void testMapStateMemoryToRocks() throws Exception {
        IKeyedStateBackend<String> mem = newMemoryBackend();
        mem.setCurrentKey("k1");
        mem.getMapState(new MapStateDescriptor<>("ms", String.class, Integer.class)).put("x", 99);
        StateSnapshot snapshot = mem.snapshotState();

        RocksDBKeyedStateBackend<String> rocks = newRocksBackend();
        rocks.restoreState(snapshot);
        rocks.setCurrentKey("k1");
        assertEquals(99, rocks.getMapState(
                new MapStateDescriptor<>("ms", String.class, Integer.class)).get("x"));
        rocks.close();
    }

    // ==================== ListState ====================

    @Test
    void testListStateRoundTrip() throws Exception {
        RocksDBKeyedStateBackend<String> backend = newRocksBackend();
        backend.setCurrentKey("k1");
        backend.getListState(new ListStateDescriptor<>("ls", String.class)).add("a");
        backend.getListState(new ListStateDescriptor<>("ls", String.class)).add("b");
        StateSnapshot snapshot = backend.snapshotState();
        backend.close();

        RocksDBKeyedStateBackend<String> restored = newRocksBackend();
        restored.restoreState(snapshot);
        restored.setCurrentKey("k1");
        int count = 0;
        for (String s : restored.getListState(new ListStateDescriptor<>("ls", String.class)).get()) {
            count++;
        }
        assertEquals(2, count);
        restored.close();
    }

    // ==================== ReducingState ====================

    @Test
    void testReducingStateRoundTrip() throws Exception {
        RocksDBKeyedStateBackend<String> backend = newRocksBackend();
        backend.setCurrentKey("k1");
        ReducingState<Long> rs = backend.getReducingState(
                new ReducingStateDescriptor<>("rs", Long.class, LongCounter.class));
        rs.add(10L);
        rs.add(20L);
        StateSnapshot snapshot = backend.snapshotState();
        backend.close();

        RocksDBKeyedStateBackend<String> restored = newRocksBackend();
        restored.restoreState(snapshot);
        restored.setCurrentKey("k1");
        assertEquals(30L, restored.getReducingState(
                new ReducingStateDescriptor<>("rs", Long.class, LongCounter.class)).get());
        restored.close();
    }

    @Test
    void testReducingStateRocksToMemory() throws Exception {
        RocksDBKeyedStateBackend<String> rocks = newRocksBackend();
        rocks.setCurrentKey("k1");
        ReducingState<Long> rs = rocks.getReducingState(
                new ReducingStateDescriptor<>("rs", Long.class, LongCounter.class));
        rs.add(5L);
        rs.add(15L);
        StateSnapshot snapshot = rocks.snapshotState();
        rocks.close();

        IKeyedStateBackend<String> mem = newMemoryBackend();
        mem.restoreState(snapshot);
        mem.setCurrentKey("k1");
        assertEquals(20L, mem.getReducingState(
                new ReducingStateDescriptor<>("rs", Long.class, LongCounter.class)).get());
    }

    // ==================== AggregatingState ====================

    @Test
    void testAggregatingStateRoundTrip() throws Exception {
        RocksDBKeyedStateBackend<String> backend = newRocksBackend();
        backend.setCurrentKey("k1");
        AggregatingState<Long, Long> as = backend.getAggregatingState(
                new AggregatingStateDescriptor<>("ags", new SumAggFn(), long[].class));
        as.add(10L);
        as.add(50L);
        StateSnapshot snapshot = backend.snapshotState();
        backend.close();

        RocksDBKeyedStateBackend<String> restored = newRocksBackend();
        restored.restoreState(snapshot);
        restored.setCurrentKey("k1");
        assertEquals(60L, restored.getAggregatingState(
                new AggregatingStateDescriptor<>("ags", new SumAggFn(), long[].class)).get());
        restored.close();
    }

    // ==================== InternalListState ====================

    @Test
    void testInternalListStateRoundTrip() throws Exception {
        RocksDBKeyedStateBackend<String> backend = newRocksBackend();
        backend.setCurrentKey("k1");
        InternalListState<String, String, Integer> ils = backend.getInternalListState(
                new ListStateDescriptor<>("ils", Integer.class));
        ils.setCurrentNamespace("ns1");
        ils.add(1);
        ils.setCurrentNamespace("ns2");
        ils.add(2);
        StateSnapshot snapshot = backend.snapshotState();
        backend.close();

        RocksDBKeyedStateBackend<String> restored = newRocksBackend();
        restored.restoreState(snapshot);
        restored.setCurrentKey("k1");
        InternalListState<String, String, Integer> ils2 = restored.getInternalListState(
                new ListStateDescriptor<>("ils", Integer.class));
        ils2.setCurrentNamespace("ns1");
        assertTrue(ils2.get().iterator().hasNext());
        ils2.setCurrentNamespace("ns2");
        assertTrue(ils2.get().iterator().hasNext());
        restored.close();
    }

    // ==================== InternalAppendingState (reducing) ====================

    @Test
    void testInternalAppendingStateRoundTrip() throws Exception {
        RocksDBKeyedStateBackend<String> backend = newRocksBackend();
        backend.setCurrentKey("k1");
        InternalAppendingState<String, String, Long, Long, Long> ias = backend.getInternalAppendingState(
                new ReducingStateDescriptor<>("ias", Long.class, LongCounter.class));
        ias.setCurrentNamespace("ns1");
        ias.add(10L);
        ias.add(20L);
        StateSnapshot snapshot = backend.snapshotState();
        backend.close();

        RocksDBKeyedStateBackend<String> restored = newRocksBackend();
        restored.restoreState(snapshot);
        restored.setCurrentKey("k1");
        InternalAppendingState<String, String, Long, Long, Long> ias2 = restored.getInternalAppendingState(
                new ReducingStateDescriptor<>("ias", Long.class, LongCounter.class));
        ias2.setCurrentNamespace("ns1");
        assertEquals(30L, ias2.get());
        restored.close();
    }

    @Test
    void testAppendingStateRocksToMemory() throws Exception {
        RocksDBKeyedStateBackend<String> rocks = newRocksBackend();
        rocks.setCurrentKey("k1");
        InternalAppendingState<String, String, Long, Long, Long> ias = rocks.getInternalAppendingState(
                new ReducingStateDescriptor<>("ias", Long.class, LongCounter.class));
        ias.setCurrentNamespace("ns1");
        ias.add(7L);
        ias.add(3L);
        StateSnapshot snapshot = rocks.snapshotState();
        rocks.close();

        IKeyedStateBackend<String> mem = newMemoryBackend();
        mem.restoreState(snapshot);
        mem.setCurrentKey("k1");
        // Memory backend must also support IInternalStateBackend
        InternalAppendingState<String, String, Long, Long, Long> ias2 =
                ((io.nop.stream.core.common.state.backend.IInternalStateBackend<String>)
                        mem).getInternalAppendingState(
                        new ReducingStateDescriptor<>("ias", Long.class, LongCounter.class));
        ias2.setCurrentNamespace("ns1");
        assertEquals(10L, ias2.get());
    }

    // ==================== InternalAggregatingState ====================

    @Test
    void testInternalAggregatingStateRoundTrip() throws Exception {
        RocksDBKeyedStateBackend<String> backend = newRocksBackend();
        backend.setCurrentKey("k1");
        InternalAppendingState<String, String, Long, long[], Long> ias = backend.getInternalAppendingState(
                new AggregatingStateDescriptor<>("iags", new SumAggFn(), long[].class));
        ias.setCurrentNamespace("ns1");
        ias.add(10L);
        ias.add(20L);
        StateSnapshot snapshot = backend.snapshotState();
        backend.close();

        RocksDBKeyedStateBackend<String> restored = newRocksBackend();
        restored.restoreState(snapshot);
        restored.setCurrentKey("k1");
        InternalAppendingState<String, String, Long, long[], Long> ias2 = restored.getInternalAppendingState(
                new AggregatingStateDescriptor<>("iags", new SumAggFn(), long[].class));
        ias2.setCurrentNamespace("ns1");
        assertEquals(30L, ias2.get());
        restored.close();
    }

    // ==================== Schema checksum parity ====================

    @Test
    void testSchemaChecksumParity() throws Exception {
        IKeyedStateBackend<String> mem = newMemoryBackend();
        mem.setCurrentKey("k1");
        mem.getState(new ValueStateDescriptor<>("parityVs", Long.class)).update(1L);
        StateSnapshot memSnapshot = mem.snapshotState();

        RocksDBKeyedStateBackend<String> rocks = newRocksBackend();
        rocks.setCurrentKey("k1");
        rocks.getState(new ValueStateDescriptor<>("parityVs", Long.class)).update(1L);
        StateSnapshot rocksSnapshot = rocks.snapshotState();
        rocks.close();

        String memChecksum = getSchemaChecksum(memSnapshot, "parityVs");
        String rocksChecksum = getSchemaChecksum(rocksSnapshot, "parityVs");
        assertEquals(memChecksum, rocksChecksum,
                "Schema checksum must match between memory and RocksDB backends");
    }

    // ==================== shardCount > 1 cross-backend ====================

    @Test
    void testShardedMemoryToRocks() throws Exception {
        IKeyedStateBackend<String> mem = newMemoryBackend(3);
        mem.setCurrentKey("k1");
        mem.getState(new ValueStateDescriptor<>("svs", Long.class)).update(111L);
        mem.setCurrentKey("k2");
        mem.getState(new ValueStateDescriptor<>("svs", Long.class)).update(222L);
        mem.setCurrentKey("k3");
        mem.getState(new ValueStateDescriptor<>("svs", Long.class)).update(333L);
        StateSnapshot snapshot = mem.snapshotState();

        RocksDBKeyedStateBackend<String> rocks = newRocksBackend(3, "shard-m2r");
        rocks.restoreState(snapshot);
        rocks.setCurrentKey("k1");
        assertEquals(111L, rocks.getState(new ValueStateDescriptor<>("svs", Long.class)).value());
        rocks.setCurrentKey("k2");
        assertEquals(222L, rocks.getState(new ValueStateDescriptor<>("svs", Long.class)).value());
        rocks.setCurrentKey("k3");
        assertEquals(333L, rocks.getState(new ValueStateDescriptor<>("svs", Long.class)).value());
        rocks.close();
    }

    @Test
    void testShardedRocksToMemory() throws Exception {
        RocksDBKeyedStateBackend<String> rocks = newRocksBackend(2, "shard-r2m");
        rocks.setCurrentKey("k1");
        rocks.getState(new ValueStateDescriptor<>("svs", Long.class)).update(10L);
        rocks.setCurrentKey("k2");
        rocks.getState(new ValueStateDescriptor<>("svs", Long.class)).update(20L);
        StateSnapshot snapshot = rocks.snapshotState();
        rocks.close();

        IKeyedStateBackend<String> mem = newMemoryBackend(2);
        mem.restoreState(snapshot);
        mem.setCurrentKey("k1");
        assertEquals(10L, mem.getState(new ValueStateDescriptor<>("svs", Long.class)).value());
        mem.setCurrentKey("k2");
        assertEquals(20L, mem.getState(new ValueStateDescriptor<>("svs", Long.class)).value());
    }

    // ==================== All 8 types round-trip together ====================

    @Test
    void testAllStateTypesRoundTrip() throws Exception {
        RocksDBKeyedStateBackend<String> backend = newRocksBackend();
        backend.setCurrentKey("k1");
        backend.getState(new ValueStateDescriptor<>("v", Long.class)).update(1L);
        backend.getMapState(new MapStateDescriptor<>("m", String.class, Long.class)).put("a", 1L);
        backend.getListState(new ListStateDescriptor<>("l", String.class)).add("x");
        backend.getReducingState(new ReducingStateDescriptor<>("r", Long.class, LongCounter.class)).add(5L);
        backend.getAggregatingState(
                new AggregatingStateDescriptor<>("a", new SumAggFn(), long[].class)).add(10L);

        InternalListState<String, String, String> ils = backend.getInternalListState(
                new ListStateDescriptor<>("il", String.class));
        ils.setCurrentNamespace("ns");
        ils.add("il-val");

        InternalAppendingState<String, String, Long, Long, Long> iap = backend.getInternalAppendingState(
                new ReducingStateDescriptor<>("iap", Long.class, LongCounter.class));
        iap.setCurrentNamespace("ns");
        iap.add(3L);

        InternalAppendingState<String, String, Long, long[], Long> iag = backend.getInternalAppendingState(
                new AggregatingStateDescriptor<>("iag", new SumAggFn(), long[].class));
        iag.setCurrentNamespace("ns");
        iag.add(7L);

        StateSnapshot snapshot = backend.snapshotState();
        assertNotNull(snapshot);
        assertFalse(snapshot.isEmpty());
        backend.close();

        RocksDBKeyedStateBackend<String> restored = newRocksBackend();
        restored.restoreState(snapshot);
        restored.setCurrentKey("k1");
        assertEquals(1L, restored.getState(new ValueStateDescriptor<>("v", Long.class)).value());
        assertEquals(1L, restored.getMapState(
                new MapStateDescriptor<>("m", String.class, Long.class)).get("a"));
        assertTrue(restored.getListState(new ListStateDescriptor<>("l", String.class)).get().iterator().hasNext());
        assertEquals(5L, restored.getReducingState(
                new ReducingStateDescriptor<>("r", Long.class, LongCounter.class)).get());
        assertEquals(10L, restored.getAggregatingState(
                new AggregatingStateDescriptor<>("a", new SumAggFn(), long[].class)).get());

        InternalListState<String, String, String> ils2 = restored.getInternalListState(
                new ListStateDescriptor<>("il", String.class));
        ils2.setCurrentNamespace("ns");
        assertTrue(ils2.get().iterator().hasNext());

        InternalAppendingState<String, String, Long, Long, Long> iap2 = restored.getInternalAppendingState(
                new ReducingStateDescriptor<>("iap", Long.class, LongCounter.class));
        iap2.setCurrentNamespace("ns");
        assertEquals(3L, iap2.get());

        InternalAppendingState<String, String, Long, long[], Long> iag2 = restored.getInternalAppendingState(
                new AggregatingStateDescriptor<>("iag", new SumAggFn(), long[].class));
        iag2.setCurrentNamespace("ns");
        assertEquals(7L, iag2.get());
        restored.close();
    }

    // ==================== Container-value MapState (P1-21-01) ====================

    /**
     * P1-21-01: the RocksDB runtime path itself must preserve container-value inner
     * element types (the JSON byte round trip loses them without the element-type
     * wrapper — the audit showed RocksDB was damaged even without checkpoints).
     */
    @Test
    void testMapStateContainerValueRuntimePutGet() throws Exception {
        RocksDBKeyedStateBackend<String> backend = newRocksBackend();
        backend.setCurrentKey("k1");
        MapState<String, List<Event>> ms = backend.getMapState(
                new MapStateDescriptor<>("cq", String.class, (Class) List.class));
        List<Event> events = new ArrayList<>();
        events.add(new Event(42, "start42"));
        ms.put("t1", events);

        List<Event> restored = ms.get("t1");
        assertEquals(Event.class, restored.get(0).getClass(),
                "RocksDB runtime get() must return Event elements, not LinkedHashMap");
        assertEquals("start42", restored.get(0).getName());

        backend.close();
    }

    /**
     * P1-21-01: RocksDB container-value MapState survives its own JSON checkpoint
     * round trip with inner element types intact.
     */
    @Test
    void testMapStateContainerValueJsonRoundTrip() throws Exception {
        RocksDBKeyedStateBackend<String> backend = newRocksBackend();
        backend.setCurrentKey("k1");
        MapState<String, List<Event>> ms = backend.getMapState(
                new MapStateDescriptor<>("cq", String.class, (Class) List.class));
        List<Event> events = new ArrayList<>();
        events.add(new Event(7, "inner"));
        ms.put("t1", events);
        StateSnapshot snapshot = backend.snapshotState();
        backend.close();

        String json = io.nop.core.lang.json.JsonTool.serialize(snapshot.getStateData(), false);
        Map<String, Object> parsed = io.nop.core.lang.json.JsonTool.parseMap(json);
        StateSnapshot persisted = new StateSnapshot(parsed);

        RocksDBKeyedStateBackend<String> restored = newRocksBackend();
        restored.restoreState(persisted);
        restored.setCurrentKey("k1");
        MapState<String, List<Event>> ms2 = restored.getMapState(
                new MapStateDescriptor<>("cq", String.class, (Class) List.class));
        List<Event> restoredEvents = ms2.get("t1");
        assertEquals(Event.class, restoredEvents.get(0).getClass(),
                "RocksDB container-value elements must survive the JSON checkpoint round trip");
        assertEquals("inner", restoredEvents.get(0).getName());
        restored.close();
    }

    /**
     * P1-21-01 cross-backend consistency: the SAME JSON-persisted checkpoint with
     * container values must restore identically on the Memory and RocksDB backends
     * (the audit pinned "same snapshot, both backends" for the numeric-key face; this
     * pins the container-value face).
     */
    @SuppressWarnings("unchecked")
    @Test
    void testContainerValueSnapshotRestoresIdenticallyAcrossBackends() throws Exception {
        IKeyedStateBackend<Long> mem = new MemoryStateBackend().createKeyedStateBackend(Long.class);
        mem.setCurrentKey(123L);
        List<Event> events = new ArrayList<>();
        events.add(new Event(42, "start42"));
        mem.getMapState(new MapStateDescriptor<>("queue", Long.class, (Class) List.class))
                .put(1000L, events);
        StateSnapshot snapshot = mem.snapshotState();

        String json = io.nop.core.lang.json.JsonTool.serialize(snapshot.getStateData(), false);
        Map<String, Object> parsed = io.nop.core.lang.json.JsonTool.parseMap(json);
        StateSnapshot persisted = new StateSnapshot(parsed);

        RocksDBKeyedStateBackend<Long> rocks = new RocksDBKeyedStateBackend<>(tempDir.getAbsolutePath(), Long.class, 1, null);
        rocks.restoreState(persisted);
        rocks.setCurrentKey(123L);
        List<Event> rocksEvents = (List<Event>) (List<?>) rocks.getMapState(
                new MapStateDescriptor<>("queue", Long.class, (Class) List.class)).get(1000L);
        assertEquals(Event.class, rocksEvents.get(0).getClass(),
                "RocksDB backend must re-materialize container-value elements (same snapshot as Memory)");
        assertEquals("start42", rocksEvents.get(0).getName());

        IKeyedStateBackend<Long> memRestored = new MemoryStateBackend().createKeyedStateBackend(Long.class);
        memRestored.restoreState(persisted);
        memRestored.setCurrentKey(123L);
        List<Event> memEvents = (List<Event>) (List<?>) memRestored.getMapState(
                new MapStateDescriptor<>("queue", Long.class, (Class) List.class)).get(1000L);
        assertEquals(Event.class, memEvents.get(0).getClass(),
                "Memory backend must re-materialize container-value elements (same snapshot as RocksDB)");
        assertEquals("start42", memEvents.get(0).getName());

        rocks.close();
    }

    /** @DataBean POJO element — required by the JsonTool serialization guard. */
    @io.nop.api.core.annotations.data.DataBean
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
    }

    // ==================== Numeric-key cross-backend consistency (AR-01) ====================

    /**
     * AR-01 (P0): the same JSON-persisted checkpoint must restore identically on
     * the Memory and RocksDB backends for numeric (Long) keys. Pre-fix the Memory
     * backend lost Long keys &lt; 2^31 silently (JSON parse returns Integer, the
     * class-sensitive {@code TypedNamespaceAndKey.equals} missed live Long
     * lookups) while RocksDB re-materialized keys by keyType and restored
     * correctly — a cross-backend fork on the DEFAULT backend path.
     *
     * <p>The snapshot is taken on the Memory backend, run through the exact
     * storage-layer JSON round trip ({@code JsonTool.serialize} →
     * {@code JsonTool.parseMap}), and restored on BOTH backends: each must hit
     * the same keys with the same values.
     */
    @SuppressWarnings("unchecked")
    @Test
    void testNumericKeySnapshotRestoresIdenticallyAcrossBackends() throws Exception {
        IKeyedStateBackend<Long> mem = new MemoryStateBackend().createKeyedStateBackend(Long.class);
        mem.setCurrentKey(123L);            // < 2^31 → JSON round-trip yields Integer
        mem.getState(new ValueStateDescriptor<>("vs", Long.class)).update(10L);
        mem.setCurrentKey(3_000_000_000L);  // > 2^31 → survives as Long
        mem.getState(new ValueStateDescriptor<>("vs", Long.class)).update(20L);
        StateSnapshot snapshot = mem.snapshotState();

        String json = io.nop.core.lang.json.JsonTool.serialize(snapshot.getStateData(), false);
        Map<String, Object> parsed = io.nop.core.lang.json.JsonTool.parseMap(json);
        StateSnapshot persisted = new StateSnapshot(parsed);

        RocksDBKeyedStateBackend<Long> rocks = new RocksDBKeyedStateBackend<>(tempDir.getAbsolutePath(), Long.class, 1, null);
        rocks.restoreState(persisted);
        rocks.setCurrentKey(123L);
        assertEquals(10L, rocks.getState(new ValueStateDescriptor<>("vs", Long.class)).value(),
                "RocksDB backend must hit Long key < 2^31 after JSON checkpoint restore");
        rocks.setCurrentKey(3_000_000_000L);
        assertEquals(20L, rocks.getState(new ValueStateDescriptor<>("vs", Long.class)).value(),
                "RocksDB backend must hit Long key > 2^31 after JSON checkpoint restore");

        IKeyedStateBackend<Long> memRestored = new MemoryStateBackend().createKeyedStateBackend(Long.class);
        memRestored.restoreState(persisted);
        memRestored.setCurrentKey(123L);
        assertEquals(10L, memRestored.getState(new ValueStateDescriptor<>("vs", Long.class)).value(),
                "Memory backend must hit Long key < 2^31 after JSON checkpoint restore (same as RocksDB)");
        memRestored.setCurrentKey(3_000_000_000L);
        assertEquals(20L, memRestored.getState(new ValueStateDescriptor<>("vs", Long.class)).value(),
                "Memory backend must hit Long key > 2^31 after JSON checkpoint restore (same as RocksDB)");

        rocks.close();
    }

    // ==================== Helper ====================

    @SuppressWarnings("unchecked")
    private String getSchemaChecksum(StateSnapshot snapshot, String stateName) {
        Map<String, Object> states = (Map<String, Object>) snapshot.getStateData().get("states");
        Map<String, Object> info = (Map<String, Object>) states.get(stateName);
        return (String) info.get("schemaChecksum");
    }

    static class SumAggFn implements AggregateFunction<Long, long[], Long> {
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
