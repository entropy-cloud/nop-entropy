/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy/nop-entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.eventtime;

import io.nop.stream.core.util.clock.Clock;
import io.nop.stream.core.streamrecord.watermark.Watermark;
import io.nop.stream.core.streamrecord.watermark.WatermarkStatus;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R6 KR-08 regression pin (plan 2324, generator half): after the idleness
 * detector announces IDLE downstream (markIdle), the FIRST event must announce
 * recovery through {@code output.markActive()} — Flink parity. Before the fix,
 * {@code onEvent} only reset the local {@code isIdleNow} flag, so the IDLE
 * status was never followed by an ACTIVE status and a downstream InputGate that
 * evicted the channel from watermark merging stayed evicted forever (event time
 * permanently frozen for that channel). Reverting the {@code output.markActive()}
 * call in {@code onEvent} turns this test red.
 */
class TestWatermarksWithIdlenessActiveTransition {

    /** Controllable clock for deterministic idleness detection. */
    static class FakeClock extends Clock {
        long nanos;

        @Override
        public long relativeTimeNanos() {
            return nanos;
        }

        @Override
        public long relativeTimeMillis() {
            return nanos / 1_000_000L;
        }

        @Override
        public long absoluteTimeMillis() {
            return System.currentTimeMillis() + nanos / 1_000_000L;
        }
    }

    /** Records watermark STATUS transitions alongside watermarks. */
    static class StatusOutput implements WatermarkOutput {
        final List<WatermarkStatus> statuses = new ArrayList<>();
        final List<Long> watermarks = new ArrayList<>();

        @Override
        public void emitWatermark(Watermark watermark) {
            watermarks.add(watermark.getTimestamp());
        }

        @Override
        public void markIdle() {
            statuses.add(WatermarkStatus.IDLE);
        }

        @Override
        public void markActive() {
            statuses.add(WatermarkStatus.ACTIVE);
        }
    }

    @Test
    void firstEventAfterIdleEpisodeEmitsActiveTransition() {
        FakeClock clock = new FakeClock();
        WatermarkGenerator<String> delegate = new WatermarkGenerator<String>() {
            @Override
            public void onEvent(String event, long eventTimestamp, WatermarkOutput output) {
                output.emitWatermark(new Watermark(eventTimestamp));
            }

            @Override
            public void onPeriodicEmit(WatermarkOutput output) {
            }
        };
        WatermarksWithIdleness<String> gen = new WatermarksWithIdleness<>(
                delegate, Duration.ofMillis(10), clock);

        StatusOutput output = new StatusOutput();

        // activity, then silence past the idle timeout (checkIfIdle needs two
        // consecutive silent probes: the first starts the inactivity timer)
        gen.onEvent("a", 100L, output);
        // probe 1 observes the activity and resets; probe 2 starts the inactivity
        // timer; probe 3 passes the timeout and announces IDLE
        clock.nanos += Duration.ofMillis(20).toNanos();
        gen.onPeriodicEmit(output);
        clock.nanos += Duration.ofMillis(20).toNanos();
        gen.onPeriodicEmit(output);
        clock.nanos += Duration.ofMillis(20).toNanos();
        gen.onPeriodicEmit(output);
        assertEquals(List.of(WatermarkStatus.IDLE), output.statuses,
                "silence past the timeout must announce IDLE");

        // the first event after the idle episode must announce ACTIVE
        gen.onEvent("b", 200L, output);
        assertTrue(output.statuses.contains(WatermarkStatus.ACTIVE),
                () -> "first event after idle must emit the ACTIVE transition, got: " + output.statuses);

        // and the watermark stream resumes
        gen.onPeriodicEmit(output);
        assertTrue(!output.watermarks.isEmpty(), "watermarks must resume after reactivation");
    }
}
