package io.nop.rpc.core.composite;

import io.nop.commons.concurrent.executor.IScheduledExecutor;
import io.nop.commons.concurrent.executor.ThreadPoolConfig;
import io.nop.commons.concurrent.executor.ThreadPoolStats;
import io.nop.commons.test.time.ManualScheduler;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 测试侧薄适配器（plan 2514-WI8）：把共享 {@link ManualScheduler}（虚拟时间调度器，canonical
 * 实现位于 nop-commons test-jar）适配为本模块生产面的 {@link IScheduledExecutor}。
 * 仅做接口转发与 cancel 传播，不自建时钟/调度逻辑；任务只在 {@link ManualScheduler#advance(long)}
 * 时于推进线程上执行。
 */
final class ManualScheduledExecutor implements IScheduledExecutor {

    private final ManualScheduler delegate;

    ManualScheduledExecutor(ManualScheduler delegate) {
        this.delegate = delegate;
    }

    ManualScheduler delegate() {
        return delegate;
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public <V> CompletableFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
        ScheduledFuture[] taskRef = new ScheduledFuture[1];
        CompletableFuture<V> promise = new CompletableFuture<V>() {
            @Override
            public boolean cancel(boolean mayInterruptIfRunning) {
                boolean cancelled = taskRef[0].cancel(mayInterruptIfRunning);
                super.cancel(mayInterruptIfRunning);
                return cancelled;
            }
        };
        taskRef[0] = delegate.schedule(() -> {
            try {
                promise.complete(callable.call());
            } catch (Throwable e) {
                promise.completeExceptionally(e);
            }
            return null;
        }, delay, unit);
        return promise;
    }

    @Override
    public Future<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
        return delegate.scheduleAtFixedRate(command, initialDelay, period, unit);
    }

    @Override
    public Future<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
        return delegate.scheduleWithFixedDelay(command, initialDelay, delay, unit);
    }

    @Override
    public void execute(Runnable command) {
        delegate.execute(command);
    }

    @Override
    public String getName() {
        return "manual-scheduled-executor";
    }

    @Override
    public ThreadPoolConfig getConfig() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ThreadPoolStats stats() {
        throw new UnsupportedOperationException();
    }

    @Override
    public <V> CompletableFuture<V> submit(Callable<V> callable) {
        CompletableFuture<V> promise = new CompletableFuture<>();
        delegate.execute(() -> {
            try {
                promise.complete(callable.call());
            } catch (Throwable e) {
                promise.completeExceptionally(e);
            }
        });
        return promise;
    }

    @Override
    public <V> CompletableFuture<V> submit(Runnable task, V result) {
        return submit(() -> {
            task.run();
            return result;
        });
    }

    @Override
    public void destroy() {
        delegate.shutdownNow();
    }

    @Override
    public void refreshConfig() {
        // no-op: virtual scheduler has no dynamic config
    }
}
