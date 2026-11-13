/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.concurrent.executor;

import io.nop.api.core.config.AppConfig;
import io.nop.api.core.exceptions.NopException;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static io.nop.commons.CommonConfigs.CFG_CONCURRENT_CACHED_POOL_MAX_POOL_SIZE;
import static io.nop.commons.CommonConfigs.CFG_CONCURRENT_GLOBAL_TIMER_CORE_POOL_SIZE;
import static io.nop.commons.CommonConfigs.CFG_CONCURRENT_GLOBAL_TIMER_MAX_POOL_SIZE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 平台池设施面加固回归（设计契约：{@code ai-dev/design/concurrency/pool-facility-hardening.md}）。
 * 裁定②③④⑤各占具名测试（测试名携带工单号），另含 C10-010 成员面的容量 0/负值
 * 跨工厂语义一致性表驱动矩阵。绝缘层语义回归由 TestPeriodicTaskInsulation 承载，互不修改。
 */
public class TestThreadPoolFacilityHardening {
    static final String ERR_INVALID_PARAM = "nop.err.commons.concurrent.invalid-thread-pool-param";
    static final String ERR_CACHED_SATURATED = "nop.err.commons.concurrent.cached-pool-saturated";

    private static void assignConfig(String name, Object value) {
        AppConfig.getConfigProvider().assignConfigValue(name, value);
    }

    private static void clearConfig(String name) {
        AppConfig.getConfigProvider().assignConfigValue(name, null);
    }

    /**
     * 工单 2.1-C4-001（裁定②）：globalTimer 容量配置真实生效——新键为权威键（默认 4=历史实际生效容量），
     * 废弃旧键仅在「旧键显式配置且新键未配置」时回读并 WARN；配置值→解析值传导成立，不再死配置。
     * rule 23 接线面：globalTimer() 单例的 config 容量与 stats 可观测容量一致、config 不再被工厂改写。
     */
    @Test
    public void testGlobalTimerCorePoolSizeConfigTransmits_C4_001() {
        // 两者皆未配置 → 新键默认 4（= 历史实际生效容量，升级零行为变化）
        assertEquals(4, GlobalExecutors.resolveGlobalTimerCorePoolSize());

        // 仅旧键显式配置 → 回读旧值（不存在「配了不认」的静默断档）
        try {
            assignConfig(CFG_CONCURRENT_GLOBAL_TIMER_MAX_POOL_SIZE.getName(), 7);
            assertEquals(7, GlobalExecutors.resolveGlobalTimerCorePoolSize());

            // 旧键 + 新键都配置 → 新键权威，旧键废弃忽略
            assignConfig(CFG_CONCURRENT_GLOBAL_TIMER_CORE_POOL_SIZE.getName(), 9);
            assertEquals(9, GlobalExecutors.resolveGlobalTimerCorePoolSize());

            // 仅新键配置 → 配置值传导（死配置修复的行为钉：配置改设必须生效）
            clearConfig(CFG_CONCURRENT_GLOBAL_TIMER_MAX_POOL_SIZE.getName());
            assertEquals(9, GlobalExecutors.resolveGlobalTimerCorePoolSize());
        } finally {
            clearConfig(CFG_CONCURRENT_GLOBAL_TIMER_MAX_POOL_SIZE.getName());
            clearConfig(CFG_CONCURRENT_GLOBAL_TIMER_CORE_POOL_SIZE.getName());
        }

        // 清理后回归默认
        assertEquals(4, GlobalExecutors.resolveGlobalTimerCorePoolSize());

        // 真实构造链接线验证：平台共享单例 config 容量 == 解析值 == 池可观测容量；
        // maxPoolSize 保留惰性默认 20（不再是副作用改写后的假值 0）
        DefaultScheduledExecutor timer = (DefaultScheduledExecutor) GlobalExecutors.globalTimer();
        assertEquals(GlobalExecutors.resolveGlobalTimerCorePoolSize(), timer.getConfig().getCorePoolSize());
        assertEquals(timer.getConfig().getCorePoolSize(), timer.stats().getCorePoolSize());
        assertEquals(20, timer.getConfig().getMaxPoolSize());
    }

