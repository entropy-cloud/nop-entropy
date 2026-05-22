/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.eventtime;

import io.nop.stream.core.streamrecord.watermark.Watermark;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class TestBoundedOutOfOrdernessWatermarks {

    private static final long OUT_OF_ORDERNESS_MS = 50;

    private BoundedOutOfOrdernessWatermarks<String> generator;
    private MockWatermarkOutput output;

    @BeforeEach
    void setUp() {
        generator = new BoundedOutOfOrdernessWatermarks<>(Duration.ofMillis(OUT_OF_ORDERNESS_MS));
        output = new MockWatermarkOutput();
    }

    @Test
    void testFirstElementWatermark() {
        generator.onEvent("event", 100, output);
        generator.onPeriodicEmit(output);

        assertEquals(1, output.watermarks.size());
        long expectedWatermark = 100 - OUT_OF_ORDERNESS_MS - 1;
        assertEquals(expectedWatermark, output.watermarks.get(0).getTimestamp());
    }

    @Test
    void testWatermarkMonotonicallyIncreasing() {
        generator.onEvent("event1", 200, output);
        generator.onPeriodicEmit(output);

        int countBefore = output.watermarks.size();
        generator.onEvent("event2", 100, output);
        generator.onPeriodicEmit(output);

        assertEquals(countBefore + 1, output.watermarks.size());
        assertTrue(output.watermarks.get(countBefore).getTimestamp()
                        >= output.watermarks.get(countBefore - 1).getTimestamp(),
                "Watermark must not decrease even with out-of-order events");
    }

    @Test
    void testPeriodicEmitCorrectWatermark() {
        generator.onEvent("event1", 100, output);
        generator.onEvent("event2", 300, output);
        generator.onEvent("event3", 200, output);
        generator.onPeriodicEmit(output);

        assertEquals(1, output.watermarks.size());
        long expectedWatermark = 300 - OUT_OF_ORDERNESS_MS - 1;
        assertEquals(expectedWatermark, output.watermarks.get(0).getTimestamp());
    }

    @Test
    void testNoElementNoWatermarkChange() {
        long initialMaxTs = Long.MIN_VALUE + OUT_OF_ORDERNESS_MS + 1;
        generator.onPeriodicEmit(output);

        assertEquals(1, output.watermarks.size());
        assertEquals(initialMaxTs - OUT_OF_ORDERNESS_MS - 1,
                output.watermarks.get(0).getTimestamp());
    }

    static class MockWatermarkOutput implements WatermarkOutput {
        final List<Watermark> watermarks = new ArrayList<>();
        boolean idle = false;

        @Override
        public void emitWatermark(Watermark watermark) {
            watermarks.add(watermark);
        }

        @Override
        public void markIdle() {
            idle = true;
        }

        @Override
        public void markActive() {
            idle = false;
        }
    }
}
