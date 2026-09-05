/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.metrics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Item 16 (P-REQ-1): unit tests for the engine/task layer meter bindings —
 * each layer has ≥3 registered metrics with observable values.
 */
class TestEngineAndTaskNodeMetrics {

    @Test
    void testEngineLayerMetrics() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        EngineMetrics metrics = EngineMetrics.create(registry, "job-m");

        metrics.checkpointCompleted(1024L, 250L);
        metrics.checkpointCompleted(2048L, 100L);
        metrics.checkpointFailed();
        metrics.checkpointAborted();
        metrics.recovery();

        assertEquals(2.0, registry.get(EngineMetrics.METRIC_CHECKPOINTS_COMPLETED)
                .tag("jobId", "job-m").counter().count());
        assertEquals(1.0, registry.get(EngineMetrics.METRIC_CHECKPOINTS_FAILED)
                .tag("jobId", "job-m").counter().count());
        assertEquals(1.0, registry.get(EngineMetrics.METRIC_CHECKPOINTS_ABORTED)
                .tag("jobId", "job-m").counter().count());
        assertEquals(1.0, registry.get(EngineMetrics.METRIC_RECOVERIES)
                .tag("jobId", "job-m").counter().count());
        assertEquals(2, registry.get(EngineMetrics.METRIC_CHECKPOINT_DURATION)
                .tag("jobId", "job-m").timer().count());
        // latest size gauge reflects the last completed checkpoint
        assertEquals(2048.0, registry.get(EngineMetrics.METRIC_CHECKPOINT_SIZE)
                .tag("jobId", "job-m").gauge().value());
        assertEquals(2048L, metrics.getLatestSizeBytes());
    }

    @Test
    void testEngineNodesActiveGauge() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AtomicInteger nodes = new AtomicInteger(2);
        EngineMetrics.registerNodesActiveGauge(registry, nodes::get);

        assertEquals(2.0, registry.get(EngineMetrics.METRIC_NODES_ACTIVE).gauge().value());
        nodes.set(3);
        assertEquals(3.0, registry.get(EngineMetrics.METRIC_NODES_ACTIVE).gauge().value());
    }

    @Test
    void testTaskLayerMetrics() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        TaskNodeMetrics metrics = TaskNodeMetrics.create(registry, "tm-0");

        metrics.taskDeployed();
        metrics.taskDeployed();
        metrics.taskDeployed();
        metrics.taskCancelled();
        metrics.taskFailed();

        assertEquals(3.0, registry.get(TaskNodeMetrics.METRIC_TASKS_DEPLOYED)
                .tag("nodeId", "tm-0").counter().count());
        assertEquals(1.0, registry.get(TaskNodeMetrics.METRIC_TASKS_CANCELLED)
                .tag("nodeId", "tm-0").counter().count());
        assertEquals(1.0, registry.get(TaskNodeMetrics.METRIC_TASKS_FAILED)
                .tag("nodeId", "tm-0").counter().count());
    }

    @Test
    void testTaskRunningGauge() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AtomicInteger running = new AtomicInteger(0);
        TaskNodeMetrics.registerRunningGauge(registry, "tm-1", running::get);

        running.set(4);
        assertEquals(4.0, registry.get(TaskNodeMetrics.METRIC_TASKS_RUNNING)
                .tag("nodeId", "tm-1").gauge().value());
    }
}
