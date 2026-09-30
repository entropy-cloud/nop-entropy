/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.taskmanager;

import io.nop.stream.runtime.testsupport.TestAwait;
import io.nop.api.core.message.IMessageConsumer;
import io.nop.api.core.message.IMessageService;
import io.nop.api.core.message.IMessageSubscription;
import io.nop.api.core.message.MessageSendOptions;
import io.nop.api.core.message.MessageSubscribeOptions;
import io.nop.stream.core.execution.task.StreamTaskInvokable;
import io.nop.stream.runtime.cluster.ClusterRegistry;
import io.nop.stream.runtime.cluster.TaskAssignment;
import io.nop.stream.runtime.coordinator.TaskProgress;
import io.nop.stream.runtime.coordinator.TaskStatusReport;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * G52 wiring verification at the {@link TaskManager} level:
 * <ul>
 *   <li>{@code heartbeat()} reports per-task liveness via
 *       {@code reportNodeTaskLiveness} (with invokable null-check).</li>
 *   <li>{@code RunningTask.run()} finally reports terminal status via
 *       {@code reportTaskStatus} on both success and failure paths.</li>
 * </ul>
 */
class TestTaskManagerLivenessAndReporting {

    private TaskManager taskManager;
    private CapturingCoordinatorRpc coordinatorRpc;

    @BeforeEach
    void setUp() {
        coordinatorRpc = new CapturingCoordinatorRpc();
        taskManager = new TaskManager(
                "node-1", "localhost:9090", 4,
                new NoopMessageService(),
                new NoopClusterRegistry(),
                "control-topic");
        taskManager.setCoordinatorRpcService(coordinatorRpc);
        taskManager.start();
    }

    @AfterEach
    void tearDown() {
        taskManager.stop();
    }

    @Test
    void heartbeatReportsNoLivenessWhenNoRunningTasks() {
        taskManager.heartbeat();
        assertTrue(coordinatorRpc.livenessBatches.isEmpty(),
                "no running tasks → no liveness report");
    }

    @Test
    void heartbeatSkipsLivenessForTasksWithoutInvokable() {
        // Assign a task but do NOT install an invokable. heartbeat must skip it
        // (null-check defense, not NPE).
        long token = 1L;
        taskManager.updateFencingToken(token);
        TaskAssignment a = new TaskAssignment(
                "job-1", "v-1", 0, "node-1", "att-1", token, System.currentTimeMillis(), 1);
        taskManager.receiveAssignment(a);

        taskManager.heartbeat();

        // The task's invokable is not installed → heartbeat skips it → no report
        assertTrue(coordinatorRpc.livenessBatches.isEmpty(),
                "tasks without invokable must be skipped (no NPE, no report)");

        // Cleanup: cancel the task so it does not linger awaiting invokable
        taskManager.cancelTask("job-1", "v-1", 0, token);
    }

    // Note: the former {@code heartbeatReportsLivenessWhenInvokableInstalled}
    // smoke test asserted only {@code livenessBatches.size() >= 0} (always true).
    // Its two branches are covered deterministically by the neighboring tests:
    // running task → {@link #idleSinkTaskHeartbeatReportsFreshAliveness}
    // (exactly 1 entry, fresh timestamp); completed task →
    // {@link #heartbeatSkipsFinishedTaskWhoseRegistryEntryIsRetained}
    // (no re-report of a finished task).

    @Test
    void runningTaskFinallyReportsCompletedStatus() throws Exception {
        long token = 1L;
        taskManager.updateFencingToken(token);
        TaskAssignment a = new TaskAssignment(
                "job-1", "v-1", 0, "node-1", "att-1", token, System.currentTimeMillis(), 1);
        taskManager.receiveAssignment(a);

        // Install empty-chain invokable → run() returns immediately → COMPLETED
        StreamTaskInvokable inv = new StreamTaskInvokable(buildEmptyOperatorChain());
        taskManager.installInvokable("job-1", "v-1", 0, inv);

        // Wait for task thread to run to completion
        Thread.sleep(200);

        assertFalse(coordinatorRpc.statusReports.isEmpty(),
                "RunningTask finally block must report terminal status");
        TaskStatusReport report = coordinatorRpc.statusReports.get(0);
        assertEquals("job-1", report.getJobId());
        assertEquals("v-1", report.getVertexId());
        assertEquals(0, report.getSubtaskIndex());
        assertEquals(1, report.getAttemptNumber());
        assertEquals(TaskStatusReport.TerminalState.COMPLETED, report.getTerminalState());
    }

