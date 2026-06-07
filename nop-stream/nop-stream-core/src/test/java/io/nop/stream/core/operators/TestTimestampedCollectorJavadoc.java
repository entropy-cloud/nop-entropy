package io.nop.stream.core.operators;

import io.nop.stream.core.streamrecord.StreamRecord;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestTimestampedCollectorJavadoc {

    @Test
    void testReuseSemanticsSameRecordObject() {
        MockOutput output = new MockOutput();
        TimestampedCollector<String> collector = new TimestampedCollector<>(output);

        collector.setAbsoluteTimestamp(100L);
        collector.collect("first");
        collector.collect("second");

        assertEquals(2, output.collected.size());
        assertSame(output.collected.get(0), output.collected.get(1));
        assertEquals("second", output.collected.get(0).getValue());
    }

    @Test
    void testSetTimestampPropagated() {
        MockOutput output = new MockOutput();
        TimestampedCollector<String> collector = new TimestampedCollector<>(output);

        collector.setAbsoluteTimestamp(200L);
        collector.collect("value");

        assertEquals(1, output.collected.size());
        assertEquals(200L, output.collected.get(0).getTimestamp());
    }

    private static class MockOutput implements Output<StreamRecord<String>> {
        final java.util.List<StreamRecord<String>> collected = new java.util.ArrayList<>();

        @Override
        public void collect(StreamRecord<String> record) {
            collected.add(record);
        }

        @Override
        public void close() {}

        @Override
        public void emitWatermark(io.nop.stream.core.streamrecord.watermark.Watermark mark) {}

        @Override
        public void emitWatermarkStatus(io.nop.stream.core.streamrecord.watermark.WatermarkStatus status) {}

        @Override
        public <X> void collect(io.nop.stream.core.util.OutputTag<X> outputTag, StreamRecord<X> record) {}

        @Override
        public void emitLatencyMarker(io.nop.stream.core.streamrecord.LatencyMarker latencyMarker) {}

        @Override
        public void emitBarrier(io.nop.stream.core.checkpoint.CheckpointBarrier barrier) {}
    }
}
