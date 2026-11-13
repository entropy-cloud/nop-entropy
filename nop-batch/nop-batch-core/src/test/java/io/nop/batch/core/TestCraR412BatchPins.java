/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.batch.core;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.nop.api.core.config.AppConfig;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.FutureHelper;
import io.nop.batch.core.impl.BatchTaskContextImpl;
import io.nop.batch.core.loader.PartitionDispatchQueue;
import io.nop.batch.core.metrics.BatchTaskMetricsImpl;
import io.nop.commons.metrics.GlobalMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2546 R4-12 红钉族（core 侧，纯单元；DB 相关项由各模块自建测试承载）。
 */
public class TestCraR412BatchPins {

    // ==================== W-01 / C7-005 ====================

    /** pin01：负 maxSkipCount 必须类型化 fail-fast（缺陷态=静默当作"无限"）。 */
    @Test
    public void pin01_negativeSkipCountFailsFast() {
        BatchSkipPolicy policy = new BatchSkipPolicy();
        NopException err = assertThrows(NopException.class, () -> policy.setMaxSkipCount(-1));
        assertEquals("nop.err.batch.skip-count-invalid", err.getErrorCode());
        assertEquals(-1L, err.getParams().get("skipCount"));
    }

    /** pin01b：0 = 显式不设上限（不抛，且 shouldSkip 永不做饱和检查）。 */
    @Test
    public void pin01b_zeroMeansUnbounded() {
        BatchSkipPolicy policy = new BatchSkipPolicy().maxSkipCount(0);
        assertEquals(0L, policy.getMaxSkipCount());
        assertTrue(policy.shouldSkip(new RuntimeException("x"), 1_000_000L, null),
                "maxSkipCount=0 必须永不饱和拒绝");
    }

    /** pin01c：正上限确实会饱和（守卫语义未回归）。 */
    @Test
    public void pin01c_positiveLimitSaturates() {
        BatchSkipPolicy policy = new BatchSkipPolicy().maxSkipCount(3);
        assertTrue(policy.shouldSkip(new RuntimeException("x"), 2L, null));
        assertFalse(policy.shouldSkip(new RuntimeException("x"), 3L, null));
    }

    // ==================== W-09 / C8-008 ====================

    /** pin07a：三个只读水位出口读数正确。 */
    @Test
    public void pin07a_watermarkAccessors() {
        PartitionDispatchQueue<String> queue = new PartitionDispatchQueue<>(10, item -> item.length());
        assertEquals(0, queue.getCount());
        assertEquals(10, queue.getAvailablePermits());
        assertEquals(0, queue.getPartitionCount());

        queue.addBatch(java.util.List.of("a", "bb", "ccc"));
        assertEquals(3, queue.getCount());
        assertEquals(7, queue.getAvailablePermits());
        assertEquals(3, queue.getPartitionCount());
        queue.finish();
    }

