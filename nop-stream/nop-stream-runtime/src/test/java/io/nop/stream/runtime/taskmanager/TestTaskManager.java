/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.taskmanager;

import io.nop.api.core.message.IMessageConsumer;
import io.nop.api.core.message.IMessageService;
import io.nop.api.core.message.IMessageSubscription;
import io.nop.api.core.message.MessageSendOptions;
import io.nop.api.core.message.MessageSubscribeOptions;
import io.nop.stream.core.checkpoint.CheckpointBarrier;
import io.nop.stream.core.checkpoint.CheckpointType;
import io.nop.stream.core.checkpoint.TaskLocation;
import io.nop.stream.core.checkpoint.TaskStateSnapshot;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.jobgraph.JobGraph;
import io.nop.stream.runtime.cluster.ClusterRegistry;
import io.nop.stream.runtime.cluster.TaskAssignment;
import io.nop.stream.runtime.rpc.IStreamCoordinatorRpcService;
import io.nop.stream.runtime.rpc.TaskDeploymentDescriptor;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for TaskManager:
 * - receiveAssignment → creates task slot → runs → completes
 * - Heartbeat renewal
 * - Fencing token enforcement
 * - Stop lifecycle
 */
class TestTaskManager {

    private static final String NODE_ID = "test-node-1";
    private static final String ENDPOINT = "localhost:9090";
    private static final int CAPACITY = 4;
    private static final String CONTROL_TOPIC = "test-control";

    private TaskManager taskManager;
    private MockClusterRegistry clusterRegistry;
    private MockMessageService messageService;

    @BeforeEach
    void setUp() {
        clusterRegistry = new MockClusterRegistry();
        messageService = new MockMessageService();
        taskManager = new TaskManager(NODE_ID, ENDPOINT, CAPACITY,
                messageService, clusterRegistry, CONTROL_TOPIC);
    }

    @AfterEach
    void tearDown() {
        taskManager.stop();
    }

    @Test
    void testStartRegistersNode() {
        taskManager.start();
        assertTrue(taskManager.isRunning());
        assertTrue(clusterRegistry.registeredNodes.containsKey(NODE_ID));
    }

    @Test
    void testHeartbeatRenewsLease() {
        taskManager.start();
        taskManager.heartbeat();
        assertTrue(clusterRegistry.leaseRenewed);
    }

    @Test
    void testReceiveAssignmentCreatesTaskSlot() {
        taskManager.start();
        long fencingToken = 1L;
        taskManager.updateFencingToken(fencingToken);

        TaskAssignment assignment = new TaskAssignment(
                "job-1", "vertex-1", 0,
                NODE_ID, "attempt-1", fencingToken,
                System.currentTimeMillis());

        taskManager.receiveAssignment(assignment);

        assertEquals(1, taskManager.getRunningTaskCount());
    }

    @Test
    void testReceiveAssignmentRejectsStaleFencingToken() {
        // P0-6: stale-token handling hardened from LOG.warn + return to throw
        // StreamException. Previously the assignment was silently swallowed.
        taskManager.start();
        taskManager.updateFencingToken(1L);

        TaskAssignment assignment = new TaskAssignment(
                "job-1", "vertex-1", 0,
                NODE_ID, "attempt-1", 2L,
                System.currentTimeMillis());

        assertThrows(StreamException.class, () -> taskManager.receiveAssignment(assignment),
                "stale fencing token must throw, not be silently swallowed");
        assertEquals(0, taskManager.getRunningTaskCount());
    }

    @Test
    void testReceiveAssignmentRejectsWhenNotRunning() {
        // Don't start the task manager
        TaskAssignment assignment = new TaskAssignment(
                "job-1", "vertex-1", 0,
                NODE_ID, "attempt-1", 0L,
                System.currentTimeMillis());

        taskManager.receiveAssignment(assignment);

        assertEquals(0, taskManager.getRunningTaskCount());
    }

    @Test
    void testStopCleansUp() {
        taskManager.start();
        assertTrue(taskManager.isRunning());

        taskManager.stop();
        assertFalse(taskManager.isRunning());
    }

    @Test
    void testUpdateFencingTokenCancelsOldTasks() {
        taskManager.start();
        long oldToken = 1L;
        taskManager.updateFencingToken(oldToken);

        TaskAssignment assignment = new TaskAssignment(
                "job-1", "vertex-1", 0,
                NODE_ID, "attempt-1", oldToken,
                System.currentTimeMillis());

        taskManager.receiveAssignment(assignment);
        assertEquals(1, taskManager.getRunningTaskCount());

        // Update fencing token — old tasks should be canceled
        long newToken = 2L;
        taskManager.updateFencingToken(newToken);

        // Give time for cancellation to propagate
        assertEquals(0, taskManager.getRunningTaskCount());
    }

