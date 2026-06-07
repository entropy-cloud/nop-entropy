package io.nop.stream.core.execution;

import io.nop.stream.core.checkpoint.CheckpointBarrier;
import io.nop.stream.core.checkpoint.CheckpointType;
import io.nop.stream.core.streamrecord.StreamElement;
import io.nop.stream.core.streamrecord.StreamRecord;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class TestInputGateBarrierIndentFix {

    @Test
    void testBarrierAlignmentWithSingleChannel() throws Exception {
        ResultPartition p0 = new ResultPartition();
        InputGate gate = new InputGate(Arrays.asList(new InputChannel(p0)));

        p0.write(new CheckpointBarrier(1, 0, CheckpointType.CHECKPOINT));
        Optional<StreamElement> result = gate.read();
        assertTrue(result.isPresent());
        assertTrue(result.get().isCheckpointBarrier());
    }

    @Test
    void testBarrierAlignmentWithTwoChannels() throws Exception {
        ResultPartition p0 = new ResultPartition();
        ResultPartition p1 = new ResultPartition();
        InputGate gate = new InputGate(Arrays.asList(new InputChannel(p0), new InputChannel(p1)));

        p0.write(new CheckpointBarrier(1, 0, CheckpointType.CHECKPOINT));
        p1.write(new CheckpointBarrier(1, 0, CheckpointType.CHECKPOINT));

        Optional<StreamElement> result = gate.read();
        assertTrue(result.isPresent());
        assertTrue(result.get().isCheckpointBarrier());
    }
}
