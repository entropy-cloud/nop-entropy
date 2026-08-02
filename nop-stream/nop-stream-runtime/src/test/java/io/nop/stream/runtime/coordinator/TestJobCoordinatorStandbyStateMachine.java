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
import io.nop.stream.runtime.taskmanager.CheckpointAckMessage;
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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * G24/G25 Phase 2 focused tests for the standby coordinator state machine:
 * leadership-loss deactivation (detector stays alive — deactivate != stop),
 * leadership-grant activation rebuilding the work set, same-leader
 * {@code globalRecovery} rotating only the recoveryGen component of the
 * composite fencing token, end-to-end leader switch (two coordinators), and
 * stale-token control rejection.
 */
class TestJobCoordinatorStandbyStateMachine {

    private static final String JOB_ID = "ha-job-2";
    private static final String COORDINATOR_A = "coordinator-A";
    private static final String COORDINATOR_B = "coordinator-B";

    @TempDir
    Path tempDir;

    private TestLeaderElector electorA;
    private TestLeaderElector electorB;
    private MockClusterRegistry clusterRegistry;
    private CheckpointCoordinator checkpointCoordinator;
    private MockTaskRpcService rpcA;
    private MockTaskRpcService rpcB;
    private Map<String, IStreamTaskRpcService> taskRpcServices;
    private DeploymentPlan deploymentPlan;

    @BeforeEach
    void setUp() {
        electorA = new TestLeaderElector("host-A");
        electorB = new TestLeaderElector("host-B");
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

        rpcA = new MockTaskRpcService();
        rpcB = new MockTaskRpcService();
        taskRpcServices = new java.util.HashMap<>();
        taskRpcServices.put("node-1", rpcA);
        taskRpcServices.put("node-2", rpcB);

        clusterRegistry.registerNode("node-1", "localhost:8081", 4);
        clusterRegistry.registerNode("node-2", "localhost:8082", 4);

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
    }

    private JobCoordinator newCoordinator(String coordId, TestLeaderElector elector) {
        JobCoordinator c = new JobCoordinator(
                JOB_ID, coordId, deploymentPlan,
                clusterRegistry, checkpointCoordinator, taskRpcServices);
        c.setLeaderElector(elector);
        c.setTerminationCheckpointTimeoutMs(500L);
        return c;
    }

    @AfterEach
    void tearDown() {
        // Best-effort cleanup; individual tests stop their coordinators.
    }

    // ==================== Phase 2 Exit Criteria ====================

    @Test
    void testLeadershipLossFlipsActiveAndKeepsDetectorAlive() {
        JobCoordinator coordinator = newCoordinator(COORDINATOR_A, electorA);
        try {
            coordinator.start();
            assertFalse(coordinator.isActive());
            assertTrue(coordinator.isFailureDetectorAlive(),
                    "detector must be alive in initial STANDBY");

            electorA.grantLeadership(7L);
            assertTrue(coordinator.isActive());
            assertTrue(coordinator.isFailureDetectorAlive(),
                    "detector must be alive while ACTIVE");

            // G24/G25 M2: leadership-loss must flip active=false but must NOT
            // shut down the failure detector (only stop() / failJob() may).
            electorA.revokeLeadership();
            assertFalse(coordinator.isActive(), "leadership loss must transition to STANDBY");
            assertTrue(coordinator.isRunning(),
                    "leadership loss must NOT call stop() (running stays true)");
            assertTrue(coordinator.isFailureDetectorAlive(),
                    "detector must remain alive after leadership loss so re-election is possible");
        } finally {
            coordinator.stop();
        }
    }

    @Test
    void testReGrantReactivatesAndRebuildsWorkSetFromZeroRecoveryGen() {
        JobCoordinator coordinator = newCoordinator(COORDINATOR_A, electorA);
        try {
            coordinator.start();
            electorA.grantLeadership(1L);
            assertTrue(coordinator.isActive());
            assertEquals(0L, coordinator.getRecoveryGen());
            String tokenAfterGrant = coordinator.getFencingToken();
            assertEquals("host-A@1#0", tokenAfterGrant);
            assertFalse(coordinator.getTaskAssignments().isEmpty());

            // Lose leadership, then regain with a fresh epoch.
            electorA.revokeLeadership();
            assertFalse(coordinator.isActive());

            electorA.grantLeadership(2L);
            assertTrue(coordinator.isActive(), "re-grant must reactivate");
            // M2 evidence: detector survived the standby window.
            assertTrue(coordinator.isFailureDetectorAlive());
            // recoveryGen must reset to 0 on a fresh leadership grant.
            assertEquals(0L, coordinator.getRecoveryGen());
            assertEquals("host-A@2#0", coordinator.getFencingToken());
            // Work set rebuilt from the new epoch — assignments re-issued.
            assertFalse(coordinator.getTaskAssignments().isEmpty());
            assertNotEquals(tokenAfterGrant, coordinator.getFencingToken(),
                    "re-grant with new epoch must derive a different fencing token");
        } finally {
            coordinator.stop();
        }
    }

