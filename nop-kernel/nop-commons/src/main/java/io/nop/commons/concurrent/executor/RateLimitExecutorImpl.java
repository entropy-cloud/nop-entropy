/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
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
        // 如果上次调度的任务尚未触发，则跳过本次调用
        final CompletableFuture<Void> promise = new CompletableFuture<>();
        promiseMap.compute(key, (k, old) -> {
            if (old != null && !old.isDone())
                return old;

            executorService.schedule(() -> {
                try {
                    FutureHelper.completeAfterTask(promise, Executors.callable(task));
                } finally {
                    // 任务结束后清理条目，避免按key无限累积
                    promiseMap.remove(k, promise);
                }
                return null;
            }, delay, TimeUnit.MILLISECONDS);
            return promise;
        });
    }

    @Override
    public void debounce(final Object key, long delay, Runnable task) {
        // 如果上次调用尚未触发，则取消上次任务
        Future<?> old = promiseMap.get(key);
        if (old != null)
            old.cancel(true);
        final Future<?>[] holder = new Future<?>[1];
        Future<?> future = executorService.schedule(() -> {
            try {
                task.run();
            } finally {
                // 任务结束后清理条目，避免按key无限累积
                promiseMap.remove(key, holder[0]);
            }
            return null;
        }, delay, TimeUnit.MILLISECONDS);
        holder[0] = future;
        promiseMap.put(key, future);
    }

    @Override
    public void replace(Object key, Runnable task) {
        debounce(key, 0, task);
    }
}