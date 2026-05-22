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

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class TestAscendingTimestampsWatermarks {

    private AscendingTimestampsWatermarks<String> generator;
    private MockWatermarkOutput output;

    @BeforeEach
    void setUp() {
        generator = new AscendingTimestampsWatermarks<>();
        output = new MockWatermarkOutput();
    }

    @Test
    void testWatermarkEqualsTimestamp() {
        generator.onEvent("event", 1000, output);
        generator.onPeriodicEmit(output);

        assertEquals(1, output.watermarks.size());
        assertEquals(999, output.watermarks.get(0).getTimestamp(),
                "With zero out-of-orderness, watermark should be maxTimestamp - 1");
    }

    @Test
    void testMonotonicallyIncreasing() {
        generator.onEvent("e1", 100, output);
        generator.onPeriodicEmit(output);

        generator.onEvent("e2", 200, output);
        generator.onPeriodicEmit(output);

        generator.onEvent("e3", 300, output);
        generator.onPeriodicEmit(output);

        assertEquals(3, output.watermarks.size());
        for (int i = 1; i < output.watermarks.size(); i++) {
            assertTrue(output.watermarks.get(i).getTimestamp() > output.watermarks.get(i - 1).getTimestamp(),
                    "Watermarks must be monotonically increasing");
        }
        assertEquals(99, output.watermarks.get(0).getTimestamp());
        assertEquals(199, output.watermarks.get(1).getTimestamp());
        assertEquals(299, output.watermarks.get(2).getTimestamp());
    }

    @Test
    void testNoElementProducesInitialWatermark() {
        generator.onPeriodicEmit(output);

        assertEquals(1, output.watermarks.size());
        assertEquals(Long.MIN_VALUE, output.watermarks.get(0).getTimestamp());
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
