/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.taskmanager;

import io.nop.stream.core.checkpoint.TaskStateSnapshot;
import io.nop.stream.core.checkpoint.participant.CheckpointParticipant;
import io.nop.stream.core.execution.InputGate;
import io.nop.stream.core.execution.InputChannel;
import io.nop.stream.core.execution.ResultPartition;
import io.nop.stream.core.execution.RecordWriter;
import io.nop.stream.core.execution.task.StreamTaskInvokable;
import io.nop.stream.core.jobgraph.OperatorChain;
import io.nop.stream.core.operators.StreamMap;
import io.nop.stream.runtime.cluster.ClusterRegistry;
import io.nop.stream.runtime.cluster.TaskAssignment;
import io.nop.stream.runtime.coordinator.TaskProgress;
import io.nop.stream.runtime.coordinator.TaskStatusReport;
import io.nop.stream.runtime.rpc.IStreamCoordinatorRpcService;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 358 Phase 2 focused tests (task lifecycle hardening):
 * <ul>
 *   <li>Fix-3: 2PC checkpoint commits run on the dedicated {@code tm-commit-*}
 *       executor, not on the caller's message-service dispatch thread.</li>
 *   <li>Fix-4: an unexpected exception during heartbeat liveness bookkeeping is
 *       contained — the scheduled heartbeat loop keeps beating (before the fix a
 *       single uncaught exception permanently killed the
 *       {@code scheduleAtFixedRate} loop and froze liveness reporting).</li>
 *   <li>Fix-6: checkpoint ACK sends retry transient failures with a bounded
 *       budget; exhaustion is logged and counted.</li>
 * </ul>
 */
class TestPlan358LifecycleHardening {

    private static final String NODE = "node-p358";

    // ------------------------------------------------------------------
    // Fix-3: commit off the dispatch thread
    // ------------------------------------------------------------------

    @Test
    void checkpointCommitRunsOnDedicatedCommitExecutor() throws Exception {
        TaskManager tm = newBlockingTaskManager(new RecordingRpc());
        try {
            long token = 11L;
            tm.updateFencingToken(token);
            tm.receiveAssignment(new TaskAssignment(
                    "job-p358-commit", "v-c", 0, NODE, "att-1", token, System.currentTimeMillis(), 1));

            ThreadRecordingParticipant participant = new ThreadRecordingParticipant();
            tm.installInvokable("job-p358-commit", "v-c", 0, sinkInvokable(participant));
            awaitTrue("task installed and running", () -> tm.getRunningTaskCount() == 1, 2000);

            String callerThread = Thread.currentThread().getName();
            tm.notifyCheckpointComplete(42L, token);

            awaitTrue("finishCommit executed asynchronously",
                    () -> !participant.commitThreads.isEmpty(), 2000);
            String commitThread = participant.commitThreads.get(0);
            assertTrue(commitThread.startsWith("tm-commit-"),
                    "finishCommit must run on the dedicated commit executor, got: " + commitThread);
            assertNotEquals(callerThread, commitThread,
                    "the caller (dispatch/RPC thread) must never run the blocking commit itself");
        } finally {
            tm.stop();
        }
    }

    // ------------------------------------------------------------------
    // Fix-4: heartbeat loop survives unexpected liveness exceptions
    // ------------------------------------------------------------------

    @Test
    void heartbeatLoopSurvivesUnexpectedLivenessException() throws Exception {
        CountingClusterRegistry clusterRegistry = new CountingClusterRegistry();
        TaskManager tm = new TaskManager(NODE + "-hb", "embedded:hb", 4,
                new TestTaskManagerLivenessAndReporting.NoopMessageService(), clusterRegistry, "ct",
                50L, 30000L);
        tm.setCoordinatorRpcService(new RecordingRpc());
        tm.start();
        try {
            long token = 12L;
            tm.updateFencingToken(token);
            tm.receiveAssignment(new TaskAssignment(
                    "job-p358-hb", "v-x", 0, NODE + "-hb", "att-1", token, System.currentTimeMillis(), 1));
            tm.installInvokable("job-p358-hb", "v-x", 0, new ExplodingActivityInvokable());
            awaitTrue("task installed", () -> tm.getRunningTaskCount() == 1, 2000);

            int renewalsBeforeExplosion = clusterRegistry.renewals.get();
            // direct beat: the exploding liveness read must be contained
            assertDoesNotThrow(tm::heartbeat,
                    "an unexpected exception in liveness bookkeeping must not escape heartbeat()");

            // the scheduled loop keeps beating well past the exploding beat —
            // pre-fix, the first exploding beat permanently killed the schedule
            awaitTrue("scheduled heartbeat loop still beating",
                    () -> clusterRegistry.renewals.get() >= renewalsBeforeExplosion + 3, 2000);
        } finally {
            tm.stop();
        }
    }

    // ------------------------------------------------------------------
    // Fix-6: bounded ACK retry
    // ------------------------------------------------------------------

    @Test
    void ackSendRetriesTransientFailures() {
        FlakyAckRpc rpc = new FlakyAckRpc(2);
        TaskManager tm = newBlockingTaskManager(rpc);
        try {
            TaskStateSnapshot snapshot = new TaskStateSnapshot(
                    new io.nop.stream.core.checkpoint.TaskLocation("job-p358-ack", "pipeline-0", "v-ack", 0), 1L);
            tm.sendCheckpointAck(1L, snapshot);
            assertEquals(3, rpc.attempts.get(),
                    "transient failures must be retried within the bounded budget");
        } finally {
            tm.stop();
        }
    }

