/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.concurrent.ratelimit;

public interface IRateLimiter {
    /**
     * Acquires the given number of permits from this {@code RateLimiter} if it can be obtained without exceeding the
     * specified {@code timeout}, or returns {@code false} immediately (without waiting) if the permits would not have
     * been granted before the timeout expired.
     *
     * @param permits the number of permits to acquire
     * @param timeout the maximum time to wait for the permits. Negative values are treated as zero.
     * @return {@code true} if the permits were acquired, {@code false} otherwise
     * @throws IllegalArgumentException if the requested number of permits is negative or zero
     */
    boolean tryAcquire(int permits, long timeout);

    default boolean tryAcquire(int permits) {
        return tryAcquire(permits, 0);
    }

    default boolean tryAcquire() {
        return tryAcquire(1);
    }

    void release(int permits, long durationNanos, Throwable exception);

    default void release(int permits) {
        release(permits, 0, null);
    }
}