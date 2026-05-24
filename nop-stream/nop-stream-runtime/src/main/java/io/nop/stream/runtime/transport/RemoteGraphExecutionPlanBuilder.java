/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.transport;

import io.nop.api.core.message.IMessageService;
import io.nop.commons.partition.IPartitioner;
import io.nop.stream.core.checkpoint.TaskLocation;
import io.nop.stream.core.execution.GraphExecutionPlan;
import io.nop.stream.core.execution.InputChannel;
import io.nop.stream.core.execution.InputGate;
import io.nop.stream.core.execution.PartitionRouter;
import io.nop.stream.core.execution.RecordWriter;
import io.nop.stream.core.execution.ResultPartition;
import io.nop.stream.core.execution.StreamTaskInvokable;
import io.nop.stream.core.execution.Subtask;
import io.nop.stream.core.execution.flow.EdgeConfig;
import io.nop.stream.core.execution.plan.DeploymentPlan;
import io.nop.stream.core.execution.plan.PartitionedPlan;
import io.nop.stream.core.execution.plan.PartitionPolicy;
import io.nop.stream.core.execution.transport.TypeRegistry;
import io.nop.stream.core.jobgraph.JobEdge;
import io.nop.stream.core.jobgraph.JobGraph;
import io.nop.stream.core.jobgraph.JobVertex;
import io.nop.stream.core.jobgraph.OperatorChain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

/**
 * Builds a {@link GraphExecutionPlan} that uses {@link IMessageService} for
 * cross-TaskManager data exchange.
 *
 * <p>When both source and target subtasks are on the same TaskManager, local
 * {@link ResultPartition} instances are used (same as {@link GraphExecutionPlan#build}).
 * When they are on different TaskManagers, {@link RemoteResultPartition} and
 * {@link RemoteInputChannel} are used instead.
 *
 * <p>For the in-process simulation (all tasks on the same TaskManager but using
 * IMessageService for transport), use {@link #buildRemoteOnly} which forces all
 * edges to use the remote transport.
 */
public class RemoteGraphExecutionPlanBuilder {

    private static final Logger LOG = LoggerFactory.getLogger(RemoteGraphExecutionPlanBuilder.class);

    private final IMessageService messageService;
    private final TypeRegistry typeRegistry;
    private final String fencingToken;
    private final long epochId;

    public RemoteGraphExecutionPlanBuilder(IMessageService messageService,
                                           TypeRegistry typeRegistry,
                                           String fencingToken,
                                           long epochId) {
        this.messageService = messageService;
        this.typeRegistry = typeRegistry;
        this.fencingToken = fencingToken;
        this.epochId = epochId;
    }

