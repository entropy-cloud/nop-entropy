/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.rocksdb.metrics;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import io.nop.stream.rocksdb.RocksDBKeyedStateBackend;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Item 16 (P-REQ-8): RocksDB state-backend metrics recorder — non-empty
 * readings asserted at a real RocksDB handle, plus the backend-open wiring
 * (gauges registered into the nop-stream process composite registry).
 */
class TestRocksDBMetricsRecorder {

    private static RocksDB openRawDb(Path dir) {
        try (Options options = new Options().setCreateIfMissing(true)) {
            return RocksDB.open(options, dir.toString());
        } catch (Exception e) {
            throw new IllegalStateException("failed to open raw rocksdb at " + dir, e);
        }
    }

    @Test
    void testRecorderReadsNonEmptyProperties(@TempDir Path dir) throws Exception {
        RocksDB db = openRawDb(dir.resolve("raw"));
        try {
            db.put("k1".getBytes(StandardCharsets.UTF_8), "v1".getBytes(StandardCharsets.UTF_8));
            db.put("k2".getBytes(StandardCharsets.UTF_8), "v2".getBytes(StandardCharsets.UTF_8));

            MeterRegistry registry = new SimpleMeterRegistry();
            RocksDBMetricsRecorder recorder = new RocksDBMetricsRecorder(db);
            recorder.register(registry);

            double blockCache = registry.get(RocksDBMetricsRecorder.METRIC_BLOCK_CACHE_USAGE).gauge().value();
            double memtable = registry.get(RocksDBMetricsRecorder.METRIC_MEMTABLE_USAGE).gauge().value();
            double numKeys = registry.get(RocksDBMetricsRecorder.METRIC_ESTIMATE_NUM_KEYS).gauge().value();

            assertFalse(Double.isNaN(blockCache), "block cache usage must be readable");
            assertFalse(Double.isNaN(memtable), "memtable usage must be readable");
            assertFalse(Double.isNaN(numKeys), "estimate-num-keys must be readable");
            assertTrue(blockCache >= 0, "block cache usage non-negative");
            assertTrue(memtable > 0, "memtable usage positive after writes: " + memtable);
            assertTrue(numKeys >= 2, "estimate-num-keys reflects written entries: " + numKeys);

            assertEquals(memtable, recorder.readProperty(db, RocksDBMetricsRecorder.PROP_MEMTABLE_USAGE), 1e-9);
        } finally {
            db.close();
        }
    }

    @Test
    void testAllSixGaugesRegistered(@TempDir Path dir) {
        RocksDB db = openRawDb(dir.resolve("raw"));
        try {
            MeterRegistry registry = new SimpleMeterRegistry();
            new RocksDBMetricsRecorder(db).register(registry);

            List<String> missing = new ArrayList<>();
            for (String name : new String[]{
                    RocksDBMetricsRecorder.METRIC_BLOCK_CACHE_USAGE,
                    RocksDBMetricsRecorder.METRIC_BLOCK_CACHE_PINNED,
                    RocksDBMetricsRecorder.METRIC_MEMTABLE_USAGE,
                    RocksDBMetricsRecorder.METRIC_ESTIMATE_NUM_KEYS,
                    RocksDBMetricsRecorder.METRIC_COMPACTION_PENDING_BYTES,
                    RocksDBMetricsRecorder.METRIC_COMPACTIONS_RUNNING}) {
                if (registry.find(name).gauge() == null) {
                    missing.add(name);
                }
            }
            assertEquals(List.of(), missing, "all six rocksdb gauges registered");
        } finally {
            db.close();
        }
    }

    @Test
    void testBackendOpenPathRegistersIntoProcessComposite(@TempDir Path dir) {
        // The real backend-open path registers the gauges into the nop-stream
        // process composite registry (registration is unconditional and
        // side-effect-free; exposure is governed by the ops server).
        RocksDBKeyedStateBackend<String> backend = new RocksDBKeyedStateBackend<>(
                dir.resolve("db-wiring").toString(), String.class, 128, null);
        try {
            io.micrometer.core.instrument.Gauge gauge = io.nop.stream.core.metrics.StreamMetricsRegistries
                    .registry().find(RocksDBMetricsRecorder.METRIC_BLOCK_CACHE_USAGE).gauge();
            assertTrue(gauge != null, "open path must register rocksdb gauges into the composite registry");
        } finally {
            backend.close();
        }
    }

    @Test
    void testCloseDetachesGaugesFromNativeHandle(@TempDir Path dir) throws Exception {
        // backend.close() detaches the recorder BEFORE closing the native db —
        // afterwards the gauges report NaN instead of reading a closed handle
        // (a native read on a closed handle SIGSEGVs the JVM).
        RocksDBKeyedStateBackend<String> backend = new RocksDBKeyedStateBackend<>(
                dir.resolve("db-close").toString(), String.class, 128, null);
        MeterRegistry registry = new SimpleMeterRegistry();
        RocksDBMetricsRecorder recorder = new RocksDBMetricsRecorder(backend.getDbForTest());
        recorder.register(registry);
        assertFalse(Double.isNaN(registry.get(RocksDBMetricsRecorder.METRIC_BLOCK_CACHE_USAGE).gauge().value()),
                "live handle reads a real value");

        recorder.close();
        double afterClose = registry.get(RocksDBMetricsRecorder.METRIC_BLOCK_CACHE_USAGE).gauge().value();
        assertTrue(Double.isNaN(afterClose), "detached gauge reports NaN, got " + afterClose);
        backend.close();
    }
}
