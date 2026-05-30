/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.checkpoint;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import io.nop.stream.core.checkpoint.CheckpointType;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.checkpoint.CompletedCheckpoint;
import io.nop.stream.core.checkpoint.TaskLocation;
import io.nop.stream.core.checkpoint.TaskStateSnapshot;

public class PendingCheckpoint {

    public enum Status {
        RUNNING, COMPLETED, ABORTED
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
        if (isDisposed || status.get() != Status.RUNNING) {
            return;
        }

        notYetAcknowledgedTasks.remove(taskLocation);
        if (state != null) {
            taskStates.put(taskLocation, state);
        }

        if (isFullyAcknowledged() && !completableFuture.isDone()) {
            // AR-19: The future is not auto-completed here. The coordinator completes it
            // after successful storage. For standalone use without a coordinator, callers
            // should poll isFullyAcknowledged() and call forceComplete() or handle completion
            // themselves.
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

    public void abort(String reason, Throwable cause) {
        if (status.compareAndSet(Status.RUNNING, Status.ABORTED)) {
            isDisposed = true;
            if (!completableFuture.isDone()) {
                Exception error = cause != null
                        ? new StreamException("Checkpoint aborted: " + reason, cause)
                        : new StreamException("Checkpoint aborted: " + reason);
                completableFuture.completeExceptionally(error);
            }
        }
    }

    public void abort(String reason) {
        abort(reason, null);
    }

    public boolean isDisposed() {
        return isDisposed;
    }

    public boolean forceComplete() {
        if (isFullyAcknowledged() && !completableFuture.isDone()) {
            return completableFuture.complete(toCompletedCheckpoint());
        }
        return false;
    }

    public AtomicReference<Status> getStatus() {
        return status;
    }

    public void dispose() {
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
