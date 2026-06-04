package io.nop.stream.runtime.checkpoint;

import io.nop.stream.core.checkpoint.*;
import io.nop.stream.core.checkpoint.storage.ICheckpointStorage;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class TestCheckpointCoordinatorRaceCondition {

    private static final String JOB_ID = "test-job";
    private static final String PIPELINE_ID = "test-pipeline";

    @Test
    void testConcurrentAcknowledgeTaskNoRaceCondition() throws Exception {
        CheckpointConfig config = new CheckpointConfig();
        config.setCheckpointInterval(60000);
        config.setCheckpointTimeout(60000);
        config.setMaxConcurrentCheckpoints(10);

        AtomicInteger storeCount = new AtomicInteger(0);
        ICheckpointStorage storage = new ICheckpointStorage() {
            @Override public String getName() { return "test"; }
            @Override public String storeCheckPoint(CompletedCheckpoint checkpoint) {
                storeCount.incrementAndGet();
                return "cp-" + checkpoint.getCheckpointId();
            }
            @Override public CompletedCheckpoint getLatestCheckpoint(String jobId, String pipelineId) { return null; }
            @Override public List<CompletedCheckpoint> getAllCheckpoints(String jobId) { return Collections.emptyList(); }
            @Override public List<CompletedCheckpoint> getLatestCheckpoints(String jobId, int count) { return Collections.emptyList(); }
            @Override public void deleteCheckpoint(String jobId, String pipelineId, long checkpointId) {}
            @Override public void deleteAllCheckpoints(String jobId) {}
            @Override public int getCheckpointCount(String jobId) { return 0; }
            @Override public String storeSavepoint(CompletedCheckpoint checkpoint, String targetPath) { return targetPath; }
            @Override public CompletedCheckpoint loadSavepoint(String savepointPath) { return null; }
            @Override public SavepointMetadata loadSavepointMetadata(String savepointPath) { return null; }
            @Override public void storeEpochManifest(String jobId, String pipelineId, EpochManifest manifest) {}
            @Override public EpochManifest loadLatestEpochManifest(String jobId, String pipelineId) { return null; }
        };

        CheckpointCoordinator coordinator = new CheckpointCoordinator(
                JOB_ID, PIPELINE_ID, new CheckpointIDCounter(), storage, config);

        int numTasks = 10;
        List<TaskLocation> taskLocations = new ArrayList<>();
        for (int i = 0; i < numTasks; i++) {
            TaskLocation loc = new TaskLocation(JOB_ID, PIPELINE_ID, "vertex-" + i, i);
            taskLocations.add(loc);
        }
        coordinator.setTasksToAcknowledge(taskLocations);

        PendingCheckpoint pending = coordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
        assertNotNull(pending);
        long checkpointId = pending.getCheckpointId();

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numTasks);
        ExecutorService executor = Executors.newFixedThreadPool(numTasks);

        for (int i = 0; i < numTasks; i++) {
            final int idx = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    TaskStateSnapshot state = new TaskStateSnapshot(taskLocations.get(idx));
                    state.putOperatorState("state-key", "value-" + idx);
                    coordinator.acknowledgeTask(taskLocations.get(idx), checkpointId, state);
                } catch (Exception e) {
                    // ignore
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS), "All tasks should complete within timeout");
        executor.shutdown();

        assertEquals(1, storeCount.get(), "Checkpoint should be stored exactly once");
        coordinator.shutdown();
    }
}
