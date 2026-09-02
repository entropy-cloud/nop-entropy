/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.metrics;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;

import io.nop.stream.core.metrics.StreamMetricsRegistries;

/**
 * Item 16 (P-REQ-1 task layer): per-TaskManager meters driven from the real
 * deploy/cancel/failure paths in TaskManager (REMOTE and embedded paths).
 *
 * <p>Meter names follow the {@code nop.stream.task.*} convention; the
 * authoritative name table lives in docs-for-ai/03-modules/nop-stream.md.
 */
public final class TaskNodeMetrics {

    public static final String METRIC_TASKS_DEPLOYED = "nop.stream.task.deployed.total";
    public static final String METRIC_TASKS_CANCELLED = "nop.stream.task.cancelled.total";
    public static final String METRIC_TASKS_FAILED = "nop.stream.task.failures.total";
    public static final String METRIC_TASKS_RUNNING = "nop.stream.task.running";

    public static final String TAG_NODE_ID = "nodeId";

    private static final ConcurrentHashMap<String, TaskNodeMetrics> BY_NODE = new ConcurrentHashMap<>();

    /**
     * Strong references to registered gauge state objects (micrometer weak-gauge
     * semantics — without retention the supplier is GC-eligible and the gauge
     * silently disappears).
     */
    private static final java.util.Set<java.util.function.Supplier<?>> GAUGE_STATE_REFS =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    private final io.micrometer.core.instrument.Counter deployed;
    private final io.micrometer.core.instrument.Counter cancelled;
    private final io.micrometer.core.instrument.Counter failures;

    private TaskNodeMetrics(MeterRegistry registry, String nodeId) {
        Tags tags = Tags.of(Tag.of(TAG_NODE_ID, nodeId));
        this.deployed = registry.counter(METRIC_TASKS_DEPLOYED, tags);
        this.cancelled = registry.counter(METRIC_TASKS_CANCELLED, tags);
        this.failures = registry.counter(METRIC_TASKS_FAILED, tags);
    }

    /** Cached per-node instance bound to the process composite registry. */
    public static TaskNodeMetrics forNode(String nodeId) {
        return BY_NODE.computeIfAbsent(nodeId, id -> {
            TaskNodeMetrics metrics = new TaskNodeMetrics(StreamMetricsRegistries.registry(), id);
            return metrics;
        });
    }

    /** Test / custom-registry factory (not cached). */
    public static TaskNodeMetrics create(MeterRegistry registry, String nodeId) {
        return new TaskNodeMetrics(registry, nodeId);
    }

    public void taskDeployed() {
        deployed.increment();
    }

    public void taskCancelled() {
        cancelled.increment();
    }

    public void taskFailed() {
        failures.increment();
    }

    /**
     * Registers the per-node running-task gauge. Idempotent per registry for
     * identical id.
     */
    public static void registerRunningGauge(MeterRegistry registry, String nodeId, Supplier<Number> supplier) {
        // retain the state object strongly (weak-gauge semantics — see field javadoc)
        GAUGE_STATE_REFS.add(supplier);
        registry.gauge(METRIC_TASKS_RUNNING, Tags.of(Tag.of(TAG_NODE_ID, nodeId)),
                supplier, s -> s.get().doubleValue());
    }

    public double getDeployedCount() {
        return deployed.count();
    }

    public double getCancelledCount() {
        return cancelled.count();
    }

    public double getFailureCount() {
        return failures.count();
    }
}