    /**
     * N3 regression (plan 366 Phase 2): a success-finished task RETAINS its
     * registry entry (bounded-run tail commits), but its frozen activity clock
     * must NOT be re-reported on the heartbeat — the coordinator removed the
     * liveness key on the COMPLETED report, and a re-inserted frozen value ages
     * past taskTimeoutMs into a false TASK_STALL global recovery that cancels
     * the very tail-commit window the retained entry protects.
     */
    @Test
    void heartbeatSkipsFinishedTaskWhoseRegistryEntryIsRetained() throws Exception {
        long token = 1L;
        taskManager.updateFencingToken(token);
        TaskAssignment a = new TaskAssignment(
                "job-1", "v-done", 0, "node-1", "att-1", token, System.currentTimeMillis(), 1);
        taskManager.receiveAssignment(a);

        StreamTaskInvokable inv = new StreamTaskInvokable(buildEmptyOperatorChain());
        taskManager.installInvokable("job-1", "v-done", 0, inv);

        // Wait for the natural COMPLETED report (RunningTask.run finally).
        TestAwait.until("task reports COMPLETED",
                () -> coordinatorRpc.statusReports.stream()
                        .anyMatch(r -> "v-done".equals(r.getVertexId())
                                && r.getTerminalState() == TaskStatusReport.TerminalState.COMPLETED),
                2000);

        // The success-finished task RETAINS its registry entry (tail commits):
        // it is still visible in the registry, unlike failed/canceled tasks.
        assertTrue(taskManager.hasRegistryEntry("job-1", "v-done", 0),
                "precondition: success-finished task keeps its registry entry");

        taskManager.heartbeat();

        // Pre-fix: the retained entry was reported with its frozen activity
        // clock → coordinator merge() re-inserted the stale timestamp → false
        // TASK_STALL after taskTimeoutMs. Post-fix: no liveness entry at all.
        for (List<TaskProgress> batch : coordinatorRpc.livenessBatches) {
            assertTrue(batch.stream().noneMatch(p -> "v-done".equals(p.getVertexId())),
                    "finished task must not be re-reported on the heartbeat (frozen clock "
                            + "would age into a false TASK_STALL): " + batch);
        }

        taskManager.cancelTask("job-1", "v-done", 0, token);
    }

    private static io.nop.stream.core.jobgraph.OperatorChain buildEmptyOperatorChain() {
        // StreamMap with identity function — minimal valid operator chain
        return new io.nop.stream.core.jobgraph.OperatorChain(java.util.Collections.singletonList(
                new io.nop.stream.core.operators.StreamMap<>(x -> x)));
    }

    /**
     * AR-01 regression (idle task): a SINK-role task whose input gate delivers
     * no data must keep reporting FRESH aliveness on the heartbeat, so the
     * coordinator never flags a healthy idle task as stalled.
     *
     * <p>Pre-fix the heartbeat reported the invokable's {@code lastProgressTime}
     * (frozen at construction when no data flows) → the reported value ages past
     * taskTimeoutMs → stall → recovery. Post-fix the heartbeat reports the task
     * thread's loop-activity timestamp, which the idle loop top keeps refreshing.
     */
    @Test
    void idleSinkTaskHeartbeatReportsFreshAliveness() throws Exception {
        long token = 1L;
        taskManager.updateFencingToken(token);
        TaskAssignment a = new TaskAssignment(
                "job-1", "v-idle", 0, "node-1", "att-1", token, System.currentTimeMillis(), 1);
        taskManager.receiveAssignment(a);

        // SINK-role invokable: input gate that is never finished but never
        // delivers data → the task runs an idle loop.
        StreamTaskInvokable inv = new StreamTaskInvokable(
                buildEmptyOperatorChain(), (io.nop.stream.core.execution.RecordWriter<Object>) null,
                new IdleInputGate());
        taskManager.installInvokable("job-1", "v-idle", 0, inv);

        // Let the idle loop run past the idle-return threshold several times.
        // 负向窗口：idle 循环期间任务必须保持 running（循环维持不变量）
        TestAwait.staysTrue("idle task stays running",
                () -> taskManager.getRunningTaskCount() == 1, 800);
        assertEquals(1, taskManager.getRunningTaskCount(),
                "idle task must still be running (not completed, not failed)");

        taskManager.heartbeat();
        List<TaskProgress> batch = coordinatorRpc.livenessBatches.get(coordinatorRpc.livenessBatches.size() - 1);
        assertNotNull(batch, "heartbeat must report liveness for the idle task");
        assertEquals(1, batch.size(), "exactly one running task → one liveness entry");
        long reported = batch.get(0).getLastProgressTime();
        assertTrue(reported >= System.currentTimeMillis() - 300L,
                "idle task heartbeat must report FRESH aliveness (got " + reported
                        + "); a stale value ages past taskTimeoutMs and falsely triggers stall recovery");

        taskManager.cancelTask("job-1", "v-idle", 0, token);
    }

