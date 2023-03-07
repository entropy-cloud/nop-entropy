/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.commons.concurrent.executor;

import io.nop.api.core.util.FutureHelper;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class RateLimitExecutorImpl implements IRateLimitExecutor {
    private final IScheduledExecutor executorService;

    private final ConcurrentMap<Object, Future<?>> promiseMap = new ConcurrentHashMap<>();

    public RateLimitExecutorImpl(IScheduledExecutor executorService) {
        this.executorService = executorService;
    }

    @Override
    public void throttle(final Object key, long delay, Runnable task) {
        // 如果上次调用尚未触发，则取消上次任务
        final CompletableFuture<Void> promise = new CompletableFuture<>();
        Future<?> old = promiseMap.putIfAbsent(key, promise);
        if (old != null && !old.isDone())
            return;

        executorService.schedule(() -> {
            FutureHelper.completeAfterTask(promise, Executors.callable(task));
            return null;
        }, delay, TimeUnit.MILLISECONDS);
    }

    @Override
    public void debounce(final Object key, long delay, Runnable task) {
        // 如果上次调用尚未触发，则取消上次任务
        Future<?> old = promiseMap.get(key);
        if (old != null)
            old.cancel(true);
        Future<?> future = executorService.schedule(Executors.callable(task), delay, TimeUnit.MILLISECONDS);
        promiseMap.put(key, future);
    }

    @Override
    public void replace(Object key, Runnable task) {
        debounce(key, 0, task);
    }
}