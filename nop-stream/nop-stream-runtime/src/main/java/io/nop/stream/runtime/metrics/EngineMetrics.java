/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.metrics;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;

import io.nop.stream.core.metrics.StreamMetricsRegistries;

/**
 * Item 16 (P-REQ-1 engine layer): job/cluster-level meters driven from the
 * real coordinator lifecycle paths (checkpoint completion/failure/abort in
 * CheckpointCoordinator, global recovery in JobCoordinator, active-node gauge
 * from ClusterRegistry).
 *
 * <p>Meter names follow the {@code nop.stream.engine.*} convention; the
 * authoritative name table lives in docs-for-ai/03-modules/nop-stream.md.
 * Instances are cached per jobId against the process composite registry; a
 * fresh instance can be built against any registry for tests.
 */
public final class EngineMetrics {

    public static final String METRIC_NODES_ACTIVE = "nop.stream.engine.nodes.active";
    public static final String METRIC_CHECKPOINTS_COMPLETED = "nop.stream.engine.checkpoints.completed";
    public static final String METRIC_CHECKPOINTS_FAILED = "nop.stream.engine.checkpoints.failed";
    public static final String METRIC_CHECKPOINTS_ABORTED = "nop.stream.engine.checkpoints.aborted";
    public static final String METRIC_CHECKPOINT_DURATION = "nop.stream.engine.checkpoint.duration";
    public static final String METRIC_CHECKPOINT_SIZE = "nop.stream.engine.checkpoint.size.bytes";
    public static final String METRIC_RECOVERIES = "nop.stream.engine.recoveries.total";

    public static final String TAG_JOB_ID = "jobId";

    private static final ConcurrentHashMap<String, EngineMetrics> BY_JOB = new ConcurrentHashMap<>();

    /**
     * Strong references to registered gauge state objects. Micrometer gauges
     * hold their state object WEAKLY — without this retention the supplier is
     * GC-eligible the moment the caller's lambda goes out of scope and the
     * gauge silently disappears from scrape output.
     */
    private static final java.util.Set<Supplier<?>> GAUGE_STATE_REFS =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    private final MeterRegistry registry;
    private final String jobId;
    private final Tags tags;

    private final io.micrometer.core.instrument.Counter completed;
    private final io.micrometer.core.instrument.Counter failed;
    private final io.micrometer.core.instrument.Counter aborted;
    private final Timer duration;
    private final io.micrometer.core.instrument.Counter recoveries;
    private final AtomicLong latestSizeBytes = new AtomicLong(0);

    private EngineMetrics(MeterRegistry registry, String jobId) {
        this.registry = registry;
        this.jobId = jobId;
        this.tags = Tags.of(Tag.of(TAG_JOB_ID, jobId));
        this.completed = registry.counter(METRIC_CHECKPOINTS_COMPLETED, tags);
        this.failed = registry.counter(METRIC_CHECKPOINTS_FAILED, tags);
        this.aborted = registry.counter(METRIC_CHECKPOINTS_ABORTED, tags);
        this.duration = registry.timer(METRIC_CHECKPOINT_DURATION, tags);
        this.recoveries = registry.counter(METRIC_RECOVERIES, tags);
        registry.gauge(METRIC_CHECKPOINT_SIZE, tags, latestSizeBytes, AtomicLong::get);
    }

    /**
     * Returns the cached per-job instance bound to the process composite
     * registry (used by the real lifecycle paths).
     */
    public static EngineMetrics forJob(String jobId) {
        return BY_JOB.computeIfAbsent(jobId, id -> new EngineMetrics(StreamMetricsRegistries.registry(), id));
    }

    /** Test / custom-registry factory (not cached). */
    public static EngineMetrics create(MeterRegistry registry, String jobId) {
        return new EngineMetrics(registry, jobId);
    }

    public void checkpointCompleted(long sizeBytes, long durationMs) {
        completed.increment();
        latestSizeBytes.set(sizeBytes);
        if (durationMs > 0) {
            duration.record(java.time.Duration.ofMillis(durationMs));
        }
    }

    public void checkpointFailed() {
        failed.increment();
    }

    public void checkpointAborted() {
        aborted.increment();
    }

    public void recovery() {
        recoveries.increment();
    }

    /**
     * Registers the cluster-level active-node gauge (no job tag — the cluster
     * view). Idempotent per registry: micrometer gauges with identical id are
     * no-ops on re-registration.
     */
    public static void registerNodesActiveGauge(MeterRegistry registry, Supplier<Number> supplier) {
        // retain the state object strongly (weak-gauge semantics — see field javadoc)
        GAUGE_STATE_REFS.add(supplier);
        registry.gauge(METRIC_NODES_ACTIVE, supplier, s -> s.get().doubleValue());
    }

    public double getCompletedCount() {
        return completed.count();
    }

    public double getFailedCount() {
        return failed.count();
    }

    public double getAbortedCount() {
        return aborted.count();
    }

    public double getRecoveryCount() {
        return recoveries.count();
    }

    public long getLatestSizeBytes() {
        return latestSizeBytes.get();
    }

    public long getDurationCount() {
        return duration.count();
    }

    public MeterRegistry getRegistry() {
        return registry;
    }

    public String getJobId() {
        return jobId;
    }
}
