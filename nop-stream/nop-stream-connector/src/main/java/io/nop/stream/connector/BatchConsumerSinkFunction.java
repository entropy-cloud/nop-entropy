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
import io.nop.batch.core.impl.BatchChunkContextImpl;

import io.nop.stream.core.common.functions.sink.SinkConsistencyCapability;
import io.nop.stream.core.common.functions.SinkFunction;
import io.nop.stream.core.exceptions.StreamException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.nop.stream.core.exceptions.NopStreamErrors.*;

/**
 * Adapts nop-batch's {@link IBatchConsumerProvider} to nop-stream's {@link SinkFunction}.
 * <p>
 * Buffers incoming records and flushes them in batches to the underlying consumer.
 * Implements {@link AutoCloseable} so the operator lifecycle can flush remaining records.
 */
public class BatchConsumerSinkFunction<R> implements SinkFunction<R>, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(BatchConsumerSinkFunction.class);
    private static final long serialVersionUID = 1L;

    private final IBatchConsumerProvider.IBatchConsumer<R> consumer;
    private final int batchSize;
    private final List<R> buffer;
    private final IBatchTaskContext taskContext;
    private transient boolean flushed = false;

    public BatchConsumerSinkFunction(IBatchConsumerProvider<R> consumerProvider) {
        this(consumerProvider, 100);
    }

    public BatchConsumerSinkFunction(IBatchConsumerProvider<R> consumerProvider, int batchSize) {
        if (consumerProvider == null) {
            throw new StreamException(ERR_STREAM_NULL_ARG).param(ARG_ARG_NAME, "consumerProvider");
        }
        if (batchSize < 1) {
            throw new StreamException(ERR_STREAM_INVALID_ARG).param(ARG_ARG_NAME, "batchSize")
                    .param(ARG_DETAIL, "must be at least 1");
        }
        this.taskContext = new BatchTaskContextImpl();
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
            IBatchChunkContext chunkContext = taskContext != null
                    ? new BatchChunkContextImpl(taskContext)
                    : null;
            consumer.consume(new ArrayList<>(buffer), chunkContext);
            buffer.clear();
        } catch (Exception e) {
            throw new StreamException(ERR_STREAM_STATE_ERROR, e)
                    .param(ARG_DETAIL, "Failed to flush batch, data retained for retry");
        }
    }

    @Override
    public void finish() throws Exception {
        if (!flushed) {
            flush();
            flushed = true;
        }
    }

    @Override
    public void close() {
        try {
            if (!flushed) {
                flush();
                flushed = true;
            }
        } catch (Exception flushErr) {
            LOG.error("Flush failed in close() with buffer size={}, first record summary={}",
                    buffer.size(),
                    buffer.isEmpty() ? "<empty>" : String.valueOf(buffer.get(0)));
        } finally {
            if (consumer instanceof AutoCloseable) {
                try {
                    ((AutoCloseable) consumer).close();
                } catch (Exception e) {
                    throw new StreamException(ERR_STREAM_STATE_ERROR, e)
                            .param(ARG_DETAIL, "Failed to close consumer");
                }
            }
        }
    }

    @Override
    public SinkConsistencyCapability getSinkConsistency() {
        return SinkConsistencyCapability.IDEMPOTENT;
    }
}
