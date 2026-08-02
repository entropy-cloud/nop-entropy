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
import io.nop.stream.runtime.checkpoint.storage.LocalFileCheckpointStorage;
import io.nop.stream.runtime.cluster.InMemoryClusterRegistry;
import io.nop.stream.runtime.cluster.TaskAssignment;
import io.nop.stream.runtime.taskmanager.CheckpointAckMessage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * G52 wiring verification: per-task terminal-state reporting path end-to-end.
 *
 * <p>Verifies that:
 * <ul>
 *   <li>{@code JobCoordinator.reportTaskStatus} accepts a FAILED report and triggers
 *       global recovery (anti-hollow check).</li>
 *   <li>{@code reportTaskStatus} rejects stale-token reports (#24 — no silent skip
 *       of fencing contract).</li>
 *   <li>{@code reportNodeTaskLiveness} updates the per-subtask liveness map.</li>
 *   <li>Per-task stall detection (in {@code detectFailures}) fires when
 *       {@code lastProgressTime} ages past {@code taskTimeoutMs}.</li>
 * </ul>
 */
class TestJobCoordinatorPerTaskFailure {

    private static final String JOB_ID = "fail-job-1";

    @TempDir
    Path tempDir;

    private InMemoryClusterRegistry clusterRegistry;
    private CheckpointCoordinator checkpointCoordinator;
    private DeploymentPlan deploymentPlan;
    private JobCoordinator coordinator;

    @BeforeEach
    void setUp() {
        clusterRegistry = new InMemoryClusterRegistry();
        LocalFileCheckpointStorage storage = new LocalFileCheckpointStorage(tempDir.toString());
        CheckpointIDCounter idCounter = new CheckpointIDCounter();
        CheckpointConfig config = CheckpointConfig.builder()
                .checkpointEnabled(true).checkpointInterval(1000L)
                .checkpointTimeout(10000L).maxConcurrentCheckpoints(1)
                .maxRetainedCheckpoints(3).build();
        checkpointCoordinator = new CheckpointCoordinator(
                JOB_ID, "pipeline-0", idCounter, storage, config);

        clusterRegistry.registerNode("node-1", "localhost:9080", 4);

        // No-task-RPC fixture: JobCoordinator does not need taskRpcServices to handle
        // reportTaskStatus (it triggers recovery internally). assignTasks will warn
        // about missing RPC services but still register assignments in ClusterRegistry.
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

        // Use a no-op task RPC so assignTasks can complete; we only assert on coordinator state.
        coordinator = new JobCoordinator(
                JOB_ID, "coord-1", deploymentPlan,
                clusterRegistry, checkpointCoordinator,
                Collections.singletonMap("node-1", new NoopTaskRpc()));
        coordinator.setTerminationCheckpointTimeoutMs(500L);
    }

    @AfterEach
    void tearDown() {
        coordinator.stop();
    }

    @Test
    void reportFailedTaskStatusTriggersGlobalRecovery() {
        coordinator.start();
        coordinator.assignTasks();

        long tokenBefore = coordinator.getFencingEpoch();
        int historyBefore = clusterRegistry.getAttemptHistory(JOB_ID, "source", 0).size();

        // Simulate a per-task FAILED report from the RunningTask on node-1
        coordinator.reportTaskStatus(new TaskStatusReport(
                JOB_ID, "source", 0, 1,
                TaskStatusReport.TerminalState.FAILED,
                "simulated task exception", System.currentTimeMillis(),
                tokenBefore, System.currentTimeMillis()));

        long tokenAfter = coordinator.getFencingEpoch();
        assertNotEquals(tokenBefore, tokenAfter,
                "FAILED report must trigger global recovery (new fencing token)");

        int historyAfter = clusterRegistry.getAttemptHistory(JOB_ID, "source", 0).size();
        assertEquals(historyBefore + 1, historyAfter,
                "global recovery must append a new attempt to the history");
    }

