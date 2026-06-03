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

public class TestInputGateBarrierForwarding {

    @Test
    public void testBarrierForwardedWhenChannelFinishesBeforeBarrierReceived() throws Exception {
        ResultPartition p0 = new ResultPartition();
        ResultPartition p1 = new ResultPartition();
        List<InputChannel> channels = Arrays.asList(new InputChannel(p0), new InputChannel(p1));
        InputGate gate = new InputGate(channels);

        p0.write(new CheckpointBarrier(1, 0, CheckpointType.CHECKPOINT));
        p1.close();

        p0.close();

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

        assertEquals(1, barriers.size(), "Should forward exactly one aligned barrier");
        assertEquals(1L, barriers.get(0).getId());
    }

    @Test
    public void testBarrierForwardedWhenAllChannelsFinishAfterPartialBarriers() throws Exception {
        ResultPartition p0 = new ResultPartition();
        ResultPartition p1 = new ResultPartition();
        ResultPartition p2 = new ResultPartition();
        List<InputChannel> channels = Arrays.asList(
                new InputChannel(p0), new InputChannel(p1), new InputChannel(p2));
        InputGate gate = new InputGate(channels);

        p0.write(new CheckpointBarrier(5, 0, CheckpointType.CHECKPOINT));
        p1.close();
        p2.close();
        p0.close();

        List<CheckpointBarrier> barriers = new ArrayList<>();
        while (true) {
            Optional<StreamElement> element = gate.read();
            if (!element.isPresent()) break;
            if (element.get().isCheckpointBarrier()) {
                barriers.add(element.get().asCheckpointBarrier());
            }
        }

        assertEquals(1, barriers.size(), "Should forward barrier even when channels finish before delivering barriers");
        assertEquals(5L, barriers.get(0).getId());
    }

    @Test
    public void testBarrierForwardedWithDataOnFinishedChannels() throws Exception {
        ResultPartition p0 = new ResultPartition();
        ResultPartition p1 = new ResultPartition();
        List<InputChannel> channels = Arrays.asList(new InputChannel(p0), new InputChannel(p1));
        InputGate gate = new InputGate(channels);

        p0.write(new StreamRecord<>("data-0"));
        p0.write(new CheckpointBarrier(3, 0, CheckpointType.CHECKPOINT));
        p0.close();

        p1.write(new StreamRecord<>("data-1"));
        p1.close();

        List<String> records = new ArrayList<>();
        List<CheckpointBarrier> barriers = new ArrayList<>();
        while (true) {
            Optional<StreamElement> element = gate.read();
            if (!element.isPresent()) break;
            if (element.get().isRecord()) {
                @SuppressWarnings("unchecked")
                StreamRecord<String> rec = (StreamRecord<String>) (StreamRecord<?>) element.get().asRecord();
                records.add(rec.getValue());
            } else if (element.get().isCheckpointBarrier()) {
                barriers.add(element.get().asCheckpointBarrier());
            }
        }

        assertEquals(2, records.size());
        assertTrue(records.contains("data-0"));
        assertTrue(records.contains("data-1"));
        assertEquals(1, barriers.size(), "Barrier should be forwarded after channel finishes");
        assertEquals(3L, barriers.get(0).getId());
    }

    @Test
    public void testNoBarrierLossWhenChannelClosedBeforeBarrierRead() throws Exception {
        ResultPartition p0 = new ResultPartition();
        ResultPartition p1 = new ResultPartition();
        List<InputChannel> channels = Arrays.asList(new InputChannel(p0), new InputChannel(p1));
        InputGate gate = new InputGate(channels);

        p1.write(new CheckpointBarrier(10, 0, CheckpointType.CHECKPOINT));
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

        assertEquals(1, barriers.size(), "Barrier should be forwarded even if one channel is already finished");
        assertEquals(10L, barriers.get(0).getId());
    }
}
