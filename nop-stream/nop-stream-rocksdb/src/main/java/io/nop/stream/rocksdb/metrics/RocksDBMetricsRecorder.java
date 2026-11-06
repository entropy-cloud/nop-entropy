/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.rocksdb.metrics;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import io.micrometer.core.instrument.Meter;
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
 * warn log issued exactly once per recorder per property (CRA 8.43 R4-03
 * W-08 once-guard — RocksDB property availability varies by version, and an
 * observability gauge must not break the state backend).
 *
 * <p>CRA 8.43 R4-03 W-01 (MA-09=C2-011): every gauge Meter.Id carries a
 * {@code backend} tag whose value is a process-wide monotonic recorder
 * sequence number, so concurrently opening per-subtask backends produce
 * mutually distinct Ids (micrometer first-wins dedup can no longer swallow
 * N-1 subtasks' registrations), and {@link #close()} removes exactly the
 * Meter.Ids this recorder registered — a closed backend disappears from the
 * scrape surface instead of surviving as a permanently-NaN gauge.
 */
public class RocksDBMetricsRecorder {

    public static final String METRIC_PREFIX = "nop.stream.state.rocksdb.";

    public static final String METRIC_BLOCK_CACHE_USAGE = METRIC_PREFIX + "block.cache.usage";
    public static final String METRIC_BLOCK_CACHE_PINNED = METRIC_PREFIX + "block.cache.pinned";
    public static final String METRIC_MEMTABLE_USAGE = METRIC_PREFIX + "memtable.usage.total";
    public static final String METRIC_ESTIMATE_NUM_KEYS = METRIC_PREFIX + "estimate.num.keys";
    public static final String METRIC_COMPACTION_PENDING_BYTES = METRIC_PREFIX + "compaction.pending.bytes";
    public static final String METRIC_COMPACTIONS_RUNNING = METRIC_PREFIX + "compactions.running";

    /** CRA 8.43 R4-03 W-01: single instance-identity tag key on every gauge Id. */
    public static final String TAG_BACKEND = "backend";

    /** RocksDB stable property keys (RocksDB StatsConstants semantics). */
    static final String PROP_BLOCK_CACHE_USAGE = "rocksdb.block-cache-usage";
    static final String PROP_BLOCK_CACHE_PINNED = "rocksdb.block-cache-pinned-usage";
    static final String PROP_MEMTABLE_USAGE = "rocksdb.cur-size-all-mem-tables";
    static final String PROP_ESTIMATE_NUM_KEYS = "rocksdb.estimate-num-keys";
    static final String PROP_COMPACTION_PENDING = "rocksdb.compaction-pending";
    static final String PROP_NUM_RUNNING_COMPACTIONS = "rocksdb.num-running-compactions";

    private static final org.slf4j.Logger LOG =
            org.slf4j.LoggerFactory.getLogger(RocksDBMetricsRecorder.class);

    /** CRA 8.43 R4-03 W-01: process-wide monotonic recorder identity source. */
    private static final AtomicLong RECORDER_SEQ = new AtomicLong();

    private final AtomicReference<RocksDB> dbRef;

    /** This recorder's identity value for the {@link #TAG_BACKEND} tag. */
    private final String backendTagValue;

    /**
     * CRA 8.43 R4-03 W-01: the Meter.Ids this recorder registered (remove
     * carrier — Id values, not Meter references, so no
     * recorder&rarr;Meter&rarr;valueFunction reference ring). Guarded by this
     * recorder's monitor together with {@code registryRef}.
     */
    private final List<Meter.Id> registeredIds = new ArrayList<>(6);

    /** The registry the gauges were registered on; null until register() runs. */
    private MeterRegistry registryRef;

    /**
     * CRA 8.43 R4-03 W-08: per-recorder per-property WARN once-guard
     * (readProperty runs fully under this recorder's monitor, so a plain
     * HashSet is safe). First failure of a property warns once; later failures
     * of the same property downgrade to debug. A rebinding backend gets a NEW
     * recorder instance and therefore warns again — per-instance lifecycle
     * rate, not per-scrape rate.
     */
    private final Set<String> warnedProperties = new HashSet<>();

    public RocksDBMetricsRecorder(RocksDB db) {
        this.dbRef = new AtomicReference<>(db);
        this.backendTagValue = String.valueOf(RECORDER_SEQ.incrementAndGet());
    }

    /**
     * Detaches the gauges from the native handle and removes every Meter.Id
     * this recorder registered from the registry (CRA 8.43 R4-03 W-01 face ②
     * — the closed backend's meters disappear from the scrape surface instead
     * of surviving as permanently-NaN gauges). MUST be called before the
     * owning backend closes the RocksDB handle — reading aggregated
     * properties on a closed handle crashes the JVM in native code.
     * Synchronized with {@link #readProperty} so an in-flight scrape cannot
     * race the detach; the detach (dbRef=null, plan 2511 adjudication ①
     * detached semantics) is applied BEFORE the Ids are removed.
     */
    public synchronized void close() {
        dbRef.set(null);
        MeterRegistry registry = registryRef;
        if (registry != null) {
            for (Meter.Id id : registeredIds) {
                registry.remove(id);
            }
        }
        registeredIds.clear();
        registryRef = null;
    }

    /**
     * Registers all RocksDB gauges on the given registry (called at the
     * state-backend open path). Every gauge Id carries the {@code backend}
     * tag with this recorder's monotonic sequence value (CRA 8.43 R4-03
     * W-01 face ① — per-subtask registrations are mutually distinct).
     */
    public void register(MeterRegistry registry) {
        Tags tags = Tags.of(Tag.of(TAG_BACKEND, backendTagValue));
        gauge(registry, tags, METRIC_BLOCK_CACHE_USAGE, PROP_BLOCK_CACHE_USAGE);
        gauge(registry, tags, METRIC_BLOCK_CACHE_PINNED, PROP_BLOCK_CACHE_PINNED);
        gauge(registry, tags, METRIC_MEMTABLE_USAGE, PROP_MEMTABLE_USAGE);
        gauge(registry, tags, METRIC_ESTIMATE_NUM_KEYS, PROP_ESTIMATE_NUM_KEYS);
        gauge(registry, tags, METRIC_COMPACTION_PENDING_BYTES, PROP_COMPACTION_PENDING);
        gauge(registry, tags, METRIC_COMPACTIONS_RUNNING, PROP_NUM_RUNNING_COMPACTIONS);
    }

    private void gauge(MeterRegistry registry, Tags tags, String meterName, String property) {
        // plan 2511 C1-002 (adjudication ①): the dbRef evaluation point MUST sit
        // inside the recorder monitor — reading the reference outside the
        // synchronized block lets a scrape thread carry a stale reference across
        // the close window into a native read on a closed handle (SIGSEGV).
        registry.gauge(meterName, tags, dbRef, ref -> readCurrent(property));
        // Fetch the registered meter by its exact Id (name + backend tag) for the
        // close-time removal carrier (W-01 face 2 — Meter.Id set, not Meter refs).
        Meter meter = registry.find(meterName).tags(tags).gauge();
        if (meter != null) {
            // CRA 8.43 R4-03 W-01: bookkeep under the same monitor close() uses.
            synchronized (this) {
                this.registryRef = registry;
                this.registeredIds.add(meter.getId());
            }
        }
    }

    /**
     * Reads an aggregated property with the reference fetch INSIDE the monitor
     * (plan 2511 C1-002). If the reference is non-null at read time the close
     * sequence ({@link #close()} sets it to null under the same monitor, before
     * the owning backend closes the native handle) has not yet detached it, so
     * the native handle is still open; if it is null the recorder is detached
     * and the gauge reports {@code Double.NaN}. This closes the
     * fetch-close-use race window without introducing any new lock.
     */
    public synchronized double readCurrent(String property) {
        return readProperty(dbRef.get(), property);
    }

    /**
     * Reads an aggregated long property; returns {@code Double.NaN} (absent
     * gauge) on failure. Property availability varies across RocksDB builds
     * (persistent characteristic), so the failure is WARNed exactly once per
     * recorder per property (CRA 8.43 R4-03 W-08 once-guard — "warn once"
     * owner-doc contract) and subsequent failures of the same property are
     * logged at debug. Never touches a detached (closed) handle.
     */
    public synchronized double readProperty(RocksDB db, String property) {
        if (db == null) {
            return Double.NaN;
        }
        try {
            return db.getAggregatedLongProperty(property);
        } catch (RocksDBException e) {
            if (warnedProperties.add(property)) {
                LOG.warn("Failed to read RocksDB property {} for metrics gauge: {}", property, e.toString());
            } else {
                LOG.debug("Failed to read RocksDB property {} for metrics gauge: {}", property, e.toString());
            }
            return Double.NaN;
        }
    }
}
