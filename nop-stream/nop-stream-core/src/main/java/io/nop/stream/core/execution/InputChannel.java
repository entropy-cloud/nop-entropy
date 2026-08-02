/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.execution;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.nop.stream.core.streamrecord.StreamElement;
import io.nop.stream.core.exceptions.StreamException;

import io.nop.stream.core.exceptions.NopStreamErrors;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_ARG_NAME;
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
}