    @Test
    void testCapacityEnforcement() {
        taskManager.start();
        long token = 1L;
        taskManager.updateFencingToken(token);

        // Submit more tasks than capacity
        for (int i = 0; i < CAPACITY + 2; i++) {
            TaskAssignment assignment = new TaskAssignment(
                    "job-1", "vertex-1", i,
                    NODE_ID, "attempt-" + i, token,
                    System.currentTimeMillis());
            taskManager.receiveAssignment(assignment);
        }

        // Should not exceed capacity
        assertTrue(taskManager.getRunningTaskCount() <= CAPACITY);
    }

    @Test
    void testDuplicateAssignmentIgnored() {
        taskManager.start();
        long token = 1L;
        taskManager.updateFencingToken(token);

        TaskAssignment assignment = new TaskAssignment(
                "job-1", "vertex-1", 0,
                NODE_ID, "attempt-1", token,
                System.currentTimeMillis());

        taskManager.receiveAssignment(assignment);
        taskManager.receiveAssignment(assignment); // duplicate

        assertEquals(1, taskManager.getRunningTaskCount());
    }

    @Test
    void testCancelTaskDoesNotDoubleReleaseSemaphore() throws Exception {
        TaskManager smallTm = new TaskManager("node", "ep", 2,
                messageService, clusterRegistry, CONTROL_TOPIC);
        smallTm.start();
        long token = 1L;
        smallTm.updateFencingToken(token);

        TaskAssignment assignment = new TaskAssignment(
                "job-1", "vertex-1", 0,
                "node", "attempt-1", token,
                System.currentTimeMillis());

        smallTm.receiveAssignment(assignment);

        Thread.sleep(100);

        smallTm.cancelTask("job-1", "vertex-1", 0);

        Thread.sleep(200);

        Semaphore sem = null;
        int available = smallTm.availablePermits();
        assertTrue(available <= 2,
                "availablePermits (" + available + ") should not exceed capacity (2) after cancel");

        smallTm.stop();
    }

    @Test
    void testUpdateFencingTokenReleasesSemaphore() throws Exception {
        TaskManager smallTm = new TaskManager("node", "ep", 2,
                messageService, clusterRegistry, CONTROL_TOPIC);
        smallTm.start();
        long oldToken = 1L;
        smallTm.updateFencingToken(oldToken);

        TaskAssignment assignment = new TaskAssignment(
                "job-1", "vertex-1", 0,
                "node", "attempt-1", oldToken,
                System.currentTimeMillis());

        smallTm.receiveAssignment(assignment);
        assertEquals(1, smallTm.getRunningTaskCount());

        int permitsBefore = smallTm.availablePermits();

        long newToken = 2L;
        smallTm.updateFencingToken(newToken);

        Thread.sleep(200);

        int permitsAfter = smallTm.availablePermits();
        assertTrue(permitsAfter > permitsBefore,
                "Permits should increase after updateFencingToken cancels old tasks");

        assertTrue(permitsAfter <= 2,
                "availablePermits (" + permitsAfter + ") should not exceed capacity (2)");

        smallTm.stop();
    }

    @Test
    void testMultipleCancelsDoNotExceedCapacity() throws Exception {
        TaskManager smallTm = new TaskManager("node", "ep", 2,
                messageService, clusterRegistry, CONTROL_TOPIC);
        smallTm.start();
        long token = 1L;
        smallTm.updateFencingToken(token);

        for (int i = 0; i < 2; i++) {
            TaskAssignment assignment = new TaskAssignment(
                    "job-1", "vertex-1", i,
                    "node", "attempt-" + i, token,
                    System.currentTimeMillis());
            smallTm.receiveAssignment(assignment);
        }

        Thread.sleep(100);

        smallTm.cancelTask("job-1", "vertex-1", 0);
        smallTm.cancelTask("job-1", "vertex-1", 1);

        Thread.sleep(200);

        int available = smallTm.availablePermits();
        assertTrue(available <= 2,
                "availablePermits (" + available + ") should not exceed capacity (2) after N cancels");

        smallTm.stop();
    }

    @Test
    void testDuplicateAssignmentDoesNotLeakSemaphore() {
        TaskManager smallTm = new TaskManager("node", "ep", 2,
                messageService, clusterRegistry, CONTROL_TOPIC);
        smallTm.start();
        long token = 1L;
        smallTm.updateFencingToken(token);

        TaskAssignment assignment = new TaskAssignment(
                "job-1", "vertex-1", 0,
                "node", "attempt-1", token,
                System.currentTimeMillis());

        smallTm.receiveAssignment(assignment);
        int permitsAfterFirst = smallTm.availablePermits();

        smallTm.receiveAssignment(assignment);
        int permitsAfterDuplicate = smallTm.availablePermits();

        assertEquals(permitsAfterFirst, permitsAfterDuplicate,
                "Duplicate assignment should release the semaphore it acquired");

        assertTrue(permitsAfterDuplicate <= 2,
                "availablePermits (" + permitsAfterDuplicate + ") should not exceed capacity (2)");

        smallTm.stop();
    }

