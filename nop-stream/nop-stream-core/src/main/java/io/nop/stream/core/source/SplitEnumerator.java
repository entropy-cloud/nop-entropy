/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.source;

import java.io.Serializable;
import java.util.List;
import java.util.Optional;

/**
 * Coordinator-side enumerator of a FLIP-27 style split-based source. There is exactly one
 * enumerator per source vertex; it runs on the {@code JobCoordinator} (Stage 49 D7 —
 * v1 hardwires the enumerator to {@code JobCoordinator}/{@code CheckpointCoordinator},
 * without introducing a generic {@code OperatorCoordinator} abstraction).
 *
 * <p>The enumerator owns the global view of split state: which splits have been discovered,
 * which are still unassigned, which are assigned-but-unfinished, which are finished, and
 * the discovery cursor (see {@code checkpoint-design.md} §5.3 — the authoritative
 * 6-state decomposition). Its state is checkpointed into
 * {@code EpochManifest.sourceEnumeratorSnapshots} keyed by source vertex id.
 *
 * <p>Lifecycle: {@link #start()} (deploy-time) or {@link #start()} after
 * {@link #restoreState(Object)} (restore-time) → zero or more rounds of
 * {@link #handleSplitRequest(int, Optional)} / {@link #addReader(int)} /
 * {@link #snapshotState(long)} → {@link #close()}.
 *
 * @param <T>      the split type
 * @param <StateT> the enumerator-state type; must round-trip through
 *                 {@link Source#getEnumeratorStateSerializer()}
 */
public interface SplitEnumerator<T extends SourceSplit, StateT> extends Serializable, AutoCloseable {

    /**
     * Called once after the enumerator is created (fresh start) or after
     * {@link #restoreState(Object)} (recovery). The enumerator performs its initial split
     * discovery here and may push initial assignments through the context.
     *
     * @param context the enumerator context; non-null
     */
    void start(SplitEnumeratorContext<T> context) throws Exception;

    /**
     * Called by the framework when subtask {@code subtaskIndex} has announced readiness
     * (its reader has registered and is willing to receive splits). The enumerator may
     * proactively assign splits to that subtask here.
     */
    void addReader(int subtaskIndex) throws Exception;

    /**
     * Called when subtask {@code subtaskIndex} requests more splits (pull model, Stage 49
     * D3/D4). The enumerator may assign zero or more splits in response, via the context's
     * delivery service.
     *
     * @param subtaskIndex the requesting subtask
     * @param reason       absent when this is a normal "I'm idle" request; present with a
     *                     failure cause if the reader is requesting because a prior split failed
     */
    void handleSplitRequest(int subtaskIndex, Optional<Throwable> reason) throws Exception;

    /**
     * Snapshots the enumerator state for checkpoint {@code checkpointId}. The returned
     * value is serialized via {@link Source#getEnumeratorStateSerializer()} and written
     * into {@code EpochManifest.sourceEnumeratorSnapshots}.
     */
    StateT snapshotState(long checkpointId) throws Exception;

    /**
     * Restores the enumerator state from a checkpoint before {@link #start(SplitEnumeratorContext)}
     * is called on recovery.
     */
    void restoreState(StateT state) throws Exception;

    /** {@inheritDoc} */
    @Override
    void close() throws Exception;
}
