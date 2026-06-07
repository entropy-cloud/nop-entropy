package io.nop.stream.core.operators;

import io.nop.stream.core.checkpoint.CheckpointBarrier;
import io.nop.stream.core.checkpoint.CheckpointType;
import io.nop.stream.core.checkpoint.OperatorSnapshotResult;
import io.nop.stream.core.checkpoint.StateSnapshotContext;
import io.nop.stream.core.streamrecord.LatencyMarker;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.streamrecord.watermark.Watermark;
import io.nop.stream.core.streamrecord.watermark.WatermarkStatus;
import io.nop.stream.core.util.OutputTag;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class TestAbstractStreamOperatorProcessBarrier {

    @Test
    void testProcessBarrierSnapshotFailureDoesNotPropagate() throws Exception {
        AtomicBoolean barrierPropagated = new AtomicBoolean(false);
        AtomicReference<Exception> capturedError = new AtomicReference<>();

        AbstractStreamOperator<String> operator = new AbstractStreamOperator<>() {
            private static final long serialVersionUID = 1L;

            @Override
            public OperatorSnapshotResult snapshotState(StateSnapshotContext context) throws Exception {
                throw new io.nop.stream.core.exceptions.StreamException(
                        io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_STATE_ERROR);
            }
        };

        Output<StreamRecord<String>> output = new Output<>() {
            @Override public void collect(StreamRecord<String> record) {}
            @Override public void close() {}
            @Override public void emitWatermark(Watermark watermark) {}
            @Override public void emitWatermarkStatus(WatermarkStatus status) {}
            @Override public <X> void collect(OutputTag<X> outputTag, StreamRecord<X> record) {}
            @Override public void emitLatencyMarker(LatencyMarker latencyMarker) {}
            @Override
            public void emitBarrier(CheckpointBarrier barrier) {
                barrierPropagated.set(true);
            }
        };
        operator.setOutput(output);
        operator.setSnapshotCallback(result -> {
            if (result.getError() != null) {
                capturedError.set(result.getError());
            }
        });

        CheckpointBarrier barrier = new CheckpointBarrier(1L, System.currentTimeMillis(), CheckpointType.CHECKPOINT);
        operator.processBarrier(barrier);

        assertFalse(barrierPropagated.get(), "Barrier should NOT propagate when snapshot fails");
        assertNotNull(capturedError.get(), "Error should be reported via callback");
    }

    @Test
    void testProcessBarrierSuccessPropagates() throws Exception {
        AtomicBoolean barrierPropagated = new AtomicBoolean(false);

        AbstractStreamOperator<String> operator = new AbstractStreamOperator<>() {
            private static final long serialVersionUID = 1L;
        };

        Output<StreamRecord<String>> output = new Output<>() {
            @Override public void collect(StreamRecord<String> record) {}
            @Override public void close() {}
            @Override public void emitWatermark(Watermark watermark) {}
            @Override public void emitWatermarkStatus(WatermarkStatus status) {}
            @Override public <X> void collect(OutputTag<X> outputTag, StreamRecord<X> record) {}
            @Override public void emitLatencyMarker(LatencyMarker latencyMarker) {}
            @Override
            public void emitBarrier(CheckpointBarrier barrier) {
                barrierPropagated.set(true);
            }
        };
        operator.setOutput(output);

        CheckpointBarrier barrier = new CheckpointBarrier(1L, System.currentTimeMillis(), CheckpointType.CHECKPOINT);
        operator.processBarrier(barrier);

        assertTrue(barrierPropagated.get(), "Barrier should propagate on successful snapshot");
    }
}