    @Test
    void reportFailedTaskStatusRejectsStaleFencingToken() {
        coordinator.start();
        coordinator.assignTasks();
        long currentToken = coordinator.getFencingEpoch();

        // Send FAILED with stale token — should be ignored (no recovery)
        coordinator.reportTaskStatus(new TaskStatusReport(
                JOB_ID, "source", 0, 1,
                TaskStatusReport.TerminalState.FAILED,
                "stale-token report", System.currentTimeMillis(),
                999L, System.currentTimeMillis()));

        assertEquals(currentToken, coordinator.getFencingEpoch(),
                "Stale-token FAILED report must NOT trigger recovery");
    }

    @Test
    void reportCompletedTaskStatusDoesNotTriggerRecovery() {
        coordinator.start();
        coordinator.assignTasks();
        long tokenBefore = coordinator.getFencingEpoch();

        coordinator.reportTaskStatus(new TaskStatusReport(
                JOB_ID, "source", 0, 1,
                TaskStatusReport.TerminalState.COMPLETED,
                null, System.currentTimeMillis(),
                tokenBefore, System.currentTimeMillis()));

        assertEquals(tokenBefore, coordinator.getFencingEpoch(),
                "COMPLETED report must NOT trigger recovery");
    }

    @Test
    void reportNodeTaskLivenessUpdatesLivenessMap() throws Exception {
        coordinator.start();
        coordinator.assignTasks();
        coordinator.setTaskTimeoutMs(1_000L);

        // Make the per-subtask liveness look fresh: report lastProgressTime = now
        long fresh = System.currentTimeMillis();
        coordinator.reportNodeTaskLiveness("node-1", Collections.singletonList(
                new TaskProgress("source", 0, 1, fresh)));

        // detectFailures should NOT trigger recovery (liveness is fresh)
        long tokenBefore = coordinator.getFencingEpoch();
        coordinator.detectFailures();
        assertEquals(tokenBefore, coordinator.getFencingEpoch(),
                "Fresh liveness must NOT trigger recovery");
    }

    @Test
    void staleLivenessTriggersRecoveryViaDetectFailures() {
        coordinator.start();
        coordinator.assignTasks();
        coordinator.setTaskTimeoutMs(1_000L);

        // Plant a stale liveness timestamp (well before the cutoff)
        long staleTimestamp = System.currentTimeMillis() - 10_000L;
        coordinator.reportNodeTaskLiveness("node-1", Collections.singletonList(
                new TaskProgress("source", 0, 1, staleTimestamp)));

        long tokenBefore = coordinator.getFencingEpoch();
        coordinator.detectFailures();
        assertNotEquals(tokenBefore, coordinator.getFencingEpoch(),
                "Stale liveness (older than taskTimeoutMs) must trigger recovery");
    }

    @Test
    void nullProgressBatchIsIgnored() {
        coordinator.start();
        coordinator.assignTasks();
        long tokenBefore = coordinator.getFencingEpoch();

        // Should not throw, should not trigger recovery
        coordinator.reportNodeTaskLiveness("node-1", null);
        coordinator.reportNodeTaskLiveness("node-1", Collections.emptyList());

        assertEquals(tokenBefore, coordinator.getFencingEpoch());
    }

    /**
     * Bare-bones NoopTaskRpc so assignTasks has a target and does not throw.
     * Phase 2 only asserts coordinator-level behavior; the task side is exercised
     * in TestTaskManager.
     */
    static class NoopTaskRpc implements io.nop.stream.runtime.rpc.IStreamTaskRpcService {
        final java.util.concurrent.atomic.AtomicLong lastEpoch = new java.util.concurrent.atomic.AtomicLong();
        final CopyOnWriteArrayList<TaskAssignment> assignments = new CopyOnWriteArrayList<>();

        @Override
        public void receiveAssignment(TaskAssignment assignment) {
            assignments.add(assignment);
        }

        @Override
        public void triggerCheckpoint(io.nop.stream.core.checkpoint.CheckpointBarrier barrier, long fencingEpoch) {
        }

        @Override
        public void cancelTask(String jobId, String vertexId, int subtaskIndex) {
        }

        @Override
        public void updateFencingToken(long fencingEpoch) {
            lastEpoch.set(fencingEpoch);
        }
    }

    // Unused import placeholder to keep CheckpointAckMessage import resolution stable
    @SuppressWarnings("unused")
    private static final Class<?> KEEP = CheckpointAckMessage.class;
}
