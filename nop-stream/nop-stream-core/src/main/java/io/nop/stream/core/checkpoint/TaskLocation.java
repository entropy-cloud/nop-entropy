/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.checkpoint;

import java.io.Serializable;
import java.util.Objects;

import io.nop.api.core.annotations.data.DataBean;

@DataBean
public class TaskLocation implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String jobId;
    private final String pipelineId;
    private final String vertexId;
    private final int taskIndex;

    public TaskLocation(String jobId, String pipelineId, String vertexId, int taskIndex) {
        this.jobId = jobId;
        this.pipelineId = pipelineId;
        this.vertexId = vertexId;
        this.taskIndex = taskIndex;
    }

    public TaskLocation() {
        this("", "", "", 0);
    }

    public String getJobId() { return jobId; }
    public String getPipelineId() { return pipelineId; }
    public String getVertexId() { return vertexId; }
    public int getTaskIndex() { return taskIndex; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TaskLocation that = (TaskLocation) o;
        return taskIndex == that.taskIndex &&
                Objects.equals(jobId, that.jobId) &&
                Objects.equals(pipelineId, that.pipelineId) &&
                Objects.equals(vertexId, that.vertexId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(jobId, pipelineId, vertexId, taskIndex);
    }

    @Override
    public String toString() {
        return "TaskLocation{" +
                "jobId='" + jobId + '\'' +
                ", pipelineId='" + pipelineId + '\'' +
                ", vertexId='" + vertexId + '\'' +
                ", taskIndex=" + taskIndex +
                '}';
    }
}
