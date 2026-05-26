/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.jobgraph;

import java.io.Serializable;

import io.nop.commons.partition.IPartitioner;

import io.nop.stream.core.execution.flow.EdgeConfig;
import io.nop.stream.core.exceptions.StreamException;

/**
 * Represents an edge in the job execution graph connecting vertices with partition type information.
 *
 * <p>A JobEdge represents a connection between two job vertices in the JobGraph DAG. It defines
 * how intermediate results flow from a source vertex to a target vertex, including the partition
 * type that determines the execution mode (pipelined vs blocking) and an optional partitioner
 * for data distribution across parallel instances.
 *
 * <p>JobEdge is part of the optimized execution plan (JobGraph) that is generated from the
 * streaming topology (StreamGraph). While StreamEdge focuses on operator-level connections
 * and partitioning strategies, JobEdge focuses on execution-level connections and result
 * partition types.
 *
 * <p>The partitioner field is carried from StreamEdge through JobGraphGenerator to enable
 * RecordWriter to correctly route records to the appropriate downstream partition.
 *
 * @see JobGraph
 * @see JobVertex
 * @see ResultPartitionType
 * @see IPartitioner
 */
public class JobEdge implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String sourceVertex;
    private final String targetVertex;
    private final ResultPartitionType partitionType;
    private final IPartitioner<?> partitioner;
    private EdgeConfig edgeConfig;

    public JobEdge(String sourceVertex, String targetVertex, ResultPartitionType partitionType) {
        this(sourceVertex, targetVertex, partitionType, null);
    }

    public JobEdge(String sourceVertex, String targetVertex, ResultPartitionType partitionType,
                   IPartitioner<?> partitioner) {
        if (sourceVertex == null) {
            throw new StreamException("sourceVertex cannot be null");
        }
        if (targetVertex == null) {
            throw new StreamException("targetVertex cannot be null");
        }
        if (partitionType == null) {
            throw new StreamException("partitionType cannot be null");
        }

        this.sourceVertex = sourceVertex;
        this.targetVertex = targetVertex;
        this.partitionType = partitionType;
        this.partitioner = partitioner;
        this.edgeConfig = null;
    }

    /**
     * Gets the optional edge configuration for flow control.
     *
     * <p>When set, this configuration controls the flow control policy
     * (e.g., BLOCKING_QUEUE, CREDIT_BASED, ACK_WINDOW) and associated parameters
     * for data exchange on this edge.
     *
     * @return the edge configuration, or null if not set
     */
    public EdgeConfig getEdgeConfig() {
        return edgeConfig;
    }

    /**
     * Sets the edge configuration for flow control.
     *
     * @param edgeConfig the edge configuration (nullable)
     */
    public void setEdgeConfig(EdgeConfig edgeConfig) {
        this.edgeConfig = edgeConfig;
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

    public ResultPartitionType getPartitionType() {
        return partitionType;
    }

    public IPartitioner<?> getPartitioner() {
        return partitioner;
    }

    @Override
    public String toString() {
        return "JobEdge{" +
                "sourceVertex='" + sourceVertex + '\'' +
                ", targetVertex='" + targetVertex + '\'' +
                ", partitionType=" + partitionType +
                ", partitioner=" + partitioner +
                ", edgeConfig=" + edgeConfig +
                '}';
    }
}
