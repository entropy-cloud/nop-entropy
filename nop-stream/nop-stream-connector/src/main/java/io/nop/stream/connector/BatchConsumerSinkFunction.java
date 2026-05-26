/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.connector;

import java.util.ArrayList;
import java.util.List;

import io.nop.batch.core.IBatchChunkContext;
import io.nop.batch.core.IBatchConsumerProvider;
import io.nop.batch.core.IBatchTaskContext;
import io.nop.batch.core.impl.BatchTaskContextImpl;

import io.nop.stream.core.common.functions.sink.SinkConsistencyCapability;
import io.nop.stream.core.common.functions.SinkFunction;
import io.nop.stream.core.exceptions.StreamException;

/**
 * Adapts nop-batch's {@link IBatchConsumerProvider} to nop-stream's {@link SinkFunction}.
 * <p>
 * Buffers incoming records and flushes them in batches to the underlying consumer.
 * Implements {@link AutoCloseable} so the operator lifecycle can flush remaining records.
 */
public class BatchConsumerSinkFunction<R> implements SinkFunction<R>, AutoCloseable {

    private static final long serialVersionUID = 1L;

    private final IBatchConsumerProvider.IBatchConsumer<R> consumer;
    private final int batchSize;
    private final List<R> buffer;

    public BatchConsumerSinkFunction(IBatchConsumerProvider<R> consumerProvider) {
        this(consumerProvider, 100);
    }

    public BatchConsumerSinkFunction(IBatchConsumerProvider<R> consumerProvider, int batchSize) {
        if (consumerProvider == null) {
            throw new StreamException("consumerProvider must not be null");
        }
        if (batchSize < 1) {
            throw new StreamException("batchSize must be at least 1");
        }
        IBatchTaskContext taskContext = new BatchTaskContextImpl();
        this.consumer = consumerProvider.setup(taskContext);
        this.batchSize = batchSize;
        this.buffer = new ArrayList<>(batchSize);
    }

    @Override
    public void consume(R value) {
        buffer.add(value);
        if (buffer.size() >= batchSize) {
            flush();
        }
    }

    private void flush() {
        if (buffer.isEmpty()) {
            return;
        }
        try {
            consumer.consume(buffer, null);
        } finally {
            buffer.clear();
        }
    }

    @Override
    public void close() {
        flush();
    }

    @Override
    public SinkConsistencyCapability getSinkConsistency() {
        return SinkConsistencyCapability.IDEMPOTENT;
    }
}