    /**
     * P1 hardening (Phase 3): the {@code deployTask} redeploy-to-occupied-slot
     * path must conserve semaphore permits. The new deployment's permit is
     * acquired at method entry; releasing the old slot's permit balances it
     * (net 0 for a redeploy). The legacy code re-acquired a permit after the
     * balance, leaking one permit per redeploy and wedging the node after
     * {@code capacity} recoveries.
     *
     * <p>The test occupies a slot via {@code receiveAssignment}, then redeploys to
     * the SAME slot via {@code deployTask} with a descriptor whose JobGraph has no
     * matching vertex (so {@code buildSubtaskInvokable} fails and the catch block
     * releases the new task's permit). After the failed redeploy the slot is empty
     * and ALL permits must be returned. Before the fix the extra
     * {@code acquireUninterruptibly} left permits at {@code capacity-1}.
     */
    @Test
    void testRedeployToOccupiedSlotDoesNotLeakPermit() throws Exception {
        TaskManager smallTm = new TaskManager("node", "ep", 2,
                messageService, clusterRegistry, CONTROL_TOPIC);
        smallTm.start();
        long token = 1L;
        smallTm.updateFencingToken(token);

        // 1. Occupy the slot via the in-process receiveAssignment path.
        TaskAssignment assignment = new TaskAssignment(
                "job-1", "vertex-1", 0,
                "node", "attempt-1", token,
                System.currentTimeMillis());
        smallTm.receiveAssignment(assignment);
        assertEquals(1, smallTm.getRunningTaskCount());
        assertEquals(1, smallTm.availablePermits(),
                "one slot occupied -> capacity-1 permits");

        // 2. Redeploy to the SAME occupied slot via deployTask. The descriptor's
        //    JobGraph has no matching vertex, so buildSubtaskInvokable fails; the
        //    catch block releases the new task's permit and throws. This exercises
        //    the fence-old-slot + balance-permit block (the buggy extra acquire
        //    was right after it).
        JobGraph emptyGraph = new JobGraph("job-1");
        TaskDeploymentDescriptor descriptor = new TaskDeploymentDescriptor(
                "job-1", "vertex-1", 0, null,
                "attempt-2", 2, token,
                emptyGraph, null, null);
        assertThrows(StreamException.class, () -> smallTm.deployTask(descriptor, token),
                "buildSubtaskInvokable must fail on the empty graph (vertex not found)");

        // Let the fenced old task + failed new task settle their finally blocks.
        Thread.sleep(300);

        // 3. The redeploy FAILED -> the slot is empty and ALL permits must be
        //    returned (capacity). Before the fix this was capacity-1 (leaked).
        assertEquals(0, smallTm.getRunningTaskCount(),
                "slot must be empty after the failed redeploy (old fenced, new failed to build)");
        assertEquals(2, smallTm.availablePermits(),
                "Failed redeploy to an occupied slot must return ALL permits (no leak). "
                        + "Before the fix this was 1 (one permit leaked per redeploy).");

        smallTm.stop();
    }

    // ==================== Checkpoint & Invokable Tests ====================

    @Test
    void testSendCheckpointAckWithNoCoordinatorRpcService() {
        taskManager.start();

        TaskLocation loc = new TaskLocation("job-1", "pipeline-0", "vertex-1", 0);
        TaskStateSnapshot snapshot = TaskStateSnapshot.builder(loc)
                .checkpointId(1L)
                .putOperatorState("op-0", "test-state")
                .build();

        // sendCheckpointAck without coordinatorRpcService catches the internal StreamException
        // and logs it. The method does NOT re-throw, so we verify no crash.
        assertDoesNotThrow(() -> taskManager.sendCheckpointAck(1L, snapshot));
    }

    @Test
    void testSendCheckpointAckWithCoordinatorRpcService() {
        taskManager.start();
        MockCoordinatorRpcService mockRpc = new MockCoordinatorRpcService();
        taskManager.setCoordinatorRpcService(mockRpc);

        long token = 1L;
        taskManager.updateFencingToken(token);

        TaskLocation loc = new TaskLocation("job-1", "pipeline-0", "vertex-1", 0);
        TaskStateSnapshot snapshot = TaskStateSnapshot.builder(loc)
                .checkpointId(1L)
                .putOperatorState("op-0", "test-state")
                .build();

        taskManager.sendCheckpointAck(1L, snapshot);

        assertNotNull(mockRpc.lastAck);
        assertEquals(1L, mockRpc.lastAck.getCheckpointId());
        assertEquals(loc, mockRpc.lastAck.getTaskLocation());
        assertEquals(token, mockRpc.lastAck.getFencingEpoch());
    }

