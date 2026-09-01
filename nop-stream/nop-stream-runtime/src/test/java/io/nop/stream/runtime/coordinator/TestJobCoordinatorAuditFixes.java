/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.coordinator;

import io.nop.stream.core.checkpoint.CheckpointConfig;
import io.nop.stream.core.checkpoint.CheckpointIDCounter;
import io.nop.stream.core.checkpoint.CheckpointType;
import io.nop.stream.core.checkpoint.JobTerminationMode;
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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused tests for the runtime-audit fixes (2026-09-01, roadmap item 8):
 * R-2 standby terminate gate, R-3 standby abortCheckpoint gate, R-5 liveness
 * monotonic-merge, R-6 assignment fan-out per-dispatch containment, plus the
 * G6 (standby detectFailures no-op) and G7 (standby abort-handler branch)
 * coverage gaps from the G24/G25 gate matrix.
 */
class TestJobCoordinatorAuditFixes {

    private static final String JOB_ID = "audit-fix-job";
    private static final String COORDINATOR_A = "coordinator-A";
    private static final String COORDINATOR_B = "coordinator-B";

    @TempDir
    Path tempDir;

    private OrderedMockClusterRegistry clusterRegistry;
    private CheckpointCoordinator checkpointCoordinator;
    private Map<String, IStreamTaskRpcService> taskRpcServices;
    private RecordingTaskRpcService rpcA;
    private RecordingTaskRpcService rpcB;
    private RecordingTaskRpcService rpcC;
    private DeploymentPlan deploymentPlan;
    private final List<JobCoordinator> coordinators = new ArrayList<>();

    @BeforeEach
    void setUp() {
        clusterRegistry = new OrderedMockClusterRegistry();

        LocalFileCheckpointStorage storage = new LocalFileCheckpointStorage(tempDir.toString());
        CheckpointIDCounter idCounter = new CheckpointIDCounter();
        CheckpointConfig config = CheckpointConfig.builder()
                .checkpointEnabled(true)
                .checkpointInterval(1000L)
                .checkpointTimeout(10000L)
                .maxConcurrentCheckpoints(2)
                .build();
        checkpointCoordinator = new CheckpointCoordinator(JOB_ID, "pipeline-0", idCounter, storage, config);

        rpcA = new RecordingTaskRpcService();
        rpcB = new RecordingTaskRpcService();
        rpcC = new RecordingTaskRpcService();
        taskRpcServices = new LinkedHashMap<>();
        taskRpcServices.put("node-1", rpcA);
        taskRpcServices.put("node-2", rpcB);
        taskRpcServices.put("node-3", rpcC);

        clusterRegistry.registerNode("node-1", "localhost:8081", 4);
        clusterRegistry.registerNode("node-2", "localhost:8082", 4);
        clusterRegistry.registerNode("node-3", "localhost:8083", 4);

        Map<String, PartitionedPlan.VertexPlan> vertexPlans = new LinkedHashMap<>();
        vertexPlans.put("source", new PartitionedPlan.VertexPlan("source", 1, null));
        vertexPlans.put("mid", new PartitionedPlan.VertexPlan("mid", 1, null));
        vertexPlans.put("sink", new PartitionedPlan.VertexPlan("sink", 1, null));
        List<PartitionedPlan.EdgePlan> edgePlans = new ArrayList<>();
        edgePlans.add(new PartitionedPlan.EdgePlan("source", "mid",
                io.nop.stream.core.execution.plan.PartitionPolicy.FORWARD));
        edgePlans.add(new PartitionedPlan.EdgePlan("mid", "sink",
                io.nop.stream.core.execution.plan.PartitionPolicy.FORWARD));
        PartitionedPlan partitionedPlan = new PartitionedPlan(
                JOB_ID, "pipeline-0", vertexPlans, edgePlans, null, null);
        deploymentPlan = new DeploymentPlan(
                JOB_ID, "pipeline-0", partitionedPlan,
                "local", "memory", "local", null, null);
    }

    @AfterEach
    void tearDown() {
        for (JobCoordinator c : coordinators) {
            try {
                c.stop();
            } catch (Exception ignored) {
                // best-effort cleanup
            }
        }
        try {
            checkpointCoordinator.shutdown();
        } catch (Exception ignored) {
            // best-effort cleanup
        }
    }

    private JobCoordinator newNonHaCoordinator() {
        JobCoordinator c = new JobCoordinator(
                JOB_ID, COORDINATOR_A, deploymentPlan,
                clusterRegistry, checkpointCoordinator, taskRpcServices);
        c.setTerminationCheckpointTimeoutMs(500L);
        coordinators.add(c);
        return c;
    }

    private JobCoordinator newHaCoordinator(String coordId, TestLeaderElector elector) {
        JobCoordinator c = new JobCoordinator(
                JOB_ID, coordId, deploymentPlan,
                clusterRegistry, checkpointCoordinator, taskRpcServices);
        c.setLeaderElector(elector);
        c.setTerminationCheckpointTimeoutMs(500L);
        coordinators.add(c);
        return c;
    }

