/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.coordinator;

import java.io.Serializable;

import io.nop.api.core.annotations.data.DataBean;

/**
 * G52: per-task liveness progress piggybacked on the existing
 * {@code TaskManager.heartbeat()} via
 * {@code IStreamCoordinatorRpcService.reportNodeTaskLiveness}.
 *
 * <p>The {@code lastProgressTime} field is the heartbeat-of-heartbeats: a
 * monotonic timestamp updated by the invokable on every data-plane progress
 * (record emission for SOURCE/SELF_CONTAINED, input-gate iteration for
 * MIDDLE/SINK). The coordinator compares it against the configurable
 * {@code taskTimeout} to detect per-task stalls independent of node lease.
 */
@DataBean
public class TaskProgress implements Serializable {

    private static final long serialVersionUID = 1L;

    private String vertexId;
    private int subtaskIndex;
    private int attemptNumber;
    private long lastProgressTime;

    public TaskProgress() {
    }

    public TaskProgress(String vertexId, int subtaskIndex, int attemptNumber, long lastProgressTime) {
        this.vertexId = vertexId;
        this.subtaskIndex = subtaskIndex;
        this.attemptNumber = attemptNumber;
        this.lastProgressTime = lastProgressTime;
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

    public long getLastProgressTime() {
        return lastProgressTime;
    }

    public void setLastProgressTime(long lastProgressTime) {
        this.lastProgressTime = lastProgressTime;
    }
}
