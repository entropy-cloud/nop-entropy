package io.nop.commons.concurrent.semaphore;

import io.nop.api.core.exceptions.NopException;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class DefaultSemaphore implements ISemaphore {
    private final int maxPermits;
    private final Semaphore semaphore;
    private final AtomicLong acquireSuccessCount = new AtomicLong();
    private final AtomicLong acquireFailCount = new AtomicLong();

    public DefaultSemaphore(int maxPermits) {
        this.maxPermits = maxPermits;
        this.semaphore = new Semaphore(maxPermits);
    }

    @Override
    public int availablePermits() {
        return semaphore.availablePermits();
    }

    @Override
    public int maxPermits() {
        return maxPermits;
    }

    @Override
    public long getAcquireSuccessCount() {
        return acquireSuccessCount.get();
    }

    @Override
    public long getAcquireFailCount() {
        return acquireFailCount.get();
    }

    @Override
    public void resetStats() {
        acquireSuccessCount.set(0);
        acquireFailCount.set(0);
    }

    @Override
    public boolean tryAcquire(int permits, long timeout) {
        try {
            boolean b = semaphore.tryAcquire(permits, timeout, TimeUnit.MILLISECONDS);
            if (b) {
                acquireSuccessCount.incrementAndGet();
            } else {
                acquireFailCount.incrementAndGet();
            }
            return b;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw NopException.adapt(e);
        }
    }

    @Override
    public void release(int permits) {
        semaphore.release(permits);
    }
}
