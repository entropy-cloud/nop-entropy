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

    /**
     * C7-015（plan 2548）：默认等待上界可配置（nop.commons.concurrent.rate-limit-acquire-timeout-ms，
     * 缺省 5 分钟；<=0 = 无限等待保持修复前语义）。超时抛 NopException 类型化错误而非静默失败。
     */
    default void acquire(){
        acquire(1);
    }

    default void acquire(int permits){
        long timeoutMs = io.nop.commons.CommonConfigs.CFG_CONCURRENT_RATE_LIMIT_ACQUIRE_TIMEOUT.get();
        if (timeoutMs <= 0) {
            tryAcquire(permits, Long.MAX_VALUE);
        } else if (!tryAcquire(permits, timeoutMs)) {
            throw new io.nop.api.core.exceptions.NopException(
                    io.nop.commons.CommonErrors.ERR_CONCURRENT_RATE_LIMIT_ACQUIRE_TIMEOUT)
                    .param(io.nop.commons.CommonErrors.ARG_PERMITS, permits)
                    .param(io.nop.commons.CommonErrors.ARG_TIMEOUT, timeoutMs);
        }
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