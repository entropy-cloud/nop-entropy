package io.nop.config.source.file;

import io.nop.commons.util.objects.ValueWithLocation;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2497 Phase 2（2.2-C4-001）回归：AbstractFileConfigSource 刷新链卸载——
 * globalTimer 只承载调度触发（调度槽），刷新任务体（Files.walk 目录遍历 = 阻塞 I/O、时长无上界）
 * 经 {@code executeOn(globalWorker())} hop 到 globalWorker 执行。
 * 接线断言（rule 23）：真实 globalTimer 载体上，刷新轮次任务体执行线程可观测为
 * nop-global-worker 池线程、绝非 nop-global-timer 线程。
 */
public class TestFileConfigSourceWorkerOffload {

    static final String WORKER_PREFIX = "nop-global-worker";
    static final String TIMER_PREFIX = "nop-global-timer";

    /**
     * 可控装载结果的探测桩：armed 之后（构造期初始装载完成）每次装载记录执行线程。
     */
    static class OffloadProbeSource extends AbstractFileConfigSource {
        final AtomicReference<String> scheduledRefreshThread = new AtomicReference<>();
        final AtomicBoolean armed = new AtomicBoolean(false);
        final AtomicInteger loads = new AtomicInteger();
        volatile String value = "v0";

        OffloadProbeSource(long refreshIntervalMillis) {
            super(List.of("/nonexistent-test-path"), refreshIntervalMillis);
        }

        void disarmAndSetValue(String newValue) {
            this.value = newValue;
            this.armed.set(true);
        }

        @Override
        public String getName() {
            return "offload-probe";
        }

        @Override
        protected Map<String, ValueWithLocation> loadConfigFromPath(List<Path> paths) {
            if (value == null) {
                // super 构造期初始装载：子类字段尚未初始化（初始装载发生在测试主线程，非调度轮次）
                return new HashMap<>();
            }
            loads.incrementAndGet();
            if (armed.get()) {
                scheduledRefreshThread.compareAndSet(null, Thread.currentThread().getName());
            }
            Map<String, ValueWithLocation> vars = new HashMap<>();
            vars.put("k", ValueWithLocation.of(null, value));
            return vars;
        }
    }

    private static boolean await(BooleanSupplier cond, long timeoutMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            if (cond.getAsBoolean())
                return true;
            Thread.sleep(50);
        }
        return cond.getAsBoolean();
    }

    /**
     * 接线断言（rule 23）：定时刷新轮次的任务体运行在 globalWorker 池线程，不在 timer 线程上执行
     * （修复前 = nop-global-timer 线程直连执行 Files.walk 遍历体）。
     */
    @Test
    public void testScheduledRefreshBodyRunsOnWorkerThreadNotTimer_C4_001() throws Exception {
        OffloadProbeSource source = new OffloadProbeSource(100);
        try {
            // 构造期初始装载（load #1）发生在测试主线程；armed 后的装载 = 调度轮次
            source.disarmAndSetValue("v1");

            assertTrue(await(() -> source.scheduledRefreshThread.get() != null, 10_000),
                    "调度刷新轮次必须在超时前触发（globalTimer 载体已注册）");

            String threadName = source.scheduledRefreshThread.get();
            assertTrue(threadName.startsWith(WORKER_PREFIX),
                    "刷新任务体必须 hop 到 globalWorker 执行，实际线程: " + threadName);
            assertFalse(threadName.startsWith(TIMER_PREFIX),
                    "刷新任务体不得运行在 globalTimer 线程（阻塞 I/O 占死调度槽），实际线程: " + threadName);
        } finally {
            source.close();
        }
    }

    /**
     * 端到端链路（rule 22）：构造 → 注册 globalTimer 载体 → 调度刷新轮次 → worker 池执行任务体 →
     * 变更快照回写 → 变更回调触发。全链在真实 globalTimer/globalWorker 单例上闭合。
     */
    @Test
    public void testEndToEndScheduleToWorkerToOnChangeCallback_C4_001() throws Exception {
        OffloadProbeSource source = new OffloadProbeSource(100);
        try {
            AtomicInteger callbackCount = new AtomicInteger();
            AtomicReference<String> callbackThread = new AtomicReference<>();
            source.addOnChange(() -> {
                callbackThread.compareAndSet(null, Thread.currentThread().getName());
                callbackCount.incrementAndGet();
            });

            // 构造期初始值为 v0；armed 后置 v1 → 首个调度轮次 isChanged 成立 → 回调触发
            source.disarmAndSetValue("v1");

            assertTrue(await(() -> callbackCount.get() > 0, 10_000),
                    "配置变更必须在调度刷新轮次后触发变更回调（端到端链路闭合）");

            String threadName = callbackThread.get();
            assertTrue(threadName.startsWith(WORKER_PREFIX),
                    "变更回调随刷新任务体在 globalWorker 上执行，实际线程: " + threadName);
            assertFalse(threadName.startsWith(TIMER_PREFIX),
                    "变更回调不得运行在 globalTimer 线程，实际线程: " + threadName);
        } finally {
            source.close();
        }
    }
}
