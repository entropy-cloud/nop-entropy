/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.connector.registry;

/**
 * Parallelism capability declared by a connector factory. Mirrors the parallelism column
 * of the connector capability matrix ({@code docs-for-ai/03-modules/nop-stream-connectors.md}).
 *
 * <p>{@link #PLANNING_GATE_PARALLELISM_1} must be declared if and only if the constructed
 * sink endpoint is a {@code TwoPhaseCommitSinkFunction} — the same key the
 * {@code StreamGraphGenerator} planning-time parallelism gate checks (single fact source:
 * the descriptor never invents a second gate logic).
 */
public enum ConnectorParallelism {

    /** Endpoint supports parallel subtasks (split assignment / per-subtask partitioning). */
    PARALLEL,

    /** Endpoint is a single-instance endpoint (no parallel subtask semantics). */
    SINGLE_INSTANCE,

    /**
     * Planning-time gate: exactly-once output is only proven at parallelism 1; effective
     * parallelism &gt; 1 is rejected by {@code StreamGraphGenerator} with
     * {@code ERR_STREAM_2PC_SINK_PARALLELISM_NOT_SUPPORTED}.
     */
    PLANNING_GATE_PARALLELISM_1
}
