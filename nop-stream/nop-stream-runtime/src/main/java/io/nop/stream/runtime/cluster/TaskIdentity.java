/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.cluster;

import java.io.Serializable;

/**
 * Shared identity tuple (jobId, vertexId, subtaskIndex, attemptNumber,
 * fencingEpoch) embedded in the three cluster DTOs that previously hand-wrote
 * the same five fields + accessors: {@link TaskAssignment},
 * {@code coordinator.TaskStatusReport} and {@code rpc.TaskDeploymentDescriptor}
 * (plan 2278 Phase 2 convergence).
 *
 * <p>Embedding contract: the owning DTOs keep the field PRIVATE and expose NO
 * getter/setter for the embedded object itself — every historical flat
 * getter/setter on the DTOs delegates to this object instead, so the
 * {@code @DataBean}/JSON flat shape and the RPC property surface are unchanged.
 * Java-serialization compatibility is guaranteed for same-version peers (the
 * embedding is part of the descriptor stream); cross-version streams are not a
 * supported evolution path for these DTOs.
 */
public class TaskIdentity implements Serializable {

    private static final long serialVersionUID = 1L;

    private String jobId;
    private String vertexId;
    private int subtaskIndex;
    private int attemptNumber;
    private long fencingEpoch;

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

    public int getAttemptNumber() {
        return attemptNumber;
    }

    public void setAttemptNumber(int attemptNumber) {
        this.attemptNumber = attemptNumber;
    }

    public long getFencingEpoch() {
        return fencingEpoch;
    }

    public void setFencingEpoch(long fencingEpoch) {
        this.fencingEpoch = fencingEpoch;
    }
}
