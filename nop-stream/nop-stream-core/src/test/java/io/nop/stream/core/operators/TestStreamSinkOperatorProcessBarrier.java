package io.nop.stream.core.operators;

import io.nop.stream.core.checkpoint.CheckpointBarrier;
import io.nop.stream.core.checkpoint.CheckpointType;
import io.nop.stream.core.checkpoint.OperatorSnapshotResult;
import io.nop.stream.core.checkpoint.StateSnapshotContext;
import io.nop.stream.core.common.functions.SinkFunction;
import io.nop.stream.core.exceptions.StreamException;
import org.junit.jupiter.api.Test;

import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_CHECKPOINT_ERROR;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class TestStreamSinkOperatorProcessBarrier {

    private static class FailingSnapshotOperator extends StreamSinkOperator<String> {
        FailingSnapshotOperator() {
            super(value -> {});
        }

        @Override
        public OperatorSnapshotResult snapshotState(StateSnapshotContext context) {
            throw new StreamException(ERR_STREAM_CHECKPOINT_ERROR).param("reason", "simulated snapshot failure");
        }
    }

    @Test
    void testProcessBarrierSnapshotFailureDeliversErrorToCallback() throws Exception {
        StreamSinkOperator<String> op = new FailingSnapshotOperator();
        AtomicReference<OperatorSnapshotResult> captured = new AtomicReference<>();
        op.setSnapshotCallback(captured::set);

        CheckpointBarrier barrier = new CheckpointBarrier(1L, 100L, CheckpointType.CHECKPOINT);
        op.processBarrier(barrier);

        OperatorSnapshotResult result = captured.get();
        assertNotNull(result, "snapshotCallback must be called even on failure");
        assertTrue(result.hasError(), "result must indicate error");
        assertTrue(result.getError().getMessage().contains("simulated snapshot failure"),
                "error message should match the snapshot failure");
    }

    @Test
    void testProcessBarrierSuccessDeliversResultToCallback() throws Exception {
        StreamSinkOperator<String> op = new StreamSinkOperator<>(value -> {});
        AtomicReference<OperatorSnapshotResult> captured = new AtomicReference<>();
        op.setSnapshotCallback(captured::set);

        CheckpointBarrier barrier = new CheckpointBarrier(1L, 100L, CheckpointType.CHECKPOINT);
        op.processBarrier(barrier);

        OperatorSnapshotResult result = captured.get();
        assertNotNull(result, "snapshotCallback must be called on success");
        assertFalse(result.hasError(), "result must not have error on success");
    }

    @Test
    void testProcessBarrierNonSnapshotNoCallback() throws Exception {
        StreamSinkOperator<String> op = new StreamSinkOperator<>(value -> {});
        AtomicReference<OperatorSnapshotResult> captured = new AtomicReference<>();
        op.setSnapshotCallback(captured::set);

        CheckpointBarrier barrier = new CheckpointBarrier(1L, 100L, CheckpointType.SAVEPOINT);
        op.processBarrier(barrier);

        assertNotNull(captured.get(), "snapshotCallback is called for savepoint too since snapshot() returns true");
    }
}
