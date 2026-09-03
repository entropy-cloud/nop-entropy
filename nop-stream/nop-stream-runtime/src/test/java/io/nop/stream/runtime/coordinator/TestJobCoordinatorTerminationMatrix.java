/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.coordinator;

import io.nop.stream.core.checkpoint.CheckpointBarrier;
import io.nop.stream.core.checkpoint.CheckpointConfig;
import io.nop.stream.core.checkpoint.CheckpointIDCounter;
import io.nop.stream.core.checkpoint.CheckpointType;
import io.nop.stream.core.checkpoint.JobTerminationMode;
import io.nop.stream.core.execution.plan.DeploymentPlan;
import io.nop.stream.core.execution.plan.PartitionedPlan;
import io.nop.stream.runtime.checkpoint.CheckpointCoordinator;
import io.nop.stream.runtime.checkpoint.storage.LocalFileCheckpointStorage;
import io.nop.stream.runtime.cluster.ClusterRegistry;
import io.nop.stream.runtime.cluster.NodeInfo;
import io.nop.stream.runtime.cluster.TaskAssignment;
import io.nop.stream.runtime.event.StreamJobEvent;
import io.nop.stream.runtime.event.StreamJobEventListener;
import io.nop.stream.runtime.health.StreamJobHealth;
import io.nop.stream.runtime.rpc.IStreamTaskRpcService;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 2026-09-03-1951-1 Phase 3「先钉后拆」：parameterized behavior matrix pinning
 * the terminateDrain / terminateSuspend / terminateExportSavepoint triplet BEFORE the
 * convergence refactor. Each mode asserts every difference dimension named in the plan:
 * CheckpointType of the sent barrier, event type + payload, health transition, terminal
 * jobStatus, and whether the coordinator is stopped. The refactor must keep this exact
 * matrix green (same assertions before and after — behavior-zero-change evidence).
 */
class TestJobCoordinatorTerminationMatrix {

    private static final String JOB_ID = "terminate-matrix-job";
    private static final String COORDINATOR_ID = "coord-matrix-1";

    static java.util.stream.Stream<Object[]> modes() {
        return java.util.stream.Stream.of(
                new Object[]{JobTerminationMode.DRAIN, CheckpointType.TERMINAL_SAVEPOINT,
                        StreamJobHealth.FINISHED, Boolean.TRUE, "drain"},
                new Object[]{JobTerminationMode.SUSPEND, CheckpointType.TERMINAL_SAVEPOINT,
                        StreamJobHealth.FINISHED, Boolean.TRUE, "suspend"},
                new Object[]{JobTerminationMode.EXPORT_SAVEPOINT, CheckpointType.EXPORTED_SAVEPOINT,
                        StreamJobHealth.RUNNING, Boolean.FALSE, null});
    }

    @TempDir
    Path tempDir;

    private MockClusterRegistry clusterRegistry;
    private MockTaskRpcService mockRpcService;
    private JobCoordinator coordinator;
    private final List<String> healthTransitions = new ArrayList<>();
    private final List<StreamJobEvent> jobEvents = new ArrayList<>();

