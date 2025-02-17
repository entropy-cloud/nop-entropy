/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.util.retry;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.FutureHelper;
import io.nop.commons.concurrent.executor.IScheduledExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class RetryHelper {
    static final Logger LOG = LoggerFactory.getLogger(RetryHelper.class);

    public static <T, C> T retryCall(Callable<T> task, IRetryPolicy<C> retryPolicy, C context) {
        int retryTimes = 0;
        do {
            try {
                return task.call();
            } catch (Throwable e) {
                long delay = retryPolicy.getRetryDelay(e, retryTimes, context);
                if (delay < 0)
                    throw NopException.adapt(e);
                if (delay > 0) {
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException ex) { //NOPMD - suppressed EmptyCatchBlock
                        // ignore
                        Thread.currentThread().interrupt();
                        throw NopException.adapt(e);
                    }
                }
                retryTimes++;
            }
        } while (true);
    }

    public static <T, C> CompletableFuture<T> retryExecute(Callable<?> task, IRetryPolicy<C> retryPolicy, IScheduledExecutor executor,
                                                           C context) {
        CompletableFuture<T> promise = new CompletableFuture<>();
        _retryExecute(promise, task, retryPolicy, executor, 0, 0, context);
        return promise;
    }

    static <T, C> void _retryExecute(CompletableFuture<T> promise, Callable<?> task, IRetryPolicy<C> retryPolicy, IScheduledExecutor executor,
                                     long delay, int retryTimes, C context) {
        executor.schedule(() -> {
            FutureHelper.futureCall(task).whenComplete((ret, ex) -> {
                if (ex != null) {
                    long nextDelay = retryPolicy.getRetryDelay(ex, retryTimes, context);
                    if (nextDelay >= 0) {
                        _retryExecute(promise, task, retryPolicy, executor, nextDelay, retryTimes + 1, context);
                        return;
                    }
                    promise.completeExceptionally(ex);
                } else {
                    promise.complete((T) ret);
                }
            });
            return null;
        }, delay, TimeUnit.MILLISECONDS);
    }

    public static <T> CompletionStage<T> retryNTimes(Supplier<CompletionStage<T>> task,
                                                     Predicate<T> checkReady, int n) {
        if (n <= 0)
            return task.get();
        return task.get().thenCompose(ret -> {
            if (checkReady.test(ret))
                return FutureHelper.success(ret);
            LOG.info("nop.retry:times={}",n);
            return retryNTimes(task, checkReady, n - 1);
        });
    }
}