    @Test
    void testInstallInvokableWithInvalidTaskDoesNotCrash() {
        taskManager.start();

        // Installing an invokable for a task that doesn't exist should just log a warning
        assertDoesNotThrow(() ->
                taskManager.installInvokable("nonexistent-job", "nonexistent-vertex", 99, null));
    }

    @Test
    void testInstallInvokableOnRunningTask() throws Exception {
        taskManager.start();
        long token = 1L;
        taskManager.updateFencingToken(token);

        TaskAssignment assignment = new TaskAssignment(
                "job-1", "vertex-1", 0,
                NODE_ID, "attempt-1", token,
                System.currentTimeMillis());
        taskManager.receiveAssignment(assignment);
        assertEquals(1, taskManager.getRunningTaskCount());

        // Verify we can query completed tasks before the invokable is installed
        // (the task thread is waiting for the invokable latch)
        assertTrue(taskManager.getCompletedTaskResults().isEmpty());

        // Now cancel the task to clean up
        taskManager.cancelTask("job-1", "vertex-1", 0);
        Thread.sleep(100);

        // Task should be removed from running tasks
        assertEquals(0, taskManager.getRunningTaskCount());
    }

    @Test
    void testTriggerCheckpointWithNoRunningTasksDoesNotCrash() {
        taskManager.start();
        long token = 1L;
        taskManager.updateFencingToken(token);

        // No tasks assigned - triggerCheckpoint should not crash
        CheckpointBarrier barrier = new CheckpointBarrier(1L, System.currentTimeMillis(), CheckpointType.CHECKPOINT);
        assertDoesNotThrow(() -> taskManager.triggerCheckpoint(barrier, token));
    }

    @Test
    void testTriggerCheckpointWithStaleTokenThrows() {
        // P0-6: stale-token handling hardened from LOG.warn + return to throw
        // StreamException. The TaskManager Javadoc contract states it "rejects
        // any operation carrying an old fencing token" — silently dropping
        // the barrier was a No-Silent-No-Op violation.
        taskManager.start();
        long token = 1L;
        taskManager.updateFencingToken(token);

        CheckpointBarrier barrier = new CheckpointBarrier(1L, System.currentTimeMillis(), CheckpointType.CHECKPOINT);
        assertThrows(StreamException.class,
                () -> taskManager.triggerCheckpoint(barrier, 2L),
                "stale fencing token must throw, not be silently swallowed");
    }

    // ==================== Runtime audit 2026-09-01 fixes ====================

    /**
     * R-14: updateFencingToken must reject epoch rollback. Fencing epochs are
     * monotonic by construction; a zombie coordinator (strictly smaller epoch)
     * must never be able to roll this node's epoch backward, cancel the entire
     * active generation, and mismatch-reject every subsequent active-epoch call.
     */
    @Test
    void testUpdateFencingTokenRejectsEpochRollback() {
        taskManager.start();
        taskManager.updateFencingToken(5000L);

        TaskAssignment assignment = new TaskAssignment(
                "job-1", "vertex-1", 0,
                NODE_ID, "attempt-1", 5000L,
                System.currentTimeMillis());
        taskManager.receiveAssignment(assignment);
        assertEquals(1, taskManager.getRunningTaskCount());

        // Zombie-style rollback attempt: strictly smaller epoch must fail fast.
        StreamException ex = assertThrows(StreamException.class,
                () -> taskManager.updateFencingToken(4000L),
                "epoch rollback must be rejected (zombie coordinator fencing)");
        assertTrue(ex.getMessage().contains("4000") || ex.getMessage().contains("5000"),
                "exception should carry expected/actual epoch values");

        // The active epoch is unchanged and the active-generation task survived.
        CheckpointBarrier barrier = new CheckpointBarrier(1L, System.currentTimeMillis(), CheckpointType.CHECKPOINT);
        assertDoesNotThrow(() -> taskManager.triggerCheckpoint(barrier, 5000L),
                "active epoch must still be accepted after a rejected rollback");
        assertEquals(1, taskManager.getRunningTaskCount(),
                "active-generation task must NOT be canceled by a rejected rollback");

        // Equal epoch is a no-op (still accepted), greater epoch rotates.
        assertDoesNotThrow(() -> taskManager.updateFencingToken(5000L));
        assertDoesNotThrow(() -> taskManager.updateFencingToken(6000L));
    }

