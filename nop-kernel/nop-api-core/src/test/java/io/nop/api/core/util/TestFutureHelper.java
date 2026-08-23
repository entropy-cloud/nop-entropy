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
}