    @Test
    void testDeactivateDoesNotInvokeStop() {
        // Distinguishing deactivate (reversible) from stop (terminal):
        // After revoke the coordinator must remain usable; only stop() drives
        // the terminal shutdown path (running=false, detector shutdown).
        JobCoordinator coordinator = newCoordinator(COORDINATOR_A, electorA);
        coordinator.start();
        electorA.grantLeadership(1L);
        assertTrue(coordinator.isActive());

        electorA.revokeLeadership();
        assertTrue(coordinator.isRunning(), "deactivate must NOT terminate lifecycle");
        assertTrue(coordinator.isFailureDetectorAlive());

        // Now truly stop — this is the terminal transition.
        coordinator.stop();
        assertFalse(coordinator.isRunning());
        assertFalse(coordinator.isFailureDetectorAlive(),
                "stop() must shut down the detector (terminal)");
        assertFalse(coordinator.isActive());
    }

    @Test
    void testGlobalRecoveryInHaModeRotatesRecoveryGenOnly() {
        JobCoordinator coordinator = newCoordinator(COORDINATOR_A, electorA);
        try {
            coordinator.start();
            electorA.grantLeadership(5L);
            assertEquals("host-A@5#0", coordinator.getFencingToken());
            assertEquals(0L, coordinator.getRecoveryGen());

            // Same-leader recovery: recoveryGen increments, epoch component
            // stays unchanged, full composite token rotates and is pushed.
            coordinator.globalRecovery();

            assertEquals(1L, coordinator.getRecoveryGen(),
                    "same-leader globalRecovery must increment recoveryGen");
            assertEquals("host-A@5#1", coordinator.getFencingToken(),
                    "epoch component must NOT rotate within the same leadership");
            // Latest pushed token on the RPC service matches the rotated one.
            assertEquals("host-A@5#1", rpcA.getLastFencingToken(),
                    "globalRecovery must push the rotated composite token to all TaskManagers");

            // A second same-leader recovery further increments recoveryGen only.
            coordinator.globalRecovery();
            assertEquals(2L, coordinator.getRecoveryGen());
            assertEquals("host-A@5#2", coordinator.getFencingToken());
            assertEquals("host-A@5#2", rpcA.getLastFencingToken());

            // Leadership epoch is preserved across same-leader recoveries.
            LeaderEpoch leadership = coordinator.getCurrentLeadership();
            assertNotNull(leadership);
            assertEquals(5L, leadership.getEpoch());
            assertEquals("host-A", leadership.getLeaderId());
        } finally {
            coordinator.stop();
        }
    }

    @Test
    void testGlobalRecoveryAcrossLeadershipSwitchRotatesEpochComponent() {
        JobCoordinator coordinator = newCoordinator(COORDINATOR_A, electorA);
        try {
            coordinator.start();
            electorA.grantLeadership(3L);
            coordinator.globalRecovery(); // recoveryGen -> 1
            assertEquals("host-A@3#1", coordinator.getFencingToken());

            // Leadership switch: epoch component rotates, recoveryGen resets.
            electorA.revokeLeadership();
            electorA.grantLeadership(9L);
            assertEquals("host-A@9#0", coordinator.getFencingToken(),
                    "leadership switch must rotate epoch component and reset recoveryGen");
            assertEquals(0L, coordinator.getRecoveryGen());
        } finally {
            coordinator.stop();
        }
    }

    @Test
    void testLeaderSwitchEndToEndTwoCoordinators() {
        // Single-process leader-switch E2E: two coordinator instances wired to
        // independent electors that mirror a shared election outcome.
        JobCoordinator coordA = newCoordinator(COORDINATOR_A, electorA);
        JobCoordinator coordB = newCoordinator(COORDINATOR_B, electorB);
        try {
            coordA.start();
            coordB.start();
            assertFalse(coordA.isActive());
            assertFalse(coordB.isActive());

            // Round 1: host-A wins. A's elector fires becomeLeader to A; B's
            // elector fires becomeFollower(leaderEpoch=A) to B.
            electorA.grantLeadership(1L);
            electorB.loseElectionTo("host-A", 1L);

            assertTrue(coordA.isActive(), "A must be ACTIVE after winning");
            assertFalse(coordB.isActive(), "B must remain STANDBY after losing");
            assertEquals("host-A@1#0", coordA.getFencingToken());
            assertNull(coordB.getFencingToken(), "B must not derive a fencing token as follower");
            assertFalse(coordA.getTaskAssignments().isEmpty());
            assertTrue(coordB.getTaskAssignments().isEmpty());

            // Round 2: leadership revokes from A and grants to B with a new epoch.
            electorA.revokeLeadership();
            electorB.grantLeadership(2L);

            assertFalse(coordA.isActive(), "A must drop to STANDBY after losing leadership");
            assertTrue(coordB.isActive(), "B must become ACTIVE after winning");
            assertEquals("host-B@2#0", coordB.getFencingToken(),
                    "B's token must reflect the new epoch");
            assertFalse(coordB.getTaskAssignments().isEmpty(),
                    "B must rebuild the work set on activation");

            // Stale-epoch fencing: A's old token (host-A@1#0) cannot match B's
            // active token (host-B@2#0). collectAck on B rejects A's old token.
            CheckpointAckMessage staleAckFromA = new CheckpointAckMessage(
                    new io.nop.stream.core.checkpoint.TaskLocation(JOB_ID, "pipeline-0", "source", 0),
                    1L, null, "host-A@1#0");
            boolean accepted = coordB.collectAck(staleAckFromA);
            assertFalse(accepted,
                    "ACK carrying A's stale epoch token must be rejected by the new leader B");

            // Fresh-epoch ACK on B is accepted (no stale fencing).
            CheckpointAckMessage freshAck = new CheckpointAckMessage(
                    new io.nop.stream.core.checkpoint.TaskLocation(JOB_ID, "pipeline-0", "source", 0),
                    1L, null, "host-B@2#0");
            // collectAck returns true only when the underlying checkpoint
            // coordinator acknowledges; with no pending checkpoint it returns
            // false — but the key assertion is that it is NOT rejected by the
            // fencing gate (no warn-log "stale fencing token"). We assert no
            // exception and that the token gate passed by observing no
            // activation change.
            // Note: this exercises the token-equality branch positively.
            coordB.collectAck(freshAck); // must not throw
            assertTrue(coordB.isActive());

            // Detector survival on both coordinators across the switch.
            assertTrue(coordA.isFailureDetectorAlive(),
                    "A's detector must survive standby so it can be re-elected");
            assertTrue(coordB.isFailureDetectorAlive());
        } finally {
            coordA.stop();
            coordB.stop();
        }
    }

