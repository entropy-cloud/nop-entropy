/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.execution;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.nop.stream.core.execution.materialization.IMaterializationPoint;
import io.nop.stream.core.execution.materialization.MaterializedElement;
import io.nop.stream.core.streamrecord.StreamElement;
import io.nop.stream.core.exceptions.StreamException;

import io.nop.stream.core.exceptions.NopStreamErrors;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_ARG_NAME;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_FROM_EPOCH;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_MATERIALIZE_POINT_NOT_ATTACHED;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_NULL_ARG;

/**
 * Consumer-side handle to a {@link ResultPartition}. Wraps a single partition
 * for reading stream elements from an upstream task.
 */
public class InputChannel {

    private final ResultPartition partition;

    public InputChannel(ResultPartition partition) {
        if (partition == null) {
            throw new StreamException(ERR_STREAM_NULL_ARG).param(ARG_ARG_NAME, "partition");
        }
        this.partition = partition;
    }

    /**
     * Reads the next element from the underlying partition (blocking).
     *
     * @return the next element, or null if end-of-stream
     * @throws InterruptedException if interrupted while waiting
     */
    public StreamElement read() throws InterruptedException {
        return partition.read();
    }

    /**
     * Reads with a timeout.
     *
     * @param timeout maximum wait time
     * @param unit    time unit
     * @return the next element, or null on timeout / end-of-stream
     * @throws InterruptedException if interrupted while waiting
     */
    public StreamElement read(long timeout, TimeUnit unit) throws InterruptedException {
        return partition.read(timeout, unit);
    }

    /**
     * Returns whether the upstream producer has finished.
     */
    public boolean isFinished() {
        return partition.isFinished();
    }

    public ResultPartition getPartition() {
        return partition;
    }

    /**
     * Stage 43 (unaligned checkpoint): captures (drains) the in-flight elements
     * currently buffered in this channel for inclusion in channel state.
     *
     * <p>Per {@code checkpoint-design.md} §2.11.2 the semantics depend on whether
     * this channel has already delivered its barrier:
     * <ul>
     *   <li>{@code barrierReceived=true} (aligned channel): the barrier has already
     *       been consumed from the queue, so the remaining buffered elements are
     *       the <em>post-barrier</em> records that arrived while waiting for other
     *       channels to align.</li>
     *   <li>{@code barrierReceived=false} (non-aligned channel): the barrier has
     *       not arrived yet, so all buffered elements are <em>pre-barrier</em>
     *       records that must be preserved for exactly-once.</li>
     * </ul>
     * In both cases the mechanical operation is identical — drain all currently
     * buffered elements — because the {@link InputGate} only calls this method at
     * the correct moment (after consuming any barrier that has arrived). The flag
     * is part of the contract for clarity and is recorded with the captured state.
     *
     * <p>Records are <em>moved</em> out of the buffer (drain), not copied.
     *
     * @param barrierReceived whether this channel has delivered its barrier
     * @return the drained in-flight elements (possibly empty); never null
     */
    public List<StreamElement> captureInFlightData(boolean barrierReceived) {
        return partition.drainBufferedElements();
    }

    /**
     * Stage 43 (unaligned checkpoint): injects (pre-pends) previously captured
     * in-flight elements back into this channel's buffer so they are processed
     * before any new upstream records on recovery replay. Used by the recovery
     * path ({@code GraphModelCheckpointExecutor.restoreChannelState}).
     *
     * <p>Elements are inserted ahead of existing buffered content so replay order
     * is preserved.
     *
     * @param elements the in-flight elements to replay (may be null/empty = no-op)
     */
    public void injectElements(List<StreamElement> elements) {
        if (elements == null || elements.isEmpty()) {
            return;
        }
        partition.injectFront(elements);
    }

    // ----------------------------------------------------------------------
    // Stage 44 successor 1 — materialization replay path (option B)
    // ----------------------------------------------------------------------

