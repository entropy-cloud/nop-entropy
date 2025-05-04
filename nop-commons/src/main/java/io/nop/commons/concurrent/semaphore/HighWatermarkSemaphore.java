package io.nop.commons.concurrent.semaphore;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 *
 */
public class HighWatermarkSemaphore implements ISemaphore {
    private final Lock lock = new ReentrantLock();
    private final Condition belowLowWatermark = lock.newCondition(); // 条件变量重命名

    private final int highWatermark;
    private final int lowWatermark;
    private int usedPermits;
    private boolean overHighWatermark = false; // 新增状态标志

    private final AtomicLong acquireSuccessCount = new AtomicLong();
    private final AtomicLong acquireFailCount = new AtomicLong();

    public HighWatermarkSemaphore(int highWatermark, int lowWatermark) {
        if (highWatermark <= lowWatermark) {
            throw new IllegalArgumentException("High watermark must be greater than low watermark");
        }
        if (lowWatermark < 0 || highWatermark <= 0) {
            throw new IllegalArgumentException("Watermarks must be positive");
        }

        this.highWatermark = highWatermark;
        this.lowWatermark = lowWatermark;
        this.usedPermits = 0;
    }

    @Override
    public boolean tryAcquire(int permits, long timeout) {
        if (permits <= 0) {
            throw new IllegalArgumentException("Permits must be positive");
        }
        if (permits > highWatermark) {
            throw new IllegalArgumentException("Cannot acquire more than high watermark");
        }

        lock.lock();
        try {
            long nanos = TimeUnit.MILLISECONDS.toNanos(timeout);

            // 关键修改：如果曾经超过高水位，必须等到低水位以下
            while (overHighWatermark ||
                    (usedPermits + permits > highWatermark && usedPermits >= lowWatermark)) {
                if (nanos <= 0) {
                    return false;
                }
                nanos = belowLowWatermark.awaitNanos(nanos);
            }

            usedPermits += permits;
            // 标记是否超过高水位
            if (usedPermits > highWatermark) {
                overHighWatermark = true;
            }
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void release(int permits) {
        if (permits <= 0) {
            throw new IllegalArgumentException("Permits must be positive");
        }

        lock.lock();
        try {
            if (usedPermits < permits) {
                throw new IllegalStateException("Released more permits than acquired");
            }

            int oldUsed = usedPermits;
            usedPermits -= permits;

            // 关键修改：只有当降到低水位以下时才清除标志并通知
            if (oldUsed >= lowWatermark && usedPermits < lowWatermark) {
                overHighWatermark = false;
                belowLowWatermark.signalAll();
            }
        } finally {
            lock.unlock();
        }
    }

    public int getUsedPermits() {
        lock.lock();
        try {
            return usedPermits;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public int availablePermits() {
        lock.lock();
        try {
            return Math.max(0, highWatermark - usedPermits);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public int maxPermits() {
        return highWatermark;
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
}