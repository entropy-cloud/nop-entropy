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

public class TestWatermarkOutputMultiplexer {

    private CapturingWatermarkOutput underlyingOutput;
    private WatermarkOutputMultiplexer multiplexer;

    @BeforeEach
    void setUp() {
        underlyingOutput = new CapturingWatermarkOutput();
        multiplexer = new WatermarkOutputMultiplexer(underlyingOutput);
    }

    @Test
    void testMultiSourceWatermarkMerge() {
        multiplexer.registerNewOutput("source-1", wm -> {});
        multiplexer.registerNewOutput("source-2", wm -> {});

        WatermarkOutput output1 = multiplexer.getImmediateOutput("source-1");
        WatermarkOutput output2 = multiplexer.getImmediateOutput("source-2");

        output1.emitWatermark(new Watermark(100));
        output2.emitWatermark(new Watermark(200));

        List<Watermark> watermarks = underlyingOutput.watermarks;
        assertFalse(watermarks.isEmpty(), "Combined watermark should be emitted");

        long combined = watermarks.get(watermarks.size() - 1).getTimestamp();
        assertEquals(100, combined, "Combined watermark should be minimum of all sources");
    }

    @Test
    void testIdleSourceDoesNotBlockWatermark() {
        multiplexer.registerNewOutput("source-1", wm -> {});
        multiplexer.registerNewOutput("source-2", wm -> {});

        WatermarkOutput output1 = multiplexer.getImmediateOutput("source-1");
        WatermarkOutput output2 = multiplexer.getImmediateOutput("source-2");

        output1.emitWatermark(new Watermark(100));
        output2.emitWatermark(new Watermark(100));

        underlyingOutput.watermarks.clear();

        output1.markIdle();

        output2.emitWatermark(new Watermark(300));

        List<Watermark> watermarks = underlyingOutput.watermarks;
        assertFalse(watermarks.isEmpty(), "Idle source should not block watermark advancement");

        long latest = watermarks.get(watermarks.size() - 1).getTimestamp();
        assertEquals(300, latest, "Watermark should advance to active source's value");
    }

    static class CapturingWatermarkOutput implements WatermarkOutput {
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
