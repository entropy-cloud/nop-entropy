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
import java.util.Objects;

@DataBean
public class SavepointMetadata implements Serializable {

    private static final long serialVersionUID = 1L;

    private long checkpointId;
    private long createTime;
    private String jobId;
    private String pipelineId;
    private int operatorStateCount;
    private int keyedStateCount;

    public SavepointMetadata() {
    }

    public SavepointMetadata(long checkpointId, long createTime, String jobId,
                             String pipelineId, int operatorStateCount, int keyedStateCount) {
        this.checkpointId = checkpointId;
        this.createTime = createTime;
        this.jobId = jobId;
        this.pipelineId = pipelineId;
        this.operatorStateCount = operatorStateCount;
        this.keyedStateCount = keyedStateCount;
    }

    /**
     * Creates a SavepointMetadata from a CompletedCheckpoint.
     *
     * @param checkpoint the completed checkpoint
     * @return metadata summarizing the checkpoint
     */
    public static SavepointMetadata fromCompletedCheckpoint(CompletedCheckpoint checkpoint) {
        int operatorCount = 0;
        int keyedCount = 0;
        for (TaskStateSnapshot taskState : checkpoint.getTaskStates().values()) {
            if (taskState.getOperatorStates() != null) {
                operatorCount += taskState.getOperatorStates().size();
            }
            if (taskState.getKeyedStates() != null) {
                keyedCount += taskState.getKeyedStates().size();
            }
        }

        return new SavepointMetadata(
                checkpoint.getCheckpointId(),
                checkpoint.getCompletedTimestamp(),
                checkpoint.getJobId(),
                checkpoint.getPipelineId(),
                operatorCount,
                keyedCount
        );
    }

    public long getCheckpointId() {
        return checkpointId;
    }

    public void setCheckpointId(long checkpointId) {
        this.checkpointId = checkpointId;
    }

    public long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(long createTime) {
        this.createTime = createTime;
    }

    public String getJobId() {
        return jobId;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }

    public String getPipelineId() {
        return pipelineId;
    }

    public void setPipelineId(String pipelineId) {
        this.pipelineId = pipelineId;
    }

    public int getOperatorStateCount() {
        return operatorStateCount;
    }

    public void setOperatorStateCount(int operatorStateCount) {
        this.operatorStateCount = operatorStateCount;
    }

    public int getKeyedStateCount() {
        return keyedStateCount;
    }

    public void setKeyedStateCount(int keyedStateCount) {
        this.keyedStateCount = keyedStateCount;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SavepointMetadata that = (SavepointMetadata) o;
        return checkpointId == that.checkpointId
                && createTime == that.createTime
                && operatorStateCount == that.operatorStateCount
                && keyedStateCount == that.keyedStateCount
                && Objects.equals(jobId, that.jobId)
                && Objects.equals(pipelineId, that.pipelineId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(checkpointId, createTime, jobId, pipelineId, operatorStateCount, keyedStateCount);
    }

    @Override
    public String toString() {
        return "SavepointMetadata{" +
                "checkpointId=" + checkpointId +
                ", createTime=" + createTime +
                ", jobId='" + jobId + '\'' +
                ", pipelineId='" + pipelineId + '\'' +
                ", operatorStateCount=" + operatorStateCount +
                ", keyedStateCount=" + keyedStateCount +
                '}';
    }
}
