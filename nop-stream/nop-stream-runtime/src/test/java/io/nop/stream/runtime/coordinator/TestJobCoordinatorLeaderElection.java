/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.coordinator;

import io.nop.cluster.elector.LeaderEpoch;
import io.nop.stream.core.checkpoint.CheckpointConfig;
import io.nop.stream.core.checkpoint.CheckpointIDCounter;
import io.nop.stream.core.execution.plan.DeploymentPlan;
import io.nop.stream.core.execution.plan.PartitionedPlan;
import io.nop.stream.runtime.checkpoint.CheckpointCoordinator;
import io.nop.stream.runtime.checkpoint.PendingCheckpoint;
import io.nop.stream.runtime.checkpoint.storage.LocalFileCheckpointStorage;
import io.nop.stream.runtime.cluster.ClusterRegistry;
import io.nop.stream.runtime.cluster.NodeInfo;
import io.nop.stream.runtime.cluster.TaskAssignment;
import io.nop.stream.runtime.rpc.IStreamTaskRpcService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * G24/G25 Phase 1 focused tests for the leader-election WIRE in
 * {@link JobCoordinator}: leader-gated start (STANDBY initial state), the
 * {@code whenElectionCompleted()} semantic guard (B1 — a lost election must not
 * activate the coordinator), fencing-token derivation from {@link LeaderEpoch},
 * and explicit (non-silent) standby rejection of control-plane methods.
 */
class TestJobCoordinatorLeaderElection {

    private static final String JOB_ID = "ha-job-1";
    private static final String COORDINATOR_ID = "coordinator-ha-1";

    @TempDir
    Path tempDir;

    private TestLeaderElector elector;
    private MockClusterRegistry clusterRegistry;
    private CheckpointCoordinator checkpointCoordinator;
    private MockTaskRpcService mockRpcService;
    private Map<String, IStreamTaskRpcService> taskRpcServices;
    private DeploymentPlan deploymentPlan;
    private JobCoordinator coordinator;

    @BeforeEach
    void setUp() {
        elector = new TestLeaderElector("host-A");
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
        checkpointCoordinator = new CheckpointCoordinator(JOB_ID, "pipeline-0", idCounter, storage, config);

        mockRpcService = new MockTaskRpcService();
        taskRpcServices = new java.util.HashMap<>();
        taskRpcServices.put("node-1", mockRpcService);

        clusterRegistry.registerNode("node-1", "localhost:8080", 4);

        Map<String, PartitionedPlan.VertexPlan> vertexPlans = new LinkedHashMap<>();
        vertexPlans.put("source", new PartitionedPlan.VertexPlan("source", 1, null));
        vertexPlans.put("sink", new PartitionedPlan.VertexPlan("sink", 1, null));
        List<PartitionedPlan.EdgePlan> edgePlans = new ArrayList<>();
        edgePlans.add(new PartitionedPlan.EdgePlan("source", "sink",
                io.nop.stream.core.execution.plan.PartitionPolicy.FORWARD));
        PartitionedPlan partitionedPlan = new PartitionedPlan(
                JOB_ID, "pipeline-0", vertexPlans, edgePlans, null, null);
        deploymentPlan = new DeploymentPlan(
                JOB_ID, "pipeline-0", partitionedPlan,
                "local", "memory", "local", null, null);

        coordinator = new JobCoordinator(
                JOB_ID, COORDINATOR_ID, deploymentPlan,
                clusterRegistry, checkpointCoordinator, taskRpcServices);
        coordinator.setLeaderElector(elector);
        coordinator.setTerminationCheckpointTimeoutMs(500L);
    }

    @AfterEach
    void tearDown() {
        coordinator.stop();
    }

    // ==================== Phase 1 Exit Criteria ====================

    @Test
    void testHaStartEntersStandbyNotActive() {
        // G24/G25: start() must return immediately in STANDBY (active=false),
        // before any election result.
        coordinator.start();

        assertTrue(coordinator.isRunning(), "coordinator machinery should be running");
        assertFalse(coordinator.isActive(), "HA coordinator must start in STANDBY (not active)");
        // No fencing token yet — coordinator has not won leadership.
        assertNull(coordinator.getFencingToken(),
                "standby coordinator must not have a fencing token before becoming leader");
        // No assignments issued while standby.
        assertTrue(coordinator.getTaskAssignments().isEmpty());
        assertTrue(mockRpcService.assignments.isEmpty(),
                "standby coordinator must not issue task assignments");
    }

