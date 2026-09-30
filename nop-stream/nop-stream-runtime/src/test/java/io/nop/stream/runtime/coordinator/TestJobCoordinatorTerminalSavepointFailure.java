package io.nop.stream.runtime.coordinator;

import io.nop.stream.core.checkpoint.CheckpointIDCounter;
import io.nop.stream.core.checkpoint.JobTerminationMode;
import io.nop.stream.runtime.checkpoint.CheckpointCoordinator;
import io.nop.stream.runtime.checkpoint.storage.LocalFileCheckpointStorage;
import io.nop.stream.runtime.cluster.ClusterRegistry;
import io.nop.stream.runtime.cluster.NodeInfo;
import io.nop.stream.runtime.cluster.TaskAssignment;
import io.nop.stream.runtime.event.StreamJobEvent;
import io.nop.stream.runtime.event.StreamJobEventListener;
import io.nop.stream.runtime.health.StreamJobHealth;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 369 Phase 1 C2 (DISTRIBUTED failure path): a terminal savepoint that
 * ultimately cannot complete fails the job LOUD (failJob: FAILED status +
 * health + JOB_FAILED event) instead of the pre-fix
 * "catch → health.onFinished → stop()" silent-FINISHED teardown. The
 * EXPORT_SAVEPOINT (KEEPS_JOB_RUNNING) scope keeps the job running after a
 * loud failure record.
 */
class TestJobCoordinatorTerminalSavepointFailure {

    private static final String JOB_ID = "terminal-fail-job";
    private static final String COORDINATOR_ID = "coord-terminal-fail";

    @TempDir
    Path tempDir;

    private MockClusterRegistry clusterRegistry;
    private CoordinatorTestSupport.RecordingTaskRpcService mockRpcService;
    private JobCoordinator coordinator;
    private final List<StreamJobEvent> jobEvents = new ArrayList<>();

    @BeforeEach
    void setUp() {
        clusterRegistry = new MockClusterRegistry();
        LocalFileCheckpointStorage storage = new LocalFileCheckpointStorage(tempDir.toString());
        CheckpointCoordinator checkpointCoordinator = new CheckpointCoordinator(
                JOB_ID, "pipeline-0", new CheckpointIDCounter(), storage,
                CoordinatorTestSupport.defaultCheckpointConfig());

        // The mock TM never ACKs — the terminal savepoint future can never
        // complete, so the wait times out and the failure path runs.
        mockRpcService = new CoordinatorTestSupport.RecordingTaskRpcService();
        clusterRegistry.registerNode("node-1", "localhost:8080", 4);

        coordinator = new JobCoordinator(JOB_ID, COORDINATOR_ID,
                CoordinatorTestSupport.twoVertexForwardPlan(JOB_ID),
                clusterRegistry, checkpointCoordinator,
                Map.of("node-1", mockRpcService));
        coordinator.setTerminationCheckpointTimeoutMs(300L);
        coordinator.addJobEventListener(new StreamJobEventListener() {
            @Override
            public void onEvent(StreamJobEvent event) {
                jobEvents.add(event);
            }
        });
    }

    @AfterEach
    void tearDown() {
        coordinator.stop();
    }

    private boolean hasEvent(StreamJobEvent.EventType type) {
        return jobEvents.stream().anyMatch(e -> e.getType() == type);
    }

    /**
     * DRAIN with an uncompletable terminal savepoint → the job is FAILED
     * (status + health + JOB_FAILED event), NOT silently declared FINISHED,
     * and the coordinator is not torn down as a successful finish would.
     */
    @Test
    void testDrainFailureFailsJobLoud() {
        coordinator.start();
        coordinator.assignTasks();

        coordinator.terminate(JobTerminationMode.DRAIN);

        assertEquals(JobStatus.FAILED, coordinator.getJobStatus().getJobStatus(),
                "C2 (plan 369): the job must be FAILED after the terminal savepoint failed");
        assertTrue(hasEvent(StreamJobEvent.EventType.JOB_FAILED),
                "a JOB_FAILED event must fire for alert routing");
        assertFalse(hasEvent(StreamJobEvent.EventType.JOB_FINISHED),
                "the job must NOT be declared FINISHED");
        assertNotNull(coordinator.getJobFailureCause(),
                "the failure cause must be recorded for diagnostics");
    }

