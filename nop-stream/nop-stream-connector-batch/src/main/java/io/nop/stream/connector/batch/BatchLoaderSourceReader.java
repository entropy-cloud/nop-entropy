/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.connector.batch;

import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import io.nop.batch.core.IBatchChunkContext;
import io.nop.batch.core.IBatchLoaderProvider;
import io.nop.batch.core.IBatchTaskContext;
import io.nop.batch.core.impl.BatchTaskContextImpl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.source.SourceReader;
import io.nop.stream.core.source.SourceReaderContext;

import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_STATE_ERROR;

/**
 * WI2 (plan 2343 Phase 1): FLIP-27 reader of the batch-loader source — an incremental
 * re-shaping of the legacy {@code BatchLoaderSourceFunction} traversal (the legacy class
 * keeps its programmatic API; the connector factory produces this source).
 *
 * <p>Semantics preserved one-to-one from the legacy function:
 * <ul>
 *   <li><strong>AR-13 next-index offset</strong>: the offset counts records already
 *       emitted; restore positions the traversal client-side — records are consumed
 *       from the loader but NOT emitted until the restored offset is reached;</li>
 *   <li><strong>typed skip shortfall</strong>: if the loader is exhausted before the
 *       restored offset is skipped, the reader fails typed (non-deterministic traversal
 *       or shrunken dataset) instead of silently emitting from a wrong position;</li>
 *   <li><strong>one record per poll</strong>: {@link #pollNext()} emits at most one
 *       record per call so checkpoint barriers keep flowing through a long batch;</li>
 *   <li><strong>loader lifecycle</strong>: the loader is set up lazily on the first
 *       split assignment and closed on {@link #close()} when {@code AutoCloseable}.</li>
 * </ul>
 */
public class BatchLoaderSourceReader<S> implements SourceReader<S, BatchLoaderSplit> {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(BatchLoaderSourceReader.class);

    private final IBatchLoaderProvider<S> loaderProvider;
    private final int batchSize;

    private transient SourceReaderContext context;
    private transient IBatchLoaderProvider.IBatchLoader<S> loader;
    private transient IBatchChunkContext chunkContext;
    private transient Iterator<S> batchCursor;
    private volatile boolean finished;
    private volatile boolean closed;
    private volatile boolean started;

    /** next-index offset: records already emitted (checkpoint cursor). */
    private volatile long currentOffset;

    /** records still to consume-and-skip before emission resumes (restore positioning). */
    private long skipRemaining;

    private BatchLoaderSplit pendingSplit;

    /** guards the idle-path split request (one in-flight request, like FileSourceReader). */
    private boolean requestedAssignment;

    public BatchLoaderSourceReader(IBatchLoaderProvider<S> loaderProvider, int batchSize) {
        if (loaderProvider == null) {
            throw new IllegalArgumentException("loaderProvider is required");
        }
        if (batchSize < 1) {
            throw new IllegalArgumentException("batchSize must be at least 1, got: " + batchSize);
        }
        this.loaderProvider = loaderProvider;
        this.batchSize = batchSize;
    }

    @Override
    public void start() {
        started = true;
        // Deployment-time single-writer parity (legacy ParallelismCheckable): the
        // reader-side gate is the belt-and-braces second layer.
        if (context != null && context.getTotalParallelism() > 1) {
            throw BatchLoaderSource.singleWriterError(context.getTotalParallelism());
        }
        if (pendingSplit != null) {
            adoptSplit(pendingSplit);
            pendingSplit = null;
        }
    }

    @Override
    public void addSplits(List<BatchLoaderSplit> splits) {
        if (splits == null || splits.isEmpty()) {
            return;
        }
        if (!started) {
            // Pre-open assignment (flushPreOpenSplits ordering guarantees start() runs
            // before run(); buffers survive until then).
            pendingSplit = splits.get(splits.size() - 1);
            requestedAssignment = false;
            return;
        }
        // D-4: a second assignment after start would restart the traversal — refuse
        // typed instead of silently re-reading (no-silent-skip).
        throw BatchLoaderSource.secondAssignmentError(splits.size());
    }

    private void adoptSplit(BatchLoaderSplit split) {
        this.currentOffset = split.getOffset();
        this.skipRemaining = split.getOffset();
        IBatchTaskContext taskContext = new BatchTaskContextImpl();
        this.loader = loaderProvider.setup(taskContext);
        this.chunkContext = taskContext.newChunkContext();
        LOG.info("BatchLoaderSourceReader adopted the batch split at offset {}", currentOffset);
    }

