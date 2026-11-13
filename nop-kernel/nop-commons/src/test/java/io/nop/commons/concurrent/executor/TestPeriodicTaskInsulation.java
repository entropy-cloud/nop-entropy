/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.concurrent.executor;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 周期任务异常绝缘层的语义回归（设计契约：
 * {@code ai-dev/design/concurrency/periodic-task-insulation.md}）。
 * 五类核心语义各占具名测试：任务体 Throwable 捕获 + future 异常终态、重臂（含 Error）、
 * 确定性失败不失控（退避/抑制/升级）、isDone 守卫三相容、观测钩子；另含 cancel/关闭竞态
 * 与 GlobalExecutors 真实载体接线验证。
 */
public class TestPeriodicTaskInsulation {
    static class RecordingListener implements IPeriodicTaskListener {
        final AtomicInteger successes = new AtomicInteger();
        final List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());
        final List<Long> failureStreaks = Collections.synchronizedList(new ArrayList<>());
        final List<Long> escalations = Collections.synchronizedList(new ArrayList<>());
        final List<Throwable> rejections = Collections.synchronizedList(new ArrayList<>());

        @Override
        public void onRunSuccess() {
            successes.incrementAndGet();
        }

        @Override
        public void onRunFailure(Throwable exception, long consecutiveFailures) {
            failures.add(exception);
            failureStreaks.add(consecutiveFailures);
        }

        @Override
        public void onFailureStreakExceeded(long consecutiveFailures) {
            escalations.add(consecutiveFailures);
        }

