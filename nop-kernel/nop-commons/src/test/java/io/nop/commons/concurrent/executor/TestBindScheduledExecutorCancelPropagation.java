/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.concurrent.executor;

import org.junit.jupiter.api.Test;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BindScheduledExecutor 取消传播接入回归（CRA R1-04，plan 2499 Phase 2，裁定② C3-004。
 * 设计权威来源：{@code ai-dev/design/concurrency/bindcancel-context-unification.md} §3.2。
 * 接入后置于裁定① C3-016 修复（git 序列：本提交晚于 3f76e2a5b2 的 bindCancel 守卫修复）。
 *
 * <p>契约：schedule/submit 返回 future 的取消传播到底层 timer 任务（bindCancel 接线）；
 * executeOn 在向 worker 提交前检查包装 future 的取消/完成状态，已取消的提交直接短路
 * （排队窗口取消的兜底守卫，不替代 bindCancel 传播）。
 */
public class TestBindScheduledExecutorCancelPropagation {

    /** 受控 timer：捕获提交体但不运行，供确定性构造「取消已发生、timer 体后触发」的交错。 */
    static class CapturingTimer implements IScheduledExecutor {
        Runnable pendingRunnable;
        Callable<?> pendingCallable;
        long pendingDelayMillis = -1;

        @Override
        public <V> CompletableFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
            this.pendingCallable = callable;
            this.pendingDelayMillis = unit.toMillis(delay);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public Future<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
            throw new UnsupportedOperationException("not used in this test");
        }

