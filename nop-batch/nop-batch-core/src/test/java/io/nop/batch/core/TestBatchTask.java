/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.batch.core;

import io.nop.api.core.util.FutureHelper;
import io.nop.batch.core.impl.BatchTaskContextImpl;
import io.nop.batch.core.metrics.BatchTaskMetricsImpl;
import io.nop.commons.concurrent.executor.GlobalExecutors;
import io.nop.commons.metrics.GlobalMeterRegistry;
import io.nop.commons.util.retry.RetryPolicy;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestBatchTask {
    @Test
    public void testRetry() {
        String taskName = "test";
        BatchTaskMetricsImpl metrics = new BatchTaskMetricsImpl(GlobalMeterRegistry.instance(), null, "job", taskName);
        BatchTaskContextImpl context = new BatchTaskContextImpl();
        context.setMetrics(metrics);
        context.setTaskName(taskName);

        CompletableFuture<Void> future = new CompletableFuture<>();
        context.onAfterComplete(err -> FutureHelper.complete(future, null, err));

        BatchTaskBuilder builder = new BatchTaskBuilder();

        MockLoader loader = new MockLoader();
        MockConsumer consumer = new MockConsumer();
        builder.loader(loader).consumer(consumer).concurrency(10).executor(GlobalExecutors.cachedThreadPool());
        builder.processor(new MockProcessor());
        builder.skipPolicy(new BatchSkipPolicy().maxSkipCount(10000));
        builder.retryOneByOne(true).retryPolicy(RetryPolicy.retryNTimes(3));

        IBatchTask task = builder.buildTask();
        task.executeAsync(context);

        FutureHelper.syncGet(future);

        GlobalMeterRegistry.print();

        assertEquals(consumer.count.get(), consumer.items.size());
        assertEquals(502, consumer.count.get());
        assertEquals(502, context.getSkipItemCount());
    }

    class MockProcessor implements IBatchProcessorProvider.IBatchProcessor<String, String>, IBatchProcessorProvider<String, String> {

        @Override
        public IBatchProcessor<String, String> setup(IBatchTaskContext taskContext) {
            return this;
        }

        @Override
        public void process(String item, Consumer<String> consumer, IBatchChunkContext context) {
            int index = Integer.parseInt(item);
            if (index % 2 == 1) {
                throw new RuntimeException("error");
            }
            consumer.accept(item);
        }
    }

    class MockLoader implements IBatchLoaderProvider.IBatchLoader<String>, IBatchLoaderProvider<String> {
        int count = 0;

        @Override
        public IBatchLoader<String> setup(IBatchTaskContext context) {
            return this;
        }

        @Override
        public synchronized List<String> load(int batchSize, IBatchChunkContext context) {
            List<String> ret = new ArrayList<>();
            if (count >= 1004)
                return ret;

            for (int i = 0; i < batchSize; i++) {
                ret.add(String.valueOf(count));
                count++;

                if (count >= 1004)
                    break;
            }
            return ret;
        }
    }

    class MockConsumer implements IBatchConsumerProvider.IBatchConsumer<String>, IBatchConsumerProvider<String> {
        AtomicInteger count = new AtomicInteger();
        Set<String> items = new ConcurrentSkipListSet<>();

        @Override
        public IBatchConsumer<String> setup(IBatchTaskContext context) {
            return this;
        }

        @Override
        public void consume(Collection<String> items, IBatchChunkContext context) {
            count.addAndGet(items.size());
            this.items.addAll(items);
        }
    }
}
