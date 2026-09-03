/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.connector.batch;

import java.util.List;

import io.nop.batch.core.IBatchChunkContext;
import io.nop.batch.core.IBatchLoaderProvider;
import io.nop.batch.core.IBatchTaskContext;
import io.nop.batch.core.impl.BatchTaskContextImpl;

import io.nop.stream.core.common.functions.source.ReplayableSourceFunction;
import io.nop.stream.core.common.functions.source.SourceConsistencyCapability;
import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.connector.ConnectivityCheckable;
import io.nop.stream.core.exceptions.StreamException;

import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_ARG_NAME;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_INVALID_ARG;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_NULL_ARG;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_STATE_ERROR;

/**
 * Adapts nop-batch's {@link IBatchLoaderProvider} to nop-stream's {@link SourceFunction}.
 * <p>
 * Calls {@code loader.load(batchSize, chunkContext)} in a loop, emitting each record
 * individually to the stream. When the loader returns an empty list, the source completes.
 */
public class BatchLoaderSourceFunction<S> implements ReplayableSourceFunction<S>, ConnectivityCheckable {

    private static final long serialVersionUID = 1L;

    private final IBatchLoaderProvider<S> loaderProvider;
    private final int batchSize;

    private volatile boolean running = true;

    private long currentOffset = -1;

    public BatchLoaderSourceFunction(IBatchLoaderProvider<S> loaderProvider) {
        this(loaderProvider, 1);
    }

    public BatchLoaderSourceFunction(IBatchLoaderProvider<S> loaderProvider, int batchSize) {
        if (loaderProvider == null) {
            throw new StreamException(ERR_STREAM_NULL_ARG).param(ARG_ARG_NAME, "loaderProvider");
        }
        if (batchSize < 1) {
            throw new StreamException(ERR_STREAM_INVALID_ARG).param(ARG_ARG_NAME, "batchSize")
                    .param(ARG_DETAIL, "must be at least 1");
        }
        this.loaderProvider = loaderProvider;
        this.batchSize = batchSize;
    }

    @Override
    public void run(SourceContext<S> ctx) throws Exception {
        // Reset lifecycle state: a region restart reuses this instance (the rebuilt
        // operator chain shares the source function) after cancel() set running=false.
        // Without this reset the loop below exits immediately and the source is
        // silently treated as EOS (data flow stall).
        this.running = true;
        IBatchTaskContext taskContext = new BatchTaskContextImpl();
        IBatchLoaderProvider.IBatchLoader<S> loader = loaderProvider.setup(taskContext);
        try {
            IBatchChunkContext chunkContext = taskContext.newChunkContext();

            while (running) {
                List<S> batch = loader.load(batchSize, chunkContext);
                if (batch == null || batch.isEmpty()) {
                    break;
                }
                for (S item : batch) {
                    if (!running) {
                        return;
                    }
                    ctx.collect(item);
                    currentOffset++;
                }
            }
        } finally {
            if (loader instanceof AutoCloseable) {
                ((AutoCloseable) loader).close();
            }
        }
    }

    @Override
    public void cancel() {
        running = false;
    }

    /**
     * Item 20 (P-REQ-13, D3 batch-loader row): pre-submit connectivity probe —
     * {@code loaderProvider.setup()} exercises the provider's connectivity (e.g. a
     * JDBC loader opens its connection) without consuming any batch record; the loader
     * is closed when {@code AutoCloseable} (D3-⑥ cleanup semantics).
     */
    @Override
    public void checkConnection() throws Exception {
        IBatchTaskContext taskContext = new BatchTaskContextImpl();
        IBatchLoaderProvider.IBatchLoader<S> loader = loaderProvider.setup(taskContext);
        if (loader == null) {
            throw new StreamException(ERR_STREAM_STATE_ERROR)
                    .param(ARG_DETAIL, "loaderProvider.setup() returned null loader; provider connectivity "
                            + "cannot be verified");
        }
        if (loader instanceof AutoCloseable closeable) {
            closeable.close();
        }
    }

    @Override
    public SourceConsistencyCapability getSourceConsistency() {
        return SourceConsistencyCapability.AT_LEAST_ONCE;
    }

    @Override
    public long getCurrentOffset() {
        return currentOffset;
    }

    @Override
    public void seek(long offset) {
        this.currentOffset = offset;
    }
}
