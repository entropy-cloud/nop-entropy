/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.execution;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import io.nop.api.core.annotations.core.Internal;
import io.nop.commons.partition.IPartitioner;

import io.nop.stream.core.checkpoint.TaskLocation;
import io.nop.stream.core.execution.flow.EdgeConfig;
import io.nop.stream.core.execution.plan.DeploymentPlan;
import io.nop.stream.core.execution.plan.PartitionedPlan;
import io.nop.stream.core.execution.plan.PartitionPolicy;
import io.nop.stream.core.jobgraph.JobEdge;
import io.nop.stream.core.jobgraph.JobGraph;
import io.nop.stream.core.jobgraph.JobVertex;
import io.nop.stream.core.jobgraph.OperatorChain;

/**
 * Builds an execution plan from a JobGraph, creating data exchange channels
 * (ResultPartition, InputChannel) for inter-task communication.
 *
 * <p>For each vertex with parallelism N, N {@link Subtask} instances are created.
 * For each JobEdge, a matrix of ResultPartitions is created:
 * sourceParallelism × targetParallelition partitions. Each source subtask's
 * RecordWriter holds the targetParallelism partitions for that edge. Each target
 * subtask's InputGate receives from all sourceParallelism partitions.
 *
 * <p>Partition routing is determined by the edge's {@link PartitionPolicy}:
 * <ul>
 *   <li>FORWARD: 1:1 mapping, source subtask i → target subtask i (modulo target parallelism)</li>
 *   <li>HASH: key-based routing via {@link io.nop.stream.core.common.state.shard.StateShard#stableHash}</li>
 *   <li>REBALANCE: round-robin across all target subtasks</li>
 * </ul>
 *
 * <p>Vertices are sorted in topological order (sources first, sinks last)
 * so that tasks can be submitted in dependency order.
 *
 * <p>This class is used by both the non-checkpoint execution path
 * ({@code StreamExecutionEnvironment}) and the checkpoint execution path
 * ({@code GraphModelCheckpointExecutor}).
 */
@Internal
public class GraphExecutionPlan {

    private final List<String> sortedVertexIds;
    private final Map<String, JobVertex> executionVertices;
    private final Map<String, StreamTaskInvokable> invokables;

    /**
     * Subtasks indexed by vertexId then taskIndex.
     * When parallelism=1 (legacy mode), each vertex has exactly one subtask at index 0.
     */
    private final Map<String, List<Subtask>> subtasks;

    private GraphExecutionPlan(List<String> sortedVertexIds,
                                Map<String, JobVertex> executionVertices,
                                Map<String, StreamTaskInvokable> invokables,
                                Map<String, List<Subtask>> subtasks) {
        this.sortedVertexIds = sortedVertexIds;
        this.executionVertices = executionVertices;
        this.invokables = invokables;
        this.subtasks = subtasks;
    }

    /**
     * Creates a GraphExecutionPlan from pre-built components.
     * Used by runtime-level builders that create custom partition types.
     *
     * @param sortedVertexIds  topologically sorted vertex IDs
     * @param executionVertices map of vertexId to JobVertex
     * @param invokables       map of vertexId to StreamTaskInvokable
     * @param subtasks         map of vertexId to list of subtasks
     * @return a new GraphExecutionPlan
     */
    public static GraphExecutionPlan create(List<String> sortedVertexIds,
                                            Map<String, JobVertex> executionVertices,
                                            Map<String, StreamTaskInvokable> invokables,
                                            Map<String, List<Subtask>> subtasks) {
        return new GraphExecutionPlan(sortedVertexIds, executionVertices, invokables, subtasks);
    }

    /**
     * Builds an execution plan from the given JobGraph.
     *
     * <p>For each edge, ResultPartitions are created and wired into the
     * upstream RecordWriter and downstream InputGate. Vertices with no
     * edges use SELF_CONTAINED mode (identical to single-chain behavior).
     * Uses default barrier alignment (true = STRICT_EXACTLY_ONCE).
     *
     * @param jobGraph the job graph to plan execution for
     * @return the execution plan
     */
    public static GraphExecutionPlan build(JobGraph jobGraph) {
        return build(jobGraph, null, true);
    }

