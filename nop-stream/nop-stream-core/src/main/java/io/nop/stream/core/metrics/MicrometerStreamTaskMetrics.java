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

    public static final String TAG_JOB_ID = "jobId";
    public static final String TAG_VERTEX_ID = "vertexId";
    public static final String TAG_SUBTASK = "subtask";

    private final io.micrometer.core.instrument.Counter recordsIn;
    private final io.micrometer.core.instrument.Counter recordsOut;
    private final io.micrometer.core.instrument.Counter recordsConsumed;
    private final io.micrometer.core.instrument.Counter recordsEmitted;
    private final io.micrometer.core.instrument.Timer processingTime;
    private final io.micrometer.core.instrument.Timer emitTime;

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
            emitTime.record(java.time.Duration.ofNanos(nanos));
        }
    }

    @Override
    public void processingTime(long nanos) {
        if (nanos > 0) {
            processingTime.record(java.time.Duration.ofNanos(nanos));
        }
    }
}
