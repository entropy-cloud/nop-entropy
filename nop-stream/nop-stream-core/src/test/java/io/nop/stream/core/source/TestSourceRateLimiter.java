/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.source;

import io.nop.stream.core.exceptions.StreamException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI5 (plan 2330): token-bucket rate limiter contract — empty-start bound, channel
 * off at 0, negative rejected, bounded interruptible sleep.
 */
public class TestSourceRateLimiter {

    @Test
    public void rowsRateBoundsThroughputFromT0() throws Exception {
        // 50 rows/s, 5 rows -> theoretical 4/50 = 80ms (>= 0.9x = 72ms)
        SourceRateLimiter limiter = new SourceRateLimiter(50L, 0L);
        long start = System.nanoTime();
        for (int i = 0; i < 5; i++) {
            limiter.acquire(1, 0);
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
        assertTrue(elapsedMs >= 72, () -> "elapsed " + elapsedMs + "ms violates the empty-start bound");
        assertTrue(elapsedMs < 5_000, "sanity bound (slow CI): " + elapsedMs);
    }

    @Test
    public void bytesRateBoundsThroughput() throws Exception {
        // 1000 bytes/s, 4 x 200 bytes -> theoretical 800/1000 = 800ms (>= 0.9x = 720ms)
        SourceRateLimiter limiter = new SourceRateLimiter(0L, 1000L);
        long start = System.nanoTime();
        for (int i = 0; i < 4; i++) {
            limiter.acquire(0, 200);
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
        assertTrue(elapsedMs >= 720, () -> "elapsed " + elapsedMs + "ms violates the bound");
    }

    @Test
    public void zeroRateMeansChannelOff() throws Exception {
        SourceRateLimiter limiter = new SourceRateLimiter(0L, 0L);
        long start = System.nanoTime();
        for (int i = 0; i < 100; i++) {
            limiter.acquire(1, 100);
        }
        assertTrue((System.nanoTime() - start) / 1_000_000L < 100, "off limiter must not sleep");
        assertTrue(limiter.isOff());
    }

    @Test
    public void negativeRateIsTypedRejected() {
        assertThrows(StreamException.class, () -> new SourceRateLimiter(-1L, 0L));
        assertThrows(StreamException.class, () -> new SourceRateLimiter(0L, -5L));
    }

    @Test
    public void acquireRespondsToInterrupt() {
        SourceRateLimiter limiter = new SourceRateLimiter(1L, 0L);
        Thread.currentThread().interrupt();
        // Thread.sleep clears the interrupt flag when it throws — the thrown
        // InterruptedException itself proves the slice loop propagates it immediately.
        assertThrows(InterruptedException.class, () -> limiter.acquire(10, 0));
    }
}
