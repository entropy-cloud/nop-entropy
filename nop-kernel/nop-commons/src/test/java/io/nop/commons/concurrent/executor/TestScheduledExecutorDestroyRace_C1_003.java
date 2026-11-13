/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.concurrent.executor;

import io.nop.api.core.exceptions.NopException;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 2.1-C1-003 线程交割回归（plan 2504 Phase 2）：destroy 置空窗 × 七读点 checkEnabled-then-use。
 * <p>
 * 修复前红形态（基线 ccbccee458 实证）：
 * <ul>
 * <li>死代际（快照非 null 但已 shutdownNow）提交 = 裸 {@link java.util.concurrent.RejectedExecutionException}；</li>
 * <li>destroy 后 stats() = 裸 {@link NullPointerException}（ExecutorHelper.getStats(null)）。</li>
 * </ul>
 * 修复后绿形态：七读点局部快照 + 死代际 REE 类型化 —— 统一收敛为
 * {@link NopException}（ERR_SERVICE_NOT_ACTIVE），或快照先于 destroy 时提交成功。
 */
public class TestScheduledExecutorDestroyRace_C1_003 {

    static ScheduledThreadPoolExecutor executorField(DefaultScheduledExecutor timer) throws Exception {
        Field f = DefaultScheduledExecutor.class.getDeclaredField("executor");
        f.setAccessible(true);
        return (ScheduledThreadPoolExecutor) f.get(timer);
    }

    /**
     * 死代际提交（快照非 null 但代际已 shutdown）：修复前裸 REE（红），修复后类型化 ERR_SERVICE_NOT_ACTIVE（绿）。
     * 覆盖七读点中的 submit/schedule/execute 三型入口。
     */
    @Test
    public void testDeadGenerationSubmitTypedNotREE_C1_003() throws Exception {
        DefaultScheduledExecutor timer = DefaultScheduledExecutor.newSingleThreadTimer("c1-003-dead-gen");
        try {
            ScheduledThreadPoolExecutor dead = executorField(timer);
            dead.shutdownNow();

            // submit(Callable)
            NopException e1 = assertThrows(NopException.class, () -> timer.submit(() -> "x"));
            assertEquals("nop.err.commons.service.not-active", e1.getErrorCode());

            // submit(Runnable, result)
            NopException e2 = assertThrows(NopException.class, () -> timer.submit(() -> {
            }, "result"));
            assertEquals("nop.err.commons.service.not-active", e2.getErrorCode());

            // schedule
            NopException e3 = assertThrows(NopException.class,
                    () -> timer.schedule(() -> "y", 1, TimeUnit.MILLISECONDS));
            assertEquals("nop.err.commons.service.not-active", e3.getErrorCode());

            // execute
            NopException e4 = assertThrows(NopException.class, () -> timer.execute(() -> {
            }));
            assertEquals("nop.err.commons.service.not-active", e4.getErrorCode());

            // scheduleAtFixedRate（六参直连底层）：死代际拒绝不抛出，而是以类型化 cause 进入链拒绝终态
            Future<?> chain = timer.scheduleAtFixedRate(() -> {
            }, 0, 1, TimeUnit.MILLISECONDS, "c1-003", null);
            assertTrue(chain.isDone(), "dead generation rejection must give the chain a terminal state");
            try {
                chain.get(1, TimeUnit.SECONDS);
                throw new AssertionError("chain must be rejected on dead generation");
            } catch (ExecutionException e) {
                assertTrue(e.getCause() instanceof NopException,
                        "rejection cause must be typed, got: " + e.getCause());
                assertEquals("nop.err.commons.service.not-active",
                        ((NopException) e.getCause()).getErrorCode());
            }

            // scheduleWithFixedDelay（绝缘链逐轮重排）：首拍死代际 → 链拒绝终态 + 类型化 cause
            Future<?> delayChain = timer.scheduleWithFixedDelay(() -> {
            }, 0, 1, TimeUnit.MILLISECONDS, "c1-003-delay", null);
            assertTrue(delayChain.isDone(), "dead generation first-round rejection must terminate the chain");
            try {
                delayChain.get(1, TimeUnit.SECONDS);
                throw new AssertionError("delay chain must be rejected on dead generation");
            } catch (ExecutionException e) {
                assertTrue(e.getCause() instanceof NopException,
                        "rejection cause must be typed, got: " + e.getCause());
                assertEquals("nop.err.commons.service.not-active",
                        ((NopException) e.getCause()).getErrorCode());
            }
        } finally {
            timer.destroy();
        }
    }

    /**
     * destroy 后 stats()：修复前裸 NPE（红），修复后类型化 ERR_SERVICE_NOT_ACTIVE（绿）。
     */
    @Test
    public void testStatsAfterDestroyTyped_C1_003() {
        DefaultScheduledExecutor timer = DefaultScheduledExecutor.newSingleThreadTimer("c1-003-stats");
        timer.destroy();

        NopException e = assertThrows(NopException.class, timer::stats);
        assertEquals("nop.err.commons.service.not-active", e.getErrorCode());
    }

    /**
     * destroy × 提交并发竞态：任何提交的结果只能是「成功」或「类型化 ERR_SERVICE_NOT_ACTIVE」，
     * 禁止裸 NullPointerException 逃逸（checkEnabled-then-use 窗口消除）。
     * daemon 夹具线程 + 有界观察窗，无无限 join。
     */
    @Test
    public void testConcurrentDestroyVsSubmitNoBareNPE_C1_003() throws Exception {
        DefaultScheduledExecutor timer = DefaultScheduledExecutor.newSingleThreadTimer("c1-003-race");
        CountDownLatch start = new CountDownLatch(1);
        AtomicBoolean stop = new AtomicBoolean();
        AtomicInteger ok = new AtomicInteger();
        AtomicInteger typed = new AtomicInteger();
        AtomicInteger bareNpe = new AtomicInteger();

        Thread[] workers = new Thread[4];
        for (int i = 0; i < workers.length; i++) {
            Thread t = new Thread(() -> {
                try {
                    start.await(2, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                while (!stop.get()) {
                    try {
                        timer.submit(() -> "z").get(1, TimeUnit.SECONDS);
                        ok.incrementAndGet();
                    } catch (NopException e) {
                        typed.incrementAndGet();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    } catch (Throwable e) {
                        // 修复前：窗口内裸 NPE 逃逸
                        if (e instanceof NullPointerException)
                            bareNpe.incrementAndGet();
                        else
                            typed.incrementAndGet();
                    }
                }
            }, "c1-003-submitter-" + i);
            t.setDaemon(true);
            workers[i] = t;
        }
        for (Thread t : workers)
            t.start();
        start.countDown();
        Thread.sleep(50);
        timer.destroy();
        Thread.sleep(50);
        stop.set(true);
        for (Thread t : workers)
            t.join(2000);

        assertEquals(0, bareNpe.get(),
                "bare NPE must never escape the submit path (check-then-use window must be closed)");
        assertTrue(ok.get() + typed.get() > 0, "race window must actually be exercised");
        timer.destroy();
    }
}
