/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core.util;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * bindCancel 单点改造契约回归（CRA R1-04，plan 2499 Phase 2，裁定①/裁定⑥。
 * 设计权威来源：{@code ai-dev/design/concurrency/bindcancel-context-unification.md} §3.1/§3.6。
 *
 * <p>守卫契约：仅当完结原因为取消（异常终态为 {@link java.util.concurrent.CancellationException}）
 * 且目标未完成时才 {@code cancel(true)}——正常/失败完结零 cancel、真取消传播保持。
 * 同时收口 api-core 两个半边：completeAfterTask catch 宽度（C3-005）、
 * executeWithThrottling 幻影 release（C3-007）。
 */
public class TestFutureHelperBindCancelContract {

    /**
     * C3-016 修复前红：bindCancel 守卫不判完成原因，promise 正常完成即误取消底层 future
     * （审计给出的 {@code isCancelled()==true} 直接复现手法）。
     */
    @Test
    public void testBindCancelSuccessCompletionDoesNotCancelTarget() {
        CompletableFuture<String> promise = new CompletableFuture<>();
        CompletableFuture<String> target = new CompletableFuture<>();
        FutureHelper.bindCancel(promise, (Future<?>) target);

        promise.complete("ok");

        assertFalse(promise.isCancelled());
        assertFalse(target.isCancelled(), "success completion must not cancel the underlying future");
        assertFalse(target.isDone(), "target must stay unfinished: nothing completed it");
    }

    /**
     * C3-016 兼容矩阵钉：失败完结（异常终态非 CancellationException）不触发 cancel。
     */
    @Test
    public void testBindCancelFailureCompletionDoesNotCancelTarget() {
        CompletableFuture<String> promise = new CompletableFuture<>();
        CompletableFuture<String> target = new CompletableFuture<>();
        FutureHelper.bindCancel(promise, (Future<?>) target);

        promise.completeExceptionally(new IllegalStateException("task-failed"));

        assertTrue(promise.isCompletedExceptionally());
        assertFalse(target.isCancelled(), "failure completion must not cancel the underlying future");
        assertFalse(target.isDone());
    }

    /**
     * C3-016 兼容矩阵钉：真取消路径传播保持——promise 取消 → 底层 future 被 cancel(true)。
     */
    @Test
    public void testBindCancelTrueCancellationStillPropagates() {
        CompletableFuture<String> promise = new CompletableFuture<>();
        CompletableFuture<String> target = new CompletableFuture<>();
        FutureHelper.bindCancel(promise, (Future<?>) target);

        assertTrue(promise.cancel(false));

        assertTrue(promise.isCancelled());
        assertTrue(target.isCancelled(), "true cancellation must propagate to the underlying future");
    }

    /**
     * C3-016 兼容矩阵钉：目标已完成的场合不再重复 cancel（守卫第二半边 !isDone）。
     */
    @Test
    public void testBindCancelDoesNotRecancelFinishedTarget() {
        CompletableFuture<String> promise = new CompletableFuture<>();
        CompletableFuture<String> target = new CompletableFuture<>();
        FutureHelper.bindCancel(promise, (Future<?>) target);

        target.complete("done");
        promise.cancel(false);

        assertFalse(promise.isCancelled() && !promise.isDone());
        assertTrue(target.isDone());
        assertFalse(target.isCancelled(), "finished target must not be cancelled afterwards");
    }

    /**
     * C3-005 修复前红：任务体抛 Error 时 catch(Exception) 漏接 → promise 永不完成，
     * 调用方 syncGet 无超时挂死。修复后 promise 异常终态、syncGet 可退出。
     */
    @Test
    public void testCompleteAfterTaskErrorCompletesPromiseExceptionally() {
        CompletableFuture<Object> promise = new CompletableFuture<>();

        FutureHelper.completeAfterTask(promise, () -> {
            throw new AssertionError("boom-error-from-task-body");
        });

        assertTrue(promise.isCompletedExceptionally(), "Error in task body must settle the promise");
        ExecutionException ex = assertThrows(ExecutionException.class, () -> promise.get(1, TimeUnit.SECONDS));
        assertInstanceOf(AssertionError.class, ex.getCause());
    }

    /**
     * completeAfterTask 正常路径钉：任务体返回 CompletionStage 时绑定结果。
     */
    @Test
    public void testCompleteAfterTaskNormalCompletionStillWorks() {
        CompletableFuture<String> promise = new CompletableFuture<>();
        FutureHelper.completeAfterTask(promise, () -> CompletableFuture.completedFuture("value"));

        assertEquals("value", promise.join());
    }

