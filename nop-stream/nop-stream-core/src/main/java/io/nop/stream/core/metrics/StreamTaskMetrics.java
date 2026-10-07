/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.metrics;

/**
 * Per-task data-plane metrics hooks (item 16, P-REQ-1 operator/io layers).
 *
 * <p>Injected into {@code StreamTaskInvokable} by the runtime on both real
 * execution paths (LOCAL via GraphModelCheckpointExecutor, REMOTE via
 * TaskManager deploy). Default is {@link #NOOP} — a task without injected
 * metrics keeps the exact pre-existing behavior and serialization shape.
 *
 * <p>Layer mapping (see docs-for-ai/03-modules/nop-stream.md metric table):
 * <ul>
 *   <li>operator layer: {@link #recordsIn(long)} / {@link #recordsOut(long)} /
 *       {@link #processingTime(long)}</li>
 *   <li>io layer: {@link #recordsConsumed(long)} (source-side input) /
 *       {@link #recordsEmitted(long)} (emissions toward downstream tasks) /
 *       {@link #emitTime(long)} (cross-task emission duration — backpressure proxy)</li>
 * </ul>
 */
public interface StreamTaskMetrics {

    StreamTaskMetrics NOOP = new StreamTaskMetrics() {
        @Override
        public void recordsIn(long n) {
        }

        @Override
        public void recordsOut(long n) {
        }

        @Override
        public void recordsConsumed(long n) {
        }

        @Override
        public void recordsEmitted(long n) {
        }

        @Override
        public void emitTime(long nanos) {
        }

        @Override
        public void processingTime(long nanos) {
        }

        @Override
        public void idleTime(long nanos) {
        }

        @Override
        public void elapsedTime(long nanos) {
        }
    };

    /**
     * Operator layer: number of records dispatched from this task's input gate
     * into the operator chain (MIDDLE/SINK roles).
     */
    void recordsIn(long n);

    /**
     * Operator layer: number of records emitted by this task's operator chain
     * (through the task's record writers).
     */
    void recordsOut(long n);

    /**
     * IO layer: number of records the source fed into the pipeline
     * (SOURCE / SELF_CONTAINED roles).
     */
    void recordsConsumed(long n);

    /**
     * IO layer: number of records emitted toward downstream tasks
     * (cross-task writer emissions).
     */
    void recordsEmitted(long n);

    /**
     * IO layer: duration of the cross-task emission itself (the record
     * writer's {@code emit} call), in nanoseconds. A direct backpressure
     * proxy — when a downstream exchange queue is full the emit blocks, so
     * this timer's rise quantifies output-side backpressure at the producer.
     */
    void emitTime(long nanos);

    /**
     * Operator layer: per-record processing time of the operator chain
     * (around the input-gate record dispatch), in nanoseconds.
     */
    void processingTime(long nanos);

    /**
     * WI16 (plan 2337): accumulates one idle slice — the duration of an input-gate
     * read that returned empty (momentary idle, non-EOS). Sliced per main-loop
     * iteration, so it sums consistently with {@link #elapsedTime(long)}.
     *
     * @param nanos the idle slice duration
     */
    void idleTime(long nanos);

    /**
     * WI16 (plan 2337): accumulates one main-loop iteration slice. The denominator of
     * the busy/backpressured/idle ratios (busy = processingTime.sum − emitTime.sum,
     * backpressured = emitTime.sum, both divided by elapsed sum — emitTime is nested
     * inside processingTime, so the subtraction is exact in sum terms).
     *
     * @param nanos the iteration slice duration
     */
    void elapsedTime(long nanos);
}
