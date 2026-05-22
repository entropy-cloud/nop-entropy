/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.operators.windowing;

import io.nop.stream.core.common.functions.KeySelector;
import io.nop.stream.core.common.typeutils.TypeSerializer;
import io.nop.stream.core.operators.Output;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.streamrecord.watermark.Watermark;
import io.nop.stream.core.util.OutputTag;
import io.nop.stream.core.windowing.assigners.TumblingEventTimeWindows;
import io.nop.stream.core.windowing.triggers.EventTimeTrigger;
import io.nop.stream.core.windowing.windows.TimeWindow;
import io.nop.stream.runtime.operators.windowing.functions.InternalWindowFunction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class TestWindowOperatorWatermarkReception {

    private CapturingOutput<String> output;
    private WindowOperator<String, Integer, Object, String, TimeWindow> operator;

    @BeforeEach
    void setUp() throws Exception {
        output = new CapturingOutput<>();

        operator = new WindowOperator<>(
                TumblingEventTimeWindows.of(100L),
                new SimpleTimeWindowSerializer(),
                (KeySelector<Integer, String>) v -> "key1",
                new SimpleStringSerializer(),
                String.class,
                new ToStringWindowFunction(),
                EventTimeTrigger.create(),
                0L,
                null
        );

        operator.setOutput((Output) output);
        operator.open();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (operator != null) {
            operator.close();
        }
    }

    @Test
    void testProcessWatermarkAdvancesInternalTimerService() throws Exception {
        operator.processElement(new StreamRecord<>(5, 10));
        operator.processElement(new StreamRecord<>(7, 50));

        assertTrue(output.elements.isEmpty(), "No output before watermark reaches window.maxTimestamp");

        operator.processWatermark(new Watermark(99));
        assertEquals(1, output.elements.size(), "Watermark at window.maxTimestamp=99 should fire trigger");
        assertEquals("7", output.elements.get(0));
    }

    @Test
    void testWatermarkForwardedDownstream() throws Exception {
        operator.processWatermark(new Watermark(500));

        assertFalse(output.watermarks.isEmpty(), "Watermark should be forwarded downstream");
        assertEquals(500L, output.watermarks.get(0).getTimestamp());
    }

    @Test
    void testMultipleWindowsFireOnWatermark() throws Exception {
        operator.processElement(new StreamRecord<>(5, 10));
        operator.processElement(new StreamRecord<>(15, 110));

        operator.processWatermark(new Watermark(100));
        assertEquals(1, output.elements.size());
        assertEquals("5", output.elements.get(0));

        output.elements.clear();

        operator.processWatermark(new Watermark(200));
        assertEquals(1, output.elements.size());
        assertEquals("15", output.elements.get(0));
    }

    static class CapturingOutput<T> implements Output<StreamRecord<T>> {
        final List<T> elements = new ArrayList<>();
        final List<Watermark> watermarks = new ArrayList<>();

        @Override
        public void collect(StreamRecord<T> record) {
            elements.add(record.getValue());
        }

        @Override
        public <X> void collect(OutputTag<X> outputTag, StreamRecord<X> record) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void emitWatermark(Watermark mark) {
            watermarks.add(mark);
        }

        @Override
        public void emitWatermarkStatus(io.nop.stream.core.streamrecord.watermark.WatermarkStatus status) {
        }

        @Override
        public void emitLatencyMarker(io.nop.stream.core.streamrecord.LatencyMarker marker) {
        }

        @Override
        public void emitBarrier(io.nop.stream.core.checkpoint.CheckpointBarrier barrier) {
        }

        @Override
        public void close() {
        }
    }

    static class ToStringWindowFunction implements InternalWindowFunction<Object, String, String, TimeWindow> {
        private static final long serialVersionUID = 1L;

        @Override
        public void process(String key, TimeWindow window, InternalWindowContext context,
                            Object input, io.nop.stream.core.util.Collector<String> out) {
            out.collect(String.valueOf(input));
        }

        @Override
        public void clear(TimeWindow window, InternalWindowContext context) {
        }
    }

    static class SimpleTimeWindowSerializer implements TypeSerializer<TimeWindow> {
        private static final long serialVersionUID = 1L;

        @Override
        public boolean isImmutableType() { return true; }

        @Override
        public TypeSerializer<TimeWindow> duplicate() { return this; }

        @Override
        public TimeWindow createInstance() { return new TimeWindow(0, 0); }

        @Override
        public TimeWindow copy(TimeWindow from) { return new TimeWindow(from.getStart(), from.getEnd()); }

        @Override
        public TimeWindow copy(TimeWindow from, TimeWindow reuse) { return new TimeWindow(from.getStart(), from.getEnd()); }

        @Override
        public int getLength() { return -1; }
    }

    static class SimpleStringSerializer implements TypeSerializer<String> {
        private static final long serialVersionUID = 1L;

        @Override
        public boolean isImmutableType() { return true; }

        @Override
        public TypeSerializer<String> duplicate() { return this; }

        @Override
        public String createInstance() { return ""; }

        @Override
        public String copy(String from) { return from; }

        @Override
        public String copy(String from, String reuse) { return from; }

        @Override
        public int getLength() { return -1; }
    }
}
