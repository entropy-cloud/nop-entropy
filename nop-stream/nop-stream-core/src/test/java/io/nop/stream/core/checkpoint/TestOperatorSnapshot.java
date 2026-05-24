/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.checkpoint;

import io.nop.stream.core.common.functions.SinkFunction;
import io.nop.stream.core.common.functions.source.CheckpointedSourceFunction;
import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.common.functions.sink.TwoPhaseCommitSinkFunction;
import io.nop.stream.core.common.state.MapState;
import io.nop.stream.core.common.state.MapStateDescriptor;
import io.nop.stream.core.common.state.ValueState;
import io.nop.stream.core.common.state.ValueStateDescriptor;
import io.nop.stream.core.common.state.backend.IKeyedStateBackend;
import io.nop.stream.core.common.state.backend.IStateBackend;
import io.nop.stream.core.common.state.backend.StateSnapshot;
import io.nop.stream.core.common.state.backend.memory.MemoryStateBackend;
import io.nop.stream.core.operators.*;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.test.TestOutput;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

public class TestOperatorSnapshot {

    @Test
    public void testValueStateSnapshot() throws Exception {
        IStateBackend stateBackend = new MemoryStateBackend();
        IKeyedStateBackend<String> keyedBackend = stateBackend.createKeyedStateBackend(String.class);

        AbstractStreamOperator<Long> operator = new AbstractStreamOperator<>() {
            private static final long serialVersionUID = 1L;
        };
        operator.setStateBackend(stateBackend);
        operator.setKeyedStateBackend(keyedBackend);

        ValueStateDescriptor<Long> descriptor = new ValueStateDescriptor<>("count", Long.class, 0L);
        keyedBackend.setCurrentKey("key1");
        ValueState<Long> state = keyedBackend.getState(descriptor);
        state.update(42L);

        CheckpointBarrier barrier = new CheckpointBarrier(1L, System.currentTimeMillis(), CheckpointType.CHECKPOINT);
        TestOutput<Long> output = new TestOutput<>();
        operator.setOutput(output);

        operator.processBarrier(barrier);

        OperatorSnapshotResult snapshot = operator.getLastSnapshotResult();
        assertNotNull(snapshot);
        assertFalse(snapshot.isEmpty());

        Object keyedStateObj = snapshot.getKeyedState("keyed-state");
        assertNotNull(keyedStateObj);
        assertTrue(keyedStateObj instanceof StateSnapshot);

        IKeyedStateBackend<String> restoredBackend = stateBackend.createKeyedStateBackend(String.class);
        restoredBackend.restoreState((StateSnapshot) keyedStateObj);
        ValueState<Long> restoredState = restoredBackend.getState(descriptor);
        restoredBackend.setCurrentKey("key1");
        assertEquals(Long.valueOf(42L), restoredState.value());

        keyedBackend.close();
        restoredBackend.close();
    }

    @Test
    public void testMapStateSnapshot() throws Exception {
        IStateBackend stateBackend = new MemoryStateBackend();
        IKeyedStateBackend<String> keyedBackend = stateBackend.createKeyedStateBackend(String.class);

        AbstractStreamOperator<Void> operator = new AbstractStreamOperator<>() {
            private static final long serialVersionUID = 1L;
        };
        operator.setStateBackend(stateBackend);
        operator.setKeyedStateBackend(keyedBackend);

        MapStateDescriptor<String, Integer> descriptor = new MapStateDescriptor<>("items", String.class, Integer.class);
        keyedBackend.setCurrentKey("user1");
        MapState<String, Integer> state = keyedBackend.getMapState(descriptor);
        state.put("a", 1);
        state.put("b", 2);
        state.put("c", 3);

        CheckpointBarrier barrier = new CheckpointBarrier(2L, System.currentTimeMillis(), CheckpointType.CHECKPOINT);
        TestOutput<Void> output = new TestOutput<>();
        operator.setOutput(output);

        operator.processBarrier(barrier);

        OperatorSnapshotResult snapshot = operator.getLastSnapshotResult();
        assertNotNull(snapshot);
        assertFalse(snapshot.isEmpty());

        Object keyedStateObj = snapshot.getKeyedState("keyed-state");
        assertNotNull(keyedStateObj);
        assertTrue(keyedStateObj instanceof StateSnapshot);

        IKeyedStateBackend<String> restoredBackend = stateBackend.createKeyedStateBackend(String.class);
        restoredBackend.restoreState((StateSnapshot) keyedStateObj);
        MapState<String, Integer> restoredState = restoredBackend.getMapState(descriptor);
        restoredBackend.setCurrentKey("user1");
        assertEquals(Integer.valueOf(1), restoredState.get("a"));
        assertEquals(Integer.valueOf(2), restoredState.get("b"));
        assertEquals(Integer.valueOf(3), restoredState.get("c"));

        keyedBackend.close();
        restoredBackend.close();
    }

