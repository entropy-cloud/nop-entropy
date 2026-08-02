/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.state.backend.rocksdb;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import io.nop.stream.core.common.accumulators.LongCounter;
import io.nop.stream.core.common.functions.AggregateFunction;
import io.nop.stream.core.common.state.AggregatingState;
import io.nop.stream.core.common.state.AggregatingStateDescriptor;
import io.nop.stream.core.common.state.InternalAppendingState;
import io.nop.stream.core.common.state.InternalListState;
import io.nop.stream.core.common.state.ListState;
import io.nop.stream.core.common.state.ListStateDescriptor;
import io.nop.stream.core.common.state.MapState;
import io.nop.stream.core.common.state.MapStateDescriptor;
import io.nop.stream.core.common.state.ReducingState;
import io.nop.stream.core.common.state.ReducingStateDescriptor;
import io.nop.stream.core.common.state.ValueState;
import io.nop.stream.core.common.state.ValueStateDescriptor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 2 tests: CRUD coverage for every keyed state type backed by RocksDB.
 * Verifies values persist to RocksDB (reopen backend and values are visible).
 */
class TestRocksDBStateTypes {

    @TempDir
    File tempDir;

    private RocksDBKeyedStateBackend<String> newBackend() {
        RocksDBStateBackend factory = new RocksDBStateBackend(tempDir.getAbsolutePath());
        return (RocksDBKeyedStateBackend<String>) factory.createKeyedStateBackend(String.class);
    }

    // ==================== ValueState ====================

    @Test
    void testValueStateCRUD() throws Exception {
        RocksDBKeyedStateBackend<String> backend = newBackend();
        backend.setCurrentKey("k1");
        ValueState<Long> state = backend.getState(new ValueStateDescriptor<>("vs", Long.class, 0L));

        assertEquals(0L, state.value());
        state.update(10L);
        assertEquals(10L, state.value());
        state.update(20L);
        assertEquals(20L, state.value());
        state.clear();
        assertEquals(0L, state.value());
        backend.close();
    }

    @Test
    void testValueStateKeySwitching() throws Exception {
        RocksDBKeyedStateBackend<String> backend = newBackend();
        ValueState<String> state = backend.getState(new ValueStateDescriptor<>("vs", String.class));

        backend.setCurrentKey("k1");
        state.update("a");
        backend.setCurrentKey("k2");
        state.update("b");
        backend.setCurrentKey("k1");
        assertEquals("a", state.value());
        backend.setCurrentKey("k2");
        assertEquals("b", state.value());
        backend.close();
    }

    // ==================== MapState ====================

    @Test
    void testMapStateCRUD() throws Exception {
        RocksDBKeyedStateBackend<String> backend = newBackend();
        backend.setCurrentKey("k1");
        MapState<String, Integer> state = backend.getMapState(
                new MapStateDescriptor<>("ms", String.class, Integer.class));

        assertTrue(state.isEmpty());
        state.put("a", 1);
        state.put("b", 2);
        state.put("c", 3);
        assertFalse(state.isEmpty());
        assertEquals(1, state.get("a"));
        assertTrue(state.contains("b"));

        int count = 0;
        for (Map.Entry<String, Integer> e : state.entries()) {
            count++;
        }
        assertEquals(3, count);

        state.remove("a");
        assertFalse(state.contains("a"));
        assertNull(state.get("a"));

        state.clear();
        assertTrue(state.isEmpty());
        backend.close();
    }

    @Test
    void testMapStateKeySwitching() throws Exception {
        RocksDBKeyedStateBackend<String> backend = newBackend();
        MapState<String, Integer> state = backend.getMapState(
                new MapStateDescriptor<>("ms", String.class, Integer.class));

        backend.setCurrentKey("k1");
        state.put("x", 100);
        backend.setCurrentKey("k2");
        state.put("x", 200);

        backend.setCurrentKey("k1");
        assertEquals(100, state.get("x"));
        backend.setCurrentKey("k2");
        assertEquals(200, state.get("x"));
        backend.close();
    }

    @Test
    void testMapStatePutAll() throws Exception {
        RocksDBKeyedStateBackend<String> backend = newBackend();
        backend.setCurrentKey("k1");
        MapState<String, Integer> state = backend.getMapState(
                new MapStateDescriptor<>("ms", String.class, Integer.class));

        Map<String, Integer> batch = new HashMap<>();
        batch.put("a", 1);
        batch.put("b", 2);
        state.putAll(batch);
        assertEquals(1, state.get("a"));
        assertEquals(2, state.get("b"));
        backend.close();
    }

    // ==================== ListState ====================

    @Test
    void testListStateCRUD() throws Exception {
        RocksDBKeyedStateBackend<String> backend = newBackend();
        backend.setCurrentKey("k1");
        ListState<String> state = backend.getListState(new ListStateDescriptor<>("ls", String.class));

        assertFalse(state.get().iterator().hasNext());
        state.add("a");
        state.add("b");
        state.add("c");

        int count = 0;
        for (String s : state.get()) {
            assertEquals("" + (char) ('a' + count), s);
            count++;
        }
        assertEquals(3, count);

        state.clear();
        assertFalse(state.get().iterator().hasNext());
        backend.close();
    }

    @Test
    void testListStateUpdate() throws Exception {
        RocksDBKeyedStateBackend<String> backend = newBackend();
        backend.setCurrentKey("k1");
        ListState<Integer> state = backend.getListState(new ListStateDescriptor<>("ls", Integer.class));

        state.add(1);
        state.add(2);
        state.update(java.util.Arrays.asList(10, 20, 30));

        int count = 0;
        for (int v : state.get()) {
            count++;
        }
        assertEquals(3, count);
        backend.close();
    }

