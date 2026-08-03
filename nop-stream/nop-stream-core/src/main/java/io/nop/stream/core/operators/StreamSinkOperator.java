/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.operators;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

import io.nop.stream.core.checkpoint.CheckpointBarrier;
import io.nop.stream.core.checkpoint.OperatorSnapshotResult;
import io.nop.stream.core.checkpoint.participant.CheckpointParticipant;
import io.nop.stream.core.checkpoint.StateSnapshotContext;
import io.nop.stream.core.checkpoint.TaskStateSnapshot;
import io.nop.stream.core.common.functions.sink.TwoPhaseCommitSinkFunction;
import io.nop.stream.core.common.functions.SinkFunction;
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
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(StreamSinkOperator.class);

    public StreamSinkOperator(SinkFunction<IN> sinkFunction) {
        super(sinkFunction);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Sink operator: the user sink function is shared across subtasks so
     * that collected results remain visible to the test caller regardless of
     * which subtask produced them. A fresh operator instance is created per
     * subtask to hold per-subtask snapshot state.
     */
    @Override
    public StreamSinkOperator<IN> copyForSubtask() {
        return new StreamSinkOperator<>(userFunction);
    }

    @Override
    public void processElement(StreamRecord<IN> element) throws Exception {
        userFunction.consume(element.getValue());
    }

    @Override
    public void processWatermark(Watermark mark) throws Exception {
        // Sink is the chain tail: advance timers but do not forward (no downstream).
        if (timeServiceManager != null) {
            timeServiceManager.advanceWatermark(mark);
        }
    }

    @Override
    public void processBarrier(CheckpointBarrier barrier) throws Exception {
        OperatorSnapshotResult snapshotResult = null;
        Exception snapshotError = null;
        if (barrier.snapshot()) {
            try {
                StateSnapshotContext context = new StateSnapshotContext(barrier.getId(), barrier.getTimestamp());
                snapshotResult = snapshotState(context);

                if (userFunction instanceof CheckpointParticipant) {
                    TaskStateSnapshot participantState = ((CheckpointParticipant) userFunction).saveState(barrier.getId());
                    if (participantState != null && snapshotResult != null) {
                        for (Map.Entry<String, Object> entry : participantState.getOperatorStates().entrySet()) {
                            snapshotResult.putOperatorState("participant-" + entry.getKey(), entry.getValue());
                        }
                        for (Map.Entry<String, Object> entry : participantState.getKeyedStates().entrySet()) {
                            snapshotResult.putKeyedState("participant-" + entry.getKey(), entry.getValue());
                        }
                    }

                    ((CheckpointParticipant) userFunction).prepareCommit(barrier.getId());
                }

                this.lastSnapshotResult = snapshotResult;
            } catch (Exception e) {
                snapshotError = e;
                LOG.error("Snapshot failed for sink operator at checkpoint {}", barrier.getId(), e);
            }
        }
        if (snapshotCallback != null) {
            if (snapshotResult != null) {
                snapshotCallback.accept(snapshotResult);
            } else if (snapshotError != null) {
                OperatorSnapshotResult failureResult = new OperatorSnapshotResult();
                // Stage 45: tag the failure result so the tracker routes the abort
                // to the correct epoch (design §2.8.1 D2).
                failureResult.setCheckpointId(barrier.getId());
                failureResult.setError(snapshotError);
                snapshotCallback.accept(failureResult);
            }
        }
    }

    @Override
    public void close() throws Exception {
        super.close();
        if (userFunction instanceof AutoCloseable) {
            ((AutoCloseable) userFunction).close();
        }
    }

    @Override
    public void notifyCheckpointComplete(long checkpointId) throws Exception {
        if (userFunction instanceof CheckpointParticipant) {
            // Commit is handled by CheckpointCoordinator via finishCommit(epochId, true) path.
            // Skip direct commit() to avoid double commit.
        } else if (userFunction instanceof CheckpointListener) {
            ((CheckpointListener) userFunction).notifyCheckpointComplete(checkpointId);
        }
    }

    @Override
    public void notifyCheckpointAborted(long checkpointId) throws Exception {
        if (userFunction instanceof CheckpointParticipant) {
            // Abort is handled by CheckpointCoordinator via finishCommit(epochId, false) path.
            // Skip direct rollback() — prepared transactions are kept for subsuming commit.
        } else if (userFunction instanceof CheckpointListener) {
            ((CheckpointListener) userFunction).notifyCheckpointAborted(checkpointId);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void restoreState(OperatorSnapshotResult snapshotResult) throws Exception {
        super.restoreState(snapshotResult);
        // P0-3: previously this method invoked restoreFromEpoch with sentinel
        // arguments (-1, null) that immediately cleared the just-restored
        // pendingCommits map, breaking the downstream restoreFromEpoch call
        // dispatched by GraphModelCheckpointExecutor.restoreOperatorsFromState
        // (udf branch). The real epochId restore is now owned solely by that
        // executor path, so here we only rebuild the pendingCommits map from
        // the durable snapshot.
        if (userFunction instanceof TwoPhaseCommitSinkFunction) {
            TwoPhaseCommitSinkFunction<Object> tpcSink = (TwoPhaseCommitSinkFunction<Object>) userFunction;
            if (snapshotResult != null && !snapshotResult.isEmpty()) {
                String pendingKey = "participant-" + TwoPhaseCommitSinkFunction.PENDING_COMMITS_KEY;
                Object raw = snapshotResult.getOperatorState(pendingKey);
                if (raw instanceof Map) {
                    Map<Long, Object> pending = (Map<Long, Object>) raw;
                    tpcSink.setPendingCommits(Collections.synchronizedMap(new TreeMap<>(pending)));
                }
            }
        }
    }
}