    /**
     * An input gate that is momentarily idle forever: no data, never finished.
     * Mirrors the AR-02 idle-return path (read() returning empty so the task
     * loop cycles back to its top). The single dummy channel is never touched
     * because read()/isAllFinished() are overridden.
     */
    static class IdleInputGate extends io.nop.stream.core.execution.InputGate {
        IdleInputGate() {
            super(java.util.Collections.singletonList(
                    new io.nop.stream.core.execution.InputChannel(new io.nop.stream.core.execution.ResultPartition())));
        }

        @Override
        public java.util.Optional<io.nop.stream.core.streamrecord.StreamElement> read() {
            return java.util.Optional.empty();
        }

        @Override
        public boolean isAllFinished() {
            return false;
        }
    }

    /**
     * TE-08 rename (R5 test-effectiveness audit): this test does NOT exercise
     * the invokable-throws FAILED report path (building a failing operator
     * chain here is impractical; the FAILED wiring is covered by
     * TestJobCoordinatorPerTaskFailure#reportFailedTaskStatusTriggersGlobalRecovery).
     * It pins the negative invariant: a cancel must NOT produce a spurious
     * FAILED report.
     */
    @Test
    void cancelTaskProducesNoSpuriousFailedReport() throws Exception {
        long token = 1L;
        taskManager.updateFencingToken(token);
        TaskAssignment a = new TaskAssignment(
                "job-1", "v-fail", 0, "node-1", "att-1", token, System.currentTimeMillis(), 1);
        taskManager.receiveAssignment(a);

        // No invokable installed: a cancel must still be safe and must not
        // fabricate a terminal FAILED report.
        taskManager.cancelTask("job-1", "v-fail", 0, token);
        // 负向窗口：cancel 后不得产生虚假 FAILED 报告（循环维持不变量）
        TestAwait.staysTrue("no spurious FAILED report after cancel",
                () -> coordinatorRpc.statusReports.isEmpty(), 150);

        // Canceled task → no report (per design)
        // We only assert that no spurious FAILED report was emitted.
        for (TaskStatusReport r : coordinatorRpc.statusReports) {
            assertNotEquals(TaskStatusReport.TerminalState.FAILED, r.getTerminalState(),
                    "canceled task should not produce a spurious FAILED report");
        }
    }

    @Test
    void cancelBeforeInvokableSkipsMailboxButStillCancels() throws Exception {
        // G58 null-check defense: cancel arrives before invokable is installed.
        // Must not throw NPE; must still cancel the task slot.
        long token = 1L;
        taskManager.updateFencingToken(token);
        TaskAssignment a = new TaskAssignment(
                "job-1", "v-cancel", 0, "node-1", "att-1", token, System.currentTimeMillis(), 1);
        taskManager.receiveAssignment(a);
        assertEquals(1, taskManager.getRunningTaskCount());

        // cancel without installing invokable — must not throw
        assertDoesNotThrow(() -> taskManager.cancelTask("job-1", "v-cancel", 0, token));

        Thread.sleep(150);
        assertEquals(0, taskManager.getRunningTaskCount(),
                "task slot must be released after cancel even without invokable");
    }

