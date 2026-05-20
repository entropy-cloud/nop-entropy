/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.execution;

import io.nop.api.core.annotations.core.Internal;
import io.nop.stream.core.jobgraph.JobEdge;
import io.nop.stream.core.jobgraph.JobGraph;
import io.nop.stream.core.jobgraph.JobVertex;
import io.nop.stream.core.jobgraph.OperatorChain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

/**
 * Builds an execution plan from a JobGraph, creating data exchange channels
 * (ResultPartition, InputChannel) for inter-task communication.
 *
 * <p>For each JobEdge, a {@link ResultPartition} is created. Upstream tasks
 * get a {@link RecordWriter} connected to their outgoing partitions; downstream
 * tasks get an {@link InputGate} connected to their incoming channels.
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

    private GraphExecutionPlan(List<String> sortedVertexIds,
                               Map<String, JobVertex> executionVertices,
                               Map<String, StreamTaskInvokable> invokables) {
        this.sortedVertexIds = sortedVertexIds;
        this.executionVertices = executionVertices;
        this.invokables = invokables;
    }

    /**
     * Builds an execution plan from the given JobGraph.
     *
     * <p>For each edge, a ResultPartition is created and wired into the
     * upstream RecordWriter and downstream InputGate. Vertices with no
     * edges use SELF_CONTAINED mode (identical to single-chain behavior).
     *
     * @param jobGraph the job graph to plan execution for
     * @return the execution plan
     */
    public static GraphExecutionPlan build(JobGraph jobGraph) {
        Map<String, List<JobEdge>> outgoingEdges = new HashMap<>();
        Map<String, List<JobEdge>> incomingEdges = new HashMap<>();
        for (JobEdge edge : jobGraph.getEdges()) {
            outgoingEdges.computeIfAbsent(edge.getSourceVertex(), k -> new ArrayList<>()).add(edge);
            incomingEdges.computeIfAbsent(edge.getTargetVertex(), k -> new ArrayList<>()).add(edge);
        }

        Map<JobEdge, ResultPartition> edgePartitions = new LinkedHashMap<>();
        for (JobEdge edge : jobGraph.getEdges()) {
            edgePartitions.put(edge, new ResultPartition());
        }

        Map<String, JobVertex> executionVertices = new LinkedHashMap<>();
        Map<String, StreamTaskInvokable> invokables = new LinkedHashMap<>();

        for (Map.Entry<String, JobVertex> entry : jobGraph.getVertices().entrySet()) {
            String vertexId = entry.getKey();
            JobVertex original = entry.getValue();

            List<JobEdge> outEdges = outgoingEdges.getOrDefault(vertexId, Collections.emptyList());
            List<JobEdge> inEdges = incomingEdges.getOrDefault(vertexId, Collections.emptyList());

            OperatorChain chain = original.getOperatorChains().get(0);

            RecordWriter<Object> recordWriter = null;
            InputGate inputGate = null;

            if (!outEdges.isEmpty()) {
                ResultPartition[] partitions = new ResultPartition[outEdges.size()];
                for (int i = 0; i < outEdges.size(); i++) {
                    partitions[i] = edgePartitions.get(outEdges.get(i));
                }
                recordWriter = new RecordWriter<>(partitions, null);
            }

            if (!inEdges.isEmpty()) {
                List<InputChannel> channels = new ArrayList<>();
                for (JobEdge edge : inEdges) {
                    channels.add(new InputChannel(edgePartitions.get(edge)));
                }
                inputGate = new InputGate(channels);
            }

            StreamTaskInvokable invokable;
            if (recordWriter != null || inputGate != null) {
                invokable = new StreamTaskInvokable(chain, recordWriter, inputGate);
            } else {
                invokable = new StreamTaskInvokable(chain);
            }

            JobVertex execVertex = new JobVertex(
                original.getId(), original.getName(), original.getParallelism(),
                original.getOperatorChains(), invokable);

            executionVertices.put(vertexId, execVertex);
            invokables.put(vertexId, invokable);
        }

        List<String> sorted = topologicalSort(jobGraph);

        return new GraphExecutionPlan(sorted, executionVertices, invokables);
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
}
