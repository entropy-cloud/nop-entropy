/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.connector;

import io.nop.batch.core.IBatchConsumerProvider;
import io.nop.batch.core.IBatchLoaderProvider;

import io.nop.stream.core.common.functions.SinkFunction;
import io.nop.stream.core.datastream.DataStream;
import io.nop.stream.core.datastream.DataStreamSource;
import io.nop.stream.core.environment.StreamExecutionEnvironment;

/**
 * Convenience methods for creating sources and sinks from nop-batch providers.
 */
public final class StreamConnectors {

    private StreamConnectors() {
    }

    public static <S> DataStreamSource<S> fromBatchLoader(
            StreamExecutionEnvironment env,
            IBatchLoaderProvider<S> loaderProvider,
            String sourceName) {
        return env.addSource(new BatchLoaderSourceFunction<>(loaderProvider), sourceName);
    }

    public static <S> DataStreamSource<S> fromBatchLoader(
            StreamExecutionEnvironment env,
            IBatchLoaderProvider<S> loaderProvider,
            String sourceName,
            int batchSize) {
        return env.addSource(new BatchLoaderSourceFunction<>(loaderProvider, batchSize), sourceName);
    }

    public static <R> void toBatchConsumer(
            DataStream<R> stream,
            IBatchConsumerProvider<R> consumerProvider,
            int batchSize) {
        stream.sink(new BatchConsumerSinkFunction<>(consumerProvider, batchSize));
    }

    public static <R> void toBatchConsumer(
            DataStream<R> stream,
            IBatchConsumerProvider<R> consumerProvider) {
        stream.sink(new BatchConsumerSinkFunction<>(consumerProvider));
    }
}
