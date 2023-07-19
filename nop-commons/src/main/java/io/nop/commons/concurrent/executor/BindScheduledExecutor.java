/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.commons.concurrent.executor;

import io.nop.api.core.util.FutureHelper;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 具体执行任务时总是调度到某个特定的Executor上执行
 */
public class BindScheduledExecutor implements IScheduledExecutor {
    private final IScheduledExecutor timer;
    private final Executor executor;

    public BindScheduledExecutor(IScheduledExecutor timer, Executor executor) {
        this.timer = timer;
        this.executor = executor;
    }

    @Override
    public ThreadPoolConfig getConfig() {
        return timer.getConfig();
    }

    @Override
    public void refreshConfig() {
        timer.refreshConfig();
    }

    @Override
    public IScheduledExecutor executeOn(Executor executor) {
        if (this.executor == executor)
            return this;

        return new BindScheduledExecutor(timer, executor);
    }

    @Override
    public <V> CompletableFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
        CompletableFuture<V> future = new CompletableFuture<>();
        timer.schedule(() -> {
            executeOn(callable, future);
            return null;
        }, delay, unit);
        return future;
    }

    @Override
    public Future<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
        return timer.scheduleAtFixedRate(() -> {
            executor.execute(command);
        }, initialDelay, period, unit);
    }

    @Override
    public Future<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        AtomicReference<Future<?>> timerHandle = new AtomicReference<>();
        future.exceptionally(e -> {
            if (FutureHelper.isCancellationException(e)) {
                Future<?> f = timerHandle.get();
                if (f != null) {
                    f.cancel(false);
                }
            }
            return null;
        });
        schedule(command, initialDelay, delay, unit, future, timerHandle);
        return future;
    }

    private void schedule(Runnable command, long initialDelay, long delay, TimeUnit unit,
                          CompletableFuture<Void> future, AtomicReference<Future<?>> timerHandle) {
        Future<?> f = timer.schedule(() -> {
            if (future.isDone())
                return null;

            try {
                executor.execute(() -> {
                    if (future.isDone())
                        return;

                    try {
                        command.run();
                    } catch (Throwable e) {
                        future.completeExceptionally(e);
                        return;
                    }

                    schedule(command, delay, delay, unit, future, timerHandle);
                });
            } catch (Throwable e) {
                future.completeExceptionally(e);
            }
            return null;
        }, initialDelay, unit);

        timerHandle.set(f);
    }

    @Override
    public String getName() {
        return timer.getName();
    }

    @Override
    public ThreadPoolStats stats() {
        return timer.stats();
    }

    @Override
    public <V> CompletableFuture<V> submit(Callable<V> callable) {
        CompletableFuture<V> future = new CompletableFuture<>();
        timer.submit(() -> {
            executeOn(callable, future);
        }, null);
        return future;
    }

    @Override
    public <V> CompletableFuture<V> submit(Runnable task, V result) {
        return submit(Executors.callable(task, result));
    }

    private <V> void executeOn(Callable<V> callable, CompletableFuture<V> future) {
        try {
            executor.execute(() -> {
                try {
                    future.complete(callable.call());
                } catch (Throwable e) {
                    future.completeExceptionally(e);
                }
            });
        } catch (Throwable e) {
            future.completeExceptionally(e);
        }
    }

    @Override
    public void destroy() {
        timer.destroy();
    }

    @Override
    public void execute(Runnable command) {
        executor.execute(command);
    }
}
