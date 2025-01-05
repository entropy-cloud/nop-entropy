package io.nop.autotest.core.mock;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.concurrent.executor.IScheduledExecutor;
import io.nop.commons.concurrent.executor.ThreadPoolConfig;
import io.nop.commons.concurrent.executor.ThreadPoolStats;
import io.nop.commons.functional.Functionals;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class MockScheduledExecutor implements IScheduledExecutor {
    private String name;
    private volatile boolean started = true;
    private final DelayQueue<MockTask> tasks = new DelayQueue<>();

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public synchronized <V> CompletableFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
        CompletableFuture<V> promise = new CompletableFuture<>();
        MockTask task = new MockTask(callable, delay, 0, MockTaskKind.ONCE, promise);
        tasks.add(task);
        return promise.exceptionally(err -> {
            tasks.remove(task);
            throw NopException.adapt(err);
        });
    }

    @Override
    public synchronized Future<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
        CompletableFuture<Void> promise = new CompletableFuture<>();
        MockTask task = new MockTask(Functionals.asCallable(command, null), initialDelay,
                period, MockTaskKind.SCHEDULED_AT_FIXED_RATE, promise);
        tasks.add(task);
        return promise.exceptionally(err -> {
            tasks.remove(task);
            throw NopException.adapt(err);
        });
    }

    @Override
    public Future<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
        CompletableFuture<Void> promise = new CompletableFuture<>();
        MockTask task = new MockTask(Functionals.asCallable(command, null), initialDelay,
                delay, MockTaskKind.SCHEDULED_WITH_FIXED_DELAY, promise);
        tasks.add(task);
        return promise.exceptionally(err -> {
            tasks.remove(task);
            throw NopException.adapt(err);
        });
    }

    public Object triggerNext() {
        MockTask task = pollTask();
        if (task == null)
            return null;
        return task.call();
    }

    protected synchronized MockTask pollTask() {
        MockTask task = tasks.poll();
        return task;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public ThreadPoolConfig getConfig() {
        return null;
    }

    @Override
    public ThreadPoolStats stats() {
        return null;
    }

    @Override
    public <V> CompletableFuture<V> submit(Callable<V> callable) {
        return schedule(callable, 0, TimeUnit.MILLISECONDS);
    }

    @Override
    public <V> CompletableFuture<V> submit(Runnable task, V result) {
        return submit(Functionals.asCallable(task, result));
    }

    @Override
    public void refreshConfig() {

    }

    @Override
    public void destroy() {
        started = false;
    }

    @Override
    public void execute(Runnable command) {
        submit(command, null);
    }
}
