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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;

/**
 * 利用底层的loader读取数据，然后按照partition切分成多个顺序队列。确保一个partition的数据不会同时有两个线程在处理。
 *
 * @param <S>
 */
public class AsyncFetchPartitionDispatchLoaderProvider<S>
        implements IBatchLoaderProvider<S> {
    static final Logger LOG = LoggerFactory.getLogger(AsyncFetchPartitionDispatchLoaderProvider.class);

    private final IBatchLoaderProvider<S> loader;
    private final Executor executor;
    private final int fetchThreadCount;
    private final int loadBatchSize;
    private final BiFunction<S, IBatchTaskContext, Integer> partitionFn;

    private int loadBatchMultiplyFactor = 30;
    private int maxLockQueueCountPerThread = 100;

    /**
     * 串行化load+addBatch。多个fetch线程并发执行时，保证数据页按底层loader的源序依次进入队列，
     * 否则同一partition内的记录处理顺序会被跨页打乱
     */
    private final Object fetchMutex = new Object();

    public AsyncFetchPartitionDispatchLoaderProvider(IBatchLoaderProvider<S> loader, Executor executor, int fetchThreadCount,
                                                     int loadBatchSize, BiFunction<S, IBatchTaskContext, Integer> partitionFn) {
        this.loader = loader;
        this.executor = executor;
        this.fetchThreadCount = fetchThreadCount;
        this.loadBatchSize = loadBatchSize;
        this.partitionFn = partitionFn;
    }

    public int getLoadBatchMultiplyFactor() {
        return loadBatchMultiplyFactor;
    }

    public void setLoadBatchMultiplyFactor(int loadBatchMultiplyFactor) {
        this.loadBatchMultiplyFactor = loadBatchMultiplyFactor;
    }

    public int getMaxLockQueueCountPerThread() {
        return maxLockQueueCountPerThread;
    }

    public void setMaxLockQueueCountPerThread(int maxLockQueueCountPerThread) {
        this.maxLockQueueCountPerThread = maxLockQueueCountPerThread;
    }

    @Override
    public IBatchLoader<S> setup(IBatchTaskContext context) {
        IBatchLoader<S> loader = this.loader.setup(context);

        PartitionDispatchQueue<S> queue = new PartitionDispatchQueue<>(loadBatchSize * loadBatchMultiplyFactor,
                item -> partitionFn.apply(item, context), fetchThreadCount);
        queue.setMaxLockQueueCountPerThread(maxLockQueueCountPerThread);

        AtomicReference<Exception> exception = new AtomicReference<>();

        context.onAfterComplete(err -> {
            queue.finish();
        });

        IBatchLoader<S> resultLoader = (batchSize, ctx) -> {
            Exception err = exception.get();
            if (err != null)
                throw NopException.adapt(err);

            MapOfInt<List<S>> map = queue.takeBatch(batchSize, ctx.getThreadIndex(), null);
            if (map == null) {
                // takeBatch返回null表示不再有数据。此时fetch线程可能是因为异常而退出，
                // 需要再次检查异常，避免加载失败被静默吞掉、任务以0条记录"成功"结束
                err = exception.get();
                if (err != null)
                    throw NopException.adapt(err);
                return Collections.emptyList();
            }

            ctx.onAfterComplete(error -> {
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
                    while (!context.isCancelled() && !queue.isFinished()) {
                        IBatchChunkContext ctx = context.newChunkContext();
                        ctx.setConcurrency(fetchThreadCount);
                        ctx.setThreadIndex(threadIndex);
                        try {
                            // load与addBatch在同一临界区内完成，保证页按源序入队
                            synchronized (fetchMutex) {
                                List<S> list = loader.load(loadBatchSize, ctx);
                                if (list.isEmpty()) {
                                    queue.markNoMorData();
                                    LOG.info("nop.batch.exit-fetch-thread:threadIndex={}", threadIndex);
                                    return;
                                }
                                queue.addBatch(list);
                            }
                        } catch (Exception e) {
                            LOG.error("nop.batch.exit-fetch-thread-when-fail:threadIndex={}", threadIndex, e);
                            // 保留第一个异常作为根因，避免被后续线程的次要异常覆盖
                            exception.compareAndSet(null, e);
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