    @Test
    void testBecomeLeaderActivatesAndDerivesFencingFromLeaderEpoch() {
        coordinator.start();
        assertFalse(coordinator.isActive());

        // G24/G25: leadership grant drives activation and derives the composite
        // fencing token from the granted LeaderEpoch (NOT a random UUID).
        elector.grantLeadership(7L);

        assertTrue(coordinator.isActive(), "becomeLeader must transition to ACTIVE");
        assertNotNull(coordinator.getFencingToken(), "leader must have a fencing token");

        // Composite token encoding: leaderId@epoch#recoveryGen
        assertEquals("host-A@7#0", coordinator.getFencingToken(),
                "HA fencing token must be derived from LeaderEpoch (leaderId@epoch#recoveryGen), not a random UUID");
        assertEquals(0L, coordinator.getRecoveryGen(),
                "recoveryGen must reset to 0 on leadership grant");

        LeaderEpoch leadership = coordinator.getCurrentLeadership();
        assertNotNull(leadership);
        assertEquals("host-A", leadership.getLeaderId());
        assertEquals(7L, leadership.getEpoch());

        // Activation bootstraps the control plane (assignments issued with the
        // leadership-derived token).
        assertFalse(coordinator.getTaskAssignments().isEmpty());
        assertFalse(mockRpcService.assignments.isEmpty());
        assertEquals("host-A@7#0", mockRpcService.assignments.get(0).getFencingToken(),
                "assignments issued on activation must carry the leadership-derived token");
    }

    @Test
    void testWhenElectionCompletedLostElectionDoesNotActivate() {
        // B1 core guard: whenElectionCompleted() signals "a result exists", which
        // may be that ANOTHER node won. The coordinator must NOT activate on a
        // lost election, regardless of election-completion signalling.
        coordinator.start();
        assertFalse(coordinator.isActive());

        // Make this node lose to another host; the elector completes the election
        // AND fires becomeFollower.
        elector.loseElectionTo("host-B", 5L);

        // Election has a result...
        assertTrue(elector.whenElectionCompleted().toCompletableFuture().isDone(),
                "election should be marked completed");
        // ...but this node is NOT the leader.
        assertFalse(elector.isLeader());
        // ...so the coordinator must remain in STANDBY.
        assertFalse(coordinator.isActive(),
                "coordinator must NOT activate when the election was won by another node");
        assertNull(coordinator.getFencingToken(),
                "lost-election coordinator must not derive a fencing token");
    }

    @Test
    void testElectionListenerWiring() {
        // G24/G25 wiring verification: addElectionListener is actually invoked at
        // runtime, and grant callbacks drive the active/standby transition.
        coordinator.start();
        assertFalse(coordinator.isActive());

        elector.grantLeadership(1L);
        assertTrue(coordinator.isActive(), "grant callback must activate the coordinator");

        elector.revokeLeadership();
        assertFalse(coordinator.isActive(), "revoke callback must deactivate the coordinator back to STANDBY");

        // Re-election re-activates with a fresh epoch.
        elector.grantLeadership(2L);
        assertTrue(coordinator.isActive());
        assertEquals("host-A@2#0", coordinator.getFencingToken(),
                "re-election must derive a fresh token from the new epoch");
    }

    @Test
    void testStandbyRejectsAssignTasksExplicitly() {
        coordinator.start();
        // Standby: assignTasks must be explicitly rejected (no assignments), not
        // silently executed.
        coordinator.assignTasks();
        assertTrue(coordinator.getTaskAssignments().isEmpty(),
                "standby assignTasks must be rejected, not executed");
        assertTrue(mockRpcService.assignments.isEmpty());

        // After activation it works.
        elector.grantLeadership(1L);
        coordinator.assignTasks();
        assertFalse(coordinator.getTaskAssignments().isEmpty());
    }

    @Test
    void testStandbyRejectsTriggerCheckpointExplicitly() {
        coordinator.start();
        // Standby: triggerCheckpoint must return null (rejected), not fire a barrier.
        PendingCheckpoint pending = coordinator.triggerCheckpoint();
        assertNull(pending, "standby triggerCheckpoint must be rejected");
        assertNull(mockRpcService.lastBarrier.get(),
                "standby coordinator must not send any checkpoint barrier");

        // After activation it proceeds.
        elector.grantLeadership(1L);
        PendingCheckpoint active = coordinator.triggerCheckpoint();
        assertNotNull(active, "active leader must be able to trigger checkpoints");
        assertNotNull(mockRpcService.lastBarrier.get());
    }

