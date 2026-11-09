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

    /**
     * 1.7-C1-004（plan 2547 裁定 A）：原实现 get→cancel→schedule→put 四步非原子，且
     * {@code holder[0]} 在提交后才写（delay=0 时定时线程可先读到 null → CHM.remove(key,null) NPE
     * 被 Future 吞掉 → 陈旧条目残留）；两个业务线程并发时还可能出现「双 null → 双 schedule →
     * 两个任务都执行」的去抖契约破坏。
     * <p>
     * 改为 {@code compute} 单原子域：cancel 旧任务、schedule 新任务、写回新 future 全在 compute 内完成
     * （与同文件 {@link #throttle} 的正确先例同构）。schedule 的 lambda 捕获 {@code promise} 这个
     * effectively-final 局部变量，消除 holder 数组的不安全发布。
     */
    @Override
    public void debounce(final Object key, long delay, Runnable task) {
        // 如果上次调用尚未触发，则取消上次任务
        final CompletableFuture<Void> promise = new CompletableFuture<>();
        promiseMap.compute(key, (k, old) -> {
            if (old != null)
                old.cancel(true);

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
    public void replace(Object key, Runnable task) {
        debounce(key, 0, task);
    }
}