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
 * Reader of a FLIP-27 style split-based source, running on the task side (one instance per
 * parallel source subtask). A reader pulls records out of its currently-assigned splits via
 * {@link #pollNext()} and tracks per-split cursors so they survive checkpoint/restore.
 *
 * <p>Stage 49 D1 (FLIP-27): a reader consumes whole splits; it does not subdivide them.
 * A reader may hold zero, one, or several splits at a time and may request more via the
 * {@link SplitAssignmentProxy} once it has drained its current set.
 *
 * <p>Lifecycle: {@link #start()} → zero or more rounds of {@link #addSplits(List)} /
 * {@link #pollNext()} / {@link #snapshotState(long)} / {@link #restoreState(List)} →
 * {@link #close()}. Per-split cursor state lives in the reader; the enumerator's
 * coordinator-side state (which splits exist, who owns them, which are finished) lives in
 * {@link SplitEnumerator} and is checkpointed separately via
 * {@code EpochManifest.sourceEnumeratorSnapshots}.
 *
 * @param <OUT> the element type this reader emits
 * @param <T>   the split type this reader consumes
 */
public interface SourceReader<OUT, T extends SourceSplit> extends Serializable, AutoCloseable {

    /**
     * Called once before the first {@link #pollNext()} / {@link #addSplits(List)} call.
     * Readers typically register themselves with the enumerator via
     * {@link SourceReaderContext#getAssignmentProxy()} here.
     */
    void start();

    /**
     * Called by the framework to deliver split assignments from the enumerator. The reader
     * must adopt each split's cursor as-is (for a fresh split, the cursor is the start
     * position; for a restored split, the cursor is the last checkpointed position).
     *
     * @param splits splits to add; non-null, may be empty (no-op)
     */
    void addSplits(List<T> splits);

    /**
     * Pulls the next available record from the currently-held splits.
     *
     * @return {@link Optional#empty()} if no record is currently available but the source
     *         is not yet finished; a non-empty value if a record was produced; readers
     *         signal source exhaustion by returning empty on every subsequent poll AND
     *         having no remaining splits assigned.
     */
    Optional<OUT> pollNext() throws Exception;

    /**
     * Snapshots the per-split cursors of all currently-held splits for checkpoint
     * {@code checkpointId}. The returned list is written into the task's operator state
     * ({@code TaskEpochSnapshot}) and restored via {@link #restoreState(List)} on recovery.
     *
     * @return the current per-split cursors; non-null, may be empty if no splits assigned
     */
    List<T> snapshotState(long checkpointId) throws Exception;

    /**
     * Restores per-split cursors from a checkpoint. Called before {@link #start()} on
     * recovery, with the splits the reader previously snapshotted (or that were
     * reassigned to this subtask after a reshard). The reader must adopt each split's
     * cursor as its starting position.
     *
     * @param splits restored split cursors; non-null, may be empty
     */
    void restoreState(List<T> splits) throws Exception;

    /**
     * Notifies the reader that checkpoint {@code checkpointId} has been durably persisted.
     * Default no-op; readers that buffer external-side effects (e.g. acks) override.
     */
    default void notifyCheckpointComplete(long checkpointId) {
    }

    /**
     * Returns {@code true} when this reader has consumed all input it will ever receive and
     * no further calls to {@link #pollNext()} will produce a record.
     *
     * <p>Default {@code false} (the reader never self-terminates — appropriate for unbounded
     * sources). Bounded source readers override to return {@code true} once their assigned
     * splits are exhausted AND they have been told (or can infer) that no more splits will
     * arrive. The {@code SourceReaderOperator} main loop uses this to exit gracefully.
     */
    default boolean isFinished() {
        return false;
    }

    /** {@inheritDoc} */
    @Override
    void close() throws Exception;
}