    /**
     * EXPORT_SAVEPOINT failure: loud record, but the job KEEPS RUNNING
     * (KEEPS_JOB_RUNNING scope — no status change, no stop).
     */
    @Test
    void testExportSavepointFailureKeepsJobRunning() {
        coordinator.start();
        coordinator.assignTasks();

        coordinator.terminate(JobTerminationMode.EXPORT_SAVEPOINT);

        assertEquals(JobStatus.RUNNING, coordinator.getJobStatus().getJobStatus(),
                "the export failure must not change the job status");
        assertFalse(hasEvent(StreamJobEvent.EventType.JOB_FAILED),
                "the export failure is recorded but does not fail the job");
        assertFalse(hasEvent(StreamJobEvent.EventType.JOB_FINISHED),
                "the export failure does not finish the job either");
        assertTrue(coordinator.isRunning(), "the job continues running");
        assertNotNull(mockRpcService.lastBarrier.get(),
                "the export savepoint barrier was still sent");
        // A later successful export must still work (the coordinator is alive).
        assertTrue(coordinator.isActive(), "the coordinator stays ACTIVE after an export failure");
    }

    /**
     * Health-machine companion assertion: the DRAIN failure drives the health
     * state machine to FAILED (onFailJob), not FINISHED.
     */
    @Test
    void testDrainFailureDrivesHealthToFailed() {
        List<StreamJobHealth> seen = new ArrayList<>();
        coordinator.addHealthListener((jobId, from, to, cause) -> seen.add(to));
        coordinator.start();
        coordinator.assignTasks();

        coordinator.terminate(JobTerminationMode.DRAIN);

        assertTrue(seen.contains(StreamJobHealth.FAILED),
                "health must reach FAILED after the terminal savepoint failure: " + seen);
        assertFalse(seen.contains(StreamJobHealth.FINISHED),
                "health must NOT reach FINISHED: " + seen);
    }

    // ==================== Mocks ====================

    static class MockClusterRegistry implements ClusterRegistry {
        final Map<String, NodeInfo> nodes = new ConcurrentHashMap<>();

        @Override
        public void registerCoordinator(String jobId, String coordinatorId, long fencingEpoch) {
        }

        @Override
        public io.nop.stream.runtime.cluster.CoordinatorInfo getActiveCoordinator(String jobId) {
            return null;
        }

        @Override
        public void registerNode(String nodeId, String endpoint, int capacity) {
            nodes.put(nodeId, new NodeInfo(nodeId, endpoint, capacity,
                    System.currentTimeMillis(), System.currentTimeMillis()));
        }

        @Override
        public boolean renewLease(String nodeId, long leaseTimeoutMs) {
            return nodes.containsKey(nodeId);
        }

        @Override
        public io.nop.stream.runtime.cluster.LeaseInfo getNodeLease(String nodeId) {
            return null;
        }

        @Override
        public List<NodeInfo> getActiveNodes() {
            return new ArrayList<>(nodes.values());
        }

        @Override
        public void assignTask(String jobId, String vertexId, int subtaskIndex,
                               String nodeId, String attemptId, long fencingEpoch,
                               int attemptNumber) {
        }

        @Override
        public TaskAssignment getTaskAssignment(String jobId, String vertexId, int subtaskIndex) {
            return null;
        }

        @Override
        public List<TaskAssignment> getAttemptHistory(String jobId, String vertexId, int subtaskIndex) {
            return new ArrayList<>();
        }

        @Override
        public void removeTaskAssignment(String jobId, String vertexId, int subtaskIndex) {
        }
    }
}
