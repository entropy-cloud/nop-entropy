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

import io.nop.stream.core.exceptions.NopStreamErrors;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_ARG_NAME;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_NULL_ARG;

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

    /**
     * Stage 44 successor 1 (materialization point mechanism, option B): when
     * {@code true}, this edge is a region-boundary materialization edge — the
     * producer side dual-writes every stream element into the main in-flight
     * queue <em>and</em> into an attached {@code IMaterializationPoint} bypass
     * (epoch-tagged), and the consumer side can replay the materialized content
     * on recovery. Default {@code false} (opt-in; zero regression for existing
     * jobs).
     *
     * <p>This marker is the carrier for successor plan 2's region decomposition:
     * materialization-enabled edges are region cut points; non-enabled edges
     * connect vertices inside the same pipelined region.
     *
     * <p>This marker does <em>not</em> change {@link ResultPartitionType}: the
     * default {@code determinePartitionType} path still returns
     * {@code PIPELINED}/{@code PIPELINED_BOUNDED}. Materialization is an opt-in
     * <em>additional</em> bypass, not a partition-type enum change.
     */
    private boolean materializationEnabled = false;

    public JobEdge(String sourceVertex, String targetVertex, ResultPartitionType partitionType) {
        this(sourceVertex, targetVertex, partitionType, null);
    }

    public JobEdge(String sourceVertex, String targetVertex, ResultPartitionType partitionType,
                   IPartitioner<?> partitioner) {
        if (sourceVertex == null) {
            throw new StreamException(ERR_STREAM_NULL_ARG).param(ARG_ARG_NAME, "sourceVertex");
        }
        if (targetVertex == null) {
            throw new StreamException(ERR_STREAM_NULL_ARG).param(ARG_ARG_NAME, "targetVertex");
        }
        if (partitionType == null) {
            throw new StreamException(ERR_STREAM_NULL_ARG).param(ARG_ARG_NAME, "partitionType");
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
     * (e.g., BLOCKING_QUEUE) and associated parameters
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
     * Stage 44 successor 1: returns whether this edge is a materialization point
     * edge (region-boundary bypass enabled). Default {@code false} (opt-in).
     *
     * @return {@code true} if the producer dual-writes into a materialization
     *         bypass point and the consumer can replay from it
     */
    public boolean isMaterializationEnabled() {
        return materializationEnabled;
    }

    /**
     * Stage 44 successor 1: explicitly marks this edge as a materialization point
     * edge (region-boundary bypass). When {@code true},
     * {@code GraphExecutionPlan.build(...)} attaches an
     * {@code IMaterializationPoint} to every {@code ResultPartition} in this
     * edge's partition matrix.
     *
     * <p>This marker does not depend on automatic region identification
     * (successor plan 2): manual opt-in is sufficient to enable materialization.
     *
     * @param materializationEnabled {@code true} to enable dual-write bypass +
     *                               consumer replay; {@code false} to disable
     */
    public void setMaterializationEnabled(boolean materializationEnabled) {
        this.materializationEnabled = materializationEnabled;
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
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof JobEdge)) return false;
        JobEdge jobEdge = (JobEdge) o;
        return sourceVertex.equals(jobEdge.sourceVertex)
                && targetVertex.equals(jobEdge.targetVertex)
                && partitionType == jobEdge.partitionType;
    }

    @Override
    public int hashCode() {
        int result = sourceVertex.hashCode();
        result = 31 * result + targetVertex.hashCode();
        result = 31 * result + partitionType.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "JobEdge{" +
                "sourceVertex='" + sourceVertex + '\'' +
                ", targetVertex='" + targetVertex + '\'' +
                ", partitionType=" + partitionType +
                ", partitioner=" + partitioner +
                ", edgeConfig=" + edgeConfig +
                ", materializationEnabled=" + materializationEnabled +
                '}';
    }
}