    // ==================== ReducingState ====================

    @Test
    void testReducingStateCRUD() throws Exception {
        RocksDBKeyedStateBackend<String> backend = newBackend();
        backend.setCurrentKey("k1");
        ReducingState<Long> state = backend.getReducingState(
                new ReducingStateDescriptor<>("rs", Long.class, LongCounter.class));

        assertNull(state.get());
        state.add(10L);
        state.add(20L);
        state.add(30L);
        assertEquals(60L, state.get());

        state.clear();
        assertNull(state.get());
        backend.close();
    }

    // ==================== AggregatingState ====================

    @Test
    void testAggregatingStateCRUD() throws Exception {
        RocksDBKeyedStateBackend<String> backend = newBackend();
        backend.setCurrentKey("k1");
        AggregatingState<Long, Long> state = backend.getAggregatingState(
                new AggregatingStateDescriptor<>("ags", new SumAggFn(), long[].class));

        assertNull(state.get());
        state.add(10L);
        state.add(20L);
        state.add(30L);
        assertEquals(60L, state.get());

        state.clear();
        assertNull(state.get());
        backend.close();
    }

    // ==================== InternalListState (namespace isolation) ====================

    @Test
    void testInternalListStateNamespaceIsolation() throws Exception {
        RocksDBKeyedStateBackend<String> backend = newBackend();
        InternalListState<String, String, Integer> state =
                backend.getInternalListState(new ListStateDescriptor<>("ils", Integer.class));

        backend.setCurrentKey("k1");

        state.setCurrentNamespace("ns1");
        state.add(1);
        state.add(2);

        state.setCurrentNamespace("ns2");
        state.add(100);
        state.add(200);

        state.setCurrentNamespace("ns1");
        int count = 0;
        for (int v : state.get()) {
            count++;
        }
        assertEquals(2, count);

        state.setCurrentNamespace("ns2");
        count = 0;
        for (int v : state.get()) {
            count++;
        }
        assertEquals(2, count);

        state.setCurrentNamespace("ns1");
        state.clear();
        assertFalse(state.get().iterator().hasNext());

        state.setCurrentNamespace("ns2");
        assertTrue(state.get().iterator().hasNext());
        backend.close();
    }

    // ==================== InternalAppendingState (reducing) ====================

    @Test
    void testInternalAppendingStateNamespaceIsolation() throws Exception {
        RocksDBKeyedStateBackend<String> backend = newBackend();
        InternalAppendingState<String, String, Long, Long, Long> state =
                backend.getInternalAppendingState(
                        new ReducingStateDescriptor<>("ias", Long.class, LongCounter.class));

        backend.setCurrentKey("k1");

        state.setCurrentNamespace("ns1");
        state.add(10L);
        state.add(20L);

        state.setCurrentNamespace("ns2");
        state.add(100L);

        state.setCurrentNamespace("ns1");
        assertEquals(30L, state.get());

        state.setCurrentNamespace("ns2");
        assertEquals(100L, state.get());

        state.setCurrentNamespace("ns1");
        state.clear();
        assertNull(state.get());

        state.setCurrentNamespace("ns2");
        assertEquals(100L, state.get());
        backend.close();
    }

    // ==================== InternalAggregatingState ====================

    @Test
    void testInternalAggregatingStateNamespaceIsolation() throws Exception {
        RocksDBKeyedStateBackend<String> backend = newBackend();
        InternalAppendingState<String, String, Long, long[], Long> state =
                backend.getInternalAppendingState(
                        new AggregatingStateDescriptor<>("iags", new SumAggFn(), long[].class));

        backend.setCurrentKey("k1");

        state.setCurrentNamespace("ns1");
        state.add(10L);
        state.add(20L);

        state.setCurrentNamespace("ns2");
        state.add(100L);

        state.setCurrentNamespace("ns1");
        assertEquals(30L, state.get());

        state.setCurrentNamespace("ns2");
        assertEquals(100L, state.get());
        backend.close();
    }

    // ==================== Persist to RocksDB (reopen) ====================

    @Test
    void testValueStatePersistsAcrossReopen() throws Exception {
        String dbPath = tempDir.getAbsolutePath();
        RocksDBKeyedStateBackend<String> backend1 = new RocksDBKeyedStateBackend<>(dbPath, String.class, 1, null);
        backend1.setCurrentKey("persistKey");
        ValueState<Long> state1 = backend1.getState(new ValueStateDescriptor<>("persistVs", Long.class));
        state1.update(999L);
        backend1.close();

        RocksDBKeyedStateBackend<String> backend2 = new RocksDBKeyedStateBackend<>(dbPath, String.class, 1, null);
        backend2.setCurrentKey("persistKey");
        ValueState<Long> state2 = backend2.getState(new ValueStateDescriptor<>("persistVs", Long.class));
        assertEquals(999L, state2.value());
        backend2.close();
    }

    // ==================== Schema mismatch ====================

    @Test
    void testSchemaMismatchThrows() throws Exception {
        RocksDBKeyedStateBackend<String> backend = newBackend();
        backend.setCurrentKey("k1");
        backend.getState(new ValueStateDescriptor<>("schemaTest", Long.class));
        backend.close();

        RocksDBKeyedStateBackend<String> backend2 = new RocksDBKeyedStateBackend<>(
                tempDir.getAbsolutePath(), String.class, 1, null);
        backend2.setCurrentKey("k1");
        backend2.getState(new ValueStateDescriptor<>("schemaTest", Long.class));
        // Same type — should not throw

        // Different type on same name — should throw
        assertThrows(Exception.class, () -> {
            backend2.getState(new ValueStateDescriptor<>("schemaTest", String.class));
        });
        backend2.close();
    }

    // ==================== Helper ====================

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
