/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.jobgraph;

import io.nop.stream.core.graph.StreamEdge;
import io.nop.stream.core.graph.StreamGraph;
import io.nop.stream.core.graph.StreamNode;
import io.nop.stream.core.operator.StreamOperator;
import io.nop.stream.core.operator.StreamOperatorFactory;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Generates an optimized JobGraph from a StreamGraph through operator chaining.
 *
 * <p>JobGraphGenerator is responsible for converting the logical streaming topology (StreamGraph)
 * into an optimized execution plan (JobGraph). The key optimization is operator chaining, where
 * multiple operators are fused together to run in a single thread, avoiding serialization and
 * network overhead.
 *
 * <p>The generation process involves three main steps:
 * <ol>
 *   <li><strong>Chain Identification</strong>: Identify groups of operators that can be chained together
 *       based on chaining criteria (same parallelism, forward partitioning, etc.)</li>
 *   <li><strong>Vertex Creation</strong>: Create JobVertex instances for each operator chain, where each
 *       vertex represents a schedulable task containing fused operators</li>
 *   <li><strong>Edge Creation</strong>: Create JobEdge instances to connect vertices, defining how
 *       intermediate results flow between tasks</li>
 * </ol>
 *
 * <p><strong>Chaining Criteria</strong>: Two operators can be chained if:
 * <ul>
 *   <li>They have the same parallelism</li>
 *   <li>The edge between them uses forward partitioning (null partitioner or ForwardPartitioner)</li>
 *   <li>There is a direct edge from the source to the target (no branching in between)</li>
 *   <li>The source node is not a sink and the target node is not a source</li>
 * </ul>
 *
 * <p><strong>ID Mapping</strong>: The generator maps StreamNode Integer IDs to JobVertex String IDs.
 * Each JobVertex ID is generated as "vertex-" + first StreamNode ID in the chain.
 *
 * <p>This class is stateless and can be reused to generate multiple JobGraphs from different StreamGraphs.
 *
 * @see StreamGraph
 * @see JobGraph
 * @see JobVertex
 * @see JobEdge
 * @see OperatorChain
 */
