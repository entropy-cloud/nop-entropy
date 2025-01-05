package io.nop.autotest.core.mock;

import io.nop.api.core.exceptions.NopException;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

public class MockTask implements Delayed {
    private final Callable<?> action;
    private final long initialDelay;
    private final long period;
    private final MockTaskKind taskKind;
    private final CompletableFuture<?> promise;

    public MockTask(Callable<?> action, long initialDelay, long period, MockTaskKind taskKind,
                    CompletableFuture<?> promise) {
        this.action = action;
        this.initialDelay = initialDelay;
        this.period = period;
        this.taskKind = taskKind;
        this.promise = promise;
    }

    @Override
    public long getDelay(TimeUnit unit) {
        return unit.convert(initialDelay, TimeUnit.MILLISECONDS);
    }

    @Override
    public int compareTo(Delayed o) {
        return Long.compare(initialDelay, o.getDelay(TimeUnit.MILLISECONDS));
    }

    public Callable<?> getAction() {
        return action;
    }

    public Object call() {
        try {
            Object ret = action.call();
            if (taskKind == MockTaskKind.ONCE)
                ((CompletableFuture<Object>) promise).complete(ret);
            return ret;
        } catch (Exception e) {
            promise.completeExceptionally(e);
            throw NopException.adapt(e);
        }
    }

    public long getInitialDelay() {
        return initialDelay;
    }

    public long getPeriod() {
        return period;
    }

    public MockTaskKind getTaskKind() {
        return taskKind;
    }
}
