/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:   https://www.zhihu.com/people/canonical-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.execution;

import io.nop.stream.core.streamrecord.StreamElement;
import io.nop.stream.core.streamrecord.watermark.Watermark;
import io.nop.stream.core.streamrecord.watermark.WatermarkStatus;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AR-9 component proofs (plan 1326-2 Phase 4): the InputGate merge implements
 * Flink {@code StatusWatermarkValve} semantics — idle channels are excluded
 * from the min watermark merge (a silent upstream subtask no longer pins the
 * downstream event time), IDLE/ACTIVE transitions are forwarded to the chain.
 */
class TestInputGateWatermarkIdleness {

    private static void writeAll(ResultPartition p, Object... elements) throws InterruptedException {
        for (Object e : elements) {
            if (e instanceof Watermark) {
                p.write((Watermark) e);
            } else if (e instanceof WatermarkStatus) {
                p.write((WatermarkStatus) e);
            }
        }
    }

    /**
     * Partial idle: channel 0 (watermark 50) goes idle while channel 1 is at
     * 100 — the merged watermark must advance to 100 immediately (the idle
     * channel was holding the min), and further watermarks on the active
     * channel keep advancing the merge. Pre-fix, the merge stayed pinned at 50
     * forever.
     */
    @Test
    void testIdleChannelNoLongerPinsMinWatermark() throws Exception {
        ResultPartition p0 = new ResultPartition();
        ResultPartition p1 = new ResultPartition();
        InputGate gate = new InputGate(Arrays.asList(new InputChannel(p0), new InputChannel(p1)), null, true);

        List<Object> downstream = new ArrayList<>();

        writeAll(p0, new Watermark(50));
        writeAll(p1, new Watermark(100));
        // merged = min(50, 100) = 50
        drainOne(gate, downstream);
        assertEquals(new Watermark(50), downstream.get(downstream.size() - 1));
        assertEquals(50L, gate.getCurrentWatermark());

        // channel 0 goes idle → merged advances to 100 (active channels only)
        writeAll(p0, WatermarkStatus.IDLE);
        drainOne(gate, downstream);
        assertEquals(new Watermark(100), downstream.get(downstream.size() - 1),
                "idle channel excluded from min: merged watermark must advance to 100");
        assertEquals(100L, gate.getCurrentWatermark(),
                "getCurrentWatermark must exclude idle channels");

        // active channel advances further → merged follows
        writeAll(p1, new Watermark(300));
        drainOne(gate, downstream);
        assertEquals(new Watermark(300), downstream.get(downstream.size() - 1),
                "further active-channel watermarks keep advancing the merge "
                        + "(pre-fix: pinned at the idle channel's stale 50 forever)");
        assertEquals(300L, gate.getCurrentWatermark());

        p0.close();
        p1.close();
    }

    /**
     * All channels idle → the IDLE status itself is forwarded downstream (the
     * next task boundary can propagate it further); a reactivation forwards
     * ACTIVE and re-enables watermark emission.
     */
    @Test
    void testAllIdleForwardsIdleStatusAndReactivationForwardsActive() throws Exception {
        ResultPartition p0 = new ResultPartition();
        ResultPartition p1 = new ResultPartition();
        InputGate gate = new InputGate(Arrays.asList(new InputChannel(p0), new InputChannel(p1)), null, true);

        List<Object> downstream = new ArrayList<>();

        writeAll(p0, new Watermark(10));
        writeAll(p1, new Watermark(20));
        drainOne(gate, downstream);
        assertEquals(new Watermark(10), downstream.get(downstream.size() - 1));

        writeAll(p0, WatermarkStatus.IDLE); // partial
        drainOne(gate, downstream);
        assertEquals(new Watermark(20), downstream.get(downstream.size() - 1),
                "partial idle advances the merge to the active channel's watermark");

        writeAll(p1, WatermarkStatus.IDLE); // all idle → forward IDLE itself
        drainOne(gate, downstream);
        assertEquals(WatermarkStatus.IDLE, downstream.get(downstream.size() - 1),
                "all-channels-idle must forward the IDLE status to the chain");
        assertTrue(gate.isAllChannelsIdle());

        writeAll(p1, WatermarkStatus.ACTIVE); // reactivation → forward ACTIVE
        drainOne(gate, downstream);
        assertEquals(WatermarkStatus.ACTIVE, downstream.get(downstream.size() - 1),
                "a channel reactivation must forward the ACTIVE status to the chain");
        assertFalse(gate.isAllChannelsIdle());

        // reactivated channel drives the merge again
        writeAll(p1, new Watermark(500));
        drainOne(gate, downstream);
        assertEquals(new Watermark(500), downstream.get(downstream.size() - 1),
                "after reactivation the channel's watermarks drive the merge again");

        p0.close();
        p1.close();
    }

    /**
     * Single-channel gate: the status passes through to the chain (it is the
     * cross-task idleness carrier for 1:1 edges) and idleness is tracked.
     */
    @Test
    void testSingleChannelStatusPassesThrough() throws Exception {
        ResultPartition p0 = new ResultPartition();
        InputGate gate = new InputGate(new InputChannel(p0), null);

        writeAll(p0, new Watermark(42));
        writeAll(p0, WatermarkStatus.IDLE);
        writeAll(p0, WatermarkStatus.ACTIVE);
        p0.close();

        List<Object> seen = new ArrayList<>();
        while (true) {
            Optional<StreamElement> e = gate.read();
            if (!e.isPresent()) {
                if (gate.isAllFinished()) {
                    break;
                }
                continue;
            }
            if (e.get().isWatermark()) {
                seen.add(e.get().asWatermark());
            } else if (e.get().isWatermarkStatus()) {
                seen.add(e.get().asWatermarkStatus());
            }
        }
        assertEquals(Arrays.asList(new Watermark(42), WatermarkStatus.IDLE, WatermarkStatus.ACTIVE), seen,
                "single-channel gate forwards watermark status elements as-is "
                        + "(the cross-task idleness carrier)");
    }

    /** Reads elements until one data-plane (non-record) element is observed or timeout. */
    private static void drainOne(InputGate gate, List<Object> sink) throws Exception {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            Optional<StreamElement> e = gate.read();
            if (!e.isPresent()) {
                continue;
            }
            if (e.get().isWatermark()) {
                sink.add(e.get().asWatermark());
                return;
            }
            if (e.get().isWatermarkStatus()) {
                sink.add(e.get().asWatermarkStatus());
                return;
            }
        }
        throw new AssertionError("no watermark/status element observed within timeout");
    }
}