    @Test
    void ackSendExhaustedBudgetIsCounted() {
        String nodeId = NODE + "-ackfail";
        TaskManager tm = new TaskManager(nodeId, "embedded:ackfail", 2,
                new TestTaskManagerLivenessAndReporting.NoopMessageService(),
                new TestTaskManagerLivenessAndReporting.NoopClusterRegistry(), "ct");
        FlakyAckRpc rpc = new FlakyAckRpc(Integer.MAX_VALUE);
        tm.setCoordinatorRpcService(rpc);
        try {
            TaskStateSnapshot snapshot = new TaskStateSnapshot(
                    new io.nop.stream.core.checkpoint.TaskLocation("job-p358-ack2", "pipeline-0", "v-ack2", 0), 2L);
            tm.sendCheckpointAck(2L, snapshot);

            assertEquals(3, rpc.attempts.get(), "retry budget is bounded (3 attempts)");
            assertEquals(1.0, io.nop.stream.runtime.metrics.TaskNodeMetrics.forNode(nodeId).getAckSendFailureCount(),
                    "budget exhaustion must be counted (no silent skip)");
        } finally {
            tm.stop();
        }
    }

    // ------------------------------------------------------------------
    // helpers & stubs
    // ------------------------------------------------------------------

    private static TaskManager newBlockingTaskManager(IStreamCoordinatorRpcService rpc) {
        TaskManager tm = new TaskManager(NODE, "embedded:p358", 4,
                new TestTaskManagerLivenessAndReporting.NoopMessageService(),
                new TestTaskManagerLivenessAndReporting.NoopClusterRegistry(), "ct");
        tm.setCoordinatorRpcService(rpc);
        tm.start();
        return tm;
    }

    private static StreamTaskInvokable sinkInvokable(ThreadRecordingParticipant participant) {
        return new StreamTaskInvokable(
                new OperatorChain(Collections.<io.nop.stream.core.operators.StreamOperator<?>>singletonList(participant)),
                (RecordWriter<Object>) null,
                new TestTaskManagerLivenessAndReporting.IdleInputGate());
    }

    /** SINK-role invokable whose liveness read explodes (exercises the Fix-4 guard). */
    static class ExplodingActivityInvokable extends StreamTaskInvokable {
        ExplodingActivityInvokable() {
            super(new OperatorChain(Collections.singletonList(new StreamMap<Object, Object>(x -> x))),
                    (RecordWriter<Object>) null,
                    new TestTaskManagerLivenessAndReporting.IdleInputGate());
        }

        @Override
        public long getLastActivityTime() {
            throw new IllegalStateException("boom (plan 358 Fix-4 test)");
        }
    }

    /** StreamMap operator that records the thread executing each 2PC commit. */
    static class ThreadRecordingParticipant extends StreamMap<Object, Object> implements CheckpointParticipant {
        final List<String> commitThreads = new CopyOnWriteArrayList<>();

        ThreadRecordingParticipant() {
            super(x -> x);
        }

        @Override
        public TaskStateSnapshot saveState(long epochId) {
            return null;
        }

        @Override
        public void prepareCommit(long epochId) {
        }

        @Override
        public void finishCommit(long epochId, boolean success) {
            commitThreads.add(Thread.currentThread().getName());
        }

        @Override
        public void restoreFromEpoch(long epochId, TaskStateSnapshot state) {
        }
    }

    static class CountingClusterRegistry extends TestTaskManagerLivenessAndReporting.NoopClusterRegistry {
        final AtomicInteger renewals = new AtomicInteger();

        @Override
        public boolean renewLease(String nodeId, long leaseTimeoutMs) {
            renewals.incrementAndGet();
            return true;
        }
    }

    static class RecordingRpc implements IStreamCoordinatorRpcService {
        final List<TaskProgress> liveness = new CopyOnWriteArrayList<>();
        final List<TaskStatusReport> statuses = new CopyOnWriteArrayList<>();

        @Override
        public void receiveCheckpointAck(CheckpointAckMessage ack) {
        }

        @Override
        public void reportNodeTaskLiveness(String nodeId, List<TaskProgress> progress) {
            liveness.addAll(progress);
        }

        @Override
        public void reportTaskStatus(TaskStatusReport report) {
            statuses.add(report);
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

    static class FlakyAckRpc extends RecordingRpc {
        final int failuresBeforeSuccess;
        final AtomicInteger attempts = new AtomicInteger();

        FlakyAckRpc(int failuresBeforeSuccess) {
            this.failuresBeforeSuccess = failuresBeforeSuccess;
        }

        @Override
        public void receiveCheckpointAck(io.nop.stream.runtime.taskmanager.CheckpointAckMessage ack) {
            if (attempts.incrementAndGet() <= failuresBeforeSuccess) {
                throw new IllegalStateException("transient backend failure (plan 358 Fix-6 test)");
            }
        }
    }

    private static void awaitTrue(String what, java.util.function.BooleanSupplier condition,
                                  long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError("timed out waiting for: " + what);
            }
            Thread.sleep(20);
        }
    }
}