    /** pin07b：Gauge 注册后可读；taskId==null 时不得 NPE（exec.id 回退）。 */
    @Test
    public void pin07b_gaugeReadableWithNullTaskId() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        GlobalMeterRegistry.registerInstance(registry);
        try {
            PartitionDispatchQueue<String> queue = new PartitionDispatchQueue<>(10, item -> 0);
            // 模拟 AsyncFetch provider 的注册语义（exec.id 三级回退）
            BatchTaskContextImpl ctx = new BatchTaskContextImpl();
            ctx.setTaskName("pin07b-task");
            ctx.setTaskKey("key-1");
            assertEquals(null, ctx.getTaskId());

            List<Gauge> gauges = io.nop.batch.core.loader.AsyncFetchPartitionDispatchLoaderProvider
                    .registerWatermarkGauges(ctx, queue);
            assertEquals(2, gauges.size());
            queue.addBatch(java.util.List.of("x", "y"));
            Gauge backlog = registry.find("nop.batch.dispatch.backlog")
                    .tag("exec.id", "key-1").gauge();
            assertNotNull(backlog, "Gauge 必须可按 task.name+exec.id 找到");
            assertEquals(2.0, backlog.value(), 0.001);
            queue.finish();
        } finally {
            GlobalMeterRegistry.registerInstance(new SimpleMeterRegistry());
        }
    }

    /** pin07c：注销后 Gauge 不再可查（防 meter-id 复用陈旧读数 + 逐任务累积）。 */
    @Test
    public void pin07c_gaugeRemovedAfterComplete() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        GlobalMeterRegistry.registerInstance(registry);
        try {
            PartitionDispatchQueue<String> queue = new PartitionDispatchQueue<>(10, item -> 0);
            BatchTaskContextImpl ctx = new BatchTaskContextImpl();
            ctx.setTaskName("pin07c-task");
            ctx.setTaskKey("key-c");

            List<Gauge> gauges = io.nop.batch.core.loader.AsyncFetchPartitionDispatchLoaderProvider
                    .registerWatermarkGauges(ctx, queue);
            for (Gauge g : gauges)
                registry.remove(g);
            assertEquals(null, registry.find("nop.batch.dispatch.backlog").tag("exec.id", "key-c").gauge(),
                    "注销后必须不可查");
            queue.finish();
        } finally {
            GlobalMeterRegistry.registerInstance(new SimpleMeterRegistry());
        }
    }

    /** pin07d：同名并发两任务 exec.id 不同 → meter id 不互撞、互不覆盖。 */
    @Test
    public void pin07d_sameTaskNameDifferentExecIds() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        GlobalMeterRegistry.registerInstance(registry);
        try {
            PartitionDispatchQueue<String> q1 = new PartitionDispatchQueue<>(10, item -> 0);
            PartitionDispatchQueue<String> q2 = new PartitionDispatchQueue<>(10, item -> 0);
            BatchTaskContextImpl c1 = new BatchTaskContextImpl();
            c1.setTaskName("same"); c1.setTaskKey("e1");
            BatchTaskContextImpl c2 = new BatchTaskContextImpl();
            c2.setTaskName("same"); c2.setTaskKey("e2");

            io.nop.batch.core.loader.AsyncFetchPartitionDispatchLoaderProvider.registerWatermarkGauges(c1, q1);
            io.nop.batch.core.loader.AsyncFetchPartitionDispatchLoaderProvider.registerWatermarkGauges(c2, q2);

            q1.addBatch(java.util.List.of("a"));
            q2.addBatch(java.util.List.of("a", "b", "c"));

            assertEquals(1.0, registry.find("nop.batch.dispatch.backlog").tag("exec.id", "e1").gauge().value(), 0.001);
            assertEquals(3.0, registry.find("nop.batch.dispatch.backlog").tag("exec.id", "e2").gauge().value(), 0.001);
            q1.finish(); q2.finish();
        } finally {
            GlobalMeterRegistry.registerInstance(new SimpleMeterRegistry());
        }
    }

    // ==================== W-07 / C8-004 ====================

    /** pin08a：真实 executeAsync 后 SUCCESS Timer 计数 > 0，且 task.name 为真实任务名。 */
    @Test
    public void pin08a_taskTimerCountsWithRealTaskName() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        GlobalMeterRegistry.registerInstance(registry);
        String unique = "pin08a-" + System.nanoTime();
        try {
            BatchTaskBuilder<String, String> builder = new BatchTaskBuilder<>();
            builder.taskName(unique);
            builder.loader(ctx -> onceLoader("v"));
            builder.consumer(ctx -> (items, c) -> {
            });
            builder.concurrency(1);

            IBatchTaskContext ctx = new BatchTaskContextImpl();
            FutureHelper.syncGet(builder.buildTask().executeAsync(ctx));

            // 必须带 status=SUCCESS——nop.batch.task 下有两个仅靠 status 区分的 Timer
            Timer success = registry.find("nop.batch.task")
                    .tags("task.name", unique, "status", "SUCCESS").timer();
            assertNotNull(success, "必须能按 task.name+status=SUCCESS 找到 Timer");
            assertTrue(success.count() > 0, "SUCCESS Timer 必须真实计时（count>0），实际=" + success.count());
        } finally {
            GlobalMeterRegistry.registerInstance(new SimpleMeterRegistry());
        }
    }

    /** pin08b：外部已注入 metrics 时不被覆盖，beginTask/endTask 落在注入实例上。 */
    @Test
    public void pin08b_injectedMetricsRespected() {
        CountingMetrics injected = new CountingMetrics();
        BatchTaskContextImpl ctx = new BatchTaskContextImpl();
        ctx.setMetrics(injected);

        BatchTaskBuilder<String, String> builder = new BatchTaskBuilder<>();
        builder.taskName("pin08b");
        builder.loader(c -> onceLoader("v"));
        builder.consumer(c -> (items, chunkCtx) -> {
        });
        builder.concurrency(1);

        FutureHelper.syncGet(builder.buildTask().executeAsync(ctx));
        assertTrue(injected.begun.get() > 0, "beginTask 必须落在注入实例上");
        assertTrue(injected.ended.get() > 0, "endTask 必须落在注入实例上");
        assertEquals(injected, ctx.getMetrics(), "外部注入的 metrics 不得被覆盖");
    }

    /** pin08c：nop.batch.metrics.enabled=false 时不自动注册任何 task meter。 */
    @Test
    public void pin08c_metricsDisabledRegistersNothing() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        GlobalMeterRegistry.registerInstance(registry);
        boolean prev = BatchConfigs.CFG_BATCH_METRICS_ENABLED.get();
        AppConfig.getConfigProvider().updateConfigValue(BatchConfigs.CFG_BATCH_METRICS_ENABLED, false);
        String unique = "pin08c-" + System.nanoTime();
        try {
            BatchTaskBuilder<String, String> builder = new BatchTaskBuilder<>();
            builder.taskName(unique);
            builder.loader(c -> onceLoader("v"));
            builder.consumer(c -> (items, chunkCtx) -> {
            });
            builder.concurrency(1);

            BatchTaskContextImpl ctx = new BatchTaskContextImpl();
            FutureHelper.syncGet(builder.buildTask().executeAsync(ctx));

            assertEquals(0, registry.find("nop.batch.task").tag("task.name", unique).timers().size(),
                    "开关关闭时不得注册 task meter");
        } finally {
            AppConfig.getConfigProvider().updateConfigValue(BatchConfigs.CFG_BATCH_METRICS_ENABLED, prev);
            GlobalMeterRegistry.registerInstance(new SimpleMeterRegistry());
        }
    }

    // ==================== W-08 / C8-007 ====================

    /** pin09a：重试后 metrics.retry 计数 > 0（缺陷态恒 0）。 */
    @Test
    public void pin09a_retryMetricIncremented() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        GlobalMeterRegistry.registerInstance(registry);
        String unique = "pin09a-" + System.nanoTime();
        try {
            BatchTaskMetricsImpl metrics = new BatchTaskMetricsImpl(registry, null, null, unique);
            BatchTaskContextImpl ctx = new BatchTaskContextImpl();
            ctx.setMetrics(metrics);
            ctx.setTaskName(unique);

            IBatchChunkContext chunk = ctx.newChunkContext();
            io.nop.commons.util.retry.RetryPolicy<IBatchChunkContext> policy =
                    io.nop.commons.util.retry.RetryPolicy.retryNTimes(1);
            io.nop.batch.core.consumer.RetryConsumeHelper.checkRetry(policy, new RuntimeException("x"), 1, chunk);

            assertTrue(metrics.getRetryItemCount() > 0, "重试必须进 metrics.retry（缺陷态恒 0）");
        } finally {
            GlobalMeterRegistry.registerInstance(new SimpleMeterRegistry());
        }
    }

    /** pin09b：getLoadItemCount 返回条数（非 chunk 次数）。 */
    @Test
    public void pin09b_loadItemCountIsItemCount() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        BatchTaskMetricsImpl metrics = new BatchTaskMetricsImpl(registry, null, null, "pin09b");

        metrics.endLoad(metrics.beginLoad(), 100, true);   // 一次 load 采样，100 条
        metrics.endLoad(metrics.beginLoad(), 50, true);    // 第二次采样，50 条

        assertEquals(150L, metrics.getLoadItemCount(),
                "必须返回条目数（150），而非 Timer 采样次数（2）");
    }

    // ==================== W-10 / C8-009 ====================

    /** pin10a：未配置（0）→ Provider 保留缺省 30/100。 */
    @Test
    public void pin10a_unsetKeepsProviderDefaults() {
        BatchDispatchConfig<Object> config = new BatchDispatchConfig<>();
        assertEquals(0, config.getLoadBatchMultiplyFactor());
        assertEquals(0, config.getMaxLockQueueCountPerThread());
    }

    /** pin10b：config 设负值 → fail-fast；Provider setter 设 0 → fail-fast。 */
    @Test
    public void pin10b_negativeAndZeroGuarded() {
        BatchDispatchConfig<Object> config = new BatchDispatchConfig<>();
        NopException e1 = assertThrows(NopException.class, () -> config.setLoadBatchMultiplyFactor(-1));
        assertEquals("nop.err.batch.dispatch-factor-invalid", e1.getErrorCode());
        NopException e2 = assertThrows(NopException.class, () -> config.setMaxLockQueueCountPerThread(-5));
        assertEquals("nop.err.batch.dispatch-factor-invalid", e2.getErrorCode());

        io.nop.batch.core.loader.AsyncFetchPartitionDispatchLoaderProvider<Object> provider =
                new io.nop.batch.core.loader.AsyncFetchPartitionDispatchLoaderProvider<>(
                        ctx -> (batchSize, chunkCtx) -> new ArrayList<>(), null, 1, 10, (item, ctx) -> 0);
        NopException e3 = assertThrows(NopException.class, () -> provider.setLoadBatchMultiplyFactor(0));
        assertEquals("nop.err.batch.dispatch-factor-invalid", e3.getErrorCode());
    }

    /** pin10c：设正值 → 透传并生效（Provider 读到该值）。 */
    @Test
    public void pin10c_positiveValuePropagates() {
        io.nop.batch.core.loader.PartitionDispatchLoaderProvider<Object> provider =
                new io.nop.batch.core.loader.PartitionDispatchLoaderProvider<>(
                        ctx -> (batchSize, chunkCtx) -> new ArrayList<>(), 10, (item, ctx) -> 0);
        assertEquals(30, provider.getLoadBatchMultiplyFactor());
        provider.setLoadBatchMultiplyFactor(7);
        provider.setMaxLockQueueCountPerThread(11);
        assertEquals(7, provider.getLoadBatchMultiplyFactor());
        assertEquals(11, provider.getMaxLockQueueCountPerThread());
    }

    // ==================== 辅助 ====================

    private static IBatchLoaderProvider.IBatchLoader<String> onceLoader(String value) {
        AtomicInteger n = new AtomicInteger();
        return (batchSize, ctx) -> n.getAndIncrement() == 0
                ? new ArrayList<>(java.util.List.of(value)) : new ArrayList<>();
    }

    static class CountingMetrics implements IBatchTaskMetrics {
        final AtomicInteger begun = new AtomicInteger();
        final AtomicInteger ended = new AtomicInteger();

        @Override public Object beginTask() { begun.incrementAndGet(); return new Object(); }
        @Override public void endTask(Object meter, boolean success) { ended.incrementAndGet(); }
        @Override public Object beginChunk() { return new Object(); }
        @Override public void endChunk(Object meter, boolean success) { }
        @Override public Object beginLoad() { return new Object(); }
        @Override public void endLoad(Object meter, int count, boolean success) { }
        @Override public Object beginConsume(int count) { return new Object(); }
        @Override public void endConsume(Object meter, int count, boolean success) { }
        @Override public Object beginProcess() { return new Object(); }
        @Override public void endProcess(Object meter, boolean success) { }
        @Override public void retry(int count) { }
        @Override public void skipError(int count) { }
        @Override public long getLoadItemCount() { return 0; }
        @Override public long getProcessItemCount() { return 0; }
        @Override public long getSkipItemCount() { return 0; }
        @Override public long getRetryItemCount() { return 0; }
        @Override public long getConsumeItemCount() { return 0; }
    }
}
