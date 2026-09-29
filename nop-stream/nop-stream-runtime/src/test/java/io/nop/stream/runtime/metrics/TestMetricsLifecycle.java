/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.nop.stream.core.metrics.StreamMetricsRegistries;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 358 Fix-9: job/node metrics have a real lifecycle release path.
 * <ul>
 *   <li>{@code EngineMetrics.releaseJob} removes the cached per-job meter set so
 *       a coordination process running many short jobs does not accumulate
 *       meters (and re-registration after release yields a FRESH instance).</li>
 *   <li>{@code TaskNodeMetrics.releaseNode} removes the running-task gauge and
 *       its retained state so a restarted node's new supplier takes effect
 *       instead of silently keeping the old instance's gauge.</li>
 * </ul>
 */
class TestMetricsLifecycle {

    private static final String JOB = "job-p358-metrics";
    private static final String NODE = "node-p358-metrics";

    @AfterEach
    void cleanUp() {
        EngineMetrics.releaseJob(JOB);
        TaskNodeMetrics.releaseNode(NODE);
    }

    @Test
    void releaseJobRemovesCachedEngineMeters() {
        EngineMetrics metrics = EngineMetrics.forJob(JOB);
        metrics.checkpointCompleted(100L, 10L);
        assertEquals(1.0, completedCount(),
                "precondition: the checkpoint-completed counter registered for the job");

        assertTrue(EngineMetrics.releaseJob(JOB), "the first release must find and remove the cached instance");
        assertFalse(EngineMetrics.releaseJob(JOB), "release must be idempotent (nothing cached anymore)");

        // Re-register after release: must yield a FRESH instance (count 0),
        // not the stale meter set.
        EngineMetrics.forJob(JOB);
        assertEquals(0.0, completedCount(),
                "after release, re-registration must yield a fresh instance, not the stale meter set");
    }

    /**
     * Reads the job-tagged checkpoints-completed counter straight from the
     * registry (observation without depending on EngineMetrics accessors).
     */
    private double completedCount() {
        io.micrometer.core.instrument.Counter counter = StreamMetricsRegistries.registry()
                .find(EngineMetrics.METRIC_CHECKPOINTS_COMPLETED)
                .tag(EngineMetrics.TAG_JOB_ID, JOB).counter();
        return counter == null ? Double.NaN : counter.count();
    }

    @Test
    void releaseNodeRemovesRunningGaugeAndAllowsFreshRegistration() {
        MeterRegistry registry = StreamMetricsRegistries.registry();

        AtomicInteger oldState = new AtomicInteger(7);
        TaskNodeMetrics.registerRunningGauge(registry, NODE, oldState::get);
        assertEquals(7.0, gaugeValue(registry), "precondition: gauge registered");

        TaskNodeMetrics.releaseNode(NODE);
        assertNull(gauge(registry), "release must remove the node's gauge from the registry");

        AtomicInteger newState = new AtomicInteger(9);
        TaskNodeMetrics.registerRunningGauge(registry, NODE, newState::get);
        assertEquals(9.0, gaugeValue(registry),
                "a restarted node must register a FRESH gauge (the old instance's supplier must not win)");
    }

    private io.micrometer.core.instrument.Gauge gauge(MeterRegistry registry) {
        return registry.find(TaskNodeMetrics.METRIC_TASKS_RUNNING)
                .tag(TaskNodeMetrics.TAG_NODE_ID, NODE).gauge();
    }

    private double gaugeValue(MeterRegistry registry) {
        io.micrometer.core.instrument.Gauge gauge = gauge(registry);
        return gauge == null ? Double.NaN : gauge.value();
    }
}
