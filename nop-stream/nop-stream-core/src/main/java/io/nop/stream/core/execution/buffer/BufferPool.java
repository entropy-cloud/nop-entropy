/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.execution.buffer;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import io.nop.stream.core.exceptions.StreamException;

import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_ARG_NAME;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_INVALID_ARG;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_INVALID_STATE;

/**
 * Default in-memory {@link IBufferPool} backed by a <b>fair</b> {@link Semaphore}.
 *
 * <p>Fairness is intentional: a fair (FIFO) semaphore prevents a single
 * high-rate partition from monopolising permits and starving other partitions
 * in a fan-out scenario — the cross-partition fairness this pool exists to provide.
 *
 * <p>Permits are tracked in <b>element count</b>. One permit corresponds to one
 * {@code StreamElement} currently buffered in some local {@code ResultPartition}
 * queue. Acquire on exhaustion blocks (does not throw); release after consume
 * wakes the next blocked producer.
 *
 * <p>{@link #close()} releases the full capacity worth of permits so any producer
 * blocked in {@link #acquire()} is woken; subsequent {@link #acquire()} throws
 * rather than re-blocking.
 */
public class BufferPool implements IBufferPool {

    /** Fair semaphore so cross-partition permit acquisition is FIFO (no starvation). */
    private final Semaphore permits;

    private final int totalCapacity;

    private volatile boolean closed;

    /**
     * @param totalCapacity the global maximum number of in-flight elements across
     *                      all partitions sharing this pool (must be positive)
     */
    public BufferPool(int totalCapacity) {
        if (totalCapacity <= 0) {
            throw new StreamException(ERR_STREAM_INVALID_ARG)
                    .param(ARG_ARG_NAME, "totalCapacity")
                    .param(ARG_DETAIL, "must be positive, got: " + totalCapacity);
        }
        this.totalCapacity = totalCapacity;
        this.permits = new Semaphore(totalCapacity, true);
    }

    @Override
    public void acquire() throws InterruptedException {
        if (closed) {
            throw new StreamException(ERR_STREAM_INVALID_STATE)
                    .param(ARG_DETAIL, "BufferPool is closed");
        }
        permits.acquire();
        if (closed) {
            // Acquired a permit right as the pool was closed; release it back and fail fast
            // so the caller does not proceed to write into an abandoned partition.
            permits.release();
            throw new StreamException(ERR_STREAM_INVALID_STATE)
                    .param(ARG_DETAIL, "BufferPool is closed");
        }
    }

    @Override
    public boolean tryAcquire(long timeout, TimeUnit unit) throws InterruptedException {
        if (closed) {
            return false;
        }
        boolean acquired = permits.tryAcquire(timeout, unit);
        if (!acquired) {
            return false;
        }
        if (closed) {
            permits.release();
            return false;
        }
        return true;
    }

    @Override
    public void release() {
        permits.release();
    }

    @Override
    public int getGlobalTotalCapacity() {
        return totalCapacity;
    }

    @Override
    public int getGlobalUsage() {
        int available = permits.availablePermits();
        if (available >= totalCapacity) {
            // More permits available than capacity (e.g. after close released extra)
            return 0;
        }
        return totalCapacity - available;
    }

    @Override
    public int getGlobalAvailableCapacity() {
        int available = permits.availablePermits();
        if (available > totalCapacity) {
            return totalCapacity;
        }
        return Math.max(0, available);
    }

    @Override
    public boolean isGlobalBackpressured() {
        return getGlobalAvailableCapacity() <= 0;
    }

    @Override
    public void close() {
        closed = true;
        // Release the full capacity so any producer blocked in acquire() is woken.
        // Extra releases beyond capacity are harmless (metering is clamped).
        permits.release(totalCapacity);
    }

    @Override
    public boolean isClosed() {
        return closed;
    }
}
