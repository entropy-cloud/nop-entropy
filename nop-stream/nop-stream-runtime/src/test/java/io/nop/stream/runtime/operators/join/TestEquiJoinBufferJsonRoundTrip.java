/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy/nop-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.operators.join;

import io.nop.core.lang.json.JsonTool;
import io.nop.stream.core.checkpoint.CheckpointBarrier;
import io.nop.stream.core.checkpoint.CheckpointType;
import io.nop.stream.core.checkpoint.OperatorSnapshotResult;
import io.nop.stream.core.checkpoint.TaskLocation;
import io.nop.stream.core.checkpoint.TaskStateSnapshot;
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
import io.nop.stream.runtime.checkpoint.storage.CheckpointSerDe;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * R5 KR-7 regression pin: the equi-join working view must survive the REAL
 * durable JSON persist path ({@code CheckpointSerDe} + {@code JsonTool},
 * storageType=local round trip). Before the fix the durable (only-data-bean)
 * default threw ERR_JSON_ONLY_DATA_BEAN_IS_SERIALIZABLE at persist, and a
 * degraded config silently restored an empty working view (join state idles
 * forever while the keyed mirror holds the records).
 */
class TestEquiJoinBufferJsonRoundTrip {

    private static final TaskLocation LOC_0 = new TaskLocation("job-join", "pipeline-0", "join-vertex", 0);

    private static EquiJoinOperator<String, String> openJoin() throws Exception {
        EquiJoinOperator<String, String> op = new EquiJoinOperator<>(JoinType.INNER, null, 0L);
        op.setStateBackend(new MemoryStateBackend());
        op.setOutput(new JsonListOutput());
        op.open();
        return op;
    }

    private static void feed(EquiJoinOperator<String, String> op, boolean left, String key,
                             String payload, long ts) throws Exception {
        op.processElement(new StreamRecord<>(new JoinSideRecord<Object>(left, key, payload), ts));
    }

    @Test
    public void workingViewSurvivesDurableJsonRoundTripAndContinuesJoining() throws Exception {
        EquiJoinOperator<String, String> op1 = openJoin();
        feed(op1, true, "k", "a", 10);

        op1.processBarrier(new CheckpointBarrier(1L, System.currentTimeMillis(), CheckpointType.CHECKPOINT));
        OperatorSnapshotResult snap = op1.getLastSnapshotResult();
        assertNotNull(snap);

        // Real durable JSON persist path
        TaskStateSnapshot taskState = TaskStateSnapshot.builder(LOC_0).checkpointId(1L).build();
        for (Map.Entry<String, Object> e : snap.getOperatorStates().entrySet()) {
            taskState.putOperatorState(e.getKey(), e.getValue());
        }
        for (Map.Entry<String, Object> e : snap.getKeyedStates().entrySet()) {
            taskState.putKeyedState(e.getKey(), e.getValue());
        }
        Map<String, Object> form = CheckpointSerDe.serializeTaskStateSnapshot(taskState);
        Map<String, Object> parsed = JsonTool.parseMap(JsonTool.serialize(form, false));
        TaskStateSnapshot restoredTaskState = CheckpointSerDe.deserializeTaskStateSnapshot(parsed, LOC_0);

        OperatorSnapshotResult restored = new OperatorSnapshotResult();
        for (Map.Entry<String, Object> e : restoredTaskState.getOperatorStates().entrySet()) {
            restored.putOperatorState(e.getKey(), e.getValue());
        }
        for (Map.Entry<String, Object> e : restoredTaskState.getKeyedStates().entrySet()) {
            restored.putKeyedState(e.getKey(), e.getValue());
        }

        // Kill + restore, then the join continues with the restored buffer
        EquiJoinOperator<String, String> op2 = openJoin();
        // open() already ran (buffer live): restoreState applies the working
        // view immediately via the dual-order path
        op2.restoreState(restored);
        op2.setCurrentKey("k");
        assertEquals(1, op2.bufferedCount(), "working view must survive the durable JSON round trip");

        feed(op2, false, "k", "b", 20);
        assertEquals(List.of("J|a|b"), ((JsonListOutput) op2.getOutput()).records,
                "restored buffer joins post-restore arrival");

        // watermark trims the restored state (same contract as the in-memory pin)
        op2.processWatermark(new Watermark(100));
        assertEquals(0, op2.bufferedCount());
    }

    static final class JsonListOutput implements Output<StreamRecord<JoinMatch<String, String>>> {
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
