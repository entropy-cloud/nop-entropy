/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.concurrent.executor;

import org.junit.jupiter.api.Test;

import io.nop.commons.concurrent.thread.NamedThreadFactory;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ExecutorHelper 包装层取消契约回归（CRA R1-04，plan 2499 Phase 2，裁定① nop-commons 本体半边。
 * 设计权威来源：{@code ai-dev/design/concurrency/bindcancel-context-unification.md} §3.1）。
 *
 * <p>C3-016：submit×2/schedule 三处包装 Callable 在 complete 之后清除残留中断标志，
 * 成功/失败完结不再对底层 FutureTask 注入假取消；真取消传播保持（兼容矩阵钉）。
 * C3-005：throttleExecute 双层 catch(Throwable)——任务体 Error 结算 future 而非悬挂。
 */
public class TestExecutorHelperCancelContract {

    /**
     * 记录底层 FutureTask 的单线程池：ExecutorHelper.submit 内部经 executor.submit(Callable) 提交，
     * 捕获返回的原始 Future 以断言其终态（TPE 载体的 getTask 会吸收中断标志，中断残留
     * 对下一任务不可观测——审计载明该依赖，故以底层 FutureTask 终态为判别面）。
     */
    static class RecordingPool extends ThreadPoolExecutor {
        volatile Future<?> lastRaw;

        RecordingPool(String name) {
            super(1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(10),
                    new NamedThreadFactory(name, true));
        }

        @Override
        public <T> Future<T> submit(Callable<T> task) {
            Future<T> future = super.submit(task);
            this.lastRaw = future;
            return future;
        }
    }

    /** 记录底层 ScheduledFuture 的单线程 timer（schedule 载体同构）。 */
    static class RecordingTimer extends ScheduledThreadPoolExecutor {
        volatile Future<?> lastRaw;

        RecordingTimer(String name) {
            super(1, new NamedThreadFactory(name, true));
        }

