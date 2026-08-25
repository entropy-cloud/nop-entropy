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
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestFutureHelper {
    @Test
    public void testException() {
        CompletionStage<Void> future = FutureHelper.success("a").thenRun(() -> {
            throw new IllegalStateException("a");
        });
        AtomicReference<Object> ref = new AtomicReference<>();
        future.whenComplete((ret, err) -> {
            ref.set(err);
        });
        assertTrue(ref.get().getClass() == IllegalStateException.class);
    }

    @Test
    public void testException2() {
        CompletionStage<Void> future = CompletableFuture.completedFuture("a").thenRun(() -> {
            throw new IllegalStateException("a");
        });
        AtomicReference<Object> ref = new AtomicReference<>();
        future.whenComplete((ret, err) -> {
            ref.set(err);
        });
        assertTrue(ref.get().getClass() == CompletionException.class);
    }

    @Test
    public void testThenRunAsyncSuccess() {
        CompletableFuture<String> future = new CompletableFuture<>();
        AtomicBoolean executed = new AtomicBoolean(false);
        CompletableFuture<String> ret = FutureHelper.thenRun(future, () -> executed.set(true));
        assertFalse(executed.get(), "task should not run before completion");
        future.complete("a");
        assertTrue(executed.get(), "task must run after async success");
        assertEquals("a", ret.join());
    }

    @Test
    public void testThenRunAsyncFailure() {
        CompletableFuture<String> future = new CompletableFuture<>();
        AtomicBoolean executed = new AtomicBoolean(false);
        CompletableFuture<String> ret = FutureHelper.thenRun(future, () -> executed.set(true));
        future.completeExceptionally(new IllegalStateException("a"));
        assertTrue(executed.get(), "task must run after async failure");
    }

    @Test
    public void testThenRunSyncValue() {
        AtomicBoolean executed = new AtomicBoolean(false);
        String ret = FutureHelper.thenRun("a", () -> executed.set(true));
        assertTrue(executed.get(), "task must run immediately for sync value");
        assertEquals("a", ret);
    }

    /**
     * 回归（语义固化）：已失败的ResolvedPromise上whenComplete的action抛异常时，
     * 返回stage必须保持以原异常完成（与whenCompleteAsync一致），
     * action的异常不允许向外传播（修复前该异常被完全吞掉、无任何记录，修复后至少有error日志）。
     * 注：本断言只固化返回值契约；日志行为无法在单测中断言，故不做红验证。
     */
    @Test
    public void testWhenCompleteOnFailedPromiseActionThrows() {
        IllegalStateException origin = new IllegalStateException("origin");
        IllegalStateException fromAction = new IllegalStateException("from-action");

        ResolvedPromise<String> failed = FutureHelper.reject(origin);
        CompletionStage<String> ret = failed.whenComplete((v, e) -> {
            throw fromAction;
        });

        assertSame(failed, ret, "failed source stage must be returned unchanged");
        AtomicReference<Object> observed = new AtomicReference<>();
        ret.whenComplete((v, e) -> observed.set(e));
        assertSame(origin, observed.get(), "source exception must be preserved, action error must not propagate");
    }
}
