/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.concurrent.ratelimit;

import io.nop.api.core.time.CoreMetrics;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class CompositeRateLimiter implements IRateLimiter {
    private final List<IRateLimiter> limiters;

    public CompositeRateLimiter(List<IRateLimiter> limiters) {
        this.limiters = limiters;
    }

    @Override
    public boolean tryAcquire(int permits, long timeout) {
        if (timeout < 0)
            timeout = 0;

        int n = limiters.size();
        long now = CoreMetrics.nanoTime();

        for (int i = 0; i < n; i++) {
            IRateLimiter limiter = limiters.get(i);
            if (!limiter.tryAcquire(permits, timeout)) {
                _revert(i, permits);
                return false;
            }
            long diff = TimeUnit.NANOSECONDS.toMillis(CoreMetrics.nanoTimeDiff(now));
            timeout -= diff;
            if (timeout < 0)
                timeout = 0;
        }
        return true;
    }

    void _revert(int index, int permits) {
        for (int i = 0; i < index; i++) {
            limiters.get(i).release(permits, -1, null);
        }
    }

    @Override
    public void release(int permits, long duration, Throwable exception) {
        for (int i = 0, n = limiters.size(); i < n; i++) {
            limiters.get(i).release(permits, duration, exception);
        }
    }
}