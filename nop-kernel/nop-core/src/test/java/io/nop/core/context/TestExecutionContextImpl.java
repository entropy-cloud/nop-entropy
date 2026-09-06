/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.context;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.exceptions.ErrorCode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestExecutionContextImpl {
    static final ErrorCode ERR_TEST_MOCK_FAILURE =
            ErrorCode.define("nop.err.core.test-mock-failure", "mock failure for test");


    @Test
    public void testCompleteExceptionallyAfterCompleteIsIgnored() {
        // 终态只能被设置一次：complete()之后再调用completeExceptionally()不应改写error，
        // 否则完成回调已经以null(成功)触发后，上下文却处于失败状态
        ExecutionContextImpl ctx = new ExecutionContextImpl();
        AtomicInteger callbackCount = new AtomicInteger();
        AtomicReference<Throwable> callbackArg = new AtomicReference<>();
        ctx.onAfterComplete(err -> {
            callbackCount.incrementAndGet();
            callbackArg.set(err);
        });

        ctx.complete();
        ctx.completeExceptionally(new NopException(ERR_TEST_MOCK_FAILURE));

        assertTrue(ctx.isDone());
        assertNull(ctx.getError());
        assertEquals(1, callbackCount.get());
        assertNull(callbackArg.get());
    }

    @Test
    public void testCompleteAfterCompleteExceptionallyIsIgnored() {
        ExecutionContextImpl ctx = new ExecutionContextImpl();
        AtomicInteger callbackCount = new AtomicInteger();
        ctx.onAfterComplete(err -> callbackCount.incrementAndGet());

        NopException err = new NopException(ERR_TEST_MOCK_FAILURE);
        ctx.completeExceptionally(err);
        ctx.complete();

        assertTrue(ctx.isDone());
        assertEquals(err, ctx.getError());
        assertEquals(1, callbackCount.get());
    }

    @Test
    public void testConcurrentCompleteAndCompleteExceptionally() throws Exception {
        // 并发complete/completeExceptionally时，afterComplete回调只能触发一次，
        // 且参数必须与最终终态一致：回调参数为null当且仅当error为null
        int rounds = 500;
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < rounds; round++) {
                ExecutionContextImpl ctx = new ExecutionContextImpl();
                AtomicInteger callbackCount = new AtomicInteger();
                List<Throwable> callbackArgs = new ArrayList<>();
                ctx.onAfterComplete(e -> {
                    callbackCount.incrementAndGet();
                    callbackArgs.add(e);
                });

                CountDownLatch latch = new CountDownLatch(1);
                Future<?> f1 = executor.submit(() -> {
                    try {
                        latch.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    ctx.complete();
                });
                Future<?> f2 = executor.submit(() -> {
                    try {
                        latch.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    ctx.completeExceptionally(new NopException(ERR_TEST_MOCK_FAILURE));
                });
                latch.countDown();
                // 落败的complete()在fireBeforeComplete中读到已设置的error会按既有语义抛出异常，这里忽略
                try {
                    f1.get(10, TimeUnit.SECONDS);
                } catch (ExecutionException expectedLoser) {
                    // 落败方抛出属预期，断言在下方对 callback 次数/参数做
                }
                try {
                    f2.get(10, TimeUnit.SECONDS);
                } catch (ExecutionException expectedLoser) {
                    // 同上：并发落败方的异常不进入断言
                }

                assertEquals(1, callbackCount.get());
                assertEquals(callbackArgs.get(0) == null, ctx.getError() == null);
            }
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }
}