    @Test
    void cancelAfterInvokableInvokesMailboxSignalCancel() throws Exception {
        // G58: RunningTask.cancel() must invoke invokable.getMailboxExecutor().signalCancel()
        // before future.cancel(true). We construct a RunningTask directly, install an
        // invokable, then call cancel() and inspect the mailbox flag — sidestepping the
        // full TaskManager.receiveAssignment lifecycle (which would have the task
        // thread finish too quickly for a blocking-source-based test).
        RunningTask rt = new RunningTask(taskManager,
                "job-1", "v-mbx", 0, 1L, "att-1", 1);
        StreamTaskInvokable inv = new StreamTaskInvokable(buildEmptyOperatorChain());

        // Install invokable on the RunningTask directly
        rt.setInvokable(inv);

        boolean mailboxCancelledBefore = inv.getMailboxExecutor().isCancelled();
        assertFalse(mailboxCancelledBefore,
                "mailbox should NOT be cancelled before cancel");

        // G58: cancel() must call inv.getMailboxExecutor().signalCancel()
        rt.cancel();

        boolean mailboxCancelledAfter = inv.getMailboxExecutor().isCancelled();
        assertTrue(mailboxCancelledAfter,
                "G58: RunningTask.cancel() must invoke mailbox.signalCancel (mailbox.isCancelled() should be true)");
    }

    @Test
    void cancelWithoutInvokableDoesNotThrow() {
        // G58 null-check defense: cancel arrives before invokable is installed.
        // RunningTask.cancel() must not throw NPE.
        RunningTask rt = new RunningTask(taskManager,
                "job-1", "v-noop", 0, 1L, "att-1", 1);
        // invokable field is still null
        assertDoesNotThrow(() -> rt.cancel());
    }

    // ==================== Mocks ====================

    static class CapturingCoordinatorRpc implements io.nop.stream.runtime.rpc.IStreamCoordinatorRpcService {
        final CopyOnWriteArrayList<TaskStatusReport> statusReports = new CopyOnWriteArrayList<>();
        final CopyOnWriteArrayList<List<TaskProgress>> livenessBatches = new CopyOnWriteArrayList<>();

        @Override
        public void receiveCheckpointAck(CheckpointAckMessage ack) {}

        @Override
        public void reportTaskStatus(TaskStatusReport report) {
            statusReports.add(report);
        }

        @Override
        public void reportNodeTaskLiveness(String nodeId, List<TaskProgress> progress) {
            livenessBatches.add(new ArrayList<>(progress));
        }

        @Override
        public void terminate(io.nop.stream.core.checkpoint.JobTerminationMode mode) {}

        @Override
        public void abortCheckpoint(long epochId) {}

        @Override
        public io.nop.stream.runtime.coordinator.JobStatusResponse getJobStatus() {
            return new io.nop.stream.runtime.coordinator.JobStatusResponse();
        }
    }

    static class NoopMessageService implements IMessageService {
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
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }
    }

    static class NoopClusterRegistry implements ClusterRegistry {
        @Override public void registerCoordinator(String jobId, String coordinatorId, long fencingEpoch) {}
        @Override public io.nop.stream.runtime.cluster.CoordinatorInfo getActiveCoordinator(String jobId) { return null; }
        @Override public void registerNode(String nodeId, String endpoint, int capacity) {}
        @Override public boolean renewLease(String nodeId, long leaseTimeoutMs) { return true; }
        @Override public io.nop.stream.runtime.cluster.LeaseInfo getNodeLease(String nodeId) { return null; }
        @Override public List<io.nop.stream.runtime.cluster.NodeInfo> getActiveNodes() { return new ArrayList<>(); }

        @Override
        public void assignTask(String jobId, String vertexId, int subtaskIndex,
                               String nodeId, String attemptId, long fencingEpoch,
                               int attemptNumber) {}

        @Override
        public TaskAssignment getTaskAssignment(String jobId, String vertexId, int subtaskIndex) { return null; }

        @Override
        public List<TaskAssignment> getAttemptHistory(String jobId, String vertexId, int subtaskIndex) {
            return new ArrayList<>();
        }

        @Override
        public void removeTaskAssignment(String jobId, String vertexId, int subtaskIndex) {}
    }
}