    /**
     * R-15: an invokable-install timeout must terminate as FAILED (reported to
     * the coordinator), never as a silent COMPLETED. Before the fix the timeout
     * path returned normally, the finally block computed success=true, and a
     * never-run subtask was reported COMPLETED — no failover, potential data
     * loss.
     */
    @Test
    void testInvokableInstallTimeoutReportsFailedNotCompleted() throws Exception {
        long savedTimeout = TaskManager.invokableWaitTimeoutMs;
        TaskManager.invokableWaitTimeoutMs = 200L;
        try {
            taskManager.start();
            MockCoordinatorRpcService mockRpc = new MockCoordinatorRpcService();
            taskManager.setCoordinatorRpcService(mockRpc);
            long token = 1L;
            taskManager.updateFencingToken(token);

            TaskAssignment assignment = new TaskAssignment(
                    "job-1", "vertex-1", 0,
                    NODE_ID, "attempt-1", token,
                    System.currentTimeMillis());
            taskManager.receiveAssignment(assignment);

            // Never install the invokable — wait for the timeout to fire.
            long deadline = System.currentTimeMillis() + 5_000;
            while (taskManager.getCompletedTaskResults().isEmpty()
                    && System.currentTimeMillis() < deadline) {
                Thread.sleep(50);
            }

            String key = "job-1/vertex-1/0";
            TaskManager.TaskResult result = taskManager.getCompletedTaskResults().get(key);
            assertNotNull(result, "task must terminate after the invokable-install timeout");
            assertFalse(result.isSuccess(),
                    "invokable-install timeout must NOT be reported as success");
            assertFalse(result.isCanceled(), "a genuine timeout is not a cancel");
            assertNotNull(result.getError(), "timeout must be recorded as the task error");
            assertTrue(result.getError().getMessage().contains("invokable"),
                    "error should point at the invokable installation: " + result.getError().getMessage());

            assertEquals(1, mockRpc.statusReports.size(), "exactly one terminal report expected");
            io.nop.stream.runtime.coordinator.TaskStatusReport report = mockRpc.statusReports.get(0);
            assertEquals(io.nop.stream.runtime.coordinator.TaskStatusReport.TerminalState.FAILED,
                    report.getTerminalState(),
                    "timeout must report FAILED so failover can act (was COMPLETED before the fix)");
            assertNotNull(report.getErrorCause());
        } finally {
            TaskManager.invokableWaitTimeoutMs = savedTimeout;
        }
    }

    /**
     * R-20: receiveAssignment rejections (capacity exhausted, stale epoch) must
     * be reported to the coordinator as FAILED TaskStatusReports — the RPC is
     * one-way, so a warn-and-return (or a bare throw) is invisible to the
     * coordinator and the slot stalls until supervision detects it.
     */
    @Test
    void testReceiveAssignmentCapacityRejectionReportsFailed() {
        taskManager.start();
        MockCoordinatorRpcService mockRpc = new MockCoordinatorRpcService();
        taskManager.setCoordinatorRpcService(mockRpc);
        long token = 1L;
        taskManager.updateFencingToken(token);

        // Fill every slot (tasks block on the invokable latch, never complete).
        for (int i = 0; i < CAPACITY; i++) {
            taskManager.receiveAssignment(new TaskAssignment(
                    "job-1", "vertex-1", i,
                    NODE_ID, "attempt-" + i, token,
                    System.currentTimeMillis()));
        }
        assertEquals(CAPACITY, taskManager.getRunningTaskCount());
        assertTrue(mockRpc.statusReports.isEmpty(), "accepted assignments must not report");

        // One more assignment exceeds capacity -> rejected AND reported FAILED.
        TaskAssignment overflow = new TaskAssignment(
                "job-1", "vertex-1", CAPACITY,
                NODE_ID, "attempt-overflow", token,
                System.currentTimeMillis());
        taskManager.receiveAssignment(overflow);

        assertEquals(CAPACITY, taskManager.getRunningTaskCount(), "overflow must not run");
        assertEquals(1, mockRpc.statusReports.size(),
                "capacity rejection must be observable to the coordinator");
        assertEquals(io.nop.stream.runtime.coordinator.TaskStatusReport.TerminalState.FAILED,
                mockRpc.statusReports.get(0).getTerminalState());
        assertEquals("vertex-1", mockRpc.statusReports.get(0).getVertexId());
    }

    /** R-20: stale-epoch receiveAssignment must throw AND report FAILED. */
    @Test
    void testReceiveAssignmentStaleEpochReportsFailed() {
        taskManager.start();
        MockCoordinatorRpcService mockRpc = new MockCoordinatorRpcService();
        taskManager.setCoordinatorRpcService(mockRpc);
        taskManager.updateFencingToken(100L);

        TaskAssignment stale = new TaskAssignment(
                "job-1", "vertex-1", 0,
                NODE_ID, "attempt-1", 7L,
                System.currentTimeMillis());
        assertThrows(StreamException.class, () -> taskManager.receiveAssignment(stale));

        assertEquals(0, taskManager.getRunningTaskCount());
        assertEquals(1, mockRpc.statusReports.size(),
                "fencing rejection must be observable to the coordinator");
        assertEquals(io.nop.stream.runtime.coordinator.TaskStatusReport.TerminalState.FAILED,
                mockRpc.statusReports.get(0).getTerminalState());
    }

