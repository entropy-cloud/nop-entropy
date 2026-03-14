/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.graph;

import io.nop.commons.partition.IPartitioner;
import io.nop.stream.core.util.OutputTag;

import java.io.Serializable;

/**
 * Represents an edge in the streaming execution graph.
 * 
 * <p>A StreamEdge represents a connection between two stream nodes (vertices) in the
 * StreamGraph DAG. It defines how data flows from a source node to a target node,
 * including partitioning strategy and optional side-output configuration.
 * 
 * <p>StreamEdges are created during topology construction when operations are chained
 * together. Each edge contains:
 * <ul>
 *   <li>sourceId: ID of the upstream node that produces data</li>
 *   <li>targetId: ID of the downstream node that consumes data</li>
 *   <li>outputTag: Optional tag for side outputs (null for main output)</li>
 *   <li>partitioner: Optional strategy for distributing data across parallel instances</li>
 *   <li>name: Optional human-readable name for the edge</li>
 * </ul>
 * 
 * <p>The partitioner field determines how data is distributed when the target node
 * has parallelism > 1. Common partitioning strategies include:
 * <ul>
 *   <li>Forward: Send to same parallel instance (default for non-keyed)</li>
 *   <li>KeyBy: Partition by key for keyed streams</li>
 *   <li>Round-robin: Distribute evenly across instances</li>
 *   <li>Random: Random distribution</li>
 *   <li>Custom: User-defined partitioning logic</li>
 * </ul>
 * 
 * <p>Side outputs (via outputTag) allow operators to emit multiple output streams.
 * This is useful for:
 * <ul>
 *   <li>Late data handling in windowed operations</li>
 *   <li>Dead letter queues for failed records</li>
 *   <li>Branching logic based on data characteristics</li>
 * </ul>
 * 
 * <p>This class uses a mixed immutability pattern:
 * <ul>
 *   <li>sourceId and targetId are final (required and immutable)</li>
 *   <li>outputTag, partitioner, and name are mutable (optional configuration)</li>
 * </ul>
 * 
 * @see StreamGraph
 * @see StreamNode
 * @see IPartitioner
 * @see OutputTag
 */
public class StreamEdge implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * ID of the source node (upstream producer) for this edge.
     */
    private final int sourceId;
    
    /**
     * ID of the target node (downstream consumer) for this edge.
     */
    private final int targetId;
    
    /**
     * Optional output tag for side outputs.
     * 
     * <p>When null, this edge represents the main output stream from the source node.
     * When non-null, this edge represents a named side output stream.
     * 
     * <p>Side outputs allow operators to emit multiple output streams with different types,",
     * useful for handling late data, errors, or branching logic.
     */
    private OutputTag<?> outputTag;
    
    /**
     * Optional partitioner for distributing data across parallel instances.
     * 
     * <p>When null, forward partitioning is used (data goes to same parallel instance).
     * When non-null, the partitioner determines which parallel instance receives each record.
     * 
     * <p>Common use cases:
     * <ul>
     *   <li>Key-based partitioning for keyed streams (e.g., keyBy operations)</li>
     *   <li>Round-robin for load balancing</li>
     *   <li>Custom partitioning logic for specialized distribution</li>
     * </ul>
     */
    private IPartitioner<?> partitioner;
    
    /**
     * Optional human-readable name for this edge.
     * 
     * <p>Used for debugging, logging, and visualization of the streaming topology.
     * May be null if no specific name is needed.
     */
    private String name;
    
    /**
     * Constructs a new StreamEdge with the specified source and target node IDs.
     * 
     * <p>This is the minimal constructor for creating an edge. Optional configuration
     * (outputTag, partitioner, name) can be set via setter methods.
     * 
     * @param sourceId ID of the upstream source node
     * @param targetId ID of the downstream target node
     */
    public StreamEdge(int sourceId, int targetId) {
        this.sourceId = sourceId;
        this.targetId = targetId;
    }
    
    /**
     * Gets the ID of the source (upstream) node for this edge.
     * 
     * @return the source node ID
     */
    public int getSourceId() {
        return sourceId;
    }
    
    /**
     * Gets the ID of the target (downstream) node for this edge.
     * 
     * @return the target node ID
     */
    public int getTargetId() {
        return targetId;
    }
    
    /**
     * Gets the output tag for side outputs.
     * 
     * @return the output tag, or null if this is the main output
     */
    public OutputTag<?> getOutputTag() {
        return outputTag;
    }
    
    /**
     * Sets the output tag for side outputs.
     * 
     * @param outputTag the output tag for side outputs, or null for main output
     */
    public void setOutputTag(OutputTag<?> outputTag) {
        this.outputTag = outputTag;
    }
    
    /**
     * Gets the partitioner for data distribution.
     * 
     * @return the partitioner, or null if forward partitioning is used
     */
    public IPartitioner<?> getPartitioner() {
        return partitioner;
    }
    
    /**
     * Sets the partitioner for data distribution.
     * 
     * @param partitioner the partitioner to use, or null for forward partitioning
     */
    public void setPartitioner(IPartitioner<?> partitioner) {
        this.partitioner = partitioner;
    }
    
    /**
     * Gets the human-readable name for this edge.
     * 
     * @return the edge name, or null if not set
     */
    public String getName() {
        return name;
    }
    
    /**
     * Sets the human-readable name for this edge.
     * 
     * @param name the edge name, or null to remove the name
     */
    public void setName(String name) {
        this.name = name;
    }
    
    @Override
    public String toString() {
        return "StreamEdge{" +
                "sourceId=" + sourceId +
                ", targetId=" + targetId +
                ", outputTag=" + outputTag +
                ", partitioner=" + partitioner +
                ", name='" + name + '\'' +
                '}';
    }
}
