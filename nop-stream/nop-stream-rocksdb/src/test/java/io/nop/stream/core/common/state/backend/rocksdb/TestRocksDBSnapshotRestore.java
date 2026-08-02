/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.state.backend.rocksdb;

import java.io.File;
import java.util.Map;

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
import io.nop.stream.core.common.state.backend.StateSnapshot;
import io.nop.stream.core.common.state.backend.memory.MemoryStateBackend;
import io.nop.stream.core.common.state.backend.IKeyedStateBackend;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

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
