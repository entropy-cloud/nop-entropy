/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy/nop-entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.operators;

import io.nop.stream.core.checkpoint.CheckpointBarrier;
import io.nop.stream.core.checkpoint.CheckpointType;
import io.nop.stream.core.checkpoint.OperatorSnapshotResult;
import io.nop.stream.core.checkpoint.StateSnapshotContext;
import io.nop.stream.core.checkpoint.TaskStateSnapshot;
import io.nop.stream.core.checkpoint.participant.CheckpointParticipant;
import io.nop.stream.core.common.functions.SinkFunction;
import io.nop.stream.core.exceptions.NopStreamErrors;
import io.nop.stream.core.exceptions.StreamException;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R6 AR-01 regression pin (plan 2325): a saveState/prepareCommit failure AFTER a
 * successful snapshotState must route the TAGGED FAILURE to the tracker — never
 * a success ACK. The pre-fix code delivered on {@code snapshotResult != null}
 * first, so the coordinator durably recorded a checkpoint whose sink-side
 * pending commits never existed; on restore the records past the restored
 * cursor were neither committed nor pending (silent exactly-once loss).
 * Reverting the delivery order in {@code StreamSinkOperator.processBarrier}
 * turns this test red (the callback receives a success result with
 * {@code hasError() == false} instead of the tagged failure).
 */
class TestStreamSinkOperatorParticipantFailurePriority {

    /** Sink function that is also a checkpoint participant failing at saveState. */
    static class FailingParticipantSink
            implements SinkFunction<String>, CheckpointParticipant {
        private static final long serialVersionUID = 1L;

        @Override
        public void consume(String value) {
        }

        @Override
        public TaskStateSnapshot saveState(long epochId) {
            throw new StreamException(NopStreamErrors.ERR_STREAM_CHECKPOINT_ERROR)
                    .param("reason", "simulated saveState failure (R6 AR-01)");
        }

        @Override
        public void prepareCommit(long epochId) {
        }

        @Override
        public void finishCommit(long epochId, boolean success) {
        }

        @Override
        public void restoreFromEpoch(long epochId, TaskStateSnapshot state) {
        }
    }

    @Test
    void participantSaveStateFailureAfterSuccessfulSnapshotRoutesTaggedFailure() throws Exception {
        StreamSinkOperator<String> op = new StreamSinkOperator<>(new FailingParticipantSink());
        AtomicReference<OperatorSnapshotResult> captured = new AtomicReference<>();
        op.setSnapshotCallback(captured::set);

        CheckpointBarrier barrier = new CheckpointBarrier(7L, 100L, CheckpointType.CHECKPOINT);
        op.processBarrier(barrier);

        OperatorSnapshotResult result = captured.get();
        assertNotNull(result, "snapshotCallback must be called");
        assertTrue(result.hasError(),
                "saveState failure after a successful snapshotState must route the TAGGED FAILURE, "
                        + "not a success ACK (AR-01 error priority)");
        assertTrue(String.valueOf(result.getError()).contains("AR-01"),
                () -> "the routed failure must be the participant's saveState error: " + result.getError());
        assertEquals(7L, result.getCheckpointId(), "the failure must be tagged with the epoch");
    }

    @Test
    void healthyParticipantStillRoutesSuccess() throws Exception {
        StreamSinkOperator<String> op = new StreamSinkOperator<String>(value -> {
        });
        AtomicReference<OperatorSnapshotResult> captured = new AtomicReference<>();
        op.setSnapshotCallback(captured::set);

        CheckpointBarrier barrier = new CheckpointBarrier(7L, 100L, CheckpointType.CHECKPOINT);
        op.processBarrier(barrier);

        OperatorSnapshotResult result = captured.get();
        assertNotNull(result);
        assertFalse(result.hasError(), "healthy flow must keep routing success");
    }

    private static void assertEquals(long expected, long actual, String message) {
        org.junit.jupiter.api.Assertions.assertEquals(expected, actual, message);
    }
}