    /**
     * Builds an execution plan from the given JobGraph with an optional DeploymentPlan.
     *
     * <p>When a DeploymentPlan is provided, its edge configuration and memory budget
     * can influence the execution plan construction. When null, default behavior applies.
     * Uses default barrier alignment (true = STRICT_EXACTLY_ONCE).
     *
     * @param jobGraph       the job graph to plan execution for
     * @param deploymentPlan optional deployment plan (null uses default behavior)
     * @return the execution plan
     */
    public static GraphExecutionPlan build(JobGraph jobGraph, DeploymentPlan deploymentPlan) {
        return build(jobGraph, deploymentPlan, true);
    }

    /**
     * Builds an execution plan from the given JobGraph with full configuration.
     *
     * <p>Supports parallelism > 1: for each vertex with parallelism N, N subtasks are created.
     * For each edge, a matrix of sourceParallelism × targetParallelism ResultPartitions
     * is allocated. The partition routing strategy is determined by the edge's
     * {@link PartitionPolicy} (resolved from the DeploymentPlan or defaulting to FORWARD).
     *
     * @param jobGraph         the job graph to plan execution for
     * @param deploymentPlan   optional deployment plan (null uses default behavior)
     * @param barrierAlignment if true, InputGates use barrier alignment (STRICT_EXACTLY_ONCE);
     *                         if false, they allow records to flow through during barriers (AT_LEAST_ONCE)
     * @return the execution plan
     */
    public static GraphExecutionPlan build(JobGraph jobGraph, DeploymentPlan deploymentPlan,
                                           boolean barrierAlignment) {
        // --- 1. Build adjacency maps ---
        Map<String, List<JobEdge>> outgoingEdges = new HashMap<>();
        Map<String, List<JobEdge>> incomingEdges = new HashMap<>();
        for (JobEdge edge : jobGraph.getEdges()) {
            outgoingEdges.computeIfAbsent(edge.getSourceVertex(), k -> new ArrayList<>()).add(edge);
            incomingEdges.computeIfAbsent(edge.getTargetVertex(), k -> new ArrayList<>()).add(edge);
        }

        // --- 2. Resolve parallelism for each vertex ---
        Map<String, Integer> parallelismMap = resolveParallelism(jobGraph, deploymentPlan);

        // --- 3. Allocate partition matrix per edge ---
        // Key: edge -> [sourceSubtaskIndex][targetSubtaskIndex] = ResultPartition
        Map<JobEdge, ResultPartition[][]> edgePartitionMatrix = new LinkedHashMap<>();
        for (JobEdge edge : jobGraph.getEdges()) {
            int srcP = parallelismMap.getOrDefault(edge.getSourceVertex(), 1);
            int tgtP = parallelismMap.getOrDefault(edge.getTargetVertex(), 1);
            ResultPartition[][] matrix = new ResultPartition[srcP][tgtP];
            for (int s = 0; s < srcP; s++) {
                for (int t = 0; t < tgtP; t++) {
                    matrix[s][t] = new ResultPartition();
                }
            }
            edgePartitionMatrix.put(edge, matrix);
        }

        // --- 4. Legacy single-task structures (for backward compat) ---
        Map<String, JobVertex> executionVertices = new LinkedHashMap<>();
        Map<String, StreamTaskInvokable> invokables = new LinkedHashMap<>();

        // --- 5. New subtask structures ---
        Map<String, List<Subtask>> subtasksMap = new LinkedHashMap<>();

        // --- 6. Build subtasks for each vertex ---
        for (Map.Entry<String, JobVertex> entry : jobGraph.getVertices().entrySet()) {
            String vertexId = entry.getKey();
            JobVertex original = entry.getValue();
            int parallelism = parallelismMap.getOrDefault(vertexId, 1);

            List<JobEdge> outEdges = outgoingEdges.getOrDefault(vertexId, Collections.emptyList());
            List<JobEdge> inEdges = incomingEdges.getOrDefault(vertexId, Collections.emptyList());

            List<Subtask> vertexSubtasks = new ArrayList<>(parallelism);

            for (int taskIndex = 0; taskIndex < parallelism; taskIndex++) {
                OperatorChain chain = taskIndex == 0
                        ? original.getOperatorChains().get(0)
                        : original.getOperatorChains().get(0).deepCopy();

                RecordWriter<Object> recordWriter = null;
                InputGate inputGate = null;

                // Build RecordWriter: for each outgoing edge, collect the partitions
                // that this source subtask writes to (one per target subtask per edge)
                if (!outEdges.isEmpty()) {
                    List<ResultPartition> writerPartitions = new ArrayList<>();
                    List<IPartitioner<?>> edgePartitioners = new ArrayList<>();

                    for (JobEdge edge : outEdges) {
                        ResultPartition[][] matrix = edgePartitionMatrix.get(edge);
                        if (matrix != null) {
                            for (int t = 0; t < matrix[taskIndex].length; t++) {
                                writerPartitions.add(matrix[taskIndex][t]);
                            }
                        }
                    }

                    if (!writerPartitions.isEmpty()) {
                        // Resolve the partition policy for the primary outgoing edge
                        PartitionPolicy policy = resolvePartitionPolicy(
                                outEdges.get(0), deploymentPlan);
                        IPartitioner<?> partitioner = outEdges.get(0).getPartitioner();
                        EdgeConfig writerConfig = resolveEdgeConfig(outEdges.get(0), deploymentPlan);

                        PartitionRouter router = PartitionRouter.create(
                                policy, writerPartitions.size(), partitioner, taskIndex);

                        recordWriter = new RecordWriter<Object>(
                                writerPartitions.toArray(new ResultPartition[0]),
                                (IPartitioner<Object>) partitioner, writerConfig, router);
                    }
                }

                // Build InputGate: for each incoming edge, collect the partitions
                // from all source subtasks that feed into this target subtask
                if (!inEdges.isEmpty()) {
                    List<InputChannel> channels = new ArrayList<>();
                    for (JobEdge edge : inEdges) {
                        ResultPartition[][] matrix = edgePartitionMatrix.get(edge);
                        if (matrix != null) {
                            for (int s = 0; s < matrix.length; s++) {
                                channels.add(new InputChannel(matrix[s][taskIndex]));
                            }
                        }
                    }

                    if (!channels.isEmpty()) {
                        EdgeConfig gateConfig = resolveEdgeConfig(inEdges.get(0), deploymentPlan);
                        inputGate = new InputGate(channels, gateConfig, barrierAlignment);
                    }
                }

                StreamTaskInvokable invokable;
                if (recordWriter != null || inputGate != null) {
                    invokable = new StreamTaskInvokable(chain, recordWriter, inputGate);
                } else {
                    invokable = new StreamTaskInvokable(chain);
                }

                TaskLocation taskLocation = new TaskLocation(
                        jobGraph.getJobName(), "pipeline-0", vertexId, taskIndex);

                Subtask subtask = new Subtask(vertexId, taskIndex, taskLocation, invokable);
                vertexSubtasks.add(subtask);

                // For backward compat: store the first subtask's invokable in the legacy map
                if (taskIndex == 0) {
                    invokables.put(vertexId, invokable);

                    JobVertex execVertex = new JobVertex(
                            original.getId(), original.getName(), original.getParallelism(),
                            original.getOperatorChains(), invokable);
                    executionVertices.put(vertexId, execVertex);
                }
            }

            subtasksMap.put(vertexId, vertexSubtasks);
        }

        List<String> sorted = topologicalSort(jobGraph);

        return new GraphExecutionPlan(sorted, executionVertices, invokables, subtasksMap);
    }

