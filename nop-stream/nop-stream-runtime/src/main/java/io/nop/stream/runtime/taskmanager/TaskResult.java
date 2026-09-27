/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.taskmanager;

/**
 * Terminal outcome of a completed task, retained by the TaskManager's bounded
 * completed-tasks map for coordinator-side result inspection.
 */
public class TaskResult {
    private final String jobId;
    private final String vertexId;
    private final int subtaskIndex;
    private final boolean success;
    private final boolean canceled;
    private final Throwable error;

    public TaskResult(String jobId, String vertexId, int subtaskIndex,
                      boolean success, boolean canceled, Throwable error) {
        this.jobId = jobId;
        this.vertexId = vertexId;
        this.subtaskIndex = subtaskIndex;
        this.success = success;
        this.canceled = canceled;
        this.error = error;
    }

    public String getJobId() { return jobId; }
    public String getVertexId() { return vertexId; }
    public int getSubtaskIndex() { return subtaskIndex; }
    public boolean isSuccess() { return success; }
    public boolean isCanceled() { return canceled; }
    public Throwable getError() { return error; }
}
