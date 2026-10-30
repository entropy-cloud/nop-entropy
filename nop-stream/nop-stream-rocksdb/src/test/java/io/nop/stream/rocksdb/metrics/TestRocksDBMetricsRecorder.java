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

    /**
     * plan 2511 C1-002 / C10-002 rewrite: the gauge value function must fetch
     * the db reference INSIDE the recorder monitor. The white-box probe below
     * captures the exact value function handed to the registry and replays the
     * fetch-close-use race deterministically: the test thread holds the
     * recorder monitor while the scrape thread runs the value function and the
     * detach happens under the held monitor. A value function that evaluates
     * the reference outside the monitor (the pre-fix shape) carries the live
     * reference across the detach and reports a non-NaN value for a "closed"
     * recorder — the SIGSEGV window observed at test level without any real
     * native close.
     */
    @Test
    void testC1_002_GaugeFetchesDbRefInsideMonitorAcrossCloseWindow(@TempDir Path dir) throws Exception {
        RocksDB db = openRawDb(dir.resolve("raw-c1002"));
        try {
            db.put("k".getBytes(StandardCharsets.UTF_8), "v".getBytes(StandardCharsets.UTF_8));

            GaugeFunctionCaptureRegistry registry = new GaugeFunctionCaptureRegistry();
            RocksDBMetricsRecorder recorder = new RocksDBMetricsRecorder(db);
            recorder.register(registry);
            java.util.function.ToDoubleFunction<Object> valueFn = registry.capturedValueFunction();
            assertTrue(valueFn != null, "gauge value function must be captured");

            java.util.concurrent.atomic.AtomicReference<Object> scraped =
                    new java.util.concurrent.atomic.AtomicReference<>();
            java.util.concurrent.atomic.AtomicReference<String> scrapeStage =
                    new java.util.concurrent.atomic.AtomicReference<>("init");
            java.util.concurrent.atomic.AtomicReference<Throwable> scrapeError =
                    new java.util.concurrent.atomic.AtomicReference<>();
            java.util.concurrent.CountDownLatch scrapeStarted = new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.CountDownLatch scrapeDone = new java.util.concurrent.CountDownLatch(1);

            // Hold the recorder monitor so the scrape thread cannot reach the
            // reference read (post-fix) before the detach is applied.
            synchronized (recorder) {
                Thread scrape = new Thread(() -> {
                    scrapeStarted.countDown();
                    try {
                        scrapeStage.set("calling-value-fn");
                        double raw = valueFn.applyAsDouble(null);
                        scrapeStage.set("value-fn-returned");
                        scraped.set(raw);
                    } catch (Throwable t) {
                        scrapeError.set(t);
                    }
                    scrapeDone.countDown();
                });
                scrape.start();
                assertTrue(scrapeStarted.await(5, java.util.concurrent.TimeUnit.SECONDS),
                        "scrape thread must start");
                // Scrape is running; apply the detach exactly like close() does
                // — under the monitor the scrape thread needs.
                java.lang.reflect.Field refField = RocksDBMetricsRecorder.class
                        .getDeclaredField("dbRef");
                refField.setAccessible(true);
                @SuppressWarnings("unchecked")
                java.util.concurrent.atomic.AtomicReference<RocksDB> dbRef =
                        (java.util.concurrent.atomic.AtomicReference<RocksDB>) refField.get(recorder);
                dbRef.set(null);
            }
            assertTrue(scrapeDone.await(5, java.util.concurrent.TimeUnit.SECONDS),
                    "scrape must finish once the monitor is released, error="
                            + scrapeError.get() + ", stage=" + scrapeStage.get());
            Object result = scraped.get();
            assertTrue(result instanceof Double && Double.isNaN((Double) result),
                    "scrape across the close window must observe the detached (null) reference "
                            + "and report NaN, got " + result + ", error=" + scrapeError.get()
                            + ", stage=" + scrapeStage.get());
        } finally {
            db.close();
        }
    }

    /**
     * plan 2511 C1-002 / C10-002 stress leg: many rounds of concurrent
     * close×scrape interleaving on a live handle must never throw and must end
     * detached (NaN). Complements the deterministic monitor-window probe above.
     */
    @Test
    void testC1_002_CloseScrapeStressWindowStaysSafe(@TempDir Path dir) throws Exception {
        RocksDB db = openRawDb(dir.resolve("raw-c1002-stress"));
        try {
            db.put("k".getBytes(StandardCharsets.UTF_8), "v".getBytes(StandardCharsets.UTF_8));

            MeterRegistry registry = new SimpleMeterRegistry();
            RocksDBMetricsRecorder recorder = new RocksDBMetricsRecorder(db);
            recorder.register(registry);

            java.util.concurrent.atomic.AtomicReference<Throwable> scrapeFailure =
                    new java.util.concurrent.atomic.AtomicReference<>();
            java.util.concurrent.CountDownLatch stop = new java.util.concurrent.CountDownLatch(1);
            Thread scraper = new Thread(() -> {
                try {
                    while (stop.getCount() > 0) {
                        // Replayed gauge scrape through the registered meter.
                        registry.get(RocksDBMetricsRecorder.METRIC_BLOCK_CACHE_USAGE).gauge().value();
                        registry.get(RocksDBMetricsRecorder.METRIC_MEMTABLE_USAGE).gauge().value();
                    }
                } catch (Throwable t) {
                    scrapeFailure.set(t);
                } finally {
                    stop.countDown();
                }
            });
            scraper.start();
            for (int round = 0; round < 1000; round++) {
                recorder.close();
                // reopen the window: restore the live handle under the same
                // contract the backend close/open cycle provides.
                java.lang.reflect.Field refField = RocksDBMetricsRecorder.class
                        .getDeclaredField("dbRef");
                refField.setAccessible(true);
                @SuppressWarnings("unchecked")
                java.util.concurrent.atomic.AtomicReference<RocksDB> dbRef =
                        (java.util.concurrent.atomic.AtomicReference<RocksDB>) refField.get(recorder);
                dbRef.set(db);
            }
            stop.await(5, java.util.concurrent.TimeUnit.SECONDS);
            scraper.join(5000);
            assertTrue(scrapeFailure.get() == null,
                    "close×scrape interleaving must never surface an exception, got "
                            + scrapeFailure.get());
            recorder.close();
            double afterClose = registry.get(RocksDBMetricsRecorder.METRIC_BLOCK_CACHE_USAGE).gauge().value();
            assertTrue(Double.isNaN(afterClose), "detached gauge reports NaN, got " + afterClose);
        } finally {
            db.close();
        }
    }

    /** Captures the value function handed to {@code registry.gauge(...)} for white-box probes. */
    static final class GaugeFunctionCaptureRegistry extends SimpleMeterRegistry {
        private volatile java.util.function.ToDoubleFunction<Object> captured;

        @Override
        protected <T> io.micrometer.core.instrument.Gauge newGauge(io.micrometer.core.instrument.Meter.Id id,
                                                                   T obj,
                                                                   java.util.function.ToDoubleFunction<T> valueFunction) {
            if (captured == null) {
                captured = state -> valueFunction.applyAsDouble((T) state);
            }
            return super.newGauge(id, obj, valueFunction);
        }

        java.util.function.ToDoubleFunction<Object> capturedValueFunction() {
            return captured;
        }
    }
}
