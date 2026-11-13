package io.nop.batch.core.impl;

import io.nop.api.core.exceptions.NopException;
import io.nop.batch.core.BatchDispatchConfig;
import io.nop.batch.core.BatchTaskBuilder;
import io.nop.commons.concurrent.executor.GlobalExecutors;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 1.7-C8-003（plan 2498 Phase 5）：batch 装配期并发护栏 + 兜底池形态钉。
 */
public class TestBatchAssemblyGuard_C8_003 {

    /**
     * rule 23 接线验证：真实 BatchTaskBuilder→BatchTask 构造链上，超界 concurrency 触发
     * 构造期类型化 fail-fast（护栏生效于真实装配链，非仅 Guard 单测）。
     */
    @Test
    public void overCapConcurrencyFailsFastOnRealBuildChain_C8_003() {
        BatchTaskBuilder<String, String> builder = new BatchTaskBuilder<>();
        builder.taskName("cap-test");
        builder.concurrency(201);

        NopException e = assertThrows(NopException.class, builder::buildTask);
        assertEquals("nop.err.batch.concurrency-exceeds-cap", e.getErrorCode());
        assertEquals("concurrency", e.getParams().get("paramName"));
        assertEquals(201, e.getParams().get("paramValue"));
        assertEquals(200, e.getParams().get("maxConcurrencyCap"));
    }

    /**
     * rule 23 接线验证：fetchThreadCount 的真实装配漏斗为 BatchDispatchConfig.setFetchThreadCount
     * （DSL 装配 ModelBasedBatchTaskBuilderFactory.buildDispatchConfig 与程序化装配共同经过），
     * 超界配置在任务装配期即报错，不进入 AsyncFetch 线程创建路径；装配失败无半装配状态。
     */
    @Test
    public void overCapFetchThreadCountFailsFastOnDispatchConfigAssembly_C8_003() {
        BatchDispatchConfig<String> config = new BatchDispatchConfig<>();

        NopException e = assertThrows(NopException.class, () -> config.setFetchThreadCount(201));
        assertEquals("nop.err.batch.concurrency-exceeds-cap", e.getErrorCode());
        assertEquals("fetchThreadCount", e.getParams().get("paramName"));
        assertEquals(201, e.getParams().get("paramValue"));
        assertEquals(200, e.getParams().get("maxConcurrencyCap"));

        assertEquals(0, config.getFetchThreadCount());
    }

    /**
     * 兜底池形态评估结论钉（1.7-C8-003 评估①）：未配置 executor 时兜底载体 = 平台共享 cached 池
     * 单例（R1-02 裁定④基线：有界 200 + 达界熔断 REE），不创建本地无界池、不迁移 globalWorker；
     * 显式配置 executor 时优先使用显式池。
     */
    @Test
    public void executorFallbackPinsPlatformBoundedCachedPool_C8_003() throws Exception {
        Executor fallback = BatchTask.getExecutor(null);
        assertSame(GlobalExecutors.cachedThreadPool(), fallback);

        Executor explicit = r -> {
        };
        assertSame(explicit, BatchTask.getExecutor(explicit));

        // 真实构造链上的兜底选择：builder 未配 executor → BatchTask 持有平台共享 cached 池
        BatchTaskBuilder<String, String> builder = new BatchTaskBuilder<>();
        builder.taskName("fallback-pool");
        builder.concurrency(4);
        BatchTask<String> task = (BatchTask<String>) builder.buildTask();
        Field executorField = BatchTask.class.getDeclaredField("executor");
        executorField.setAccessible(true);
        assertSame(GlobalExecutors.cachedThreadPool(), executorField.get(task));
    }

    /**
     * 既有文档化行为零回退钉：concurrency<=0 归一为 1（下界）；界内上界值（=cap 200）正常装配。
     */
    @Test
    public void withinCapConcurrencyBuildsAndNormalizesLowerBound_C8_003() throws Exception {
        Field concurrencyField = BatchTask.class.getDeclaredField("concurrency");
        concurrencyField.setAccessible(true);

        BatchTaskBuilder<String, String> builder = new BatchTaskBuilder<>();
        builder.taskName("within-cap-lower");
        builder.concurrency(0);
        BatchTask<String> task = (BatchTask<String>) builder.buildTask();
        assertEquals(1, concurrencyField.get(task));

        BatchTaskBuilder<String, String> builder2 = new BatchTaskBuilder<>();
        builder2.taskName("within-cap-max");
        builder2.concurrency(200);
        BatchTask<String> task2 = (BatchTask<String>) builder2.buildTask();
        assertEquals(200, concurrencyField.get(task2));
    }
}
