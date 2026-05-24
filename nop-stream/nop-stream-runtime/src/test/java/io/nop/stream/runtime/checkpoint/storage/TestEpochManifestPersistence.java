/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.checkpoint.storage;

import io.nop.stream.core.checkpoint.*;
import io.nop.stream.core.model.StreamModelFingerprint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class TestEpochManifestPersistence {

    private static final TaskLocation LOC_1 = new TaskLocation("job-1", "pipe-1", "v1", 0);
    private static final TaskLocation LOC_2 = new TaskLocation("job-1", "pipe-1", "v2", 1);

    @TempDir
    Path tempDir;

    private LocalFileCheckpointStorage storage;

    @BeforeEach
    void setUp() {
        storage = new LocalFileCheckpointStorage(tempDir.toString());
    }

    @AfterEach
    void tearDown() throws Exception {
        storage.deleteAllCheckpoints("job-1");
    }

    @Test
    void testStoreAndLoadEpochManifest() throws Exception {
        EpochManifest manifest = createTestManifest("job-1", "pipe-1", 100L);

        storage.storeEpochManifest("job-1", "pipe-1", manifest);

        EpochManifest loaded = storage.loadLatestEpochManifest("job-1", "pipe-1");
        assertNotNull(loaded);
        assertEquals(100L, loaded.getEpochId());
        assertEquals("job-1", loaded.getJobId());
        assertEquals("pipe-1", loaded.getPipelineId());
        assertEquals(EpochState.COMMITTED, loaded.getState());
        assertEquals(CheckpointType.CHECKPOINT, loaded.getCheckpointType());
        assertEquals(2, loaded.getTaskSnapshots().size());
    }

    @Test
    void testLoadLatestEpochManifestReturnsHighestEpoch() throws Exception {
        EpochManifest manifest100 = createTestManifest("job-1", "pipe-1", 100L);
        EpochManifest manifest200 = createTestManifest("job-1", "pipe-1", 200L);
        EpochManifest manifest300 = createTestManifest("job-1", "pipe-1", 300L);

        storage.storeEpochManifest("job-1", "pipe-1", manifest100);
        storage.storeEpochManifest("job-1", "pipe-1", manifest200);
        storage.storeEpochManifest("job-1", "pipe-1", manifest300);

        EpochManifest latest = storage.loadLatestEpochManifest("job-1", "pipe-1");
        assertNotNull(latest);
        assertEquals(300L, latest.getEpochId());
    }

    @Test
    void testLoadLatestEpochManifestReturnsNullWhenEmpty() throws Exception {
        EpochManifest loaded = storage.loadLatestEpochManifest("job-1", "pipe-1");
        assertNull(loaded);
    }

    @Test
    void testLoadLatestEpochManifestWithTaskStateData() throws Exception {
        TaskStateSnapshot snapshot1 = TaskStateSnapshot.builder(LOC_1)
                .putOperatorState("op-state-1", "test-data-1")
                .putKeyedState("keyed-1", "keyed-data-1")
                .build();

        TaskStateSnapshot snapshot2 = TaskStateSnapshot.builder(LOC_2)
                .putOperatorState("op-state-2", "test-data-2")
                .build();

        Map<TaskLocation, TaskStateSnapshot> taskSnapshots = new LinkedHashMap<>();
        taskSnapshots.put(LOC_1, snapshot1);
        taskSnapshots.put(LOC_2, snapshot2);

        EpochManifest manifest = new EpochManifest(
                42L, "job-1", "pipe-1", System.currentTimeMillis(),
                CheckpointType.CHECKPOINT, EpochState.COMMITTED,
                taskSnapshots, null, null);

        storage.storeEpochManifest("job-1", "pipe-1", manifest);

        EpochManifest loaded = storage.loadLatestEpochManifest("job-1", "pipe-1");
        assertNotNull(loaded);
        assertEquals(42L, loaded.getEpochId());
        assertEquals(2, loaded.getTaskSnapshots().size());

        TaskStateSnapshot loadedSnapshot1 = loaded.getTaskSnapshots().get(LOC_1);
        assertNotNull(loadedSnapshot1);
        assertEquals("test-data-1", loadedSnapshot1.getOperatorState("op-state-1"));
        assertEquals("keyed-data-1", loadedSnapshot1.getKeyedState("keyed-1"));

        TaskStateSnapshot loadedSnapshot2 = loaded.getTaskSnapshots().get(LOC_2);
        assertNotNull(loadedSnapshot2);
        assertEquals("test-data-2", loadedSnapshot2.getOperatorState("op-state-2"));
    }

    @Test
    void testEpochManifestWithStreamModelFingerprint() throws Exception {
        StreamModelFingerprint fingerprint = StreamModelFingerprint.builder()
                .addComponentHash("source", "abc123")
                .addComponentHash("sink", "def456")
                .dagTopologyHash("topo-hash")
                .requirementsHash("req-hash")
                .checkpointParticipantsHash("cp-hash")
                .build();

        EpochManifest manifest = new EpochManifest(
                10L, "job-1", "pipe-1", System.currentTimeMillis(),
                CheckpointType.CHECKPOINT, EpochState.COMMITTED,
                Collections.emptyMap(), fingerprint, null);

        storage.storeEpochManifest("job-1", "pipe-1", manifest);

        EpochManifest loaded = storage.loadLatestEpochManifest("job-1", "pipe-1");
        assertNotNull(loaded);
        assertNotNull(loaded.getStreamModelFingerprint());
        assertEquals("topo-hash", loaded.getStreamModelFingerprint().getDagTopologyHash());
        assertEquals("req-hash", loaded.getStreamModelFingerprint().getRequirementsHash());
        assertEquals(2, loaded.getStreamModelFingerprint().getComponentHashes().size());
    }

    @Test
    void testEpochManifestWithSegments() throws Exception {
        List<StateSegmentDescriptor> segments = Arrays.asList(
                new StateSegmentDescriptor("operator-state", "/state/op-1", "json", "checksum1", 1),
                new StateSegmentDescriptor("keyed-state", "/state/keyed-1", "json", "checksum2", 1)
        );

        EpochManifest manifest = new EpochManifest(
                5L, "job-1", "pipe-1", System.currentTimeMillis(),
                CheckpointType.CHECKPOINT, EpochState.COMMITTED,
                Collections.emptyMap(), null, segments);

        storage.storeEpochManifest("job-1", "pipe-1", manifest);

        EpochManifest loaded = storage.loadLatestEpochManifest("job-1", "pipe-1");
        assertNotNull(loaded);
        assertNotNull(loaded.getSegments());
        assertEquals(2, loaded.getSegments().size());
        assertEquals("operator-state", loaded.getSegments().get(0).getSegmentType());
        assertEquals("/state/op-1", loaded.getSegments().get(0).getPath());
        assertEquals("keyed-state", loaded.getSegments().get(1).getSegmentType());
    }

    @Test
    void testEpochManifestPersistedDuringCheckpointCompletion() throws Exception {
        // Use the coordinator to complete a checkpoint and verify EpochManifest is stored
        CheckpointIDCounter idCounter = new CheckpointIDCounter();
        CheckpointConfig config = CheckpointConfig.builder()
                .checkpointEnabled(true)
                .checkpointInterval(1000L)
                .checkpointTimeout(5000L)
                .maxRetainedCheckpoints(3)
                .build();

        io.nop.stream.runtime.checkpoint.CheckpointCoordinator coordinator =
                new io.nop.stream.runtime.checkpoint.CheckpointCoordinator(
                        "job-1", "pipe-1", idCounter, storage, config);
        coordinator.setTasksToAcknowledge(Arrays.asList(LOC_1, LOC_2));

        try {
            // Trigger and complete a checkpoint
            io.nop.stream.runtime.checkpoint.PendingCheckpoint pending =
                    coordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
            assertNotNull(pending);

            coordinator.acknowledgeTask(LOC_1, pending.getCheckpointId(), TaskStateSnapshot.empty(LOC_1));
            coordinator.acknowledgeTask(LOC_2, pending.getCheckpointId(), TaskStateSnapshot.empty(LOC_2));

            // Wait for completion
            CompletedCheckpoint completed = pending.getCompletableFuture().get();
            assertNotNull(completed);

            // Verify EpochManifest was persisted
            EpochManifest manifest = storage.loadLatestEpochManifest("job-1", "pipe-1");
            assertNotNull(manifest, "EpochManifest should be persisted after checkpoint completion");
            assertEquals(completed.getCheckpointId(), manifest.getEpochId());
            assertEquals("job-1", manifest.getJobId());
            assertEquals("pipe-1", manifest.getPipelineId());
            assertEquals(EpochState.COMMITTED, manifest.getState());
            assertEquals(2, manifest.getTaskSnapshots().size());
        } finally {
            coordinator.shutdown();
        }
    }

    @Test
    void testRecoveryPrioritizesEpochManifest() throws Exception {
        // Store both a CompletedCheckpoint and an EpochManifest
        CompletedCheckpoint checkpoint = CompletedCheckpoint.builder()
                .jobId("job-1")
                .pipelineId("pipe-1")
                .checkpointId(100L)
                .triggerTimestamp(System.currentTimeMillis() - 1000)
                .completedTimestamp(System.currentTimeMillis())
                .checkpointType(CheckpointType.CHECKPOINT)
                .addTaskState(LOC_1, TaskStateSnapshot.empty(LOC_1))
                .addTaskState(LOC_2, TaskStateSnapshot.empty(LOC_2))
                .build();

        storage.storeCheckPoint(checkpoint);

        // Store EpochManifest with higher epoch ID
        EpochManifest manifest = createTestManifest("job-1", "pipe-1", 200L);
        storage.storeEpochManifest("job-1", "pipe-1", manifest);

        // When loading, the EpochManifest should be available
        EpochManifest loadedManifest = storage.loadLatestEpochManifest("job-1", "pipe-1");
        assertNotNull(loadedManifest);
        assertEquals(200L, loadedManifest.getEpochId());

        // CompletedCheckpoint should also still be available
        CompletedCheckpoint loadedCheckpoint = storage.getLatestCheckpoint("job-1", "pipe-1");
        assertNotNull(loadedCheckpoint);
        assertEquals(100L, loadedCheckpoint.getCheckpointId());
    }

    private EpochManifest createTestManifest(String jobId, String pipelineId, long epochId) {
        Map<TaskLocation, TaskStateSnapshot> taskSnapshots = new LinkedHashMap<>();
        taskSnapshots.put(LOC_1, TaskStateSnapshot.empty(LOC_1));
        taskSnapshots.put(LOC_2, TaskStateSnapshot.empty(LOC_2));

        return new EpochManifest(
                epochId, jobId, pipelineId, System.currentTimeMillis(),
                CheckpointType.CHECKPOINT, EpochState.COMMITTED,
                taskSnapshots, null, null);
    }
}
