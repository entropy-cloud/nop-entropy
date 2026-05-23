/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.graph;

import io.nop.stream.core.execution.plan.PartitionPolicy;
import io.nop.stream.core.execution.plan.PartitionedPlan;
import io.nop.stream.core.jobgraph.JobEdge;
import io.nop.stream.core.jobgraph.JobGraph;
import io.nop.stream.core.jobgraph.JobVertex;
import io.nop.stream.core.model.StreamModelFingerprint;

import java.util.*;

public class PartitionedPlanGenerator {

    public PartitionedPlan generate(JobGraph jobGraph, StreamModelFingerprint fingerprint) {
        if (jobGraph == null) {
            throw new IllegalArgumentException("JobGraph must not be null");
        }

        Map<String, PartitionedPlan.VertexPlan> vertexPlans = new LinkedHashMap<>();
        List<PartitionedPlan.EdgePlan> edgePlans = new ArrayList<>();
        Set<String> checkpointAckSet = new LinkedHashSet<>();

        for (Map.Entry<String, JobVertex> entry : jobGraph.getVertices().entrySet()) {
            String vertexId = entry.getKey();
            JobVertex vertex = entry.getValue();
            String operatorId = vertex.getName() != null ? vertex.getName() : vertexId;

            vertexPlans.put(vertexId, new PartitionedPlan.VertexPlan(
                    vertexId, vertex.getParallelism(), operatorId));
            checkpointAckSet.add(vertexId);
        }

        for (JobEdge edge : jobGraph.getEdges()) {
            PartitionPolicy policy = inferPartitionPolicy(edge);
            edgePlans.add(new PartitionedPlan.EdgePlan(
                    edge.getSourceVertex(), edge.getTargetVertex(), policy));
        }

        return new PartitionedPlan(
                jobGraph.getJobName() != null ? jobGraph.getJobName() : "local-job",
                "pipeline-0",
                vertexPlans,
                edgePlans,
                checkpointAckSet,
                fingerprint);
    }

    PartitionPolicy inferPartitionPolicy(JobEdge edge) {
        if (edge.getPartitioner() == null) {
            return PartitionPolicy.FORWARD;
        }
        String partitionerName = edge.getPartitioner().getClass().getSimpleName().toLowerCase();
        if (partitionerName.contains("hash")) {
            return PartitionPolicy.HASH;
        } else if (partitionerName.contains("rebalance")) {
            return PartitionPolicy.REBALANCE;
        } else if (partitionerName.contains("broadcast")) {
            return PartitionPolicy.BROADCAST;
        }
        return PartitionPolicy.FORWARD;
    }
}
