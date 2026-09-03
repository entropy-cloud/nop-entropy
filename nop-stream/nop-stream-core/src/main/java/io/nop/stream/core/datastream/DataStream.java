/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.datastream;

import io.nop.stream.core.common.eventtime.WatermarkStrategy;
import io.nop.stream.core.common.functions.FilterFunction;
import io.nop.stream.core.common.functions.FlatMapFunction;
import io.nop.stream.core.common.functions.KeySelector;
import io.nop.stream.core.common.functions.MapFunction;
import io.nop.stream.core.common.functions.ProcessFunction;
import io.nop.stream.core.common.functions.SinkFunction;
import io.nop.stream.core.common.typeinfo.TypeInformation;
import io.nop.stream.core.operators.OneInputStreamOperator;

public interface DataStream<T> {
    TypeInformation<T> getType();

    /**
     * Sets the parallelism of the vertex producing this stream (the wrapped
     * transformation), overriding the environment-level parallelism it was
     * constructed with. Undeclared callers keep the environment value — the DSL
     * resolution order is transform-level &gt; stream(environment)-level &gt; default 1.
     *
     * <p>Covers every transform build-point return type via covariant overrides
     * ({@link SingleOutputStreamOperator}, {@link KeyedStream}; source streams are
     * {@code SingleOutputStreamOperator}s). Caveat for {@code WindowedStream}: it is
     * virtual (wraps the upstream keyed transformation, no vertex of its own), so
     * this call would retarget the upstream vertex — declare the parallelism on the
     * concrete window function call instead.
     *
     * <p>Delegates to {@link io.nop.stream.core.transformation.Transformation#setParallelism(int)}:
     * values &lt; 1 and non-1 values on a {@code forceNonParallel()}-locked
     * transformation are rejected with typed errors.
     *
     * @param parallelism the per-operator parallelism, at least 1
     * @return this stream, for chaining
     */
    DataStream<T> setParallelism(int parallelism);

    /**
     * It creates a new {@link KeyedStream} that uses the provided key for partitioning its operator
     * states.
     *
     * @param key The KeySelector to be used for extracting the key for partitioning
     * @return The {@link DataStream} with partitioned state (i.e. KeyedStream)
     */
    <K> KeyedStream<T, K> keyBy(KeySelector<T, K> key);

    /**
     * Applies a map transformation on a {@link DataStream}. The transformation
     * calls a {@link MapFunction} for each element of the DataStream. Each
     * MapFunction call returns exactly one element.
     *
     * @param mapper The MapFunction that is called for each element of the DataStream.
     * @param <R>    The type of the elements in the returned stream.
     * @return The transformed {@link DataStream}.
     */
    <R> SingleOutputStreamOperator<R> map(MapFunction<T, R> mapper);

    /**
     * Applies a filter transformation on a {@link DataStream}. The transformation
     * calls a {@link FilterFunction} for each element of the DataStream and retains
     * only those element for which the function returns true.
     *
     * @param filter The FilterFunction that is called for each element of the DataStream.
     * @return The filtered DataStream.
     */
    SingleOutputStreamOperator<T> filter(FilterFunction<T> filter);

    /**
     * Applies a flatMap transformation on a {@link DataStream}. The transformation
     * calls a {@link FlatMapFunction} for each element of the DataStream. Each
     * FlatMapFunction call can return zero, one, or more elements.
     *
     * @param flatMapper The FlatMapFunction that is called for each element of the DataStream.
     * @param <R>        The type of the elements in the returned stream.
     * @return The transformed {@link DataStream}.
     */
    <R> SingleOutputStreamOperator<R> flatMap(FlatMapFunction<T, R> flatMapper);

    /**
     * Applies a {@link ProcessFunction} on a {@link DataStream}, enabling stateful processing
     * and timer access.
     *
     * <p>The function is called for each element in the stream and can produce zero, one,
     * or more output elements via the {@link io.nop.stream.core.util.Collector}.
     *
     * @param processFunction The ProcessFunction that is called for each element.
     * @param <R>             The type of the elements in the returned stream.
     * @return The transformed {@link SingleOutputStreamOperator}.
     */
    <R> SingleOutputStreamOperator<R> process(ProcessFunction<T, R> processFunction);

    /**
     * Assigns timestamps to the elements in the data stream and generates watermarks
     * based on the provided {@link WatermarkStrategy}.
     *
     * <p>This method creates a transformation that extracts event-time timestamps from
     * stream elements and generates watermarks to signal event time progress to downstream
     * operators.
     *
     * @param strategy the watermark strategy that defines how to assign timestamps and generate watermarks
     * @return a new data stream with timestamps and watermarks assigned
     */
    SingleOutputStreamOperator<T> assignTimestampsAndWatermarks(WatermarkStrategy<T> strategy);

    /**
     * Method for passing user defined operators along with the type information that will transform
     * the DataStream.
     *
     * @param operatorName name of the operator, for logging purposes
     * @param outTypeInfo  the output type of the operator
     * @param operator     the object containing the transformation logic
     * @param <R>          type of the return stream
     * @return the data stream constructed
     */
    <R> SingleOutputStreamOperator<R> transform(
            String operatorName,
            TypeInformation<R> outTypeInfo,
            OneInputStreamOperator<T, R> operator);

    /**
     * Prints the elements of the DataStream to the standard output.
     * 
     * <p>This method creates a sink that prints each element to standard output.
     * The elements are printed line by line without any prefix.
     * 
     * <p>This is a convenience method for development and debugging purposes.
     * Calling this method only registers the sink; use {@code env.execute()} to run the job.
     */
    void print();

    /**
     * Prints the elements of the DataStream to the standard output using a custom sink function.
     * 
     * <p>Calling this method only registers the sink; use {@code env.execute()} to run the job.
     * 
     * @param sinkFunction the sink function to use for printing elements
     */
    void print(SinkFunction<T> sinkFunction);

    /**
     * Collects the elements of the DataStream using a collector function.
     * 
     * <p>Calling this method only registers the sink; use {@code env.execute()} to run the job.
     * 
     * @param collectorFunction the sink function to use for collecting elements
     */
    void collect(SinkFunction<T> collectorFunction);

    /**
     * Sends the elements of the DataStream to a sink function.
     *
     * <p>Calling this method only registers the sink; use {@code env.execute()} to run the job.
     *
     * @param sinkFunction the sink function to send elements to
     */
    void sink(SinkFunction<T> sinkFunction);

    /**
     * Sends the elements of the DataStream to a sink function with an explicit
     * per-operator parallelism (item 29: overrides the environment-level stamp;
     * the {@code <sink parallelism="...">} DSL declaration consumes this entry).
     *
     * <p>Calling this method only registers the sink; use {@code env.execute()} to run the job.
     *
     * @param sinkFunction the sink function to send elements to
     * @param parallelism  the sink vertex parallelism, at least 1
     */
    void sink(SinkFunction<T> sinkFunction, int parallelism);
}
