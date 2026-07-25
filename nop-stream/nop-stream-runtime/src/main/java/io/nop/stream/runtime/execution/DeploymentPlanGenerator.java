/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.execution;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.nop.stream.core.execution.flow.EdgeConfig;
import io.nop.stream.core.execution.flow.FlowControlPolicy;
import io.nop.stream.core.execution.flow.MemoryBudget;
import io.nop.stream.core.execution.plan.DeploymentAssignment;
import io.nop.stream.core.execution.plan.DeploymentPlan;
import io.nop.stream.core.execution.plan.PartitionedPlan;
import io.nop.stream.core.exceptions.StreamException;

import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_ARG_NAME;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_INVALID_STATE;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_NULL_ARG;

public class DeploymentPlanGenerator {

    public DeploymentPlan generateLocal(PartitionedPlan partitionedPlan) {
        if (partitionedPlan == null) {
            throw new StreamException(ERR_STREAM_NULL_ARG).param(ARG_ARG_NAME, "partitionedPlan");
        }

        Map<String, EdgeConfig> edgeConfigs = buildEdgeConfigs(partitionedPlan);

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

    /**
     * Generates a distributed DeploymentPlan with a materialized round-robin
     * subtask→node assignment.
     *
     * @param partitionedPlan the partitioned execution plan
     * @param activeNodeIds   the ordered list of active node identifiers
     * @return a DeploymentPlan whose {@link DeploymentPlan#getAssignment()} carries the mapping
     * @throws StreamException if {@code partitionedPlan} is null or {@code activeNodeIds} is empty
     */
    public DeploymentPlan generateDistributed(PartitionedPlan partitionedPlan, List<String> activeNodeIds) {
        if (partitionedPlan == null) {
            throw new StreamException(ERR_STREAM_NULL_ARG).param(ARG_ARG_NAME, "partitionedPlan");
        }
        if (activeNodeIds == null || activeNodeIds.isEmpty()) {
            throw new StreamException(ERR_STREAM_INVALID_STATE).param(ARG_DETAIL,
                    "Cannot generate distributed DeploymentPlan: no active nodes available. "
                            + "At least one node must be registered before distributed plan generation.");
        }

        Map<String, EdgeConfig> edgeConfigs = buildEdgeConfigs(partitionedPlan);

        DeploymentAssignment assignment = buildRoundRobinAssignment(partitionedPlan, activeNodeIds);

        return new DeploymentPlan(
                partitionedPlan.getJobId(),
                partitionedPlan.getPipelineId(),
                partitionedPlan,
                "remote",
                "memory",
                "local",
                edgeConfigs,
                MemoryBudget.defaultLocalBudget(64 * 1024 * 1024),
                assignment);
    }

    private Map<String, EdgeConfig> buildEdgeConfigs(PartitionedPlan partitionedPlan) {
        Map<String, EdgeConfig> edgeConfigs = new LinkedHashMap<>();
        for (PartitionedPlan.EdgePlan edgePlan : partitionedPlan.getEdgePlans()) {
            String edgeKey = edgePlan.getSourceVertexId() + "->" + edgePlan.getTargetVertexId();
            edgeConfigs.put(edgeKey, EdgeConfig.defaultConfig());
        }
        return edgeConfigs;
    }

    /**
     * Round-robin assignment: iterates over all vertices and all subtasks, distributing
     * them across the active node set using a global counter. This matches the legacy
     * round-robin semantics previously in {@code JobCoordinator.assignTasks()}.
     */
    static DeploymentAssignment buildRoundRobinAssignment(PartitionedPlan partitionedPlan,
                                                          List<String> activeNodeIds) {
        Map<String, List<String>> vertexAssignments = new LinkedHashMap<>();
        int globalIndex = 0;

        for (Map.Entry<String, PartitionedPlan.VertexPlan> entry :
                partitionedPlan.getVertexPlans().entrySet()) {
            String vertexId = entry.getKey();
            int parallelism = entry.getValue().getParallelism();

            List<String> nodesForVertex = new ArrayList<>(parallelism);
            for (int subtaskIndex = 0; subtaskIndex < parallelism; subtaskIndex++) {
                nodesForVertex.add(activeNodeIds.get(globalIndex % activeNodeIds.size()));
                globalIndex++;
            }
            vertexAssignments.put(vertexId, nodesForVertex);
        }

        return new DeploymentAssignment(vertexAssignments);
    }
}
