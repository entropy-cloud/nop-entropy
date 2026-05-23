/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.coordinator;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.stream.runtime.cluster.TaskAssignment;

import java.io.Serializable;

/**
 * Control message sent from JobCoordinator to a specific TaskManager
 * to instruct it to start running a task.
 */
@DataBean
public class TaskAssignmentMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    private TaskAssignment assignment;
    private String targetNodeId;

    public TaskAssignmentMessage() {
    }

    public TaskAssignmentMessage(TaskAssignment assignment, String targetNodeId) {
        this.assignment = assignment;
        this.targetNodeId = targetNodeId;
    }

    public TaskAssignment getAssignment() {
        return assignment;
    }

    public void setAssignment(TaskAssignment assignment) {
        this.assignment = assignment;
    }

    public String getTargetNodeId() {
        return targetNodeId;
    }

    public void setTargetNodeId(String targetNodeId) {
        this.targetNodeId = targetNodeId;
    }
}