    /**
     * Resolves parallelism for each vertex. Checks the DeploymentPlan's PartitionedPlan first,
     * then falls back to the JobVertex's parallelism.
     */
    private static Map<String, Integer> resolveParallelism(JobGraph jobGraph,
                                                            DeploymentPlan deploymentPlan) {
        Map<String, Integer> result = new LinkedHashMap<>();
        Map<String, Integer> planParallelism = null;

        if (deploymentPlan != null && deploymentPlan.getPartitionedPlan() != null) {
            planParallelism = new HashMap<>();
            for (Map.Entry<String, PartitionedPlan.VertexPlan> entry :
                    deploymentPlan.getPartitionedPlan().getVertexPlans().entrySet()) {
                planParallelism.put(entry.getKey(), entry.getValue().getParallelism());
            }
        }

        for (Map.Entry<String, JobVertex> entry : jobGraph.getVertices().entrySet()) {
            String vertexId = entry.getKey();
            int parallelism = entry.getValue().getParallelism();
            if (planParallelism != null && planParallelism.containsKey(vertexId)) {
                parallelism = planParallelism.get(vertexId);
            }
            result.put(vertexId, Math.max(1, parallelism));
        }
        return result;
    }

    /**
     * Resolves the PartitionPolicy for a given JobEdge.
     *
     * <p>Checks the DeploymentPlan's PartitionedPlan edge plans first,
     * then defaults to FORWARD.
     */
    private static PartitionPolicy resolvePartitionPolicy(JobEdge edge, DeploymentPlan deploymentPlan) {
        if (deploymentPlan != null && deploymentPlan.getPartitionedPlan() != null) {
            for (PartitionedPlan.EdgePlan edgePlan :
                    deploymentPlan.getPartitionedPlan().getEdgePlans()) {
                if (edgePlan.getSourceVertexId().equals(edge.getSourceVertex())
                        && edgePlan.getTargetVertexId().equals(edge.getTargetVertex())) {
                    return edgePlan.getPartitionPolicy();
                }
            }
        }
        // If edge has a partitioner, assume HASH
        if (edge.getPartitioner() != null) {
            return PartitionPolicy.HASH;
        }
        return PartitionPolicy.FORWARD;
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
    private static EdgeConfig resolveEdgeConfig(JobEdge edge, DeploymentPlan deploymentPlan) {
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

    /**
     * Topological sort using Kahn's algorithm. Sources (no incoming edges)
     * come first, sinks (no outgoing edges) come last.
     */
    private static List<String> topologicalSort(JobGraph jobGraph) {
        Map<String, List<String>> adjacency = new HashMap<>();
        Map<String, Integer> inDegree = new HashMap<>();

        for (String vertexId : jobGraph.getVertices().keySet()) {
            adjacency.put(vertexId, new ArrayList<>());
            inDegree.put(vertexId, 0);
        }

        for (JobEdge edge : jobGraph.getEdges()) {
            adjacency.get(edge.getSourceVertex()).add(edge.getTargetVertex());
            inDegree.merge(edge.getTargetVertex(), 1, Integer::sum);
        }

        Queue<String> queue = new LinkedList<>();
        for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.add(entry.getKey());
            }
        }

        List<String> sorted = new ArrayList<>();
        while (!queue.isEmpty()) {
            String vertexId = queue.poll();
            sorted.add(vertexId);
            for (String neighbor : adjacency.get(vertexId)) {
                int newDegree = inDegree.get(neighbor) - 1;
                inDegree.put(neighbor, newDegree);
                if (newDegree == 0) {
                    queue.add(neighbor);
                }
            }
        }

        return sorted;
    }

    public List<String> getSortedVertexIds() {
        return sortedVertexIds;
    }

    public Map<String, JobVertex> getExecutionVertices() {
        return executionVertices;
    }

    public Map<String, StreamTaskInvokable> getInvokables() {
        return invokables;
    }

    /**
     * Returns all subtasks indexed by vertexId.
     * Each vertex maps to a list of Subtask instances, one per parallelism unit.
     *
     * @return unmodifiable map of vertexId to list of subtasks
     */
    public Map<String, List<Subtask>> getSubtasks() {
        return Collections.unmodifiableMap(subtasks);
    }

    /**
     * Returns the subtasks for a specific vertex.
     *
     * @param vertexId the vertex ID
     * @return list of subtasks for the vertex, or empty list if vertex not found
     */
    public List<Subtask> getSubtasks(String vertexId) {
        return subtasks.getOrDefault(vertexId, Collections.emptyList());
    }
}
