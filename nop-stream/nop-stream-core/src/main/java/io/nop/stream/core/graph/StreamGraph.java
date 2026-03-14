/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.graph;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents the streaming topology as a Directed Acyclic Graph (DAG).
 * This is the core data structure that captures the structure of a streaming
 * program, including all operations, their connections, and execution properties.
 * 
 * <p>The StreamGraph maintains two main collections:
 * <ul>
 *   <li>Stream nodes: Represent operations (sources, transformations, sinks)</li>
 *   <li>Stream edges: Represent data flow between operations</li>
 * </ul>
 * 
 * <p>This class is inspired by Apache Flink's StreamGraph design but simplified
 * for nop-stream's requirements. It serves as the intermediate representation
 * between the user-facing DataStream API and the execution layer.
 * 
 * @see StreamNode
 * @see StreamEdge
 */
public class StreamGraph implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * Map of all stream nodes in the graph, keyed by node ID.
     */
    private final Map<Integer, StreamNode> streamNodes;
    
    /**
     * Map of stream edges, keyed by source node ID.
     * Each source node can have multiple outgoing edges.
     */
    private final Map<Integer, List<StreamEdge>> streamEdges;
    
    /**
     * List of source node IDs in the graph.
     * Source nodes are the entry points of the streaming topology.
     */
    private final List<Integer> sourceIDs;
    
    /**
     * List of sink node IDs in the graph.
     * Sink nodes are the terminal points of the streaming topology.
     */
    private final List<Integer> sinkIDs;
    
    /**
     * Creates a new empty StreamGraph.
     */
    public StreamGraph() {
        this.streamNodes = new HashMap<>();
        this.streamEdges = new HashMap<>();
        this.sourceIDs = new ArrayList<>();
        this.sinkIDs = new ArrayList<>();
    }
    
    /**
     * Adds a stream node to the graph.
     * 
     * @param node the stream node to add
     * @throws IllegalArgumentException if a node with the same ID already exists
     */
    public void addStreamNode(StreamNode node) {
        if (node == null) {
            throw new IllegalArgumentException("StreamNode cannot be null");
        }
        
        int nodeId = node.getId();
        if (streamNodes.containsKey(nodeId)) {
            throw new IllegalArgumentException(
                "StreamNode with ID " + nodeId + " already exists in the graph"
            );
        }
        
        streamNodes.put(nodeId, node);
    }
    
    /**
     * Adds a stream edge to the graph.
     * The edge represents data flow from source to target node.
     * 
     * @param edge the stream edge to add
     * @throws IllegalArgumentException if the edge is null or references non-existent nodes
     */
    public void addStreamEdge(StreamEdge edge) {
        if (edge == null) {
            throw new IllegalArgumentException("StreamEdge cannot be null");
        }
        
        int sourceId = edge.getSourceId();
        int targetId = edge.getTargetId();
        
        // Verify that both source and target nodes exist
        if (!streamNodes.containsKey(sourceId)) {
            throw new IllegalArgumentException(
                "Source node " + sourceId + " does not exist in the graph"
            );
        }
        if (!streamNodes.containsKey(targetId)) {
            throw new IllegalArgumentException(
                "Target node " + targetId + " does not exist in the graph"
            );
        }
        
        // Add edge to the edges map
        streamEdges.computeIfAbsent(sourceId, k -> new ArrayList<>()).add(edge);
    }
    
    /**
     * Retrieves a stream node by its ID.
     * 
     * @param id the node ID
     * @return the stream node, or null if not found
     */
    public StreamNode getStreamNode(int id) {
        return streamNodes.get(id);
    }
    
    /**
     * Retrieves all outgoing edges from a source node.
     * 
     * @param sourceId the source node ID
     * @return an unmodifiable list of edges, or an empty list if no edges exist
     */
    public List<StreamEdge> getStreamEdges(int sourceId) {
        List<StreamEdge> edges = streamEdges.get(sourceId);
        return edges != null ? Collections.unmodifiableList(edges) : Collections.emptyList();
    }
    
    /**
     * Returns all stream nodes in the graph.
     * 
     * @return an unmodifiable map of all stream nodes
     */
    public Map<Integer, StreamNode> getStreamNodes() {
        return Collections.unmodifiableMap(streamNodes);
    }
    
    /**
     * Returns all stream edges in the graph.
     * 
     * @return an unmodifiable map of all stream edges
     */
    public Map<Integer, List<StreamEdge>> getAllStreamEdges() {
        return Collections.unmodifiableMap(streamEdges);
    }
    
    /**
     * Returns the list of source node IDs.
     * 
     * @return an unmodifiable list of source node IDs
     */
    public List<Integer> getSourceIDs() {
        return Collections.unmodifiableList(sourceIDs);
    }
    
    /**
     * Returns the list of sink node IDs.
     * 
     * @return an unmodifiable list of sink node IDs
     */
    public List<Integer> getSinkIDs() {
        return Collections.unmodifiableList(sinkIDs);
    }
    
    /**
     * Adds a source node ID to the list of sources.
     * 
     * @param sourceId the source node ID to add
     */
    public void addSourceID(int sourceId) {
        if (!sourceIDs.contains(sourceId)) {
            sourceIDs.add(sourceId);
        }
    }
    
    /**
     * Adds a sink node ID to the list of sinks.
     * 
     * @param sinkId the sink node ID to add
     */
    public void addSinkID(int sinkId) {
        if (!sinkIDs.contains(sinkId)) {
            sinkIDs.add(sinkId);
        }
    }
    
    /**
     * Returns the total number of nodes in the graph.
     * 
     * @return the number of stream nodes
     */
    public int getNumberOfNodes() {
        return streamNodes.size();
    }
    
    /**
     * Returns the total number of edges in the graph.
     * 
     * @return the number of stream edges
     */
    public int getNumberOfEdges() {
        return streamEdges.values().stream()
            .mapToInt(List::size)
            .sum();
    }
    
    /**
     * Checks if the graph contains a node with the specified ID.
     * 
     * @param nodeId the node ID to check
     * @return true if the node exists, false otherwise
     */
    public boolean containsNode(int nodeId) {
        return streamNodes.containsKey(nodeId);
    }
    
    /**
     * Clears all nodes, edges, sources, and sinks from the graph.
     */
    public void clear() {
        streamNodes.clear();
        streamEdges.clear();
        sourceIDs.clear();
        sinkIDs.clear();
    }
    
    @Override
    public String toString() {
        return "StreamGraph{" +
            "nodes=" + streamNodes.size() +
            ", edges=" + getNumberOfEdges() +
            ", sources=" + sourceIDs.size() +
            ", sinks=" + sinkIDs.size() +
            '}';
    }
}
