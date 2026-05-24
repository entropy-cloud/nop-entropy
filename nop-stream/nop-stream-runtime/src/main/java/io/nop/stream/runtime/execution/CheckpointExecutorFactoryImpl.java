/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.execution;

import io.nop.api.core.annotations.core.Internal;
import io.nop.stream.core.checkpoint.CheckpointConfig;
import io.nop.stream.core.environment.StreamExecutionResult;
import io.nop.stream.core.execution.ICheckpointExecutorFactory;
import io.nop.stream.core.execution.plan.DeploymentPlan;
import io.nop.stream.core.execution.plan.PartitionedPlan;
import io.nop.stream.core.jobgraph.JobGraph;
import io.nop.stream.core.model.StreamModel;

/**
 * Implementation of {@link ICheckpointExecutorFactory} that delegates to
 * {@link GraphModelCheckpointExecutor}.
 */
@Internal
public class CheckpointExecutorFactoryImpl implements ICheckpointExecutorFactory {

    @Override
    public StreamExecutionResult executeWithCheckpoint(
            JobGraph jobGraph,
            String jobName,
            CheckpointConfig checkpointConfig) throws Exception {
        return GraphModelCheckpointExecutor.executeWithCheckpoint(jobGraph, jobName, checkpointConfig);
    }

    @Override
    public String triggerSavepoint(JobGraph jobGraph,
                                   CheckpointConfig checkpointConfig,
                                   String targetPath) throws Exception {
        return GraphModelCheckpointExecutor.triggerSavepoint(jobGraph, checkpointConfig, targetPath);
    }

    @Override
    public StreamExecutionResult executeWithSavepoint(JobGraph jobGraph,
                                                       String jobName,
                                                       CheckpointConfig checkpointConfig,
                                                       String savepointPath) throws Exception {
        return GraphModelCheckpointExecutor.executeWithSavepoint(jobGraph, jobName, checkpointConfig, savepointPath);
    }

    @Override
    public StreamExecutionResult executeWithCheckpoint(
            StreamModel streamModel,
            PartitionedPlan partitionedPlan,
            DeploymentPlan deploymentPlan) throws Exception {
        return GraphModelCheckpointExecutor.executeWithCheckpoint(
                streamModel, partitionedPlan, deploymentPlan);
    }
}
