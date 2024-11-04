/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.batch.core.loader;

import io.nop.api.core.exceptions.NopException;
import io.nop.batch.core.IBatchChunkContext;
import io.nop.batch.core.IBatchLoaderProvider;
import io.nop.batch.core.IBatchTaskContext;
import io.nop.commons.collections.MapOfInt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * 利用底层的loader读取数据，然后按照partition切分成多个顺序队列。确保一个partition的数据不会同时有两个线程在处理。
 *
 * @param <S>
 */
public class PartitionDispatchLoaderProvider<S>
        implements IBatchLoaderProvider<S> {
    private final IBatchLoaderProvider<S> loader;
    private final Executor executor;
    private final int fetchThreadCount;
    private final int loadBatchSize;
    private final Function<S, Integer> partitionFn;

    public PartitionDispatchLoaderProvider(IBatchLoaderProvider<S> loader, Executor executor, int fetchThreadCount,
                                           int loadBatchSize, Function<S, Integer> partitionFn) {
        this.loader = loader;
        this.executor = executor;
        this.fetchThreadCount = fetchThreadCount;
        this.loadBatchSize = loadBatchSize;
        this.partitionFn = partitionFn;
    }

    @Override
    public IBatchLoader<S> setup(IBatchTaskContext context) {
        IBatchLoader<S> loader = this.loader.setup(context);

        PartitionDispatchQueue<S> queue = new PartitionDispatchQueue<>(loadBatchSize * 20, partitionFn, fetchThreadCount);
        AtomicReference<Exception> exception = new AtomicReference<>();

        context.addAfterComplete(err -> {
            queue.finish();
        });

        IBatchLoader<S> resultLoader = (batchSize, ctx) -> {
            Exception err = exception.get();
            if (err != null)
                throw NopException.adapt(err);

            MapOfInt<List<S>> map = queue.takeBatch(batchSize, ctx.getThreadIndex());
            if (map == null) {
                return Collections.emptyList();
            }

            ctx.addAfterComplete(error -> {
                queue.completeBatch(map, ctx.getThreadIndex());
            });

            List<S> ret = new ArrayList<>(batchSize);
            map.forEachEntry((list, index) -> {
                ret.addAll(list);
            });
            return ret;
        };

        for (int i = 0; i < fetchThreadCount; i++) {
            final int threadIndex = i;
            executor.execute(() -> {
                try {
                    while (!context.isCancelled()) {
                        IBatchChunkContext ctx = context.newChunkContext();
                        ctx.setConcurrency(fetchThreadCount);
                        ctx.setThreadIndex(threadIndex);
                        try {
                            List<S> list = loader.load(loadBatchSize, ctx);
                            if (list.isEmpty()) {
                                return;
                            }
                            queue.addBatch(list);
                        } catch (Exception e) {
                            exception.set(e);
                            return;
                        }
                    }
                } finally {
                    queue.exitFetchThread();
                }
            });
        }

        return resultLoader;
    }
}
