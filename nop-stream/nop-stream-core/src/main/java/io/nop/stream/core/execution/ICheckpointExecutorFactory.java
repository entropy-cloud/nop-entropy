/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.execution;

import io.nop.api.core.annotations.core.Internal;

import io.nop.stream.core.checkpoint.CheckpointConfig;
import io.nop.stream.core.environment.StreamExecutionResult;
import io.nop.stream.core.execution.plan.DeploymentPlan;
import io.nop.stream.core.execution.plan.PartitionedPlan;
import io.nop.stream.core.jobgraph.JobGraph;
import io.nop.stream.core.model.StreamModel;

/**
 * Factory interface for creating checkpoint-aware execution.
 *
 * <p>Since the core module cannot depend on the runtime module, this factory
 * allows the runtime module to register a checkpoint executor that the core
 * module can delegate to when checkpointing is enabled.
 */
@Internal
public interface ICheckpointExecutorFactory {

    StreamExecutionResult executeWithCheckpoint(
            JobGraph jobGraph,
            String jobName,
            CheckpointConfig checkpointConfig) throws Exception;

    /**
     * Triggers a savepoint for a running or completed job identified by the given job graph.
     * The savepoint is persisted via the configured {@link io.nop.stream.core.checkpoint.storage.ICheckpointStorage}.
     *
     * @param jobGraph       the job graph describing the pipeline
     * @param checkpointConfig checkpoint configuration including storage path
     * @param targetPath     hint for where to store the savepoint (may be used by storage impl)
     * @return the path where the savepoint was stored
     * @throws Exception if the savepoint could not be triggered or persisted
     */
    String triggerSavepoint(JobGraph jobGraph,
                            CheckpointConfig checkpointConfig,
                            String targetPath) throws Exception;

    /**
     * Executes a job graph, restoring operator states from a previously taken savepoint.
     *
     * @param jobGraph       the job graph describing the pipeline
     * @param jobName        human-readable job name
     * @param checkpointConfig checkpoint configuration
     * @param savepointPath  path to the savepoint to restore from (as returned by {@link #triggerSavepoint})
     * @return the execution result
     * @throws Exception if execution or state restoration fails
     */
    StreamExecutionResult executeWithSavepoint(JobGraph jobGraph,
                                               String jobName,
                                               CheckpointConfig checkpointConfig,
                                               String savepointPath) throws Exception;

    /**
     * Executes a streaming job with checkpoint support, using the provided
     * PartitionedPlan and DeploymentPlan for execution planning.
     *
     * <p>Default implementation delegates to the simpler
     * {@link #executeWithCheckpoint(JobGraph, String, CheckpointConfig)} to maintain
     * backward compatibility.
     *
     * @param streamModel     the stream model describing the pipeline topology
     * @param partitionedPlan the partitioned execution plan
     * @param deploymentPlan  the deployment plan for local execution
     * @return the execution result
     * @throws Exception if execution fails
     */
    default StreamExecutionResult executeWithCheckpoint(
            StreamModel streamModel,
            PartitionedPlan partitionedPlan,
            DeploymentPlan deploymentPlan) throws Exception {
        throw new UnsupportedOperationException(
                "executeWithCheckpoint(StreamModel, PartitionedPlan, DeploymentPlan) not implemented");
    }
}
