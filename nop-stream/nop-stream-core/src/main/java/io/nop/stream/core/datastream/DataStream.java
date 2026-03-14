/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.datastream;

import io.nop.stream.core.common.functions.KeySelector;
import io.nop.stream.core.common.functions.FilterFunction;
import io.nop.stream.core.common.functions.FlatMapFunction;
import io.nop.stream.core.common.functions.MapFunction;
import io.nop.stream.core.operators.OneInputStreamOperator;
import io.nop.stream.core.common.typeinfo.TypeInformation;
import io.nop.stream.core.common.functions.SinkFunction;

public interface DataStream<T> {
    TypeInformation<T> getType();

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
     * 
     * @throws Exception if an error occurs while executing the sink
     */
    void print() throws Exception;

    /**
     * Prints the elements of the DataStream to the standard output using a custom sink function.
     * 
     * <p>This method allows for customized printing behavior through the provided SinkFunction.
     * 
     * @param sinkFunction the sink function to use for printing elements
     * @throws Exception if an error occurs while executing the sink
     */
    void print(SinkFunction<T> sinkFunction) throws Exception;

    /**
     * Collects the elements of the DataStream using a collector function.
     * 
     * <p>This method allows for custom collection behavior through the provided SinkFunction.
     * 
     * @param collectorFunction the sink function to use for collecting elements
     * @throws Exception if an error occurs while executing the collector
     */
    void collect(SinkFunction<T> collectorFunction) throws Exception;

    /**
     * Sends the elements of the DataStream to a sink function.
     * 
     * <p>This is a method for sending elements to any sink function.
     * 
     * @param sinkFunction the sink function to send elements to
     * @throws Exception if an error occurs while executing the sink
     */
    void sink(SinkFunction<T> sinkFunction) throws Exception;
}
