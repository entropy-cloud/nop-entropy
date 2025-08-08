/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.concurrent.ratelimit;

import io.nop.api.core.annotations.data.DataBean;

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

    default void acquire(){
        tryAcquire(1, Long.MAX_VALUE);
    }

    default void acquire(int permits){
        tryAcquire(permits, Long.MAX_VALUE);
    }

    double getPermitsPerSecond();

    long getAcquireSuccessCount();

    long getAcquireFailCount();

    default RateLimiterStats getStats() {
        return new RateLimiterStats(getPermitsPerSecond(), getAcquireSuccessCount(), getAcquireFailCount());
    }

    void resetStats();

    @DataBean
    class RateLimiterStats {
        private final double permitsPerSecond;
        private final long acquireSuccessCount;
        private final long acquireFailCount;

        public RateLimiterStats(double permitsPerSecond,
                                long acquireSuccessCount,
                                long acquireFailCount) {
            this.permitsPerSecond = permitsPerSecond;
            this.acquireSuccessCount = acquireSuccessCount;
            this.acquireFailCount = acquireFailCount;
        }

        public double getPermitsPerSecond() {
            return permitsPerSecond;
        }

        public long getAcquireSuccessCount() {
            return acquireSuccessCount;
        }

        public long getAcquireFailCount() {
            return acquireFailCount;
        }
    }
}