    /**
     * R-2: a standby coordinator must reject terminate() — terminate(CANCEL) on
     * a standby previously set CANCELED and stop()'d the standby instance (and,
     * in the shared-CheckpointCoordinator single-JVM HA topology, mutated the
     * active leader's coordinator state).
     */
    @Test
    void standbyTerminateIsRejected() {
        TestLeaderElector elector = new TestLeaderElector("host-standby");
        JobCoordinator standby = newHaCoordinator(COORDINATOR_B, elector);
        standby.start();
        assertFalse(standby.isActive(), "coordinator starts in STANDBY under an elector");

        standby.terminate(JobTerminationMode.CANCEL);

        assertTrue(standby.isRunning(),
                "standby terminate must NOT stop the standby instance (was a fencing bypass before R-2)");

        // DRAIN-style terminate must equally be rejected on standby: no doomed
        // pending checkpoint may be created by a standby.
        standby.terminate(JobTerminationMode.DRAIN);
        assertTrue(standby.isRunning(), "standby terminate(DRAIN) must also be a no-op");
        assertEquals(0, rpcA.cancelCount.get() + rpcB.cancelCount.get() + rpcC.cancelCount.get(),
                "standby terminate must not fire any task RPCs");
    }

    /** R-2 companion: an ACTIVE (non-HA) coordinator's terminate still works. */
    @Test
    void activeTerminateStillStops() {
        JobCoordinator active = newNonHaCoordinator();
        active.start();
        assertTrue(active.isActive(), "non-HA start goes ACTIVE");

        active.terminate(JobTerminationMode.CANCEL);

        assertFalse(active.isRunning(), "active terminate(CANCEL) must still stop the job");
    }

    /**
     * R-3: a standby coordinator must not abort the (potentially shared, in the
     * single-JVM HA topology) pending checkpoint via abortCheckpoint.
     */
    @Test
    void standbyAbortCheckpointIsNoOp() {
        TestLeaderElector elector = new TestLeaderElector("host-standby");
        JobCoordinator standby = newHaCoordinator(COORDINATOR_B, elector);
        standby.start();
        assertFalse(standby.isActive());

        seedTasksToAcknowledge();
        PendingCheckpoint pending = checkpointCoordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
        assertNotNull(pending);

        standby.abortCheckpoint(pending.getCheckpointId());

        assertEquals(PendingCheckpoint.Status.RUNNING, pending.getStatus().get(),
                "standby abortCheckpoint must not abort the shared pending checkpoint");
        assertFalse(pending.isDisposed());
    }

    /**
     * G7 (gate-matrix gap): the DISTRIBUTED abort handler registered by a
     * STANDBY coordinator must refuse to fire cancelTask RPCs when the shared
     * CheckpointCoordinator aborts a pending checkpoint.
     */
    @Test
    void standbyAbortHandlerDoesNotFireCancelTask() {
        TestLeaderElector elector = new TestLeaderElector("host-standby");
        JobCoordinator standby = newHaCoordinator(COORDINATOR_B, elector);
        standby.start();
        standby.registerDistributedAbortHandler();
        assertFalse(standby.isActive());

        seedTasksToAcknowledge();
        PendingCheckpoint pending = checkpointCoordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
        assertNotNull(pending);

        checkpointCoordinator.abortPendingCheckpoint(pending, "audit-fix G7 standby abort");

        assertEquals(PendingCheckpoint.Status.ABORTED, pending.getStatus().get(),
                "the abort itself proceeds (driven by the shared coordinator)");
        assertEquals(0, rpcA.cancelCount.get() + rpcB.cancelCount.get() + rpcC.cancelCount.get(),
                "a STANDBY coordinator's abort handler must never fire cancelTask RPCs");
    }

    private void seedTasksToAcknowledge() {
        checkpointCoordinator.setTasksToAcknowledge(java.util.Arrays.asList(
                new io.nop.stream.core.checkpoint.TaskLocation(JOB_ID, "pipeline-0", "source", 0),
                new io.nop.stream.core.checkpoint.TaskLocation(JOB_ID, "pipeline-0", "sink", 0)));
    }

