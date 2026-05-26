/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.execution;

import java.util.LinkedHashMap;
import java.util.Map;

import io.nop.stream.core.execution.flow.EdgeConfig;
import io.nop.stream.core.execution.flow.FlowControlPolicy;
import io.nop.stream.core.execution.flow.MemoryBudget;
import io.nop.stream.core.execution.plan.DeploymentPlan;
import io.nop.stream.core.execution.plan.PartitionedPlan;
import io.nop.stream.core.exceptions.StreamException;

public class DeploymentPlanGenerator {

    public DeploymentPlan generateLocal(PartitionedPlan partitionedPlan) {
        if (partitionedPlan == null) {
            throw new StreamException("PartitionedPlan must not be null");
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
}
