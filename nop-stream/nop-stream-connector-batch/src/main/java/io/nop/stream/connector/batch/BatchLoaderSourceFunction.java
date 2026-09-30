/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.connector.batch;

import java.util.List;

import io.nop.batch.core.IBatchChunkContext;
import io.nop.batch.core.IBatchLoaderProvider;
import io.nop.batch.core.IBatchTaskContext;
import io.nop.batch.core.impl.BatchTaskContextImpl;

import io.nop.stream.core.common.functions.source.ParallelismCheckable;
import io.nop.stream.core.common.functions.source.ReplayableSourceFunction;
import io.nop.stream.core.common.functions.source.SourceConsistencyCapability;
import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.connector.ConnectivityCheckable;
import io.nop.stream.core.exceptions.StreamException;

import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_ACTUAL_VALUE;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_ARG_NAME;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DECLARED_VALUE;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_TYPE_NAME;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_CONNECTOR_CAPABILITY_MISMATCH;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_INVALID_ARG;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_NULL_ARG;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_STATE_ERROR;

/**
 * Adapts nop-batch's {@link IBatchLoaderProvider} to nop-stream's {@link SourceFunction}.
 * <p>
 * Calls {@code loader.load(batchSize, chunkContext)} in a loop, emitting each record
 * individually to the stream. When the loader returns an empty list, the source completes.
 *
 * <p><strong>Recovery semantics (AR-13, plan 2026-09-04-1326-3 D1 option a)</strong>:
 * the offset follows the next-index convention aligned with
 * {@code CollectionReplayableSource} — {@link #getCurrentOffset()} returns the number of
 * records already emitted (0 before any emission). After a checkpoint restore, the operator
 * calls {@link #seek(long)} with that count and the next {@link #run(SourceContext)}
 * <em>client-side skips</em> the first {@code offset} records of the loader's traversal
 * (consumed but not emitted), then resumes emission — no full re-emission, and the
 * reported offset never over-counts.
 *
 * <p><strong>Determinism precondition</strong>: client-side skip is only correct when the
 * loader's traversal order is deterministic across restarts (e.g. a query with a stable
 * ORDER BY). If the loader is exhausted before {@code offset} records have been skipped
 * (non-deterministic traversal, or the dataset shrank since the checkpoint),
 * {@link #run(SourceContext)} fails fast with a typed error instead of silently emitting
 * from a wrong position.
 *
 * <p><strong>Single-writer constraint (R5-CON-05)</strong>: the function has NO
 * per-subtask sharding — every subtask copy would traverse the SAME loader and emit the
 * full dataset, so {@code parallelism > 1} would deliver N stable duplicate copies
 * downstream. The function implements {@link ParallelismCheckable} and the execution
 * plan builders call {@link #validateParallelism(int)} at deployment time, so an
 * over-parallel configuration fails fast instead of duplicating data.
 */
