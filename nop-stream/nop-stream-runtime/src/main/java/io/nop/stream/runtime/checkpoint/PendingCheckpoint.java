/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.checkpoint;

import io.nop.stream.core.checkpoint.CheckpointType;
import io.nop.stream.core.checkpoint.CompletedCheckpoint;
import io.nop.stream.core.checkpoint.TaskStateSnapshot;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 正在进行的 checkpoint，跟踪任务 ACK 状态。
 */
public class PendingCheckpoint {

    private final long jobId;
    private final int pipelineId;
    private final long checkpointId;
    private final long triggerTimestamp;
    private final CheckpointType checkpointType;

    private final Set<Long> notYetAcknowledgedTasks;
    private final Map<Long, TaskStateSnapshot> taskStates;
    private final CompletableFuture<CompletedCheckpoint> completableFuture;

    private volatile boolean isDisposed = false;

    public PendingCheckpoint(
            long jobId,
            int pipelineId,
            long checkpointId,
            long triggerTimestamp,
            CheckpointType checkpointType,
            Set<Long> tasksToAcknowledge) {
        this.jobId = jobId;
        this.pipelineId = pipelineId;
        this.checkpointId = checkpointId;
        this.triggerTimestamp = triggerTimestamp;
        this.checkpointType = checkpointType;
        this.notYetAcknowledgedTasks = ConcurrentHashMap.newKeySet();
        this.notYetAcknowledgedTasks.addAll(tasksToAcknowledge);
        this.taskStates = new ConcurrentHashMap<>();
        this.completableFuture = new CompletableFuture<>();
    }

    public long getJobId() {
        return jobId;
    }

    public int getPipelineId() {
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

    public Set<Long> getNotYetAcknowledgedTasks() {
        return Collections.unmodifiableSet(notYetAcknowledgedTasks);
    }

    public Map<Long, TaskStateSnapshot> getTaskStates() {
        return Collections.unmodifiableMap(taskStates);
    }

    public void acknowledgeTask(long taskId, TaskStateSnapshot state) {
        if (isDisposed) {
            return;
        }

        notYetAcknowledgedTasks.remove(taskId);
        if (state != null) {
            taskStates.put(taskId, state);
        }

        if (isFullyAcknowledged() && !completableFuture.isDone()) {
            CompletedCheckpoint completed = toCompletedCheckpoint();
            completableFuture.complete(completed);
        }
    }

    public void acknowledgePrecedingCheckpoint(long checkpointId) {
        // 当更早的 checkpoint 完成时，可以安全地清理资源
        // 这里是空实现，可以扩展
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
        if (!isDisposed) {
            isDisposed = true;
            if (!completableFuture.isDone()) {
                Exception error = cause != null
                        ? new RuntimeException("Checkpoint aborted: " + reason, cause)
                        : new RuntimeException("Checkpoint aborted: " + reason);
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
                "jobId=" + jobId +
                ", pipelineId=" + pipelineId +
                ", checkpointId=" + checkpointId +
                ", checkpointType=" + checkpointType +
                ", acknowledgedTasks=" + taskStates.size() +
                ", pendingTasks=" + notYetAcknowledgedTasks.size() +
                '}';
    }
}
