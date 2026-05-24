/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.connector;

import io.nop.batch.core.IBatchChunkContext;
import io.nop.batch.core.IBatchLoaderProvider;
import io.nop.batch.core.IBatchTaskContext;
import io.nop.batch.core.impl.BatchTaskContextImpl;
import io.nop.stream.core.common.functions.source.SourceConsistencyCapability;
import io.nop.stream.core.common.functions.source.SourceFunction;

import java.util.List;

/**
 * Adapts nop-batch's {@link IBatchLoaderProvider} to nop-stream's {@link SourceFunction}.
 * <p>
 * Calls {@code loader.load(batchSize, chunkContext)} in a loop, emitting each record
 * individually to the stream. When the loader returns an empty list, the source completes.
 */
public class BatchLoaderSourceFunction<S> implements SourceFunction<S> {

    private static final long serialVersionUID = 1L;

    private final IBatchLoaderProvider<S> loaderProvider;
    private final int batchSize;

    private volatile boolean running = true;

    public BatchLoaderSourceFunction(IBatchLoaderProvider<S> loaderProvider) {
        this(loaderProvider, 1);
    }

    public BatchLoaderSourceFunction(IBatchLoaderProvider<S> loaderProvider, int batchSize) {
        if (loaderProvider == null) {
            throw new IllegalArgumentException("loaderProvider must not be null");
        }
        if (batchSize < 1) {
            throw new IllegalArgumentException("batchSize must be at least 1");
        }
        this.loaderProvider = loaderProvider;
        this.batchSize = batchSize;
    }

    @Override
    public void run(SourceContext<S> ctx) throws Exception {
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

    @Override
    public SourceConsistencyCapability getSourceConsistency() {
        return SourceConsistencyCapability.AT_LEAST_ONCE;
    }
}
