/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;

/**
 * Micrometer-backed {@link StreamTaskMetrics} (item 16, P-REQ-1 operator/io
 * layers). Registers per-task meters tagged with jobId/vertexId/subtask.
 *
 * <p>Meter names follow the {@code nop.stream.<layer>.<name>} convention;
 * the authoritative name table lives in docs-for-ai/03-modules/nop-stream.md.
 */
public class MicrometerStreamTaskMetrics implements StreamTaskMetrics {

    public static final String METRIC_OPERATOR_RECORDS_IN = "nop.stream.operator.records.in.total";
    public static final String METRIC_OPERATOR_RECORDS_OUT = "nop.stream.operator.records.out.total";
    public static final String METRIC_OPERATOR_PROCESSING_TIME = "nop.stream.operator.processing.time";
    public static final String METRIC_IO_RECORDS_CONSUMED = "nop.stream.io.records.consumed.total";
    public static final String METRIC_IO_RECORDS_EMITTED = "nop.stream.io.records.emitted.total";
    public static final String METRIC_IO_EMIT_TIME = "nop.stream.io.emit.time";

    // WI16 (plan 2337): operator-layer time-share + watermark cascading observability
    public static final String METRIC_OPERATOR_TIME_BUSY_RATIO = "nop.stream.operator.time.busy.ratio";
    public static final String METRIC_OPERATOR_TIME_BACKPRESSURED_RATIO = "nop.stream.operator.time.backpressured.ratio";
    public static final String METRIC_OPERATOR_TIME_IDLE_RATIO = "nop.stream.operator.time.idle.ratio";
    public static final String METRIC_OPERATOR_TIME_IDLE_NANOS = "nop.stream.operator.time.idle.nanos";
    public static final String METRIC_OPERATOR_TIME_ELAPSED_NANOS = "nop.stream.operator.time.elapsed.nanos";
    public static final String METRIC_OPERATOR_WATERMARK = "nop.stream.operator.watermark";
    public static final String METRIC_OPERATOR_WATERMARK_CHANNEL_LAG = "nop.stream.operator.watermark.channel.lag";

    public static final String TAG_JOB_ID = "jobId";
    public static final String TAG_VERTEX_ID = "vertexId";
    public static final String TAG_SUBTASK = "subtask";

    private final io.micrometer.core.instrument.Counter recordsIn;
    private final io.micrometer.core.instrument.Counter recordsOut;
    private final io.micrometer.core.instrument.Counter recordsConsumed;
    private final io.micrometer.core.instrument.Counter recordsEmitted;
    private final io.micrometer.core.instrument.Timer processingTime;
    private final io.micrometer.core.instrument.Timer emitTime;
    private final io.micrometer.core.instrument.Counter idleNanos;
    private final io.micrometer.core.instrument.Counter elapsedNanos;
    private final io.micrometer.core.instrument.MeterRegistry registry;
    private final io.micrometer.core.instrument.Tags tags;

    // WI16: holder-rebind pattern (ChannelQueueGauges D2c) — Micrometer gauges hold a
    // WEAK reference to their state object and silently drop same-id re-registrations.
    // Every WI16 gauge therefore reads through a STATIC strong holder keyed by the
    // meter identity; a task instance registers into it and recovery rebuilds rebind.
    private static final java.util.concurrent.ConcurrentMap<String, java.util.concurrent.atomic.AtomicReference<MicrometerStreamTaskMetrics>> TASK_HOLDERS =
            new java.util.concurrent.ConcurrentHashMap<>();

    private final java.util.concurrent.atomic.AtomicReference<io.nop.stream.core.execution.InputGate> gateRef =
            new java.util.concurrent.atomic.AtomicReference<>();
    private volatile boolean watermarkGaugesRegistered;

