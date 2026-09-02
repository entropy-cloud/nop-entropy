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
 *       {@link #recordsEmitted(long)} (emissions toward downstream tasks)</li>
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
        public void processingTime(long nanos) {
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
     * Operator layer: per-record processing time of the operator chain
     * (around the input-gate record dispatch), in nanoseconds.
     */
    void processingTime(long nanos);
}