    /**
     * 工单 2.1-C4-001（裁定②副作用面）：newScheduledExecutor 不再改写调用方持有的 config 对象——
     * 构造后 getConfig().getMaxPoolSize() 保留调用方设置值（曾被恒改写为 0），容量语义成文为 core-only。
     */
    @Test
    public void testScheduledExecutorFactoryDoesNotRewriteCallerConfig_C4_001() {
        ThreadPoolConfig config = new ThreadPoolConfig();
        config.setName("t-no-rewrite");
        config.setCorePoolSize(3);
        config.setMaxPoolSize(9);

        DefaultScheduledExecutor timer = new DefaultScheduledExecutor();
        timer.setConfig(config);
        try {
            timer.init();
            // 调用方 config 对象零改写（旧实现此处恒为 0：getConfig() 假读数 + refreshConfig max 分支永久失活）
            assertEquals(9, timer.getConfig().getMaxPoolSize());
            assertEquals(3, timer.getConfig().getCorePoolSize());
            assertEquals(3, timer.stats().getCorePoolSize());
        } finally {
            timer.destroy();
        }
    }

    /**
     * 工单 2.1-C4-003（裁定③）：池工厂入口 fail-fast 表驱动校验——poolSize≥1 / queueSize≥1 /
     * maxPoolSize≠0 / max≥core / queueCapacity≥1 / scheduled core≥1，违例抛
     * NopException(invalid-thread-pool-param)，杜绝裸 IAE 与静默 cached 语义。
     */
    @Test
    public void testFactoryParamValidationTableDriven_C4_003() {
        record Rejection(String label, Runnable call) {
        }
        Rejection[] rejections = {
                // 三/四参重载：poolSize 合法域 >=1（负值不再静默变 0-core cached）
                new Rejection("poolSize=0", () -> DefaultThreadPoolExecutor.newExecutor("t-c4-003", 0, 10)),
                new Rejection("poolSize=-1", () -> DefaultThreadPoolExecutor.newExecutor("t-c4-003", -1, 10)),
                // queueSize 合法域 >=1（0/-1 不再深爆 TaskQueue/LinkedBlockingQueue 构造 IAE）
                new Rejection("queueSize=0", () -> DefaultThreadPoolExecutor.newExecutor("t-c4-003", 2, 0)),
                new Rejection("queueSize=-5", () -> DefaultThreadPoolExecutor.newExecutor("t-c4-003", 2, -5)),
                // -1,-1 路径封死（缺陷面 JavaScriptService 旧传参形态）
                new Rejection("poolSize=-1,queueSize=-1",
                        () -> DefaultThreadPoolExecutor.newExecutor("t-c4-003", -1, -1)),
                // 配置重载 Standard 形态
                new Rejection("config.core<0", () -> {
                    ThreadPoolConfig c = new ThreadPoolConfig();
                    c.setName("t-c4-003");
                    c.setCorePoolSize(-1);
                    c.setMaxPoolSize(4);
                    DefaultThreadPoolExecutor.newExecutor(c);
                }),
                new Rejection("config.max=0", () -> {
                    ThreadPoolConfig c = new ThreadPoolConfig();
                    c.setName("t-c4-003");
                    c.setCorePoolSize(0);
                    c.setMaxPoolSize(0);
                    DefaultThreadPoolExecutor.newExecutor(c);
                }),
                new Rejection("config.core>max", () -> {
                    ThreadPoolConfig c = new ThreadPoolConfig();
                    c.setName("t-c4-003");
                    c.setCorePoolSize(5);
                    c.setMaxPoolSize(3);
                    DefaultThreadPoolExecutor.newExecutor(c);
                }),
                new Rejection("config.queueCapacity=0", () -> {
                    ThreadPoolConfig c = new ThreadPoolConfig();
                    c.setName("t-c4-003");
                    c.setCorePoolSize(2);
                    c.setMaxPoolSize(2);
                    c.setQueueCapacity(0);
                    DefaultThreadPoolExecutor.newExecutor(c);
                }),
                // scheduled 形态：core>=1（STPE core=0 永不执行任务=静默死配置）
                new Rejection("scheduled.core=0", () -> {
                    ThreadPoolConfig c = new ThreadPoolConfig();
                    c.setName("t-c4-003");
                    c.setCorePoolSize(0);
                    ExecutorHelper.newScheduledExecutor(c);
                }),
                new Rejection("scheduled.core=-3", () -> {
                    ThreadPoolConfig c = new ThreadPoolConfig();
                    c.setName("t-c4-003");
                    c.setCorePoolSize(-3);
                    ExecutorHelper.newScheduledExecutor(c);
                }),
        };

        for (Rejection rejection : rejections) {
            NopException ex = assertThrows(NopException.class, rejection.call()::run,
                    "factory entry must fail-fast: " + rejection.label());
            assertEquals(ERR_INVALID_PARAM, ex.getErrorCode(),
                    "rejection must be typed, not bare IAE or silent cached: " + rejection.label());
            assertEquals("t-c4-003", ex.getParams().get("name"), rejection.label());
        }

        // 合法形态对照：0-core 弹性池（Standard 形态 core=0/max>0 合法）、显式 cached 形态、显式无界队列合法
        DefaultThreadPoolExecutor elastic = DefaultThreadPoolExecutor.newExecutor(
                configOf("t-c4-003-elastic", 0, 4, 10));
        try {
            assertEquals(0, elastic.stats().getCorePoolSize());
            assertEquals(4, elastic.stats().getMaxPoolSize());
        } finally {
            elastic.destroy();
        }

        DefaultThreadPoolExecutor cached = DefaultThreadPoolExecutor.newExecutor(
                configOf("t-c4-003-cached", 0, -1, 10));
        try {
            assertEquals(0, cached.stats().getCorePoolSize());
            assertEquals(CFG_CONCURRENT_CACHED_POOL_MAX_POOL_SIZE.get().intValue(), cached.stats().getMaxPoolSize());
        } finally {
            cached.destroy();
        }

        DefaultThreadPoolExecutor unboundedQueue = DefaultThreadPoolExecutor.newExecutor(
                configOf("t-c4-003-unbounded", 2, 2, Integer.MAX_VALUE));
        try {
            assertNotNull(unboundedQueue);
        } finally {
            unboundedQueue.destroy();
        }

        // 队列容量传导钉：queueSize/queueCapacity → 有界 TaskQueue（缺陷面 ExportDbTool/ImportDbTool
        // 的 MAX_VALUE→10000 修复所依赖的背压语义）
        ThreadPoolExecutor bounded = ExecutorHelper.newThreadPoolExecutor(configOf("t-c4-003-queue", 2, 2, 100));
        try {
            assertEquals(100, bounded.getQueue().remainingCapacity());
        } finally {
            bounded.shutdown();
        }
    }