    @Test
    public void testSourceOffsetSnapshot() throws Exception {
        AtomicLong snapshotCheckpointId = new AtomicLong(-1);

        CheckpointedSourceFunction<String> source = new CheckpointedSourceFunction<>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void run(SourceContext<String> ctx) {
            }

            @Override
            public void cancel() {
            }

            @Override
            public OperatorSnapshotResult snapshotState(long checkpointId) throws Exception {
                snapshotCheckpointId.set(checkpointId);
                return OperatorSnapshotResult.empty();
            }
        };

        StreamSourceOperator<String> sourceOperator = new StreamSourceOperator<>(source);
        TestOutput<String> output = new TestOutput<>();
        sourceOperator.setOutput(output);
        sourceOperator.open();

        CheckpointBarrier barrier = new CheckpointBarrier(5L, System.currentTimeMillis(), CheckpointType.CHECKPOINT);
        sourceOperator.injectBarrier(barrier);

        assertEquals(5L, snapshotCheckpointId.get());
    }

    @Test
    public void testSinkPreCommit() throws Exception {
        AtomicBoolean preCommitCalled = new AtomicBoolean(false);
        AtomicLong preCommitCheckpointId = new AtomicLong(-1);

        TwoPhaseCommitSinkFunction<String> sink = new TwoPhaseCommitSinkFunction<>() {
            private static final long serialVersionUID = 1L;
            private Map<Long, Object> pendingCommits;

            @Override
            public void beginTransaction() {
            }

            @Override
            public void invoke(String value) {
            }

            @Override
            public void preCommit(long checkpointId) {
                preCommitCalled.set(true);
                preCommitCheckpointId.set(checkpointId);
            }

            @Override
            public void commit(long checkpointId) {
            }

            @Override
            public void rollback() {
            }

            @Override public Map<Long, Object> getPendingCommits() { return pendingCommits; }
            @Override public void setPendingCommits(Map<Long, Object> pending) { this.pendingCommits = pending; }
        };

        StreamSinkOperator<String> sinkOperator = new StreamSinkOperator<>(sink);
        sinkOperator.open();

        CheckpointBarrier barrier = new CheckpointBarrier(10L, System.currentTimeMillis(), CheckpointType.CHECKPOINT);
        sinkOperator.processBarrier(barrier);

        assertTrue(preCommitCalled.get());
        assertEquals(10L, preCommitCheckpointId.get());
    }

    @Test
    public void testBarrierPropagatesAfterSnapshot() throws Exception {
        IStateBackend stateBackend = new MemoryStateBackend();
        IKeyedStateBackend<String> keyedBackend = stateBackend.createKeyedStateBackend(String.class);

        AtomicBoolean snapshotCalled = new AtomicBoolean(false);

        AbstractStreamOperator<String> operator = new AbstractStreamOperator<>() {
            private static final long serialVersionUID = 1L;

            @Override
            public OperatorSnapshotResult snapshotState(StateSnapshotContext context) throws Exception {
                snapshotCalled.set(true);
                return super.snapshotState(context);
            }
        };
        operator.setStateBackend(stateBackend);
        operator.setKeyedStateBackend(keyedBackend);

        ValueStateDescriptor<Long> descriptor = new ValueStateDescriptor<>("test", Long.class, 0L);
        keyedBackend.setCurrentKey("k1");
        keyedBackend.getState(descriptor).update(99L);

        TestOutput<String> output = new TestOutput<>();
        operator.setOutput(output);

        CheckpointBarrier barrier = new CheckpointBarrier(1L, System.currentTimeMillis(), CheckpointType.CHECKPOINT);
        operator.processBarrier(barrier);

        assertTrue(snapshotCalled.get());
        assertEquals(1, output.getBarriers().size());
        assertSame(barrier, output.getBarriers().get(0));

        assertNotNull(operator.getLastSnapshotResult());
        assertFalse(operator.getLastSnapshotResult().isEmpty());

        keyedBackend.close();
    }

    @Test
    public void testSnapshotContextValues() {
        StateSnapshotContext context = new StateSnapshotContext(42L, 1234567890L);
        assertEquals(42L, context.getCheckpointId());
        assertEquals(1234567890L, context.getTimestamp());
    }

    @Test
    public void testEmptyOperatorSnapshot() throws Exception {
        AbstractStreamOperator<String> operator = new AbstractStreamOperator<>() {
            private static final long serialVersionUID = 1L;
        };

        TestOutput<String> output = new TestOutput<>();
        operator.setOutput(output);

        CheckpointBarrier barrier = new CheckpointBarrier(1L, System.currentTimeMillis(), CheckpointType.CHECKPOINT);
        operator.processBarrier(barrier);

        OperatorSnapshotResult snapshot = operator.getLastSnapshotResult();
        assertNotNull(snapshot);
        assertTrue(snapshot.isEmpty());
    }
}