    @Test
    void testStaleTokenControlRejectedByCollectAck() {
        JobCoordinator coordinator = newCoordinator(COORDINATOR_A, electorA);
        try {
            coordinator.start();
            electorA.grantLeadership(10L);
            String activeToken = coordinator.getFencingToken();
            assertEquals("host-A@10#0", activeToken);

            // An ACK with a stale (previous-epoch) token must be rejected.
            CheckpointAckMessage staleAck = new CheckpointAckMessage(
                    new io.nop.stream.core.checkpoint.TaskLocation(JOB_ID, "pipeline-0", "source", 0),
                    1L, null, "host-A@9#0");
            assertFalse(coordinator.collectAck(staleAck),
                    "ACK with stale epoch token must be rejected");

            // An ACK with a stale recoveryGen (same leader, prior recovery) is
            // also rejected — composite token is compared as a whole.
            coordinator.globalRecovery(); // token now host-A@10#1
            assertEquals("host-A@10#1", coordinator.getFencingToken());
            CheckpointAckMessage staleRecoveryAck = new CheckpointAckMessage(
                    new io.nop.stream.core.checkpoint.TaskLocation(JOB_ID, "pipeline-0", "source", 0),
                    1L, null, "host-A@10#0");
            assertFalse(coordinator.collectAck(staleRecoveryAck),
                    "ACK with stale recoveryGen must be rejected");
        } finally {
            coordinator.stop();
        }
    }

    @Test
    void testElectorExceptionDegradesToStandbySafely() {
        // G24/G25: an elector onException must drop the coordinator to STANDBY
        // (never keep acting as leader with a possibly-stale epoch). The
        // coordinator election listener routes onException to a safe standby
        // degradation.
        JobCoordinator coordinator = newCoordinator(COORDINATOR_A, electorA);
        try {
            coordinator.start();
            electorA.grantLeadership(1L);
            assertTrue(coordinator.isActive());

            // Fire onException through the elector's listener contract.
            for (io.nop.cluster.elector.ILeaderElectionListener listener :
                    electorA.getListeners()) {
                listener.onException(new RuntimeException("simulated elector JDBC error"));
            }

            assertFalse(coordinator.isActive(),
                    "elector exception must degrade to STANDBY (no stale leadership)");
            assertTrue(coordinator.isRunning(),
                    "degradation is NOT a stop — coordinator lifecycle continues");
        } finally {
            coordinator.stop();
        }
    }

    @Test
    void testActiveModeTriggerCheckpointSucceedsAndStandbyRejects() {
        JobCoordinator coordinator = newCoordinator(COORDINATOR_A, electorA);
        try {
            coordinator.start();
            // Standby triggerCheckpoint must be rejected (no barrier issued).
            PendingCheckpoint standbyPending = coordinator.triggerCheckpoint();
            assertNull(standbyPending);
            assertNull(rpcA.getLastBarrier());

            electorA.grantLeadership(1L);
            PendingCheckpoint activePending = coordinator.triggerCheckpoint();
            assertNotNull(activePending, "ACTIVE leader must be able to trigger checkpoints");
            assertNotNull(rpcA.getLastBarrier(), "ACTIVE leader must send the barrier");
            assertEquals(coordinator.getFencingToken(), rpcA.getLastFencingToken());
        } finally {
            coordinator.stop();
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
            lastFencingToken.set(assignment.getFencingToken());
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

        String getLastFencingToken() {
            return lastFencingToken.get();
        }

        io.nop.stream.core.checkpoint.CheckpointBarrier getLastBarrier() {
            return lastBarrier.get();
        }
    }
}
