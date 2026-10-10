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
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * U3 (deferred-unlock, plan 2376): the generic rate-limit wrapper. Throttling is
 * pinned with a loose timing bound (1 row/second makes the second/third collect
 * block); passthrough and capability delegation are pinned structurally.
 */
public class TestRateLimitedSourceFunction {

    static class ListSource implements SourceFunction<String> {
        private static final long serialVersionUID = 1L;
        final List<String> values = new ArrayList<>();
        volatile boolean cancelled;

        @Override
        public void run(SourceContext<String> ctx) {
            for (String v : values) {
                if (cancelled)
                    return;
                ctx.collect(v);
            }
        }

        @Override
        public void cancel() {
            cancelled = true;
        }
    }

    static class ListContext implements SourceFunction.SourceContext<String> {
        final List<String> collected = new ArrayList<>();
        long watermark;
        boolean idleMarked;

        @Override
        public void collect(String element) {
            collected.add(element);
        }

        @Override
        public void collectWithTimestamp(String element, long timestamp) {
            collected.add(element + "@" + timestamp);
        }

        @Override
        public void emitWatermark(long mark) {
            this.watermark = mark;
        }

        @Override
        public void markAsTemporarilyIdle() {
            idleMarked = true;
        }

        @Override
        public long getProcessingTime() {
            return 4242L;
        }
    }

    @Test
    public void wrapperAddsThrottlingNotConsistencySemantics() {
        ListSource delegate = new ListSource();
        RateLimitedSourceFunction<String> wrapper = new RateLimitedSourceFunction<>(
                delegate, new SourceRateLimiter(1000, 0));
        assertSame(delegate.getSourceConsistency(), wrapper.getSourceConsistency(),
                "the wrapper must surface the DELEGATE's capability");
    }

    @Test
    public void collectPathAcquiresRowTokensAndDelegates() throws Exception {
        ListSource delegate = new ListSource();
        delegate.values.addAll(List.of("a", "b", "c"));
        ListContext ctx = new ListContext();
        // 1 row/second: the first token is free, each subsequent collect blocks ~1s —
        // three collects therefore take at least ~2s (loose lower bound, no upper
        // bound assertion: scheduling jitter must not flake the test)
        RateLimitedSourceFunction<String> wrapper = new RateLimitedSourceFunction<>(
                delegate, new SourceRateLimiter(1, 0));
        long start = System.nanoTime();
        wrapper.run(ctx);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
        assertEquals(List.of("a", "b", "c"), ctx.collected,
                "rate limiting throttles but never drops");
        assertTrue(elapsedMs >= 1500,
                "1 row/second must throttle two additional collects (~2s), got " + elapsedMs + "ms");
    }

    @Test
    public void watermarkIdleAndClockPassThroughUnthrottled() throws Exception {
        ListSource delegate = new ListSource();
        delegate.values.add("probe");
        ListContext ctx = new ListContext();
        RateLimitedSourceFunction<String> wrapper = new RateLimitedSourceFunction<>(
                delegate, new SourceRateLimiter(1, 0));
        wrapper.run(new SourceFunction.SourceContext<String>() {
            @Override
            public void collect(String element) {
                // drive the passthrough probes from inside the source loop
                ctx.emitWatermark(42L);
                ctx.markAsTemporarilyIdle();
                assertEquals(4242L, ctx.getProcessingTime());
            }

            @Override
            public void collectWithTimestamp(String element, long timestamp) {
            }

            @Override
            public void emitWatermark(long mark) {
            }

            @Override
            public void markAsTemporarilyIdle() {
            }

            @Override
            public long getProcessingTime() {
                return 0L;
            }
        });
        assertEquals(42L, ctx.watermark, "watermarks pass through unthrottled");
        assertTrue(ctx.idleMarked, "idle marks pass through unthrottled");
    }

    @Test
    public void estimateBytesCoversByteArrayAndString() {
        assertEquals(5L, RateLimitedSourceFunction.estimateBytes(new byte[5]));
        assertEquals(3L, RateLimitedSourceFunction.estimateBytes("abc"));
        assertEquals(0L, RateLimitedSourceFunction.estimateBytes(new Object()),
                "unmeasurable payloads contribute zero byte tokens (row tokens still apply)");
    }

    @Test
    public void inactiveLimiterIsRejectedAtConstruction() {
        ListSource delegate = new ListSource();
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new RateLimitedSourceFunction<>(delegate, new SourceRateLimiter(0, 0)));
        assertTrue(ex.getMessage().contains("active limiter"), () -> ex.getMessage());
        assertSame(SourceConsistencyCapability.BEST_EFFORT,
                new SourceFunction<String>() {
                    @Override
                    public void run(SourceContext<String> ctx) {
                    }

                    @Override
                    public void cancel() {
                    }
                }.getSourceConsistency(),
                "sanity: the interface default capability is BEST_EFFORT (the WI15-noted "
                        + "asymmetry vs the xdef's AT_LEAST_ONCE parse default)");
    }
}
