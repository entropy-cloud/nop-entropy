/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core.context;

import io.nop.api.core.exceptions.NopException;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestContextProviderThenOnContext {

    /**
     * context已关闭后源future才完成时，thenOnContext0返回的promise必须以异常完成，
     * 而不是静默挂起（异常被丢弃进whenComplete返回的无人消费的依赖stage）。
     */
    @Test
    public void testThenOnContext0ClosedContextCompletesExceptionally() throws Exception {
        BaseContext context = new BaseContext();
        context.close();

        CompletableFuture<String> future = new CompletableFuture<>();
        CompletionStage<String> promise = ContextProvider.thenOnContext0(future, context);

        // 源future在context关闭之后完成
        future.complete("value");

        ExecutionException ex = assertThrows(ExecutionException.class,
                () -> promise.toCompletableFuture().get(2, TimeUnit.SECONDS),
                "promise must complete exceptionally when context is closed");
        assertTrue(ex.getCause() instanceof NopException,
                "cause should be NopException(ERR_CONTEXT_ALREADY_CLOSED) but was " + ex.getCause());
    }

    @Test
    public void testThenOnContext0ClosedContextImmediateComplete() throws Exception {
        BaseContext context = new BaseContext();
        context.close();

        // 源future已完成时同样不能挂起
        CompletionStage<String> promise = ContextProvider.thenOnContext0(
                CompletableFuture.completedFuture("value"), context);

        ExecutionException ex = assertThrows(ExecutionException.class,
                () -> promise.toCompletableFuture().get(2, TimeUnit.SECONDS));
        assertTrue(ex.getCause() instanceof NopException);
    }
}
