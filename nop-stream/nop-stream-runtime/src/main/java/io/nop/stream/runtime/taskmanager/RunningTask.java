/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.taskmanager;

import io.nop.api.core.time.CoreMetrics;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import io.nop.stream.core.checkpoint.CheckpointBarrier;
import io.nop.stream.core.checkpoint.TaskLocation;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.execution.CheckpointBarrierTracker;
import io.nop.stream.core.execution.task.StreamTaskInvokable;
import io.nop.stream.runtime.cluster.TaskAssignment;
import io.nop.stream.runtime.coordinator.TaskStatusReport;
import io.nop.stream.runtime.rpc.IStreamCoordinatorRpcService;

import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_INVALID_STATE;

/**
 * A running task tracked by the TaskManager.
 */
public class RunningTask implements Runnable {
    private final TaskManager owner;
    private final String jobId;
    // Package-private below: fields read/written directly by the owning
    // TaskManager (liveness scan, slot replacement CAS), as in the former
    // inner-class layout.
    final String vertexId;
    final int subtaskIndex;
    private final long fencingEpoch;
    final String attemptId;
    /**
     * Per-subtask attempt number (mirrors {@link TaskAssignment#getAttemptNumber()}).
     * Carried in {@link TaskStatusReport} so the coordinator can correlate reports
     * with the right attempt.
     */
    final int attemptNumber;
    private final TaskLocation taskLocation;
    private final CountDownLatch invokableLatch;

    volatile StreamTaskInvokable invokable;
    private volatile Future<?> future;
    private volatile boolean canceled;
    private volatile Throwable error;
    /**
     * Set when the task thread reached its terminal state. A
     * SUCCESSFULLY completed task RETAINS its registry entry (bounded-run tail
     * commits — see the finally block), so {@link TaskManager#getRunningTaskCount()} must
     * exclude finished entries or completion detectors
     * ({@code EmbeddedDistributedExecutor}/{@code RpcDistributedExecutor})
     * would wait forever on retained entries.
     */
    private volatile boolean finished;
    final AtomicBoolean semaphoreReleased = new AtomicBoolean(false);

    public RunningTask(TaskManager owner, String jobId, String vertexId, int subtaskIndex,
                       long fencingEpoch, String attemptId) {
        this(owner, jobId, vertexId, subtaskIndex, fencingEpoch, attemptId, 1);
    }

    public RunningTask(TaskManager owner, String jobId, String vertexId, int subtaskIndex,
                       long fencingEpoch, String attemptId, int attemptNumber) {
        this.owner = owner;
        this.jobId = jobId;
        this.vertexId = vertexId;
        this.subtaskIndex = subtaskIndex;
        this.fencingEpoch = fencingEpoch;
        this.attemptId = attemptId;
        this.attemptNumber = attemptNumber;
        this.taskLocation = new TaskLocation(jobId, "pipeline-0", vertexId, subtaskIndex);
        this.invokableLatch = new CountDownLatch(1);
    }

