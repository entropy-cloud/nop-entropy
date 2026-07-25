/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.coordinator;

import io.nop.stream.core.checkpoint.*;
import io.nop.stream.core.execution.plan.DeploymentPlan;
import io.nop.stream.core.execution.plan.PartitionedPlan;
import io.nop.stream.runtime.checkpoint.CheckpointCoordinator;
import io.nop.stream.runtime.checkpoint.PendingCheckpoint;
import io.nop.stream.runtime.checkpoint.storage.LocalFileCheckpointStorage;
import io.nop.stream.runtime.cluster.ClusterRegistry;
import io.nop.stream.runtime.cluster.NodeInfo;
import io.nop.stream.runtime.cluster.TaskAssignment;
import io.nop.stream.runtime.rpc.IStreamCoordinatorRpcService;
import io.nop.stream.runtime.rpc.IStreamTaskRpcService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Stage 28 (G23) focused verification:
 * <ul>
 *   <li>{@link IStreamCoordinatorRpcService#terminate} delegates to JobCoordinator's
 *       existing four-mode implementation and the CANCEL mode now sets
 *       {@link JobStatus#CANCELED} (closes the gap recorded in JobStatus.java).</li>
 *   <li>{@link IStreamCoordinatorRpcService#abortCheckpoint} triggers the existing
 *       LOCAL abort path (CheckpointCoordinator.abortPendingCheckpoint → abort handler),
 *       and unmatched epochId is explicitly handled (no silent swallow, #24).</li>
 *   <li>{@link IStreamCoordinatorRpcService#getJobStatus} returns a
 *       {@link JobStatusResponse} carrying status + failure cause.</li>
 *   <li>DRAIN + SUSPEND CheckpointType consistency (aligned to
 *       checkpoint-design.md §7.3: both use TERMINAL_SAVEPOINT).</li>
 * </ul>
 */
class TestCoordinatorRpcControlPlane {

    private static final String JOB_ID = "rpc-control-job-1";
    private static final String COORDINATOR_ID = "coord-rpc-1";

    @TempDir
    Path tempDir;

    private MockClusterRegistry clusterRegistry;
    private CheckpointCoordinator checkpointCoordinator;
    private MockTaskRpcService mockRpcService;
    private Map<String, IStreamTaskRpcService> taskRpcServices;
    private DeploymentPlan deploymentPlan;
    private JobCoordinator coordinator;

    @BeforeEach
    void setUp() {
        clusterRegistry = new MockClusterRegistry();
        LocalFileCheckpointStorage storage = new LocalFileCheckpointStorage(tempDir.toString());
        CheckpointIDCounter idCounter = new CheckpointIDCounter();
        CheckpointConfig config = CheckpointConfig.builder()
                .checkpointEnabled(true)
                .checkpointInterval(1000L)
                .checkpointTimeout(10000L)
                .maxConcurrentCheckpoints(1)
                .maxRetainedCheckpoints(3)
                .build();
        checkpointCoordinator = new CheckpointCoordinator(
                JOB_ID, "pipeline-0", idCounter, storage, config);

        mockRpcService = new MockTaskRpcService();
        taskRpcServices = new HashMap<>();
        taskRpcServices.put("node-1", mockRpcService);
        clusterRegistry.registerNode("node-1", "localhost:8080", 4);

        Map<String, PartitionedPlan.VertexPlan> vertexPlans = new LinkedHashMap<>();
        vertexPlans.put("source", new PartitionedPlan.VertexPlan("source", 1, null));
        vertexPlans.put("sink", new PartitionedPlan.VertexPlan("sink", 1, null));
        List<PartitionedPlan.EdgePlan> edges = new ArrayList<>();
        edges.add(new PartitionedPlan.EdgePlan("source", "sink",
                io.nop.stream.core.execution.plan.PartitionPolicy.FORWARD));
        PartitionedPlan partitionedPlan = new PartitionedPlan(
                JOB_ID, "pipeline-0", vertexPlans, edges, null, null);
        deploymentPlan = new DeploymentPlan(
                JOB_ID, "pipeline-0", partitionedPlan,
                "local", "memory", "local", null, null);

        coordinator = new JobCoordinator(
                JOB_ID, COORDINATOR_ID, deploymentPlan,
                clusterRegistry, checkpointCoordinator, taskRpcServices);
        coordinator.setTerminationCheckpointTimeoutMs(500L);
    }

    @AfterEach
    void tearDown() {
        coordinator.stop();
    }

    // ==================== terminate (CANCEL sets CANCELED) ====================

    @Test
    void terminateCancelSetsCanceledStatus() {
        coordinator.start();
        assertEquals(JobStatus.RUNNING, coordinator.getJobStatus().getJobStatus(),
                "precondition: job RUNNING after start");

        coordinator.terminate(JobTerminationMode.CANCEL);

        // Stage 28 fix: terminateCancel now sets jobStatus = CANCELED before stop().
        // Before the fix, getJobStatus() returned RUNNING (the gap recorded in
        // JobStatus.java:21).
        assertEquals(JobStatus.CANCELED, coordinator.getJobStatus().getJobStatus(),
                "CANCEL must transition jobStatus to CANCELED");
        assertFalse(coordinator.isRunning(),
                "CANCEL must stop the coordinator");
    }

    // ==================== terminate (DRAIN uses TERMINAL_SAVEPOINT) ====================

    @Test
    void terminateDrainUsesTerminalSavepointCheckpointType() {
        coordinator.start();
        coordinator.assignTasks();

        coordinator.terminate(JobTerminationMode.DRAIN);

        // Stage 28 consistency fix: DRAIN now uses TERMINAL_SAVEPOINT (aligned to
        // checkpoint-design.md §7.3), not COMPLETED_POINT_TYPE.
        CheckpointBarrier lastBarrier = mockRpcService.lastBarrier.get();
        assertNotNull(lastBarrier,
                "DRAIN must send a barrier to task managers");
        assertEquals(CheckpointType.TERMINAL_SAVEPOINT, lastBarrier.getCheckpointType(),
                "DRAIN barrier must be TERMINAL_SAVEPOINT (checkpoint-design.md §7.3)");
    }

    // ==================== terminate (SUSPEND uses TERMINAL_SAVEPOINT) ====================

    @Test
    void terminateSuspendUsesTerminalSavepointCheckpointType() {
        coordinator.start();
        coordinator.assignTasks();

        coordinator.terminate(JobTerminationMode.SUSPEND);

        // Stage 28 consistency fix: JobCoordinator.terminateSuspend() already used
        // TERMINAL_SAVEPOINT; this test pins that choice so a future refactor that
        // reverts to SAVEPOINT will be caught here.
        CheckpointBarrier lastBarrier = mockRpcService.lastBarrier.get();
        assertNotNull(lastBarrier,
                "SUSPEND must send a barrier to task managers");
        assertEquals(CheckpointType.TERMINAL_SAVEPOINT, lastBarrier.getCheckpointType(),
                "SUSPEND barrier must be TERMINAL_SAVEPOINT (checkpoint-design.md §7.3)");
    }

    // ==================== abortCheckpoint (wiring #23) ====================

    @Test
    void abortCheckpointTriggersExistingAbortPath() {
        coordinator.start();
        coordinator.assignTasks();

        AtomicBoolean abortFired = new AtomicBoolean(false);
        AtomicLong abortedEpoch = new AtomicLong(-1L);
        checkpointCoordinator.setAbortHandler(epochId -> {
            abortFired.set(true);
            abortedEpoch.set(epochId);
        });

        PendingCheckpoint pending = coordinator.triggerCheckpoint();
        assertNotNull(pending, "precondition: checkpoint triggered");
        assertEquals(1, checkpointCoordinator.getNumberOfPendingCheckpoints(),
                "precondition: one pending checkpoint");

        // RPC path: coordinator.abortCheckpoint(epochId) → CheckpointCoordinator
        // .abortPendingCheckpoint → abortHandler callback (LOCAL path)
        coordinator.abortCheckpoint(pending.getCheckpointId());

        assertTrue(abortFired.get(),
                "abortCheckpoint must fire the registered LOCAL abort handler (#23 wiring)");
        assertEquals(pending.getCheckpointId(), abortedEpoch.get(),
                "abort handler must receive the correct epochId");
        assertEquals(0, checkpointCoordinator.getNumberOfPendingCheckpoints(),
                "aborted checkpoint must be removed from pending set");
    }

    // ==================== abortCheckpoint (no silent no-op #24) ====================

    @Test
    void abortCheckpointUnmatchedEpochIsExplicitNoOp() {
        coordinator.start();
        coordinator.assignTasks();

        AtomicBoolean abortFired = new AtomicBoolean(false);
        checkpointCoordinator.setAbortHandler(epochId -> abortFired.set(true));

        // Trigger one real checkpoint
        PendingCheckpoint pending = coordinator.triggerCheckpoint();
        assertNotNull(pending);

        // Abort a non-existent epochId — must NOT throw, must NOT fire the handler,
        // and is explicitly observable via the warning log (#24: no silent swallow).
        long unknownEpoch = pending.getCheckpointId() + 9999;
        assertDoesNotThrow(() -> coordinator.abortCheckpoint(unknownEpoch),
                "unmatched epochId must not throw");

        assertFalse(abortFired.get(),
                "abort handler must NOT fire for an unknown epochId");
        assertEquals(1, checkpointCoordinator.getNumberOfPendingCheckpoints(),
                "the real pending checkpoint must still be present (unmatched abort is a no-op)");
    }

    @Test
    void abortCheckpointWhenNotRunningIsExplicitNoOp() {
        // Coordinator not started → abortCheckpoint should be observable no-op
        AtomicBoolean abortFired = new AtomicBoolean(false);
        checkpointCoordinator.setAbortHandler(epochId -> abortFired.set(true));

        assertDoesNotThrow(() -> coordinator.abortCheckpoint(42L));
        assertFalse(abortFired.get(),
                "abort handler must NOT fire when coordinator is not running");
    }

    // ==================== getJobStatus ====================

    @Test
    void getJobStatusReturnsResponseWithRunningState() {
        coordinator.start();
        JobStatusResponse response = coordinator.getJobStatus();
        assertNotNull(response);
        assertEquals(JobStatus.RUNNING, response.getJobStatus());
        assertNull(response.getFailureCause(),
                "failure cause must be null while RUNNING");
    }

    @Test
    void getJobStatusReturnsResponseWithFailureCause() {
        coordinator.start();
        coordinator.failJob(new RuntimeException("boom"));
        JobStatusResponse response = coordinator.getJobStatus();
        assertNotNull(response);
        assertEquals(JobStatus.FAILED, response.getJobStatus());
        assertNotNull(response.getFailureCause(),
                "failure cause must be captured after failJob");
        assertTrue(response.getFailureCause().contains("boom"),
                "failure cause must include the original throwable text");
    }

    @Test
    void getJobStatusReflectsCanceledAfterTerminateCancel() {
        coordinator.start();
        coordinator.terminate(JobTerminationMode.CANCEL);
        JobStatusResponse response = coordinator.getJobStatus();
        assertEquals(JobStatus.CANCELED, response.getJobStatus());
    }

    // ==================== Mocks ====================

    static class MockClusterRegistry implements ClusterRegistry {
        final Map<String, NodeInfo> nodes = new ConcurrentHashMap<>();
        final Map<String, io.nop.stream.runtime.cluster.CoordinatorInfo> coordinators = new ConcurrentHashMap<>();

        @Override
        public void registerCoordinator(String jobId, String coordinatorId, String fencingToken) {
            coordinators.put(jobId, new io.nop.stream.runtime.cluster.CoordinatorInfo(
                    jobId, coordinatorId, fencingToken, System.currentTimeMillis()));
        }

        @Override
        public io.nop.stream.runtime.cluster.CoordinatorInfo getActiveCoordinator(String jobId) {
            return coordinators.get(jobId);
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
                               String nodeId, String attemptId, String fencingToken,
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

    static class MockTaskRpcService implements IStreamTaskRpcService {
        final AtomicReference<CheckpointBarrier> lastBarrier = new AtomicReference<>();
        final AtomicReference<String> lastFencingToken = new AtomicReference<>();

        @Override
        public void receiveAssignment(TaskAssignment assignment) {
        }

        @Override
        public void triggerCheckpoint(CheckpointBarrier barrier, String fencingToken) {
            lastBarrier.set(barrier);
            lastFencingToken.set(fencingToken);
        }

        @Override
        public void cancelTask(String jobId, String vertexId, int subtaskIndex) {
        }

        @Override
        public void updateFencingToken(String newToken) {
            lastFencingToken.set(newToken);
        }
    }
}
