/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.checkpoint;

import io.nop.api.core.annotations.data.DataBean;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@DataBean
public class CompletedCheckpoint implements Serializable {

    private static final long serialVersionUID = 2L;

    private final String jobId;
    private final String pipelineId;
    private final long checkpointId;
    private final long triggerTimestamp;
    private final long completedTimestamp;
    private final CheckpointType checkpointType;
    private final Map<TaskLocation, TaskStateSnapshot> taskStates;
    private boolean restored;

    public CompletedCheckpoint(
            String jobId,
            String pipelineId,
            long checkpointId,
            long triggerTimestamp,
            long completedTimestamp,
            CheckpointType checkpointType,
            Map<TaskLocation, TaskStateSnapshot> taskStates) {
        this.jobId = jobId;
        this.pipelineId = pipelineId;
        this.checkpointId = checkpointId;
        this.triggerTimestamp = triggerTimestamp;
        this.completedTimestamp = completedTimestamp;
        this.checkpointType = checkpointType;
        this.taskStates = taskStates != null ? taskStates : new HashMap<>();
        this.restored = false;
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

    public long getCompletedTimestamp() {
        return completedTimestamp;
    }

    public CheckpointType getCheckpointType() {
        return checkpointType;
    }

    public Map<TaskLocation, TaskStateSnapshot> getTaskStates() {
        return taskStates;
    }

    public TaskStateSnapshot getTaskState(TaskLocation taskLocation) {
        return taskStates.get(taskLocation);
    }

    public void addTaskState(TaskLocation taskLocation, TaskStateSnapshot state) {
        taskStates.put(taskLocation, state);
    }

    public boolean isRestored() {
        return restored;
    }

    public void setRestored(boolean restored) {
        this.restored = restored;
    }

    public int getTaskCount() {
        return taskStates.size();
    }

    public long estimateSize() {
        long size = 0;
        for (TaskStateSnapshot state : taskStates.values()) {
            size += state.estimateSize();
        }
        return size;
    }

    public long getDuration() {
        return completedTimestamp - triggerTimestamp;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CompletedCheckpoint that = (CompletedCheckpoint) o;
        return checkpointId == that.checkpointId &&
                triggerTimestamp == that.triggerTimestamp &&
                completedTimestamp == that.completedTimestamp &&
                Objects.equals(jobId, that.jobId) &&
                Objects.equals(pipelineId, that.pipelineId) &&
                checkpointType == that.checkpointType &&
                Objects.equals(taskStates, that.taskStates);
    }

    @Override
    public int hashCode() {
        return Objects.hash(jobId, pipelineId, checkpointId, triggerTimestamp, completedTimestamp, checkpointType, taskStates);
    }

    @Override
    public String toString() {
        return "CompletedCheckpoint{" +
                "jobId='" + jobId + '\'' +
                ", pipelineId='" + pipelineId + '\'' +
                ", checkpointId=" + checkpointId +
                ", triggerTimestamp=" + triggerTimestamp +
                ", completedTimestamp=" + completedTimestamp +
                ", checkpointType=" + checkpointType +
                ", taskCount=" + taskStates.size() +
                ", restored=" + restored +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String jobId;
        private String pipelineId;
        private long checkpointId;
        private long triggerTimestamp;
        private long completedTimestamp;
        private CheckpointType checkpointType = CheckpointType.CHECKPOINT;
        private Map<TaskLocation, TaskStateSnapshot> taskStates = new HashMap<>();

        public Builder jobId(String jobId) {
            this.jobId = jobId;
            return this;
        }

        public Builder pipelineId(String pipelineId) {
            this.pipelineId = pipelineId;
            return this;
        }

        public Builder checkpointId(long checkpointId) {
            this.checkpointId = checkpointId;
            return this;
        }

        public Builder triggerTimestamp(long triggerTimestamp) {
            this.triggerTimestamp = triggerTimestamp;
            return this;
        }

        public Builder completedTimestamp(long completedTimestamp) {
            this.completedTimestamp = completedTimestamp;
            return this;
        }

        public Builder checkpointType(CheckpointType checkpointType) {
            this.checkpointType = checkpointType;
            return this;
        }

        public Builder addTaskState(TaskLocation taskLocation, TaskStateSnapshot state) {
            this.taskStates.put(taskLocation, state);
            return this;
        }

        public Builder taskStates(Map<TaskLocation, TaskStateSnapshot> taskStates) {
            this.taskStates = taskStates != null ? taskStates : new HashMap<>();
            return this;
        }

        public CompletedCheckpoint build() {
            return new CompletedCheckpoint(
                    jobId, pipelineId, checkpointId,
                    triggerTimestamp, completedTimestamp,
                    checkpointType, taskStates);
        }
    }
}