public class JobGraphGenerator implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Generates an optimized JobGraph from the given StreamGraph.
     *
     * <p>This method performs the complete conversion process:
     * <ol>
     *   <li>Identifies operator chains in the StreamGraph</li>
     *   <li>Creates JobVertex for each chain</li>
     *   <li>Creates JobEdge to connect vertices</li>
     * </ol>
     *
     * @param streamGraph the StreamGraph to convert (must not be null)
     * @return the optimized JobGraph ready for execution
     * @throws IllegalArgumentException if streamGraph is null
     */
    public JobGraph generate(StreamGraph streamGraph) {
        if (streamGraph == null) {
            throw new IllegalArgumentException("StreamGraph cannot be null");
        }

        // Create a new JobGraph with the job name from streamGraph
        JobGraph jobGraph = new JobGraph("stream-job");

        // Step 1: Identify chains of operators that can be fused
        List<List<StreamNode>> chains = identifyChains(streamGraph);

        // Build node-to-vertex map while chains are still in scope
        Map<Integer, String> nodeToVertexMap = new HashMap<>();

        // Step 2: Create JobVertex for each chain
        for (List<StreamNode> chain : chains) {
            JobVertex vertex = createJobVertex(chain, streamGraph);
            jobGraph.addVertex(vertex);
            // Map every node in the chain to this vertex
            for (StreamNode node : chain) {
                nodeToVertexMap.put(node.getId(), vertex.getId());
            }
        }

        // Step 3: Create JobEdges to connect vertices
        createJobEdges(streamGraph, jobGraph, nodeToVertexMap);

        return jobGraph;
    }

    /**
     * Identifies groups of operators that can be chained together.
     *
     * <p>This method traverses the StreamGraph and groups operators into chains based on
     * the chaining criteria. Each chain represents a sequence of operators that can be
     * executed in a single thread without serialization overhead.
     *
     * <p><strong>Algorithm</strong>:
     * <ol>
     *   <li>Start from source nodes in the graph</li>
     *   <li>For each node, check if it can be chained with its downstream nodes</li>
     *   <li>If chainable, add to the current chain; otherwise, start a new chain</li>
     *   <li>Continue until all nodes are processed</li>
     * </ol>
     *
     * @param streamGraph the StreamGraph to analyze
     * @return list of operator chains, where each chain is a list of StreamNodes
     */
    private List<List<StreamNode>> identifyChains(StreamGraph streamGraph) {
        List<List<StreamNode>> chains = new ArrayList<>();
        Set<Integer> processedNodes = new HashSet<>();

        // Start from source nodes
        for (Integer sourceId : streamGraph.getSourceIDs()) {
            StreamNode sourceNode = streamGraph.getStreamNode(sourceId);
            if (sourceNode != null && !processedNodes.contains(sourceId)) {
                List<StreamNode> chain = new ArrayList<>();
                buildChain(sourceNode, chain, processedNodes, streamGraph);
                if (!chain.isEmpty()) {
                    chains.add(chain);
                }
            }
        }

        // Process any remaining nodes not reachable from sources
        for (Integer nodeId : streamGraph.getStreamNodes().keySet()) {
            if (!processedNodes.contains(nodeId)) {
                StreamNode node = streamGraph.getStreamNode(nodeId);
                List<StreamNode> chain = new ArrayList<>();
                buildChain(node, chain, processedNodes, streamGraph);
                if (!chain.isEmpty()) {
                    chains.add(chain);
                }
            }
        }

        return chains;
    }

    /**
     * Recursively builds a chain of operators starting from the given node.
     *
     * <p>This method performs depth-first traversal to build chains, following edges
     * and adding nodes to the chain as long as they meet the chaining criteria.
     *
     * @param currentNode the current node being processed
     * @param chain the chain being built
     * @param processedNodes set of already processed node IDs
     * @param streamGraph the StreamGraph being analyzed
     */
    private void buildChain(StreamNode currentNode, List<StreamNode> chain,
                           Set<Integer> processedNodes, StreamGraph streamGraph) {
        if (currentNode == null || processedNodes.contains(currentNode.getId())) {
            return;
        }

        // Add current node to chain and mark as processed
        chain.add(currentNode);
        processedNodes.add(currentNode.getId());

        // Get outgoing edges from this node
        List<StreamEdge> outgoingEdges = streamGraph.getStreamEdges(currentNode.getId());

        // Only continue chain if there's exactly one outgoing edge (no branching)
        if (outgoingEdges.size() == 1) {
            StreamEdge edge = outgoingEdges.get(0);
            StreamNode nextNode = streamGraph.getStreamNode(edge.getTargetId());

            // Check if the next node can be chained with the current node
            if (nextNode != null && !processedNodes.contains(nextNode.getId()) &&
                canChain(currentNode, nextNode, edge, streamGraph)) {
                buildChain(nextNode, chain, processedNodes, streamGraph);
            }
        }
    }

    /**
     * Determines if two stream nodes can be chained together.
     *
     * <p>Two nodes can be chained if they meet all the following criteria:
     * <ul>
     *   <li><strong>Same parallelism</strong>: Both nodes must have the same parallelism setting</li>
     *   <li><strong>Forward partitioning</strong>: The edge between them must use forward partitioning
     *       (null partitioner or ForwardPartitioner)</li>
     *   <li><strong>Not source/sink boundary</strong>: The source should not be a sink, and the target
     *       should not be a source (sources and sinks act as chain boundaries)</li>
     *   <li><strong>Single edge</strong>: The source node should have only one outgoing edge and the
     *       target node should have only one incoming edge (no branching)</li>
     * </ul>
     *
     * <p><strong>Note</strong>: This implementation uses null partitioner check for forward partitioning,
     * as that's the convention in the current implementation. A ForwardPartitioner class can be added
     * later if needed.
     *
     * @param node1 the source node (upstream)
     * @param node2 the target node (downstream)
     * @param edge the edge connecting node1 to node2
     * @param streamGraph the StreamGraph containing these nodes
     * @return true if the nodes can be chained, false otherwise
     */
    private boolean canChain(StreamNode node1, StreamNode node2, StreamEdge edge, StreamGraph streamGraph) {
        // 1. Check parallelism - both nodes must have the same parallelism
        if (node1.getParallelism() != node2.getParallelism()) {
            return false;
        }

        // 2. Check partitioning strategy - must be forward partitioning
        // Forward partitioning means partitioner is null (data goes to same parallel instance)
        if (edge.getPartitioner() != null) {
            // Non-null partitioner means data is redistributed, cannot chain
            return false;
        }

        // 3. Check if node1 is a sink - sinks are chain boundaries
        if (streamGraph.getSinkIDs().contains(node1.getId())) {
            return false;
        }

        // 4. Check if node2 is a source - sources are chain boundaries
        if (streamGraph.getSourceIDs().contains(node2.getId())) {
            return false;
        }

        // 5. Check if node1 has multiple outgoing edges (branching)
        List<StreamEdge> node1Outgoing = streamGraph.getStreamEdges(node1.getId());
        if (node1Outgoing.size() > 1) {
            return false;
        }

        // 6. Check if node2 has multiple incoming edges (merging)
        int node2IncomingCount = countIncomingEdges(node2.getId(), streamGraph);
        if (node2IncomingCount > 1) {
            return false;
        }

        // All criteria met, can chain
        return true;
    }

    /**
     * Counts the number of incoming edges for a node.
     *
     * @param nodeId the ID of the node
     * @param streamGraph the StreamGraph to analyze
     * @return the number of incoming edges
     */
    private int countIncomingEdges(int nodeId, StreamGraph streamGraph) {
        int count = 0;
        for (List<StreamEdge> edges : streamGraph.getAllStreamEdges().values()) {
            for (StreamEdge edge : edges) {
                if (edge.getTargetId() == nodeId) {
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * Creates a JobVertex from a chain of StreamNodes.
     *
     * <p>This method creates an optimized JobVertex that encapsulates the entire chain of operators.
     * The JobVertex contains:
     * <ul>
     *   <li>A unique String ID (based on the first node's ID in the chain)</li>
     *   <li>A descriptive name (concatenation of operator names)</li>
     *   <li>The parallelism from the nodes (all nodes in chain have same parallelism)</li>
     *   <li>An OperatorChain containing all operators to execute</li>
     *   <li>An Invokable for execution</li>
     * </ul>
     *
     * @param chain the list of StreamNodes to combine into a JobVertex
     * @param streamGraph the StreamGraph containing these nodes
     * @return a new JobVertex representing the chained operators
     * @throws IllegalArgumentException if chain is null or empty
     */
    private JobVertex createJobVertex(List<StreamNode> chain, StreamGraph streamGraph) {
        if (chain == null || chain.isEmpty()) {
            throw new IllegalArgumentException("Chain cannot be null or empty");
        }

        // Generate vertex ID from the first node in the chain
        StreamNode firstNode = chain.get(0);
        String vertexId = "vertex-" + firstNode.getId();

        // Build vertex name from chain
        StringBuilder nameBuilder = new StringBuilder();
        for (int i = 0; i < chain.size(); i++) {
            if (i > 0) {
                nameBuilder.append(" -> ");
            }
            nameBuilder.append(chain.get(i).getName());
        }
        String vertexName = nameBuilder.toString();

        // All nodes in chain have same parallelism
        int parallelism = firstNode.getParallelism();

        // Create operators from factories
        List<StreamOperator<?>> operators = new ArrayList<>();
        for (StreamNode node : chain) {
            StreamOperator<?> operator = createOperatorFromFactory(node);
            if (operator != null) {
                operators.add(operator);
            }
        }
        // Create operator chain
        OperatorChain operatorChain = new OperatorChain(operators);
        List<OperatorChain> operatorChains = new ArrayList<>();
        operatorChains.add(operatorChain);

        // Create a simple invokable (placeholder for actual execution logic)
        Invokable<?> invokable = createInvokable(operatorChain);

        // Create and return JobVertex
        return new JobVertex(vertexId, vertexName, parallelism, operatorChains, invokable);
    }

    /**
     * Creates a StreamOperator from the node's factory.
     *
     * <p>This helper method handles the generic type wildcard capture issue when calling
     * createStreamOperator with wildcard types. The type safety is guaranteed by the fact that
     * the factory and output type both come from the same StreamNode.
     *
     * @param node the StreamNode containing the factory and output type
     * @return the created operator, or null if factory is null or creation fails
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private StreamOperator<?> createOperatorFromFactory(StreamNode node) {
        StreamOperatorFactory<?> factory = node.getOperatorFactory();
        if (factory == null) {
            return null;
        }

        // Cast to raw type to handle wildcard capture
        // Type safety is guaranteed because factory and outputType come from the same node
        StreamOperatorFactory rawFactory = (StreamOperatorFactory) factory;
        io.nop.stream.core.common.typeinfo.TypeInformation outputType =
            (io.nop.stream.core.common.typeinfo.TypeInformation) node.getOutputType();

        return rawFactory.createStreamOperator(outputType);
    }

    /**
     * Creates an Invokable for the given operator chain.
     *
     * <p>This is a placeholder implementation. The full implementation would create an invokable
     * that executes the operator chain with proper lifecycle management, checkpointing, etc.
     *
     * @param operatorChain the operator chain to execute
     * @return an Invokable instance
     */
    private Invokable<?> createInvokable(final OperatorChain operatorChain) {
        return new Invokable<Object>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void invoke() throws Exception {
                // Open the chain before processing
                operatorChain.open();

                // Process elements (actual implementation would read from input)
                // This is a placeholder - real implementation would process actual data

                // Close the chain after processing
                operatorChain.close();
            }
        };
    }

    /**
     * Creates JobEdges to connect JobVertices in the JobGraph.
     *
     * <p>This method traverses the StreamGraph edges and creates corresponding JobEdges
     * in the JobGraph. For each StreamEdge that connects two nodes that are NOT in the
     * same chain, a JobEdge is created to connect their respective JobVertices.
     *
     * <p><strong>Edge Creation Logic</strong>:
     * <ol>
     *   <li>For each StreamEdge, find the JobVertex containing the source node</li>
     *   <li>Find the JobVertex containing the target node</li>
     *   <li>If they are different vertices (not in same chain), create a JobEdge</li>
     *   <li>Determine the partition type based on the edge's partitioner</li>
     * </ol>
     *
     * <p><strong>Partition Type Selection</strong>:
     * <ul>
     *   <li>Null partitioner (forward) -> PIPELINED</li>
     *   <li>Non-null partitioner -> PIPELINED_BOUNDED (for backpressure support)</li>
     * </ul>
     *
     * @param streamGraph the source StreamGraph
     * @param jobGraph the target JobGraph to add edges to
     * @param nodeToVertexMap pre-built mapping from StreamNode ID to JobVertex ID
     */
    private void createJobEdges(StreamGraph streamGraph, JobGraph jobGraph,
                                Map<Integer, String> nodeToVertexMap) {

        // Track created edges to avoid duplicates
        Set<String> createdEdges = new HashSet<>();

        // Iterate through all StreamEdges
        for (Map.Entry<Integer, List<StreamEdge>> entry : streamGraph.getAllStreamEdges().entrySet()) {
            for (StreamEdge streamEdge : entry.getValue()) {
                int sourceNodeId = streamEdge.getSourceId();
                int targetNodeId = streamEdge.getTargetId();

                String sourceVertexId = nodeToVertexMap.get(sourceNodeId);
                String targetVertexId = nodeToVertexMap.get(targetNodeId);

                // Only create edge if vertices are different (not in same chain)
                if (sourceVertexId != null && targetVertexId != null &&
                    !sourceVertexId.equals(targetVertexId)) {

                    // Create unique edge key to avoid duplicates
                    String edgeKey = sourceVertexId + "->" + targetVertexId;
                    if (!createdEdges.contains(edgeKey)) {
                        // Determine partition type based on partitioner
                        ResultPartitionType partitionType = determinePartitionType(streamEdge);

                        // Create and add JobEdge
                        JobEdge jobEdge = new JobEdge(sourceVertexId, targetVertexId, partitionType);
                        jobGraph.addEdge(jobEdge);

                        createdEdges.add(edgeKey);
                    }
                }
            }
        }
    }

    /**
     * Determines the ResultPartitionType based on the StreamEdge's partitioner.
     *
     * <p>The partition type affects how intermediate results are produced and consumed:
     * <ul>
     *   <li><strong>PIPELINED</strong>: For forward partitioning (null partitioner), enables
     *       streaming execution with low latency</li>
     *   <li><strong>PIPELINED_BOUNDED</strong>: For non-forward partitioning, enables
     *       pipelined execution with backpressure support</li>
     * </ul>
     *
     * @param streamEdge the StreamEdge to analyze
     * @return the appropriate ResultPartitionType
     */
    private ResultPartitionType determinePartitionType(StreamEdge streamEdge) {
        if (streamEdge.getPartitioner() == null) {
            // Forward partitioning - use PIPELINED for low latency
            return ResultPartitionType.PIPELINED;
        } else {
            // Non-forward partitioning - use PIPELINED_BOUNDED for backpressure
            return ResultPartitionType.PIPELINED_BOUNDED;
        }
    }
}