    /**
     * G6 (gate-matrix gap): a STANDBY coordinator's failure detector must be a
     * no-op even when liveness data is genuinely stalled — liveness-driven
     * recovery belongs to the active leader only. The stalled liveness is
     * seeded through the legitimate ACTIVE reporting path (a second, active
     * coordinator sharing the same registry/RPC topology).
     */
    @Test
    void standbyDetectFailuresIsNoOpOnStalledLiveness() {
        JobCoordinator active = newNonHaCoordinator();
        active.start();
        active.assignTasks();
        // Seed a genuinely stalled liveness value via the ACTIVE path (old
        // timestamp far below the cutoff).
        active.setTaskTimeoutMs(1000L);
        List<TaskProgress> stalled = Collections.singletonList(
                new TaskProgress("source", 0, 1, System.currentTimeMillis() - 60_000L));
        active.reportNodeTaskLiveness("node-1", stalled);
        assertEquals(1, active.getSubtaskLivenessCount());

        TestLeaderElector elector = new TestLeaderElector("host-standby");
        JobCoordinator standby = newHaCoordinator(COORDINATOR_B, elector);
        standby.start();
        standby.setTaskTimeoutMs(1000L);
        assertFalse(standby.isActive());

        long restartsBefore = standby.getRestartCount();
        long epochBefore = standby.getFencingEpoch();
        long assignmentsBefore = totalAssignments();

        standby.detectFailures();

        assertEquals(restartsBefore, standby.getRestartCount(),
                "standby detectFailures must not trigger recovery even on stalled liveness");
        assertEquals(epochBefore, standby.getFencingEpoch(),
                "standby detectFailures must not rotate the fencing epoch");
        assertEquals(assignmentsBefore, totalAssignments(),
                "standby detectFailures must not re-issue assignments");
    }

    /**
     * R-5: interleaved liveness deliveries must never regress the recorded
     * value — the merge-based monotonic max keeps the newest timestamp even
     * under concurrent out-of-order delivery (the old
     * getOrDefault→compare→put sequence could let an older report overwrite a
     * newer one, causing spurious stall detection and spurious recovery).
     */
    @Test
    void livenessMergeIsMonotonicUnderConcurrentDelivery() throws Exception {
        JobCoordinator active = newNonHaCoordinator();
        active.start();

        int iterations = 2000;
        long newer = 1_000_000L;
        long older = 500_000L;
        CountDownLatch start = new CountDownLatch(1);
        Thread tNew = new Thread(() -> {
            try {
                start.await();
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            for (int i = 0; i < iterations; i++) {
                active.reportNodeTaskLiveness("node-1",
                        Collections.singletonList(new TaskProgress("source", 0, 1, newer)));
            }
        });
        Thread tOld = new Thread(() -> {
            try {
                start.await();
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            for (int i = 0; i < iterations; i++) {
                active.reportNodeTaskLiveness("node-1",
                        Collections.singletonList(new TaskProgress("source", 0, 1, older)));
            }
        });
        tNew.start();
        tOld.start();
        start.countDown();
        tNew.join(10_000);
        tOld.join(10_000);

        assertEquals(1, active.getSubtaskLivenessCount());
        assertEquals(newer, active.getSubtaskLivenessValue("source/0"),
                "the monotonic max must survive interleaved older deliveries");
    }

    /**
     * R-6: one failing assignment dispatch must not abort the remaining
     * fan-out — every non-failing node still receives its assignment and
     * assignTasks() completes without propagating the failure.
     */
    @Test
    void assignmentFanOutSurvivesPerDispatchFailure() {
        rpcB.failReceiveAssignment = true;

        JobCoordinator active = newNonHaCoordinator();
        active.start();

        assertDoesNotThrow(active::assignTasks,
                "a single unreachable node must not abort the assignment fan-out");

        assertTrue(rpcA.assignments.size() >= 1, "node-1 (before the failing dispatch) must receive its assignment");
        assertTrue(rpcC.assignments.size() >= 1,
                "node-3 (after the failing dispatch in registration order) must still receive its assignment "
                        + "(before R-6 the fan-out aborted at the first failure)");
        assertEquals(0, rpcB.assignments.size(), "the failing node itself receives nothing");
    }

    private long totalAssignments() {
        return rpcA.assignments.size() + rpcB.assignments.size() + rpcC.assignments.size();
    }

    // ==================== Fixtures ====================

    /**
     * Insertion-ordered registry so round-robin assignment is deterministic
     * (node-1, node-2, node-3 in registration order).
     */
    static class OrderedMockClusterRegistry implements ClusterRegistry {
        final Map<String, NodeInfo> nodes = new LinkedHashMap<>();

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

    static class RecordingTaskRpcService implements IStreamTaskRpcService {
        final List<TaskAssignment> assignments = new java.util.concurrent.CopyOnWriteArrayList<>();
        final AtomicInteger cancelCount = new AtomicInteger();
        final AtomicLong lastFencingEpoch = new AtomicLong();
        volatile boolean failReceiveAssignment;

        @Override
        public void receiveAssignment(TaskAssignment assignment) {
            if (failReceiveAssignment) {
                throw new IllegalStateException("simulated unreachable node (audit-fix R-6)");
            }
            assignments.add(assignment);
            lastFencingEpoch.set(assignment.getFencingEpoch());
        }

        @Override
        public void triggerCheckpoint(io.nop.stream.core.checkpoint.CheckpointBarrier barrier, long fencingEpoch) {
            lastFencingEpoch.set(fencingEpoch);
        }

        @Override
        public void cancelTask(String jobId, String vertexId, int subtaskIndex) {
            cancelCount.incrementAndGet();
        }

        @Override
        public void updateFencingToken(long fencingEpoch) {
            lastFencingEpoch.set(fencingEpoch);
        }
    }
}
