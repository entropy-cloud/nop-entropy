/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.batch.core;

import io.nop.batch.core.IBatchLoaderProvider.IBatchLoader;
import io.nop.batch.core.impl.BatchTaskContextImpl;
import io.nop.batch.core.loader.AsyncFetchPartitionDispatchLoaderProvider;
import io.nop.commons.concurrent.executor.GlobalExecutors;
import io.nop.commons.concurrent.thread.ThreadHelper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * fetch线程加载失败时，正在takeBatch中等待数据的消费线程必须感知到异常并以失败结束，
 * 而不是把空结果当作EOF、任务以已取出的数据"成功"结束
 */
public class TestAsyncFetchLoaderFailure {

    private IBatchChunkContext newChunkContext(IBatchTaskContext context) {
        IBatchChunkContext chunkContext = context.newChunkContext();
        chunkContext.setConcurrency(1);
        chunkContext.setThreadIndex(0);
        return chunkContext;
    }

    @Test
    public void testFetchFailWhileConsumerWaiting() throws InterruptedException {
        Object gate = new Object();
        IBatchLoaderProvider<String> loader = ctx -> (batchSize, chunkCtx) -> {
            synchronized (gate) {
                gate.notifyAll();
                try {
                    gate.wait(5000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            throw new IllegalStateException("db-unreachable");
        };

        AsyncFetchPartitionDispatchLoaderProvider<String> provider =
                new AsyncFetchPartitionDispatchLoaderProvider<>(loader,
                        GlobalExecutors.cachedThreadPool(), 1, 5, (k, ctx) -> 0);

        IBatchTaskContext context = new BatchTaskContextImpl();
        IBatchLoader<String> resultLoader = provider.setup(context);
        context.fireTaskBegin();

        CountDownLatch consumerStarted = new CountDownLatch(1);
        AtomicReference<RuntimeException> consumerError = new AtomicReference<>();
        AtomicReference<List<String>> consumerResult = new AtomicReference<>();
        Thread consumer = new Thread(() -> {
            consumerStarted.countDown();
            try {
                consumerResult.set(resultLoader.load(5, newChunkContext(context)));
            } catch (RuntimeException e) {
                consumerError.set(e);
            }
        });
        consumer.setDaemon(true);
        consumer.start();

        // 等待消费线程进入takeBatch等待（fetch线程在gate上挂起，尚未失败）
        assertTrue(consumerStarted.await(5, TimeUnit.SECONDS));
        ThreadHelper.sleep(300);

        // 释放gate让fetch线程失败
        synchronized (gate) {
            gate.notifyAll();
        }
        consumer.join(10000);
        context.complete();

        // 修复前：takeBatch返回null后不再检查exception，消费线程拿到空列表（假EOF）
        // 修复后：抛出fetch线程的失败异常
        RuntimeException error = consumerError.get();
        assertTrue(error != null, "consumer load should fail with the fetch error instead of returning empty list");
        assertEquals("db-unreachable", error.getMessage());
    }

    @Test
    public void testFetchFailAfterFirstPage() throws InterruptedException {
        Object gate = new Object();
        AtomicInteger loads = new AtomicInteger();

        IBatchLoaderProvider<String> loader = ctx -> (batchSize, chunkCtx) -> {
            if (loads.incrementAndGet() == 1)
                return List.of("a", "b", "c", "d", "e");
            // 第二页加载挂起，等消费线程进入takeBatch等待后再失败
            synchronized (gate) {
                try {
                    gate.wait(5000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            throw new IllegalStateException("db-flap");
        };

        AsyncFetchPartitionDispatchLoaderProvider<String> provider =
                new AsyncFetchPartitionDispatchLoaderProvider<>(loader,
                        GlobalExecutors.cachedThreadPool(), 1, 5, (k, ctx) -> 0);

        IBatchTaskContext context = new BatchTaskContextImpl();
        IBatchLoader<String> resultLoader = provider.setup(context);
        context.fireTaskBegin();

        List<String> first = resultLoader.load(5, newChunkContext(context));
        assertEquals(5, first.size());

        CountDownLatch consumerStarted = new CountDownLatch(1);
        AtomicReference<RuntimeException> consumerError = new AtomicReference<>();
        Thread consumer = new Thread(() -> {
            consumerStarted.countDown();
            try {
                resultLoader.load(5, newChunkContext(context));
            } catch (RuntimeException e) {
                consumerError.set(e);
            }
        });
        consumer.setDaemon(true);
        consumer.start();

        assertTrue(consumerStarted.await(5, TimeUnit.SECONDS));
        ThreadHelper.sleep(300);

        synchronized (gate) {
            gate.notifyAll();
        }
        consumer.join(10000);
        context.complete();

        RuntimeException error = consumerError.get();
        assertTrue(error != null, "consumer load should fail with the fetch error instead of returning empty list");
        assertEquals("db-flap", error.getMessage());
    }
}
