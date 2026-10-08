/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.metrics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI16 (plan 2337): the derived time-share gauges. Ruling F1: backpressured =
 * emitTime.sum, busy = processingTime.sum − emitTime.sum (emit is nested inside
 * processing), idle = the new idle accumulator, denominator = the new elapsed
 * accumulator; NaN while elapsed is 0.
 */
class TestStreamTaskMetricsTimeShare {

    private static double gauge(SimpleMeterRegistry registry, String name) {
        return registry.get(name).gauge().value();
    }

    @Test
    void ratiosDeriveFromNestedTimersAndAccumulators() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MicrometerStreamTaskMetrics m = new MicrometerStreamTaskMetrics(registry, "j1", "v1", 0);

        // before any slice: NaN (denominator 0)
        assertTrue(Double.isNaN(gauge(registry, MicrometerStreamTaskMetrics.METRIC_OPERATOR_TIME_BUSY_RATIO)));

        m.elapsedTime(TimeUnit.MILLISECONDS.toNanos(1000));
        m.processingTime(TimeUnit.MILLISECONDS.toNanos(600)); // busy 400 + emit 200 = 600
        m.emitTime(TimeUnit.MILLISECONDS.toNanos(200));
        m.idleTime(TimeUnit.MILLISECONDS.toNanos(300));

        assertEquals(0.4, gauge(registry, MicrometerStreamTaskMetrics.METRIC_OPERATOR_TIME_BUSY_RATIO), 1e-9);
        assertEquals(0.2, gauge(registry, MicrometerStreamTaskMetrics.METRIC_OPERATOR_TIME_BACKPRESSURED_RATIO), 1e-9);
        assertEquals(0.3, gauge(registry, MicrometerStreamTaskMetrics.METRIC_OPERATOR_TIME_IDLE_RATIO), 1e-9);
        assertEquals((double) TimeUnit.MILLISECONDS.toNanos(300),
                registry.get(MicrometerStreamTaskMetrics.METRIC_OPERATOR_TIME_IDLE_NANOS).counter().count(), 1e-6);
        assertEquals((double) TimeUnit.MILLISECONDS.toNanos(1000),
                registry.get(MicrometerStreamTaskMetrics.METRIC_OPERATOR_TIME_ELAPSED_NANOS).counter().count(), 1e-6);
    }

    @Test
    void emitExceedingProcessingClampsBusyFloorAtZero() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MicrometerStreamTaskMetrics m = new MicrometerStreamTaskMetrics(registry, "j1", "v1", 0);
        m.elapsedTime(TimeUnit.MILLISECONDS.toNanos(1000));
        m.emitTime(TimeUnit.MILLISECONDS.toNanos(800));
        // a defensive floor: measurement jitter must not produce a negative busy share
        assertEquals(0.0, gauge(registry, MicrometerStreamTaskMetrics.METRIC_OPERATOR_TIME_BUSY_RATIO), 1e-9);
        assertEquals(0.8, gauge(registry, MicrometerStreamTaskMetrics.METRIC_OPERATOR_TIME_BACKPRESSURED_RATIO), 1e-9);
    }

}
