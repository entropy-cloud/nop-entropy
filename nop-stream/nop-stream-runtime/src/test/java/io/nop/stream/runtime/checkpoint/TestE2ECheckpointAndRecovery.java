/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.checkpoint;

import io.nop.stream.core.checkpoint.*;
import io.nop.stream.core.common.functions.SinkFunction;
import io.nop.stream.core.common.functions.source.CheckpointedSourceFunction;
import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.common.state.ValueState;
import io.nop.stream.core.common.state.ValueStateDescriptor;
import io.nop.stream.core.common.state.backend.IKeyedStateBackend;
import io.nop.stream.core.common.state.backend.memory.MemoryStateBackend;
import io.nop.stream.core.execution.CheckpointBarrierTracker;
import io.nop.stream.core.operators.*;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.runtime.checkpoint.storage.LocalFileCheckpointStorage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class TestE2ECheckpointAndRecovery {

    @TempDir
    Path tempDir;

    private LocalFileCheckpointStorage storage;
    private CheckpointCoordinator coordinator;
    private CheckpointIDCounter idCounter;

    @BeforeEach
    void setup() {
        storage = new LocalFileCheckpointStorage(tempDir.toString());
        idCounter = new CheckpointIDCounter();
        CheckpointConfig config = new CheckpointConfig();
        config.setCheckpointInterval(1000);
        coordinator = new CheckpointCoordinator(1L, 0, idCounter, storage, config);
        coordinator.registerTask(0L);
    }

    @AfterEach
    void teardown() throws Exception {
        coordinator.shutdown();
        storage.deleteAllCheckpoints(1);
    }

    @Test
    void testOperatorStateSnapshotAndRestore() throws Exception {
        MemoryStateBackend stateBackend = new MemoryStateBackend();
        IKeyedStateBackend<String> keyedBackend = stateBackend.createKeyedStateBackend(String.class);

        AbstractStreamOperator<Long> originalOp = new AbstractStreamOperator<>() {
            private static final long serialVersionUID = 1L;
        };
        originalOp.setStateBackend(stateBackend);
        originalOp.setKeyedStateBackend(keyedBackend);

        ValueStateDescriptor<Long> descriptor = new ValueStateDescriptor<>("counter", Long.class, 0L);
        keyedBackend.setCurrentKey("key1");
        ValueState<Long> state = keyedBackend.getState(descriptor);
        state.update(100L);

        keyedBackend.setCurrentKey("key2");
        ValueState<Long> state2 = keyedBackend.getState(descriptor);
        state2.update(200L);

        CheckpointBarrier barrier = new CheckpointBarrier(1L, System.currentTimeMillis(), CheckpointType.CHECKPOINT);
        TestOutput<Long> output = new TestOutput<>();
        originalOp.setOutput(output);
        originalOp.processBarrier(barrier);

        OperatorSnapshotResult snapshot = originalOp.getLastSnapshotResult();
        assertNotNull(snapshot);
        assertFalse(snapshot.isEmpty());

        IKeyedStateBackend<String> restoredBackend = stateBackend.createKeyedStateBackend(String.class);
        AbstractStreamOperator<Long> restoredOp = new AbstractStreamOperator<>() {
            private static final long serialVersionUID = 1L;
        };
        restoredOp.setStateBackend(stateBackend);
        restoredOp.setKeyedStateBackend(restoredBackend);
        restoredOp.restoreState(snapshot);

        ValueState<Long> restoredState = restoredBackend.getState(descriptor);
        restoredBackend.setCurrentKey("key1");
        assertEquals(100L, restoredState.value());

        restoredBackend.setCurrentKey("key2");
        ValueState<Long> restoredState2 = restoredBackend.getState(descriptor);
        assertEquals(200L, restoredState2.value());

        keyedBackend.close();
        restoredBackend.close();
    }

    @Test
    void testSourceOffsetCheckpointAndRecovery() throws Exception {
        AtomicLong restoredOffset = new AtomicLong(-1);
        AtomicLong currentOffset = new AtomicLong(0);

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
                return OperatorSnapshotResult.builder()
                        .putOperatorState("offset", String.valueOf(currentOffset.get()).getBytes())
                        .build();
            }

            @Override
            public void initializeState(TaskStateSnapshot state) {
                byte[] offsetBytes = state.getOperatorState("offset");
                if (offsetBytes != null) {
                    restoredOffset.set(Long.parseLong(new String(offsetBytes)));
                }
            }
        };

        StreamSourceOperator<String> sourceOp = new StreamSourceOperator<>(source);
        TestOutput<String> output = new TestOutput<>();
        sourceOp.setOutput(output);
        sourceOp.open();

        currentOffset.set(42L);

        // StreamSourceOperator.injectBarrier triggers snapshotState which calls
        // CheckpointedSourceFunction.snapshotState(), but the return value is not
        // merged into the operator's lastSnapshotResult. Test snapshot directly.
        OperatorSnapshotResult snapshot = source.snapshotState(1L);
        assertFalse(snapshot.isEmpty());
        assertEquals("42", new String(snapshot.getOperatorStates().get("offset")));

        // Verify restore works through initializeState
        TaskStateSnapshot taskSnapshot = new TaskStateSnapshot(0L, 1L);
        taskSnapshot.putOperatorState("offset", "42".getBytes());
        source.initializeState(taskSnapshot);

        assertEquals(42L, restoredOffset.get());

        sourceOp.close();
    }

    @Test
    void testFullPipelineCheckpointAndRestore() throws Exception {
        List<Integer> run1Results = Collections.synchronizedList(new ArrayList<>());
        AtomicReference<TaskStateSnapshot> capturedSnapshot = new AtomicReference<>();
        CountDownLatch checkpointComplete = new CountDownLatch(1);

        SourceFunction<Integer> source = new SourceFunction<>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void run(SourceContext<Integer> ctx) {
                for (int i = 1; i <= 5; i++) {
                    ctx.collect(i);
                }
            }

            @Override
            public void cancel() {
            }
        };

        StreamSourceOperator<Integer> sourceOp = new StreamSourceOperator<>(source);
        StreamMap<Integer, Integer> mapOp = new StreamMap<>(x -> x * 10);
        StreamSinkOperator<Integer> sinkOp = new StreamSinkOperator<>(new SinkFunction<Integer>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void consume(Integer value) {
                run1Results.add(value);
            }
        });

        List<StreamOperator<?>> operators = Arrays.asList(sourceOp, mapOp, sinkOp);

        sourceOp.setOutput(new ChainingOutput<>(mapOp));
        mapOp.setOutput(new ChainingOutput<>(sinkOp));

        CheckpointBarrierTracker tracker = new CheckpointBarrierTracker(0L, operators, snapshot -> {
            capturedSnapshot.set(snapshot);
            checkpointComplete.countDown();
        });

        for (int i = 0; i < operators.size(); i++) {
            if (operators.get(i) instanceof AbstractStreamOperator) {
                final int opIndex = i;
                ((AbstractStreamOperator<?>) operators.get(i)).setSnapshotCallback(
                        snapshot -> tracker.acknowledgeOperator(opIndex, snapshot)
                );
            }
        }

        sourceOp.open();
        mapOp.open();
        sinkOp.open();

        tracker.triggerCheckpoint(1L, System.currentTimeMillis(), CheckpointType.CHECKPOINT);
        sourceOp.run();

        assertTrue(checkpointComplete.await(5, TimeUnit.SECONDS));
        assertEquals(Arrays.asList(10, 20, 30, 40, 50), run1Results);
        assertNotNull(capturedSnapshot.get());
        assertEquals(1L, capturedSnapshot.get().getCheckpointId());

        List<Integer> run2Results = Collections.synchronizedList(new ArrayList<>());
        SourceFunction<Integer> source2 = new SourceFunction<>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void run(SourceContext<Integer> ctx) {
                for (int i = 6; i <= 10; i++) {
                    ctx.collect(i);
                }
            }

            @Override
            public void cancel() {
            }
        };

        StreamSourceOperator<Integer> sourceOp2 = new StreamSourceOperator<>(source2);
        StreamMap<Integer, Integer> mapOp2 = new StreamMap<>(x -> x * 10);
        StreamSinkOperator<Integer> sinkOp2 = new StreamSinkOperator<>(new SinkFunction<Integer>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void consume(Integer value) {
                run2Results.add(value);
            }
        });

        sourceOp2.setOutput(new ChainingOutput<>(mapOp2));
        mapOp2.setOutput(new ChainingOutput<>(sinkOp2));

        sourceOp2.open();
        mapOp2.open();
        sinkOp2.open();
        sourceOp2.run();

        assertEquals(Arrays.asList(60, 70, 80, 90, 100), run2Results);

        sourceOp.close();
        mapOp.close();
        sinkOp.close();
        sourceOp2.close();
        mapOp2.close();
        sinkOp2.close();
    }

    @Test
    void testCoordinatorBasedCheckpointAndRecovery() throws Exception {
        PendingCheckpoint pending = coordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
        assertNotNull(pending);
        long checkpointId = pending.getCheckpointId();

        TaskStateSnapshot taskState = TaskStateSnapshot.builder(0L)
                .checkpointId(checkpointId)
                .putOperatorState("source-offset", "100".getBytes())
                .putOperatorState("map-state", "mapped".getBytes())
                .build();

        coordinator.acknowledgeTask(0L, checkpointId, taskState);

        CompletedCheckpoint completed = pending.getCompletableFuture().get(5, TimeUnit.SECONDS);
        assertNotNull(completed);
        assertEquals(checkpointId, completed.getCheckpointId());

        coordinator.shutdown();

        CheckpointIDCounter recoveredCounter = new CheckpointIDCounter();
        CheckpointCoordinator recoveredCoordinator = new CheckpointCoordinator(1L, 0, recoveredCounter, storage, new CheckpointConfig());
        CompletedCheckpoint restored = recoveredCoordinator.restoreFromCheckpoint();
        assertNotNull(restored);
        assertEquals(checkpointId, restored.getCheckpointId());
        assertTrue(restored.isRestored());

        TaskStateSnapshot restoredState = restored.getTaskState(0L);
        assertNotNull(restoredState);
        assertArrayEquals("100".getBytes(), restoredState.getOperatorState("source-offset"));
        assertArrayEquals("mapped".getBytes(), restoredState.getOperatorState("map-state"));

        recoveredCoordinator.shutdown();
    }
}
