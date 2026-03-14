/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.jobgraph;

/**
 * Defines the type of result partition for intermediate results in the execution graph.
 *
 * <p>ResultPartitionType determines how intermediate results are produced and consumed
 * between job vertices in the JobGraph. This affects the execution mode and data exchange
 * strategy between operators.
 *
 * <p>The partition type influences:
 * <ul>
 *   <li>Pipeline vs blocking execution</li>
 *   <li>Resource consumption (memory vs disk)</li>
 *   <li>Latency vs throughput tradeoffs</li>
 *   <li>Failure recovery behavior</li>
 * </ul>
 *
 * <p>Common partition types:
 * <ul>
 *   <li><b>PIPELINED</b>: Streaming mode where producer and consumer run simultaneously,
 *       results are pipelined with low latency but require active consumers</li>
 *   <li><b>PIPELINED_BOUNDED</b>: Similar to PIPELINED but with bounded buffer size,
 *       provides backpressure to prevent memory overflow</li>
 *   <li><b>BLOCKING</b>: Batch mode where producer completes before consumer starts,
 *       results are materialized to disk/memory for reliability but with higher latency</li>
 * </ul>
 *
 * @see JobEdge
 * @see JobVertex
 */
public enum ResultPartitionType {

    /**
     * Pipelined execution with unbounded buffers.
     *
     * <p>Producer and consumer tasks run simultaneously. Results are streamed
     * directly from producer to consumer without materialization. This provides
     * the lowest latency but requires active consumers to avoid resource exhaustion.
     *
     * <p>Use case: Streaming jobs with continuous data flow.
     */
    PIPELINED(true, false),

    /**
     * Pipelined execution with bounded buffers.
     *
     * <p>Similar to PIPELINED but with bounded buffer size. When buffers are full,
     * the producer is blocked (backpressure), preventing memory overflow.
     *
     * <p>Use case: Streaming jobs with backpressure support for stability.
     */
    PIPELINED_BOUNDED(true, true),

    /**
     * Blocking execution with materialized results.
     *
     * <p>Producer task completes and materializes all results before consumer task starts.
     * Results are stored persistently (memory or disk), providing reliability and
     * the ability to retry consumers without re-executing producers.
     *
     * <p>Use case: Batch jobs or operations requiring materialized intermediate results.
     */
    BLOCKING(false, true);

    /**
     * Whether this partition type supports pipelined execution.
     */
    private final boolean pipelined;

    /**
     * Whether this partition type has bounded resource consumption.
     */
    private final boolean bounded;

    /**
     * Constructs a result partition type with the specified characteristics.
     *
     * @param pipelined true if producer and consumer run simultaneously
     * @param bounded true if resource consumption is bounded (backpressure or materialization)
     */
    ResultPartitionType(boolean pipelined, boolean bounded) {
        this.pipelined = pipelined;
        this.bounded = bounded;
    }

    /**
     * Returns whether this partition type supports pipelined execution.
     *
     * <p>Pipelined execution means producer and consumer tasks run simultaneously,
     * with results streamed directly from producer to consumer.
     *
     * @return true if pipelined execution is supported, false for blocking execution
     */
    public boolean isPipelined() {
        return pipelined;
    }

    /**
     * Returns whether this partition type has bounded resource consumption.
     *
     * <p>Bounded consumption means either:
     * <ul>
     *   <li>Pipelined with backpressure (buffers are bounded)</li>
     *   <li>Blocking with materialization (results are persisted)</li>
     * </ul>
     *
     * @return true if resource consumption is bounded, false otherwise
     */
    public boolean isBounded() {
        return bounded;
    }

    /**
     * Returns whether this partition type supports blocking execution.
     *
     * <p>Blocking execution means the producer completes before the consumer starts.
     *
     * @return true if blocking execution is supported, false for pipelined only
     */
    public boolean isBlocking() {
        return !pipelined;
    }
}
