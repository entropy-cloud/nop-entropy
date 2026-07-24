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

class TestInputGateBlockingApi {

    @Test
    void testBlockConsumptionBlocksChannel() throws Exception {
        ResultPartition p0 = new ResultPartition();
        ResultPartition p1 = new ResultPartition();
        List<InputChannel> channels = Arrays.asList(new InputChannel(p0), new InputChannel(p1));
        InputGate gate = new InputGate(channels);

        p0.write(new StreamRecord<>("data-ch0"));
        p1.write(new StreamRecord<>("data-ch1"));

        gate.blockConsumption(0);

        Optional<StreamElement> first = gate.read();
        assertTrue(first.isPresent());
        assertTrue(first.get().isRecord());
        assertEquals("data-ch1", first.get().asRecord().getValue(),
                "Should read from channel 1 while channel 0 is blocked");

        Optional<StreamElement> second = gate.read();
        assertTrue(second.isPresent());
        assertTrue(second.get().isRecord());
        assertEquals("data-ch1", second.get().asRecord().getValue(),
                "Channel 0 should still be blocked, reading again from channel 1 should return null");

        p0.close();
        p1.close();
    }

    @Test
    void testResumeConsumptionUnblocksChannel() throws Exception {
        ResultPartition p0 = new ResultPartition();
        ResultPartition p1 = new ResultPartition();
        List<InputChannel> channels = Arrays.asList(new InputChannel(p0), new InputChannel(p1));
        InputGate gate = new InputGate(channels);

        p0.write(new StreamRecord<>("data-ch0"));
        p1.write(new StreamRecord<>("data-ch1"));

        gate.blockConsumption(0);
        // channel 0 is blocked, should read from channel 1
        Optional<StreamElement> first = gate.read();
        assertTrue(first.isPresent());
        assertEquals("data-ch1", first.get().asRecord().getValue());

        gate.resumeConsumption(0);

        Optional<StreamElement> second = gate.read();
        assertTrue(second.isPresent());
        assertEquals("data-ch0", second.get().asRecord().getValue(),
                "Channel 0 should be readable after resumeConsumption");

        p0.close();
        p1.close();
    }

    @Test
    void testResumeConsumptionAllUnblocksAllChannels() throws Exception {
        ResultPartition p0 = new ResultPartition();
        ResultPartition p1 = new ResultPartition();
        List<InputChannel> channels = Arrays.asList(new InputChannel(p0), new InputChannel(p1));
        InputGate gate = new InputGate(channels);

        p0.write(new StreamRecord<>("data-ch0"));
        p1.write(new StreamRecord<>("data-ch1"));

        gate.blockConsumption(0);
        gate.blockConsumption(1);

        gate.resumeConsumptionAll();

        Optional<StreamElement> first = gate.read();
        assertTrue(first.isPresent(),
                "Should read data after resumeConsumptionAll");
        assertTrue(first.get().isRecord());

        p0.close();
        p1.close();
    }

    @Test
    void testResumeOnUnblockedChannelIsSafeNoOp() {
        ResultPartition p0 = new ResultPartition();
        List<InputChannel> channels = Arrays.asList(new InputChannel(p0));
        InputGate gate = new InputGate(channels);

        assertDoesNotThrow(() -> gate.resumeConsumption(0),
                "Resume on unblocked channel should be safe no-op");
    }

    @Test
    void testBlockInvalidChannelThrows() {
        ResultPartition p0 = new ResultPartition();
        List<InputChannel> channels = Arrays.asList(new InputChannel(p0));
        InputGate gate = new InputGate(channels);

        assertThrows(IllegalArgumentException.class,
                () -> gate.blockConsumption(-1),
                "Negative channel index should throw");
        assertThrows(IllegalArgumentException.class,
                () -> gate.blockConsumption(5),
                "Out-of-range channel index should throw");
    }

    @Test
    void testResumeInvalidChannelThrows() {
        ResultPartition p0 = new ResultPartition();
        List<InputChannel> channels = Arrays.asList(new InputChannel(p0));
        InputGate gate = new InputGate(channels);

        assertThrows(IllegalArgumentException.class,
                () -> gate.resumeConsumption(-1),
                "Negative channel index should throw");
        assertThrows(IllegalArgumentException.class,
                () -> gate.resumeConsumption(5),
                "Out-of-range channel index should throw");
    }

    @Test
    void testBarrierAlignmentUsesBlockingApi() throws Exception {
        ResultPartition p0 = new ResultPartition();
        ResultPartition p1 = new ResultPartition();
        List<InputChannel> channels = Arrays.asList(new InputChannel(p0), new InputChannel(p1));
        InputGate gate = new InputGate(channels);

        p0.write(new CheckpointBarrier(1, 0, CheckpointType.CHECKPOINT));

        p1.write(new StreamRecord<>("data-between"));

        Optional<StreamElement> between = gate.read();
        assertTrue(between.isPresent());
        assertTrue(between.get().isRecord(),
                "Should read record from channel 1 while channel 0 is blocked by barrier alignment");

        p1.write(new CheckpointBarrier(1, 0, CheckpointType.CHECKPOINT));
        p0.close();
        p1.close();

        Optional<StreamElement> aligned = gate.read();
        assertTrue(aligned.isPresent());
        assertTrue(aligned.get().isCheckpointBarrier(),
                "Should emit aligned barrier after both channels deliver");
        assertEquals(1L, aligned.get().asCheckpointBarrier().getId());
    }
}
