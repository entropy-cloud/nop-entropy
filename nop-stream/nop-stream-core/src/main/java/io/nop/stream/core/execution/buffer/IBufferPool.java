/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.execution.buffer;

import java.util.concurrent.TimeUnit;

/**
 * Per-job, cross-partition global buffer quota expressed in <b>element count</b>
 * (not bytes), shared by all local {@code ResultPartition} instances created by
 * a single {@code GraphExecutionPlan.build(...)} invocation.
 *
 * <p><b>Cardinality</b>: one pool instance per job execution. All partitions
 * built within the same plan share the same pool, so that the aggregate in-flight
 * element count across a fan-out of N partitions is observable and bounded.
 *
 * <p><b>Exhaustion contract</b>: when the pool is exhausted, {@link #acquire()}
 * <b>blocks</b> the caller (consistent with {@code ResultPartition.write()} using
 * {@code LinkedBlockingQueue.put()} to block the producer). It never throws a
 * {@code RuntimeException} on exhaustion. The block is wakeable by thread
 * interrupt (never a permanent deadlock) and by {@link #close()}.
 *
 * <p><b>Unit</b>: element count. This SPI deliberately does not interact with
 * {@code MemoryBudget.networkBuffers} (byte-based); the two units are
 * incomparable and no conversion is performed.
 *
 * <p><b>Scope</b>: this SPI only governs local {@code ResultPartition} instances.
 * {@code RemoteResultPartition} / {@code RemoteInputChannel} intentionally bypass
 * the pool (cross-JVM bound is provided by {@code IMessageService} backend,
 * Stage 40).
 */
public interface IBufferPool {

    /**
     * Acquire a single element permit, blocking if the pool is exhausted until one
     * is available or the current thread is interrupted.
     *
     * @throws InterruptedException if the current thread is interrupted while waiting
     * @throws io.nop.stream.core.exceptions.StreamException if the pool has been closed
     */
    void acquire() throws InterruptedException;

    /**
     * Acquire a single element permit, blocking up to the given timeout.
     *
     * @param timeout the maximum time to wait
     * @param unit    the time unit of the timeout
     * @return {@code true} if a permit was acquired; {@code false} if the timeout elapsed
     *         or the pool is closed
     * @throws InterruptedException if the current thread is interrupted while waiting
     */
    boolean tryAcquire(long timeout, TimeUnit unit) throws InterruptedException;

    /**
     * Release a single element permit back to the pool, waking one blocked acquirer if any.
     */
    void release();

    /**
     * @return the global total (maximum) permit capacity of this pool
     */
    int getGlobalTotalCapacity();

    /**
     * @return the number of permits currently acquired (in-flight elements) across
     *         all partitions sharing this pool
     */
    int getGlobalUsage();

    /**
     * @return the number of permits currently available to acquire
     */
    int getGlobalAvailableCapacity();

    /**
     * @return {@code true} if the pool is currently exhausted (no permits available)
     */
    boolean isGlobalBackpressured();

    /**
     * Close the pool, waking any threads blocked in {@link #acquire()} / {@link #tryAcquire(long, TimeUnit)}.
     * After close, {@link #acquire()} throws rather than blocking. Idempotent.
     */
    void close();

    /**
     * @return {@code true} if {@link #close()} has been called
     */
    boolean isClosed();
}
