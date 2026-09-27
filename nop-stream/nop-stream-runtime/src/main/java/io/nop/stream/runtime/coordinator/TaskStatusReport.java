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
import io.nop.stream.runtime.cluster.TaskIdentity;

/**
 * G52: per-task terminal-state report sent from a {@code RunningTask}
 * to the {@code JobCoordinator} via {@code IStreamCoordinatorRpcService.reportTaskStatus}.
 *
 * <p>Carries enough context for the coordinator to update per-subtask liveness and
 * trigger recovery when a task reaches a terminal state (COMPLETED or FAILED) while
 * its host node is still alive — the gap that node-level lease detection cannot
 * close on its own.
 */
@DataBean
public class TaskStatusReport implements Serializable {

    private static final long serialVersionUID = 1L;

    public enum TerminalState {
        COMPLETED,
        FAILED
    }

    /**
     * Shared identity tuple. Private and NOT a bean property: the flat
     * getters/setters below delegate to it so the @DataBean/JSON flat shape is
     * unchanged (plan 2278 Phase 2, see {@link TaskIdentity}).
     */
    private TaskIdentity identity = new TaskIdentity();
    private TerminalState terminalState;
    private String errorCause;
    private long lastProgressTime;
    private long reportedAt;

    public TaskStatusReport() {
    }

    public TaskStatusReport(String jobId, String vertexId, int subtaskIndex,
                            int attemptNumber, TerminalState terminalState,
                            String errorCause, long lastProgressTime,
                            long fencingEpoch, long reportedAt) {
        this.terminalState = terminalState;
        this.errorCause = errorCause;
        this.lastProgressTime = lastProgressTime;
        this.reportedAt = reportedAt;
        this.identity.setJobId(jobId);
        this.identity.setVertexId(vertexId);
        this.identity.setSubtaskIndex(subtaskIndex);
        this.identity.setAttemptNumber(attemptNumber);
        this.identity.setFencingEpoch(fencingEpoch);
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

    public int getAttemptNumber() {
        return identity.getAttemptNumber();
    }

    public void setAttemptNumber(int attemptNumber) {
        identity.setAttemptNumber(attemptNumber);
    }

    public TerminalState getTerminalState() {
        return terminalState;
    }

    public void setTerminalState(TerminalState terminalState) {
        this.terminalState = terminalState;
    }

    public String getErrorCause() {
        return errorCause;
    }

    public void setErrorCause(String errorCause) {
        this.errorCause = errorCause;
    }

    public long getLastProgressTime() {
        return lastProgressTime;
    }

    public void setLastProgressTime(long lastProgressTime) {
        this.lastProgressTime = lastProgressTime;
    }

    /** Monotonic fencing epoch of the reporting task; coordinator rejects stale-epoch reports. */
    public long getFencingEpoch() {
        return identity.getFencingEpoch();
    }

    public void setFencingEpoch(long fencingEpoch) {
        identity.setFencingEpoch(fencingEpoch);
    }

    public long getReportedAt() {
        return reportedAt;
    }

    public void setReportedAt(long reportedAt) {
        this.reportedAt = reportedAt;
    }
}
