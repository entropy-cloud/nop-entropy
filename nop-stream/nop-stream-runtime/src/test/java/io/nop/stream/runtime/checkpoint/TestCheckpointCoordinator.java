/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.checkpoint;

import io.nop.stream.core.checkpoint.*;
import io.nop.stream.core.checkpoint.participant.CheckpointParticipant;
import io.nop.stream.core.checkpoint.storage.ICheckpointStorage;
import io.nop.stream.core.common.state.CheckpointListener;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.runtime.checkpoint.storage.LocalFileCheckpointStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class TestCheckpointCoordinator {

    private static final TaskLocation LOC_1 = new TaskLocation("1", "1", "v1", 1);
    private static final TaskLocation LOC_2 = new TaskLocation("1", "1", "v2", 2);
    private static final TaskLocation LOC_11 = new TaskLocation("1", "1", "v11", 11);
    private static final TaskLocation LOC_22 = new TaskLocation("1", "1", "v22", 22);
    private static final TaskLocation LOC_33 = new TaskLocation("1", "1", "v33", 33);

    @TempDir
    Path tempDir;

    private CheckpointCoordinator coordinator;
    private LocalFileCheckpointStorage storage;
    private CheckpointIDCounter idCounter;
    private CheckpointConfig config;

    @BeforeEach
    void setUp() {
        storage = new LocalFileCheckpointStorage(tempDir.toString());
        idCounter = new CheckpointIDCounter();
        config = CheckpointConfig.builder()
                .checkpointEnabled(true)
                .checkpointInterval(1000L)
                .checkpointTimeout(5000L)
                .maxConcurrentCheckpoints(1)
                .maxRetainedCheckpoints(3)
                .build();

        coordinator = new CheckpointCoordinator("1", "1", idCounter, storage, config);
        coordinator.setTasksToAcknowledge(java.util.Arrays.asList(LOC_1, LOC_2));
    }

    @AfterEach
    void tearDown() {
        coordinator.shutdown();
    }

    @Test
    void testTriggerCheckpoint() {
        PendingCheckpoint pending = coordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);

        assertNotNull(pending);
        assertEquals(0L, pending.getCheckpointId());
        assertEquals(1, coordinator.getNumberOfPendingCheckpoints());
    }

    @Test
    void testAcknowledgeTask() {
        PendingCheckpoint pending = coordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
        assertNotNull(pending);

        TaskStateSnapshot state = TaskStateSnapshot.builder(LOC_1)
                .putOperatorState("op1", "data".getBytes())
                .build();

        boolean acknowledged = coordinator.acknowledgeTask(LOC_1, pending.getCheckpointId(), state);
        assertTrue(acknowledged);
    }

    @Test
    void testAcknowledgeUnknownCheckpoint() {
        boolean acknowledged = coordinator.acknowledgeTask(LOC_1, 999L, TaskStateSnapshot.empty(LOC_1));
        assertFalse(acknowledged);
    }

    @Test
    void testListenerNotification() throws Exception {
        AtomicLong notifiedCheckpointId = new AtomicLong(-1);

        coordinator.addListener(new CheckpointListener() {
            @Override
            public void notifyCheckpointComplete(long checkpointId) throws Exception {
                notifiedCheckpointId.set(checkpointId);
            }
        });

        PendingCheckpoint pending = coordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
        assertNotNull(pending);

        coordinator.acknowledgeTask(LOC_1, pending.getCheckpointId(), TaskStateSnapshot.empty(LOC_1));
        coordinator.acknowledgeTask(LOC_2, pending.getCheckpointId(), TaskStateSnapshot.empty(LOC_2));

        Thread.sleep(100);

        CompletedCheckpoint completed = pending.getCompletableFuture().get();
        assertNotNull(completed);

        assertEquals(pending.getCheckpointId(), notifiedCheckpointId.get());
    }

    @Test
    void testMaxConcurrentCheckpoints() {
        config.setMaxConcurrentCheckpoints(1);

        PendingCheckpoint first = coordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
        assertNotNull(first);

        PendingCheckpoint second = coordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
        assertNull(second);
    }

    @Test
    void testSetTasksToAcknowledge() {
        coordinator.setTasksToAcknowledge(java.util.Arrays.asList(LOC_11, LOC_22, LOC_33));

        PendingCheckpoint pending = coordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
        assertNotNull(pending);
        assertEquals(3, pending.getNumberOfTasks());
    }

    @Test
    void testAbortIsIdempotentForPendingCount() {
        PendingCheckpoint pending = coordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
        assertNotNull(pending);
        assertEquals(1, coordinator.getNumberOfPendingCheckpoints());

        coordinator.abortPendingCheckpoint(pending, "first abort");
        assertEquals(0, coordinator.getNumberOfPendingCheckpoints());

        coordinator.abortPendingCheckpoint(pending, "second abort");
        assertEquals(0, coordinator.getNumberOfPendingCheckpoints());
    }

    @Test
    void testGetLatestCheckpoint() throws Exception {
        PendingCheckpoint pending = coordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
        coordinator.acknowledgeTask(LOC_1, pending.getCheckpointId(), TaskStateSnapshot.empty(LOC_1));
        coordinator.acknowledgeTask(LOC_2, pending.getCheckpointId(), TaskStateSnapshot.empty(LOC_2));

        CompletedCheckpoint completed = pending.getCompletableFuture().get();
        assertNotNull(completed);

        CompletedCheckpoint latest = coordinator.getLatestCheckpoint();
        assertNotNull(latest);
        assertEquals(completed.getCheckpointId(), latest.getCheckpointId());
    }

    @Test
    void testShutdown() {
        coordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
        assertEquals(1, coordinator.getNumberOfPendingCheckpoints());

        coordinator.shutdown();
        assertEquals(0, coordinator.getNumberOfPendingCheckpoints());
    }

    @Test
    void testStorageFailureNoCounterLeak() throws Exception {
        ICheckpointStorage failingStorage = new ICheckpointStorage() {
            private final java.util.concurrent.ConcurrentHashMap<String, CompletedCheckpoint> store = new java.util.concurrent.ConcurrentHashMap<>();

            @Override public String getName() { return "FailingStorage"; }
            @Override public String storeCheckPoint(CompletedCheckpoint checkpoint) throws Exception {
                throw new StreamException("Simulated storage failure");
            }
            @Override public CompletedCheckpoint getLatestCheckpoint(String jobId, String pipelineId) { return null; }
            @Override public java.util.List<CompletedCheckpoint> getAllCheckpoints(String jobId) { return java.util.Collections.emptyList(); }
            @Override public java.util.List<CompletedCheckpoint> getLatestCheckpoints(String jobId, int count) { return java.util.Collections.emptyList(); }
            @Override public void deleteCheckpoint(String jobId, String pipelineId, long checkpointId) {}
            @Override public void deleteAllCheckpoints(String jobId) {}
            @Override public int getCheckpointCount(String jobId) { return 0; }
            @Override public boolean exists(String jobId, String pipelineId, long checkpointId) { return false; }
            @Override public String storeSavepoint(CompletedCheckpoint checkpoint, String targetPath) throws Exception { return targetPath; }
            @Override public CompletedCheckpoint loadSavepoint(String savepointPath) { return null; }
            @Override public SavepointMetadata loadSavepointMetadata(String savepointPath) { return null; }
            @Override public void storeEpochManifest(String jobId, String pipelineId, EpochManifest manifest) {}
            @Override public EpochManifest loadLatestEpochManifest(String jobId, String pipelineId) { return null; }
        };

        CheckpointCoordinator coord = new CheckpointCoordinator("1", "1", new CheckpointIDCounter(), failingStorage, config);
        coord.setTasksToAcknowledge(java.util.Arrays.asList(LOC_1, LOC_2));

        try {
            PendingCheckpoint pending = coord.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
            assertNotNull(pending);
            assertEquals(1, coord.getNumberOfPendingCheckpoints());

            coord.acknowledgeTask(LOC_1, pending.getCheckpointId(), TaskStateSnapshot.empty(LOC_1));
            coord.acknowledgeTask(LOC_2, pending.getCheckpointId(), TaskStateSnapshot.empty(LOC_2));

            Thread.sleep(200);

            assertEquals(0, coord.getNumberOfPendingCheckpoints(),
                    "Counter should be zero after storage failure");
        } finally {
            coord.shutdown();
        }
    }

    @Test
    void testSchedulerStartStop() {
        coordinator.startCheckpointScheduler();
        coordinator.stopCheckpointScheduler();
    }

    @Test
    void testConsecutiveTriggerFailureResetsOnSuccess() {
        coordinator.setTasksToAcknowledge(java.util.Collections.emptyList());
        assertNull(coordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT));

        coordinator.setTasksToAcknowledge(java.util.Arrays.asList(LOC_1, LOC_2));
        PendingCheckpoint pending = coordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
        assertNotNull(pending);
        coordinator.abortPendingCheckpoint(pending, "test");

        PendingCheckpoint next = coordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
        assertNotNull(next);
        assertEquals(1, coordinator.getNumberOfPendingCheckpoints());
    }

    @Test
    void testRetryFailedCommitsUsesOriginalSuccessValue() throws Exception {
        AtomicReference<Boolean> lastSuccessValue = new AtomicReference<>(null);
        AtomicBoolean shouldFailOnFirstCall = new AtomicBoolean(true);

        CheckpointParticipant participant = new CheckpointParticipant() {
            @Override
            public TaskStateSnapshot saveState(long epochId) { return TaskStateSnapshot.empty(LOC_1); }
            @Override
            public void prepareCommit(long epochId) {}
            @Override
            public void finishCommit(long epochId, boolean success) throws Exception {
                lastSuccessValue.set(success);
                if (shouldFailOnFirstCall.getAndSet(false)) {
                    throw new StreamException("Simulated failure on first call");
                }
            }
            @Override
            public void restoreFromEpoch(long epochId, TaskStateSnapshot state) {}
        };

        coordinator.addParticipant(participant);
        PendingCheckpoint pending = coordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
        assertNotNull(pending);
        coordinator.acknowledgeTask(LOC_1, pending.getCheckpointId(), TaskStateSnapshot.empty(LOC_1));
        coordinator.acknowledgeTask(LOC_2, pending.getCheckpointId(), TaskStateSnapshot.empty(LOC_2));

        pending.getCompletableFuture().get();

        assertNotNull(lastSuccessValue.get(), "finishCommit should have been called");
        assertTrue(lastSuccessValue.get(), "First commit should use success=true");
    }

    @Test
    void testShutdownNotifiesParticipantsAbort() {
        AtomicBoolean abortReceived = new AtomicBoolean(false);
        AtomicBoolean listenerAbortReceived = new AtomicBoolean(false);

        CheckpointParticipant participant = new CheckpointParticipant() {
            @Override public TaskStateSnapshot saveState(long epochId) { return TaskStateSnapshot.empty(LOC_1); }
            @Override public void prepareCommit(long epochId) {}
            @Override
            public void finishCommit(long epochId, boolean success) {
                if (!success) abortReceived.set(true);
            }
            @Override public void restoreFromEpoch(long epochId, TaskStateSnapshot state) {}
        };

        coordinator.addParticipant(participant);
        coordinator.addListener(new CheckpointListener() {
            @Override
            public void notifyCheckpointComplete(long checkpointId) {}
            @Override
            public void notifyCheckpointAborted(long checkpointId) {
                listenerAbortReceived.set(true);
            }
        });

        coordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
        assertEquals(1, coordinator.getNumberOfPendingCheckpoints());

        coordinator.shutdown();
        assertTrue(abortReceived.get(), "Participant should receive finishCommit(false) during shutdown");
        assertTrue(listenerAbortReceived.get(), "Listener should receive notifyCheckpointAborted during shutdown");
        assertEquals(0, coordinator.getNumberOfPendingCheckpoints());
    }
}
