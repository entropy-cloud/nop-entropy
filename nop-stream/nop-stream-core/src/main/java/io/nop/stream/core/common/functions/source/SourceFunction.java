/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.functions.source;

import io.nop.stream.core.common.functions.StreamFunction;

import java.io.Serializable;

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
    }
}
