/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.metrics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Item 16 (P-REQ-1 operator/io layers): registration + behavior of the
 * micrometer-backed per-task metrics, plus the serializable handle semantics.
 */
class TestMicrometerStreamTaskMetrics {

    @Test
    void testOperatorAndIoMetersRegisteredAndCounted() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MicrometerStreamTaskMetrics metrics = new MicrometerStreamTaskMetrics(
                registry, "job-1", "vertex-a", 0);

        metrics.recordsIn(3);
        metrics.recordsOut(2);
        metrics.recordsConsumed(5);
        metrics.recordsEmitted(2);
        metrics.processingTime(1_000_000L);
        metrics.emitTime(500_000L);

        assertEquals(3.0, registry.get(MicrometerStreamTaskMetrics.METRIC_OPERATOR_RECORDS_IN)
                .tag("jobId", "job-1").tag("vertexId", "vertex-a").tag("subtask", "0").counter().count());
        assertEquals(2.0, registry.get(MicrometerStreamTaskMetrics.METRIC_OPERATOR_RECORDS_OUT)
                .tag("jobId", "job-1").counter().count());
        assertEquals(5.0, registry.get(MicrometerStreamTaskMetrics.METRIC_IO_RECORDS_CONSUMED)
                .tag("jobId", "job-1").counter().count());
        assertEquals(2.0, registry.get(MicrometerStreamTaskMetrics.METRIC_IO_RECORDS_EMITTED)
                .tag("jobId", "job-1").counter().count());
        assertEquals(1, registry.get(MicrometerStreamTaskMetrics.METRIC_OPERATOR_PROCESSING_TIME)
                .tag("jobId", "job-1").timer().count());
        assertTrue(registry.get(MicrometerStreamTaskMetrics.METRIC_OPERATOR_PROCESSING_TIME)
                .timer().max(java.util.concurrent.TimeUnit.NANOSECONDS) > 0, "timer recorded a positive max");
        // io layer third meter: emission-duration timer (backpressure proxy)
        assertEquals(1, registry.get(MicrometerStreamTaskMetrics.METRIC_IO_EMIT_TIME)
                .tag("jobId", "job-1").timer().count());
        assertTrue(registry.get(MicrometerStreamTaskMetrics.METRIC_IO_EMIT_TIME)
                .timer().max(java.util.concurrent.TimeUnit.NANOSECONDS) > 0,
                "emit timer recorded a positive max");
    }

    @Test
    void testNegativeAndZeroIncrementsIgnored() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MicrometerStreamTaskMetrics metrics = new MicrometerStreamTaskMetrics(
                registry, "job-1", "vertex-a", 0);

        metrics.recordsIn(0);
        metrics.recordsIn(-1);
        metrics.processingTime(0);
        metrics.processingTime(-5);
        metrics.emitTime(0);
        metrics.emitTime(-5);

        assertEquals(0.0, registry.get(MicrometerStreamTaskMetrics.METRIC_OPERATOR_RECORDS_IN).counter().count());
        assertEquals(0, registry.get(MicrometerStreamTaskMetrics.METRIC_OPERATOR_PROCESSING_TIME).timer().count());
        assertEquals(0, registry.get(MicrometerStreamTaskMetrics.METRIC_IO_EMIT_TIME).timer().count());
    }

    @Test
    void testHandleDelegationAndSwap() {
        TaskMetricsHandle handle = new TaskMetricsHandle();
        // Default NOOP — no exception, no NPE
        handle.recordsIn(1);
        handle.processingTime(10);

        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MicrometerStreamTaskMetrics real = new MicrometerStreamTaskMetrics(registry, "j", "v", 0);
        handle.setDelegate(real);
        handle.recordsIn(2);
        assertEquals(2.0, registry.get(MicrometerStreamTaskMetrics.METRIC_OPERATOR_RECORDS_IN).counter().count());

        handle.setDelegate(null);
        assertSame(StreamTaskMetrics.NOOP, handle.getDelegate());
    }

    @Test
    void testHandleSerializationResetsToNoop() throws Exception {
        TaskMetricsHandle handle = new TaskMetricsHandle();
        handle.setDelegate(new MicrometerStreamTaskMetrics(new SimpleMeterRegistry(), "j", "v", 0));

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(handle);
        }
        TaskMetricsHandle deserialized;
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bos.toByteArray()))) {
            deserialized = (TaskMetricsHandle) ois.readObject();
        }

        assertNotSame(handle, deserialized);
        // The micrometer delegate is not serializable — after deserialization the
        // handle must fall back to NOOP (never a stale/invalid reference).
        assertSame(StreamTaskMetrics.NOOP, deserialized.getDelegate());
        assertInstanceOf(StreamTaskMetrics.class, deserialized);
        assertFalse(deserialized.getDelegate() instanceof MicrometerStreamTaskMetrics);
    }

    @Test
    void testCompositeRegistryAddRemoveMember() {
        // P-REQ-1 exposure premise: meters registered before attaching a member
        // registry become visible to that member (this is why nop-stream uses a
        // composite instead of swapping the platform global single instance).
        io.micrometer.core.instrument.composite.CompositeMeterRegistry composite = StreamMetricsRegistries.registry();
        SimpleMeterRegistry member = new SimpleMeterRegistry();
        composite.add(member);

        new MicrometerStreamTaskMetrics(composite, "job-x", "vertex-x", 0).recordsIn(7);
        assertEquals(7.0, member.get(MicrometerStreamTaskMetrics.METRIC_OPERATOR_RECORDS_IN).counter().count());

        composite.remove(member);
    }
}
