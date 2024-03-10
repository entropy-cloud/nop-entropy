/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.concurrent.ratelimit;

import com.google.common.util.concurrent.Monitor;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.Guard;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static io.nop.api.core.util.Guard.positiveInt;
import static io.nop.commons.CommonErrors.ARG_COUNT;
import static io.nop.commons.CommonErrors.ARG_HIGH_WATERMARK;
import static io.nop.commons.CommonErrors.ERR_RATE_LIMIT_ACQUIRE_COUNT_EXCEED_LIMIT;

public class WatermarkRateLimiter implements IRateLimiter {
    private int lowWatermark;
    private int highWatermark;

    private final Monitor monitor = new Monitor();
    private final Monitor.Guard acquireOneGuard = monitor.newGuard(() -> _tryAcquire(1));

    // 1 means exceed highWatermark
    private int state = 0;

    private final AtomicInteger acquiredCount;

    public WatermarkRateLimiter(int lowWatermark, int highWatermark, AtomicInteger count) {
        update(lowWatermark, highWatermark);
        this.acquiredCount = count == null ? new AtomicInteger() : count;
    }

    public WatermarkRateLimiter(int lowWatermark, int highWatermark) {
        this(lowWatermark, highWatermark, new AtomicInteger());
    }

    public void update(int lowWatermark, int highWatermark) {
        Guard.checkArgument(lowWatermark > 0, "lowWatermark must be positive", lowWatermark);
        Guard.checkArgument(highWatermark > 0, "highWatermark must be positive", highWatermark);
        Guard.checkArgument(lowWatermark <= highWatermark, "lowWatermark must be smaller than highWatermark",
                lowWatermark, highWatermark);
        this.lowWatermark = lowWatermark;
        this.highWatermark = highWatermark;
    }

    @Override
    public boolean tryAcquire(int permits, long timeout) {
        positiveInt(permits, "permits not positive");

        if (permits > highWatermark)
            throw new NopException(ERR_RATE_LIMIT_ACQUIRE_COUNT_EXCEED_LIMIT).param(ARG_COUNT, permits)
                    .param(ARG_HIGH_WATERMARK, highWatermark);

        Monitor.Guard guard = permits == 1 ? acquireOneGuard : monitor.newGuard(() -> _tryAcquire(permits));

        return monitor.waitForUninterruptibly(guard, timeout, TimeUnit.MILLISECONDS);
    }

    boolean _tryAcquire(int permits) {
        // 超过高水位后将会一直处于等待状态，直至降到低水位之后才能返回获取成功
        if (state == 1) {
            if (permits + acquiredCount.get() <= lowWatermark) {
                acquiredCount.addAndGet(permits);
                state = 0;
                return true;
            }
            return false;
        } else {
            if (acquiredCount.get() < highWatermark) {
                int n = acquiredCount.addAndGet(permits);
                if (n >= highWatermark)
                    state = 1;
                return true;
            }
            return false;
        }
    }

    public int getAcquiredCount() {
        return acquiredCount.get();
    }

    @Override
    public void release(int permits, long duration, Throwable exception) {
        monitor.enter();
        try {
            acquiredCount.addAndGet(-permits);
        } finally {
            monitor.leave();
        }
    }
}