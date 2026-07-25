/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.execution;

import java.util.List;

import io.nop.stream.core.environment.StreamExecutionResult;
import io.nop.stream.core.execution.plan.DeploymentPlan;
import io.nop.stream.core.execution.plan.PartitionedPlan;
import io.nop.stream.core.jobgraph.JobGraph;

public interface IStreamExecutionDispatcher {

    boolean supportsDeploymentMode(DeploymentMode mode);

    /**
     * Returns the ordered list of node identifiers that this dispatcher will bring
     * online for the given partitioned plan.
     *
     * <p>Used by {@code StreamExecutionEnvironment.generateDeploymentPlan()} to
     * materialize a physical subtask→node assignment into the
     * {@link DeploymentPlan} before execution begins.
     *
     * @param partitionedPlan the partitioned execution plan
     * @return ordered list of expected node identifiers (never empty)
     */
    List<String> getExpectedNodeIds(PartitionedPlan partitionedPlan);

    StreamExecutionResult execute(JobGraph jobGraph, PartitionedPlan partitionedPlan,
                                  DeploymentPlan deploymentPlan) throws Exception;
}
