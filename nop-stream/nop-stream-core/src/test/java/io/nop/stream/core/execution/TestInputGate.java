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

        List<Long> emittedWatermarks = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            Optional<StreamElement> element = gate.read();
            if (!element.isPresent()) break;
            if (element.get().isWatermark()) {
                emittedWatermarks.add(element.get().asWatermark().getTimestamp());
            }
        }

        assertFalse(emittedWatermarks.isEmpty());
        assertEquals(100L, emittedWatermarks.get(0));

        p0.write(new Watermark(150));
        p0.close();
        p1.close();
        p2.close();

        boolean found150 = false;
        while (true) {
            Optional<StreamElement> element = gate.read();
            if (!element.isPresent()) break;
            if (element.get().isWatermark()) {
                if (element.get().asWatermark().getTimestamp() == 150L) {
                    found150 = true;
                }
            }
        }
        assertTrue(found150, "Should emit Watermark(150) when channel 0 advances from 100 to 150");
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
}
