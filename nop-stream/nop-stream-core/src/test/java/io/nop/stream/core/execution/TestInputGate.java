package io.nop.stream.core.execution;

import io.nop.stream.core.streamrecord.StreamElement;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.streamrecord.watermark.Watermark;
import io.nop.stream.core.checkpoint.CheckpointBarrier;
import io.nop.stream.core.checkpoint.CheckpointType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class TestInputGate {

    @Test
    public void testSingleChannelRead() throws Exception {
        ResultPartition partition = new ResultPartition();
        InputChannel channel = new InputChannel(partition);
        InputGate gate = new InputGate(channel);

        partition.write(new StreamRecord<>("hello"));
        Optional<StreamElement> element = gate.read();

        assertTrue(element.isPresent());
        assertTrue(element.get().isRecord());
        assertEquals("hello", element.get().asRecord().getValue());
    }

    @Test
    public void testMultiChannelWatermarkMerge() throws Exception {
        ResultPartition p0 = new ResultPartition();
        ResultPartition p1 = new ResultPartition();
        ResultPartition p2 = new ResultPartition();
        List<InputChannel> channels = Arrays.asList(
                new InputChannel(p0), new InputChannel(p1), new InputChannel(p2));
        InputGate gate = new InputGate(channels);

        p0.write(new Watermark(100));
        p1.write(new Watermark(200));
        p2.write(new Watermark(300));
        p0.write(new Watermark(150));

        p0.close();
        p1.close();
        p2.close();

        List<Long> emittedWatermarks = new ArrayList<>();
        while (true) {
            Optional<StreamElement> element = gate.read();
            if (!element.isPresent()) break;
            if (element.get().isWatermark()) {
                emittedWatermarks.add(element.get().asWatermark().getTimestamp());
            }
        }

        assertFalse(emittedWatermarks.isEmpty());
        assertEquals(100L, emittedWatermarks.get(0));
        assertTrue(emittedWatermarks.contains(150L),
                "Should emit Watermark(150) when channel 0 advances from 100 to 150");
    }

    @Test
    public void testMultiChannelReadRoundRobin() throws Exception {
        ResultPartition p0 = new ResultPartition();
        ResultPartition p1 = new ResultPartition();
        ResultPartition p2 = new ResultPartition();
        List<InputChannel> channels = Arrays.asList(
                new InputChannel(p0), new InputChannel(p1), new InputChannel(p2));
        InputGate gate = new InputGate(channels);

        p0.write(new StreamRecord<>("a"));
        p1.write(new StreamRecord<>("b"));
        p2.write(new StreamRecord<>("c"));
        p0.close();
        p1.close();
        p2.close();

        List<String> results = new ArrayList<>();
        while (true) {
            Optional<StreamElement> element = gate.read();
            if (!element.isPresent()) break;
            if (element.get().isRecord()) {
                @SuppressWarnings("unchecked")
                StreamRecord<String> rec = (StreamRecord<String>) (StreamRecord<?>) element.get().asRecord();
                results.add(rec.getValue());
            }
        }

        assertEquals(3, results.size());
        assertTrue(results.contains("a"));
        assertTrue(results.contains("b"));
        assertTrue(results.contains("c"));
    }

    @Test
    public void testHighWatermarkEventCountNoStackOverflow() throws Exception {
        ResultPartition p0 = new ResultPartition();
        ResultPartition p1 = new ResultPartition();
        List<InputChannel> channels = Arrays.asList(new InputChannel(p0), new InputChannel(p1));
        InputGate gate = new InputGate(channels);

        int eventCount = 500;
        for (int i = 0; i < eventCount; i++) {
            p0.write(new Watermark(i));
            p1.write(new Watermark(i));
        }

        p0.close();
        p1.close();

        List<StreamElement> results = new ArrayList<>();
        while (true) {
            Optional<StreamElement> element = gate.read();
            if (!element.isPresent()) break;
            results.add(element.get());
        }

        assertFalse(results.isEmpty(), "Should have received watermark events");
    }

    @Test
    public void testHighBarrierEventCountNoStackOverflow() throws Exception {
        ResultPartition p0 = new ResultPartition();
        ResultPartition p1 = new ResultPartition();
        List<InputChannel> channels = Arrays.asList(new InputChannel(p0), new InputChannel(p1));
        InputGate gate = new InputGate(channels, null, false);

        int eventCount = 500;
        for (int i = 0; i < eventCount; i++) {
            p0.write(new StreamRecord<>("d-" + i));
            p1.write(new StreamRecord<>("d-" + i));
            p0.write(new CheckpointBarrier(i, i, CheckpointType.CHECKPOINT));
            p1.write(new CheckpointBarrier(i, i, CheckpointType.CHECKPOINT));
        }

        p0.close();
        p1.close();

        List<StreamElement> results = new ArrayList<>();
        while (true) {
            Optional<StreamElement> element = gate.read();
            if (!element.isPresent()) break;
            results.add(element.get());
        }

        assertFalse(results.isEmpty(), "Should have received events");
    }

    @Test
    public void testAllChannelsFinished() throws Exception {
        ResultPartition p0 = new ResultPartition();
        ResultPartition p1 = new ResultPartition();
        List<InputChannel> channels = Arrays.asList(new InputChannel(p0), new InputChannel(p1));
        InputGate gate = new InputGate(channels);

        p0.write(new StreamRecord<>("x"));
        p1.write(new StreamRecord<>("y"));

        assertFalse(gate.isAllFinished());

        p0.close();
        assertFalse(gate.isAllFinished());

        p1.close();
        assertTrue(gate.isAllFinished());
    }

    @Test
    public void testSingleChannelFinished() throws Exception {
        ResultPartition p0 = new ResultPartition();
        ResultPartition p1 = new ResultPartition();
        List<InputChannel> channels = Arrays.asList(new InputChannel(p0), new InputChannel(p1));
        InputGate gate = new InputGate(channels);

        p0.write(new StreamRecord<>("x"));
        p1.write(new StreamRecord<>("y"));

        p0.close();
        assertFalse(gate.isAllFinished(),
                "isAllFinished should be false when only one of two channels is closed");
    }

    @Test
    public void testOverlappingBarriersHandledIndependentlyInNonAlignedMode() throws Exception {
        // Stage 45: overlapping barrier ids are no longer rejected. In AT_LEAST_ONCE
        // (non-aligned) mode each barrier is emitted on first receipt and the two
        // epochs' alignment state is tracked independently. This replaces the legacy
        // single-in-flight rejection that threw on a different-id barrier.
        ResultPartition p0 = new ResultPartition();
        ResultPartition p1 = new ResultPartition();
        List<InputChannel> channels = Arrays.asList(new InputChannel(p0), new InputChannel(p1));
        InputGate gate = new InputGate(channels, null, false);

        p0.write(new CheckpointBarrier(1, 0, CheckpointType.CHECKPOINT));
        p0.write(new CheckpointBarrier(2, 0, CheckpointType.CHECKPOINT));

        p1.write(new StreamRecord<>("keep-p1-busy"));
        p1.close();
        p0.close();

        List<Long> emittedBarrierIds = new ArrayList<>();
        while (true) {
            Optional<StreamElement> e = gate.read();
            if (!e.isPresent()) break;
            if (e.get().isCheckpointBarrier()) {
                emittedBarrierIds.add(e.get().asCheckpointBarrier().getId());
            }
        }

        assertTrue(emittedBarrierIds.contains(1L), "Barrier 1 should be emitted (first receipt)");
        assertTrue(emittedBarrierIds.contains(2L), "Barrier 2 should be emitted independently (multi-epoch)");
    }
}
