/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.execution;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

import io.nop.api.core.annotations.core.Internal;
import io.nop.commons.partition.IPartitioner;

import io.nop.stream.core.execution.flow.EdgeConfig;
import io.nop.stream.core.execution.plan.DeploymentPlan;
import io.nop.stream.core.execution.plan.PartitionPolicy;
import io.nop.stream.core.jobgraph.JobEdge;

/**
 * Shared per-edge plan-assembly helpers used by BOTH plan builders: the local
 * {@link GraphExecutionPlan} (nop-stream-core) and the remote
 * {@code RemoteGraphExecutionPlanBuilder} (nop-stream-runtime).
 *
 * <p>{@link #createWriterForEdge} is the single converged implementation of the
 * "collect one source subtask's partitions for an edge and wrap them in a routed
 * {@link RecordWriter}" assembly that previously existed as three verbatim copies
 * (local single-edge + local fan-out edge + remote single-edge/remote fan-out).
 * The partition-policy resolution is intentionally injected as a parameter: the
 * local and remote builders use DIFFERENT policies (see the DELIBERATE DIVERGENCE
 * notes on the two builders' {@code resolvePartitionPolicy} methods, plan 2278 B1)
 * and each caller passes its own resolver, so behavior is unchanged bit-for-bit.
 *
 * <p>{@link #resolveEdgeConfig} is the single converged implementation of the
 * edge-config lookup (edge-attached config first, then the DeploymentPlan's
 * edgeConfigs map keyed by the RAW {@code "source->target"} form — only the
 * derived topic is sanitized, never this key).
 */
@Internal
public final class EdgeAssembly {

    private EdgeAssembly() {
    }

    /**
     * Creates the RecordWriter one source subtask uses for a single outgoing edge:
     * collects the matrix partitions this source subtask writes to (one per target
     * subtask), resolves the edge's partition policy via the caller-supplied
     * {@code policyResolver}, and builds the router.
     * Returns {@code null} when the edge has no partition matrix or no partitions
     * for this source subtask index — the caller then skips the edge exactly as
     * the pre-convergence local and remote branches did.
     */
    public static RecordWriter<Object> createWriterForEdge(JobEdge edge,
                                                           ResultPartition[][] matrix,
                                                           int taskIndex,
                                                           DeploymentPlan deploymentPlan,
                                                           BiFunction<JobEdge, DeploymentPlan, PartitionPolicy> policyResolver) {
        List<ResultPartition> writerPartitions = new ArrayList<>();
        if (matrix != null) {
            for (int t = 0; t < matrix[taskIndex].length; t++) {
                writerPartitions.add(matrix[taskIndex][t]);
            }
        }

        if (writerPartitions.isEmpty()) {
            return null;
        }

        PartitionPolicy policy = policyResolver.apply(edge, deploymentPlan);
        IPartitioner<?> partitioner = edge.getPartitioner();
        EdgeConfig writerConfig = resolveEdgeConfig(edge, deploymentPlan);
        PartitionRouter router = PartitionRouter.create(
                policy, writerPartitions.size(), partitioner, taskIndex);
        return new RecordWriter<Object>(
                writerPartitions.toArray(new ResultPartition[0]),
                (IPartitioner<Object>) partitioner, writerConfig, router);
    }

    /**
     * Resolves the EdgeConfig for a given JobEdge.
     *
     * <p>First checks if the JobEdge already has an EdgeConfig set (e.g., from JobGraphGenerator).
     * If not, looks up the edge key in the DeploymentPlan's edgeConfigs map.
     * The edge key is formatted as "sourceVertex->targetVertex".
     *
     * @param edge           the JobEdge to resolve config for
     * @param deploymentPlan optional deployment plan containing edge configurations
     * @return the resolved EdgeConfig, or null if none available
     */
    public static EdgeConfig resolveEdgeConfig(JobEdge edge, DeploymentPlan deploymentPlan) {
        // Priority 1: EdgeConfig already set on the JobEdge itself
        if (edge.getEdgeConfig() != null) {
            return edge.getEdgeConfig();
        }
        // Priority 2: Look up in DeploymentPlan's edgeConfigs map
        if (deploymentPlan != null) {
            String edgeKey = edge.getSourceVertex() + "->" + edge.getTargetVertex();
            return deploymentPlan.getEdgeConfigs().get(edgeKey);
        }
        return null;
    }
}
