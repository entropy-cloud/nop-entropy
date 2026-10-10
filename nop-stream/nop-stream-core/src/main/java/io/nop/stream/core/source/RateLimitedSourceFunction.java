/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.source;

import io.nop.stream.core.common.functions.source.SourceConsistencyCapability;
import io.nop.stream.core.common.functions.source.SourceFunction;

import java.io.Serializable;

/**
 * U3 (deferred-unlock, plan 2376): generic XDSL rate-limit wrapper for ANY
 * {@link SourceFunction} — the declarative face of the engine-side
 * {@link SourceRateLimiter} (multi-engine WI5), previously reachable only through
 * connector-specific params (kafka/file). The wrapper intercepts the source context:
 * every {@code collect}/{@code collectWithTimestamp} acquires one row token (plus an
 * estimated byte token) before delegating, so rate limiting IS backpressure — the
 * source thread blocks. Watermarks, idle marks and the processing-time clock pass
 * through unthrottled.
 *
 * <p>Byte accounting is an estimate: {@code byte[]} uses its length, {@code String}
 * its character count; any other payload contributes zero byte tokens (row tokens
 * still apply). Declared {@code rateLimitRowsPerSecond}/{@code rateLimitBytesPerSecond}
 * of 0 (the xdef default) never constructs this wrapper.
 */
public class RateLimitedSourceFunction<T> implements SourceFunction<T> {

    private static final long serialVersionUID = 1L;

    private final SourceFunction<T> delegate;
    private final SourceRateLimiter limiter;

    public RateLimitedSourceFunction(SourceFunction<T> delegate, SourceRateLimiter limiter) {
        if (delegate == null)
            throw new IllegalArgumentException("delegate source is null");
        if (limiter == null || limiter.isOff())
            throw new IllegalArgumentException("a rate-limit wrapper requires an active limiter");
        this.delegate = delegate;
        this.limiter = limiter;
    }

    public SourceRateLimiter getLimiter() {
        return limiter;
    }

    @Override
    public SourceConsistencyCapability getSourceConsistency() {
        // fidelity checks and connector-consistency validation must see the DELEGATE's
        // capability — the wrapper adds throttling, not consistency semantics
        return delegate.getSourceConsistency();
    }

    @Override
    public void run(SourceContext<T> ctx) throws Exception {
        delegate.run(new ThrottledContext(ctx));
    }

    @Override
    public void cancel() {
        delegate.cancel();
    }

    static long estimateBytes(Object value) {
        if (value instanceof byte[])
            return ((byte[]) value).length;
        if (value instanceof String)
            return ((String) value).length();
        return 0L;
    }

    private class ThrottledContext implements SourceContext<T>, Serializable {

        private static final long serialVersionUID = 1L;

        private final SourceContext<T> delegateContext;

        ThrottledContext(SourceContext<T> delegateContext) {
            this.delegateContext = delegateContext;
        }

        @Override
        public void collect(T element) {
            acquireFor(element);
            delegateContext.collect(element);
        }

        @Override
        public void collectWithTimestamp(T element, long timestamp) {
            acquireFor(element);
            delegateContext.collectWithTimestamp(element, timestamp);
        }

        @Override
        public void emitWatermark(long mark) {
            delegateContext.emitWatermark(mark);
        }

        @Override
        public void markAsTemporarilyIdle() {
            delegateContext.markAsTemporarilyIdle();
        }

        @Override
        public long getProcessingTime() {
            return delegateContext.getProcessingTime();
        }

        private void acquireFor(T element) {
            try {
                limiter.acquire(1L, estimateBytes(element));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("rate-limited source interrupted during "
                        + "backpressure wait (cancel() propagates through the delegate's "
                        + "running flag)", e);
            }
        }
    }
}