        @Override
        public void onChainRejected(Throwable exception) {
            rejections.add(exception);
        }
    }

    private static boolean await(Supplier<Boolean> cond, long timeoutMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline && !cond.get())
            Thread.sleep(5);
        return cond.get();
    }

    private static DefaultScheduledExecutor newTimer(String name) {
        return DefaultScheduledExecutor.newSingleThreadTimer(name);
    }

    private static CompletableFuture<?> asCompletable(Future<?> chain) {
        assertTrue(chain instanceof CompletableFuture, "insulated chain must be a CompletableFuture");
        return (CompletableFuture<?>) chain;
    }

    private static InsulatedPeriodicChain asChain(Future<?> chain) {
        assertTrue(chain instanceof InsulatedPeriodicChain, "insulated chain must be a InsulatedPeriodicChain");
        return (InsulatedPeriodicChain) chain;
    }

    /**
     * 语义①：任务体抛 RuntimeException 被设施捕获，返回 future completeExceptionally（终态传播），
     * 且链重臂继续运行，cancel 后终止。
     */
    @Test
    public void testTaskBodyThrowableIsCaughtAndCompletesFutureExceptionally() throws Exception {
        DefaultScheduledExecutor timer = newTimer("t-insulation-1");
        try {
            AtomicInteger runs = new AtomicInteger();
            RecordingListener listener = new RecordingListener();
            Future<?> chain = timer.scheduleWithFixedDelay(() -> {
                if (runs.incrementAndGet() == 1)
                    throw new IllegalStateException("boom-first-round");
            }, 0, 20, TimeUnit.MILLISECONDS, "task-1", listener);

            assertTrue(await(chain::isDone, 5000), "future must complete exceptionally after first failure");
            assertFalse(chain.isCancelled());
            assertTrue(asCompletable(chain).isCompletedExceptionally());

            assertTrue(await(() -> runs.get() >= 2, 5000), "chain must re-arm after failure");
            assertEquals(1, listener.failures.size());
            assertInstanceOf(IllegalStateException.class, listener.failures.get(0));

            chain.cancel(false);
            // 失败已占用 future 终态：cancel 对失败完结的链仍必须生效（stopRequested），链停止
            assertTrue(asChain(chain).isStopped(), "cancel must stop the chain even after failure completion");
            int atCancel = runs.get();
            Thread.sleep(150);
            assertTrue(runs.get() <= atCancel + 1, "no new rounds after cancel: " + runs.get());
        } finally {
            timer.destroy();
        }
    }

    /**
     * 语义②：任务体抛 Error 后链不被取消（重臂可观测），future 按失败终态结算。
     */
    @Test
    public void testChainRearmsAfterErrorTaskBody() throws Exception {
        DefaultScheduledExecutor timer = newTimer("t-insulation-2");
        try {
            AtomicInteger runs = new AtomicInteger();
            Future<?> chain = timer.scheduleWithFixedDelay(() -> {
                if (runs.incrementAndGet() == 1)
                    throw new LinkageError("deterministic-error");
            }, 0, 20, TimeUnit.MILLISECONDS, "task-2", null);

            assertTrue(await(() -> runs.get() >= 2, 5000), "chain must re-arm after Error");
            assertFalse(chain.isCancelled());
            assertTrue(chain.isDone());
            assertTrue(asCompletable(chain).isCompletedExceptionally());

            chain.cancel(false);
            assertTrue(asChain(chain).isStopped());
        } finally {
            timer.destroy();
        }
    }

    /**
     * 语义④（isDone 守卫三相容，载体级回归）：BindScheduledExecutor 是 C3-015 记录的杀链主证载体——
     * 旧实现「future 异常完结 → isDone 守卫退出 → 链死亡」；新契约下 future 异常完结但链必须继续。
     */
    @Test
    public void testBindCarrierFailureTerminalStateDoesNotKillChain() throws Exception {
        DefaultScheduledExecutor timer = newTimer("t-insulation-3");
        try {
            BindScheduledExecutor bind = new BindScheduledExecutor(timer, Runnable::run);
            AtomicInteger runs = new AtomicInteger();
            RecordingListener listener = new RecordingListener();
            Future<?> chain = bind.scheduleWithFixedDelay(() -> {
                if (runs.incrementAndGet() == 1)
                    throw new LinkageError("bind-carrier-error");
            }, 0, 20, TimeUnit.MILLISECONDS, "task-3", listener);

            assertTrue(await(chain::isDone, 5000));
            assertTrue(asCompletable(chain).isCompletedExceptionally());
            assertTrue(await(() -> runs.get() >= 2, 5000),
                    "Bind carrier chain must survive first failure (old isDone-guard would kill it)");
            assertTrue(await(() -> listener.successes.get() >= 1, 5000), "recovered round must be observed");

            chain.cancel(false);
            assertTrue(asChain(chain).isStopped());
            int atCancel = runs.get();
            Thread.sleep(150);
            assertTrue(runs.get() <= atCancel + 1, "no new rounds after cancel: " + runs.get());
        } finally {
            timer.destroy();
        }
    }

    /**
     * 语义③（链级）：任务体确定性失败时重臂按退避下限（1s）推迟，不形成紧失败循环，链不被取消。
     */
    @Test
    public void testDeterministicFailureRearmsWithBackoffWithoutRunaway() throws Exception {
        DefaultScheduledExecutor timer = newTimer("t-insulation-4");
        try {
            AtomicInteger runs = new AtomicInteger();
            Future<?> chain = timer.scheduleWithFixedDelay(() -> {
                runs.incrementAndGet();
                throw new IllegalStateException("always-fail");
            }, 0, 10, TimeUnit.MILLISECONDS, "task-4", null);

            // 失败后重臂下限 1s：300ms 窗口内只应有首轮（至多加一次竞态在飞轮次）
            Thread.sleep(300);
            int count = runs.get();
            assertTrue(count >= 1, "first round must run");
            assertTrue(count <= 2, "backoff must prevent failure storm, count=" + count);
            assertFalse(chain.isCancelled(), "deterministic failure must not cancel the chain");

            chain.cancel(false);
        } finally {
            timer.destroy();
        }
    }

    /**
     * 语义③（策略级）：连续失败计数驱动的升级阈值、日志抑制谓词、退避序列与恢复清零。
     */
    @Test
    public void testFailureGovernanceEscalationSuppressionBackoffAndRecovery() {
        InsulatedPeriodicChain chain = new InsulatedPeriodicChain();
        RecordingListener listener = new RecordingListener();
        AtomicBoolean failing = new AtomicBoolean(true);
        InsulatedPeriodicTask guard = new InsulatedPeriodicTask("guard-test", () -> {
            if (failing.get())
                throw new IllegalStateException("governance-probe");
        }, listener, chain);

        // 25 次确定性失败：Throwable 不逃逸、失败事件逐次上报、升级只在 10/20 触发
        for (int i = 0; i < 25; i++)
            assertFalse(guard.runBody(), "runBody must never let Throwable escape");
        assertEquals(25, listener.failures.size());
        assertEquals(List.of(10L, 20L), listener.escalations);

        // 日志抑制谓词：第 1 次失败 + 每第 10 次连续失败记录，中间抑制
        assertTrue(InsulatedPeriodicTask.shouldLogFailure(1));
        for (long streak = 2; streak <= 9; streak++)
            assertFalse(InsulatedPeriodicTask.shouldLogFailure(streak), "streak=" + streak);
        assertTrue(InsulatedPeriodicTask.shouldLogFailure(10));
        assertFalse(InsulatedPeriodicTask.shouldLogFailure(15));
        assertTrue(InsulatedPeriodicTask.shouldLogFailure(20));

        // 退避序列：base=10ms 失败后 1000→2000→4000→8000→16000→32000→60000（封顶）
        long[] expected = {1000, 2000, 4000, 8000, 16000, 32000, 60000, 60000};
        for (int fails = 1; fails <= expected.length; fails++) {
            InsulatedPeriodicChain c2 = new InsulatedPeriodicChain();
            InsulatedPeriodicTask g2 = new InsulatedPeriodicTask("backoff-probe", () -> {
                throw new IllegalStateException("backoff-probe");
            }, null, c2);
            for (int i = 0; i < fails; i++)
                g2.runBody();
            assertEquals(expected[fails - 1], g2.nextRearmDelayMillis(10), "streak=" + fails);
        }

        // 恢复：成功一轮清零失败计数、恢复原周期、onRunSuccess 可观测
        failing.set(false);
        assertTrue(guard.runBody());
        assertEquals(1, listener.successes.get());
        assertEquals(10, guard.nextRearmDelayMillis(10));
    }

    /**
     * 语义⑤：观测钩子触发——失败/恢复回调按序可达，timer 销毁（关闭竞态）触发拒绝回调与终态。
     */
    @Test
    public void testObservationHooksFireForSuccessFailureAndRejection() throws Exception {
        DefaultScheduledExecutor timer = newTimer("t-insulation-6");
        try {
            // 失败/恢复钩子（失败后 future 结算，链继续重臂）
            AtomicInteger runs = new AtomicInteger();
            RecordingListener failingListener = new RecordingListener();
            Future<?> failingChain = timer.scheduleWithFixedDelay(() -> {
                if (runs.incrementAndGet() == 1)
                    throw new IllegalStateException("hook-check");
            }, 0, 20, TimeUnit.MILLISECONDS, "task-6a", failingListener);

            assertTrue(await(() -> !failingListener.failures.isEmpty(), 5000));
            assertInstanceOf(IllegalStateException.class, failingListener.failures.get(0));
            assertEquals(1L, failingListener.failureStreaks.get(0));
            assertTrue(await(() -> failingListener.successes.get() >= 1, 5000),
                    "recovery round must fire onRunSuccess");
            // 修订（2026-11-14）：恢复可达性即契约（语义⑤=失败/恢复回调按序可达）。
            // 链以 20ms 周期持续重臂直至销毁，销毁前窗口内的后续成功轮次属调度时序
            // 而非契约面——原 assertEquals(1,...) 精确计数在满载下偶发读到 2（时序工件）。
            // 恢复「先失败后成功」的顺序契约仍由 failures[0] 与 successes 先后断言承载。
            assertTrue(await(() -> failingListener.successes.get() >= 2, 5000),
                    "periodic chain keeps re-arming after recovery (sanity)");

            // 拒绝钩子：健康链在 timer 销毁时收到 onChainRejected 且 future 有终态（不悬挂）
            RecordingListener healthyListener = new RecordingListener();
            Future<?> healthyChain = timer.scheduleWithFixedDelay(() -> {
            }, 0, 30, TimeUnit.MILLISECONDS, "task-6b", healthyListener);

            timer.destroy();
            assertTrue(await(healthyChain::isDone, 2000),
                    "timer destroy must give the healthy chain a terminal state");
            assertTrue(asCompletable(healthyChain).isCompletedExceptionally());
            assertEquals(1, healthyListener.rejections.size());
        } finally {
            timer.destroy();
        }
    }

    /**
     * cancel/stop 语义：在飞轮次运行中 cancel，在飞轮次自然执行完毕，后续轮次不再触发。
     */
    @Test
    public void testCancelStopsChainAfterInFlightRoundFinishes() throws Exception {
        DefaultScheduledExecutor timer = newTimer("t-insulation-7");
        try {
            CountDownLatch started = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            AtomicInteger runs = new AtomicInteger();
            Future<?> chain = timer.scheduleWithFixedDelay(() -> {
                runs.incrementAndGet();
                started.countDown();
                try {
                    release.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, 0, 10, TimeUnit.MILLISECONDS, "task-7", null);

            assertTrue(started.await(2, TimeUnit.SECONDS), "first round must start");
            chain.cancel(false);
            assertTrue(chain.isCancelled());

            release.countDown(); // 让在飞轮次自然结束
            assertTrue(await(() -> runs.get() == 1, 2000));
            Thread.sleep(50);
            assertEquals(1, runs.get(), "no further rounds after cancel");
        } finally {
            timer.destroy();
        }
    }

    /**
     * 关闭竞态：timer destroy 后链 future 必须获得终态（不悬挂）且链不复活。
     */
    @Test
    public void testTimerDestroyGivesTerminalStateWithoutResurrection() throws Exception {
        DefaultScheduledExecutor timer = newTimer("t-insulation-8");
        try {
            AtomicInteger runs = new AtomicInteger();
            Future<?> chain = timer.scheduleWithFixedDelay(runs::incrementAndGet, 0, 30, TimeUnit.MILLISECONDS,
                    "task-8", null);

            assertTrue(await(() -> runs.get() >= 1, 2000));
            timer.destroy();

            assertTrue(await(chain::isDone, 2000), "destroy must terminate the chain future (no hang)");
            assertTrue(asCompletable(chain).isCompletedExceptionally());
            int atDestroy = runs.get();
            Thread.sleep(120);
            assertTrue(runs.get() <= atDestroy, "chain must not resurrect after destroy: " + runs.get());
        } finally {
            timer.destroy();
        }
    }

    /**
     * rule 23 接线验证：GlobalExecutors.globalTimer() + executeOn（BindScheduledExecutor 载体，
     * 即 retry scanner / cluster registration 等平台消费方的真实拓扑）在运行时真实走到绝缘路径——
     * 任务体失败被守卫观测、链经真实设施重臂、future 获得失败终态。
     */
    @Test
    public void testGlobalTimerBindWiringReachesInsulatedPath() throws Exception {
        IScheduledExecutor timer = GlobalExecutors.globalTimer(); // 平台共享单例，只 cancel 自己的链，绝不 destroy
        AtomicInteger runs = new AtomicInteger();
        RecordingListener listener = new RecordingListener();

        Future<?> chain = timer.executeOn(Runnable::run).scheduleWithFixedDelay(() -> {
            if (runs.incrementAndGet() == 1)
                throw new IllegalStateException("wiring-probe");
        }, 0, 20, TimeUnit.MILLISECONDS, "wiring-test", listener);

        try {
            assertTrue(await(() -> !listener.failures.isEmpty(), 5000),
                    "insulation path must be reached through GlobalExecutors.globalTimer().executeOn(...)");
            assertInstanceOf(IllegalStateException.class, listener.failures.get(0));
            assertTrue(await(() -> runs.get() >= 2, 5000), "chain must re-arm through the real facility");
            assertTrue(chain.isDone() && !chain.isCancelled());
            assertTrue(asCompletable(chain).isCompletedExceptionally());
        } finally {
            chain.cancel(false);
        }
    }
}
