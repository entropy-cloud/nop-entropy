/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.rocksdb.metrics;

import java.util.concurrent.atomic.AtomicReference;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;

import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;

/**
 * Item 16 (P-REQ-8): RocksDB state-backend metrics recorder. Registers gauges
 * backed by RocksDB aggregated properties (block cache / memtable / compaction
 * / key-count statistics) at the real state-backend open path.
 *
 * <p>Meter names follow the {@code nop.stream.state.rocksdb.*} convention;
 * the authoritative name table lives in docs-for-ai/03-modules/nop-stream.md.
 * A read failure of a property surfaces as an absent gauge value (NaN) plus a
 * one-shot warn log — RocksDB property availability varies by version, and an
 * observability gauge must not break the state backend.
 */
public class RocksDBMetricsRecorder {

    public static final String METRIC_PREFIX = "nop.stream.state.rocksdb.";

    public static final String METRIC_BLOCK_CACHE_USAGE = METRIC_PREFIX + "block.cache.usage";
    public static final String METRIC_BLOCK_CACHE_PINNED = METRIC_PREFIX + "block.cache.pinned";
    public static final String METRIC_MEMTABLE_USAGE = METRIC_PREFIX + "memtable.usage.total";
    public static final String METRIC_ESTIMATE_NUM_KEYS = METRIC_PREFIX + "estimate.num.keys";
    public static final String METRIC_COMPACTION_PENDING_BYTES = METRIC_PREFIX + "compaction.pending.bytes";
    public static final String METRIC_COMPACTIONS_RUNNING = METRIC_PREFIX + "compactions.running";

    /** RocksDB stable property keys (RocksDB StatsConstants semantics). */
    static final String PROP_BLOCK_CACHE_USAGE = "rocksdb.block-cache-usage";
    static final String PROP_BLOCK_CACHE_PINNED = "rocksdb.block-cache-pinned-usage";
    static final String PROP_MEMTABLE_USAGE = "rocksdb.cur-size-all-mem-tables";
    static final String PROP_ESTIMATE_NUM_KEYS = "rocksdb.estimate-num-keys";
    static final String PROP_COMPACTION_PENDING = "rocksdb.compaction-pending";
    static final String PROP_NUM_RUNNING_COMPACTIONS = "rocksdb.num-running-compactions";

    private static final org.slf4j.Logger LOG =
            org.slf4j.LoggerFactory.getLogger(RocksDBMetricsRecorder.class);

    private final AtomicReference<RocksDB> dbRef;

    public RocksDBMetricsRecorder(RocksDB db) {
        this.dbRef = new AtomicReference<>(db);
    }

    /**
     * Detaches the gauges from the native handle. MUST be called before the
     * owning backend closes the RocksDB handle — reading aggregated
     * properties on a closed handle crashes the JVM in native code.
     * Synchronized with {@link #readProperty} so an in-flight scrape cannot
     * race the detach.
     */
    public synchronized void close() {
        dbRef.set(null);
    }

    /**
     * Registers all RocksDB gauges on the given registry (called at the
     * state-backend open path).
     */
    public void register(MeterRegistry registry) {
        gauge(registry, METRIC_BLOCK_CACHE_USAGE, PROP_BLOCK_CACHE_USAGE);
        gauge(registry, METRIC_BLOCK_CACHE_PINNED, PROP_BLOCK_CACHE_PINNED);
        gauge(registry, METRIC_MEMTABLE_USAGE, PROP_MEMTABLE_USAGE);
        gauge(registry, METRIC_ESTIMATE_NUM_KEYS, PROP_ESTIMATE_NUM_KEYS);
        gauge(registry, METRIC_COMPACTION_PENDING_BYTES, PROP_COMPACTION_PENDING);
        gauge(registry, METRIC_COMPACTIONS_RUNNING, PROP_NUM_RUNNING_COMPACTIONS);
    }

    private void gauge(MeterRegistry registry, String meterName, String property) {
        registry.gauge(meterName, Tags.empty(), dbRef, ref -> readProperty(ref.get(), property));
    }

    /**
     * Reads an aggregated long property; returns {@code Double.NaN} (absent
     * gauge) on failure after a one-shot-style warn — property availability
     * varies across RocksDB builds. Never touches a detached (closed) handle.
     */
    public synchronized double readProperty(RocksDB db, String property) {
        if (db == null) {
            return Double.NaN;
        }
        try {
            return db.getAggregatedLongProperty(property);
        } catch (RocksDBException e) {
            LOG.warn("Failed to read RocksDB property {} for metrics gauge: {}", property, e.toString());
            return Double.NaN;
        }
    }
}
