/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.operators.join;

import io.nop.stream.core.checkpoint.CheckpointBarrier;
import io.nop.stream.core.checkpoint.CheckpointType;
import io.nop.stream.core.common.state.backend.IStateBackend;
import io.nop.stream.core.common.state.backend.memory.MemoryStateBackend;
import io.nop.stream.core.model.JoinMatch;
import io.nop.stream.core.model.JoinSideRecord;
import io.nop.stream.core.model.JoinType;
import io.nop.stream.core.operators.Output;
import io.nop.stream.core.operators.join.EquiJoinOperator;
import io.nop.stream.core.streamrecord.LatencyMarker;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.streamrecord.watermark.Watermark;
import io.nop.stream.core.streamrecord.watermark.WatermarkStatus;
import io.nop.stream.core.util.OutputTag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * WI13: the window equi-join form (join-operator.md §4) — cross-side pairs match
 * only within the same tumbling event-time window (duration 10 in these scenarios);
 * the window fires at {@code windowEnd + timeout} (the D9-released lateness grace):
 * the completion side's never-matched records complete there, and the window's
 * entries drop on both sides (bounded state). Records arriving after their window
 * fired are dropped on sight — no state growth, fire-and-update semantics (D9).
 */
public class TestEquiJoinWindowTimeout {

    private static EquiJoinOperator<String, String> join(JoinType type, long duration, long timeout) throws Exception {
        EquiJoinOperator<String, String> op = new EquiJoinOperator<>(type, duration, timeout);
        IStateBackend stateBackend = new MemoryStateBackend();
        op.setStateBackend(stateBackend);
        op.setOutput(new WindowListOutput());
        op.open();
        return op;
    }

    private static void feed(EquiJoinOperator<String, String> op, boolean left, String key,
                             String payload, long ts) throws Exception {
        op.processElement(new StreamRecord<>(new JoinSideRecord<Object>(left, key, payload), ts));
    }

    private static void wm(EquiJoinOperator<String, String> op, long ts) throws Exception {
        op.processWatermark(new Watermark(ts));
    }

    private static List<String> outs(EquiJoinOperator<String, String> op) {
        return ((WindowListOutput) op.getOutput()).records;
    }

    @Test
    public void sameWindowPairsEagerlyAndDropsAtFire() throws Exception {
        EquiJoinOperator<String, String> op = join(JoinType.INNER, 10L, 0L);
        feed(op, true, "k", "a", 1);   // window [0,10)
        feed(op, false, "k", "b", 5);  // same window
        assertEquals(List.of("J|a|b"), outs(op), "same-window cross-side pair emits eagerly");
        wm(op, 30); // fires [0,10)
        assertEquals(List.of("J|a|b"), outs(op), "INNER adds no completion at fire");
        assertEquals(0, op.bufferedCount(), "fired window drops its entries");
        assertEquals(0, op.keyedBufferEntries(), "keyed durable copy drops in lockstep");
    }

    @Test
    public void crossWindowRecordsNeverMatch() throws Exception {
        EquiJoinOperator<String, String> op = join(JoinType.INNER, 10L, 0L);
        feed(op, true, "k", "a", 1);   // window [0,10)
        feed(op, false, "k", "b", 15); // window [10,20) — different window, same key
        assertEquals(List.of(), outs(op), "cross-window records never match, same key notwithstanding");
        wm(op, 40); // fires both windows
        assertEquals(List.of(), outs(op));
        assertEquals(0, op.bufferedCount(), "both windows dropped after firing");
    }

    @Test
    public void completionDeliveredAtWindowEndPlusTimeout() throws Exception {
        // window [0,10) fires when watermark >= 10 + 5 = 15
        EquiJoinOperator<String, String> op = join(JoinType.LEFT, 10L, 5L);
        feed(op, true, "k", "a", 1);
        wm(op, 12);
        assertEquals(List.of(), outs(op), "watermark 12 < windowEnd+timeout — not fired yet");
        assertEquals(1, op.bufferedCount(), "entry retained inside the timeout grace");
        wm(op, 15);
        assertEquals(List.of("J|a|null"), outs(op), "unmatched left completes at fire");
        assertEquals(0, op.bufferedCount());
        assertEquals(0, op.keyedBufferEntries(), "durable entry removed with the window");
    }

    @Test
    public void lateRecordDroppedOnSight() throws Exception {
        EquiJoinOperator<String, String> op = join(JoinType.LEFT, 10L, 0L);
        feed(op, true, "k", "a", 1);
        wm(op, 30); // window [0,10) fired and dropped; "a" completed (LEFT)
        assertEquals(List.of("J|a|null"), outs(op));
        // a late record for the already-fired window: dropped, no completion, no state
        feed(op, true, "k", "late", 5);
        wm(op, 40);
        assertEquals(List.of("J|a|null"), outs(op), "late record produces no second completion");
        assertEquals(0, op.bufferedCount(), "late record left no state behind");
    }

    /** output buffer with no E2E env — collects formatted JoinMatch values. */
    static final class WindowListOutput implements Output<StreamRecord<JoinMatch<String, String>>> {
        final List<String> records = new ArrayList<>();

        @Override
        public void collect(StreamRecord<JoinMatch<String, String>> record) {
            records.add(record.getValue().toString());
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
}