    public MicrometerStreamTaskMetrics(MeterRegistry registry,
                                       String jobId, String vertexId, int subtaskIndex) {
        Tags tags = Tags.of(
                Tag.of(TAG_JOB_ID, jobId == null ? "unknown" : jobId),
                Tag.of(TAG_VERTEX_ID, vertexId == null ? "unknown" : vertexId),
                Tag.of(TAG_SUBTASK, String.valueOf(subtaskIndex)));

        this.recordsIn = registry.counter(METRIC_OPERATOR_RECORDS_IN, tags);
        this.recordsOut = registry.counter(METRIC_OPERATOR_RECORDS_OUT, tags);
        this.recordsConsumed = registry.counter(METRIC_IO_RECORDS_CONSUMED, tags);
        this.recordsEmitted = registry.counter(METRIC_IO_RECORDS_EMITTED, tags);
        this.processingTime = registry.timer(METRIC_OPERATOR_PROCESSING_TIME, tags);
        this.emitTime = registry.timer(METRIC_IO_EMIT_TIME, tags);
        this.idleNanos = registry.counter(METRIC_OPERATOR_TIME_IDLE_NANOS, tags);
        this.elapsedNanos = registry.counter(METRIC_OPERATOR_TIME_ELAPSED_NANOS, tags);
        this.registry = registry;
        this.tags = tags;

        // derived time-share gauges (review F1 ruling B'): busy = (processing − emit)/elapsed,
        // backpressured = emit/elapsed, idle = idle/elapsed — NaN while elapsed is 0.
        // All read through the static strong holder (weak-gauge trap, D2c).
        java.util.concurrent.atomic.AtomicReference<MicrometerStreamTaskMetrics> holder =
                TASK_HOLDERS.computeIfAbsent(holderKey(jobId, vertexId, subtaskIndex),
                        k -> new java.util.concurrent.atomic.AtomicReference<>(this));
        holder.set(this);
        io.micrometer.core.instrument.Gauge.builder(METRIC_OPERATOR_TIME_BUSY_RATIO, holder,
                h -> {
                    MicrometerStreamTaskMetrics m = h.get();
                    return m == null ? Double.NaN
                            : ratio(Math.max(0L, m.processingTime.totalTime(java.util.concurrent.TimeUnit.NANOSECONDS)
                                    - m.emitTime.totalTime(java.util.concurrent.TimeUnit.NANOSECONDS)),
                                    m.elapsedNanos.count());
                })
                .tags(tags)
                .register(registry);
        io.micrometer.core.instrument.Gauge.builder(METRIC_OPERATOR_TIME_BACKPRESSURED_RATIO, holder,
                h -> {
                    MicrometerStreamTaskMetrics m = h.get();
                    return m == null ? Double.NaN
                            : ratio(m.emitTime.totalTime(java.util.concurrent.TimeUnit.NANOSECONDS),
                                    m.elapsedNanos.count());
                })
                .tags(tags)
                .register(registry);
        io.micrometer.core.instrument.Gauge.builder(METRIC_OPERATOR_TIME_IDLE_RATIO, holder,
                h -> {
                    MicrometerStreamTaskMetrics m = h.get();
                    return m == null ? Double.NaN : ratio(m.idleNanos.count(), m.elapsedNanos.count());
                })
                .tags(tags)
                .register(registry);
    }

    private static String holderKey(String jobId, String vertexId, int subtaskIndex) {
        return jobId + "|" + vertexId + "|" + subtaskIndex;
    }

    private String myHolderKey() {
        return holderKey(processingTime.getId().getTag(TAG_JOB_ID),
                processingTime.getId().getTag(TAG_VERTEX_ID),
                processingTime.getId().getTag(TAG_SUBTASK));
    }

    /**
     * WI16: detaches this task instance from its static holder (task close). The
     * registered gauges keep their meter ids but read NaN afterwards — an ended task
     * must not keep reporting its last ratios.
     */
    public void releaseHolder() {
        java.util.concurrent.atomic.AtomicReference<MicrometerStreamTaskMetrics> holder =
                TASK_HOLDERS.get(myHolderKey());
        if (holder != null) {
            holder.compareAndSet(this, null);
        }
    }

    private static double ratio(double numerator, double denominator) {
        return denominator <= 0 ? Double.NaN : numerator / denominator;
    }