    /**
     * C3-007 修复前红：acquire 被中断即未获得许可，旧实现仍补偿 release → 许可数虚增（n=1 出现 2）。
     */
    @Test
    public void testExecuteWithThrottlingInterruptedAcquireDoesNotInflatePermit() {
        Semaphore limit = new Semaphore(1);
        Thread.currentThread().interrupt();
        try {
            CompletionStage<?> ret = FutureHelper.executeWithThrottling(() -> {
                throw new IllegalStateException("must-not-run");
            }, limit);
            assertTrue(FutureHelper.isError(ret), "interrupted acquire must settle with the interrupt error");
        } finally {
            // 清除本测试注入的中断标志，避免污染后续测试
            Thread.interrupted();
        }

        assertEquals(1, limit.availablePermits(),
                "interrupted acquire must not phantom-release the permit (n=1 stays 1)");
    }

    /**
     * C3-007 语义钉：提交被拒（supplier 抛异常，许可已获得）的补偿语义保持——release 恰一次、异常传播。
     */
    @Test
    public void testExecuteWithThrottlingRejectionStillCompensatesPermit() {
        Semaphore limit = new Semaphore(1);

        assertThrows(IllegalStateException.class,
                () -> FutureHelper.executeWithThrottling(() -> {
                    throw new IllegalStateException("rejected-before-async");
                }, limit));

        assertEquals(1, limit.availablePermits(), "rejection after acquire must compensate exactly once");
    }

    /**
     * executeWithThrottling 正常路径钉：任务完成后许可归还。
     */
    @Test
    public void testExecuteWithThrottlingNormalPathReleasesPermit() {
        Semaphore limit = new Semaphore(1);
        AtomicBoolean ran = new AtomicBoolean(false);

        CompletionStage<String> ret = FutureHelper.executeWithThrottling(() -> {
            ran.set(true);
            return CompletableFuture.completedFuture("ok");
        }, limit);

        assertEquals("ok", FutureHelper.syncGet(ret));
        assertTrue(ran.get());
        assertEquals(1, limit.availablePermits(), "permit must be returned after completion");
    }

    /**
     * 裁定⑥ ICancelToken 平台契约：bindCancelToken 注册前已取消的 token 立即取消目标 future（补触发）。
     */
    @Test
    public void testBindCancelTokenFiresImmediatelyForAlreadyCancelledToken() {
        CancellableToken token = new CancellableToken();
        token.cancel("already-cancelled");

        CompletableFuture<String> future = new CompletableFuture<>();
        FutureHelper.bindCancelToken(token, future);

        assertTrue(future.isCancelled(),
                "already-cancelled token must cancel the target future immediately (no registration blind window)");
    }

    /**
     * 裁定⑥ bindCancelToken cleanup 重载：注册前已取消 → 立即以 cancelReason 调 cleanup（对称补触发）。
     */
    @Test
    public void testBindCancelTokenCleanupOverloadFiresImmediatelyForAlreadyCancelledToken() {
        CancellableToken token = new CancellableToken();
        token.cancel("cleanup-reason");

        AtomicBoolean cleaned = new AtomicBoolean(false);
        CompletableFuture<String> promise = new CompletableFuture<>();
        FutureHelper.bindCancelToken(token, reason -> {
            if ("cleanup-reason".equals(reason))
                cleaned.set(true);
        }, promise);

        assertTrue(cleaned.get(), "cleanup must fire immediately for already-cancelled token");
        assertFalse(promise.isDone(), "cleanup overload must not settle the promise");
    }

    /**
     * 裁定⑥ bindCancelToken 正常路径钉：token 取消传播到 future（监听器注册→触发→终态反注册闭环）。
     */
    @Test
    public void testBindCancelTokenPropagatesCancellationAndUnregisters() {
        CancellableToken token = new CancellableToken();
        CompletableFuture<String> future = new CompletableFuture<>();
        FutureHelper.bindCancelToken(token, future);

        assertFalse(future.isDone());
        token.cancel("later-cancel");
        assertTrue(future.isCancelled(), "token cancellation must propagate to the bound future");
    }

    /**
     * 测试用 ICancelToken 实现（SPI 面：直接实现 ICancelToken 的最小形态，语义与
     * nop-commons Cancellable 基类一致——appendOnCancel 对已取消 token 立即补发）。
     */
    static class CancellableToken implements ICancelToken {
        private final java.util.List<java.util.function.Consumer<String>> listeners = new java.util.ArrayList<>();
        private volatile boolean cancelled;
        private volatile String reason;

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public String getCancelReason() {
            return reason;
        }

        @Override
        public synchronized void appendOnCancel(java.util.function.Consumer<String> task) {
            if (task == null)
                return;
            listeners.add(task);
            if (cancelled)
                task.accept(reason);
        }

        @Override
        public synchronized void removeOnCancel(java.util.function.Consumer<String> task) {
            listeners.remove(task);
        }

        void cancel(String reason) {
            synchronized (this) {
                if (cancelled)
                    return;
                this.cancelled = true;
                this.reason = reason;
            }
            for (java.util.function.Consumer<String> listener : new java.util.ArrayList<>(listeners)) {
                listener.accept(reason);
            }
        }
    }
}