    /**
     * Builds an execution plan where ALL edges use IMessageService transport.
     * This is useful for in-process testing with LocalMessageService.
     *
     * @param jobGraph         the job graph
     * @param deploymentPlan   optional deployment plan
     * @param barrierAlignment barrier alignment mode
     * @return the execution plan
     */
    public GraphExecutionPlan buildRemoteOnly(JobGraph jobGraph,
                                              DeploymentPlan deploymentPlan,
                                              boolean barrierAlignment) {
        // --- 1. Build adjacency maps ---
        Map<String, List<JobEdge>> outgoingEdges = new HashMap<>();
        Map<String, List<JobEdge>> incomingEdges = new HashMap<>();
        for (JobEdge edge : jobGraph.getEdges()) {
            outgoingEdges.computeIfAbsent(edge.getSourceVertex(), k -> new ArrayList<>()).add(edge);
            incomingEdges.computeIfAbsent(edge.getTargetVertex(), k -> new ArrayList<>()).add(edge);
        }

        // --- 2. Resolve parallelism ---
        Map<String, Integer> parallelismMap = resolveParallelism(jobGraph, deploymentPlan);

        // --- 3. Allocate partition matrix per edge ---
        // We use RemoteResultPartition for all producer-side partitions
        // and RemoteInputChannel for all consumer-side channels
        String jobId = jobGraph.getJobName();

        Map<JobEdge, ResultPartition[][]> edgePartitionMatrix = new LinkedHashMap<>();
        Map<JobEdge, List<RemoteInputChannel>> edgeInputChannels = new LinkedHashMap<>();

        for (JobEdge edge : jobGraph.getEdges()) {
            int srcP = parallelismMap.getOrDefault(edge.getSourceVertex(), 1);
            int tgtP = parallelismMap.getOrDefault(edge.getTargetVertex(), 1);

            // Build an edge ID from sourceVertex->targetVertex
            String edgeId = edge.getSourceVertex() + "->" + edge.getTargetVertex();

            ResultPartition[][] matrix = new ResultPartition[srcP][tgtP];
            List<RemoteInputChannel> channels = new ArrayList<>();

            for (int s = 0; s < srcP; s++) {
                for (int t = 0; t < tgtP; t++) {
                    String topic = StreamTopicNaming.buildTopic(jobId, edgeId, s, t);
                    // Producer side: RemoteResultPartition
                    matrix[s][t] = new RemoteResultPartition(
                            messageService, topic, typeRegistry, edgeId,
                            fencingToken, epochId);

                    // Consumer side: RemoteInputChannel (created per target subtask per source)
                    RemoteInputChannel remoteChannel = new RemoteInputChannel(
                            messageService, topic, fencingToken, epochId);
                    channels.add(remoteChannel);
                }
            }

            edgePartitionMatrix.put(edge, matrix);
            edgeInputChannels.put(edge, channels);
        }

        // --- 4. Build subtasks ---
        Map<String, JobVertex> executionVertices = new LinkedHashMap<>();
        Map<String, StreamTaskInvokable> invokables = new LinkedHashMap<>();
        Map<String, List<Subtask>> subtasksMap = new LinkedHashMap<>();

        for (Map.Entry<String, JobVertex> entry : jobGraph.getVertices().entrySet()) {
            String vertexId = entry.getKey();
            JobVertex original = entry.getValue();
            int parallelism = parallelismMap.getOrDefault(vertexId, 1);

            List<JobEdge> outEdges = outgoingEdges.getOrDefault(vertexId, Collections.emptyList());
            List<JobEdge> inEdges = incomingEdges.getOrDefault(vertexId, Collections.emptyList());

            List<Subtask> vertexSubtasks = new ArrayList<>(parallelism);

            for (int taskIndex = 0; taskIndex < parallelism; taskIndex++) {
                OperatorChain chain = original.getOperatorChains().get(0);

                RecordWriter<Object> recordWriter = null;
                InputGate inputGate = null;

                // Build RecordWriter
                if (!outEdges.isEmpty()) {
                    List<ResultPartition> writerPartitions = new ArrayList<>();

                    for (JobEdge edge : outEdges) {
                        ResultPartition[][] matrix = edgePartitionMatrix.get(edge);
                        if (matrix != null) {
                            for (int t = 0; t < matrix[taskIndex].length; t++) {
                                writerPartitions.add(matrix[taskIndex][t]);
                            }
                        }
                    }

                    if (!writerPartitions.isEmpty()) {
                        PartitionPolicy policy = resolvePartitionPolicy(
                                outEdges.get(0), deploymentPlan);
                        IPartitioner<?> partitioner = outEdges.get(0).getPartitioner();
                        EdgeConfig writerConfig = resolveEdgeConfig(outEdges.get(0), deploymentPlan);

                        PartitionRouter router = PartitionRouter.create(
                                policy, writerPartitions.size(), partitioner, taskIndex);

                        recordWriter = new RecordWriter<>(
                                writerPartitions.toArray(new ResultPartition[0]),
                                (IPartitioner<Object>) partitioner, writerConfig, router);
                    }
                }

                // Build InputGate using RemoteInputChannels
                if (!inEdges.isEmpty()) {
                    List<InputChannel> channels = new ArrayList<>();
                    for (JobEdge edge : inEdges) {
                        int srcP = parallelismMap.getOrDefault(edge.getSourceVertex(), 1);
                        List<RemoteInputChannel> edgeChannels = edgeInputChannels.get(edge);
                        if (edgeChannels != null) {
                            // This target subtask (taskIndex) receives from all source subtasks
                            // Channels are stored as [src0tgt0, src0tgt1, ..., src1tgt0, ...]
                            // We need channels where targetIndex == taskIndex
                            int tgtP = parallelismMap.getOrDefault(edge.getTargetVertex(), 1);
                            for (int s = 0; s < srcP; s++) {
                                int idx = s * tgtP + taskIndex;
                                if (idx < edgeChannels.size()) {
                                    channels.add(edgeChannels.get(idx));
                                }
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

        return GraphExecutionPlan.create(sorted, executionVertices, invokables, subtasksMap);
    }

    // --- Helper methods (same logic as GraphExecutionPlan) ---

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
        if (edge.getPartitioner() != null) {
            return PartitionPolicy.HASH;
        }
        return PartitionPolicy.FORWARD;
    }

    private static EdgeConfig resolveEdgeConfig(JobEdge edge, DeploymentPlan deploymentPlan) {
        if (edge.getEdgeConfig() != null) {
            return edge.getEdgeConfig();
        }
        if (deploymentPlan != null) {
            String edgeKey = edge.getSourceVertex() + "->" + edge.getTargetVertex();
            return deploymentPlan.getEdgeConfigs().get(edgeKey);
        }
        return null;
    }

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
}
