/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.concurrent.ratelimit;

import com.google.common.util.concurrent.RateLimiter;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class DefaultRateLimiter implements IRateLimiter {
    private final RateLimiter rateLimiter;
    private final AtomicLong acquireSuccessCount = new AtomicLong();
    private final AtomicLong acquireFailCount = new AtomicLong();

    public DefaultRateLimiter(double permitsPerSecond) {
        this.rateLimiter = RateLimiter.create(permitsPerSecond);
    }

    public static IRateLimiter create(double permitsPerSecond) {
        return new DefaultRateLimiter(permitsPerSecond);
    }

    @Override
    public double getPermitsPerSecond() {
        return rateLimiter.getRate();
    }

    @Override
    public long getAcquireSuccessCount() {
        return acquireSuccessCount.get();
    }

    @Override
    public long getAcquireFailCount() {
        return acquireSuccessCount.get();
    }

    @Override
    public void resetStats() {
        acquireFailCount.set(0);
        acquireSuccessCount.set(0);
    }

    @Override
    public boolean tryAcquire(int permits, long timeout) {
        boolean b = rateLimiter.tryAcquire(permits, timeout, TimeUnit.MILLISECONDS);
        if (b) {
            acquireSuccessCount.incrementAndGet();
        } else {
            acquireFailCount.incrementAndGet();
        }
        return b;
    }

    public double getRate() {
        return rateLimiter.getRate();
    }

    public void setRate(double permitsPerSecond) {
        rateLimiter.setRate(permitsPerSecond);
    }
}