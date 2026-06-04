/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.operators;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.nop.stream.core.checkpoint.CheckpointBarrier;
import io.nop.stream.core.checkpoint.OperatorSnapshotResult;
import io.nop.stream.core.checkpoint.StateSnapshotContext;
import io.nop.stream.core.checkpoint.TaskLocation;
import io.nop.stream.core.checkpoint.TaskStateSnapshot;
import io.nop.stream.core.common.functions.source.CheckpointedSourceFunction;
import io.nop.stream.core.common.functions.source.ReplayableSourceFunction;
import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.common.state.CheckpointListener;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.exceptions.StreamException;

import static io.nop.stream.core.exceptions.NopStreamErrors.*;

/**
 * A stream operator that wraps a {@link SourceFunction} and emits elements through the
 * operator chain by calling {@code output.collect()} from within a SourceContext wrapper.
 *
 * <p>This operator is the head of any pipeline: it has no input and produces output by
 * running the user-supplied source function.
 *
 * @param <OUT> The type of elements produced by this source
 */
public class StreamSourceOperator<OUT> extends AbstractStreamOperator<OUT> {

    private static final Logger LOG = LoggerFactory.getLogger(StreamSourceOperator.class);

    private static final long serialVersionUID = 1L;

    public static final String SOURCE_OFFSET_KEY = "source-offset";

    /**
     * Queue of pending barriers to be injected by the source reading thread.
     * Capacity 1: if a barrier is already queued, new triggers are rejected
     * (overlapping checkpoints are not allowed).
     */
    private final LinkedBlockingQueue<CheckpointBarrier> pendingBarriers = new LinkedBlockingQueue<>(1);

    private final SourceFunction<OUT> sourceFunction;

    private volatile boolean isRunning = true;

    /**
     * Set to true once sourceFunction.run() has returned.
     * After this point, offerBarrier() directly injects instead of queueing.
     */
    private volatile boolean finished = false;

    public StreamSourceOperator(SourceFunction<OUT> sourceFunction) {
        this.sourceFunction = sourceFunction;
    }

    /**
     * Non-blocking offer of a barrier into the pending queue.
     * Called by the scheduler/checkpoint thread (source-pull pattern).
     *
     * <p>If the source has already finished ({@code finished == true}), the barrier
     * is directly injected instead of being queued. This handles the case where
     * a checkpoint/savepoint is triggered after a finite source completes.
     *
     * @return true if the barrier was accepted, false if a barrier is already queued
     */
    public boolean offerBarrier(CheckpointBarrier barrier) {
        if (finished) {
            // Source has finished running; directly inject the barrier.
            try {
                injectBarrier(barrier);
            } catch (Exception e) {
                throw new StreamException(ERR_STREAM_BARRIER_INJECTION_FAILED, e).param(ARG_DETAIL, "after source finished");
            }
            return true;
        }
        boolean accepted = pendingBarriers.offer(barrier);
        if (!accepted) {
            LOG.warn("Barrier {} rejected: pending queue already has a barrier", barrier.getId());
        }
        return accepted;
    }

    /**
     * Returns true if there is a pending barrier waiting to be injected.
     */
    public boolean hasPendingBarrier() {
        return !pendingBarriers.isEmpty();
    }

    /**
     * Drains and returns a pending barrier from the queue, if any.
     * Useful for testing or when the source has already finished and
     * barriers need to be manually injected.
     */
    public CheckpointBarrier drainPendingBarrier() {
        return pendingBarriers.poll();
    }

    /**
     * Returns the wrapped source function.
     *
     * @return the source function
     */
    public SourceFunction<OUT> getSourceFunction() {
        return sourceFunction;
    }

    /**
     * Runs the source function, emitting elements through the operator chain.
     * The source function calls {@link SourceFunction.SourceContext#collect(Object)},
     * which we forward to {@code output.collect(new StreamRecord<>(element))}.
     *
     * @throws Exception if the source function fails
     */
    public void run() throws Exception {
        SourceFunction.SourceContext<OUT> ctx = new SourceFunction.SourceContext<OUT>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void collect(OUT element) {
                injectPendingBarrier();
                output.collect(new StreamRecord<>(element));
            }

            @Override
            public void collectWithTimestamp(OUT element, long timestamp) {
                injectPendingBarrier();
                output.collect(new StreamRecord<>(element, timestamp));
            }

            @Override
            public void emitWatermark(long mark) {
                output.emitWatermark(new io.nop.stream.core.streamrecord.watermark.Watermark(mark));
            }

            @Override
            public void markAsTemporarilyIdle() {
                output.emitWatermarkStatus(io.nop.stream.core.streamrecord.watermark.WatermarkStatus.IDLE);
            }

            @Override
            public long getProcessingTime() {
                return System.currentTimeMillis();
            }
        };

