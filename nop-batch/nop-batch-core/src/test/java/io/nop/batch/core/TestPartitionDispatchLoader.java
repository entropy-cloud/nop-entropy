/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.batch.core;

import io.nop.batch.core.IBatchLoaderProvider.IBatchLoader;
import io.nop.batch.core.debug.DebugBatchLoader;
import io.nop.batch.core.impl.BatchTaskContextImpl;
import io.nop.batch.core.loader.AsyncFetchPartitionDispatchLoaderProvider;
import io.nop.batch.core.loader.PartitionDispatchLoaderProvider;
import io.nop.commons.concurrent.executor.GlobalExecutors;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestPartitionDispatchLoader {
    @Test
    public void testAsyncFetchLoader() {
        int n = 1000000;
        DebugBatchLoader baseLoader = new DebugBatchLoader(n);
        int fetchThreadCount = 1;
        AsyncFetchPartitionDispatchLoaderProvider<String> loaderProvider = new AsyncFetchPartitionDispatchLoaderProvider<>(taskContext -> baseLoader,
                GlobalExecutors.cachedThreadPool(), fetchThreadCount, 20, (k, ctx) -> k.hashCode() % 3);

        IBatchTaskContext context = new BatchTaskContextImpl();
        IBatchLoader<String> loader = loaderProvider.setup(context);

        Set<String> list = new ConcurrentSkipListSet<>();
        int concurrency = 5;
        CountDownLatch latch = new CountDownLatch(concurrency);
        context.fireTaskBegin();
        for (int i = 0; i < concurrency; i++) {
            int threadIndex = i;
            GlobalExecutors.cachedThreadPool().execute(() -> {
                while (!context.isCancelled()) {
                    IBatchChunkContext chunkContext = context.newChunkContext();
                    chunkContext.setThreadIndex(threadIndex);
                    chunkContext.setConcurrency(concurrency);
                    try {
                        context.fireChunkBegin(chunkContext);
                        List<String> batch = loader.load(10, chunkContext);
                        if (batch.isEmpty()) {
                            latch.countDown();
                            return;
                        }
                        list.addAll(batch);
                        // System.out.println(StringHelper.join(batch, "\n"));
                        context.fireChunkEnd(chunkContext, null);
                        chunkContext.complete();
                    } catch (Exception e) {
                        chunkContext.completeExceptionally(e);
                    }
                }
            });
        }

        try {
            latch.await();
        } catch (Exception e) {
        }

        context.complete();
        assertEquals(n, list.size());
    }

    @Test
    public void testLoad() {
        int n = 1000000;
        DebugBatchLoader baseLoader = new DebugBatchLoader(n);
        PartitionDispatchLoaderProvider<String> loaderProvider = new PartitionDispatchLoaderProvider<>(taskContext -> baseLoader, 20, (k, ctx) -> k.hashCode() % 8);

        IBatchTaskContext context = new BatchTaskContextImpl();
        IBatchLoader<String> loader = loaderProvider.setup(context);

        Set<String> list = new ConcurrentSkipListSet<>();
        int concurrency = 5;
        CountDownLatch latch = new CountDownLatch(concurrency);
        context.fireTaskBegin();
        for (int i = 0; i < concurrency; i++) {
            int threadIndex = i;
            GlobalExecutors.cachedThreadPool().execute(() -> {
                while (!context.isCancelled()) {
                    IBatchChunkContext chunkContext = context.newChunkContext();
                    chunkContext.setThreadIndex(threadIndex);
                    chunkContext.setConcurrency(concurrency);
                    try {
                        context.fireChunkBegin(chunkContext);
                        List<String> batch = loader.load(10, chunkContext);
                        if (batch.isEmpty()) {
                            latch.countDown();
                            return;
                        }
                        list.addAll(batch);
                        // System.out.println(StringHelper.join(batch, "\n"));
                        context.fireChunkEnd(chunkContext, null);
                        chunkContext.complete();
                    } catch (Exception e) {
                        chunkContext.completeExceptionally(e);
                    }
                }
            });
        }

        try {
            latch.await();
        } catch (Exception e) {
        }

        context.complete();
        assertEquals(n, list.size());
    }
}
