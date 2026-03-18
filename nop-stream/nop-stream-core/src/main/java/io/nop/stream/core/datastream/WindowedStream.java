/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.datastream;

import io.nop.stream.core.common.functions.AggregateFunction;
import io.nop.stream.core.common.functions.WindowFunction;
import io.nop.stream.core.common.functions.ReduceFunction;
import io.nop.stream.core.windowing.evictors.Evictor;
import io.nop.stream.core.windowing.triggers.Trigger;
import io.nop.stream.core.windowing.windows.Window;

/**
 * A {@code WindowedStream} represents a data stream where elements are grouped by key, and for each
 * key, the stream of elements is split into windows based on a WindowAssigner. Window emission is
 * triggered based on a Trigger.
 *
 * <p>The windows are conceptually evaluated for each key individually, meaning windows can trigger
 * at different points for each key.
 *
 * <p>Note that the {@code WindowedStream} is purely an API construct, during runtime the
 * {@code WindowedStream} will be collapsed together with the {@code KeyedStream} and the operation
 * over the window into one single operation.
 *
 * @param <T> The type of elements in the stream.
 * @param <K> The type of the key by which elements are grouped.
 * @param <W> The type of {@code Window} that the {@code WindowAssigner} assigns the elements to.
 */
public interface WindowedStream<T, K, W extends Window> extends DataStream<T> {

    /**
     * Applies the given window function to each window. The window function is called for each
     * evaluation of the window for each key individually. The output of the window function is
     * interpreted as a regular non-windowed stream.
     *
     * <p>Note that this function requires that all data in the windows is buffered until the window
     * is evaluated, as the function provides no means of incremental aggregation.
     *
     * @param function The window function.
     * @param <R> The type of the elements in the resulting stream, equal to the WindowFunction's result type.
     * @return The data stream that is the result of applying the window function to the window.
     */
    <R> SingleOutputStreamOperator<R> apply(WindowFunction<T, R, K, W> function);

    /**
     * Applies the given aggregation function to each window. The aggregation function is called for
     * each element, aggregating values incrementally and keeping the state to one accumulator per
     * key and window.
     *
     * @param function The aggregation function.
     * @param <ACC> The type of the AggregateFunction's accumulator.
     * @param <R> The type of the elements in the resulting stream, equal to the AggregateFunction's result type.
     * @return The data stream that is the result of applying the aggregation function to the window.
     */
    <ACC, R> SingleOutputStreamOperator<R> aggregate(AggregateFunction<T, ACC, R> function);

    /**
     * Applies a reduce function to each window. The reduce function is called
     * iteratively on the elements of the window until only one element remains.
     *
     * @param function The reduce function to apply to each window.
     * @return The resulting data stream.
     */
    SingleOutputStreamOperator<T> reduce(ReduceFunction<T> function);

    /**
     * Sets the trigger for this windowed stream.
     *
     * @param trigger The trigger to use.
     * @return This windowed stream.
     */
    WindowedStream<T, K, W> trigger(Trigger<? super T, ? super W> trigger);

    /**
     * Sets the evictor for this windowed stream.
     *
     * @param evictor The evictor to use.
     * @return This windowed stream.
     */
    WindowedStream<T, K, W> evictor(Evictor<? super T, ? super W> evictor);
}
