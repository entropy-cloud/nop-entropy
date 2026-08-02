/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.cluster;

import java.io.Serializable;

import io.nop.api.core.annotations.data.DataBean;

@DataBean
public class TaskAssignment implements Serializable {

    private static final long serialVersionUID = 1L;

    private String jobId;
    private String vertexId;
    private int subtaskIndex;
    private String nodeId;
    private String attemptId;
    /**
     * 单调 fencing epoch（Stage 39：取代原复合 String fencingToken，统一为 long）。
     * 同时编码 leadership 切换与同 leader 内 recovery，由 {@code JobCoordinator} 派生。
     */
    private long fencingEpoch;
    private long assignedAt;

    /**
     * Monotonically increasing attempt number per (jobId, vertexId, subtaskIndex).
     * Driven by {@code JobCoordinator} on every (re)assignment so that the
     * {@link ClusterRegistry} can preserve full attempt history (G56).
     * First attempt = 1; increments on each global recovery.
     */
    private int attemptNumber;

    public TaskAssignment() {
    }

    public TaskAssignment(String jobId, String vertexId, int subtaskIndex, String nodeId,
                          String attemptId, long fencingEpoch, long assignedAt) {
        this(jobId, vertexId, subtaskIndex, nodeId, attemptId, fencingEpoch, assignedAt, 1);
    }

    public TaskAssignment(String jobId, String vertexId, int subtaskIndex, String nodeId,
                          String attemptId, long fencingEpoch, long assignedAt,
                          int attemptNumber) {
        this.jobId = jobId;
        this.vertexId = vertexId;
        this.subtaskIndex = subtaskIndex;
        this.nodeId = nodeId;
        this.attemptId = attemptId;
        this.fencingEpoch = fencingEpoch;
        this.assignedAt = assignedAt;
        this.attemptNumber = attemptNumber;
    }

    public String getJobId() {
        return jobId;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }

    public String getVertexId() {
        return vertexId;
    }

    public void setVertexId(String vertexId) {
        this.vertexId = vertexId;
    }

    public int getSubtaskIndex() {
        return subtaskIndex;
    }

    public void setSubtaskIndex(int subtaskIndex) {
        this.subtaskIndex = subtaskIndex;
    }

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public String getAttemptId() {
        return attemptId;
    }

    public void setAttemptId(String attemptId) {
        this.attemptId = attemptId;
    }

    public long getFencingEpoch() {
        return fencingEpoch;
    }

    public void setFencingEpoch(long fencingEpoch) {
        this.fencingEpoch = fencingEpoch;
    }

    public long getAssignedAt() {
        return assignedAt;
    }

    public void setAssignedAt(long assignedAt) {
        this.assignedAt = assignedAt;
    }

    public int getAttemptNumber() {
        return attemptNumber;
    }

    public void setAttemptNumber(int attemptNumber) {
        this.attemptNumber = attemptNumber;
    }
}
