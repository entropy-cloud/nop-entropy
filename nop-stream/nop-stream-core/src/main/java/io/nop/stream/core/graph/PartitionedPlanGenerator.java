/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.graph;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.nop.stream.core.execution.plan.PartitionedPlan;
import io.nop.stream.core.execution.plan.PartitionPolicy;
import io.nop.stream.core.execution.plan.PartitionPolicyAware;
import io.nop.stream.core.jobgraph.JobEdge;
import io.nop.stream.core.jobgraph.JobGraph;
import io.nop.stream.core.jobgraph.JobVertex;
import io.nop.stream.core.model.StreamModelFingerprint;
import io.nop.stream.core.exceptions.StreamException;

import io.nop.stream.core.exceptions.NopStreamErrors;
import io.nop.stream.core.model.StreamModel;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_ARG_NAME;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_CLASS_NAME;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_NULL_ARG;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_INVALID_STATE;

public class PartitionedPlanGenerator {

    public PartitionedPlan generate(JobGraph jobGraph, StreamModelFingerprint fingerprint) {
        if (jobGraph == null) {
            throw new StreamException(ERR_STREAM_NULL_ARG).param(ARG_ARG_NAME, "jobGraph");
        }

        validateFingerprint(jobGraph, fingerprint);

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

    void validateFingerprint(JobGraph jobGraph, StreamModelFingerprint receivedFingerprint) {
        StreamModel model = jobGraph.getStreamModel();
        if (model == null) {
            return;
        }
        StreamModelFingerprint computedFingerprint = model.computeFingerprint();
        if (!computedFingerprint.isCompatibleWith(receivedFingerprint)) {
            throw new StreamException(ERR_STREAM_INVALID_STATE)
                    .param(ARG_DETAIL, "StreamModel requirements incompatible");
        }
    }

    PartitionPolicy inferPartitionPolicy(JobEdge edge) {
        if (edge.getPartitioner() == null) {
            return PartitionPolicy.FORWARD;
        }
        if (edge.getPartitioner() instanceof PartitionPolicyAware) {
            return ((PartitionPolicyAware) edge.getPartitioner()).getPartitionPolicy();
        }
        // Fail-fast: an unidentified partitioner must not be silently classified by
        // class-name substring matching (the prior behaviour). Historical bug AR-3:
        // class-name matching mis-routed custom partitioners whose class name happened
        // to contain "hash"/"rebalance"/"broadcast" substrings, and silently defaulted
        // everything else to FORWARD — both paths corrupted parallel routing. The
        // partitioner must implement PartitionPolicyAware to declare its policy; if it
        // does not, we throw so the missing declaration is fixed at the source.
        throw new StreamException(ERR_STREAM_INVALID_STATE)
                .param(ARG_DETAIL,
                        "Partitioner on edge " + edge.getSourceVertex() + "->"
                                + edge.getTargetVertex()
                                + " does not implement PartitionPolicyAware. "
                                + "Partition policy cannot be inferred by class-name matching "
                                + "(removed: silent mis-routing bug AR-3). Have the partitioner "
                                + "implement PartitionPolicyAware#getPartitionPolicy() to declare "
                                + "its policy.")
                .param(ARG_CLASS_NAME, edge.getPartitioner().getClass().getName());
    }
}
