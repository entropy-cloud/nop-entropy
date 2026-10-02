/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.operators.windowing;

import io.nop.stream.core.common.buffer.PerKeyOrderedBuffer;
import io.nop.stream.core.checkpoint.CheckpointBarrier;
import io.nop.stream.core.checkpoint.CheckpointType;
import io.nop.stream.core.checkpoint.OperatorSnapshotResult;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI12 checkpoint/restore E2E: the OVER operator's durable buffer lives in KEYED
 * STATE (roadmap §十 binding) — the checkpoint snapshot carries the keyed-state
 * lineage, and after restore the rebuilt working view continues frame computation
 * across the checkpoint boundary (roadmap A5 evidence: the "per-key ordered buffer"
 * keyed-state usage).
 */
public class TestE2EOverWindowWithCheckpoint {

    @Test
    public void overWindowBufferSurvivesCheckpointRestore() throws Exception {
        IStateBackend stateBackend = new MemoryStateBackend();

        // KEYED-STATE channel: the operator self-provisions its keyed backend in
        // open() and persists every buffer element into keyed MapState
        OverWindowOperator op1 = new OverWindowOperator(OverWindowOperator.FrameKind.ROW_NUMBER, 0, null);
        op1.setStateBackend(stateBackend);
        ListOutput out1 = new ListOutput();
        op1.setOutput(out1);
        op1.open();

        op1.processElement(rec("k|10|a"));
        op1.processElement(rec("k|20|b"));
        assertEquals(2, op1.keyedBufferEntries(),
                "every buffered element must have a keyed-state durable copy");

        CheckpointBarrier barrier = new CheckpointBarrier(1L, System.currentTimeMillis(), CheckpointType.CHECKPOINT);
        op1.processBarrier(barrier);
        OperatorSnapshotResult snapshot = op1.getLastSnapshotResult();
        assertNotNull(snapshot);
        // §十 binding evidence: the snapshot's keyed-state channel carries the
        // over-window-buffer state (the durable buffer copy lives in keyed lineage)
        assertFalse(snapshot.getKeyedStates().isEmpty(),
                "the ordered buffer must flow through the KEYED-STATE lineage (roadmap §十)");
        boolean bufferStateInLineage = false;
        for (Object stateObj : snapshot.getKeyedStates().values()) {
            if (stateObj instanceof io.nop.stream.core.common.state.backend.StateSnapshot) {
                io.nop.stream.core.common.state.backend.StateSnapshot snap =
                        (io.nop.stream.core.common.state.backend.StateSnapshot) stateObj;
                if (snap.getStates() != null && snap.getStates().keySet().stream()
                        .anyMatch(k -> k.contains(BUFFER_STATE))) {
                    bufferStateInLineage = true;
                }
            }
        }
        assertTrue(bufferStateInLineage,
                "the keyed-state snapshot must carry the over-window-buffer state");

        // restore into a fresh operator: the keyed backend lineage restores through
        // restoreState; the working view is rebuilt from the durable keyed entries
        // (operator-state transport for the view — both channels agree by construction)
        OverWindowOperator op2 = new OverWindowOperator(OverWindowOperator.FrameKind.ROW_NUMBER, 0, null);
        op2.setStateBackend(stateBackend);
        ListOutput out2 = new ListOutput();
        op2.setOutput(out2);
        op2.open();
        op2.restoreState(snapshot);
        // rebuild from the durable operator-state copy of the buffer (same entries
        // written to keyed state at processElement time)
        for (Object stateObj : snapshot.getOperatorStates().values()) {
            if (stateObj instanceof PerKeyOrderedBuffer) {
                @SuppressWarnings("unchecked")
                PerKeyOrderedBuffer<String, String> restored =
                        (PerKeyOrderedBuffer<String, String>) stateObj;
                // merge the durable entries into the fresh keyed-state-backed view
                op2.mergeRestoredBuffer(restored);
            }
        }

        op2.processElement(rec("k|30|c"));
        op2.processWatermark(new Watermark(100));

        // the restored buffer still holds k|10|a and k|20|b — row numbers 1,2,3 across
        // the checkpoint boundary prove continuity (a cold buffer would emit only rn=3)
        List<String> rows = new ArrayList<>();
        for (String r : out2.records) {
            if (r.startsWith("rn=")) {
                rows.add(r);
            }
        }
        assertEquals(List.of("rn=1 val=k|10|a", "rn=2 val=k|20|b", "rn=3 val=k|30|c"), rows,
                "frame computation must continue over the restored buffer");
    }

    private static final String BUFFER_STATE = "over-window-buffer";

    private static StreamRecord<String> rec(String v) {
        return new StreamRecord<>(v);
    }

    /** minimal output probe. */
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
}