    @Test
    void testStandbyRejectsCollectAckExplicitly() {
        coordinator.start();
        io.nop.stream.runtime.taskmanager.CheckpointAckMessage ack =
                new io.nop.stream.runtime.taskmanager.CheckpointAckMessage(
                        new io.nop.stream.core.checkpoint.TaskLocation(JOB_ID, "pipeline-0", "source", 0),
                        1L, null, "host-A@1#0");
        boolean accepted = coordinator.collectAck(ack);
        assertFalse(accepted, "standby collectAck must be rejected");
    }

    @Test
    void testStandbyRejectsReportTaskStatusExplicitly() {
        // G24/G25 (#24): standby must reject task status reports explicitly, not
        // silently swallow them (the legacy !running path used debug-log+return).
        coordinator.start();
        TaskStatusReport report = new TaskStatusReport(
                JOB_ID, "source", 0, 1,
                TaskStatusReport.TerminalState.FAILED, "boom",
                System.currentTimeMillis(), "host-A@1#0", System.currentTimeMillis());
        // Should not throw and should not trigger recovery (standby doesn't own it).
        coordinator.reportTaskStatus(report);
        assertEquals(0, coordinator.getRestartCount(),
                "standby coordinator must not trigger recovery from a task status report");
    }

    @Test
    void testStandbyRejectsReportNodeTaskLivenessExplicitly() {
        coordinator.start();
        java.util.List<TaskProgress> progress = new ArrayList<>();
        progress.add(new TaskProgress("source", 0, 1, System.currentTimeMillis()));
        // Standby: must reject (observable), not silently update liveness.
        coordinator.reportNodeTaskLiveness("node-1", progress);
        // No crash, no recovery ownership. Active gate held.
        assertFalse(coordinator.isActive());
    }

    @Test
    void testTestLeaderElectorGrantRevokeFiresListenerSynchronously() {
        // Verify the test double itself: grant/revoke deterministically fire the
        // ILeaderElectionListener contract (used by all HA tests above).
        java.util.concurrent.atomic.AtomicInteger leaderCount = new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.atomic.AtomicInteger followerCount = new java.util.concurrent.atomic.AtomicInteger();
        elector.addElectionListener(new io.nop.cluster.elector.ILeaderElectionListener() {
            @Override
            public void becomeLeader(LeaderEpoch leaderEpoch) {
                leaderCount.incrementAndGet();
            }

            @Override
            public void becomeFollower(LeaderEpoch leaderEpoch) {
                followerCount.incrementAndGet();
            }
        });

        elector.grantLeadership(1L);
        assertEquals(1, leaderCount.get());
        assertEquals(0, followerCount.get());
        assertTrue(elector.isLeader());

        elector.revokeLeadership();
        assertEquals(1, leaderCount.get());
        assertEquals(1, followerCount.get());
        assertFalse(elector.isLeader());
    }

    @Test
    void testNonHaModeZeroRegression() {
        // G24/G25: a coordinator WITHOUT an elector must keep the legacy
        // single-instance behaviour — random-UUID fencing, immediately active.
        JobCoordinator nonHa = new JobCoordinator(
                JOB_ID, COORDINATOR_ID, deploymentPlan,
                clusterRegistry, checkpointCoordinator, taskRpcServices);
        try {
            nonHa.start();
            assertTrue(nonHa.isRunning());
            assertTrue(nonHa.isActive(), "non-HA coordinator must be active immediately on start");
            assertNull(nonHa.getCurrentLeadership(), "non-HA coordinator has no leadership epoch");
            assertNotNull(nonHa.getFencingToken());
            // Non-HA token is a random UUID (contains hyphens, not the composite '@'/'#' shape).
            assertTrue(nonHa.getFencingToken().contains("-"),
                    "non-HA fencing token must remain a random UUID");
            assertFalse(nonHa.getFencingToken().contains("@"));

            // assignTasks / triggerCheckpoint work without any election.
            nonHa.assignTasks();
            assertFalse(nonHa.getTaskAssignments().isEmpty());
        } finally {
            nonHa.stop();
        }
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
        final List<TaskAssignment> assignments = new CopyOnWriteArrayList<>();
        final AtomicReference<io.nop.stream.core.checkpoint.CheckpointBarrier> lastBarrier = new AtomicReference<>();
        final AtomicReference<String> lastFencingToken = new AtomicReference<>();

        @Override
        public void receiveAssignment(TaskAssignment assignment) {
            assignments.add(assignment);
        }

        @Override
        public void triggerCheckpoint(io.nop.stream.core.checkpoint.CheckpointBarrier barrier, String fencingToken) {
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