    /**
     * R-21: deployTask must reject a descriptor whose embedded fencing epoch
     * disagrees with the RPC parameter — the RunningTask is keyed by the
     * descriptor's field, so a divergent value would run the task under an
     * epoch that was never validated.
     */
    @Test
    void testDeployTaskRejectsDescriptorEpochMismatch() {
        taskManager.start();
        MockCoordinatorRpcService mockRpc = new MockCoordinatorRpcService();
        taskManager.setCoordinatorRpcService(mockRpc);
        long token = 100L;
        taskManager.updateFencingToken(token);

        JobGraph graph = new JobGraph("job-1");
        TaskDeploymentDescriptor descriptor = new TaskDeploymentDescriptor(
                "job-1", "vertex-1", 0, null,
                "attempt-1", 1, token + 5,
                graph, null, null);

        assertThrows(StreamException.class, () -> taskManager.deployTask(descriptor, token),
                "descriptor epoch diverging from the RPC parameter must fail fast");
        assertEquals(0, taskManager.getRunningTaskCount());
        assertEquals(1, mockRpc.statusReports.size(), "rejection must be reported to the coordinator");
        assertEquals(io.nop.stream.runtime.coordinator.TaskStatusReport.TerminalState.FAILED,
                mockRpc.statusReports.get(0).getTerminalState());
    }

    /**
     * R-1: heartbeat interval and lease timeout are constructor-injectable
     * (06-30 audit: hardcoded with no injection point). The lease renewal must
     * use the configured timeout; non-positive values fail fast.
     */
    @Test
    void testHeartbeatAndLeaseIntervalsConfigurable() {
        TaskManager customTm = new TaskManager("node-cfg", "ep", 2,
                messageService, clusterRegistry, CONTROL_TOPIC, 250L, 900L);
        try {
            customTm.start();
            customTm.heartbeat();
            assertTrue(clusterRegistry.leaseRenewed);
            assertEquals(900L, clusterRegistry.lastRenewLeaseTimeoutMs,
                    "lease renewal must use the configured lease timeout");
        } finally {
            customTm.stop();
        }

        assertThrows(StreamException.class,
                () -> new TaskManager("node-bad", "ep", 2,
                        messageService, clusterRegistry, CONTROL_TOPIC, 0L, 900L),
                "non-positive heartbeat interval must fail fast");
        assertThrows(StreamException.class,
                () -> new TaskManager("node-bad", "ep", 2,
                        messageService, clusterRegistry, CONTROL_TOPIC, 250L, -1L),
                "non-positive lease timeout must fail fast");
    }

    /**
     * Item 14 (composite-scenario distributed defect): a fenced stale attempt's
     * thread can exit AFTER its replacement was deployed under the same task key
     * (recovery cancels the old attempt, but the thread winds down asynchronously
     * under real load; the fresh deployTask puts the new RunningTask under the
     * same key immediately). The stale attempt's finally must NOT remove the
     * replacement's registry entry — the legacy unconditional
     * {@code runningTasks.remove(key)} made the fresh task invisible to
     * {@code triggerCheckpoint} (barrier never registered on its tracker —
     * checkpoint ACKs dropped with "no matching in-flight epoch" in the S1
     * multi-JVM kill/recover drill) and to {@code cancelTask}.
     *
     * <p>Deterministic reproduction: attempt 1's source ignores interrupts (the
     * slow wind-down), attempt 2 deploys into the vacated slot, and only THEN is
     * attempt 1 released — its exit path runs strictly after the replacement's
     * registration.
     */
    @Test
    void testStaleAttemptExitDoesNotRemoveReplacementRegistryEntry() throws Exception {
        TaskManager tm = new TaskManager("node-race", "ep", 2,
                messageService, clusterRegistry, CONTROL_TOPIC);
        tm.start();
        try {
            long epoch1 = 1L;
            tm.updateFencingToken(epoch1);

            // Attempt 1: deployed with an interrupt-insensitive gated source.
            InterruptInsensitiveGatedSource staleSource = new InterruptInsensitiveGatedSource();
            tm.deployTask(new TaskDeploymentDescriptor(
                    "job-race", "vertex-race", 0, "node-race",
                    "attempt-1", 1, epoch1,
                    singleSourceJobGraph("job-race", "vertex-race", staleSource), null, null), epoch1);
            assertTrue(staleSource.awaitRunning(5_000L), "attempt 1 must enter its source run()");

            // Recovery: epoch rotation fences attempt 1 out of the registry; its
            // thread STAYS parked in the gated source (ignores the cancel
            // interrupt), then attempt 2 deploys into the same slot.
            long epoch2 = epoch1 + 1;
            tm.updateFencingToken(epoch2);
            InterruptInsensitiveGatedSource replacementSource = new InterruptInsensitiveGatedSource();
            tm.deployTask(new TaskDeploymentDescriptor(
                    "job-race", "vertex-race", 0, "node-race",
                    "attempt-2", 2, epoch2,
                    singleSourceJobGraph("job-race", "vertex-race", replacementSource), null, null), epoch2);
            assertEquals(1, tm.getRunningTaskCount(), "the replacement must own the slot");
            assertEquals(1, tm.availablePermits(), "one slot occupied of capacity 2");

            // NOW the stale attempt's thread finally exits (post-replacement).
            staleSource.release();
            assertTrue(staleSource.awaitExited(5_000L), "stale attempt 1 must exit its run()");
            // Deterministic wait for the stale attempt's FINALLY block: it records
            // the task in completedTaskResults right before the registry remove.
            String staleKey = "job-race/vertex-race/0";
            waitForCondition(() -> tm.getCompletedTaskResults().containsKey(staleKey), 5_000L);
            // ... and let any post-remove effects settle (registry visibility).
            Thread.sleep(200L);

            assertEquals(1, tm.getRunningTaskCount(),
                    "the replacement's registry entry must survive the stale attempt's exit "
                            + "(before the fix the stale finally removed it — the fresh task became "
                            + "invisible to triggerCheckpoint/cancelTask)");
            assertEquals(1, tm.availablePermits(),
                    "stale attempt exit must not double-release its semaphore permit");

            // The replacement remains checkpoint-reachable: a trigger at the
            // current epoch must reach a registered task (no throw) and leave
            // the registration intact.
            tm.triggerCheckpoint(new CheckpointBarrier(
                    1L, System.currentTimeMillis(), CheckpointType.CHECKPOINT), epoch2);
            assertEquals(1, tm.getRunningTaskCount(), "replacement stays registered after trigger");

            replacementSource.release();
        } finally {
            tm.stop();
        }
    }

