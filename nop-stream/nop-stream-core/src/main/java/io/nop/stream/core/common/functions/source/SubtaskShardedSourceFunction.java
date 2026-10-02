/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 */
package io.nop.stream.core.common.functions.source;

/**
 * Implemented by source functions that shard their data domain across parallel
 * subtasks with a deterministic routing function (cdc-design.md §3.3, layer B).
 *
 * <p>Contract:
 * <ul>
 *   <li>{@link #validateParallelism(int)} (from {@link ParallelismCheckable}) is invoked by
 *       the execution-plan builders on the <em>template</em> instance before any subtask copy
 *       exists; implementations must record the validated parallelism so later
 *       {@link #copyForSubtask(int)} copies can route deterministically.</li>
 *   <li>{@link #copyForSubtask(int)} produces an independent function copy carrying the
 *       subtask identity (routing shard + per-instance external identifiers). Copies must not
 *       share mutable state; the natural implementation is a serialization round-trip of the
 *       (serializable) function.</li>
 *   <li>Routing must be a pure function of (data key, totalParallelism) so every deployment
 *       and every restore computes the same shard assignment.</li>
 * </ul>
 *
 * <p>Functions that do NOT implement this interface keep the legacy semantics: the function
 * instance is shared across subtask copies of the wrapping {@code StreamSourceOperator}, and
 * only single-instance execution is sound (parallelism > 1 is either rejected via
 * {@link ParallelismCheckable} or produces duplicated data).
 *
 * @param <T> the record type emitted by the source
 */
public interface SubtaskShardedSourceFunction<T> extends SourceFunction<T>, ParallelismCheckable {

    /**
     * Produces the per-subtask copy for the parallel subtask identified by
     * {@code subtaskIndex} (0-based, stable across region restarts). The copy routes the
     * data shard computed from the total parallelism recorded at
     * {@link #validateParallelism(int)} time.
     *
     * @param subtaskIndex the runtime subtask/task index (0..totalParallelism-1)
     * @return an independent function copy bound to the given shard
     */
    SubtaskShardedSourceFunction<T> copyForSubtask(int subtaskIndex);
}
