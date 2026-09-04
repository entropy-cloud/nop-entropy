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
 * <p>A {@code TwoPhaseCommitSinkFunction} endpoint declares {@link #PARALLEL}: parallel
 * subtasks hold independent UDF copies ({@code copyForSubtask(int)}) whose commit keys /
 * output paths carry the subtask identity, so exactly-once holds at
 * {@code parallelism > 1}. Subclasses that do not override {@code copyForSubtask(int)}
 * fail fast at deploy time (base-class default, checkpoint-design.md §6.4.3).
 */
public enum ConnectorParallelism {

    /** Endpoint supports parallel subtasks (split assignment / per-subtask partitioning). */
    PARALLEL,

    /** Endpoint is a single-instance endpoint (no parallel subtask semantics). */
    SINGLE_INSTANCE
}
