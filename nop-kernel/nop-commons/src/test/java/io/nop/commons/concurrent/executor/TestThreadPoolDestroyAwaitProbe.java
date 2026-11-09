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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2547 R4-13（C4-002）destroy 语义载体钉（端到端，非 mock）。
 * <p>
 * 三腿：① await>0 时有界等待在飞任务完成；② await=0 时保持 shutdown() 现语义（不等）；
 * ③ init() 前 destroy 抛类型化异常而非 NPE。
 */
public class TestThreadPoolDestroyAwaitProbe {

    /** pin0402a：waitForTasksToCompleteOnShutdown=true + awaitTerminationSeconds=2 → destroy 等任务完成。 */
    @Test
    public void pin0402a_destroyWaitsForInFlightTasks() throws Exception {
        ThreadPoolConfig config = new ThreadPoolConfig();
        config.setName("probe-await");
        config.setCorePoolSize(2);
        config.setMaxPoolSize(2);
        config.setWaitForTasksToCompleteOnShutdown(true);
        config.setAwaitTerminationSeconds(2);

        DefaultThreadPoolExecutor executor = new DefaultThreadPoolExecutor();
        executor.setConfig(config);
        executor.init();

        AtomicBoolean taskCompleted = new AtomicBoolean();
        CountDownLatch started = new CountDownLatch(1);
        executor.submit(() -> {
            started.countDown();
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            taskCompleted.set(true);
            return null;
        });

        assertTrue(started.await(5, TimeUnit.SECONDS), "任务必须已开始执行");
        executor.destroy();

        assertTrue(taskCompleted.get(),
                "destroy 返回时在飞任务必须已完成（有界等待生效）");
    }

    /** pin0402b：awaitTerminationSeconds=0 → 保持 shutdown() 现语义（不等待/不中断）。 */
    @Test
    public void pin0402b_zeroAwaitKeepsShutdownSemantics() throws Exception {
        ThreadPoolConfig config = new ThreadPoolConfig();
        config.setName("probe-noawait");
        config.setCorePoolSize(2);
        config.setMaxPoolSize(2);
        config.setWaitForTasksToCompleteOnShutdown(true);
        config.setAwaitTerminationSeconds(0);

        DefaultThreadPoolExecutor executor = new DefaultThreadPoolExecutor();
        executor.setConfig(config);
        executor.init();

        AtomicBoolean interrupted = new AtomicBoolean();
        CountDownLatch started = new CountDownLatch(1);
        executor.submit(() -> {
            started.countDown();
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                interrupted.set(true);
                Thread.currentThread().interrupt();
            }
            return null;
        });

        assertTrue(started.await(5, TimeUnit.SECONDS));
        long t0 = System.currentTimeMillis();
        executor.destroy();
        long elapsed = System.currentTimeMillis() - t0;