    @Override
    public void run() {
        if (canceled) {
            TaskManager.LOG.info("Task {}/{}/{} was canceled before execution", jobId, vertexId, subtaskIndex);
            return;
        }

        TaskManager.LOG.info("Running task {}/{}/{} (attempt={})", jobId, vertexId, subtaskIndex, attemptId);

        try {
            // Wait for invokable to be installed if not yet available
            StreamTaskInvokable inv = waitForInvokable();
            if (canceled) {
                TaskManager.LOG.info("Task {}/{}/{} canceled while waiting for invokable", jobId, vertexId, subtaskIndex);
                return;
            }
            if (inv == null) {
                // Invokable-install timeout: NOT a success and NOT a cancel. A
                // timeout means the coordinator died (or stalled) between the
                // assignment and the install — the finally block must report
                // FAILED so failover kicks in; reporting COMPLETED here would
                // silently mark a never-run subtask as successful.
                this.error = new StreamException(ERR_STREAM_INVALID_STATE).param(ARG_DETAIL,
                        "Timed out after " + TaskManager.invokableWaitTimeoutMs
                                + "ms waiting for invokable installation for "
                                + jobId + "/" + vertexId + "/" + subtaskIndex);
                TaskManager.LOG.error("Task {}/{}/{} failed: invokable installation timed out",
                        jobId, vertexId, subtaskIndex);
                return;
            }

            inv.invoke();

            if (!canceled) {
                TaskManager.LOG.info("Task {}/{}/{} completed successfully", jobId, vertexId, subtaskIndex);
            }
        } catch (Throwable t) {
            if (!canceled) {
                this.error = t;
                TaskManager.LOG.error("Task {}/{}/{} failed", jobId, vertexId, subtaskIndex, t);
            }
        } finally {
            String key = TaskManager.taskKey(jobId, vertexId, subtaskIndex);
            boolean success = error == null && !canceled;
            finished = true;
            owner.completedTasks.put(key, new TaskResult(jobId, vertexId, subtaskIndex,
                    success, canceled, error));
            if (owner.completedTasks.size() > TaskManager.MAX_COMPLETED_TASKS) {
                java.util.Iterator<String> it = owner.completedTasks.keySet().iterator();
                if (it.hasNext()) {
                    it.next();
                    it.remove();
                }
            }
            // Remove the
            // registry entry ONLY if this task still owns it. A stale attempt's
            // thread can outlive its replacement's deployment (recovery cancels
            // the old attempt, but the thread winds down asynchronously; the
            // fresh deployTask puts the new RunningTask under the same key
            // immediately). The old finally's unconditional remove(key) then
            // deleted the REPLACEMENT's entry — the new task kept running but
            // became invisible to triggerCheckpoint (barrier never registered
            // on its tracker, checkpoint ACKs dropped with "no matching
            // in-flight epoch") and to cancelTask ("No running task to
            // cancel"). Conditional remove closes the window.
            //
            // Bounded-run tail commits: a NATURALLY COMPLETED task
            // (success, not canceled) RETAINS its registry entry. The last
            // data of a bounded run reaches the 2PC sinks exactly at
            // EOS-MAX_WATERMARK — after the task finished but BEFORE the
            // coordinator's checkpoint for it completes. If the finished task
            // deregistered, the commit notification
            // (notifyCheckpointComplete → sink finishCommit) found no task and
            // the epoch's buffered output was lost forever (no later recovery
            // exists to re-commit durable-but-uncommitted transactions).
            // Retaining the entry lets the finished sink commit its last
            // epochs; the entry is replaced by any redeployment and cleared
            // on stop(). Canceled/failed tasks still remove themselves.
            if (success) {
                TaskManager.LOG.debug("Task {}/{}/{} finished; retaining registry entry for post-finish commit notifications",
                        jobId, vertexId, subtaskIndex);
            } else {
                boolean stillOwner = owner.runningTasks.remove(key, this);
                if (!stillOwner) {
                    TaskManager.LOG.debug("Task {}/{} attempt {} exit: registry entry already owned by a newer attempt",
                            jobId, vertexId, attemptId);
                }
            }
            if (semaphoreReleased.compareAndSet(false, true)) {
                owner.capacitySemaphore.release();
            }

            // Per-task terminal-state report to the coordinator. Only
            // COMPLETED / FAILED are reported (CANCELED is initiated by the
            // coordinator itself, no need to echo back). #24 — failure to
            // report is logged (no silent swallow).
            if (!canceled) {
                reportTerminalStatus(success);
            }
        }
    }

    /**
     * Reports this task's terminal state to the coordinator. Failures
     * are logged but do not tear down the run loop (#24 — explicit handling,
     * not silent).
     */
    private void reportTerminalStatus(boolean success) {
        IStreamCoordinatorRpcService rpc = owner.coordinatorRpcService;
        if (rpc == null) {
            // No coordinator wired (e.g. unit-test fixture); skip silently.
            return;
        }
        long lastProgress = -1L;
        StreamTaskInvokable inv = this.invokable;
        if (inv != null) {
            lastProgress = inv.getLastProgressTime();
        }
        TaskStatusReport.TerminalState state = success
                ? TaskStatusReport.TerminalState.COMPLETED
                : TaskStatusReport.TerminalState.FAILED;
        if (!success) {
            // Count real task failures.
            owner.nodeMetrics.taskFailed();
        }
        String cause = error != null ? error.toString() : null;
        TaskStatusReport report = new TaskStatusReport(
                jobId, vertexId, subtaskIndex, attemptNumber,
                state, cause, lastProgress, fencingEpoch,
                CoreMetrics.currentTimeMillis());
        try {
            rpc.reportTaskStatus(report);
        } catch (Exception e) {
            TaskManager.LOG.warn("Failed to report terminal status for {}/{}/{} (state={})",
                    jobId, vertexId, subtaskIndex, state, e);
        }
    }

    private StreamTaskInvokable waitForInvokable() throws InterruptedException {
        if (!invokableLatch.await(TaskManager.invokableWaitTimeoutMs, TimeUnit.MILLISECONDS)) {
            TaskManager.LOG.warn("Timed out waiting for invokable for {}/{}/{}", jobId, vertexId, subtaskIndex);
            return null;
        }
        return invokable;
    }

    public void setInvokable(StreamTaskInvokable invokable) {
        this.invokable = invokable;
        invokableLatch.countDown();
    }