    @Override
    public Optional<S> pollNext() throws Exception {
        if (finished || closed) {
            return Optional.empty();
        }
        if (pendingSplit != null) {
            // Defensive ordering: the engine flushes pre-open splits before start(), so
            // adoption normally happens there — a late arrival must still be adopted,
            // never silently ignored.
            adoptSplit(pendingSplit);
            pendingSplit = null;
        }
        if (loader == null) {
            // No split adopted yet — ask the enumerator once and stay idle (the initial
            // addReader registration already triggers the assignment; this is the
            // fallback path, guarded against per-poll spam like FileSourceReader).
            if (!requestedAssignment && context != null && context.getAssignmentProxy() != null) {
                requestedAssignment = true;
                context.getAssignmentProxy().requestSplits(context.getSubtaskIndex(),
                        Optional.empty());
            }
            return Optional.empty();
        }
        S next = nextRecord();
        if (next == null) {
            finished = true;
            return Optional.empty();
        }
        currentOffset++;
        return Optional.of(next);
    }

    /**
     * Advances the loader traversal by one emit-able record; {@code null} means natural
     * EOS (empty batch with the skip obligation settled). Mirrors the legacy run() loop:
     * skipped records are consumed from the loader but not emitted, and a shortfall
     * (loader exhausted while skips remain) fails typed.
     */
    private S nextRecord() throws Exception {
        for (;;) {
            if (batchCursor != null && batchCursor.hasNext()) {
                S item = batchCursor.next();
                if (skipRemaining > 0) {
                    skipRemaining--;
                    continue;
                }
                return item;
            }
            List<S> batch = loader.load(batchSize, chunkContext);
            if (batch == null || batch.isEmpty()) {
                if (skipRemaining > 0) {
                    // AR-13: the checkpoint claims more emitted records than the loader
                    // can traverse — non-deterministic traversal or shrunken dataset.
                    throw BatchLoaderSource.skipShortfallError(skippedTotal(), currentOffset);
                }
                return null;
            }
            batchCursor = batch.iterator();
        }
    }

    private long skippedTotal() {
        // records skipped so far this run = restored offset - remaining skips
        return currentOffset - skipRemaining;
    }

    @Override
    public boolean isFinished() {
        return finished || closed;
    }

    @Override
    public List<BatchLoaderSplit> snapshotState(long checkpointId) {
        if (loader == null) {
            // Nothing adopted yet — no traversal state to checkpoint.
            return java.util.Collections.emptyList();
        }
        return java.util.Collections.singletonList(new BatchLoaderSplit(currentOffset));
    }

    @Override
    public void restoreState(List<BatchLoaderSplit> splits) {
        if (splits == null || splits.isEmpty()) {
            return;
        }
        if (loader != null) {
            // Restore replays only before the traversal starts; a post-start restore
            // would silently rewind the live cursor — refuse typed (no-silent-skip).
            throw BatchLoaderSource.postStartRestoreError();
        }
        BatchLoaderSplit split = splits.get(splits.size() - 1);
        this.currentOffset = split.getOffset();
        this.skipRemaining = split.getOffset();
        // The restored cursor needs the loader adopted at start() — route through the
        // same pending-split path as a fresh assignment (start() calls adoptSplit).
        this.pendingSplit = new BatchLoaderSplit(split.getOffset());
    }

    @Override
    public void close() throws Exception {
        closed = true;
        finished = true;
        try {
            if (loader instanceof AutoCloseable) {
                ((AutoCloseable) loader).close();
            }
        } catch (Exception e) {
            // CRA 8.44 R4-04 W-05 face③ (adjudication C.5): close() is the terminal
            // cleanup face — a loader close failure must surface typed (leak visible,
            // recovery path engaged), never WARN-swallowed (the pre-fix shape closed
            // "normally" while the loader's resources leaked silently). Same family
            // discipline as BatchConsumerSinkFunction.close flush failures. The loader
            // reference is nulled BEFORE the failure propagates so the terminal object
            // state stays consistent.
            loader = null;
            throw new StreamException(ERR_STREAM_STATE_ERROR, e)
                    .param(ARG_DETAIL, "Failed to close the batch loader during reader close; "
                            + "loader resources may leak");
        }
        loader = null;
    }

    /** Test visibility: the traversal cursor (next-index convention). */
    public long getCurrentOffsetForTest() {
        return currentOffset;
    }
}