    /**
     * WI16: binds the watermark cascading gauges to this task's input gate (holder
     * rebind pattern — repeated wiring rebinds the same meter ids). SOURCE and
     * SELF_CONTAINED roles have no input gate and never call this; before the first
     * watermark the gauges read NaN (declared sentinel, never Long.MIN_VALUE).
     */
    public void bindWatermarkGauges(io.nop.stream.core.execution.InputGate gate) {
        this.gateRef.set(gate);
        java.util.concurrent.atomic.AtomicReference<MicrometerStreamTaskMetrics> holder =
                TASK_HOLDERS.computeIfAbsent(watermarkHolderKey(),
                        k -> new java.util.concurrent.atomic.AtomicReference<>(this));
        holder.set(this);
        if (watermarkGaugesRegistered) {
            return;
        }
        watermarkGaugesRegistered = true;
        io.micrometer.core.instrument.Tags wmTags = io.micrometer.core.instrument.Tags.of(
                io.micrometer.core.instrument.Tag.of(TAG_JOB_ID,
                        processingTime.getId().getTag(TAG_JOB_ID) == null ? "unknown"
                                : processingTime.getId().getTag(TAG_JOB_ID)),
                io.micrometer.core.instrument.Tag.of(TAG_VERTEX_ID,
                        processingTime.getId().getTag(TAG_VERTEX_ID) == null ? "unknown"
                                : processingTime.getId().getTag(TAG_VERTEX_ID)),
                io.micrometer.core.instrument.Tag.of(TAG_SUBTASK,
                        processingTime.getId().getTag(TAG_SUBTASK) == null ? "unknown"
                                : processingTime.getId().getTag(TAG_SUBTASK)));
        io.micrometer.core.instrument.Gauge.builder(METRIC_OPERATOR_WATERMARK, holder,
                h -> mergedWatermarkOrNaN(h.get() == null ? null : h.get().gateRef.get()))
                .tags(wmTags)
                .register(registry);
        io.micrometer.core.instrument.Gauge.builder(METRIC_OPERATOR_WATERMARK_CHANNEL_LAG, holder,
                h -> channelLagOrNaN(h.get() == null ? null : h.get().gateRef.get()))
                .tags(wmTags)
                .register(registry);
    }

    private String watermarkHolderKey() {
        // jobId included: two jobs on one process with identical vertex/subtask ids must
        // not cross-attribute watermark gauges (audit M4)
        return holderKey(
                processingTime.getId().getTag(TAG_JOB_ID) == null ? "unknown"
                        : processingTime.getId().getTag(TAG_JOB_ID),
                processingTime.getId().getTag(TAG_VERTEX_ID) == null ? "unknown"
                        : processingTime.getId().getTag(TAG_VERTEX_ID),
                processingTime.getId().getTag(TAG_SUBTASK) == null ? "unknown"
                        : processingTime.getId().getTag(TAG_SUBTASK));
    }

    private String holderKey(String jobId, String vertexId, String subtask) {
        return jobId + "|" + vertexId + "|" + subtask;
    }

    private static double mergedWatermarkOrNaN(io.nop.stream.core.execution.InputGate gate) {
        if (gate == null) {
            return Double.NaN;
        }
        long wm = gate.getCurrentWatermark();
        return wm == Long.MIN_VALUE ? Double.NaN : wm;
    }

    private static double channelLagOrNaN(io.nop.stream.core.execution.InputGate gate) {
        if (gate == null) {
            return Double.NaN;
        }
        long merged = gate.getCurrentWatermark();
        if (merged == Long.MIN_VALUE) {
            return Double.NaN;
        }
        long max = gate.getMaxActiveChannelWatermark();
        if (max == Long.MIN_VALUE) {
            return Double.NaN;
        }
        return Math.max(0L, max - merged);
    }

    @Override
    public void recordsIn(long n) {
        if (n > 0) {
            recordsIn.increment(n);
        }
    }

    @Override
    public void recordsOut(long n) {
        if (n > 0) {
            recordsOut.increment(n);
        }
    }

    @Override
    public void recordsConsumed(long n) {
        if (n > 0) {
            recordsConsumed.increment(n);
        }
    }

    @Override
    public void recordsEmitted(long n) {
        if (n > 0) {
            recordsEmitted.increment(n);
        }
    }

    @Override
    public void emitTime(long nanos) {
        if (nanos > 0) {
            // record(long, TimeUnit) — the Duration overload
            // allocated a Duration object per record on the data-plane hot path.
            emitTime.record(nanos, java.util.concurrent.TimeUnit.NANOSECONDS);
        }
    }

    @Override
    public void processingTime(long nanos) {
        if (nanos > 0) {
            processingTime.record(nanos, java.util.concurrent.TimeUnit.NANOSECONDS);
        }
    }

    @Override
    public void idleTime(long nanos) {
        if (nanos > 0) {
            idleNanos.increment(nanos);
        }
    }

    @Override
    public void elapsedTime(long nanos) {
        if (nanos > 0) {
            elapsedNanos.increment(nanos);
        }
    }
}