        @Override
        public Future<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
            throw new UnsupportedOperationException("not used in this test");
        }

        @Override
        public <V> CompletableFuture<V> submit(Callable<V> callable) {
            this.pendingCallable = callable;
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public <V> CompletableFuture<V> submit(Runnable task, V result) {
            this.pendingRunnable = task;
            return CompletableFuture.completedFuture(result);
        }

        @Override
        public void execute(Runnable command) {
            command.run();
        }

        void runPending() throws Exception {
            if (pendingRunnable != null) {
                Runnable run = pendingRunnable;
                pendingRunnable = null;
                run.run();
                return;
            }
            Callable<?> call = pendingCallable;
            pendingCallable = null;
            if (call != null)
                call.call();
        }

        @Override
        public void destroy() {
        }

        @Override
        public void refreshConfig() {
        }

        @Override
        public String getName() {
            return "capturing-timer";
        }

        @Override
        public ThreadPoolConfig getConfig() {
            ThreadPoolConfig config = new ThreadPoolConfig();
            config.setName(getName());
            return config;
        }

        @Override
        public ThreadPoolStats stats() {
            return new ThreadPoolStats();
        }
    }

    /** 记录是否向 worker 投递的 Executor。 */
    static class RecordingWorker implements Executor {
        final AtomicBoolean dispatched = new AtomicBoolean(false);
        final AtomicBoolean bodyRan = new AtomicBoolean(false);

        @Override
        public void execute(Runnable command) {
            dispatched.set(true);
            command.run();
        }
    }

    /**
     * C3-004 修复前红：submit 返回 future 的取消不传播——timer 体照常触发并向 worker 投递、
     * 任务体完整执行（不可撤销副作用在取消后照常发生）。
     */
    @Test
    public void testSubmitCancellationPropagatesToTimerTask() throws Exception {
        CapturingTimer timer = new CapturingTimer();
        RecordingWorker worker = new RecordingWorker();
        BindScheduledExecutor bind = new BindScheduledExecutor(timer, worker);

        AtomicBoolean bodyRan = new AtomicBoolean(false);
        CompletableFuture<String> future = bind.submit(() -> {
            bodyRan.set(true);
            return "done";
        });
        // timer 体尚未触发时取消
        assertTrue(future.cancel(true));
        // timer 体后触发：修复后 executeOn 守卫短路，不再投递 worker、任务体不执行
        timer.runPending();

        assertTrue(future.isCancelled());
        assertFalse(worker.dispatched.get(),
                "cancelled submit must not dispatch to the worker (executeOn isDone guard)");
        assertFalse(bodyRan.get(), "cancelled task body must never execute");
    }

    /**
     * C3-004 修复前红：schedule 返回 future 的取消不传播到底层 timer 调度——延迟任务照常触发。
     */
    @Test
    public void testScheduleCancellationPropagatesToTimerTask() throws Exception {
        CapturingTimer timer = new CapturingTimer();
        RecordingWorker worker = new RecordingWorker();
        BindScheduledExecutor bind = new BindScheduledExecutor(timer, worker);

        AtomicBoolean bodyRan = new AtomicBoolean(false);
        CompletableFuture<String> future = bind.schedule(() -> {
            bodyRan.set(true);
            return "done";
        }, 100, TimeUnit.MILLISECONDS);

        assertTrue(future.cancel(true));
        assertEquals(100, timer.pendingDelayMillis, "schedule must delegate with the requested delay");
        timer.runPending();

        assertTrue(future.isCancelled());
        assertFalse(worker.dispatched.get(),
                "cancelled schedule must not dispatch to the worker");
        assertFalse(bodyRan.get(), "cancelled delayed task body must never execute");
    }

    /**
     * 正常路径钉：未取消时 submit/schedule 照常投递 worker、任务体执行、future 正常结算。
     */
    @Test
    public void testSubmitAndScheduleNormalPathStillWork() throws Exception {
        CapturingTimer timer = new CapturingTimer();
        RecordingWorker worker = new RecordingWorker();
        BindScheduledExecutor bind = new BindScheduledExecutor(timer, worker);

        CompletableFuture<String> submitted = bind.submit(() -> "submit-ok");
        timer.runPending();
        assertEquals("submit-ok", submitted.get(5, TimeUnit.SECONDS));
        assertTrue(worker.dispatched.get());

        RecordingWorker worker2 = new RecordingWorker();
        BindScheduledExecutor bind2 = new BindScheduledExecutor(timer, worker2);
        CompletableFuture<String> scheduled = bind2.schedule(() -> "schedule-ok", 0, TimeUnit.MILLISECONDS);
        timer.runPending();
        assertEquals("schedule-ok", scheduled.get(5, TimeUnit.SECONDS));
        assertTrue(worker2.dispatched.get());
    }

    /**
     * executeOn 守卫钉：future 已完结（非取消）时同样短路——不投递 worker。
     */
    @Test
    public void testExecuteOnGuardSkipsDispatchForDoneFuture() throws Exception {
        CapturingTimer timer = new CapturingTimer();
        RecordingWorker worker = new RecordingWorker();
        BindScheduledExecutor bind = new BindScheduledExecutor(timer, worker);

        CompletableFuture<String> future = bind.submit(() -> "unused");
        // 模拟「排队窗口内已完结」：直接以取消终态完结包装 future（终态已由 future 承载）
        future.cancel(false);
        timer.runPending();

        assertFalse(worker.dispatched.get(), "done future must not be dispatched to the worker");
    }

    /**
     * 真实构造链接线验证（rule 23）：DefaultScheduledExecutor 载体上，schedule 延迟触发前取消，
     * 任务体不再执行（bindCancel 传播经 ExecutorHelper 包装层全链生效）。
     */
    @Test
    public void testRealTimerChainCancelBeforeFirePreventsExecution() throws Exception {
        DefaultScheduledExecutor timer = DefaultScheduledExecutor.newSingleThreadTimer("test-c304-real");
        Executor worker = Runnable::run;
        try {
            BindScheduledExecutor bind = new BindScheduledExecutor(timer, worker);
            CountDownLatch bodyRan = new CountDownLatch(1);

            CompletableFuture<String> future = bind.schedule(() -> {
                bodyRan.countDown();
                return "ok";
            }, 300, TimeUnit.MILLISECONDS);
            assertTrue(future.cancel(true), "cancel before timer fire must succeed");

            assertFalse(bodyRan.await(700, TimeUnit.MILLISECONDS),
                    "cancelled delayed task must never execute on the real timer chain");
            assertTrue(future.isCancelled());
        } finally {
            timer.destroy();
        }
    }

    /**
     * 真实构造链接线验证（rule 23）：不取消时延迟任务正常执行（传播接线不破坏正常调度）。
     */
    @Test
    public void testRealTimerChainNormalScheduleStillFires() throws Exception {
        DefaultScheduledExecutor timer = DefaultScheduledExecutor.newSingleThreadTimer("test-c304-real2");
        Executor worker = Runnable::run;
        try {
            BindScheduledExecutor bind = new BindScheduledExecutor(timer, worker);
            CountDownLatch bodyRan = new CountDownLatch(1);

            CompletableFuture<String> future = bind.schedule(() -> {
                bodyRan.countDown();
                return "ok";
            }, 10, TimeUnit.MILLISECONDS);

            assertTrue(bodyRan.await(5, TimeUnit.SECONDS),
                    "uncancelled delayed task must execute on the real timer chain");
            assertEquals("ok", future.get(5, TimeUnit.SECONDS));
        } finally {
            timer.destroy();
        }
    }

    /**
     * submit(Runnable, V) 委托面同覆盖：取消后任务体不执行。
     */
    @Test
    public void testRunnableSubmitDelegationCoveredByCancelGuard() throws Exception {
        CapturingTimer timer = new CapturingTimer();
        RecordingWorker worker = new RecordingWorker();
        BindScheduledExecutor bind = new BindScheduledExecutor(timer, worker);

        AtomicBoolean ran = new AtomicBoolean(false);
        CompletableFuture<String> future = bind.submit(() -> ran.set(true), "result");
        assertTrue(future.cancel(true));
        timer.runPending();

        assertFalse(ran.get(), "cancelled runnable submission must never execute");
        assertTrue(future.isCancelled());
    }

    /**
     * 拒绝路径钉：worker 提交被拒时 future 异常终态（executeOn 兜底语义保持）。
     */
    @Test
    public void testWorkerRejectionCompletesFutureExceptionally() throws Exception {
        CapturingTimer timer = new CapturingTimer();
        Executor rejectingWorker = task -> {
            throw new IllegalStateException("worker-rejected");
        };
        BindScheduledExecutor bind = new BindScheduledExecutor(timer, rejectingWorker);

        CompletableFuture<String> future = bind.submit(() -> "unused");
        timer.runPending();

        assertTrue(future.isCompletedExceptionally());
        java.util.concurrent.ExecutionException ex = assertThrows(java.util.concurrent.ExecutionException.class,
                () -> future.get(1, TimeUnit.SECONDS));
        assertInstanceOf(IllegalStateException.class, ex.getCause());
    }
}
