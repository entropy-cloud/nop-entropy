/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.execution;

import io.nop.api.core.annotations.core.Internal;
import io.nop.stream.core.checkpoint.TaskLocation;
import io.nop.stream.core.jobgraph.Invokable;
import io.nop.stream.core.jobgraph.JobVertex;
import io.nop.stream.core.jobgraph.OperatorChain;

/**
 * Represents a single parallel subtask instance of a {@link JobVertex}.
 *
 * <p>When a vertex has parallelism N, there are N subtasks indexed 0..N-1,
 * each with its own {@link StreamTaskInvokable}, {@link RecordWriter}, and {@link InputGate}.
 *
 * <p>The subtask's identity within the job is captured by its {@link TaskLocation}.
 */
@Internal
public class Subtask {

    private final String vertexId;
    private final int taskIndex;
    private final TaskLocation taskLocation;
    private final StreamTaskInvokable invokable;

    public Subtask(String vertexId, int taskIndex, TaskLocation taskLocation,
                   StreamTaskInvokable invokable) {
        this.vertexId = vertexId;
        this.taskIndex = taskIndex;
        this.taskLocation = taskLocation;
        this.invokable = invokable;
    }

    public String getVertexId() {
        return vertexId;
    }

    public int getTaskIndex() {
        return taskIndex;
    }

    public TaskLocation getTaskLocation() {
        return taskLocation;
    }

    public StreamTaskInvokable getInvokable() {
        return invokable;
    }

    @Override
    public String toString() {
        return "Subtask{vertexId='" + vertexId + "', taskIndex=" + taskIndex + '}';
    }
}
