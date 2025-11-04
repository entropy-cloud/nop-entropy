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
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestFutureHelper {
    @Test
    public void testException() {
        CompletionStage<Void> future = FutureHelper.success("a").thenRun(() -> {
            throw new RuntimeException("a");
        });
        AtomicReference<Object> ref = new AtomicReference<>();
        future.whenComplete((ret, err) -> {
            ref.set(err);
        });
        assertTrue(ref.get().getClass() == RuntimeException.class);
    }

    @Test
    public void testException2() {
        CompletionStage<Void> future = CompletableFuture.completedFuture("a").thenRun(() -> {
            throw new RuntimeException("a");
        });
        AtomicReference<Object> ref = new AtomicReference<>();
        future.whenComplete((ret, err) -> {
            ref.set(err);
        });
        assertTrue(ref.get().getClass() == CompletionException.class);
    }
}
