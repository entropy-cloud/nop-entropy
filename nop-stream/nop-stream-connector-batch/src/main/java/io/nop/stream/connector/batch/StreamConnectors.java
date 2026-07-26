/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.connector.batch;

import io.nop.batch.core.IBatchConsumerProvider;
import io.nop.batch.core.IBatchLoaderProvider;

import io.nop.stream.core.datastream.DataStream;
import io.nop.stream.core.datastream.DataStreamSource;
import io.nop.stream.core.environment.StreamExecutionEnvironment;

/**
 * Convenience methods for creating sources and sinks from nop-batch providers.
 *
 * <p>This class lives in {@code nop-stream-connector-batch} (not the base
 * {@code nop-stream-connector}) because it references {@code nop-batch-core}
 * types directly. Keeping it out of the base module ensures the base connector
 * stays loadable when {@code nop-batch-core} is absent (AR-2 contract).</p>
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