    public void setFuture(Future<?> future) {
        this.future = future;
    }

    public void cancel() {
        canceled = true;
        invokableLatch.countDown();
        // Cooperative mailbox cancel first, then interrupt. Mirrors the
        // LOCAL path (GraphModelCheckpointExecutor) — the mailbox
        // signalCancel() raises the cancel flag and queues a cancel marker so
        // the task thread observes cancellation at its next mailbox drain even
        // if it is not in a blocking-interruptible section.
        //
        // null-check defense: invokable is volatile, lazily set by
        // setInvokable() (30s waitForInvokable window). If cancel arrives
        // before the invokable is installed, skip the mailbox call (no NPE);
        // the state-transition + future.cancel(true) + latch countdown still
        // apply so the task exits cleanly once the invokable arrives (or
        // waitForInvokable times out).
        StreamTaskInvokable inv = this.invokable;
        if (inv != null) {
            try {
                inv.getMailboxExecutor().signalCancel();
            } catch (Exception e) {
                TaskManager.LOG.warn("signalCancel failed for {}/{}/{} (falling back to interrupt-only)",
                        jobId, vertexId, subtaskIndex, e);
            }
        }
        if (future != null) {
            future.cancel(true);
        }
    }

    public void triggerCheckpoint(CheckpointBarrier barrier) {
        StreamTaskInvokable inv = this.invokable;
        if (inv == null) {
            TaskManager.LOG.debug("Cannot trigger checkpoint: invokable not yet installed for {}/{}/{}",
                    jobId, vertexId, subtaskIndex);
            return;
        }
        CheckpointBarrierTracker tracker = inv.getBarrierTracker();
        if (tracker == null) {
            TaskManager.LOG.debug("No barrier tracker for {}/{}/{}", jobId, vertexId, subtaskIndex);
            return;
        }
        try {
            tracker.triggerCheckpoint(barrier.getId(), barrier.getTimestamp(), barrier.getCheckpointType());
        } catch (Exception e) {
            TaskManager.LOG.error("Failed to trigger checkpoint on {}/{}/{}", jobId, vertexId, subtaskIndex, e);
        }
    }

    /**
     * Forwards a durable-checkpoint notification to this task's
     * operator chain's {@link io.nop.stream.core.checkpoint.participant.CheckpointParticipant}s
     * (the 2PC sink UDFs). Mirrors the LOCAL coordinator-side participant
     * notification — the commit only happens after the coordinator persisted
     * the epoch manifest (2PC invariant: commit after durable).
     */
    public void notifyCheckpointComplete(long checkpointId) {
        StreamTaskInvokable inv = this.invokable;
        if (inv == null || inv.getOperatorChain() == null) {
            TaskManager.LOG.debug("Cannot notify checkpoint completion: invokable not installed for {}/{}/{}",
                    jobId, vertexId, subtaskIndex);
            return;
        }
        for (io.nop.stream.core.operators.StreamOperator<?> op : inv.getOperatorChain().getOperators()) {
            try {
                if (op instanceof io.nop.stream.core.checkpoint.participant.CheckpointParticipant) {
                    ((io.nop.stream.core.checkpoint.participant.CheckpointParticipant) op)
                            .finishCommit(checkpointId, true);
                } else if (op instanceof io.nop.stream.core.operators.AbstractUdfStreamOperator) {
                    Object udf = ((io.nop.stream.core.operators.AbstractUdfStreamOperator<?, ?>) op).getUserFunction();
                    if (udf instanceof io.nop.stream.core.checkpoint.participant.CheckpointParticipant && udf != op) {
                        ((io.nop.stream.core.checkpoint.participant.CheckpointParticipant) udf)
                                .finishCommit(checkpointId, true);
                    }
                }
            } catch (Exception e) {
                // Observable failure, not a silent swallow: the coordinator-side
                // forwarder retries failed commits and the subsuming commit of
                // the next epoch re-covers this one (ledger/manifest idempotency
                // guards make the retry safe).
                TaskManager.LOG.error("finishCommit({}) failed for an operator of {}/{}/{} — "
                        + "subsuming commit / retry will re-cover this epoch",
                        checkpointId, jobId, vertexId, subtaskIndex, e);
            }
        }
    }

    public long getFencingEpoch() {
        return fencingEpoch;
    }

    /** Whether the task thread reached its terminal state. */
    public boolean isFinished() {
        return finished;
    }

    public String getJobId() { return jobId; }
    public String getVertexId() { return vertexId; }
    public int getSubtaskIndex() { return subtaskIndex; }
    public TaskLocation getTaskLocation() { return taskLocation; }
}
