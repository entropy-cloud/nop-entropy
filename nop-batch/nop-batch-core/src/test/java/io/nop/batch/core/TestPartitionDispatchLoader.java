/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.batch.core;

import io.nop.batch.core.debug.DebugLoader;
import io.nop.batch.core.impl.BatchTaskContextImpl;
import io.nop.batch.core.loader.PartitionDispatchLoader;
import io.nop.commons.concurrent.executor.GlobalExecutors;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestPartitionDispatchLoader {
    @Test
    public void testLoad() {
        int n = 1000000;
        DebugLoader baseLoader = new DebugLoader(n);
        int fetchThreadCount = 1;
        PartitionDispatchLoader<String> loader = new PartitionDispatchLoader<>(baseLoader,
                GlobalExecutors.cachedThreadPool(), fetchThreadCount, 20, k -> k.hashCode() % 3);

        IBatchTaskContext context = new BatchTaskContextImpl();
        Set<String> list = new ConcurrentSkipListSet<>();
        int concurrency = 5;
        CountDownLatch latch = new CountDownLatch(concurrency);
        loader.onTaskBegin(context);
        for (int i = 0; i < concurrency; i++) {
            int threadIndex = i;
            GlobalExecutors.cachedThreadPool().execute(() -> {
                while (!context.isCancelled()) {
                    IBatchChunkContext chunkContext = context.newChunkContext();
                    chunkContext.setThreadIndex(threadIndex);
                    chunkContext.setConcurrency(concurrency);
                    loader.onChunkBegin(chunkContext);
                    List<String> batch = loader.load(10, chunkContext);
                    if (batch.isEmpty()) {
                        latch.countDown();
                        return;
                    }
                    list.addAll(batch);
                    // System.out.println(StringHelper.join(batch, "\n"));
                    loader.onChunkEnd(null, chunkContext);
                }
            });
        }

        try {
            latch.await();
        } catch (Exception e) {
        }

        loader.onTaskEnd(null, context);
        assertEquals(n, list.size());
    }
}
