/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.checkpoint;

import io.nop.stream.core.checkpoint.CheckpointConfig;
import io.nop.stream.core.checkpoint.CheckpointIDCounter;
import io.nop.stream.core.checkpoint.CheckpointType;
import io.nop.stream.core.checkpoint.CompletedCheckpoint;
import io.nop.stream.core.checkpoint.EpochManifest;
import io.nop.stream.core.checkpoint.SavepointMetadata;
import io.nop.stream.core.checkpoint.TaskLocation;
import io.nop.stream.core.checkpoint.TaskStateSnapshot;
import io.nop.stream.core.checkpoint.storage.CheckpointStorageException;
import io.nop.stream.core.checkpoint.storage.ICheckpointStorage;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.runtime.checkpoint.metrics.CheckpointHistoryEntry;
import io.nop.stream.runtime.checkpoint.storage.LocalFileCheckpointStorage;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Item 16 (P-REQ-6): checkpoint observation history — recorded at the REAL
 * completion / failure / abort paths, bounded, failure entries carrying the
 * cause. Failure path driven by an always-failing storage (same pattern as
 * TestCheckpointCoordinatorPersistFailureLog).
 */
class TestCheckpointHistory {

    private static final TaskLocation LOC_1 = new TaskLocation("hist-job", "p", "v1", 0);
    private static final TaskLocation LOC_2 = new TaskLocation("hist-job", "p", "v2", 1);

    @TempDir
    Path tempDir;

    private CheckpointCoordinator coordinator;

    private CheckpointCoordinator buildCoordinator(ICheckpointStorage storage) {
        CheckpointConfig config = CheckpointConfig.builder()
                .checkpointEnabled(true)
                .checkpointInterval(60_000L)
                .checkpointTimeout(30_000L)
                .minPause(0L)
                .maxConcurrentCheckpoints(1)
                .maxRetainedCheckpoints(5)
                .asyncSnapshotEnabled(false)
                .build();
        CheckpointCoordinator c = new CheckpointCoordinator(
                "hist-job", "p", new CheckpointIDCounter(), storage, config);
        c.setTasksToAcknowledge(Arrays.asList(LOC_1, LOC_2));
        return c;
    }

    @BeforeEach
    void setUp() {
        coordinator = buildCoordinator(new LocalFileCheckpointStorage(tempDir.toString()));
    }

    @AfterEach
    void tearDown() {
        if (coordinator != null) {
            coordinator.shutdown();
        }
    }

    @Test
    void completedCheckpointRecordedInHistory() throws Exception {
        PendingCheckpoint cp = coordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
        assertNotNull(cp);
        coordinator.acknowledgeTask(LOC_1, cp.getCheckpointId(), TaskStateSnapshot.empty(LOC_1));
        coordinator.acknowledgeTask(LOC_2, cp.getCheckpointId(), TaskStateSnapshot.empty(LOC_2));

        List<CheckpointHistoryEntry> history = coordinator.getCheckpointHistory();
        assertEquals(1, history.size());
        CheckpointHistoryEntry entry = history.get(0);
        assertEquals(cp.getCheckpointId(), entry.getCheckpointId());
        assertEquals(CheckpointHistoryEntry.Status.COMPLETED, entry.getStatus());
        assertNull(entry.getFailureCause(), "completed entry has no failure cause");
        assertTrue(entry.getDurationMs() >= 0);
        assertTrue(entry.getTriggerTimestamp() > 0);
    }

    @Test
    void failedCheckpointRecordedWithCause() {
        coordinator.shutdown();
        coordinator = buildCoordinator(failingStorage());

        PendingCheckpoint cp = coordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
        assertNotNull(cp);
        coordinator.acknowledgeTask(LOC_1, cp.getCheckpointId(), TaskStateSnapshot.empty(LOC_1));
        coordinator.acknowledgeTask(LOC_2, cp.getCheckpointId(), TaskStateSnapshot.empty(LOC_2));

        assertEquals(PendingCheckpoint.Status.FAILED, cp.getStatus().get());

        List<CheckpointHistoryEntry> history = coordinator.getCheckpointHistory();
        assertEquals(1, history.size());
        CheckpointHistoryEntry entry = history.get(0);
        assertEquals(CheckpointHistoryEntry.Status.FAILED, entry.getStatus());
        assertNotNull(entry.getFailureCause(), "failure entries must carry the failureCause");
        assertTrue(entry.getFailureCause().contains("Failed to store checkpoint"),
                "cause preserves the persist-failure context: " + entry.getFailureCause());

        // overview snapshot also carries the failure cause (P-REQ-6 overview face)
        assertNotNull(coordinator.getMetrics().snapshot().getFailureCause());
    }

    @Test
    void abortedCheckpointRecordedWithReason() {
        PendingCheckpoint cp = coordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
        assertNotNull(cp);
        coordinator.abortPendingCheckpoint(cp, "test abort reason");

        List<CheckpointHistoryEntry> history = coordinator.getCheckpointHistory();
        assertEquals(1, history.size());
        CheckpointHistoryEntry entry = history.get(0);
        assertEquals(CheckpointHistoryEntry.Status.ABORTED, entry.getStatus());
        assertEquals("test abort reason", entry.getFailureCause());
    }

    @Test
    void historyIsBoundedAndNewestFirst() {
        coordinator.setCheckpointHistoryMaxEntries(3);
        for (int i = 0; i < 5; i++) {
            PendingCheckpoint cp = coordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
            assertNotNull(cp);
            coordinator.abortPendingCheckpoint(cp, "abort-" + i);
        }
        List<CheckpointHistoryEntry> history = coordinator.getCheckpointHistory();
        assertEquals(3, history.size(), "history bounded to max entries");
        // newest first
        assertEquals("abort-4", history.get(0).getFailureCause());
        assertEquals("abort-3", history.get(1).getFailureCause());
        assertEquals("abort-2", history.get(2).getFailureCause());
    }

    private static ICheckpointStorage failingStorage() {
        return new ICheckpointStorage() {
            @Override public String getName() { return "AlwaysFailingStorage"; }
            @Override
            public String storeCheckPoint(CompletedCheckpoint checkpoint) throws CheckpointStorageException {
                throw new StreamException("simulated storage failure for cp " + checkpoint.getCheckpointId());
            }
            @Override public CompletedCheckpoint getLatestCheckpoint(String jobId, String pipelineId) { return null; }
            @Override public List<CompletedCheckpoint> getAllCheckpoints(String jobId) { return Collections.emptyList(); }
            @Override public List<CompletedCheckpoint> getLatestCheckpoints(String jobId, int count) { return Collections.emptyList(); }
            @Override public void deleteCheckpoint(String jobId, String pipelineId, long checkpointId) { }
            @Override public void deleteAllCheckpoints(String jobId) { }
            @Override public int getCheckpointCount(String jobId) { return 0; }
            @Override public String storeSavepoint(CompletedCheckpoint checkpoint, String targetPath) { return targetPath; }
            @Override public CompletedCheckpoint loadSavepoint(String savepointPath) { return null; }
            @Override public SavepointMetadata loadSavepointMetadata(String savepointPath) { return null; }
            @Override public void storeEpochManifest(String jobId, String pipelineId, EpochManifest manifest) { }
            @Override public EpochManifest loadLatestEpochManifest(String jobId, String pipelineId) { return null; }
        };
    }
}
