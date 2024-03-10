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
import io.nop.batch.core.IBatchChunkListener;
import io.nop.batch.core.IBatchLoader;
import io.nop.batch.core.IBatchTaskContext;
import io.nop.batch.core.IBatchTaskListener;
import io.nop.batch.core.IEnhancedBatchLoader;
import io.nop.commons.collections.MapOfInt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Function;

/**
 * 利用底层的loader读取数据，然后按照partition切分成多个顺序队列。确保一个partition的数据不会同时有两个线程在处理。
 *
 * @param <S>
 */
public class PartitionDispatchLoader<S>
        implements IEnhancedBatchLoader<S, IBatchChunkContext>, IBatchTaskListener, IBatchChunkListener {
    private final IBatchLoader<S, IBatchChunkContext> loader;
    private final Executor executor;
    private final int fetchThreadCount;
    private final int loadBatchSize;
    private final Function<S, Integer> partitionFn;

    private PartitionDispatchQueue<S> queue;
    private Exception exception;

    public PartitionDispatchLoader(IBatchLoader<S, IBatchChunkContext> loader, Executor executor, int fetchThreadCount,
                                   int loadBatchSize, Function<S, Integer> partitionFn) {
        this.loader = loader;
        this.executor = executor;
        this.fetchThreadCount = fetchThreadCount;
        this.loadBatchSize = loadBatchSize;
        this.partitionFn = partitionFn;
    }

    @Override
    public IBatchLoader<S, IBatchChunkContext> getBaseLoader() {
        return loader;
    }

    @Override
    public void onTaskBegin(IBatchTaskContext context) {
        PartitionDispatchQueue<S> queue = new PartitionDispatchQueue<>(loadBatchSize * 20, partitionFn);
        this.queue = queue;

        for (int i = 0; i < fetchThreadCount; i++) {
            int threadIndex = i;
            executor.execute(() -> {
                while (!context.isCancelled()) {
                    IBatchChunkContext ctx = context.newChunkContext();
                    ctx.setConcurrency(fetchThreadCount);
                    ctx.setThreadIndex(threadIndex);
                    try {
                        List<S> list = loader.load(loadBatchSize, ctx);
                        if (list.isEmpty()) {
                            queue.markNoMoreData();
                            return;
                        }
                        queue.addBatch(list);
                    } catch (Exception e) {
                        exception = e;
                        return;
                    }
                }
            });
        }
    }

    @Override
    public void onTaskEnd(Throwable exception, IBatchTaskContext context) {
        if (!context.isDone())
            context.cancel();

        if (queue != null)
            queue.finish();

        this.queue = null;
        this.exception = null;
    }

    @Override
    public List<S> load(int batchSize, IBatchChunkContext context) {
        if (exception != null)
            throw NopException.adapt(exception);

        MapOfInt<List<S>> map = queue.takeBatch(batchSize, context.getThreadIndex());
        if (map == null) {
            return Collections.emptyList();
        }

        context.setAttribute(PartitionDispatchLoader.class.getName(), map);
        List<S> ret = new ArrayList<>(batchSize);
        map.forEachEntry((list, index) -> {
            ret.addAll(list);
        });
        return ret;
    }

    @Override
    public void onChunkEnd(Throwable exception, IBatchChunkContext context) {
        MapOfInt<List<S>> map = (MapOfInt<List<S>>) context.getAttribute(PartitionDispatchLoader.class.getName());
        if (map != null) {
            queue.completeBatch(map, context.getThreadIndex());
        }
    }
}
