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

    /**
     * Shared identity tuple. Private and NOT a bean property: the flat
     * getters/setters below delegate to it so the @DataBean/JSON flat shape is
     * unchanged (plan 2278 Phase 2, see {@link TaskIdentity}).
     */
    private TaskIdentity identity = new TaskIdentity();
    private String nodeId;
    private String attemptId;
    private long assignedAt;

    public TaskAssignment() {
    }

    public TaskAssignment(String jobId, String vertexId, int subtaskIndex, String nodeId,
                          String attemptId, long fencingEpoch, long assignedAt) {
        this(jobId, vertexId, subtaskIndex, nodeId, attemptId, fencingEpoch, assignedAt, 1);
    }

    public TaskAssignment(String jobId, String vertexId, int subtaskIndex, String nodeId,
                          String attemptId, long fencingEpoch, long assignedAt,
                          int attemptNumber) {
        this.nodeId = nodeId;
        this.attemptId = attemptId;
        this.assignedAt = assignedAt;
        this.identity.setJobId(jobId);
        this.identity.setVertexId(vertexId);
        this.identity.setSubtaskIndex(subtaskIndex);
        this.identity.setFencingEpoch(fencingEpoch);
        this.identity.setAttemptNumber(attemptNumber);
    }

    public String getJobId() {
        return identity.getJobId();
    }

    public void setJobId(String jobId) {
        identity.setJobId(jobId);
    }

    public String getVertexId() {
        return identity.getVertexId();
    }

    public void setVertexId(String vertexId) {
        identity.setVertexId(vertexId);
    }

    public int getSubtaskIndex() {
        return identity.getSubtaskIndex();
    }

    public void setSubtaskIndex(int subtaskIndex) {
        identity.setSubtaskIndex(subtaskIndex);
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

    /**
     * 单调 fencing epoch（Stage 39：取代原复合 String fencingToken，统一为 long）。
     * 同时编码 leadership 切换与同 leader 内 recovery，由 {@code JobCoordinator} 派生。
     */
    public long getFencingEpoch() {
        return identity.getFencingEpoch();
    }

    public void setFencingEpoch(long fencingEpoch) {
        identity.setFencingEpoch(fencingEpoch);
    }

    public long getAssignedAt() {
        return assignedAt;
    }

    public void setAssignedAt(long assignedAt) {
        this.assignedAt = assignedAt;
    }

    /**
     * Monotonically increasing attempt number per (jobId, vertexId, subtaskIndex).
     * Driven by {@code JobCoordinator} on every (re)assignment so that the
     * {@link ClusterRegistry} can preserve full attempt history (G56).
     * First attempt = 1; increments on each global recovery.
     */
    public int getAttemptNumber() {
        return identity.getAttemptNumber();
    }

    public void setAttemptNumber(int attemptNumber) {
        identity.setAttemptNumber(attemptNumber);
    }
}