        isRunning = true;
        sourceFunction.run(ctx);
        // Mark finished BEFORE draining so that concurrent offerBarrier() calls
        // will either (a) see finished=true and inject directly, or (b) see
        // finished=false, queue the barrier, and we drain it here.
        finished = true;
        drainAndInjectPendingBarriers();
    }

    /**
     * Drains all barriers from the pending queue and injects them.
     * Called after the source function finishes to ensure any barriers
     * that were queued during the last collect() calls are processed.
     */
    private void drainAndInjectPendingBarriers() {
        CheckpointBarrier barrier;
        while ((barrier = pendingBarriers.poll()) != null) {
            try {
                injectBarrier(barrier);
            } catch (Exception e) {
                throw new StreamException(ERR_STREAM_BARRIER_INJECTION_FAILED, e).param(ARG_DETAIL, "pending barrier after source finished");
            }
        }
    }

    private void injectPendingBarrier() {
        CheckpointBarrier barrier = pendingBarriers.poll();
        if (barrier != null) {
            try {
                injectBarrier(barrier);
            } catch (Exception e) {
                throw new StreamException(ERR_STREAM_BARRIER_INJECTION_FAILED, e).param(ARG_DETAIL, "pending barrier");
            }
        }
    }

    @Override
    public void open() throws Exception {
    }

    @Override
    public void finish() throws Exception {
        isRunning = false;
    }

    @Override
    public void close() throws Exception {
        isRunning = false;
        try {
            sourceFunction.cancel();
        } catch (Exception e) {
            LOG.warn("Error cancelling source function during close", e);
        }
    }

    @Override
    public void notifyCheckpointComplete(long checkpointId) throws Exception {
        if (sourceFunction instanceof CheckpointListener) {
            ((CheckpointListener) sourceFunction).notifyCheckpointComplete(checkpointId);
        }
    }

    @Override
    public OperatorSnapshotResult snapshotState(StateSnapshotContext context) throws Exception {
        OperatorSnapshotResult result = super.snapshotState(context);
        if (sourceFunction instanceof ReplayableSourceFunction) {
            long offset = ((ReplayableSourceFunction<?>) sourceFunction).getCurrentOffset();
            result.putOperatorState(SOURCE_OFFSET_KEY, offset);
        }
        if (sourceFunction instanceof CheckpointedSourceFunction) {
            OperatorSnapshotResult sourceResult =
                    ((CheckpointedSourceFunction<?>) sourceFunction).snapshotState(context.getCheckpointId());
            if (sourceResult != null) {
                result.merge(sourceResult);
            }
        }
        return result;
    }

    @Override
    public void restoreState(OperatorSnapshotResult snapshotResult) throws Exception {
        super.restoreState(snapshotResult);
        if (sourceFunction instanceof ReplayableSourceFunction && snapshotResult != null) {
            Object offsetObj = snapshotResult.getOperatorState(SOURCE_OFFSET_KEY);
            if (offsetObj != null) {
                long offset;
                if (offsetObj instanceof Number) {
                    offset = ((Number) offsetObj).longValue();
                } else {
                    offset = Long.parseLong(String.valueOf(offsetObj));
                }
                ((ReplayableSourceFunction<?>) sourceFunction).seek(offset);
            }
        }
        if (sourceFunction instanceof CheckpointedSourceFunction) {
            TaskStateSnapshot taskState = new TaskStateSnapshot(new TaskLocation("", "", "", 0));
            if (snapshotResult != null) {
                for (Map.Entry<String, Object> entry : snapshotResult.getOperatorStates().entrySet()) {
                    taskState.putOperatorState(entry.getKey(), entry.getValue());
                }
                for (Map.Entry<String, Object> entry : snapshotResult.getKeyedStates().entrySet()) {
                    taskState.putKeyedState(entry.getKey(), entry.getValue());
                }
            }
            ((CheckpointedSourceFunction<?>) sourceFunction).initializeState(taskState);
        }
    }

    public void injectBarrier(CheckpointBarrier barrier) throws Exception {
        OperatorSnapshotResult snapshotResult = null;
        if (barrier.snapshot()) {
            StateSnapshotContext context = new StateSnapshotContext(barrier.getId(), barrier.getTimestamp());
            snapshotResult = snapshotState(context);
            this.lastSnapshotResult = snapshotResult;
        }
        if (snapshotCallback != null && snapshotResult != null) {
            snapshotCallback.accept(snapshotResult);
        }
        if (output != null) {
            output.emitBarrier(barrier);
        }
    }
}