        @Override
        public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
            ScheduledFuture<V> future = super.schedule(callable, delay, unit);
            this.lastRaw = future;
            return future;
        }
    }

    /**
     * C3-016 修复前红：submit 成功完结路径必然触发 bindCancel 误取消——底层 FutureTask 以
     * cancelled 假终态收场并向 worker 注入中断；队列中已就绪的下一个任务观测到中断残留
     * （队列非空时 getTask 不经 park，不吸收中断标志）。
     */
    @Test
    public void testSubmitSuccessDoesNotCancelUnderlyingFutureTask() throws Exception {
        RecordingPool pool = new RecordingPool("test-c316-submit");
        try {
            CountDownLatch t1Started = new CountDownLatch(1);
            CountDownLatch releaseT1 = new CountDownLatch(1);
            CountDownLatch t2Done = new CountDownLatch(1);
            AtomicBoolean t2SawInterrupt = new AtomicBoolean(true);

            CompletableFuture<Void> f1 = ExecutorHelper.submit(pool, () -> {
                t1Started.countDown();
                releaseT1.await(5, TimeUnit.SECONDS);
                return null;
            });
            CompletableFuture<Void> f2 = ExecutorHelper.submit(pool, () -> {
                t2SawInterrupt.set(Thread.currentThread().isInterrupted());
                t2Done.countDown();
                return null;
            });

            assertTrue(t1Started.await(5, TimeUnit.SECONDS), "first task must start");
            Thread.sleep(100);
            releaseT1.countDown();

            assertTrue(f1.get(5, TimeUnit.SECONDS) == null);
            assertTrue(t2Done.await(5, TimeUnit.SECONDS), "second task must run");
            assertTrue(f2.get(5, TimeUnit.SECONDS) == null);

            // 判别面（审计手法）：修复前成功完结路径必然误取消底层 FutureTask（isCancelled()==true）
            Future<?> raw = pool.lastRaw;
            assertTrue(raw.isDone());
            assertFalse(raw.isCancelled(),
                    "success completion must not leave the underlying FutureTask in cancelled state");
            // 绿态断言：成功路径零中断注入，接续任务无中断残留
            assertFalse(t2SawInterrupt.get(),
                    "success completion must not leave interrupt residue on the worker thread");
        } finally {
            pool.shutdownNow();
        }
    }

    /**
     * C3-016 修复前红：schedule 载体同构——成功完结路径的中断残留泄漏给同一 worker 的下一个任务。
     */
    @Test
    public void testScheduleSuccessDoesNotCancelUnderlyingFutureTask() throws Exception {
        RecordingTimer timer = new RecordingTimer("test-c316-schedule");
        try {
            CountDownLatch t1Started = new CountDownLatch(1);
            CountDownLatch releaseT1 = new CountDownLatch(1);
            CountDownLatch t2Done = new CountDownLatch(1);
            AtomicBoolean t2SawInterrupt = new AtomicBoolean(true);

            CompletableFuture<Void> f1 = ExecutorHelper.schedule(timer, () -> {
                t1Started.countDown();
                releaseT1.await(5, TimeUnit.SECONDS);
                return null;
            }, 0, TimeUnit.MILLISECONDS);
            CompletableFuture<Void> f2 = ExecutorHelper.schedule(timer, () -> {
                t2SawInterrupt.set(Thread.currentThread().isInterrupted());
                t2Done.countDown();
                return null;
            }, 0, TimeUnit.MILLISECONDS);

            assertTrue(t1Started.await(5, TimeUnit.SECONDS), "first task must start");
            Thread.sleep(100);
            releaseT1.countDown();

            assertTrue(f1.get(5, TimeUnit.SECONDS) == null);
            assertTrue(t2Done.await(5, TimeUnit.SECONDS), "second task must run");
            assertTrue(f2.get(5, TimeUnit.SECONDS) == null);

            // 判别面：schedule 载体同构——修复前成功完结误取消底层 ScheduledFuture
            Future<?> raw = timer.lastRaw;
            assertTrue(raw.isDone());
            assertFalse(raw.isCancelled(),
                    "success completion must not leave the underlying ScheduledFuture in cancelled state");
            // 绿态断言：schedule 载体零中断注入
            assertFalse(t2SawInterrupt.get(),
                    "success completion must not leave interrupt residue on the timer worker thread");
        } finally {
            timer.shutdownNow();
        }
    }

    /**
     * 兼容矩阵钉：真取消路径传播保持——取消包装 future 后在飞任务被中断（cancel(true) 语义），
     * 任务体收到 InterruptedException 且包装 future 以取消终态收场。
     */
    @Test
    public void testSubmitTrueCancellationStillInterruptsRunningTask() throws Exception {
        ThreadPoolExecutor pool = ExecutorHelper.newSingleThreadExecutor("test-c316-cancel", 10, true);
        try {
            CountDownLatch t1Started = new CountDownLatch(1);
            CountDownLatch taskInterrupted = new CountDownLatch(1);

            CompletableFuture<Void> f1 = ExecutorHelper.submit(pool, () -> {
                t1Started.countDown();
                try {
                    new CountDownLatch(5).await();
                } catch (InterruptedException e) {
                    taskInterrupted.countDown();
                    Thread.currentThread().interrupt();
                }
                return null;
            });

            assertTrue(t1Started.await(5, TimeUnit.SECONDS), "task must start before cancel");
            assertTrue(f1.cancel(true), "cancel of the wrapper future must succeed");

            assertTrue(taskInterrupted.await(5, TimeUnit.SECONDS),
                    "true cancellation must interrupt the running task");
            assertTrue(f1.isCancelled());
        } finally {
            pool.shutdownNow();
        }
    }

    /**
     * 兼容矩阵钉：排队中取消从队列移除待执行任务（cancel 生效于 FutureTask NEW 状态）。
     */
    @Test
    public void testSubmitCancelFromQueuePreventsExecution() throws Exception {
        ThreadPoolExecutor pool = ExecutorHelper.newSingleThreadExecutor("test-c316-queue-cancel", 10, true);
        try {
            CountDownLatch blockerDone = new CountDownLatch(1);
            AtomicBoolean ran = new AtomicBoolean(false);

            CompletableFuture<Void> blocker = ExecutorHelper.submit(pool, () -> {
                blockerDone.await(5, TimeUnit.SECONDS);
                return null;
            });
            CompletableFuture<Void> queued = ExecutorHelper.submit(pool, () -> {
                ran.set(true);
                return null;
            });

            assertTrue(queued.cancel(true), "queued wrapper future must be cancellable");
            blockerDone.countDown();
            assertTrue(blocker.get(5, TimeUnit.SECONDS) == null);

            Thread.sleep(100);
            assertFalse(ran.get(), "cancelled queued task must never execute");
            assertTrue(queued.isCancelled());
        } finally {
            pool.shutdownNow();
        }
    }

    /**
     * C3-005 修复前红：任务体抛 Error 时 catch(Exception) 漏接 → future 永不完成（调用方
     * syncGet 无超时挂死）。修复后 Error 结算 future 异常终态、许可照常归还。
     */
    @Test
    public void testThrottleExecuteTaskBodyErrorCompletesFutureExceptionally() throws Exception {
        Semaphore semaphore = new Semaphore(1);

        CompletableFuture<Void> future = ExecutorHelper.throttleExecute(Runnable::run, semaphore, () -> {
            throw new AssertionError("boom-error-from-throttled-task");
        });

        assertTrue(future.isCompletedExceptionally(), "Error in task body must settle the future");
        ExecutionException ex = assertThrows(ExecutionException.class, () -> future.get(1, TimeUnit.SECONDS));
        assertInstanceOf(AssertionError.class, ex.getCause());
        assertEquals(1, semaphore.availablePermits(), "permit must be released even on Error");
    }

    /**
     * C3-005 正常路径钉：throttleExecute 成功完结 + 许可归还。
     */
    @Test
    public void testThrottleExecuteNormalPathStillWorks() throws Exception {
        Semaphore semaphore = new Semaphore(1);
        AtomicBoolean ran = new AtomicBoolean(false);

        CompletableFuture<Void> future = ExecutorHelper.throttleExecute(Runnable::run, semaphore,
                () -> ran.set(true));

        assertTrue(future.get(5, TimeUnit.SECONDS) == null);
        assertTrue(ran.get());
        assertEquals(1, semaphore.availablePermits());
    }

    /**
     * C3-005 executor 拒绝路径钉：execute 被拒时许可补偿 + future 异常终态（补偿语义保持）。
     */
    @Test
    public void testThrottleExecuteRejectionCompensatesPermit() {
        Semaphore semaphore = new Semaphore(1);
        java.util.concurrent.Executor rejectingExecutor = task -> {
            throw new RejectedExecutionException("pool-rejected");
        };

        CompletableFuture<Void> future = ExecutorHelper.throttleExecute(rejectingExecutor, semaphore,
                () -> {
                });

        assertTrue(future.isCompletedExceptionally(), "rejection must settle the future exceptionally");
        assertThrows(ExecutionException.class, () -> future.get(1, TimeUnit.SECONDS));
        assertEquals(1, semaphore.availablePermits(), "rejection must compensate the permit exactly once");
    }
}
