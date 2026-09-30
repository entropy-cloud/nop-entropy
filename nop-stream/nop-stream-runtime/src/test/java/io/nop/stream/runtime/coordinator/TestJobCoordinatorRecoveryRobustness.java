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
import io.nop.stream.core.checkpoint.JobTerminationMode;
import io.nop.stream.core.checkpoint.TaskLocation;
import io.nop.stream.core.execution.plan.DeploymentPlan;
import io.nop.stream.core.execution.plan.PartitionedPlan;
import io.nop.stream.runtime.checkpoint.CheckpointCoordinator;
import io.nop.stream.runtime.checkpoint.storage.LocalFileCheckpointStorage;
import io.nop.stream.runtime.cluster.ClusterRegistry;
import io.nop.stream.runtime.cluster.NodeInfo;
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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused tests for the plan 368 Phase 3 coordination/concurrency fixes
 * (R5-CC-01/03/04/05/11 + R5-REG-02):
 * <ul>
 *   <li>CC-01 — a failing fencing-token push must not abort the recovery
 *       (per-node isolation) and an empty assignment working set after a first
 *       assignment must re-trigger recovery (sentinel) instead of wedging.</li>
 *   <li>CC-03 — stop()/terminate(CANCEL) fans a cancelTask RPC out to every
 *       assigned task, best-effort.</li>
 *   <li>CC-04 — recovery clears the previous generation's subtaskLiveness
 *       entries (no phantom stall from frozen timestamps).</li>
 *   <li>CC-05 — heartbeats from a superseded attempt are rejected.</li>
 *   <li>CC-11 — terminateWithTerminalSavepoint restores the interrupt flag.</li>
 *   <li>REG-02 — an in-flight heartbeat must not resurrect a COMPLETED task's
 *       removed liveness entry.</li>
 * </ul>
 */
class TestJobCoordinatorRecoveryRobustness {

    private static final String JOB_ID = "r5-p3-job";
    private static final String COORDINATOR_ID = "coordinator-r5p3";

    @TempDir
    Path tempDir;

    private ThrowingAssignRegistry clusterRegistry;
    private CheckpointCoordinator checkpointCoordinator;
    private Map<String, IStreamTaskRpcService> taskRpcServices;
    private CoordinatorTestSupport.RecordingTaskRpcService rpcA;
    private CoordinatorTestSupport.RecordingTaskRpcService rpcB;
    private CoordinatorTestSupport.RecordingTaskRpcService rpcC;
    private DeploymentPlan deploymentPlan;
    private final List<JobCoordinator> coordinators = new ArrayList<>();