    /**
     * 工单 2.1-C4-004（裁定④）：cached 形态池获得构造期上界（nop.commons.concurrent.cached-pool.maxPoolSize，
     * 默认 200），达界提交获得显式拒绝信号（REE 携带 cached-pool-saturated 错误键 + 池名 + 上界）——
     * 熔断信号在进程濒死之前可见。
     */
    @Test
    public void testCachedFormBoundedWithSaturationSignal_C4_004() throws Exception {
        String boundKey = CFG_CONCURRENT_CACHED_POOL_MAX_POOL_SIZE.getName();
        CountDownLatch release = new CountDownLatch(1);
        try {
            assignConfig(boundKey, 3);
            DefaultThreadPoolExecutor executor = DefaultThreadPoolExecutor.newExecutor(
                    configOf("t-c4-004", 0, -1, 0));
            try {
                assertEquals(3, executor.stats().getMaxPoolSize(), "cached bound must come from config key");

                CountDownLatch running = new CountDownLatch(3);
                for (int i = 0; i < 3; i++) {
                    executor.execute(() -> {
                        running.countDown();
                        try {
                            release.await(10, TimeUnit.SECONDS);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    });
                }
                assertTrue(running.await(5, TimeUnit.SECONDS), "3 threads must be occupied");

                // 第 4 个并发提交达界：显式 REE（Abort 语义），消息携带注册错误键与上界
                RejectedExecutionException ex = assertThrows(RejectedExecutionException.class,
                        () -> executor.execute(() -> {
                        }));
                assertTrue(ex.getMessage().contains(ERR_CACHED_SATURATED),
                        "saturation signal must carry the registered error key: " + ex.getMessage());
                assertTrue(ex.getMessage().contains("maxPoolSize=3"),
                        "saturation signal must carry the bound: " + ex.getMessage());
            } finally {
                executor.destroy();
            }
        } finally {
            release.countDown();
            clearConfig(boundKey);
        }
    }

    /**
     * 工单 2.1-C4-006（裁定⑤ TPE 侧）：updateThreadPool 收缩重排——真缩容先降 core 再降 max
     * （修复缩容必炸裸 IAE），缩容后池仍可执行任务；扩容先升 max 再对齐 core（放大路径不预抬，
     * 无创建-销毁抖动——抖动只存在于 setter 间瞬态，公开 stats 不可观测，此处以最终态一致为断言口径）；
     * core>max 热更输入钳制 + 不抛异常。
     */
    @Test
    public void testShrinkHotUpdateReordersCoreBeforeMax_C4_006() throws Exception {
        DefaultThreadPoolExecutor executor = DefaultThreadPoolExecutor.newExecutor(configOf("t-c4-006", 8, 8, 10));
        try {
            // 真缩容 8/8 → 2/4：旧实现先 setMaximumPoolSize(4) 必炸裸 IAE（此时 core 仍为 8）
            executor.setConfig(configOf("t-c4-006", 2, 4, 10));
            executor.refreshConfig();
            assertEquals(2, executor.stats().getCorePoolSize());
            assertEquals(4, executor.stats().getMaxPoolSize());

            // 缩容后池仍可执行任务（修复前缩容必炸 IAE 的行为对照）
            CompletableFuture<Integer> done = executor.submit(() -> 42);
            assertEquals(42, done.get(5, TimeUnit.SECONDS));

            // 扩容 2/4 → 6/10：单调生效
            executor.setConfig(configOf("t-c4-006", 6, 10, 10));
            executor.refreshConfig();
            assertEquals(6, executor.stats().getCorePoolSize());
            assertEquals(10, executor.stats().getMaxPoolSize());

            // core>max 热更输入：钳制到 max（不中断热更派发链）
            executor.setConfig(configOf("t-c4-006", 12, 5, 10));
            executor.refreshConfig();
            assertEquals(5, executor.stats().getCorePoolSize());
            assertEquals(5, executor.stats().getMaxPoolSize());
        } finally {
            executor.destroy();
        }

        // 直接工厂层（DefaultThreadPoolExecutor 之外）同样成立：ExecutorHelper.updateThreadPool 缩容不抛
        ThreadPoolExecutor raw = ExecutorHelper.newThreadPoolExecutor(configOf("t-c4-006-raw", 8, 8, 10));
        try {
            ExecutorHelper.updateThreadPool(raw, configOf("t-c4-006-raw", 2, 4, 10));
            assertEquals(2, raw.getCorePoolSize());
            assertEquals(4, raw.getMaximumPoolSize());
        } finally {
            raw.shutdown();
        }
    }

    /**
     * 工单 2.1-C4-006（裁定⑤ scheduled 专用热更路径）：DefaultScheduledExecutor.refreshConfig 仅对齐
     * corePoolSize、不经通用 updateThreadPool、无 max 钳制（core 热增值大于惰性 max 时不得被静默钳回——
     * 复刻死配置形态即失败）；core≤0 输入抛裁定③类型化异常（STPE core=0 永不执行任务，钳制无合法目标）。
     */
    @Test
    public void testScheduledHotUpdateCoreOnlyWithoutMaxClamp_C4_006() {
        DefaultScheduledExecutor timer = DefaultScheduledExecutor.newSingleThreadTimer("t-c4-006-sched");
        try {
            // core-only 对齐：max=1（newSingleThreadTimer 设置）不钳制 core 热增值
            timer.getConfig().setCorePoolSize(25);
            timer.refreshConfig();
            assertEquals(25, timer.stats().getCorePoolSize(), "core hot-update must not be clamped by lazy max");

            // core<=0 热更输入：类型化异常（非裸 IAE、非静默永不执行）
            timer.getConfig().setCorePoolSize(0);
            NopException ex = assertThrows(NopException.class, timer::refreshConfig);
            assertEquals(ERR_INVALID_PARAM, ex.getErrorCode());
            assertEquals("t-c4-006-sched", ex.getParams().get("name"));
        } finally {
            timer.getConfig().setCorePoolSize(1);
            timer.destroy();
        }
    }

    /**
     * C10-010 成员面（容量 0/负值跨工厂语义一致性）：同一非法输入（0/负值）在全部工厂入口
     * 产生同一类型化错误码，行为可表驱动对照、不静默分叉；合法特例（Standard core=0 弹性池、
     * maxPoolSize<0 显式 cached 标记、Integer.MAX_VALUE 显式无界队列）逐一钉死。
     */
    @Test
    public void testCapacityZeroNegativeCrossFactoryConsistencyMatrix_C10_010() {
        // 值 0 / 负值 × 工厂入口矩阵：全部拒绝且同一错误码
        int[] invalidValues = {0, -1};
        for (int v : invalidValues) {
            String label = "value=" + v;

            // 入口 A：三参重载 poolSize
            NopException e1 = assertThrows(NopException.class,
                    () -> DefaultThreadPoolExecutor.newExecutor("t-c10-010", v, 10), "poolSize " + label);
            assertEquals(ERR_INVALID_PARAM, e1.getErrorCode(), "poolSize " + label);

            // 入口 A：三参重载 queueSize
            NopException e2 = assertThrows(NopException.class,
                    () -> DefaultThreadPoolExecutor.newExecutor("t-c10-010", 2, v), "queueSize " + label);
            assertEquals(ERR_INVALID_PARAM, e2.getErrorCode(), "queueSize " + label);

            // 入口 B：配置重载 Standard 形态 maxPoolSize=0 非法（-1 为合法 cached 标记，见合法特例表）
            if (v == 0) {
                ThreadPoolConfig c = configOf("t-c10-010", 0, v, 10);
                NopException e3 = assertThrows(NopException.class, () -> DefaultThreadPoolExecutor.newExecutor(c),
                        "maxPoolSize " + label);
                assertEquals(ERR_INVALID_PARAM, e3.getErrorCode(), "maxPoolSize " + label);
            }

            // 入口 B：配置重载 Standard 形态 queueCapacity（cached 形态除外——cached 分支 queueCapacity 不适用）
            ThreadPoolConfig cq = configOf("t-c10-010", 2, 2, v);
            NopException e4 = assertThrows(NopException.class, () -> DefaultThreadPoolExecutor.newExecutor(cq),
                    "queueCapacity " + label);
            assertEquals(ERR_INVALID_PARAM, e4.getErrorCode(), "queueCapacity " + label);

            // 入口 C：scheduled 形态 corePoolSize
            ThreadPoolConfig cs = configOf("t-c10-010", v, 20, 10);
            NopException e5 = assertThrows(NopException.class, () -> ExecutorHelper.newScheduledExecutor(cs),
                    "scheduled core " + label);
            assertEquals(ERR_INVALID_PARAM, e5.getErrorCode(), "scheduled core " + label);
        }

        // 合法特例表（同一非法域外的显式合法形态，跨入口语义成文）：
        // (1) Standard 形态 core=0/max>0 = 0-core 弹性池（合法）
        // (2) maxPoolSize<0 = 显式 cached 标记（合法，上界=平台配置）
        // (3) queueCapacity=Integer.MAX_VALUE = 显式无界队列（合法，创建期 WARN）
        DefaultThreadPoolExecutor elastic = DefaultThreadPoolExecutor.newExecutor(
                configOf("t-c10-010-legal", 0, 4, 10));
        try {
            assertEquals(0, elastic.stats().getCorePoolSize());
        } finally {
            elastic.destroy();
        }

        DefaultThreadPoolExecutor cached = DefaultThreadPoolExecutor.newExecutor(
                configOf("t-c10-010-legal", 0, -1, 10));
        try {
            assertEquals(CFG_CONCURRENT_CACHED_POOL_MAX_POOL_SIZE.get().intValue(), cached.stats().getMaxPoolSize());
        } finally {
            cached.destroy();
        }

        DefaultThreadPoolExecutor unbounded = DefaultThreadPoolExecutor.newExecutor(
                configOf("t-c10-010-legal", 2, 2, Integer.MAX_VALUE));
        try {
            assertEquals(2, unbounded.stats().getCorePoolSize());
        } finally {
            unbounded.destroy();
        }

        // globalTimer 容量解析与工厂校验语义一致：0/负值经解析进入构造链必被同一错误码拦截
        String coreKey = CFG_CONCURRENT_GLOBAL_TIMER_CORE_POOL_SIZE.getName();
        try {
            assignConfig(coreKey, 0);
            assertEquals(0, GlobalExecutors.resolveGlobalTimerCorePoolSize(),
                    "resolution must be config-driven; construction chain rejects it below");
            ThreadPoolConfig cs = configOf("t-c10-010-timer", 0, 20, 10);
            NopException e = assertThrows(NopException.class, () -> ExecutorHelper.newScheduledExecutor(cs));
            assertEquals(ERR_INVALID_PARAM, e.getErrorCode());
        } finally {
            clearConfig(coreKey);
        }
        assertNotEquals(0, GlobalExecutors.resolveGlobalTimerCorePoolSize(), "cleanup must restore default");
    }

    private static ThreadPoolConfig configOf(String name, int corePoolSize, int maxPoolSize, int queueCapacity) {
        ThreadPoolConfig config = new ThreadPoolConfig();
        config.setName(name);
        config.setCorePoolSize(corePoolSize);
        config.setMaxPoolSize(maxPoolSize);
        config.setQueueCapacity(queueCapacity);
        return config;
    }
}
