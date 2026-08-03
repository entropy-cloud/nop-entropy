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
import io.nop.stream.core.jobgraph.region.RegionId;

/**
 * Represents a single parallel subtask instance of a {@link JobVertex}.
 *
 * <p>When a vertex has parallelism N, there are N subtasks indexed 0..N-1,
 * each with its own {@link StreamTaskInvokable}, {@link RecordWriter}, and {@link InputGate}.
 *
 * <p>The subtask's identity within the job is captured by its {@link TaskLocation},
 * and its failure-domain membership is captured by its {@link RegionId}
 * (Stage 44 successor plan 2: region identification).
 */
@Internal
public class Subtask {

    private final String vertexId;
    private final int taskIndex;
    private final TaskLocation taskLocation;
    private final StreamTaskInvokable invokable;
    /**
     * The region this subtask belongs to (Stage 44 successor plan 2). May be
     * {@code null} for subtasks built via
     * {@link GraphExecutionPlan#create(java.util.List, java.util.Map, java.util.Map, java.util.Map)}
     * by runtime builders that do not have region information; for subtasks
     * built via {@link GraphExecutionPlan#build} the region ID is always
     * populated from the job graph decomposition.
     */
    private final RegionId regionId;

    public Subtask(String vertexId, int taskIndex, TaskLocation taskLocation,
                   StreamTaskInvokable invokable) {
        this(vertexId, taskIndex, taskLocation, invokable, null);
    }

    /**
     * Constructs a subtask with an explicit region ID (Stage 44 successor
     * plan 2).
     *
     * @param vertexId     the owning vertex ID
     * @param taskIndex    the parallel subtask index (0-based)
     * @param taskLocation the task location (job/pipeline/vertex/index identity)
     * @param invokable    the invokable that runs this subtask's operator chain
     * @param regionId     the region this subtask belongs to (may be null when
     *                     the caller has no region information, e.g. runtime
     *                     builders using {@link GraphExecutionPlan#create})
     */
    public Subtask(String vertexId, int taskIndex, TaskLocation taskLocation,
                   StreamTaskInvokable invokable, RegionId regionId) {
        this.vertexId = vertexId;
        this.taskIndex = taskIndex;
        this.taskLocation = taskLocation;
        this.invokable = invokable;
        this.regionId = regionId;
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

    /**
     * Returns the region this subtask belongs to (Stage 44 successor plan 2).
     *
     * @return the region ID, or {@code null} if the subtask was built without
     *         region information
     */
    public RegionId getRegionId() {
        return regionId;
    }

    @Override
    public String toString() {
        return "Subtask{vertexId='" + vertexId + "', taskIndex=" + taskIndex
                + (regionId != null ? ", region=" + regionId : "") + '}';
    }
}