public class BatchLoaderSourceFunction<S> implements ReplayableSourceFunction<S>, ConnectivityCheckable,
        ParallelismCheckable {

    private static final long serialVersionUID = 1L;

    private final IBatchLoaderProvider<S> loaderProvider;
    private final int batchSize;

    private volatile boolean running = true;

    /**
     * AR-10: {@code volatile} — {@link #seek(long)} and {@link #getCurrentOffset()} run on
     * the operator/checkpoint threads while {@link #run(SourceContext)} advances the
     * counter on the task thread; without the modifier the checkpoint snapshot could
     * observe a stale value indefinitely.
     */
    private volatile long currentOffset = 0;

    public BatchLoaderSourceFunction(IBatchLoaderProvider<S> loaderProvider) {
        this(loaderProvider, 1);
    }

    public BatchLoaderSourceFunction(IBatchLoaderProvider<S> loaderProvider, int batchSize) {
        if (loaderProvider == null) {
            throw new StreamException(ERR_STREAM_NULL_ARG).param(ARG_ARG_NAME, "loaderProvider");
        }
        if (batchSize < 1) {
            throw new StreamException(ERR_STREAM_INVALID_ARG).param(ARG_ARG_NAME, "batchSize")
                    .param(ARG_DETAIL, "must be at least 1");
        }
        this.loaderProvider = loaderProvider;
        this.batchSize = batchSize;
    }

    @Override
    public void run(SourceContext<S> ctx) throws Exception {
        // Reset lifecycle state: a region restart reuses this instance (the rebuilt
        // operator chain shares the source function) after cancel() set running=false.
        // Without this reset the loop below exits immediately and the source is
        // silently treated as EOS (data flow stall).
        this.running = true;
        IBatchTaskContext taskContext = new BatchTaskContextImpl();
        IBatchLoaderProvider.IBatchLoader<S> loader = loaderProvider.setup(taskContext);
        try {
            IBatchChunkContext chunkContext = taskContext.newChunkContext();

            // AR-13: client-side skip of already-emitted records (next-index convention).
            // Records are consumed from the loader but NOT emitted, so one recovery =
            // one continuation, not a full re-emission with an over-counted offset.
            long skipRemaining = Math.max(0L, currentOffset);
            long skipped = 0L;

            while (running) {
                List<S> batch = loader.load(batchSize, chunkContext);
                if (batch == null || batch.isEmpty()) {
                    if (skipRemaining > 0) {
                        // The checkpoint claims more emitted records than the loader can
                        // traverse: loader order is not deterministic across restarts, or
                        // the dataset shrank. Fail fast instead of silently re-emitting
                        // from a wrong position.
                        throw new StreamException(ERR_STREAM_STATE_ERROR)
                                .param(ARG_DETAIL, "BatchLoaderSourceFunction skip shortfall: loader exhausted "
                                        + "after " + skipped + " of " + currentOffset
                                        + " records to skip — loader traversal order is not deterministic "
                                        + "across restarts, or the dataset shrank since the checkpoint");
                    }
                    break;
                }
                for (S item : batch) {
                    if (!running) {
                        return;
                    }
                    if (skipRemaining > 0) {
                        skipRemaining--;
                        skipped++;
                        continue;
                    }
                    ctx.collect(item);
                    currentOffset++;
                }
            }
        } finally {
            if (loader instanceof AutoCloseable) {
                ((AutoCloseable) loader).close();
            }
        }
    }

    @Override
    public void cancel() {
        running = false;
    }

    /**
     * Item 20 (P-REQ-13, D3 batch-loader row): pre-submit connectivity probe —
     * {@code loaderProvider.setup()} exercises the provider's connectivity (e.g. a
     * JDBC loader opens its connection) without consuming any batch record; the loader
     * is closed when {@code AutoCloseable} (D3-⑥ cleanup semantics).
     */
    @Override
    public void checkConnection() throws Exception {
        IBatchTaskContext taskContext = new BatchTaskContextImpl();
        IBatchLoaderProvider.IBatchLoader<S> loader = loaderProvider.setup(taskContext);
        if (loader == null) {
            throw new StreamException(ERR_STREAM_STATE_ERROR)
                    .param(ARG_DETAIL, "loaderProvider.setup() returned null loader; provider connectivity "
                            + "cannot be verified");
        }
        if (loader instanceof AutoCloseable closeable) {
            closeable.close();
        }
    }

    @Override
    public SourceConsistencyCapability getSourceConsistency() {
        return SourceConsistencyCapability.AT_LEAST_ONCE;
    }

    /**
     * R5-CON-05: fail fast at deployment when the vertex is configured with
     * {@code parallelism > 1}. The function traverses the SAME loader in every subtask
     * copy with no shard filter, so parallel deployment would deliver N stable duplicate
     * copies of the full dataset downstream. Called by the execution-plan builders
     * before any subtask is created.
     */
    @Override
    public void validateParallelism(int parallelism) {
        if (parallelism > 1) {
            throw new StreamException(ERR_STREAM_CONNECTOR_CAPABILITY_MISMATCH)
                    .param(ARG_TYPE_NAME, "batch-loader")
                    .param(ARG_DECLARED_VALUE, "parallelism=" + parallelism)
                    .param(ARG_ACTUAL_VALUE,
                            "single-writer implementation without per-subtask sharding — every "
                                    + "subtask copy would emit the full loader dataset (N-times "
                                    + "duplication); deploy with parallelism=1");
        }
    }

    @Override
    public long getCurrentOffset() {
        return currentOffset;
    }

    /**
     * AR-13: repositions the source so the next {@link #run(SourceContext)} skips the
     * first {@code offset} records of the loader traversal and resumes emission from
     * record {@code offset}. Negative offsets are rejected typed (never silently
     * clamped — the counter must not lie).
     */
    @Override
    public void seek(long offset) {
        if (offset < 0) {
            throw new StreamException(ERR_STREAM_INVALID_ARG)
                    .param(ARG_ARG_NAME, "offset")
                    .param(ARG_DETAIL, "offset must be non-negative, got: " + offset);
        }
        this.currentOffset = offset;
    }
}
