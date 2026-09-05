/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.connector.batch;

import java.util.ArrayList;
import java.util.List;

import io.nop.batch.core.IBatchChunkContext;
import io.nop.batch.core.IBatchConsumerProvider;
import io.nop.batch.core.IBatchTaskContext;
import io.nop.batch.core.impl.BatchTaskContextImpl;
import io.nop.batch.core.impl.BatchChunkContextImpl;

import io.nop.stream.core.common.functions.sink.SinkConsistencyCapability;
import io.nop.stream.core.common.functions.SinkFunction;
import io.nop.stream.core.connector.ConnectivityCheckable;
import io.nop.stream.core.exceptions.StreamException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_ARG_NAME;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_CHAINING_OUTPUT_FLUSH_FAILED;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_INVALID_ARG;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_NULL_ARG;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_STATE_ERROR;

/**
 * Adapts nop-batch's {@link IBatchConsumerProvider} to nop-stream's {@link SinkFunction}.
 * <p>
 * Buffers incoming records and flushes them in batches to the underlying consumer.
 * Implements {@link AutoCloseable} so the operator lifecycle can flush remaining records.
 *
 * <p><strong>Thread-safety contract</strong> (P1-15): the nop-stream operator
 * model executes each subtask on a single task thread
 * ({@code StreamSinkOperator.processElement} → {@code consume} is invoked
 * sequentially from one thread). Accordingly {@code consume()} is NOT
 * designed for concurrent invocation and the internal {@code buffer} is
 * unsynchronized. If a future execution model introduces concurrency,
 * synchronization must be added here.</p>
 */
public class BatchConsumerSinkFunction<R> implements SinkFunction<R>, ConnectivityCheckable, AutoCloseable {

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
        // P1-15: reject null at the boundary instead of accepting it into the
        // buffer, where it would surface later as an opaque NPE during flush
        // (or worse, be silently persisted). Failing fast preserves the
        // stream's data contract.
        if (value == null) {
            throw new StreamException(ERR_STREAM_NULL_ARG).param(ARG_ARG_NAME, "value");
        }
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
        Exception flushError = null;
        try {
            if (!flushed) {
                flush();
                flushed = true;
            }
        } catch (Exception flushErr) {
            LOG.error("Flush failed in close() with buffer size={}, first record summary={}",
                    buffer.size(),
                    buffer.isEmpty() ? "<empty>" : String.valueOf(buffer.get(0)));
            flushError = flushErr;
        } finally {
            if (consumer instanceof AutoCloseable) {
                try {
                    ((AutoCloseable) consumer).close();
                } catch (Exception e) {
                    if (flushError != null) {
                        // Prioritize the flush failure (possible data loss) as the
                        // primary error; the close failure rides along as suppressed.
                        flushError.addSuppressed(e);
                    } else {
                        throw new StreamException(ERR_STREAM_STATE_ERROR, e)
                                .param(ARG_DETAIL, "Failed to close consumer");
                    }
                }
            }
        }
        if (flushError != null) {
            throw new StreamException(ERR_STREAM_CHAINING_OUTPUT_FLUSH_FAILED, flushError)
                    .param(ARG_DETAIL, "Flush failed in close(), data may be lost");
        }
    }

    @Override
    public SinkConsistencyCapability getSinkConsistency() {
        return SinkConsistencyCapability.IDEMPOTENT;
    }

    /**
     * Item 20 (P-REQ-13, D3 batch-consumer row): pre-submit connectivity probe —
     * this family's probe point IS the construction path (the constructor already ran
     * {@code consumerProvider.setup()}, the audit-recognized natural probe point).
     * The check therefore verifies the construction-level setup result instead of
     * re-invoking setup on the constructed instance: a null consumer means the
     * provider never connected — fail fast rather than reporting a silent pass.
     */
    @Override
    public void checkConnection() {
        if (consumer == null) {
            throw new StreamException(ERR_STREAM_STATE_ERROR)
                    .param(ARG_DETAIL, "consumer is null: consumerProvider.setup() did not run or returned "
                            + "null at construction time; sink connectivity cannot be trusted");
        }
    }
}
