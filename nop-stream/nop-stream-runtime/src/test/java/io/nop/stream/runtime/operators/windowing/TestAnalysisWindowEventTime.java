/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.operators.windowing;

import io.nop.stream.core.checkpoint.CheckpointBarrier;
import io.nop.stream.core.checkpoint.CheckpointType;
import io.nop.stream.core.common.state.backend.IStateBackend;
import io.nop.stream.core.common.state.backend.memory.MemoryStateBackend;
import io.nop.stream.core.operators.Output;
import io.nop.stream.core.streamrecord.LatencyMarker;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.streamrecord.watermark.Watermark;
import io.nop.stream.core.streamrecord.watermark.WatermarkStatus;
import io.nop.stream.core.util.OutputTag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI12: the analysis window (OVER) operator under event-time semantics (D6=(a)) —
 * roadmap-named test class. Discriminating scenarios: out-of-order elements are
 * sorted by the per-key buffer before frame evaluation (arrival order ≠ event-time
 * order); ROW_NUMBER yields 1-based event-time-ordered numbering; the sliding sum
 * evaluates over the declared frame with history.
 */
public class TestAnalysisWindowEventTime {

    private static StreamRecord<String> rec(String v) {
        return new StreamRecord<>(v);
    }

    static final class ListOutput implements Output<StreamRecord<String>> {
        final List<String> records = new ArrayList<>();

        @Override
        public void collect(StreamRecord<String> record) {
            records.add(record.getValue());
        }

        @Override
        public <X> void collect(OutputTag<X> outputTag, StreamRecord<X> record) {
        }

        @Override
        public void emitWatermark(Watermark mark) {
        }

        @Override
        public void emitWatermarkStatus(WatermarkStatus status) {
        }

        @Override
        public void emitLatencyMarker(LatencyMarker latencyMarker) {
        }

        @Override
        public void emitBarrier(CheckpointBarrier barrier) {
        }

        @Override
        public void close() {
        }
    }

    private static List<String> filtered(ListOutput out, String prefix) {
        List<String> r = new ArrayList<>();
        for (String s : out.records) {
            if (s.startsWith(prefix)) {
                r.add(s);
            }
        }
        return r;
    }

    @Test
    public void rowNumberEmitsOrderedPerKeyNumbering() throws Exception {
        OverWindowOperator op = new OverWindowOperator(
                OverWindowOperator.FrameKind.ROW_NUMBER, 0, null);
        IStateBackend stateBackend = new MemoryStateBackend();
        op.setStateBackend(stateBackend);
        ListOutput out = new ListOutput();
        op.setOutput(out);
        op.open();

        // out-of-order arrival: ts 30 before ts 10
        op.processElement(rec("k|30|c"));
        op.processElement(rec("k|10|a"));
        op.processElement(rec("k|20|b"));
        op.processWatermark(new Watermark(100));

        // ROW_NUMBER over the event-time-ordered buffer: 1=a(10) 2=b(20) 3=c(30)
        // (arrival order was c,a,b — the buffer sorted them)
        assertEquals(List.of("rn=1 val=k|10|a", "rn=2 val=k|20|b", "rn=3 val=k|30|c"),
                filtered(out, "rn="), "row numbers follow event-time order, not arrival order");
        assertEquals(0, op.sortedView("k").size(), "ROW_NUMBER consumes the frame at the watermark");
    }

    @Test
    public void slidingSumEmitsRunningFrameAggregate() throws Exception {
        OverWindowOperator op = new OverWindowOperator(
                OverWindowOperator.FrameKind.FRAME_SLIDING_AGG, 2, "sum");
        IStateBackend stateBackend = new MemoryStateBackend();
        op.setStateBackend(stateBackend);
        ListOutput out = new ListOutput();
        op.setOutput(out);
        op.open();

        op.processElement(rec("k|10|1"));
        op.processWatermark(new Watermark(10));
        op.processElement(rec("k|20|2"));
        op.processWatermark(new Watermark(20));
        op.processElement(rec("k|30|3"));
        op.processWatermark(new Watermark(30));

        // frame = last 2 elements: [1]→1, [1,2]→3, [2,3]→5 — history retained across
        // watermarks (trimToCount keeps the newest frameSize elements)
        assertEquals(List.of("agg=1 n=1", "agg=3 n=2", "agg=5 n=2"),
                filtered(out, "agg="), "sliding sum evaluates over the declared frame");
    }

    @Test
    public void keysAreIsolatedInFrameEvaluation() throws Exception {
        OverWindowOperator op = new OverWindowOperator(
                OverWindowOperator.FrameKind.ROW_NUMBER, 0, null);
        IStateBackend stateBackend = new MemoryStateBackend();
        op.setStateBackend(stateBackend);
        ListOutput out = new ListOutput();
        op.setOutput(out);
        op.open();

        op.processElement(rec("a|10|a1"));
        op.processElement(rec("b|20|b1"));
        op.processWatermark(new Watermark(100));

        // each key's frame numbering restarts — keys never share the buffer
        List<String> rn = filtered(out, "rn=");
        assertEquals(List.of("rn=1 val=a|10|a1", "rn=1 val=b|20|b1"), rn,
                "per-key numbering must restart per key");
        assertTrue(op.sortedView("a").isEmpty() && op.sortedView("b").isEmpty(),
                "frames consumed at the watermark");
    }
}
