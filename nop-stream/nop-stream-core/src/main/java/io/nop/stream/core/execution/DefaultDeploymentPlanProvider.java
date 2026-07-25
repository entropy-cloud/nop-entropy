/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.execution;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.nop.api.core.annotations.core.Internal;

import io.nop.stream.core.execution.flow.EdgeConfig;
import io.nop.stream.core.execution.flow.MemoryBudget;
import io.nop.stream.core.execution.plan.DeploymentPlan;
import io.nop.stream.core.execution.plan.PartitionedPlan;
import io.nop.stream.core.exceptions.StreamException;

import io.nop.stream.core.exceptions.NopStreamErrors;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_ARG_NAME;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_NULL_ARG;

/**
 * Default fallback implementation of {@link IDeploymentPlanProvider}
 * used when no runtime module is on the classpath.
 *
 * <p>Creates a minimal DeploymentPlan with default edge configs
 * and a local memory budget. Distributed generation is not supported
 * because the core module has no access to a cluster registry.
 */
@Internal
class DefaultDeploymentPlanProvider implements IDeploymentPlanProvider {

    static final DefaultDeploymentPlanProvider INSTANCE = new DefaultDeploymentPlanProvider();

    @Override
    public DeploymentPlan generateLocal(PartitionedPlan partitionedPlan) {
        if (partitionedPlan == null) {
            throw new StreamException(ERR_STREAM_NULL_ARG).param(ARG_ARG_NAME, "partitionedPlan");
        }

        Map<String, EdgeConfig> edgeConfigs = new LinkedHashMap<>();
        for (PartitionedPlan.EdgePlan edgePlan : partitionedPlan.getEdgePlans()) {
            String edgeKey = edgePlan.getSourceVertexId() + "->" + edgePlan.getTargetVertexId();
            edgeConfigs.put(edgeKey, EdgeConfig.defaultConfig());
        }

        return new DeploymentPlan(
                partitionedPlan.getJobId(),
                partitionedPlan.getPipelineId(),
                partitionedPlan,
                "local",
                "memory",
                "local",
                edgeConfigs,
                MemoryBudget.defaultLocalBudget(64 * 1024 * 1024));
    }

    @Override
    public DeploymentPlan generateDistributed(PartitionedPlan partitionedPlan, List<String> activeNodeIds) {
        throw new StreamException(NopStreamErrors.ERR_STREAM_UNSUPPORTED)
                .param(NopStreamErrors.ARG_OPERATION,
                        "DefaultDeploymentPlanProvider.generateDistributed"
                                + " — runtime module is not on the classpath");
    }
}
