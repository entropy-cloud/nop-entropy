package io.nop.job.core;

import io.nop.commons.concurrent.executor.DefaultScheduledExecutor;
import io.nop.commons.concurrent.executor.IScheduledExecutor;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 2570 C-3 回归钉：扫描器存活判定只认设施级拒绝（listener.onChainRejected），
 * 单轮任务体失败是失败信号——链重臂、running 保持 true、扫描业务不停摆。
 * 修复前：whenComplete 非 Cancellation 异常判定把单轮失败误读为链拒绝，
 * running=false 后 doScan 直接 return——6 个 job 扫描器业务静默停摆 + 僵尸链持续重臂。
 *
 * <p>载体：测试专用的独立 {@link DefaultScheduledExecutor}（覆写 protected getExecutor()）。
 * 禁用 globalTimer 单例（destroy 会污染共享池）；MockScheduledExecutor 位于 nop-autotest-core，
 * nop-job-core 测试依赖方向不可达。
 */
public class TestScannerChainRejectionR2570 {

    private static boolean await(Supplier<Boolean> cond, long timeoutMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline && !cond.get())
            Thread.sleep(20);
        return cond.get();
    }

    private static DefaultScheduledExecutor newTimer(String name) {
        return DefaultScheduledExecutor.newSingleThreadTimer(name);
    }

    /**
     * 探针 scanner：任务体异常经「onCycleStart 失败 → onScanCycleFailed rethrow」路径逃逸
     * scanOnce 的 2496 绝缘边界。注意：常规 scanBatch 失败被 scanBatches 内层 catch（→onScanFailed）
     * 截获，永远不会到达绝缘层守卫；只有外层 catch 链（onCycleStart 等）且 onScanCycleFailed
     * 自身抛出，才会作为任务体 Throwable 逃逸到守卫——这是「单轮失败抵达链」的真实触发面。
     */
    static class FirstRoundFailureScanner extends AbstractBatchScanner {
        int cycles = 0;

        @Override
        protected IScheduledExecutor getExecutor() {
            // 测试自持 executor，由测试负责 destroy（钉 2 复用 destroy 作为确定性拒绝触发源）
            return sharedTestTimer.executeOn(Runnable::run);
        }

        @Override
        protected void onCycleStart() {
            if (++cycles == 1)
                throw new IllegalStateException("boom-first-cycle");
        }

        @Override
        protected void onScanCycleFailed(Throwable e) {
            // 模拟子类失败钩子自身抛出——异常逃逸 scanOnce → doScan → 绝缘层守卫
            throw new IllegalStateException("hook-rethrow", e);
        }

        @Override
        protected boolean scanBatch() {
            return false;
        }
    }

    static DefaultScheduledExecutor sharedTestTimer;

    /**
     * 钉 1（先红后绿）：单轮 scanBatch 抛异常后，下一轮仍执行（scanCycleCount 继续增长），
     * running 保持 true——失败信号不停摆扫描业务。
     * 修复前：首轮失败即 running=false，doScan 此后直接 return，cycleCount 不再增长。
     */
    @Test
    public void testSingleRoundFailureDoesNotStallScanning() throws Exception {
        sharedTestTimer = newTimer("t-scanner-2570a");
        try {
            FirstRoundFailureScanner scanner = new FirstRoundFailureScanner();
            scanner.scanIntervalMs = 50;
            scanner.startScanning();

            assertTrue(await(() -> scanner.getScanCycleCount() >= 1, 5000),
                    "first cycle must run");
            assertTrue(await(() -> scanner.getScanCycleCount() >= 2, 10000),
                    "chain must re-arm and run a second cycle after the failed one");
            assertTrue(scanner.running, "failure signal must not flip running to false");
            assertEquals(0, scanner.getScanChainRejectedCount(),
                    "a task-body failure must not be counted as chain rejection");
        } finally {
            sharedTestTimer.destroy();
        }
    }

    /**
     * 钉 2：设施级拒绝（destroy 触发统一拒绝路径）时 running=false、scanChainRejectedCount 增长——
     * 拒绝停摆语义不回退。
     */
    @Test
    public void testChainRejectionStopsScanning() throws Exception {
        sharedTestTimer = newTimer("t-scanner-2570b");
        FirstRoundFailureScanner scanner = new FirstRoundFailureScanner();
        try {
            scanner.scanIntervalMs = 50;
            scanner.startScanning();
            assertTrue(await(() -> scanner.getScanCycleCount() >= 1, 5000), "scanner must run");
            assertFalse(scanner.getScanChainRejectedCount() > 0, "no rejection before destroy");
        } finally {
            // destroy 触发绝缘层统一拒绝路径 → onChainRejected → running=false
            sharedTestTimer.destroy();
        }
        assertTrue(await(() -> !scanner.running, 5000),
                "facility-level rejection must flip running to false");
        assertTrue(scanner.getScanChainRejectedCount() >= 1,
                "chain rejection must be counted");
    }
}
