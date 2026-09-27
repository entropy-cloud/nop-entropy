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

import io.micrometer.core.instrument.Meter;
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
 *
 * <p>Lifecycle: per-node meters and the running-task gauge are
 * bound to the node lifecycle. {@link #releaseNode(String)} removes the cached
 * instance, its registry meters, and the retained gauge state. Without it, a
 * restarted TaskManager with the same nodeId would silently keep reporting the
 * OLD instance's gauge (micrometer returns the existing meter for an identical
 * id, so the new supplier never takes effect) and dead nodes would pin their
 * state for the lifetime of the process.
 */
public final class TaskNodeMetrics {

    public static final String METRIC_TASKS_DEPLOYED = "nop.stream.task.deployed.total";
    public static final String METRIC_TASKS_CANCELLED = "nop.stream.task.cancelled.total";
    public static final String METRIC_TASKS_FAILED = "nop.stream.task.failures.total";
    public static final String METRIC_TASKS_RUNNING = "nop.stream.task.running";
    public static final String METRIC_TASKS_ACK_SEND_FAILED = "nop.stream.task.ackSendFailures.total";

    public static final String TAG_NODE_ID = "nodeId";

    private static final ConcurrentHashMap<String, TaskNodeMetrics> BY_NODE = new ConcurrentHashMap<>();

    /**
     * Strong references to registered running-gauge state objects, keyed by
     * nodeId (micrometer weak-gauge semantics — without retention the supplier
     * is GC-eligible and the gauge silently disappears). Keying by node lets
     * {@link #releaseNode(String)} drop the retention entry together with the
     * registry meter.
     */
    private static final ConcurrentHashMap<String, Supplier<?>> RUNNING_GAUGE_REFS =
            new ConcurrentHashMap<>();

    private final MeterRegistry registry;
    private final String nodeId;
    private final io.micrometer.core.instrument.Counter deployed;
    private final io.micrometer.core.instrument.Counter cancelled;
    private final io.micrometer.core.instrument.Counter failures;
    private final io.micrometer.core.instrument.Counter ackSendFailures;

    private TaskNodeMetrics(MeterRegistry registry, String nodeId) {
        this.registry = registry;
        this.nodeId = nodeId;
        Tags tags = Tags.of(Tag.of(TAG_NODE_ID, nodeId));
        this.deployed = registry.counter(METRIC_TASKS_DEPLOYED, tags);
        this.cancelled = registry.counter(METRIC_TASKS_CANCELLED, tags);
        this.failures = registry.counter(METRIC_TASKS_FAILED, tags);
        this.ackSendFailures = registry.counter(METRIC_TASKS_ACK_SEND_FAILED, tags);
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

    /** Counts checkpoint ACKs whose bounded send budget was exhausted. */
    public void ackSendFailed() {
        ackSendFailures.increment();
    }

    /**
     * Registers the per-node running-task gauge. Idempotent per registry for
     * identical id.
     */
    public static void registerRunningGauge(MeterRegistry registry, String nodeId, Supplier<Number> supplier) {
        // retain the state object strongly (weak-gauge semantics — see field javadoc)
        RUNNING_GAUGE_REFS.put(nodeId, supplier);
        registry.gauge(METRIC_TASKS_RUNNING, Tags.of(Tag.of(TAG_NODE_ID, nodeId)),
                supplier, s -> s.get().doubleValue());
    }

    /**
     * Releases every meter bound to a node's lifecycle — the
     * cached {@link TaskNodeMetrics} instance, its registry meters, and the
     * retained running-gauge state. Invoked from {@code TaskManager.stop()} so a
     * restarted node re-registers a fresh gauge and a dead node pins nothing.
     */
    public static void releaseNode(String nodeId) {
        TaskNodeMetrics metrics = BY_NODE.remove(nodeId);
        if (metrics != null) {
            metrics.removeFromRegistry();
        }
        Supplier<?> state = RUNNING_GAUGE_REFS.remove(nodeId);
        if (state != null) {
            StreamMetricsRegistries.registry()
                    .remove(new Meter.Id(METRIC_TASKS_RUNNING,
                            Tags.of(Tag.of(TAG_NODE_ID, nodeId)), null, null, Meter.Type.GAUGE));
        }
    }

    private void removeFromRegistry() {
        Tags tags = Tags.of(Tag.of(TAG_NODE_ID, nodeId));
        registry.remove(deployed.getId());
        registry.remove(cancelled.getId());
        registry.remove(failures.getId());
        registry.remove(ackSendFailures.getId());
        registry.remove(new Meter.Id(METRIC_TASKS_RUNNING, tags, null, null, Meter.Type.GAUGE));
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

    public double getAckSendFailureCount() {
        return ackSendFailures.count();
    }
}
