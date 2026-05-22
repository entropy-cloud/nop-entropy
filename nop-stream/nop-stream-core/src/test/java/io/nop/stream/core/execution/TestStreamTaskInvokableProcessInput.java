package io.nop.stream.core.execution;

import io.nop.stream.core.checkpoint.CheckpointBarrier;
import io.nop.stream.core.operators.Input;
import io.nop.stream.core.streamrecord.LatencyMarker;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.streamrecord.watermark.Watermark;
import io.nop.stream.core.streamrecord.watermark.WatermarkStatus;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class TestStreamTaskInvokableProcessInput {

    private static class MockInput implements Input<Object> {
        final List<String> received = new ArrayList<>();

        @Override
        public void processElement(StreamRecord<Object> element) {
            received.add("record:" + element.getValue());
        }

        @Override
        public void processWatermark(Watermark mark) {
            received.add("watermark:" + mark.getTimestamp());
        }

        @Override
        public void processWatermarkStatus(WatermarkStatus status) {
            received.add("status:" + status.getStatus());
        }

        @Override
        public void processLatencyMarker(LatencyMarker latencyMarker) {
        }

        @Override
        public void setKeyContextElement(StreamRecord<Object> record) {
        }

        @Override
        public void processBarrier(CheckpointBarrier barrier) {
            received.add("barrier:" + barrier.getId());
        }
    }

    @Test
    void testWatermarkStatusIsProcessedByInput() {
        MockInput input = new MockInput();
        input.processWatermarkStatus(WatermarkStatus.IDLE);
        input.processWatermarkStatus(WatermarkStatus.ACTIVE);

        assertEquals(2, input.received.size());
        assertEquals("status:-1", input.received.get(0));
        assertEquals("status:0", input.received.get(1));
    }

    @Test
    void testWatermarkStatusStreamElementRouting() {
        MockInput input = new MockInput();

        assertTrue(WatermarkStatus.IDLE.isWatermarkStatus());
        assertTrue(WatermarkStatus.ACTIVE.isWatermarkStatus());
        assertFalse(new Watermark(1000L).isWatermarkStatus());

        input.processWatermarkStatus(WatermarkStatus.IDLE.asWatermarkStatus());
        input.processWatermark(new Watermark(1000L));
        input.processWatermarkStatus(WatermarkStatus.ACTIVE.asWatermarkStatus());

        assertEquals(3, input.received.size());
        assertEquals("status:-1", input.received.get(0));
        assertEquals("watermark:1000", input.received.get(1));
        assertEquals("status:0", input.received.get(2));
    }

    @Test
    void testInputGateReadsWithWatermarkStatus() throws Exception {
        ResultPartition partition = new ResultPartition();
        InputChannel channel = new InputChannel(partition);
        InputGate gate = new InputGate(channel);

        partition.write(WatermarkStatus.IDLE);
        partition.write(new StreamRecord<>("data"));
        partition.write(WatermarkStatus.ACTIVE);
        partition.close();

        var e1 = gate.read();
        assertTrue(e1.isPresent());
        assertTrue(e1.get().isWatermarkStatus());

        var e2 = gate.read();
        assertTrue(e2.isPresent());
        assertTrue(e2.get().isRecord());

        var e3 = gate.read();
        assertTrue(e3.isPresent());
        assertTrue(e3.get().isWatermarkStatus());
    }
}
