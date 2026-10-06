/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy/nop-entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.operators.join;

import io.nop.stream.core.checkpoint.CheckpointBarrier;
import io.nop.stream.core.checkpoint.CheckpointType;
import io.nop.stream.core.checkpoint.OperatorSnapshotResult;
import io.nop.stream.core.common.buffer.PerKeyOrderedBuffer;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R6 ST-02 regression pins (plan 2323): the operator-state transport handed to
 * the async persist thread must be a point-in-time frozen copy of the join
 * buffer. Post-barrier processing (a matching right arrival mutates the left
 * record's matched flag in place and grows the live buffer) must never leak
 * into the snapshot — previously it resurfaced as duplicated buffer entries
 * after restore. Reverting {@code EquiJoinOperator.snapshotState} to hand out
 * the live buffer turns these tests red.
 *
 * <p>Design-B compatibility (frozen copy is a full {@code PerKeyOrderedBuffer}):
 * the in-memory restore path and the working-view counters keep working without
 * any JSON round trip, and the {@code @type} marker stays in the transport so
 * the JSON persist path dispatches unchanged.
 */
public class TestEquiJoinBufferFreezePostBarrier {

    @Test
    public void postBarrierMutationsNeverLeakIntoOperatorStateTransport() throws Exception {
        EquiJoinOperator<String, String> op1 = openJoin();

        // left buffered before the checkpoint
        feed(op1, true, "k", "a", 10);

        CheckpointBarrier barrier = new CheckpointBarrier(1L, System.currentTimeMillis(), CheckpointType.CHECKPOINT);
        op1.processBarrier(barrier);
        OperatorSnapshotResult snapshot = op1.getLastSnapshotResult();
        assertNotNull(snapshot);
        Object transport = snapshot.getOperatorState(EquiJoinOperator.bufferStateName());
        assertTrue(transport instanceof PerKeyOrderedBuffer,
                "operator-state transport must remain a typed PerKeyOrderedBuffer (design B)");
        // The persist thread dispatches PerKeyOrderedBuffer -> toSerializableForm()
        // before JSON, so freeze the pin at that exact boundary (the raw buffer
        // object itself is not JSON-serializable under the only-data-bean gate).
        String before = io.nop.core.lang.json.JsonTool.serialize(
                ((PerKeyOrderedBuffer<?, ?>) transport).toSerializableForm(), false);
        assertTrue(before.contains("\"@type\":\"PerKeyOrderedBuffer\""),
                "JSON format marker must be unchanged for the persist path");

        // Post-barrier work: a matching right arrival mutates the live buffer in
        // place (matched flag on the left record) and joins the pair.
        feed(op1, false, "k", "b", 20);
        assertEquals(List.of("J|a|b"), outs(op1), "post-barrier join works on the live buffer");

        // The snapshot handed to the persist thread must be untouched.
        String after = io.nop.core.lang.json.JsonTool.serialize(
                ((PerKeyOrderedBuffer<?, ?>) snapshot
                        .getOperatorState(EquiJoinOperator.bufferStateName())).toSerializableForm(),
                false);
        assertEquals(before, after, "post-barrier mutation leaked into the operator-state transport");
        assertTrue(before.contains("\"a\"") && !after.contains("\"b\""),
                "snapshot must hold only the pre-barrier buffer content");
    }

    @Test
    public void frozenTransportRestoresWorkingViewWithoutJsonRoundTrip() throws Exception {
        EquiJoinOperator<String, String> op1 = openJoin();
        feed(op1, true, "k", "a", 10);
        CheckpointBarrier barrier = new CheckpointBarrier(1L, System.currentTimeMillis(), CheckpointType.CHECKPOINT);
        op1.processBarrier(barrier);
        OperatorSnapshotResult snapshot = op1.getLastSnapshotResult();

        // Post-barrier growth of the live buffer must not reach the snapshot.
        feed(op1, true, "k", "c", 30);

        EquiJoinOperator<String, String> op2 = openJoin();
        op2.restoreState(snapshot);
        assertEquals(1, op2.bufferedCount(),
                "working view must restore exactly the pre-barrier rows (no duplication, no loss)");
        // Join continuity across the boundary: the restored left matches a post-restore right.
        feed(op2, false, "k", "b", 20);
        assertEquals(List.of("J|a|b"), outs(op2));
    }

    private static EquiJoinOperator<String, String> openJoin() throws Exception {
        EquiJoinOperator<String, String> op = new EquiJoinOperator<>(JoinType.INNER, null, 0L);
        op.setStateBackend(new MemoryStateBackend());
        op.setOutput(new FreezeListOutput());
        op.open();
        return op;
    }

    private static void feed(EquiJoinOperator<String, String> op, boolean left, String key,
                             String payload, long ts) throws Exception {
        op.processElement(new StreamRecord<>(new JoinSideRecord<Object>(left, key, payload), ts));
    }

    private static List<String> outs(EquiJoinOperator<String, String> op) {
        return ((FreezeListOutput) op.getOutput()).records;
    }

    static final class FreezeListOutput implements Output<StreamRecord<JoinMatch<String, String>>> {
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
