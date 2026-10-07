/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.execution;

import io.nop.stream.core.streamrecord.watermark.Watermark;
import io.nop.stream.core.streamrecord.watermark.WatermarkStatus;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * WI16 (plan 2337): the intra-gate cascading-lag observable — lag = max(active
 * channel watermarks) − merged, both sides sharing the idle-exclusion semantics.
 * A rising lag is exactly "some channel races ahead while another pins the min".
 */
class TestInputGateWatermarkLag {

    private static void writeAll(ResultPartition p, Object... elements) throws InterruptedException {
        for (Object e : elements) {
            if (e instanceof Watermark) {
                p.write((Watermark) e);
            } else if (e instanceof WatermarkStatus) {
                p.write((WatermarkStatus) e);
            }
        }
    }

    private static void drainAll(InputGate gate) throws InterruptedException {
        List<Object> sink = new ArrayList<>();
        while (true) {
            var e = gate.read();
            if (!e.isPresent()) {
                return;
            }
            sink.add(e.get());
        }
    }

    @Test
    void lagTracksFastestActiveChannelAgainstMerged() throws Exception {
        ResultPartition p0 = new ResultPartition();
        ResultPartition p1 = new ResultPartition();
        InputGate gate = new InputGate(Arrays.asList(new InputChannel(p0), new InputChannel(p1)), null, true);

        writeAll(p0, new Watermark(50));
        writeAll(p1, new Watermark(100));
        drainAll(gate);
        // merged = min(50,100) = 50; max active = 100 → lag 50
        assertEquals(50L, gate.getMaxActiveChannelWatermark() - gate.getCurrentWatermark());

        // the slow channel advances → lag closes to 0
        writeAll(p0, new Watermark(100));
        drainAll(gate);
        assertEquals(0L, gate.getMaxActiveChannelWatermark() - gate.getCurrentWatermark());
    }

    /** Idle channels are excluded from BOTH sides of the lag (same semantics as the merge). */
    @Test
    void lagExcludesIdleChannelsOnBothSides() throws Exception {
        ResultPartition p0 = new ResultPartition();
        ResultPartition p1 = new ResultPartition();
        InputGate gate = new InputGate(Arrays.asList(new InputChannel(p0), new InputChannel(p1)), null, true);

        writeAll(p0, new Watermark(10));
        writeAll(p1, new Watermark(200));
        drainAll(gate);
        assertEquals(190L, gate.getMaxActiveChannelWatermark() - gate.getCurrentWatermark());

        // the fast channel goes idle: both max and merged now read the active set only
        writeAll(p1, WatermarkStatus.IDLE);
        drainAll(gate);
        assertEquals(10L, gate.getCurrentWatermark());
        assertEquals(10L, gate.getMaxActiveChannelWatermark());
        assertEquals(0L, gate.getMaxActiveChannelWatermark() - gate.getCurrentWatermark());
    }

    /** Before the first watermark both getters read MIN_VALUE: the gauge layer maps that to NaN. */
    @Test
    void beforeFirstWatermarkReadsMinValue() {
        ResultPartition p0 = new ResultPartition();
        InputGate gate = new InputGate(new InputChannel(p0), null);
        assertEquals(Long.MIN_VALUE, gate.getCurrentWatermark());
        assertEquals(Long.MIN_VALUE, gate.getMaxActiveChannelWatermark());
    }
}
