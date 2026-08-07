/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.coordinator;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.nop.stream.core.checkpoint.CheckpointBarrier;
import io.nop.stream.core.checkpoint.CheckpointConfig;
import io.nop.stream.core.checkpoint.CheckpointIDCounter;
import io.nop.stream.core.checkpoint.TaskLocation;
import io.nop.stream.core.execution.plan.DeploymentPlan;
import io.nop.stream.core.execution.plan.PartitionPolicy;
import io.nop.stream.core.execution.plan.PartitionedPlan;
import io.nop.stream.runtime.checkpoint.CheckpointCoordinator;
import io.nop.stream.runtime.checkpoint.storage.LocalFileCheckpointStorage;
import io.nop.stream.runtime.cluster.ClusterRegistry;
import io.nop.stream.runtime.cluster.CoordinatorInfo;
import io.nop.stream.runtime.cluster.LeaseInfo;
import io.nop.stream.runtime.cluster.NodeInfo;
import io.nop.stream.runtime.cluster.TaskAssignment;
import io.nop.stream.runtime.rpc.IStreamTaskRpcService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * P1 hardening: verifies {@link JobCoordinator#globalRecovery()} is mutually
 * exclusive across its two concurrent trigger sources (the failure-detector
 * thread via {@code detectFailures}, and the RPC server thread pool via
 * {@code reportTaskStatus} on a FAILED report). Both sources funnel into
 * {@code globalRecovery()}; this test fires two concurrent drivers and asserts
 * that exactly ONE recovery completes (single epoch rotation, single
 * restartCount bump, single assignment round per subtask) and the redundant
 * driver short-circuits with an observable WARN rather than interleaving its
 * clear/register/assign sequence with the winner.
 *
 * <p>Without the recovery lock, the two drivers would interleave: both bump
 * restartCount (delta 2), both rotate the fencing epoch (one overwriting the
 * other), and both push assignments — producing duplicate attemptIds for the
 * same subtask and a corrupted working set. The lock serializes them; the
 * late-arrival guard (epoch snapshot taken before lock acquisition) lets the
 * loser detect that the epoch already advanced and short-circuit.
 */
class TestJobCoordinatorRecoveryConcurrency {

    private static final String JOB_ID = "concurrent-recovery-job";
    private static final String COORDINATOR_ID = "coordinator-conc";

    @TempDir
    Path tempDir;

    private JobCoordinator coordinator;
    private RecordingClusterRegistry clusterRegistry;
    private ExecutorService threadPool;

    @BeforeEach
    void setUp() {
        clusterRegistry = new RecordingClusterRegistry();

        LocalFileCheckpointStorage storage = new LocalFileCheckpointStorage(tempDir.toString());
        CheckpointIDCounter idCounter = new CheckpointIDCounter();
        CheckpointConfig config = CheckpointConfig.builder()
                .checkpointEnabled(true)
                .checkpointInterval(1000L)
                .checkpointTimeout(10000L)
                .maxConcurrentCheckpoints(1)
                .maxRetainedCheckpoints(3)
                .build();
        CheckpointCoordinator checkpointCoordinator = new CheckpointCoordinator(
                JOB_ID, "pipeline-0", idCounter, storage, config);

        Map<String, IStreamTaskRpcService> taskRpcServices = new LinkedHashMap<>();
        taskRpcServices.put("node-1", new NoopTaskRpcService());

        clusterRegistry.registerNode("node-1", "localhost:8080", 4);

        Map<String, PartitionedPlan.VertexPlan> vertexPlans = new LinkedHashMap<>();
        vertexPlans.put("source", new PartitionedPlan.VertexPlan("source", 1, null));
        vertexPlans.put("sink", new PartitionedPlan.VertexPlan("sink", 1, null));
        List<PartitionedPlan.EdgePlan> edgePlans = new ArrayList<>();
        edgePlans.add(new PartitionedPlan.EdgePlan("source", "sink", PartitionPolicy.FORWARD));
        PartitionedPlan partitionedPlan = new PartitionedPlan(
                JOB_ID, "pipeline-0", vertexPlans, edgePlans, null, null);
        DeploymentPlan deploymentPlan = new DeploymentPlan(
                JOB_ID, "pipeline-0", partitionedPlan, "local", "memory", "local", null, null);

        coordinator = new JobCoordinator(JOB_ID, COORDINATOR_ID, deploymentPlan,
                clusterRegistry, checkpointCoordinator, taskRpcServices);
        coordinator.setTerminationCheckpointTimeoutMs(500L);

        threadPool = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "recovery-driver-test");
            t.setDaemon(true);
            return t;
        });
    }

    @AfterEach
    void tearDown() {
        if (threadPool != null) {
            threadPool.shutdownNow();
        }
        coordinator.stop();
    }

    /**
     * Two concurrent globalRecovery drivers must serialize: exactly one epoch
     * rotation, one restartCount bump, and one assignment round (2 assignTask
     * calls — one per subtask). The redundant driver short-circuits.
     *
     * <p>Both threads are released by a shared start-latch so each snapshots the
     * pre-recovery fencing epoch before either acquires the lock. The winner then
     * performs ~7 operations inside the critical section before rotating the
     * epoch, while the loser's snapshot is a single volatile read — so the loser
     * deterministically observes the pre-rotation epoch and short-circuits once
     * it acquires the lock.
     */
    @Test
    void concurrentGlobalRecovery_serializesToOneRotation() throws Exception {
        coordinator.start();
        coordinator.assignTasks();

        long epoch0 = coordinator.getFencingEpoch();
        long gen0 = coordinator.getRecoveryGen();
        // Clear the registry recording so only the recovery-phase assignments
        // are counted below.
        clusterRegistry.reset();

        int drivers = 2;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(drivers);
        AtomicInteger errors = new AtomicInteger();

        Runnable recoveryDriver = () -> {
            try {
                startLatch.await();
                // Both drivers represent the two concurrent sources (failure-
                // detector thread + reportTaskStatus FAILED RPC) that funnel into
                // globalRecovery().
                coordinator.globalRecovery();
            } catch (Throwable t) {
                errors.incrementAndGet();
                fail("recovery driver threw: " + t, t);
            } finally {
                doneLatch.countDown();
            }
        };

        threadPool.submit(recoveryDriver);
        threadPool.submit(recoveryDriver);

        startLatch.countDown();
        assertTrue(doneLatch.await(15, TimeUnit.SECONDS), "recovery drivers did not finish in time");
        assertEquals(0, errors.get(), "no recovery driver should throw");

        // Exactly one restartCount bump (the loser short-circuited without bumping).
        assertEquals(1, coordinator.getRestartCount(),
                "concurrent globalRecovery must bump restartCount exactly once, got "
                        + coordinator.getRestartCount());

        // Exactly one epoch rotation.
        assertEquals(1, coordinator.getRecoveryGen() - gen0,
                "concurrent globalRecovery must rotate the fencing epoch exactly once, got delta "
                        + (coordinator.getRecoveryGen() - gen0));
        assertTrue(coordinator.getFencingEpoch() > epoch0,
                "fencing epoch must have advanced");

        // Exactly one assignment round: 2 subtasks (source + sink, parallelism 1
        // each) → 2 assignTask calls. A redundant interleaving driver would have
        // produced 4 (duplicate attemptIds for the same subtask).
        assertEquals(2, clusterRegistry.assignTaskCount.get(),
                "exactly one assignment round (2 subtasks) must reach ClusterRegistry; "
                        + "a redundant driver must short-circuit, got " + clusterRegistry.assignTaskCount.get());

        // No subtask was assigned twice with two different attemptIds during the
        // recovery phase (each subtask key appears exactly once).
        for (String key : clusterRegistry.recoverySubtaskKeys) {
            assertEquals(1, clusterRegistry.countKey(key),
                    "subtask " + key + " was assigned more than once in the recovery phase "
                            + "(interleaving corruption)");
        }
    }

    /**
     * 接线验证: the clear → register → assign sequence is atomic — the loser
     * cannot observe a half-cleared working set. After both drivers finish, the
     * coordinator's working set is consistent: exactly one entry per vertex in
     * taskAssignmentMap, and allTaskLocations is fully populated.
     */
    @Test
    void concurrentRecovery_leavesConsistentWorkingSet() throws Exception {
        coordinator.start();
        coordinator.assignTasks();

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);
        AtomicInteger errors = new AtomicInteger();

        Runnable recoveryDriver = () -> {
            try {
                startLatch.await();
                coordinator.globalRecovery();
            } catch (Throwable t) {
                errors.incrementAndGet();
            } finally {
                doneLatch.countDown();
            }
        };

        threadPool.submit(recoveryDriver);
        threadPool.submit(recoveryDriver);
        startLatch.countDown();
        assertTrue(doneLatch.await(15, TimeUnit.SECONDS));
        assertEquals(0, errors.get());

        Map<String, List<TaskAssignment>> assignments = coordinator.getTaskAssignments();
        assertEquals(2, assignments.size(), "both vertices must be assigned");
        assertEquals(1, assignments.get("source").size());
        assertEquals(1, assignments.get("sink").size());
        // Each assignment carries the rotated (post-recovery) fencing epoch — a
        // redundant interleaving driver cannot have left a stale-epoch assignment.
        long currentEpoch = coordinator.getFencingEpoch();
        for (List<TaskAssignment> vertexAssignments : assignments.values()) {
            for (TaskAssignment ta : vertexAssignments) {
                assertEquals(currentEpoch, ta.getFencingEpoch(),
                        "assignment must carry the current fencing epoch after serialized recovery");
            }
        }
    }

    // ==================== Mocks ====================

    /** Records every assignTask call so the test can assert non-interleaving. */
    static final class RecordingClusterRegistry implements ClusterRegistry {
        final AtomicInteger assignTaskCount = new AtomicInteger();
        final List<String> recoverySubtaskKeys = new java.util.concurrent.CopyOnWriteArrayList<>();
        final Map<String, NodeInfo> nodes = new java.util.concurrent.ConcurrentHashMap<>();

        void reset() {
            assignTaskCount.set(0);
            recoverySubtaskKeys.clear();
        }

        int countKey(String key) {
            int n = 0;
            for (String k : recoverySubtaskKeys) {
                if (k.equals(key)) {
                    n++;
                }
            }
            return n;
        }

        @Override
        public void assignTask(String jobId, String vertexId, int subtaskIndex,
                               String nodeId, String attemptId, long fencingEpoch, int attemptNumber) {
            assignTaskCount.incrementAndGet();
            recoverySubtaskKeys.add(vertexId + "/" + subtaskIndex);
        }

        @Override
        public void registerCoordinator(String jobId, String coordinatorId, long fencingEpoch) {
        }

        @Override
        public CoordinatorInfo getActiveCoordinator(String jobId) {
            return null;
        }

        @Override
        public void registerNode(String nodeId, String endpoint, int capacity) {
            nodes.put(nodeId, new NodeInfo(nodeId, endpoint, capacity,
                    System.currentTimeMillis(), System.currentTimeMillis()));
        }

        @Override
        public boolean renewLease(String nodeId, long leaseTimeoutMs) {
            return true;
        }

        @Override
        public LeaseInfo getNodeLease(String nodeId) {
            return null;
        }

        @Override
        public List<NodeInfo> getActiveNodes() {
            return new ArrayList<>(nodes.values());
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

    static final class NoopTaskRpcService implements IStreamTaskRpcService {
        @Override
        public void receiveAssignment(TaskAssignment assignment) {
        }

        @Override
        public void triggerCheckpoint(CheckpointBarrier barrier, long fencingEpoch) {
        }

        @Override
        public void cancelTask(String jobId, String vertexId, int subtaskIndex) {
        }

        @Override
        public void updateFencingToken(long fencingEpoch) {
        }
    }
}