    @BeforeEach
    void setUp() {
        clusterRegistry = new MockClusterRegistry();
        LocalFileCheckpointStorage storage = new LocalFileCheckpointStorage(tempDir.toString());
        CheckpointConfig config = CheckpointConfig.builder()
                .checkpointEnabled(true)
                .checkpointInterval(1000L)
                .checkpointTimeout(10_000L)
                .maxConcurrentCheckpoints(1)
                .maxRetainedCheckpoints(3)
                .build();
        CheckpointCoordinator checkpointCoordinator = new CheckpointCoordinator(
                JOB_ID, "pipeline-0", new CheckpointIDCounter(), storage, config);

        mockRpcService = new MockTaskRpcService();
        clusterRegistry.registerNode("node-1", "localhost:8080", 4);

        Map<String, PartitionedPlan.VertexPlan> vertexPlans = new java.util.LinkedHashMap<>();
        vertexPlans.put("source", new PartitionedPlan.VertexPlan("source", 1, null));
        vertexPlans.put("sink", new PartitionedPlan.VertexPlan("sink", 1, null));
        List<PartitionedPlan.EdgePlan> edges = new ArrayList<>();
        edges.add(new PartitionedPlan.EdgePlan("source", "sink",
                io.nop.stream.core.execution.plan.PartitionPolicy.FORWARD));
        PartitionedPlan partitionedPlan = new PartitionedPlan(
                JOB_ID, "pipeline-0", vertexPlans, edges, null, null);
        DeploymentPlan deploymentPlan = new DeploymentPlan(
                JOB_ID, "pipeline-0", partitionedPlan, "local", "memory", "local", null, null);

        coordinator = new JobCoordinator(JOB_ID, COORDINATOR_ID, deploymentPlan,
                clusterRegistry, checkpointCoordinator,
                Map.of("node-1", mockRpcService));
        // Short timeout: the mock TM never ACKs, so the terminal checkpoint future times
        // out — each mode must STILL complete its health/event/stop side effects (the
        // timeout is caught + logged per current behavior).
        coordinator.setTerminationCheckpointTimeoutMs(200L);
        coordinator.addHealthListener((jobId, from, to, cause) ->
                healthTransitions.add(from + "->" + to));
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

    @ParameterizedTest(name = "{0}")
    @MethodSource("modes")
    void terminateModeBehaviorMatrix(JobTerminationMode mode, CheckpointType expectedType,
                                     StreamJobHealth expectedHealth, boolean expectedStopped,
                                     String expectedFinishedPayload) {
        coordinator.start();
        coordinator.assignTasks();
        assertTrue(coordinator.isRunning(), "precondition: running after start");

        coordinator.terminate(mode);

        // Dimension 1 — barrier CheckpointType sent to the task managers.
        CheckpointBarrier barrier = mockRpcService.lastBarrier.get();
        assertNotNull(barrier, mode + " must send a barrier to task managers");
        assertEquals(expectedType, barrier.getCheckpointType(),
                mode + " barrier CheckpointType");

        // Dimension 2 — job event: JOB_FINISHED with per-mode payload for DRAIN/SUSPEND;
        // NO JOB_FINISHED event for EXPORT_SAVEPOINT (job continues running).
        boolean hasFinished = jobEvents.stream()
                .anyMatch(e -> e.getType() == StreamJobEvent.EventType.JOB_FINISHED);
        if (expectedFinishedPayload != null) {
            assertTrue(hasFinished, mode + " must fire JOB_FINISHED");
            StreamJobEvent finished = jobEvents.stream()
                    .filter(e -> e.getType() == StreamJobEvent.EventType.JOB_FINISHED)
                    .findFirst().orElseThrow();
            assertEquals(expectedFinishedPayload, finished.getCause(),
                    mode + " JOB_FINISHED payload");
        } else {
            assertFalse(hasFinished,
                    "EXPORT_SAVEPOINT must NOT fire JOB_FINISHED (job continues running)");
        }

        // Dimension 3 — health transition.
        assertEquals(expectedHealth, coordinator.getHealth(), mode + " terminal health");
        if (expectedHealth == StreamJobHealth.FINISHED) {
            assertTrue(healthTransitions.contains("RUNNING->FINISHED"),
                    mode + " must transition RUNNING->FINISHED (was " + healthTransitions + ")");
        } else {
            assertFalse(healthTransitions.contains("RUNNING->FINISHED"),
                    "EXPORT_SAVEPOINT must NOT transition to FINISHED");
        }

        // Dimension 4 — terminal jobStatus: the triplet never mutates jobStatus
        // (only terminateCancel sets CANCELED); stop() does not touch it either.
        assertEquals(JobStatus.RUNNING, coordinator.getJobStatus().getJobStatus(),
                mode + " leaves jobStatus at RUNNING (current pinned behavior)");

        // Dimension 5 — stop or keep running.
        assertEquals(!expectedStopped, coordinator.isRunning(),
                mode + (expectedStopped ? " must stop the coordinator" : " must keep running"));
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

    static class MockTaskRpcService implements IStreamTaskRpcService {
        final AtomicReference<CheckpointBarrier> lastBarrier = new AtomicReference<>();

        @Override
        public void receiveAssignment(TaskAssignment assignment) {
        }

        @Override
        public void triggerCheckpoint(CheckpointBarrier barrier, long fencingEpoch) {
            lastBarrier.set(barrier);
        }

        @Override
        public void cancelTask(String jobId, String vertexId, int subtaskIndex) {
        }

        @Override
        public void updateFencingToken(long fencingEpoch) {
        }
    }
}
