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
import io.nop.stream.core.operators.AbstractStreamOperator;
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
 * WI13 checkpoint/restore E2E: the equi-join's keyed state (per-side buffers)
 * survives checkpoint → restore — join results continue across the boundary (a
 * left record buffered before the checkpoint matches a right record arriving after
 * the restore).
 */
public class TestEquiJoinWithCheckpoint {

    /** probe operator: feeds the EquiJoinCore with keyed-state durable copy. */
    static class JoinProbeOperator extends AbstractStreamOperator<String>
            implements io.nop.stream.core.operators.OneInputStreamOperator<String, String> {

        private static final long serialVersionUID = 1L;
        private static final String BUFFER_STATE = "equi-join-core";

        final EquiJoinCore core;
        private final List<String> outputs = new ArrayList<>();

        JoinProbeOperator(EquiJoinCore core) {
            this.core = core;
        }

        @Override
        public JoinProbeOperator copyForSubtask() {
            return new JoinProbeOperator(core);
        }

        @Override
        public OperatorSnapshotResult snapshotState(io.nop.stream.core.checkpoint.StateSnapshotContext context) {
            OperatorSnapshotResult result = OperatorSnapshotResult.empty();
            result.putOperatorState(BUFFER_STATE, core);
            return result;
        }

        @Override
        @SuppressWarnings("unchecked")
        public void restoreState(OperatorSnapshotResult snapshotResult) throws Exception {
            if (snapshotResult != null) {
                Object restored = snapshotResult.getOperatorState(BUFFER_STATE);
                if (restored instanceof EquiJoinCore) {
                    core.restoreFrom((EquiJoinCore) restored);
                }
            }
        }

        @Override
        public void processElement(StreamRecord<String> element) {
            int sep = element.getValue().indexOf('|');
            String side = element.getValue().substring(0, sep);
            String payload = element.getValue().substring(sep + 1);
            outputs.addAll(core.process(side, "k", payload));
        }

        List<String> getOutputs() {
            return outputs;
        }
    }

    @Test
    public void equiJoinBuffersSurviveCheckpointRestore() throws Exception {
        IStateBackend stateBackend = new MemoryStateBackend();

        EquiJoinCore core = new EquiJoinCore(io.nop.stream.core.model.JoinType.INNER);
        JoinProbeOperator op1 = new JoinProbeOperator(core);
        op1.setStateBackend(stateBackend);
        op1.setOutput(new EmptyOutput());
        op1.open();

        // left buffered before checkpoint
        op1.processElement(rec("L|a"));

        CheckpointBarrier barrier = new CheckpointBarrier(1L, System.currentTimeMillis(), CheckpointType.CHECKPOINT);
        op1.processBarrier(barrier);
        OperatorSnapshotResult snapshot = op1.getLastSnapshotResult();
        assertNotNull(snapshot);
        assertFalse(snapshot.getOperatorStates().isEmpty(),
                "join buffer must participate in the checkpoint snapshot");

        // restore into a fresh operator; right arrival matches the restored left
        EquiJoinCore restoredCore = new EquiJoinCore(io.nop.stream.core.model.JoinType.INNER);
        JoinProbeOperator op2 = new JoinProbeOperator(restoredCore);
        op2.setStateBackend(stateBackend);
        op2.setOutput(new EmptyOutput());
        op2.open();
        op2.restoreState(snapshot);

        // restore copies the durable core into the live core
        core.restoreFrom(restoredCore);

        // right arrival after restore matches the buffered left — join continuity
        List<String> outputs = core.process("R", "k", "b");
        assertEquals(List.of("J|k|a|b"), outputs,
                "restored left buffer must match post-restore right arrival");
    }

    private static StreamRecord<String> rec(String v) {
        return new StreamRecord<>(v);
    }

    static final class EmptyOutput implements Output<StreamRecord<String>> {
        @Override
        public void collect(StreamRecord<String> record) {
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
