/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.source;

import io.nop.stream.core.common.eventtime.TimestampAssigner;
import io.nop.stream.core.common.functions.source.ParallelismCheckable;
import io.nop.stream.core.common.functions.source.ReplayableSourceFunction;
import io.nop.stream.core.common.functions.source.SourceConsistencyCapability;
import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.checkpoint.OperatorSnapshotResult;
import io.nop.stream.core.checkpoint.TaskStateSnapshot;
import io.nop.stream.core.connector.ConnectivityCheckable;
import io.nop.stream.core.exceptions.StreamException;

import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_ARG_NAME;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_CONNECTOR_CAPABILITY_MISMATCH;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_INVALID_ARG;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_NULL_ARG;

/**
 * WI10 (plan 2334): wraps a {@link ReplayableSourceFunction} and filters its replay
 * to an event-time range {@code [from, to)} — the RECALCULATE job shape (replay a
 * bounded time range and re-write results through an idempotent upsert sink).
 *
 * <p><strong>Emission contract</strong>: the wrapper intercepts BOTH emission paths of
 * {@link SourceFunction.SourceContext}. A record arriving through {@code collect(element)}
 * (no timestamp) has its event time extracted via the injected {@link TimestampAssigner};
 * in-range records are re-emitted through {@code collectWithTimestamp(element, ts)} so
 * downstream event-time windows see the timestamp without an extra assignment step.
 * Records arriving through {@code collectWithTimestamp(element, ts)} are passed through
 * unchanged when {@code ts} is in range. Out-of-range records are dropped (never
 * re-emitted without a timestamp — a silent range violation would corrupt the recompute).
 *
 * <p><strong>Delegation contract</strong>: offset semantics stay with the delegate —
 * {@code seek}/{@code getCurrentOffset}/{@code snapshotState}/{@code initializeState} all
 * forward unchanged. Capability faces forward too, so wrapping cannot silently strip
 * deployment-time protections: {@link #getSourceConsistency()} returns the delegate's
 * declaration; {@link ParallelismCheckable} forwards to a checkable delegate (a wrapped
 * batch-loader keeps its single-writer {@code parallelism=1} guard); a delegate without
 * {@link ConnectivityCheckable} has no probe contract, so {@link #checkConnection()} fails
 * typed instead of faking a pass (No-Silent-No-Op).
 *
 * <p><strong>RECALCULATE jobs are fresh bounded jobs</strong>: submit with a NEW jobId and
 * a fresh checkpoint storage directory — resubmitting the same jobId auto-restores the
 * seek'ed offset and replays nothing. See the owner-doc cookbook.
 *
 * <p>Interaction with WI9 SKIP_GAP: the suspend-data-policy dispatch inspects the concrete
 * source type, so a wrapped source is typed-rejected for SKIP_GAP. RECALCULATE jobs are
 * fresh bounded runs and normally never hit that path (documented in the cookbook).
 */
