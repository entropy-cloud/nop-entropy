/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.checkpoint;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import io.nop.stream.core.checkpoint.CheckpointType;
import io.nop.stream.core.exceptions.StreamException;

import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_REASON;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_CHECKPOINT_ABORTED;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_CHECKPOINT_FAILED;
import io.nop.stream.core.checkpoint.CompletedCheckpoint;
import io.nop.stream.core.checkpoint.TaskLocation;
import io.nop.stream.core.checkpoint.TaskStateSnapshot;

public class PendingCheckpoint {

    public enum Status {
        RUNNING, COMPLETED, ABORTED, FAILED
    }

    private final String jobId;
    private final String pipelineId;
    private final long checkpointId;
    private final long triggerTimestamp;
    private final CheckpointType checkpointType;

    private final Set<TaskLocation> notYetAcknowledgedTasks;
    private final Map<TaskLocation, TaskStateSnapshot> taskStates;
    private final CompletableFuture<CompletedCheckpoint> completableFuture;

    private final AtomicReference<Status> status;
    private volatile boolean isDisposed = false;

    public static boolean isValidTransition(Status from, Status to) {
        if (from == Status.RUNNING) {
            return to == Status.COMPLETED || to == Status.ABORTED || to == Status.FAILED;
        }
        if (from == to) {
            return true;
        }
        return false;
    }

    private void checkValidTransition(Status target) {
        Status current = status.get();
        if (!isValidTransition(current, target)) {
            throw new StreamException("Illegal state transition: " + current + " -> " + target);
        }
    }

    public PendingCheckpoint(
            String jobId,
            String pipelineId,
            long checkpointId,
            long triggerTimestamp,
            CheckpointType checkpointType,
            Set<TaskLocation> tasksToAcknowledge) {
        this.jobId = jobId;
        this.pipelineId = pipelineId;
        this.checkpointId = checkpointId;
        this.triggerTimestamp = triggerTimestamp;
        this.checkpointType = checkpointType;
        this.notYetAcknowledgedTasks = ConcurrentHashMap.newKeySet();
        this.notYetAcknowledgedTasks.addAll(tasksToAcknowledge);
        this.taskStates = new ConcurrentHashMap<>();
        this.completableFuture = new CompletableFuture<>();
        this.status = new AtomicReference<>(Status.RUNNING);
    }

    public String getJobId() {
        return jobId;
    }

    public String getPipelineId() {
        return pipelineId;
    }

    public long getCheckpointId() {
        return checkpointId;
    }

    public long getTriggerTimestamp() {
        return triggerTimestamp;
    }

    public CheckpointType getCheckpointType() {
        return checkpointType;
    }

    public CompletableFuture<CompletedCheckpoint> getCompletableFuture() {
        return completableFuture;
    }

    public boolean isFullyAcknowledged() {
        return notYetAcknowledgedTasks.isEmpty();
    }

    public int getNumberOfAcknowledgedTasks() {
        return taskStates.size();
    }

    public int getNumberOfNotAcknowledgedTasks() {
        return notYetAcknowledgedTasks.size();
    }

    public int getNumberOfTasks() {
        return notYetAcknowledgedTasks.size() + taskStates.size();
    }

    public Set<TaskLocation> getNotYetAcknowledgedTasks() {
        return Collections.unmodifiableSet(notYetAcknowledgedTasks);
    }

    public Map<TaskLocation, TaskStateSnapshot> getTaskStates() {
        return Collections.unmodifiableMap(taskStates);
    }

    public TaskStateSnapshot getTaskState(TaskLocation taskLocation) {
        return taskStates.get(taskLocation);
    }

    public synchronized void acknowledgeTask(TaskLocation taskLocation, TaskStateSnapshot state) {
        if (isDisposed) {
            throw new StreamException("Cannot acknowledge disposed checkpoint " + checkpointId);
        }
        if (status.get() != Status.RUNNING) {
            throw new StreamException("Cannot acknowledge checkpoint " + checkpointId
                    + " in state " + status.get());
        }

        notYetAcknowledgedTasks.remove(taskLocation);
        if (state != null) {
            taskStates.put(taskLocation, state);
        }
    }

    public CompletedCheckpoint toCompletedCheckpoint() {
        return CompletedCheckpoint.builder()
                .jobId(jobId)
                .pipelineId(pipelineId)
                .checkpointId(checkpointId)
                .triggerTimestamp(triggerTimestamp)
                .completedTimestamp(System.currentTimeMillis())
                .checkpointType(checkpointType)
                .taskStates(new HashMap<>(taskStates))
                .build();
    }

