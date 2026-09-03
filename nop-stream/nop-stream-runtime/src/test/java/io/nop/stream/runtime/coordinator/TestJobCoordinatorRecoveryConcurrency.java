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
 * P1 hardening: verifies {@link JobCoordinator#requestRecovery()} deduplicates
 * concurrent triggers across its two production sources (the failure-detector
 * thread via {@code detectFailures}, and the RPC server thread pool via
 * {@code reportTaskStatus} on a FAILED report). Both sources funnel into
 * {@code requestRecovery()}; this suite asserts that exactly ONE recovery
 * completes (single epoch rotation, single restartCount bump, single assignment
 * round per subtask) and the redundant driver short-circuits with an observable
 * WARN rather than interleaving its clear/register/assign sequence with the
 * winner.
 *
 * <p><strong>Determinism strategy (why these tests are NOT flaky):</strong> the
 * previous implementation deduped via a fencing-epoch snapshot taken BEFORE lock
 * acquisition, which depended on the loser thread snapshotting before the winner
 * rotated the epoch — a timing assumption the JVM scheduler does not guarantee.
 * Replacing it with a CAS on {@code recoveryPending} at the trigger boundary
 * makes the <em>overlapping-concurrent</em> case deterministic, but a
 * start-latch alone still cannot guarantee overlap when the mocks are no-ops
 * (globalRecovery completes in microseconds, so the scheduler can serialize the
 * two drivers and the second CAS observes an already-cleared flag).
 *
 * <p>To eliminate all scheduler dependency, {@link RecordingClusterRegistry}
 * supports a <em>block gate</em>: the test installs two latches, fires driver A,
 * waits until A is blocked <strong>inside {@code assignTask}</strong> (which
 * runs under {@code recoveryLock} via {@code prepareAssignmentsLocked}), then
 * fires driver B. Because A is provably mid-recovery with
 * {@code recoveryPending=true} at that instant, B's CAS(false→true) MUST fail
 * and B MUST short-circuit immediately. If the dedup contract regressed, B would
 * call {@code globalRecovery} and block on {@code recoveryLock} (held by A), and
 * the test's bounded await on B would time out — surfacing the regression
 * deterministically rather than as an intermittent count mismatch.
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
     * Deterministic dedup test: driver A is positioned mid-recovery (blocked
     * inside {@code assignTask} under {@code recoveryLock}) before driver B
     * fires. Asserts exactly one epoch rotation, one restartCount bump, and one
     * assignment round (2 assignTask calls — one per subtask). The redundant
     * driver B short-circuits via the {@code recoveryPending} CAS.
     *
     * <p>If the CAS dedup regressed, B would proceed into {@code globalRecovery}
     * and block on {@code recoveryLock} held by A; the bounded await on
     * {@code bDone} would then time out, failing the test deterministically
     * (instead of producing an intermittent restartCount=2 as the old
     * epoch-snapshot guard did).
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

        // Install the block gate AFTER the initial assignTasks() so only
        // recovery-phase assignTask calls are gated.
        CountDownLatch assignEntered = new CountDownLatch(1);
        CountDownLatch releaseAssign = new CountDownLatch(1);
        clusterRegistry.installBlockGate(assignEntered, releaseAssign);

        AtomicInteger errors = new AtomicInteger();
        CountDownLatch aDone = new CountDownLatch(1);

        // Driver A: enters globalRecovery, acquires recoveryLock, reaches
        // assignTask (called from prepareAssignmentsLocked UNDER the lock) and
        // blocks there. recoveryPending is still TRUE at this point (it is only
        // cleared in globalRecovery's finally, which has NOT run yet).
        threadPool.submit(() -> {
            try {
                coordinator.requestRecovery();
            } catch (Throwable t) {
                errors.incrementAndGet();
                fail("driver A threw: " + t, t);
            } finally {
                aDone.countDown();
            }
        });

        // Wait until A is provably mid-recovery (blocked inside assignTask,
        // holding recoveryLock, recoveryPending still true).
        assertTrue(assignEntered.await(15, TimeUnit.SECONDS),
                "driver A did not reach assignTask (mid-recovery) in time");

        // Driver B: fired WHILE A is mid-recovery. Its CAS(false→true) must
        // observe recoveryPending=true and short-circuit immediately. If the CAS
        // incorrectly succeeded, B would call globalRecovery and block on
        // recoveryLock (held by A), and this bounded await would time out —
        // surfacing the regression deterministically.
        CountDownLatch bDone = new CountDownLatch(1);
        threadPool.submit(() -> {
            try {
                coordinator.requestRecovery();
            } catch (Throwable t) {
                errors.incrementAndGet();
                fail("driver B threw: " + t, t);
            } finally {
                bDone.countDown();
            }
        });
        assertTrue(bDone.await(5, TimeUnit.SECONDS),
                "driver B must short-circuit and return immediately (not block on recoveryLock) "
                        + "— if this timed out, the recoveryPending CAS dedup regressed and B entered globalRecovery");

        // Release A: assignTask returns, prepareAssignmentsLocked finishes,
        // finally clears recoveryPending + unlocks, fan-out runs, globalRecovery
        // returns.
        releaseAssign.countDown();
        assertTrue(aDone.await(15, TimeUnit.SECONDS), "driver A did not finish in time");
        assertEquals(0, errors.get(), "no recovery driver should throw");

        // Exactly one restartCount bump (the loser short-circuited without bumping).
        assertEquals(1, coordinator.getRestartCount(),
                "concurrent requestRecovery must bump restartCount exactly once, got "
                        + coordinator.getRestartCount());

        // Exactly one epoch rotation.
        assertEquals(1, coordinator.getRecoveryGen() - gen0,
                "concurrent requestRecovery must rotate the fencing epoch exactly once, got delta "
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
     * 接线验证: the clear → register → assign sequence is atomic — a redundant
     * driver cannot observe a half-cleared working set. After both drivers
     * finish, the coordinator's working set is consistent: exactly one entry per
     * vertex in taskAssignmentMap, and every assignment carries the current
     * fencing epoch.
     *
     * <p>This test is dedup-insensitive: its assertions hold whether the two
     * drivers dedupe to one recovery or (in a pathological scheduling) both
     * complete, because each recovery clears-then-reassigns and the final
     * working set is always consistent. The dedup-only guarantee is exercised by
     * {@link #concurrentGlobalRecovery_serializesToOneRotation()}.
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
                coordinator.requestRecovery();
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

    /**
     * Records every assignTask call so the test can assert non-interleaving.
     * Optionally supports a <strong>block gate</strong> ({@link #installBlockGate})
     * so a test can deterministically position one driver mid-recovery (blocked
     * inside assignTask, which runs under {@code recoveryLock}) before firing a
     * second driver.
     */
    static final class RecordingClusterRegistry implements ClusterRegistry {
        final AtomicInteger assignTaskCount = new AtomicInteger();
        final List<String> recoverySubtaskKeys = new java.util.concurrent.CopyOnWriteArrayList<>();
        final Map<String, NodeInfo> nodes = new java.util.concurrent.ConcurrentHashMap<>();

        // Block gate: when installed, assignTask counts down assignEnteredGate on
        // first entry (signalling "I'm mid-recovery") and then awaits
        // releaseAssignGate before returning. Both latches are nullable; a null
        // gate means assignTask runs straight through (the default).
        private volatile CountDownLatch assignEnteredGate;
        private volatile CountDownLatch releaseAssignGate;

        void installBlockGate(CountDownLatch assignEntered, CountDownLatch releaseAssign) {
            this.assignEnteredGate = assignEntered;
            this.releaseAssignGate = releaseAssign;
        }

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
            // Signal "mid-recovery" on first entry, then block until the test
            // releases. Subsequent calls find both latches already at zero and
            // return immediately.
            CountDownLatch entered = assignEnteredGate;
            if (entered != null) {
                entered.countDown();
            }
            CountDownLatch release = releaseAssignGate;
            if (release != null) {
                try {
                    assertTrue(release.await(15, TimeUnit.SECONDS),
                            "assignTask block gate was not released within 15s (test deadlock?)");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
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
        public void cancelTask(String jobId, String vertexId, int subtaskIndex, long fencingEpoch) {
        }

        @Override
        public void updateFencingToken(long fencingEpoch) {
        }
    }
}
