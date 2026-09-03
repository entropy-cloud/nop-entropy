/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.functions.source;

import java.io.Serializable;

import io.nop.stream.core.common.functions.StreamFunction;

/**
 * Base interface for a stream data source. A SourceFunction emits data by implementing
 * the {@link #run(SourceContext)} method, which can then be cancelled by calling {@link #cancel()}.
 * <p>
 * This is a simplified version based on Apache Flink's SourceFunction interface.
 *
 * @param <T> The type of the elements produced by this source.
 */
public interface SourceFunction<T> extends StreamFunction, Serializable {

    /**
     * Starts the source. Implementations can use the {@link SourceContext} to emit elements.
     * The run method should block until the source is cancelled or the data is exhausted.
     *
     * @param ctx The context to use for emitting elements
     * @throws Exception Any exception that causes the source to fail
     */
    void run(SourceContext<T> ctx) throws Exception;

    /**
     * Cancels the source. This method is called to signal the source to stop.
     * Implementations should ensure this method returns quickly.
     */
    void cancel();

    /**
     * Returns the consistency capability of this source.
     * Default is {@link SourceConsistencyCapability#BEST_EFFORT}.
     * Connectors should override this to declare their actual capability.
     *
     * @return the source consistency capability
     */
    default SourceConsistencyCapability getSourceConsistency() {
        return SourceConsistencyCapability.BEST_EFFORT;
    }

    /**
     * Interface that sources use to emit elements.
     *
     * @param <T> The type of the elements produced by the source
     */
    interface SourceContext<T> extends Serializable {

        /**
         * Emits an element.
         *
         * @param element The element to emit
         */
        void collect(T element);

        /**
         * Emits an element with a timestamp.
         *
         * @param element   The element to emit
         * @param timestamp The timestamp of the element in milliseconds
         */
        void collectWithTimestamp(T element, long timestamp);

        /**
         * Emits a watermark.
         *
         * @param mark The watermark to emit
         */
        void emitWatermark(long mark);

        /**
         * Marks the source as idle.
         */
        void markAsTemporarilyIdle();

        /**
         * Returns the current processing time.
         *
         * @return The current processing time
         */
        long getProcessingTime();

        /**
         * Returns whether the owning task has been cooperatively asked to cancel.
         *
         * <p>This is the observable cancellation surface for source bodies that do not
         * call {@link #collect(Object)} on every iteration (e.g. an inline xpl source
         * loop): the documented pattern is
         * {@code while (!ctx.isCancelled()) { ... ctx.collect(x); ... }}. The engine's
         * production context reflects the task mailbox's cancel flag — the same truth
         * source as the cooperative checkpoint-abort exception thrown from
         * {@code collect()} — so both exit paths (loop-condition polling and the
         * collect-time cooperative exception) observe one cancel signal.
         *
         * <p>The default implementation returns {@code false}: contexts that cannot
         * observe cancellation (e.g. plain test doubles) must not fake a cancel
         * signal. The production context wired by
         * {@code io.nop.stream.core.operators.StreamSourceOperator} overrides this to
         * reflect the engine's cancel flag.
         *
         * @return true once the owning task has been asked to cancel
         */
        default boolean isCancelled() {
            return false;
        }
    }
}
