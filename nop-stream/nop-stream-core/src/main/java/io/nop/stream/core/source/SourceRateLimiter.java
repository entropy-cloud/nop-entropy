/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.source;

import io.nop.api.core.time.CoreMetrics;
import io.nop.stream.core.exceptions.NopStreamErrors;
import io.nop.stream.core.exceptions.StreamException;

import java.io.Serializable;

import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_INVALID_ARG;

/**
 * WI5 (plan 2330): rows/bytes dual-channel token-bucket rate limiter for source
 * readers. The bucket starts EMPTY — the configured rate is honored strictly from
 * t=0 (a correct implementation can never emit faster than
 * {@code max(N/rateRows, B/rateBytes)} for N rows / B bytes).
 *
 * <p><strong>Semantics</strong>: {@code 0} on a channel = that channel is OFF (no
 * throttling); a NEGATIVE rate is rejected at construction. Callers distinguish
 * "unset" from "explicit" at their config layer (a factory turns an absent optional
 * param into 0); the limiter itself only knows on/off.
 *
 * <p><strong>Blocking contract</strong>: {@link #acquire} sleeps in bounded slices
 * (never longer than {@value #MAX_SLEEP_SLICE_MS} ms per slice) and responds to
 * interrupt — callers must not hold locks across an acquire. Serializable with plain
 * long state so source readers can persist/rebuild it without read-through traps.
 */
public final class SourceRateLimiter implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Upper bound of one continuous sleep slice, so cancel/interrupt latency stays bounded. */
    public static final long MAX_SLEEP_SLICE_MS = 100L;

    private final long rowsPerSecond;
    private final long bytesPerSecond;

    private transient long lastRefillNanos;
    private transient double availableRowTokens;
    private transient double availableByteTokens;

    /**
     * @param rowsPerSecond  max records per second; 0 = rows channel off; negative = rejected
     * @param bytesPerSecond max bytes per second; 0 = bytes channel off; negative = rejected
     */
    public SourceRateLimiter(long rowsPerSecond, long bytesPerSecond) {
        if (rowsPerSecond < 0) {
            throw new StreamException(ERR_STREAM_INVALID_ARG)
                    .param(ARG_DETAIL, "rowsPerSecond must not be negative: " + rowsPerSecond);
        }
        if (bytesPerSecond < 0) {
            throw new StreamException(ERR_STREAM_INVALID_ARG)
                    .param(ARG_DETAIL, "bytesPerSecond must not be negative: " + bytesPerSecond);
        }
        this.rowsPerSecond = rowsPerSecond;
        this.bytesPerSecond = bytesPerSecond;
        this.lastRefillNanos = CoreMetrics.nanoTime();
    }

    public long getRowsPerSecond() {
        return rowsPerSecond;
    }

    public long getBytesPerSecond() {
        return bytesPerSecond;
    }

    public boolean isOff() {
        return rowsPerSecond == 0 && bytesPerSecond == 0;
    }

    /**
     * Blocks until the given rows/bytes fit both configured budgets.
     *
     * @throws InterruptedException when the sleeping thread is interrupted (propagated
     *                              immediately, slice loop exits)
     */
    public void acquire(long rows, long bytes) throws InterruptedException {
        if (isOff()) {
            return;
        }
        long now = CoreMetrics.nanoTime();
        refill(now);
        while (true) {
            long waitNanos = deficitNanos(rows, bytes);
            if (waitNanos <= 0) {
                // consume tokens and return
                if (rowsPerSecond > 0) {
                    availableRowTokens -= rows;
                }
                if (bytesPerSecond > 0) {
                    availableByteTokens -= bytes;
                }
                return;
            }
            long sleepMs = Math.min(MAX_SLEEP_SLICE_MS, Math.max(1L, waitNanos / 1_000_000L));
            Thread.sleep(sleepMs);
            refill(CoreMetrics.nanoTime());
        }
    }

    private void refill(long now) {
        double elapsedSeconds = (now - lastRefillNanos) / 1_000_000_000.0;
        if (elapsedSeconds <= 0) {
            return;
        }
        if (rowsPerSecond > 0) {
            availableRowTokens = Math.min(availableRowTokens + rowsPerSecond * elapsedSeconds, rowsPerSecond);
        }
        if (bytesPerSecond > 0) {
            availableByteTokens = Math.min(availableByteTokens + bytesPerSecond * elapsedSeconds, bytesPerSecond);
        }
        lastRefillNanos = now;
    }

    private long deficitNanos(long rows, long bytes) {
        long worst = 0;
        if (rowsPerSecond > 0 && rows > availableRowTokens) {
            worst = Math.max(worst, nanosFor((long) (rows - availableRowTokens), rowsPerSecond));
        }
        if (bytesPerSecond > 0 && bytes > availableByteTokens) {
            worst = Math.max(worst, nanosFor((long) (bytes - availableByteTokens), bytesPerSecond));
        }
        return worst;
    }

    private static long nanosFor(long tokenDeficit, long ratePerSecond) {
        return (long) Math.ceil(tokenDeficit * 1_000_000_000.0 / ratePerSecond);
    }
}
