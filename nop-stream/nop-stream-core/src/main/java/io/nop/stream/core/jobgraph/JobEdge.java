/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.jobgraph;

import java.io.Serializable;

/**
 * Represents an edge in the job execution graph connecting vertices with partition type information.
 *
 * <p>A JobEdge represents a connection between two job vertices in the JobGraph DAG. It defines
 * how intermediate results flow from a source vertex to a target vertex, including the partition
 * type that determines the execution mode (pipelined vs blocking).
 *
 * <p>JobEdge is part of the optimized execution plan (JobGraph) that is generated from the
 * streaming topology (StreamGraph). While StreamEdge focuses on operator-level connections
 * and partitioning strategies, JobEdge focuses on execution-level connections and result
 * partition types.
 *
 * <p>Key differences from StreamEdge:
 * <ul>
 *   <li><b>ID Type</b>: Uses String IDs for vertices (JobVertex) vs Integer IDs (StreamNode)</li>
 *   <li><b>Partition Info</b>: Uses ResultPartitionType (execution mode) vs IPartitioner (data distribution)</li>
 *   <li><b>Immutability</b>: All fields are final (fully immutable) vs mixed immutability in StreamEdge</li>
 *   <li><b>Purpose</b>: Optimized execution plan vs streaming topology representation</li>
 * </ul>
 *
 * <p>The partition type determines the execution strategy:
 * <ul>
 *   <li><b>PIPELINED</b>: Streaming execution with low latency, producer and consumer run simultaneously</li>
 *   <li><b>PIPELINED_BOUNDED</b>: Pipelined with backpressure to prevent memory overflow</li>
 *   <li><b>BLOCKING</b>: Batch execution where producer completes before consumer starts</li>
 * </ul>
 *
 * <p>This class is fully immutable - all fields are final and set at construction time.
 * This ensures the execution plan cannot be modified after creation, which is important
 * for execution reliability and scheduling.
 *
 * @see JobGraph
 * @see JobVertex
 * @see ResultPartitionType
 */
public class JobEdge implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * ID of the source vertex (upstream producer) for this edge.
     *
     * <p>The source vertex produces intermediate results that are consumed by the target vertex.
     * Uses String ID to match JobVertex's String ID scheme, which provides more flexibility
     * for optimized execution graphs compared to Integer IDs in StreamNode.
     */
    private final String sourceVertex;

    /**
     * ID of the target vertex (downstream consumer) for this edge.
     *
     * <p>The target vertex consumes intermediate results produced by the source vertex.
     * Uses String ID to match JobVertex's String ID scheme.
     */
    private final String targetVertex;

    /**
     * The partition type that determines how intermediate results are produced and consumed.
     *
     * <p>This defines the execution mode between the source and target vertices:
     * <ul>
     *   <li>PIPELINED: Streaming execution with simultaneous producer/consumer</li>
     *   <li>PIPELINED_BOUNDED: Pipelined with backpressure support</li>
     *   <li>BLOCKING: Batch execution with materialized intermediate results</li>
     * </ul>
     *
     * <p>The partition type affects scheduling decisions, resource consumption, latency,
     * and failure recovery behavior.
     */
    private final ResultPartitionType partitionType;

    /**
     * Constructs a new JobEdge with the specified source vertex, target vertex, and partition type.
     *
     * <p>All parameters are required and must be non-null. This constructor validates all inputs
     * and throws IllegalArgumentException if any parameter is null.
     *
     * <p>This is the only constructor for JobEdge, enforcing full immutability. All fields
     * must be provided at construction time and cannot be changed afterward.
     *
     * @param sourceVertex ID of the upstream source vertex (must not be null)
     * @param targetVertex ID of the downstream target vertex (must not be null)
     * @param partitionType the partition type for intermediate results (must not be null)
     * @throws IllegalArgumentException if any parameter is null
     */
    public JobEdge(String sourceVertex, String targetVertex, ResultPartitionType partitionType) {
        if (sourceVertex == null) {
            throw new IllegalArgumentException("sourceVertex cannot be null");
        }
        if (targetVertex == null) {
            throw new IllegalArgumentException("targetVertex cannot be null");
        }
        if (partitionType == null) {
            throw new IllegalArgumentException("partitionType cannot be null");
        }

        this.sourceVertex = sourceVertex;
        this.targetVertex = targetVertex;
        this.partitionType = partitionType;
    }

    /**
     * Gets the ID of the source (upstream) vertex for this edge.
     *
     * <p>The source vertex produces intermediate results that flow through this edge
     * to the target vertex.
     *
     * @return the source vertex ID (never null)
     */
    public String getSourceVertex() {
        return sourceVertex;
    }

    /**
     * Gets the ID of the target (downstream) vertex for this edge.
     *
     * <p>The target vertex consumes intermediate results that flow through this edge
     * from the source vertex.
     *
     * @return the target vertex ID (never null)
     */
    public String getTargetVertex() {
        return targetVertex;
    }

    /**
     * Gets the partition type for intermediate results on this edge.
     *
     * <p>The partition type determines the execution mode:
     * <ul>
     *   <li>PIPELINED: Streaming execution with low latency</li>
     *   <li>PIPELINED_BOUNDED: Pipelined with backpressure</li>
     *   <li>BLOCKING: Batch execution with materialized results</li>
     * </ul>
     *
     * @return the partition type (never null)
     */
    public ResultPartitionType getPartitionType() {
        return partitionType;
    }

    /**
     * Returns a string representation of this JobEdge for debugging purposes.
     *
     * <p>The string includes all fields: sourceVertex, targetVertex, and partitionType.
     *
     * @return a string representation of this edge
     */
    @Override
    public String toString() {
        return "JobEdge{" +
                "sourceVertex='" + sourceVertex + '\'' +
                ", targetVertex='" + targetVertex + '\'' +
                ", partitionType=" + partitionType +
                '}';
    }
}
