/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.checkpoint;

import io.nop.core.lang.json.JsonTool;
import io.nop.stream.core.checkpoint.*;
import io.nop.stream.core.checkpoint.storage.ICheckpointStorage;
import io.nop.stream.core.common.functions.SinkFunction;
import io.nop.stream.core.common.functions.source.CheckpointedSourceFunction;
import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.common.functions.sink.TwoPhaseCommitSinkFunction;
import io.nop.stream.core.common.state.ValueState;
import io.nop.stream.core.common.state.ValueStateDescriptor;
import io.nop.stream.core.common.state.backend.IKeyedStateBackend;
import io.nop.stream.core.common.state.backend.StateSnapshot;
import io.nop.stream.core.common.state.backend.memory.MemoryKeyedStateBackend;
import io.nop.stream.core.common.state.backend.memory.MemoryStateBackend;
import io.nop.stream.core.operators.*;
import io.nop.stream.runtime.checkpoint.storage.LocalFileCheckpointStorage;
import org.junit.jupiter.api.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class TestCheckpointRecovery {

    private static final TaskLocation LOC_1 = new TaskLocation("1", "0", "v1", 1);
    private static final TaskLocation LOC_2 = new TaskLocation("1", "0", "v2", 2);

    private static Path tempDir;
    private ICheckpointStorage storage;
    private CheckpointCoordinator coordinator;
    private CheckpointIDCounter idCounter;

    @BeforeAll
    static void setupClass() throws Exception {
        tempDir = Files.createTempDirectory("checkpoint-recovery-test");
    }

    @AfterAll
    static void teardownClass() throws Exception {
        deleteDirectory(tempDir.toFile());
    }

    @BeforeEach
    void setup() {
        storage = new LocalFileCheckpointStorage(tempDir.toString());
        idCounter = new CheckpointIDCounter();
        CheckpointConfig config = new CheckpointConfig();
        config.setCheckpointInterval(1000);
        coordinator = new CheckpointCoordinator("1", "0", idCounter, storage, config);
        coordinator.setTasksToAcknowledge(java.util.Arrays.asList(LOC_1, LOC_2));
    }

    @AfterEach
    void teardown() throws Exception {
        coordinator.shutdown();
        storage.deleteAllCheckpoints("1");
    }

    @Test
    void testBasicCheckpointAndRecovery() throws Exception {
        PendingCheckpoint pending = coordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
        assertNotNull(pending);
        long checkpointId = pending.getCheckpointId();

        TaskStateSnapshot state1 = TaskStateSnapshot.builder(LOC_1)
                .putOperatorState("state1", "data1")
                .build();
        TaskStateSnapshot state2 = TaskStateSnapshot.builder(LOC_2)
                .putOperatorState("state2", "data2")
                .build();

        coordinator.acknowledgeTask(LOC_1, checkpointId, state1);
        coordinator.acknowledgeTask(LOC_2, checkpointId, state2);

        Thread.sleep(200);

        CompletedCheckpoint completed = coordinator.getLatestCheckpoint();
        assertNotNull(completed);
        assertEquals(checkpointId, completed.getCheckpointId());

        coordinator.shutdown();

        CheckpointIDCounter recoveredIdCounter = new CheckpointIDCounter();
        CheckpointCoordinator recoveredCoordinator = new CheckpointCoordinator("1", "0", recoveredIdCounter, storage, new CheckpointConfig());
        recoveredCoordinator.restoreFromCheckpoint();

        CompletedCheckpoint restored = recoveredCoordinator.getLatestCheckpoint();
        assertNotNull(restored);
        assertEquals(checkpointId, restored.getCheckpointId());
        assertTrue(restored.isRestored());

        TaskStateSnapshot restoredState1 = restored.getTaskState(LOC_1);
        assertNotNull(restoredState1);
        assertEquals("data1", restoredState1.getOperatorState("state1"));

        TaskStateSnapshot restoredState2 = restored.getTaskState(LOC_2);
        assertNotNull(restoredState2);
        assertEquals("data2", restoredState2.getOperatorState("state2"));

        recoveredCoordinator.shutdown();
    }

    @Test
    void testMultipleCheckpointRecovery() throws Exception {
        for (int i = 0; i < 3; i++) {
            CheckpointIDCounter iterIdCounter = new CheckpointIDCounter();
            CheckpointCoordinator iterCoordinator = new CheckpointCoordinator("1", "0", iterIdCounter, storage, new CheckpointConfig());
            iterCoordinator.setTasksToAcknowledge(java.util.Arrays.asList(LOC_1, LOC_2));

            PendingCheckpoint pending = iterCoordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
            assertNotNull(pending);
            long checkpointId = pending.getCheckpointId();

            TaskStateSnapshot state1 = TaskStateSnapshot.builder(LOC_1)
                    .putOperatorState("iteration", String.valueOf(i))
                    .build();
            TaskStateSnapshot state2 = TaskStateSnapshot.builder(LOC_2)
                    .putOperatorState("iteration", String.valueOf(i))
                    .build();

            iterCoordinator.acknowledgeTask(LOC_1, checkpointId, state1);
            iterCoordinator.acknowledgeTask(LOC_2, checkpointId, state2);
            Thread.sleep(100);

            iterCoordinator.shutdown();
        }

        Thread.sleep(100);

        CheckpointIDCounter recoveredIdCounter = new CheckpointIDCounter();
        CheckpointCoordinator recoveredCoordinator = new CheckpointCoordinator("1", "0", recoveredIdCounter, storage, new CheckpointConfig());
        recoveredCoordinator.restoreFromCheckpoint();

        CompletedCheckpoint restored = recoveredCoordinator.getLatestCheckpoint();
        assertNotNull(restored);

        Object restoredState = restored.getTaskState(LOC_1).getOperatorState("iteration");
        assertEquals("2", restoredState);

        recoveredCoordinator.shutdown();
    }

    @Test
    void testCheckpointWithKeyedState() throws Exception {
        PendingCheckpoint pending = coordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
        assertNotNull(pending);
        long checkpointId = pending.getCheckpointId();

        TaskStateSnapshot state1 = TaskStateSnapshot.builder(LOC_1)
                .putOperatorState("operator", "op-data")
                .build();
        TaskStateSnapshot state2 = TaskStateSnapshot.builder(LOC_2)
                .putOperatorState("operator", "op-data-2")
                .build();

        coordinator.acknowledgeTask(LOC_1, checkpointId, state1);
        coordinator.acknowledgeTask(LOC_2, checkpointId, state2);

        Thread.sleep(200);

        coordinator.shutdown();

        CheckpointIDCounter recoveredIdCounter = new CheckpointIDCounter();
        CheckpointCoordinator recoveredCoordinator = new CheckpointCoordinator("1", "0", recoveredIdCounter, storage, new CheckpointConfig());
        recoveredCoordinator.restoreFromCheckpoint();

        CompletedCheckpoint restored = recoveredCoordinator.getLatestCheckpoint();
        assertNotNull(restored);

        TaskStateSnapshot restoredState = restored.getTaskState(LOC_1);
        assertNotNull(restoredState);

        assertEquals("op-data", restoredState.getOperatorState("operator"));

        recoveredCoordinator.shutdown();
    }

    @Test
    void testCheckpointAbortAndRecovery() throws Exception {
        PendingCheckpoint pending1 = coordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
        assertNotNull(pending1);
        long checkpointId1 = pending1.getCheckpointId();

        TaskStateSnapshot state1 = TaskStateSnapshot.builder(LOC_1)
                .putOperatorState("data", "task1-data")
                .build();
        coordinator.acknowledgeTask(LOC_1, checkpointId1, state1);

        Thread.sleep(100);

        coordinator.abortPendingCheckpoint(pending1, "Test abort");

        PendingCheckpoint pending2 = coordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
        assertNotNull(pending2);
        long checkpointId2 = pending2.getCheckpointId();

        TaskStateSnapshot state1v2 = TaskStateSnapshot.builder(LOC_1)
                .putOperatorState("data", "task1-data-v2")
                .build();
        TaskStateSnapshot state2v2 = TaskStateSnapshot.builder(LOC_2)
                .putOperatorState("data", "task2-data")
                .build();

        coordinator.acknowledgeTask(LOC_1, checkpointId2, state1v2);
        coordinator.acknowledgeTask(LOC_2, checkpointId2, state2v2);

        Thread.sleep(200);

        CompletedCheckpoint completed = coordinator.getLatestCheckpoint();
        assertNotNull(completed);
        assertEquals(checkpointId2, completed.getCheckpointId());

        coordinator.shutdown();

        CheckpointIDCounter recoveredIdCounter = new CheckpointIDCounter();
        CheckpointCoordinator recoveredCoordinator = new CheckpointCoordinator("1", "0", recoveredIdCounter, storage, new CheckpointConfig());
        recoveredCoordinator.restoreFromCheckpoint();

        CompletedCheckpoint restored = recoveredCoordinator.getLatestCheckpoint();
        assertNotNull(restored);
        assertEquals(checkpointId2, restored.getCheckpointId());

        assertEquals("task1-data-v2", restored.getTaskState(LOC_1).getOperatorState("data"));
        assertEquals("task2-data", restored.getTaskState(LOC_2).getOperatorState("data"));

        recoveredCoordinator.shutdown();
    }

    @Test
    void testSavepointRecovery() throws Exception {
        PendingCheckpoint pending = coordinator.tryTriggerPendingCheckpoint(CheckpointType.SAVEPOINT);
        assertNotNull(pending);
        long checkpointId = pending.getCheckpointId();

        TaskStateSnapshot state1 = TaskStateSnapshot.builder(LOC_1)
                .putOperatorState("savepoint-data", "important")
                .build();
        TaskStateSnapshot state2 = TaskStateSnapshot.builder(LOC_2)
                .putOperatorState("savepoint-data", "important-2")
                .build();

        coordinator.acknowledgeTask(LOC_1, checkpointId, state1);
        coordinator.acknowledgeTask(LOC_2, checkpointId, state2);

        Thread.sleep(200);

        CompletedCheckpoint savepoint = coordinator.getLatestCheckpoint();
        assertNotNull(savepoint);
        assertEquals(CheckpointType.SAVEPOINT, savepoint.getCheckpointType());

        coordinator.shutdown();

        CheckpointIDCounter recoveredIdCounter = new CheckpointIDCounter();
        CheckpointCoordinator recoveredCoordinator = new CheckpointCoordinator("1", "0", recoveredIdCounter, storage, new CheckpointConfig());
        recoveredCoordinator.restoreFromCheckpoint();

        CompletedCheckpoint restored = recoveredCoordinator.getLatestCheckpoint();
        assertNotNull(restored);
        assertEquals(CheckpointType.SAVEPOINT, restored.getCheckpointType());

        recoveredCoordinator.shutdown();
    }

    @Test
    void testKeyedStateBackendRestore() throws Exception {
        MemoryStateBackend stateBackend = new MemoryStateBackend();
        IKeyedStateBackend<String> backend = stateBackend.createKeyedStateBackend(String.class);

        ValueStateDescriptor<Integer> descriptor = new ValueStateDescriptor<>("counter", Integer.class);
        backend.setCurrentKey("key1");
        ValueState<Integer> counter = backend.getState(descriptor);
        counter.update(42);

        backend.setCurrentKey("key2");
        ValueState<Integer> counter2 = backend.getState(descriptor);
        counter2.update(99);

        StateSnapshot snapshot = backend.snapshotState();
        assertNotNull(snapshot);
        assertFalse(snapshot.isEmpty());

        IKeyedStateBackend<String> restored = stateBackend.createKeyedStateBackend(String.class);
        restored.restoreState(snapshot);

        ValueState<Integer> restoredCounter = restored.getState(descriptor);
        restored.setCurrentKey("key1");
        assertEquals(42, restoredCounter.value());

        restored.setCurrentKey("key2");
        ValueState<Integer> restoredCounter2 = restored.getState(descriptor);
        assertEquals(99, restoredCounter2.value());
    }

    @Test
    void testOperatorStateRestore() throws Exception {
        MemoryStateBackend stateBackend = new MemoryStateBackend();

        AbstractStreamOperator<String> op = new AbstractStreamOperator<String>() {
            private static final long serialVersionUID = 1L;
        };
        op.setStateBackend(stateBackend);
        IKeyedStateBackend<String> keyedBackend = stateBackend.createKeyedStateBackend(String.class);
        op.setKeyedStateBackend(keyedBackend);

        ValueStateDescriptor<Integer> desc = new ValueStateDescriptor<>("count", Integer.class);
        keyedBackend.setCurrentKey("test-key");
        ValueState<Integer> state = keyedBackend.getState(desc);
        state.update(100);

        OperatorSnapshotResult snapshot = op.snapshotState(
                new StateSnapshotContext(1L, System.currentTimeMillis()));
        assertFalse(snapshot.isEmpty());

        AbstractStreamOperator<String> restoredOp = new AbstractStreamOperator<String>() {
            private static final long serialVersionUID = 1L;
        };
        IKeyedStateBackend<String> restoredBackend = stateBackend.createKeyedStateBackend(String.class);
        restoredOp.setKeyedStateBackend(restoredBackend);
        restoredOp.setStateBackend(stateBackend);

        restoredOp.restoreState(snapshot);

        ValueState<Integer> restoredState = restoredBackend.getState(desc);
        restoredBackend.setCurrentKey("test-key");
        assertEquals(100, restoredState.value());
    }

    @Test
    void testSourceOffsetRecovery() throws Exception {
        AtomicLong restoredOffset = new AtomicLong(-1);
        AtomicLong currentOffset = new AtomicLong(500);

        CheckpointedSourceFunction<String> source = new CheckpointedSourceFunction<String>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void run(SourceContext<String> ctx) {
            }

            @Override
            public void cancel() {
            }

            @Override
            public OperatorSnapshotResult snapshotState(long checkpointId) {
                return OperatorSnapshotResult.builder()
                        .putOperatorState("offset", currentOffset.get())
                        .build();
            }

            @Override
            public void initializeState(TaskStateSnapshot state) {
                Object offsetObj = state.getOperatorState("offset");
                if (offsetObj != null) {
                    if (offsetObj instanceof Number) {
                        restoredOffset.set(((Number) offsetObj).longValue());
                    } else {
                        restoredOffset.set(Long.parseLong(String.valueOf(offsetObj)));
                    }
                }
            }
        };

        OperatorSnapshotResult snapshot = source.snapshotState(1L);
        assertFalse(snapshot.isEmpty());

        StreamSourceOperator<String> sourceOp = new StreamSourceOperator<>(source);
        sourceOp.restoreState(snapshot);

        assertEquals(500, restoredOffset.get());
    }

    @Test
    void testSinkRollbackOnRecovery() throws Exception {
        AtomicInteger beginCount = new AtomicInteger(0);
        AtomicInteger rollbackCount = new AtomicInteger(0);

        TwoPhaseCommitSinkFunction<String> sink = new TwoPhaseCommitSinkFunction<String>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void beginTransaction() {
                beginCount.incrementAndGet();
            }

            @Override
            public void invoke(String value) {
            }

            @Override
            public void preCommit(long checkpointId) {
            }

            @Override
            public void commit(long checkpointId) {
            }

            @Override
            public void rollback() {
                rollbackCount.incrementAndGet();
            }
        };

        sink.beginTransaction();
        assertEquals(1, beginCount.get());

        StreamSinkOperator<String> sinkOp = new StreamSinkOperator<>(sink);
        sinkOp.restoreState(OperatorSnapshotResult.empty());

        assertEquals(1, rollbackCount.get());
        assertEquals(2, beginCount.get());
    }

    private static void deleteDirectory(File dir) {
        if (dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    deleteDirectory(file);
                }
            }
        }
        dir.delete();
    }
}