    private static JobGraph singleSourceJobGraph(String jobId, String vertexId,
                                                 io.nop.stream.core.common.functions.source.SourceFunction<Integer> source) {
        io.nop.stream.core.operators.StreamSourceOperator<Integer> sourceOp =
                new io.nop.stream.core.operators.StreamSourceOperator<>(source);
        io.nop.stream.core.jobgraph.OperatorChain chain =
                new io.nop.stream.core.jobgraph.OperatorChain(java.util.Collections.singletonList(sourceOp));
        io.nop.stream.core.execution.StreamTaskInvokable invokable =
                new io.nop.stream.core.execution.StreamTaskInvokable(chain);
        // The sink vertex only exists so the source gets an OUTPUT (without an
        // out-edge invokeSource skips sourceOp.run()); only the source vertex is
        // ever deployed by this test.
        io.nop.stream.core.operators.StreamSinkOperator<Integer> sinkOp =
                new io.nop.stream.core.operators.StreamSinkOperator<>(
                        new io.nop.stream.core.common.functions.sink.PrintSinkFunction<>());
        io.nop.stream.core.jobgraph.OperatorChain sinkChain =
                new io.nop.stream.core.jobgraph.OperatorChain(java.util.Collections.singletonList(sinkOp));
        io.nop.stream.core.execution.StreamTaskInvokable sinkInvokable =
                new io.nop.stream.core.execution.StreamTaskInvokable(sinkChain);
        JobGraph graph = new JobGraph(jobId);
        graph.addVertex(new io.nop.stream.core.jobgraph.JobVertex(
                vertexId, "Source", 1, java.util.Collections.singletonList(chain), invokable));
        graph.addVertex(new io.nop.stream.core.jobgraph.JobVertex(
                vertexId + "-sink", "Sink", 1, java.util.Collections.singletonList(sinkChain), sinkInvokable));
        graph.addEdge(new io.nop.stream.core.jobgraph.JobEdge(
                vertexId, vertexId + "-sink", io.nop.stream.core.jobgraph.ResultPartitionType.PIPELINED));
        return graph;
    }

    /** Gated source that ignores interrupts: models a fenced attempt's slow wind-down. */
    static final class InterruptInsensitiveGatedSource
            implements io.nop.stream.core.common.functions.source.SourceFunction<Integer> {
        private volatile boolean released = false;
        private volatile boolean running = false;
        private volatile boolean exited = false;

        @Override
        public void run(io.nop.stream.core.common.functions.source.SourceFunction.SourceContext<Integer> ctx) throws Exception {
            running = true;
            while (!released) {
                try {
                    Thread.sleep(20L);
                } catch (InterruptedException e) {
                    // Fenced-attempt wind-down: ignore the cancel interrupt.
                    Thread.currentThread().interrupt();
                }
            }
            exited = true;
        }

        @Override
        public void cancel() {
            released = true;
        }

        void release() {
            released = true;
        }

        boolean awaitRunning(long timeoutMs) throws InterruptedException {
            long deadline = System.currentTimeMillis() + timeoutMs;
            while (System.currentTimeMillis() < deadline) {
                if (running) {
                    return true;
                }
                Thread.sleep(10L);
            }
            return running;
        }

        boolean awaitExited(long timeoutMs) throws InterruptedException {
            long deadline = System.currentTimeMillis() + timeoutMs;
            while (System.currentTimeMillis() < deadline) {
                if (exited) {
                    return true;
                }
                Thread.sleep(10L);
            }
            return exited;
        }
    }

