/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.batch.core;

import io.nop.api.core.util.FutureHelper;
import io.nop.batch.core.IBatchConsumerProvider.IBatchConsumer;
import io.nop.batch.core.IBatchLoaderProvider;
import io.nop.batch.core.IBatchLoaderProvider.IBatchLoader;
import io.nop.batch.core.impl.BatchTaskContextImpl;
import io.nop.commons.concurrent.executor.GlobalExecutors;
import io.nop.commons.util.retry.RetryPolicy;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 配置了dispatcher时loadRetryPolicy同样必须生效：加载失败要按重试策略重试，
 * 而不是被静默忽略、瞬时DB抖动直接导致任务失败
 */
public class TestBatchTaskDispatchLoadRetry {

    static class FlakyLoader implements IBatchLoaderProvider<String>, IBatchLoader<String> {
        final AtomicInteger calls = new AtomicInteger();

        @Override
        public IBatchLoader<String> setup(IBatchTaskContext context) {
            return this;
        }

        @Override
        public List<String> load(int batchSize, IBatchChunkContext context) {
            int n = calls.incrementAndGet();
            if (n == 1)
                throw new IllegalStateException("transient-db-flap");
            if (n == 2)
                return List.of("a", "b", "c", "d", "e");
            return List.of();
        }
    }

    static class RecordingConsumer implements IBatchConsumer<String>, IBatchConsumerProvider<String> {
        final List<String> items = new ArrayList<>();

        @Override
        public IBatchConsumer<String> setup(IBatchTaskContext context) {
            return this;
        }

        @Override
        public void consume(Collection<String> items, IBatchChunkContext context) {
            this.items.addAll(items);
        }
    }

    @Test
    public void testLoadRetryPolicyAppliedWithDispatchConfig() {
        FlakyLoader loader = new FlakyLoader();
        RecordingConsumer consumer = new RecordingConsumer();

        BatchTaskBuilder<String, String> builder = new BatchTaskBuilder<>();
        builder.loader(loader).consumer(consumer);
        builder.concurrency(1).executor(GlobalExecutors.cachedThreadPool()).batchSize(5);
        builder.loadRetryPolicy(RetryPolicy.retryNTimes(2));

        BatchDispatchConfig<String> dispatchConfig = new BatchDispatchConfig<>();
        dispatchConfig.setLoadBatchSize(5);
        dispatchConfig.setPartitionFn((k, ctx) -> 0);
        builder.dispatchConfig(dispatchConfig);

        IBatchTask task = builder.buildTask();

        BatchTaskContextImpl context = new BatchTaskContextImpl();
        context.setTaskName("test-dispatch-load-retry");

        CompletableFuture<Void> future = new CompletableFuture<>();
        context.onAfterComplete(err -> FutureHelper.complete(future, null, err));

        task.executeAsync(context);
        FutureHelper.syncGet(future);

        // 首次加载失败被重试恢复，5条数据全部处理完成（1次失败+1次成功+1次EOF）
        assertEquals(List.of("a", "b", "c", "d", "e"), consumer.items);
        assertEquals(3, loader.calls.get());
    }
}