    public synchronized void abort(String reason, Throwable cause) {
        checkValidTransition(Status.ABORTED);
        if (status.compareAndSet(Status.RUNNING, Status.ABORTED)) {
            isDisposed = true;
            if (!completableFuture.isDone()) {
                Exception error = cause != null
                        ? new StreamException(ERR_STREAM_CHECKPOINT_ABORTED, cause).param(ARG_REASON, reason)
                        : new StreamException(ERR_STREAM_CHECKPOINT_ABORTED).param(ARG_REASON, reason);
                completableFuture.completeExceptionally(error);
            }
        }
    }

    public synchronized void fail(String reason, Throwable cause) {
        checkValidTransition(Status.FAILED);
        if (status.compareAndSet(Status.RUNNING, Status.FAILED)) {
            isDisposed = true;
            if (!completableFuture.isDone()) {
                Exception error = cause != null
                        ? new StreamException(ERR_STREAM_CHECKPOINT_FAILED, cause).param(ARG_REASON, reason)
                        : new StreamException(ERR_STREAM_CHECKPOINT_FAILED).param(ARG_REASON, reason);
                completableFuture.completeExceptionally(error);
            }
        }
    }

    /**
     * F-01 (Plan 2026-09-04-1326-1 Phase 2): force-fail after the checkpoint was already
     * COMPLETED in-memory (段1 CAS RUNNING→COMPLETED in {@code completePendingCheckpoint})
     * but the durable persist (段2) failed. {@link #fail(String, Throwable)} is unusable
     * there — COMPLETED→FAILED is an illegal transition for it by design (it guards the
     * RUNNING-era failure paths), and the audit's literal "use pending.fail" would throw
     * from {@code checkValidTransition} BEFORE the coordinator's 段3b bookkeeping.
     *
     * <p>This explicit force transition is legal ONLY from COMPLETED (the durable-write
     * failed, so the in-memory completion was never published durably and must be
     * retracted): the future completes exceptionally with the real root cause so every
     * waiter ({@code future.get(timeout)} blocking callers and registered callbacks) is
     * released in seconds instead of hanging for the full 10-minute default timeout.
     * Idempotent no-op from FAILED/ABORTED (already terminally failed with the future
     * completed exceptionally); no-op from RUNNING (callers must use the regular
     * {@link #fail(String, Throwable)} path there).
     */
    public synchronized void forceFail(String reason, Throwable cause) {
        Status current = status.get();
        if (current == Status.FAILED || current == Status.ABORTED) {
            return;
        }
        if (current != Status.COMPLETED) {
            // RUNNING must go through the regular fail() path (valid-transition guard)
            fail(reason, cause);
            return;
        }
        status.set(Status.FAILED);
        isDisposed = true;
        if (!completableFuture.isDone()) {
            Exception error = cause != null
                    ? new StreamException(ERR_STREAM_CHECKPOINT_FAILED, cause).param(ARG_REASON, reason)
                    : new StreamException(ERR_STREAM_CHECKPOINT_FAILED).param(ARG_REASON, reason);
            completableFuture.completeExceptionally(error);
        }
    }

    public void abort(String reason) {
        abort(reason, null);
    }

    public boolean isDisposed() {
        return isDisposed;
    }

    public synchronized boolean forceComplete() {
        if (isFullyAcknowledged() && !completableFuture.isDone()) {
            return completableFuture.complete(toCompletedCheckpoint());
        }
        return false;
    }

    public AtomicReference<Status> getStatus() {
        return status;
    }

    public synchronized void dispose() {
        if (!isDisposed) {
            isDisposed = true;
            notYetAcknowledgedTasks.clear();
            taskStates.clear();
            if (!completableFuture.isDone()) {
                completableFuture.cancel(false);
            }
        }
    }

    @Override
    public String toString() {
        return "PendingCheckpoint{" +
                "jobId='" + jobId + '\'' +
                ", pipelineId='" + pipelineId + '\'' +
                ", checkpointId=" + checkpointId +
                ", checkpointType=" + checkpointType +
                ", acknowledgedTasks=" + taskStates.size() +
                ", pendingTasks=" + notYetAcknowledgedTasks.size() +
                '}';
    }
}
