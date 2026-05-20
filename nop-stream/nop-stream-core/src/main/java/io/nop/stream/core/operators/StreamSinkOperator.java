/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.operators;

import io.nop.stream.core.checkpoint.CheckpointBarrier;
import io.nop.stream.core.checkpoint.OperatorSnapshotResult;
import io.nop.stream.core.checkpoint.StateSnapshotContext;
import io.nop.stream.core.common.functions.SinkFunction;
import io.nop.stream.core.common.functions.sink.TwoPhaseCommitSinkFunction;
import io.nop.stream.core.common.state.CheckpointListener;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.streamrecord.watermark.Watermark;

/**
 * A stream operator that wraps a {@link SinkFunction} and consumes elements from the stream.
 * This is a terminal operator — it has no output and simply passes each element to the
 * user-supplied sink function.
 *
 * @param <IN> The type of elements consumed by this sink
 */
public class StreamSinkOperator<IN> extends AbstractUdfStreamOperator<Void, SinkFunction<IN>>
        implements OneInputStreamOperator<IN, Void> {

    private static final long serialVersionUID = 1L;

    public StreamSinkOperator(SinkFunction<IN> sinkFunction) {
        super(sinkFunction);
    }

    @Override
    public void processElement(StreamRecord<IN> element) throws Exception {
        userFunction.consume(element.getValue());
    }

    @Override
    public void processWatermark(Watermark mark) throws Exception {
    }

    @Override
    public void processBarrier(CheckpointBarrier barrier) throws Exception {
        OperatorSnapshotResult snapshotResult = null;
        if (barrier.snapshot()) {
            StateSnapshotContext context = new StateSnapshotContext(barrier.getId(), barrier.getTimestamp());
            snapshotResult = snapshotState(context);
            this.lastSnapshotResult = snapshotResult;

            if (userFunction instanceof TwoPhaseCommitSinkFunction) {
                ((TwoPhaseCommitSinkFunction<?>) userFunction).preCommit(barrier.getId());
            }
        }
        if (snapshotCallback != null && snapshotResult != null) {
            snapshotCallback.accept(snapshotResult);
        }
    }

    @Override
    public void close() throws Exception {
        if (userFunction instanceof AutoCloseable) {
            ((AutoCloseable) userFunction).close();
        }
    }

    @Override
    public void notifyCheckpointComplete(long checkpointId) throws Exception {
        if (userFunction instanceof TwoPhaseCommitSinkFunction) {
            ((TwoPhaseCommitSinkFunction<?>) userFunction).commit(checkpointId);
        } else if (userFunction instanceof CheckpointListener) {
            ((CheckpointListener) userFunction).notifyCheckpointComplete(checkpointId);
        }
    }

    @Override
    public void notifyCheckpointAborted(long checkpointId) throws Exception {
        if (userFunction instanceof TwoPhaseCommitSinkFunction) {
            ((TwoPhaseCommitSinkFunction<?>) userFunction).rollback();
        } else if (userFunction instanceof CheckpointListener) {
            ((CheckpointListener) userFunction).notifyCheckpointAborted(checkpointId);
        }
    }

    @Override
    public void restoreState(OperatorSnapshotResult snapshotResult) throws Exception {
        super.restoreState(snapshotResult);
        if (userFunction instanceof TwoPhaseCommitSinkFunction) {
            @SuppressWarnings("unchecked")
            TwoPhaseCommitSinkFunction<Object> tpcSink = (TwoPhaseCommitSinkFunction<Object>) userFunction;
            if (snapshotResult == null || snapshotResult.isEmpty()) {
                // Failure recovery: rollback any pending transaction
                tpcSink.rollback();
            }
            // Always start a new transaction for the recovered session
            tpcSink.beginTransaction();
        }
    }
}