    /**
     * Stage 44 successor 1: returns the materialization bypass point attached to
     * the underlying {@link ResultPartition}, or {@code null} if this channel
     * operates in legacy (by-reference-only) mode.
     *
     * <p>This is the entry point for the consumer-side replay path: a supervision
     * loop (successor 3) checks this to decide whether replay is available for a
     * channel before activating it.
     *
     * @return the attached materialization point, or {@code null} if none
     */
    public IMaterializationPoint getMaterializationPoint() {
        return partition.getMaterializationPoint();
    }

    /**
     * Stage 44 successor 1: returns {@code true} if the underlying partition has
     * a materialization point attached (i.e. replay is available).
     */
    public boolean hasMaterializationPoint() {
        return partition.isMaterializationEnabled();
    }

    /**
     * Stage 44 successor 1: replays materialized elements from the attached
     * materialization point whose epoch is {@code >= fromEpoch}, in write order.
     *
     * <p>This is the raw replay capability — it returns the materialized elements
     * without disturbing the channel's in-flight buffer. Callers that want to
     * re-consume the materialized data through the normal {@link #read()} path
     * should use {@link #activateMaterializationReplay(long)} instead.
     *
     * <p>This method delivers the <em>capability</em> to read from the
     * materialization point; <em>when</em> replay is activated is the
     * responsibility of the supervision loop (successor plan 3), and
     * <em>which epoch to start from</em> is the responsibility of the
     * consistent-cut alignment protocol (successor plan 4).
     *
     * @param fromEpoch the inclusive lower-bound epoch (consistent-cut marker);
     *                  {@code <= 0} means "from the beginning"
     * @return the matching materialized elements in write order (never null;
     *         possibly empty if nothing matches)
     * @throws StreamException if no materialization point is attached
     *         (fail-fast; No-Silent-No-Op)
     */
    public List<MaterializedElement> replayMaterialized(long fromEpoch) {
        IMaterializationPoint point = partition.getMaterializationPoint();
        if (point == null) {
            throw new StreamException(ERR_STREAM_MATERIALIZE_POINT_NOT_ATTACHED)
                    .param(ARG_FROM_EPOCH, fromEpoch)
                    .param(ARG_DETAIL, "replayMaterialized requires a materialization-enabled edge");
        }
        return point.replay(fromEpoch);
    }

    /**
     * Stage 44 successor 1: activates the materialization replay path — reads
     * materialized elements (epoch {@code >= fromEpoch}) from the attached
     * materialization point and injects them at the <em>front</em> of the
     * channel's in-flight buffer, so subsequent {@link #read()} calls return the
     * replayed elements before any live upstream data.
     *
     * <p>This wires the replay capability into the normal consumption path:
     * after activation, the consumer's existing read loop naturally re-drives
     * the materialized content. The consistent-cut alignment protocol (which
     * {@code fromEpoch} to pass) is successor plan 4's responsibility; the
     * activation trigger (when to call this method) is successor plan 3's
     * responsibility. This method only delivers the mechanism.
     *
     * @param fromEpoch the inclusive lower-bound epoch; {@code <= 0} replays all
     * @return the number of materialized elements injected into the buffer
     * @throws StreamException if no materialization point is attached
     *         (fail-fast; No-Silent-No-Op)
     */
    public int activateMaterializationReplay(long fromEpoch) {
        IMaterializationPoint point = partition.getMaterializationPoint();
        if (point == null) {
            throw new StreamException(ERR_STREAM_MATERIALIZE_POINT_NOT_ATTACHED)
                    .param(ARG_FROM_EPOCH, fromEpoch)
                    .param(ARG_DETAIL, "activateMaterializationReplay requires a materialization-enabled edge");
        }
        List<MaterializedElement> materialized = point.replay(fromEpoch);
        if (materialized.isEmpty()) {
            return 0;
        }
        // Unwrap epoch-tagged elements back into plain StreamElements and inject
        // them ahead of any in-flight content, preserving write order.
        List<StreamElement> elements = new ArrayList<>(materialized.size());
        for (MaterializedElement me : materialized) {
            elements.add(me.getElement());
        }
        partition.injectFront(elements);
        return elements.size();
    }
}
