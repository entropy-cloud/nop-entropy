package io.nop.stream.core.execution;

import io.nop.stream.core.checkpoint.CheckpointBarrier;
import io.nop.stream.core.checkpoint.CheckpointType;
import io.nop.stream.core.streamrecord.StreamElement;
import io.nop.stream.core.streamrecord.StreamRecord;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class TestInputGateBarrierAlignment {

    @Test
    public void testBarrierAlignmentBasic() throws Exception {
        ResultPartition p0 = new ResultPartition();
        ResultPartition p1 = new ResultPartition();
        List<InputChannel> channels = Arrays.asList(new InputChannel(p0), new InputChannel(p1));
        InputGate gate = new InputGate(channels);

        p0.write(new CheckpointBarrier(1, 0, CheckpointType.CHECKPOINT));

        p1.write(new StreamRecord<>("data-between-barriers"));
        Optional<StreamElement> betweenRead = gate.read();
        assertTrue(betweenRead.isPresent());
        assertTrue(betweenRead.get().isRecord(),
                "Should read the record from channel 1 while channel 0 is blocked by barrier");

        p1.write(new CheckpointBarrier(1, 0, CheckpointType.CHECKPOINT));
        p0.close();
        p1.close();

        List<CheckpointBarrier> alignedBarriers = new ArrayList<>();
        while (true) {
            Optional<StreamElement> element = gate.read();
            if (!element.isPresent()) break;
            if (element.get().isCheckpointBarrier()) {
                alignedBarriers.add(element.get().asCheckpointBarrier());
            }
        }

        assertEquals(1, alignedBarriers.size(),
                "Should emit exactly one aligned barrier after both channels deliver it");
        assertEquals(1L, alignedBarriers.get(0).getId());
    }

    @Test
    public void testBarrierAlignmentOutOfOrder() throws Exception {
        ResultPartition p0 = new ResultPartition();
        ResultPartition p1 = new ResultPartition();
        List<InputChannel> channels = Arrays.asList(new InputChannel(p0), new InputChannel(p1));
        InputGate gate = new InputGate(channels);

        p1.write(new CheckpointBarrier(2, 0, CheckpointType.CHECKPOINT));
        p0.write(new CheckpointBarrier(2, 0, CheckpointType.CHECKPOINT));
        p0.close();
        p1.close();

        List<CheckpointBarrier> alignedBarriers = new ArrayList<>();
        while (true) {
            Optional<StreamElement> element = gate.read();
            if (!element.isPresent()) break;
            if (element.get().isCheckpointBarrier()) {
                alignedBarriers.add(element.get().asCheckpointBarrier());
            }
        }

        assertEquals(1, alignedBarriers.size(),
                "Should emit exactly one aligned barrier regardless of arrival order");
        assertEquals(2L, alignedBarriers.get(0).getId());
    }

    @Test
    public void testBarrierAlignmentWithRecordsBetween() throws Exception {
        ResultPartition p0 = new ResultPartition();
        ResultPartition p1 = new ResultPartition();
        List<InputChannel> channels = Arrays.asList(new InputChannel(p0), new InputChannel(p1));
        InputGate gate = new InputGate(channels);

        p0.write(new CheckpointBarrier(3, 0, CheckpointType.CHECKPOINT));
        p1.write(new StreamRecord<>("before-barrier"));

        Optional<StreamElement> first = gate.read();
        assertTrue(first.isPresent());
        assertTrue(first.get().isRecord(),
                "Record from channel 1 should be readable while channel 0 is blocked");
        assertEquals("before-barrier", first.get().asRecord().getValue());

        p1.write(new CheckpointBarrier(3, 0, CheckpointType.CHECKPOINT));
        p0.close();
        p1.close();

        List<CheckpointBarrier> barriers = new ArrayList<>();
        while (true) {
            Optional<StreamElement> element = gate.read();
            if (!element.isPresent()) break;
            if (element.get().isCheckpointBarrier()) {
                barriers.add(element.get().asCheckpointBarrier());
            }
        }

        assertEquals(1, barriers.size(),
                "Should emit aligned barrier after both channels deliver checkpoint 3");
        assertEquals(3L, barriers.get(0).getId());
    }

    @Test
    public void testBarriersRemainingNoUnderflow() throws Exception {
        ResultPartition p0 = new ResultPartition();
        ResultPartition p1 = new ResultPartition();
        List<InputChannel> channels = Arrays.asList(new InputChannel(p0), new InputChannel(p1));
        InputGate gate = new InputGate(channels);

        p0.write(new CheckpointBarrier(10, 0, CheckpointType.CHECKPOINT));
        p1.write(new CheckpointBarrier(10, 0, CheckpointType.CHECKPOINT));

        List<CheckpointBarrier> barriers = new ArrayList<>();
        List<String> records = new ArrayList<>();

        while (true) {
            Optional<StreamElement> element = gate.read();
            if (!element.isPresent()) break;
            if (element.get().isCheckpointBarrier()) {
                barriers.add(element.get().asCheckpointBarrier());
            } else if (element.get().isRecord()) {
                @SuppressWarnings("unchecked")
                StreamRecord<String> rec = (StreamRecord<String>) (StreamRecord<?>) element.get().asRecord();
                records.add(rec.getValue());
            }
        }

        assertDoesNotThrow(() -> {
            p0.close();
            p1.close();
            while (true) {
                Optional<StreamElement> element = gate.read();
                if (!element.isPresent()) break;
            }
        }, "Should not throw when reading after barriers have been fully aligned");

        assertEquals(1, barriers.size(),
                "Should emit exactly one aligned barrier for checkpoint 10");
        assertEquals(10L, barriers.get(0).getId());
    }
}