    private static void waitForCondition(java.util.function.BooleanSupplier condition, long timeoutMs)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(20L);
        }
    }

    // ==================== Mocks ====================

    static class MockClusterRegistry implements ClusterRegistry {
        final Map<String, Object> registeredNodes = new ConcurrentHashMap<>();
        volatile boolean leaseRenewed = false;
        volatile long lastRenewLeaseTimeoutMs = -1L;

        @Override
        public void registerCoordinator(String jobId, String coordinatorId, long fencingEpoch) {}

        @Override
        public io.nop.stream.runtime.cluster.CoordinatorInfo getActiveCoordinator(String jobId) {
            return null;
        }

        @Override
        public void registerNode(String nodeId, String endpoint, int capacity) {
            registeredNodes.put(nodeId, new Object());
        }

        @Override
        public boolean renewLease(String nodeId, long leaseTimeoutMs) {
            leaseRenewed = true;
            lastRenewLeaseTimeoutMs = leaseTimeoutMs;
            return true;
        }

        @Override
        public io.nop.stream.runtime.cluster.LeaseInfo getNodeLease(String nodeId) {
            return null;
        }

        @Override
        public List<io.nop.stream.runtime.cluster.NodeInfo> getActiveNodes() {
            List<io.nop.stream.runtime.cluster.NodeInfo> nodes = new ArrayList<>();
            for (String nodeId : registeredNodes.keySet()) {
                nodes.add(new io.nop.stream.runtime.cluster.NodeInfo(
                        nodeId, "localhost", 4, System.currentTimeMillis(), System.currentTimeMillis()));
            }
            return nodes;
        }

        @Override
        public void assignTask(String jobId, String vertexId, int subtaskIndex,
                               String nodeId, String attemptId, long fencingEpoch,
                               int attemptNumber) {}

        @Override
        public TaskAssignment getTaskAssignment(String jobId, String vertexId, int subtaskIndex) {
            return null;
        }

        @Override
        public List<TaskAssignment> getAttemptHistory(String jobId, String vertexId, int subtaskIndex) {
            return new ArrayList<>();
        }

        @Override
        public void removeTaskAssignment(String jobId, String vertexId, int subtaskIndex) {}
    }

    static class MockMessageService implements IMessageService {
        final List<Object> sentMessages = new CopyOnWriteArrayList<>();

        @Override
        public IMessageSubscription subscribe(String topic, IMessageConsumer listener, MessageSubscribeOptions options) {
            return new IMessageSubscription() {
                @Override public void cancel() {}
                @Override public boolean isSuspended() { return false; }
                @Override public boolean isCancelled() { return false; }
                @Override public void suspend() {}
                @Override public void resume() {}
            };
        }

        @Override
        public java.util.concurrent.CompletionStage<Void> sendAsync(String topic, Object message, MessageSendOptions options) {
            sentMessages.add(message);
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }
    }

    static class MockCoordinatorRpcService implements IStreamCoordinatorRpcService {
        volatile CheckpointAckMessage lastAck;
        final java.util.List<io.nop.stream.runtime.coordinator.TaskStatusReport> statusReports =
                new java.util.concurrent.CopyOnWriteArrayList<>();
        final java.util.List<io.nop.stream.runtime.coordinator.TaskProgress> livenessReports =
                new java.util.concurrent.CopyOnWriteArrayList<>();

        @Override
        public void receiveCheckpointAck(CheckpointAckMessage ack) {
            this.lastAck = ack;
        }

        @Override
        public void reportTaskStatus(io.nop.stream.runtime.coordinator.TaskStatusReport report) {
            statusReports.add(report);
        }

        @Override
        public void reportNodeTaskLiveness(String nodeId, java.util.List<io.nop.stream.runtime.coordinator.TaskProgress> progress) {
            livenessReports.addAll(progress);
        }

        @Override
        public void terminate(io.nop.stream.core.checkpoint.JobTerminationMode mode) {
        }

        @Override
        public void abortCheckpoint(long epochId) {
        }

        @Override
        public io.nop.stream.runtime.coordinator.JobStatusResponse getJobStatus() {
            return new io.nop.stream.runtime.coordinator.JobStatusResponse();
        }
    }
}
