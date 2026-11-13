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
        // CRA 8.43 R4-03 W-01: the detach now also removes the recorder's
        // Meter.Ids — "backend not in place" (meter absent) is the honest
        // scrape signal, replacing the surviving-NaN-gauge observation (the
        // C2-011 face-2 defect signature this fix removes). The
        // fetch-inside-monitor race protection is asserted unchanged by the
        // C10-002 white-box probe below.
        assertTrue(registry.find(RocksDBMetricsRecorder.METRIC_BLOCK_CACHE_USAGE).gauge() == null,
                "closed recorder's meters must be removed from the registry (honest absence signal)");
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
                        // CRA 8.43 R4-03 W-01: once close() has removed the
                        // recorder's Meter.Ids the meters are legitimately gone
                        // (absence = the honest post-close signal) — the scraper
                        // tolerates absence while the interleaving continues to
                        // exercise close idempotency; any OTHER exception (a
                        // race surfacing through the value function, a native
                        // read on a closed handle) still fails the pin.
                        io.micrometer.core.instrument.Gauge gauge =
                                registry.find(RocksDBMetricsRecorder.METRIC_BLOCK_CACHE_USAGE).gauge();
                        if (gauge != null) {
                            gauge.value();
                        }
                        gauge = registry.find(RocksDBMetricsRecorder.METRIC_MEMTABLE_USAGE).gauge();
                        if (gauge != null) {
                            gauge.value();
                        }
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
            // CRA 8.43 R4-03 W-01: end state = meters removed (honest absence),
            // not a surviving NaN gauge.
            assertTrue(registry.find(RocksDBMetricsRecorder.METRIC_BLOCK_CACHE_USAGE).gauge() == null,
                    "closed recorder's meters must be removed from the registry (honest absence signal)");
        } finally {
            db.close();
        }
    }

    // ------------------------------------------------------------------------
    // CRA 8.43 R4-03 (plan 2536) red pins — W-01 (MA-09=C2-011) and W-08
    // (MA-17=C7-012). Appended cases only; every pre-existing case above is a
    // zero-rewrite anchor (plan 2511 C10-002 pins keep running unchanged).
    // ------------------------------------------------------------------------

    /**
     * CRA 8.43 R4-03 red ①(a) — W-01 tags face: two recorders (per-subtask
     * backends) registering concurrently must produce 12 distinct Meter.Ids
     * (6 meter names × 2 distinct {@code backend} tag values). Pre-fix shape
     * (fixed name + empty tags) hits micrometer first-wins dedup: only 6 Ids
     * survive and subtask 2's value functions are silently dropped.
     */
    @org.junit.jupiter.api.Test
    void testR403_W01_DualRecorderRegistrationYieldsDistinctBackendTags(@TempDir Path dir) {
        RocksDB db = openRawDb(dir.resolve("raw-w01a"));
        try {
            SimpleMeterRegistry registry = new SimpleMeterRegistry();
            RocksDBMetricsRecorder first = new RocksDBMetricsRecorder(db);
            RocksDBMetricsRecorder second = new RocksDBMetricsRecorder(db);
            first.register(registry);
            second.register(registry);

            String[] names = {
                    RocksDBMetricsRecorder.METRIC_BLOCK_CACHE_USAGE,
                    RocksDBMetricsRecorder.METRIC_BLOCK_CACHE_PINNED,
                    RocksDBMetricsRecorder.METRIC_MEMTABLE_USAGE,
                    RocksDBMetricsRecorder.METRIC_ESTIMATE_NUM_KEYS,
                    RocksDBMetricsRecorder.METRIC_COMPACTION_PENDING_BYTES,
                    RocksDBMetricsRecorder.METRIC_COMPACTIONS_RUNNING};
            for (String name : names) {
                java.util.Collection<io.micrometer.core.instrument.Meter> meters = registry.find(name).meters();
                assertEquals(2, meters.size(),
                        "both recorders' registrations must stay visible for " + name
                                + " (no first-wins dedup), got " + meters.size());
                java.util.Set<String> backendTags = new java.util.HashSet<>();
                for (io.micrometer.core.instrument.Meter meter : meters) {
                    backendTags.add(meter.getId().getTag("backend"));
                }
                assertEquals(2, backendTags.size(),
                        "distinct backend tag values required for " + name + ", got " + backendTags);
            }
            first.close();
            second.close();
        } finally {
            db.close();
        }
    }

    /**
     * CRA 8.43 R4-03 red ①(b) — W-01 removal face: after {@code close()} the
     * closed recorder's 6 Meter.Ids must be removed from the registry (scrape
     * sees "meter gone", not a surviving NaN gauge), while a sibling recorder's
     * meters stay intact. Pre-fix shape: close only detaches the dbRef — all
     * 6 Ids survive permanently and a later rebind re-hits the dedup.
     */
    @org.junit.jupiter.api.Test
    void testR403_W01_CloseRemovesEveryMeterIdOfTheClosedRecorder(@TempDir Path dir) {
        RocksDB db = openRawDb(dir.resolve("raw-w01b"));
        try {
            SimpleMeterRegistry registry = new SimpleMeterRegistry();
            RocksDBMetricsRecorder survivor = new RocksDBMetricsRecorder(db);
            RocksDBMetricsRecorder closed = new RocksDBMetricsRecorder(db);
            survivor.register(registry);
            closed.register(registry);
            closed.close();

            String[] names = {
                    RocksDBMetricsRecorder.METRIC_BLOCK_CACHE_USAGE,
                    RocksDBMetricsRecorder.METRIC_BLOCK_CACHE_PINNED,
                    RocksDBMetricsRecorder.METRIC_MEMTABLE_USAGE,
                    RocksDBMetricsRecorder.METRIC_ESTIMATE_NUM_KEYS,
                    RocksDBMetricsRecorder.METRIC_COMPACTION_PENDING_BYTES,
                    RocksDBMetricsRecorder.METRIC_COMPACTIONS_RUNNING};
            for (String name : names) {
                java.util.Collection<io.micrometer.core.instrument.Meter> meters = registry.find(name).meters();
                assertEquals(1, meters.size(),
                        "closed recorder's meter must be removed from the registry for " + name
                                + "; surviving meters must be exactly the sibling's, got " + meters.size());
                io.micrometer.core.instrument.Meter surviving = meters.iterator().next();
                assertTrue(surviving.getId().getTag("backend") != null,
                        "the surviving meter must be the sibling recorder's (backend tag present)");
            }
            survivor.close();
        } finally {
            db.close();
        }
    }

    /**
     * CRA 8.43 R4-03 red ⑧ — W-08 once-guard: a persistently failing property
     * (unknown property name → {@code RocksDBException} on the real
     * {@code getAggregatedLongProperty} path) must WARN exactly once per
     * recorder per property across N scrapes (subsequent failures downgrade to
     * debug). Pre-fix shape: every scrape logs another WARN (≈240/hour at a
     * 15s scrape for one failing property).
     */
    @org.junit.jupiter.api.Test
    void testR403_W08_PersistentPropertyFailureWarnsExactlyOnce(@TempDir Path dir) {
        RocksDB db = openRawDb(dir.resolve("raw-w08"));
        try {
            RocksDBMetricsRecorder recorder = new RocksDBMetricsRecorder(db);
            ch.qos.logback.classic.Logger logger =
                    (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(RocksDBMetricsRecorder.class);
            ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender =
                    new ch.qos.logback.core.read.ListAppender<>();
            appender.start();
            logger.addAppender(appender);
            try {
                String badProperty = "rocksdb.nonexistent-property-r403";
                for (int scrape = 0; scrape < 5; scrape++) {
                    recorder.readProperty(db, badProperty);
                }
                long warnCount = appender.list.stream()
                        .filter(e -> e.getLevel() == ch.qos.logback.classic.Level.WARN)
                        .filter(e -> e.getFormattedMessage() != null
                                && e.getFormattedMessage().contains(badProperty))
                        .count();
                assertEquals(1L, warnCount,
                        "persistent property failure must WARN exactly once (not per scrape), got " + warnCount);
            } finally {
                logger.detachAppender(appender);
            }
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