public class TimeRangeReplayableSource<T> implements ReplayableSourceFunction<T>, ParallelismCheckable,
        ConnectivityCheckable {

    private static final long serialVersionUID = 1L;

    private final ReplayableSourceFunction<T> delegate;
    private final TimestampAssigner<T> assigner;
    private final long rangeFrom;
    private final long rangeTo;

    /**
     * @param delegate the replayable source to wrap; anything that is not a
     *                 {@link ReplayableSourceFunction} is rejected typed — the wrapper
     *                 cannot offer range replay over a source with no seek semantics
     * @param assigner extracts the event time of records emitted through the bare
     *                 {@code collect} path
     * @param from     inclusive lower event-time bound
     * @param to       exclusive upper event-time bound; must be strictly greater than
     *                 {@code from}
     */
    public TimeRangeReplayableSource(SourceFunction<T> delegate, TimestampAssigner<T> assigner,
                                     long from, long to) {
        if (delegate == null) {
            throw new StreamException(ERR_STREAM_NULL_ARG).param(ARG_ARG_NAME, "delegate");
        }
        if (assigner == null) {
            throw new StreamException(ERR_STREAM_NULL_ARG).param(ARG_ARG_NAME, "assigner");
        }
        if (!(delegate instanceof ReplayableSourceFunction<?>)) {
            throw new StreamException(ERR_STREAM_INVALID_ARG)
                    .param(ARG_ARG_NAME, "delegate")
                    .param(ARG_DETAIL, "time-range replay requires a ReplayableSourceFunction, got: "
                            + delegate.getClass().getName()
                            + " — this source has no seek/offset semantics and cannot be range-replayed");
        }
        if (to <= from) {
            throw new StreamException(ERR_STREAM_INVALID_ARG)
                    .param(ARG_ARG_NAME, "range")
                    .param(ARG_DETAIL, "range [from, to) must be non-empty, got from=" + from + ", to=" + to);
        }
        this.delegate = (ReplayableSourceFunction<T>) delegate;
        this.assigner = assigner;
        this.rangeFrom = from;
        this.rangeTo = to;
    }

    @Override
    public void run(SourceContext<T> ctx) throws Exception {
        delegate.run(new RangeFilterContext(ctx));
    }

    @Override
    public void cancel() {
        delegate.cancel();
    }

    @Override
    public SourceConsistencyCapability getSourceConsistency() {
        return delegate.getSourceConsistency();
    }

    @Override
    public void seek(long offset) {
        delegate.seek(offset);
    }

    @Override
    public long getCurrentOffset() {
        return delegate.getCurrentOffset();
    }

    @Override
    public OperatorSnapshotResult snapshotState(long checkpointId) throws Exception {
        return delegate.snapshotState(checkpointId);
    }

    @Override
    public void initializeState(TaskStateSnapshot state) throws Exception {
        delegate.initializeState(state);
    }

    /**
     * Forwards to a checkable delegate so a wrapped batch-loader keeps its
     * single-writer guard at deployment time. A delegate without a parallelism
     * constraint imposes none here either (same as running it unwrapped).
     */
    @Override
    public void validateParallelism(int parallelism) {
        if (delegate instanceof ParallelismCheckable) {
            ((ParallelismCheckable) delegate).validateParallelism(parallelism);
        }
    }

    /**
     * Forwards to a checkable delegate (e.g. batch-loader). A delegate without a
     * probe contract cannot be verified by this composite — fail typed rather than
     * fake a pass.
     */
    @Override
    public void checkConnection() throws Exception {
        if (delegate instanceof ConnectivityCheckable) {
            ((ConnectivityCheckable) delegate).checkConnection();
            return;
        }
        throw new StreamException(ERR_STREAM_CONNECTOR_CAPABILITY_MISMATCH)
                .param(ARG_DETAIL, "TimeRangeReplayableSource delegate "
                        + delegate.getClass().getName()
                        + " does not implement ConnectivityCheckable — the composite has no "
                        + "connectivity probe contract for this source family");
    }

    private boolean inRange(long ts) {
        return ts >= rangeFrom && ts < rangeTo;
    }

    private class RangeFilterContext implements SourceContext<T> {
        private final SourceContext<T> inner;

        RangeFilterContext(SourceContext<T> inner) {
            this.inner = inner;
        }

        @Override
        public void collect(T element) {
            long ts = assigner.extractTimestamp(element, TimestampAssigner.NO_TIMESTAMP);
            if (inRange(ts)) {
                inner.collectWithTimestamp(element, ts);
            }
        }

        @Override
        public void collectWithTimestamp(T element, long timestamp) {
            if (inRange(timestamp)) {
                inner.collectWithTimestamp(element, timestamp);
            }
        }

        @Override
        public void emitWatermark(long mark) {
            inner.emitWatermark(mark);
        }

        @Override
        public void markAsTemporarilyIdle() {
            inner.markAsTemporarilyIdle();
        }

        @Override
        public long getProcessingTime() {
            return inner.getProcessingTime();
        }

        @Override
        public boolean isCancelled() {
            return inner.isCancelled();
        }
    }
}