        assertTrue(elapsed < 250, "await=0 时 destroy 不得等待，实际耗时=" + elapsed + "ms");
        assertFalse(interrupted.get(), "await=0 时不得 shutdownNow 中断在飞任务（保持现语义）");
    }

    /** pin0402c：init 前 destroy 抛类型化异常而非裸 NPE。 */
    @Test
    public void pin0402c_destroyBeforeInitFailsTyped() {
        ThreadPoolConfig config = new ThreadPoolConfig();
        config.setName("probe-broken");
        DefaultThreadPoolExecutor executor = new DefaultThreadPoolExecutor();
        executor.setConfig(config);

        NopException e = assertThrows(NopException.class, executor::destroy);
        assertEquals("nop.err.commons.concurrent.thread-pool-not-initialized", e.getErrorCode());
    }

    /** pin6001：C6-001（plan 2548）排队任务在 destroy 后必须异常终态，不悬挂。 */
    @Test
    public void pin6001_destroySettlesQueuedFutures() throws Exception {
        DefaultScheduledExecutor timer = DefaultScheduledExecutor.newSingleThreadTimer("probe-dse-6001");
        try {
            java.util.concurrent.atomic.AtomicInteger executions = new java.util.concurrent.atomic.AtomicInteger();
            // 60s 延迟任务必然仍在队列（worker 不可能提前取出）
            java.util.concurrent.CompletableFuture<Object> future = timer.schedule(
                    () -> {
                        executions.incrementAndGet();
                        return "result";
                    }, 60_000L, TimeUnit.MILLISECONDS);

            assertFalse(future.isDone(), "排队任务在 destroy 前不应有终态");
            timer.destroy();

            assertTrue(future.isDone(), "缺陷态：destroy 后 future 永不完成（调用方悬挂）");
            assertTrue(future.isCompletedExceptionally(), "destroy 后 future 应异常终态");
            java.util.concurrent.ExecutionException ex =
                    assertThrows(java.util.concurrent.ExecutionException.class, future::get);
            assertTrue(ex.getCause() instanceof NopException,
                    "cause 应类型化为 NopException(ERR_SERVICE_NOT_ACTIVE)，实际 " + ex.getCause());
            assertEquals(0, executions.get(), "排队任务不应被执行");
        } finally {
            timer.destroy();
        }
    }

    /** pin6001b：对照组——DefaultThreadPoolExecutor.destroy() 走 shutdown 排空，future 正常完成。 */
    @Test
    public void pin6001b_defaultThreadPoolExecutorDrainsQueue() throws Exception {
        DefaultThreadPoolExecutor executor = new DefaultThreadPoolExecutor();
        executor.init();

        // 先占满 worker，再排队任务，随后 destroy（shutdown 排空队列）
        CountDownLatch blocker = new CountDownLatch(1);
        CountDownLatch blockerStarted = new CountDownLatch(1);
        executor.execute(() -> {
            blockerStarted.countDown();
            try {
                blocker.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        blockerStarted.await(5, TimeUnit.SECONDS);

        java.util.concurrent.CompletableFuture<String> queued = executor.submit(
                (java.util.concurrent.Callable<String>) () -> "drained");
        executor.destroy();
        blocker.countDown();

        assertTrue(queued.isDone(), "排空语义：排队任务应被执行并正常完结");
        assertFalse(queued.isCompletedExceptionally(), "对照组不应异常终态");
        assertEquals("drained", queued.get(2, TimeUnit.SECONDS));
    }

    /** pin6001c：C6-001（plan 2548）BindScheduledExecutor 外层 future 在底层 timer destroy 后异常终态。 */
    @Test
    public void pin6001c_bindExecutorPropagatesDestroyFailure() throws Exception {
        DefaultScheduledExecutor timer = DefaultScheduledExecutor.newSingleThreadTimer("probe-bind-6001");
        BindScheduledExecutor bind = new BindScheduledExecutor(timer, Runnable::run);
        try {
            java.util.concurrent.CompletableFuture<String> future = bind.schedule(
                    () -> "never", 60_000L, TimeUnit.MILLISECONDS);

            assertFalse(future.isDone());
            timer.destroy();

            assertTrue(future.isDone(), "缺陷态：外层 future 在底层 timer destroy 后永不完成");
            assertTrue(future.isCompletedExceptionally());
            java.util.concurrent.ExecutionException ex =
                    assertThrows(java.util.concurrent.ExecutionException.class, future::get);
            assertTrue(ex.getCause() instanceof NopException,
                    "外层 cause 也应类型化，实际 " + ex.getCause());
        } finally {
            timer.destroy();
        }
    }

    /** pin6001d：C6-001（plan 2548）在飞任务契约——destroy 后 future 异常终态，随后成功的结果被丢弃。 */
    @Test
    public void pin6001d_inFlightFutureSettledExceptionallyOnDestroy() throws Exception {
        DefaultScheduledExecutor timer = DefaultScheduledExecutor.newSingleThreadTimer("probe-dse-6001d");
        CountDownLatch taskStarted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try {
            java.util.concurrent.CompletableFuture<String> future = timer.submit(() -> {
                taskStarted.countDown();
                release.await(5, TimeUnit.SECONDS);
                return "late-result";
            });

            taskStarted.await(5, TimeUnit.SECONDS);
            timer.destroy();
            release.countDown();

            assertTrue(future.isDone());
            assertTrue(future.isCompletedExceptionally(),
                    "在飞任务的 future 也应在 destroy 时异常终态（结果丢弃契约）");
            java.util.concurrent.ExecutionException ex =
                    assertThrows(java.util.concurrent.ExecutionException.class, future::get);
            assertTrue(ex.getCause() instanceof NopException);
        } finally {
            release.countDown();
            timer.destroy();
        }
    }

}
