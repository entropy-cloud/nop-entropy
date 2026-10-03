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
import io.nop.stream.core.checkpoint.OperatorSnapshotResult;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI13 checkpoint/restore E2E (TestE2EWindowOperatorWithCheckpoint precedent level):
 * the equi-join's per-side buffers live in KEYED STATE (§十 durable channel — the
 * snapshot must carry the keyed lineage with the {@code equi-join-buffer} state
 * name, not just an operator-state object), and the join continues across the
 * boundary: a left record buffered before the checkpoint matches a right record
 * arriving after the restore. The durable matched flag survives too — a matched
 * record restored from state must NOT be completed a second time.
 */
public class TestEquiJoinWithCheckpoint {

    private static EquiJoinOperator<String, String> openJoin(JoinType type) throws Exception {
        EquiJoinOperator<String, String> op = new EquiJoinOperator<>(type, null, 0L);
        op.setStateBackend(new MemoryStateBackend());
        op.setOutput(new CheckpointListOutput());
        op.open();
        return op;
    }

    private static void feed(EquiJoinOperator<String, String> op, boolean left, String key,
                             String payload, long ts) throws Exception {
        op.processElement(new StreamRecord<>(new JoinSideRecord<Object>(left, key, payload), ts));
    }

    @Test
    public void equiJoinBuffersSurviveCheckpointRestoreAndContinueJoining() throws Exception {
        EquiJoinOperator<String, String> op1 = openJoin(JoinType.INNER);

        // left buffered before the checkpoint
        feed(op1, true, "k", "a", 10);
        assertEquals(1, op1.keyedBufferEntries(), "buffered element has a keyed durable copy");

        CheckpointBarrier barrier = new CheckpointBarrier(1L, System.currentTimeMillis(), CheckpointType.CHECKPOINT);
        op1.processBarrier(barrier);
        OperatorSnapshotResult snapshot = op1.getLastSnapshotResult();
        assertNotNull(snapshot);
        assertFalse(snapshot.getOperatorStates().isEmpty(),
                "working-view transport participates in the snapshot");
        // §十 lineage: the snapshot must carry the keyed state, not only the
        // operator-state transport object (WI12 E2E assertion form)
        boolean joinStateInLineage = false;
        for (Object stateObj : snapshot.getKeyedStates().values()) {
            if (stateObj instanceof io.nop.stream.core.common.state.backend.StateSnapshot) {
                io.nop.stream.core.common.state.backend.StateSnapshot snap =
                        (io.nop.stream.core.common.state.backend.StateSnapshot) stateObj;
                if (snap.getStates() != null && snap.getStates().keySet().stream()
                        .anyMatch(k -> k.contains(EquiJoinOperator.bufferStateName()))) {
                    joinStateInLineage = true;
                }
            }
        }
        assertTrue(joinStateInLineage,
                "snapshot must carry the equi-join keyed-state lineage");

        // restore into a fresh operator and continue joining
        EquiJoinOperator<String, String> op2 = openJoin(JoinType.INNER);
        op2.restoreState(snapshot);
        // the engine's keyed MapState reads are scoped to the CURRENT key — scope
        // to the join key to observe the restored durable entry
        op2.setCurrentKey("k");
        assertEquals(1, op2.keyedBufferEntries(),
                "restored operator sees the durable buffer entry via the keyed lineage");
        assertEquals(1, op2.bufferedCount(),
                "working view restored through the operator-state transport");

        // right arrival after restore matches the restored left — join continuity
        feed(op2, false, "k", "b", 20);
        assertEquals(List.of("J|a|b"), outs(op2), "restored buffer joins post-restore arrival");

        // watermark trims the restored state too
        op2.processWatermark(new Watermark(100));
        assertEquals(0, op2.keyedBufferEntries(), "restored entries trim at the watermark");
        assertEquals(0, op2.bufferedCount());
    }

    @Test
    public void matchedFlagSurvivesRestoreNoDoubleCompletion() throws Exception {
        EquiJoinOperator<String, String> op1 = openJoin(JoinType.LEFT);

        // pair emitted before the checkpoint — both records flagged matched
        feed(op1, true, "k", "a", 10);
        feed(op1, false, "k", "b", 20);
        assertEquals(List.of("J|a|b"), outs(op1));

        CheckpointBarrier barrier = new CheckpointBarrier(1L, System.currentTimeMillis(), CheckpointType.CHECKPOINT);
        op1.processBarrier(barrier);
        OperatorSnapshotResult snapshot = op1.getLastSnapshotResult();
        assertNotNull(snapshot);

        EquiJoinOperator<String, String> op2 = openJoin(JoinType.LEFT);
        op2.restoreState(snapshot);

        // the DURABLE matched flag survived: scope to the join key and inspect the
        // restored keyed entries directly
        op2.setCurrentKey("k");
        assertTrue(op2.keyedAnyMatched(),
                "the durable keyed entries carry matched=true after restore");

        // the restored "a" is already matched: the watermark must NOT complete it
        // again (a durable matched flag is what keeps outer semantics single-emit)
        op2.processWatermark(new Watermark(100));
        assertEquals(List.of(), outs(op2),
                "the matched record restored from state must NOT be completed a second time");
        assertEquals(0, op2.bufferedCount(), "restored entries trim at the watermark");
    }

    private static List<String> outs(EquiJoinOperator<String, String> op) {
        return ((CheckpointListOutput) op.getOutput()).records;
    }

    static final class CheckpointListOutput implements Output<StreamRecord<JoinMatch<String, String>>> {
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