    @BeforeEach
    void setUp() {
        clusterRegistry = new ThrowingAssignRegistry();

        LocalFileCheckpointStorage storage = new LocalFileCheckpointStorage(tempDir.toString());
        CheckpointIDCounter idCounter = new CheckpointIDCounter();
        CheckpointConfig config = CheckpointConfig.builder()
                .checkpointEnabled(true)
                .checkpointInterval(1000L)
                .checkpointTimeout(10000L)
                .maxConcurrentCheckpoints(2)
                .build();
        checkpointCoordinator = new CheckpointCoordinator(JOB_ID, "pipeline-0", idCounter, storage, config);

        rpcA = new CoordinatorTestSupport.RecordingTaskRpcService();
        rpcB = new CoordinatorTestSupport.RecordingTaskRpcService();
        rpcC = new CoordinatorTestSupport.RecordingTaskRpcService();
        taskRpcServices = new LinkedHashMap<>();
        taskRpcServices.put("node-1", rpcA);
        taskRpcServices.put("node-2", rpcB);
        taskRpcServices.put("node-3", rpcC);

        clusterRegistry.registerNode("node-1", "localhost:8081", 4);
        clusterRegistry.registerNode("node-2", "localhost:8082", 4);
        clusterRegistry.registerNode("node-3", "localhost:8083", 4);

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

    private JobCoordinator newCoordinator() {
        JobCoordinator c = new JobCoordinator(
                JOB_ID, COORDINATOR_ID, deploymentPlan,
                clusterRegistry, checkpointCoordinator, taskRpcServices);
        c.setTerminationCheckpointTimeoutMs(500L);
        coordinators.add(c);
        return c;
    }

    private long totalAssignments() {
        return rpcA.assignments.size() + rpcB.assignments.size() + rpcC.assignments.size();
    }

    // ==================== CC-01 ====================

    /**
     * CC-01: one node rejecting updateFencingToken must NOT abort the recovery.
     * Pre-fix the exception escaped globalRecovery after the assignment working
     * set had been cleared — the job stayed "active but zero assignments" with
     * no detectable failure (permanent wedge). Post-fix the push failure is
     * contained, the recovery completes, and the working set is re-materialized.
     */
    @Test
    void fencingPushFailureDoesNotAbortRecovery() {
        JobCoordinator coordinator = newCoordinator();
        coordinator.start();
        coordinator.assignTasks();
        assertEquals(2, totalAssignments());
        assertEquals(0, coordinator.getRestartCount());

        // Inject the failure AFTER start(): start()'s initial epoch sync uses
        // the same RPC and legitimately fails fast; the CC-01 subject is the
        // recovery-path rotation push.
        rpcB.failUpdateFencingToken = true;

        assertDoesNotThrow(() -> coordinator.globalRecovery(),
                "a failed per-node fencing push must not abort the recovery (CC-01)");

        assertEquals(1, coordinator.getRestartCount(), "the recovery must have completed");
        assertFalse(coordinator.getTaskAssignments().isEmpty(),
                "the assignment working set must be re-materialized (no zero-assignment wedge)");
        assertEquals(4, totalAssignments(),
                "the new generation's assignments must have been fanned out despite the failing node");
    }

    /**
     * CC-01 bottom line: a recovery that aborts AFTER the working set was
     * cleared (here: the registry assignment write fails for the rotated epoch)
     * leaves the map empty; the detectFailures sentinel must re-trigger
     * recovery so the assignments are re-materialized instead of wedging.
     */
    @Test
    void emptyWorkingSetSentinelRetriggersRecovery() {
        // Fail the registry assignment write exactly for the first rotated epoch.
        clusterRegistry.failAssignForEpoch = 2L;
        JobCoordinator coordinator = newCoordinator();
        coordinator.start();
        coordinator.assignTasks();
        assertFalse(coordinator.getTaskAssignments().isEmpty());
        assertEquals(0, coordinator.getRestartCount());

        // Recovery #1 aborts mid-flight: epoch rotated to 2, working set cleared,
        // then prepareAssignmentsLocked throws on the registry write.
        assertThrowsAny(coordinator::globalRecovery);
        assertTrue(coordinator.getTaskAssignments().isEmpty(),
                "the aborted recovery leaves the working set empty");

        // Next detector ticks: the sentinel observes active + empty + everAssigned
        // and re-triggers recovery. Recovery #2 rotates to epoch 3, for which the
        // registry write succeeds → assignments re-materialized.
        coordinator.detectFailures();
        assertEquals(2, coordinator.getRestartCount(),
                "the sentinel must have re-triggered recovery (CC-01)");
        assertFalse(coordinator.getTaskAssignments().isEmpty(),
                "assignments re-materialized after the sentinel-driven recovery");
    }

    /**
     * The sentinel must NOT fire for a fresh coordinator that has never
     * materialized assignments (start → assignTasks is caller-driven and may
     * legitimately take several detector ticks).
     */
    @Test
    void sentinelDoesNotFireBeforeFirstAssignment() {
        JobCoordinator coordinator = newCoordinator();
        coordinator.start();

        coordinator.detectFailures();
        coordinator.detectFailures();

        assertEquals(0, coordinator.getRestartCount(),
                "a never-assigned coordinator must not self-recover");
    }

    private static void assertThrowsAny(Runnable action) {
        try {
            action.run();
        } catch (Exception expected) {
            return;
        }
        throw new AssertionError("expected the recovery to throw (aborted mid-flight)");
    }

    // ==================== CC-03 ====================

    /**
     * CC-03: stop() must fan a cancelTask RPC out to the node of every
     * currently assigned subtask (pre-fix the remote tasks became orphans and
     * kept producing forever).
     */
    @Test
    void stopFansOutCancelTaskToAllAssignedNodes() {
        JobCoordinator coordinator = newCoordinator();
        coordinator.start();
        coordinator.assignTasks();
        assertEquals(2, totalAssignments());
        assertEquals(0, rpcA.cancelTaskCount.get() + rpcB.cancelTaskCount.get()
                + rpcC.cancelTaskCount.get());

        long epochAtStop = coordinator.getFencingEpoch();
        coordinator.stop();

        assertTrue(rpcA.cancelTaskKeys.contains("source/0"),
                "node-1 hosts source/0 and must receive its cancelTask");
        assertTrue(rpcB.cancelTaskKeys.contains("sink/0"),
                "node-2 hosts sink/0 and must receive its cancelTask");
        assertEquals(0, rpcC.cancelTaskCount.get(), "node-3 hosts nothing — no cancelTask");
        for (Long epoch : rpcA.cancelTaskEpochs) {
            assertEquals(Long.valueOf(epochAtStop), epoch, "cancelTask carries the current fencing epoch");
        }
        assertFalse(coordinator.isRunning(), "stop() still completes the coordinator teardown");
    }

    /**
     * CC-03: the fan-out is best-effort — a cancelTask RPC failure is logged
     * and must neither abort the remaining fan-out nor the stop().
     */
    @Test
    void stopSurvivesCancelTaskRpcFailure() {
        rpcA.failCancelTask = true;
        JobCoordinator coordinator = newCoordinator();
        coordinator.start();
        coordinator.assignTasks();

        assertDoesNotThrow(coordinator::stop,
                "a failing cancelTask RPC must not fail the coordinator shutdown");
        assertTrue(rpcB.cancelTaskKeys.contains("sink/0"),
                "the failure on node-1 must not skip the remaining nodes");
        assertFalse(coordinator.isRunning());
    }

    /**
     * CC-03: terminate(CANCEL) reaches the fan-out through its stop() call.
     */
    @Test
    void terminateCancelFansOutCancelTask() {
        JobCoordinator coordinator = newCoordinator();
        coordinator.start();
        coordinator.assignTasks();

        coordinator.terminate(JobTerminationMode.CANCEL);

        assertEquals(2, rpcA.cancelTaskCount.get() + rpcB.cancelTaskCount.get()
                + rpcC.cancelTaskCount.get(), "both assigned tasks must be canceled on terminate(CANCEL)");
    }

    // ==================== CC-04 ====================

    /**
     * CC-04: recovery must clear the previous generation's liveness entries.
     * Pre-fix the frozen timestamp of the dead generation survived under the
     * same "vertexId/subtaskIndex" key and — once older than taskTimeoutMs —
     * the first detector tick after recovery flagged a phantom TASK_STALL and
     * burned a stall-budget slot.
     */
    @Test
    void recoveryClearsStaleSubtaskLiveness() {
        JobCoordinator coordinator = newCoordinator();
        coordinator.start();
        coordinator.assignTasks();

        long stale = System.currentTimeMillis() - 120_000L;
        coordinator.reportNodeTaskLiveness("node-1", Collections.singletonList(
                new TaskProgress("source", 0, 1, stale)));
        assertEquals(1, coordinator.getSubtaskLivenessCount());

        coordinator.globalRecovery();

        assertEquals(0, coordinator.getSubtaskLivenessCount(),
                "recovery must clear the previous generation's liveness entries (CC-04)");

        coordinator.detectFailures();
        assertEquals(0, coordinator.getStallRestartCount(),
                "the stale timestamp must not trigger a phantom stall after recovery");
        assertEquals(1, coordinator.getRestartCount(),
                "only the explicit recovery ran — no stall-driven recovery followed");
    }

    // ==================== CC-05 ====================

    /**
     * CC-05: a heartbeat from a SUPERSEDED attempt (zombie task of the old
     * generation, e.g. after a fencing-push failure) must be rejected — its
     * fresh timestamps would mask a genuinely hung new-generation task.
     */
    @Test
    void staleAttemptHeartbeatIsRejected() {
        JobCoordinator coordinator = newCoordinator();
        coordinator.start();
        coordinator.assignTasks();

        long fresh = System.currentTimeMillis();
        coordinator.reportNodeTaskLiveness("node-1", Collections.singletonList(
                new TaskProgress("source", 0, 1, fresh)));
        assertEquals(1, coordinator.getSubtaskLivenessCount());

        coordinator.globalRecovery();
        assertEquals(0, coordinator.getSubtaskLivenessCount(), "CC-04 clear on rotation");

        // Zombie heartbeat: old attempt number (1) against the new generation's
        // assignment (attempt 2). Must be rejected, not merged.
        coordinator.reportNodeTaskLiveness("node-1", Collections.singletonList(
                new TaskProgress("source", 0, 1, fresh)));
        assertEquals(0, coordinator.getSubtaskLivenessCount(),
                "a superseded attempt's heartbeat must not refresh the liveness map (CC-05)");

        // The new generation's heartbeat (attempt 2) is accepted.
        coordinator.reportNodeTaskLiveness("node-1", Collections.singletonList(
                new TaskProgress("source", 0, 2, fresh)));
        assertEquals(1, coordinator.getSubtaskLivenessCount());
        assertEquals(fresh, coordinator.getSubtaskLivenessValue("source/0"));
    }

    // ==================== REG-02 ====================

    /**
     * REG-02: the COMPLETED report removes the liveness entry; an in-flight
     * heartbeat batch (built before the task finished, delivered after the
     * removal) must NOT re-insert the frozen timestamp. Pre-fix merge()'s
     * insert-on-absent resurrected it and the detector eventually flagged the
     * healthy finished task TASK_STALL (spurious recovery cutting the 2PC tail
     * commit window).
     */
    @Test
    void completedLivenessIsNotResurrectedByInFlightHeartbeat() {
        JobCoordinator coordinator = newCoordinator();
        coordinator.start();
        coordinator.assignTasks();
        coordinator.setTaskTimeoutMs(1L); // any surviving entry stalls immediately

        long frozen = System.currentTimeMillis();
        coordinator.reportNodeTaskLiveness("node-1", Collections.singletonList(
                new TaskProgress("source", 0, 1, frozen)));
        assertEquals(1, coordinator.getSubtaskLivenessCount());

        coordinator.reportTaskStatus(new TaskStatusReport(JOB_ID, "source", 0, 1,
                TaskStatusReport.TerminalState.COMPLETED, null, frozen,
                coordinator.getFencingEpoch(), System.currentTimeMillis()));
        assertEquals(0, coordinator.getSubtaskLivenessCount(), "COMPLETED removes the entry");

        // The in-flight heartbeat arrives AFTER the COMPLETED report.
        coordinator.reportNodeTaskLiveness("node-1", Collections.singletonList(
                new TaskProgress("source", 0, 1, frozen)));
        assertEquals(-1L, coordinator.getSubtaskLivenessValue("source/0"),
                "the frozen timestamp must not be re-inserted (REG-02)");
        assertEquals(0, coordinator.getSubtaskLivenessCount());

        coordinator.detectFailures();
        assertEquals(0, coordinator.getStallRestartCount(),
                "a resurrected entry would have flagged the finished task TASK_STALL");
        assertEquals(0, coordinator.getRestartCount());
    }

    /**
     * REG-02: a heartbeat delivered BEFORE the COMPLETED report still merges
     * normally (the tombstone only blocks post-completion insertion), and the
     * COMPLETED removal then cleans the merged entry.
     */
    @Test
    void heartbeatBeforeCompletionStillMerges() {
        JobCoordinator coordinator = newCoordinator();
        coordinator.start();
        coordinator.assignTasks();

        long t1 = System.currentTimeMillis();
        coordinator.reportNodeTaskLiveness("node-1", Collections.singletonList(
                new TaskProgress("sink", 0, 1, t1)));
        coordinator.reportTaskStatus(new TaskStatusReport(JOB_ID, "sink", 0, 1,
                TaskStatusReport.TerminalState.COMPLETED, null, t1,
                coordinator.getFencingEpoch(), System.currentTimeMillis()));

        assertEquals(-1L, coordinator.getSubtaskLivenessValue("sink/0"),
                "the merged entry is removed by the COMPLETED report");
        assertEquals(0, coordinator.getSubtaskLivenessCount());
    }

    // ==================== CC-11 ====================

    /**
     * CC-11: an interrupt during the terminal-savepoint wait must be honored —
     * the interrupt flag is restored on the calling thread (pre-fix the generic
     * catch swallowed it, silently defeating shutdown-hook / REST cancel).
     */
    @Test
    void drainTerminateRestoresInterruptFlag() throws Exception {
        JobCoordinator coordinator = newCoordinator();
        coordinator.setTerminationCheckpointTimeoutMs(60_000L);
        coordinator.start();
        coordinator.assignTasks();

        CountDownLatch started = new CountDownLatch(1);
        AtomicBoolean interruptObserved = new AtomicBoolean(false);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread terminator = new Thread(() -> {
            started.countDown();
            try {
                coordinator.terminate(JobTerminationMode.DRAIN);
                interruptObserved.set(Thread.currentThread().isInterrupted());
            } catch (Throwable t) {
                failure.set(t);
            }
        });
        terminator.start();
        assertTrue(started.await(5, TimeUnit.SECONDS));

        // Wait until the terminal savepoint's barrier has been sent (the wait
        // is now blocked in future.get with nothing to ACK it).
        long deadline = System.currentTimeMillis() + 10_000L;
        while (rpcA.getLastBarrier() == null && System.currentTimeMillis() < deadline) {
            Thread.sleep(10L);
        }
        CheckpointBarrier barrier = rpcA.getLastBarrier();
        assertNotNull(barrier, "the terminal savepoint barrier must have been sent");

        terminator.interrupt();
        terminator.join(10_000L);
        assertFalse(terminator.isAlive(), "the interrupted terminate must return promptly");
        if (failure.get() != null) {
            throw new AssertionError("terminate(DRAIN) threw", failure.get());
        }
        assertTrue(interruptObserved.get(),
                "the interrupt flag must be restored after the interrupted savepoint wait (CC-11)");
        assertFalse(coordinator.isRunning(), "the terminal teardown still stops the job");
    }

    // ==================== Fixture ====================

    /**
     * Registry stub that can fail the assignment write for one specific fencing
     * epoch (simulates the mid-recovery DB failure of CC-01's wedge scenario).
     */
    static class ThrowingAssignRegistry implements ClusterRegistry {
        final Map<String, NodeInfo> nodes = new LinkedHashMap<>();
        volatile long failAssignForEpoch = -1L;

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
            if (fencingEpoch == failAssignForEpoch) {
                throw new IllegalStateException(
                        "simulated registry write failure for epoch " + fencingEpoch + " (CC-01)");
            }
        }

        @Override
        public io.nop.stream.runtime.cluster.TaskAssignment getTaskAssignment(
                String jobId, String vertexId, int subtaskIndex) {
            return null;
        }

        @Override
        public List<io.nop.stream.runtime.cluster.TaskAssignment> getAttemptHistory(
                String jobId, String vertexId, int subtaskIndex) {
            return new ArrayList<>();
        }

        @Override
        public void removeTaskAssignment(String jobId, String vertexId, int subtaskIndex) {
        }
    }
}
