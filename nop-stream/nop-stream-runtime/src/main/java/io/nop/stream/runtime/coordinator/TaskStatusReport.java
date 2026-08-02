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
 * G52: per-task terminal-state report sent from a {@code TaskManager.RunningTask}
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

    private String jobId;
    private String vertexId;
    private int subtaskIndex;
    private int attemptNumber;
    private TerminalState terminalState;
    private String errorCause;
    private long lastProgressTime;
    private long reportedAt;
    /** Monotonic fencing epoch of the reporting task; coordinator rejects stale-epoch reports. */
    private long fencingEpoch;

    public TaskStatusReport() {
    }

    public TaskStatusReport(String jobId, String vertexId, int subtaskIndex,
                            int attemptNumber, TerminalState terminalState,
                            String errorCause, long lastProgressTime,
                            long fencingEpoch, long reportedAt) {
        this.jobId = jobId;
        this.vertexId = vertexId;
        this.subtaskIndex = subtaskIndex;
        this.attemptNumber = attemptNumber;
        this.terminalState = terminalState;
        this.errorCause = errorCause;
        this.lastProgressTime = lastProgressTime;
        this.fencingEpoch = fencingEpoch;
        this.reportedAt = reportedAt;
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

    public int getAttemptNumber() {
        return attemptNumber;
    }

    public void setAttemptNumber(int attemptNumber) {
        this.attemptNumber = attemptNumber;
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

    public long getFencingEpoch() {
        return fencingEpoch;
    }

    public void setFencingEpoch(long fencingEpoch) {
        this.fencingEpoch = fencingEpoch;
    }

    public long getReportedAt() {
        return reportedAt;
    }

    public void setReportedAt(long reportedAt) {
        this.reportedAt = reportedAt;
    }
}
