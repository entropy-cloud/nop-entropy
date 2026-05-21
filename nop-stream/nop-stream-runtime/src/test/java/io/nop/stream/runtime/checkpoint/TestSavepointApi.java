/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.checkpoint;

import io.nop.stream.core.checkpoint.*;
import io.nop.stream.core.checkpoint.storage.ICheckpointStorage;
import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.common.state.backend.IKeyedStateBackend;
import io.nop.stream.core.common.state.backend.memory.MemoryStateBackend;
import io.nop.stream.core.execution.CheckpointBarrierTracker;
import io.nop.stream.core.operators.*;
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

class TestSavepointApi {

    private static final TaskLocation LOC_0 = new TaskLocation("1", "0", "v0", 0);

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
        coordinator = new CheckpointCoordinator("1", "0", idCounter, storage, config);
        coordinator.registerTask(LOC_0);
    }

    @AfterEach
    void teardown() throws Exception {
        coordinator.shutdown();
        storage.deleteAllCheckpoints("1");
    }

    @Test
    void testSavepointTriggerWritesDataToFile() throws Exception {
        PendingCheckpoint pending = coordinator.tryTriggerPendingCheckpoint(CheckpointType.SAVEPOINT);
        assertNotNull(pending);

        TaskStateSnapshot taskState = TaskStateSnapshot.builder(LOC_0)
                .checkpointId(pending.getCheckpointId())
                .putOperatorState("operator-0", "savepoint-data".getBytes())
                .putKeyedState("keyed-state", "keyed-data".getBytes())
                .build();

        coordinator.acknowledgeTask(LOC_0, pending.getCheckpointId(), taskState);

        CompletedCheckpoint completed = pending.getCompletableFuture()
                .get(5, TimeUnit.SECONDS);
        assertNotNull(completed);

        String path = storage.storeCheckPoint(completed);
        assertNotNull(path);
        assertTrue(Files.exists(Path.of(path)));

        CompletedCheckpoint loaded = storage.getLatestCheckpoint("1", "0");
        assertNotNull(loaded);
        assertEquals(CheckpointType.SAVEPOINT, loaded.getCheckpointType());

        TaskStateSnapshot loadedState = loaded.getTaskState(LOC_0);
        assertNotNull(loadedState);
        assertArrayEquals("savepoint-data".getBytes(), loadedState.getOperatorState("operator-0"));
        assertArrayEquals("keyed-data".getBytes(), loadedState.getKeyedState("keyed-state"));
    }

    @Test
    void testSavepointRestoreLoadsOperatorStates() throws Exception {
        PendingCheckpoint pending = coordinator.tryTriggerPendingCheckpoint(CheckpointType.SAVEPOINT);
        assertNotNull(pending);

        TaskStateSnapshot taskState = TaskStateSnapshot.builder(LOC_0)
                .checkpointId(pending.getCheckpointId())
                .putOperatorState("operator-0", "original-state".getBytes())
                .build();

        coordinator.acknowledgeTask(LOC_0, pending.getCheckpointId(), taskState);

        CompletedCheckpoint completed = pending.getCompletableFuture()
                .get(5, TimeUnit.SECONDS);
        String savepointPath = storage.storeCheckPoint(completed);

        LocalFileCheckpointStorage restoreStorage = new LocalFileCheckpointStorage(tempDir.toString());
        CompletedCheckpoint loaded = restoreStorage.getLatestCheckpoint("1", "0");
        assertNotNull(loaded);

        TaskStateSnapshot restoredState = loaded.getTaskState(LOC_0);
        assertNotNull(restoredState);
        assertArrayEquals("original-state".getBytes(), restoredState.getOperatorState("operator-0"));
    }

    @Test
    void testSavepointTriggerAndRestoreEndToEnd() throws Exception {
        MemoryStateBackend stateBackend = new MemoryStateBackend();
        IKeyedStateBackend<String> keyedBackend = stateBackend.createKeyedStateBackend(String.class);

        AtomicLong restoredValue = new AtomicLong(-1);
        AbstractStreamOperator<Long> originalOp = new AbstractStreamOperator<>() {
            private static final long serialVersionUID = 1L;
        };
        originalOp.setStateBackend(stateBackend);
        originalOp.setKeyedStateBackend(keyedBackend);

        io.nop.stream.core.common.state.ValueStateDescriptor<Long> descriptor =
                new io.nop.stream.core.common.state.ValueStateDescriptor<>("counter", Long.class, 0L);
        keyedBackend.setCurrentKey("key1");
        io.nop.stream.core.common.state.ValueState<Long> state = keyedBackend.getState(descriptor);
        state.update(42L);

        CheckpointBarrier barrier = new CheckpointBarrier(1L, System.currentTimeMillis(), CheckpointType.SAVEPOINT);
        TestOutput<Long> output = new TestOutput<>();
        originalOp.setOutput(output);
        originalOp.processBarrier(barrier);

        OperatorSnapshotResult snapshot = originalOp.getLastSnapshotResult();
        assertNotNull(snapshot);
        assertFalse(snapshot.isEmpty());

        TaskStateSnapshot taskState = TaskStateSnapshot.builder(LOC_0)
                .checkpointId(1L)
                .putKeyedState("keyed-state", snapshot.getKeyedStates().values().iterator().next())
                .build();

        PendingCheckpoint pending = coordinator.tryTriggerPendingCheckpoint(CheckpointType.SAVEPOINT);
        coordinator.acknowledgeTask(LOC_0, pending.getCheckpointId(), taskState);
        CompletedCheckpoint completed = pending.getCompletableFuture().get(5, TimeUnit.SECONDS);
        String savepointPath = storage.storeCheckPoint(completed);

        IKeyedStateBackend<String> restoredBackend = stateBackend.createKeyedStateBackend(String.class);
        AbstractStreamOperator<Long> restoredOp = new AbstractStreamOperator<>() {
            private static final long serialVersionUID = 1L;
        };
        restoredOp.setStateBackend(stateBackend);
        restoredOp.setKeyedStateBackend(restoredBackend);

        OperatorSnapshotResult restoreSnapshot = OperatorSnapshotResult.builder()
                .putKeyedState("keyed-state", snapshot.getKeyedStates().values().iterator().next())
                .build();
        restoredOp.restoreState(restoreSnapshot);

        io.nop.stream.core.common.state.ValueState<Long> restoredState =
                restoredBackend.getState(descriptor);
        restoredBackend.setCurrentKey("key1");
        assertEquals(42L, restoredState.value());

        keyedBackend.close();
        restoredBackend.close();
    }

    @Test
    void testSavepointMetadataFromSavepointCheckpoint() throws Exception {
        PendingCheckpoint pending = coordinator.tryTriggerPendingCheckpoint(CheckpointType.SAVEPOINT);
        assertNotNull(pending);

        TaskStateSnapshot taskState = TaskStateSnapshot.builder(LOC_0)
                .checkpointId(pending.getCheckpointId())
                .putOperatorState("op-0", "data".getBytes())
                .putKeyedState("keyed", "kdata".getBytes())
                .build();

        coordinator.acknowledgeTask(LOC_0, pending.getCheckpointId(), taskState);
        CompletedCheckpoint completed = pending.getCompletableFuture().get(5, TimeUnit.SECONDS);

        SavepointMetadata metadata = SavepointMetadata.fromCompletedCheckpoint(completed);
        assertNotNull(metadata);
        assertEquals(1, metadata.getOperatorStateCount());
        assertEquals(1, metadata.getKeyedStateCount());
        assertEquals(CheckpointType.SAVEPOINT, completed.getCheckpointType());
    }
}
