package io.nop.autotest.core.mock;

import io.nop.api.core.exceptions.NopException;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/**
 * Manual-drive task record used by {@link MockScheduledExecutor}. The deadline is anchored in
 * nanoseconds against the executor's virtual clock, so
 * {@link #getDelay(TimeUnit)} decays as the virtual clock advances.
 */
public class MockTask implements Delayed {
    private final Callable<?> action;
    private final long deadlineNanos;
    private final LongSupplier clock;
    private final MockTaskKind taskKind;
    private final CompletableFuture<Object> promise;

    public MockTask(Callable<?> action, long deadlineNanos, LongSupplier clock, MockTaskKind taskKind,
                    CompletableFuture<Object> promise) {
        this.action = action;
        this.deadlineNanos = deadlineNanos;
        this.clock = clock;
        this.taskKind = taskKind;
        this.promise = promise;
    }

    @Override
    public long getDelay(TimeUnit unit) {
        long remainingNanos = Math.max(0L, deadlineNanos - clock.getAsLong());
        return unit.convert(remainingNanos, TimeUnit.NANOSECONDS);
    }

    @Override
    public int compareTo(Delayed o) {
        return Long.compare(getDelay(TimeUnit.NANOSECONDS), o.getDelay(TimeUnit.NANOSECONDS));
    }

    public Callable<?> getAction() {
        return action;
    }

    public MockTaskKind getTaskKind() {
        return taskKind;
    }

    public void completeExceptionally(Throwable err) {
        promise.completeExceptionally(err);
    }

    public Object call() {
        try {
            Object ret = action.call();
            if (taskKind == MockTaskKind.ONCE)
                promise.complete(ret);
            return ret;
        } catch (Exception e) {
            promise.completeExceptionally(e);
            throw NopException.adapt(e);
        }
    }
}
