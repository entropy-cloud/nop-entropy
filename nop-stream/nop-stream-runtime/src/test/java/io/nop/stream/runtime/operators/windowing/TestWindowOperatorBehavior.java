/*
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
import io.nop.stream.core.util.OutputTag;
import io.nop.stream.core.windowing.assigners.TumblingEventTimeWindows;
import io.nop.stream.core.windowing.triggers.EventTimeTrigger;
import io.nop.stream.core.windowing.windows.TimeWindow;
import io.nop.stream.runtime.operators.WindowOperatorTimerService;
import io.nop.stream.runtime.operators.windowing.functions.InternalWindowFunction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class TestWindowOperatorBehavior {

    private static final long WINDOW_SIZE = 200L;

    private TestOutput<String> output;
    private TestableWindowOperator operator;

    @BeforeEach
    void setUp() throws Exception {
        output = new TestOutput<>();

        operator = new TestableWindowOperator(
                TumblingEventTimeWindows.of(WINDOW_SIZE),
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

    private void processElement(int value, long timestamp) throws Exception {
        operator.processElement(new StreamRecord<>(value, timestamp));
    }

    private void advanceWatermark(long timestamp) throws Exception {
        operator.advanceInternalWatermark(timestamp);
    }

    @Test
    void testTumblingWindowTriggersAtBoundary() throws Exception {
        processElement(10, 50);
        processElement(20, 100);
        processElement(30, 150);

        assertTrue(output.isEmpty());

        advanceWatermark(199);
        assertEquals(1, output.size());
        assertEquals("30", output.getElements().get(0));
    }

    @Test
    void testWindowCleanupPreventsReFiring() throws Exception {
        processElement(42, 50);
        advanceWatermark(199);
        assertEquals(1, output.size());
        assertEquals("42", output.getElements().get(0));

        output.clear();

        processElement(99, 50);
        advanceWatermark(399);
        assertTrue(output.isEmpty(), "Late element in already-fired window should not produce output");
    }

    @Test
    void testSuccessiveWindowsFireIndependently() throws Exception {
        processElement(1, 10);
        processElement(2, 150);
        processElement(3, 210);

        advanceWatermark(199);
        assertEquals(1, output.size());
        assertEquals("2", output.getElements().get(0));

        output.clear();

        advanceWatermark(399);
        assertEquals(1, output.size());
        assertEquals("3", output.getElements().get(0));
    }

    @Test
    void testNoOutputBeforeWatermarkCrossesBoundary() throws Exception {
        processElement(5, 10);
        processElement(15, 100);

        advanceWatermark(150);
        assertTrue(output.isEmpty(), "Watermark below window.maxTimestamp should not trigger");

        advanceWatermark(198);
        assertTrue(output.isEmpty(), "Watermark at maxTimestamp-1 should not trigger");

        advanceWatermark(199);
        assertFalse(output.isEmpty(), "Watermark at maxTimestamp should trigger");
    }

    @Test
    void testEmptyWindowProducesNoOutput() throws Exception {
        advanceWatermark(199);
        assertTrue(output.isEmpty());

        advanceWatermark(399);
        assertTrue(output.isEmpty());
    }

    @Test
    void testElementExactlyAtWindowBoundary() throws Exception {
        processElement(100, 0);
        processElement(200, 200);

        advanceWatermark(199);
        assertEquals(1, output.size());
        assertEquals("100", output.getElements().get(0));

        output.clear();

        advanceWatermark(399);
        assertEquals(1, output.size());
        assertEquals("200", output.getElements().get(1 - 1));
    }

    static class TestableWindowOperator
            extends WindowOperator<String, Integer, Object, String, TimeWindow> {

        TestableWindowOperator(
                TumblingEventTimeWindows windowAssigner,
                TypeSerializer<TimeWindow> windowSerializer,
                KeySelector<Integer, String> keySelector,
                TypeSerializer<String> keySerializer,
                Class<String> keyClass,
                InternalWindowFunction<Object, String, String, TimeWindow> windowFunction,
                EventTimeTrigger trigger,
                long allowedLateness,
                OutputTag<Integer> lateDataOutputTag) {
            super(windowAssigner, windowSerializer, keySelector, keySerializer, keyClass,
                    windowFunction, trigger, allowedLateness, lateDataOutputTag);
        }

        void advanceInternalWatermark(long timestamp) throws Exception {
            if (internalTimerService instanceof WindowOperatorTimerService) {
                ((WindowOperatorTimerService<String, TimeWindow>) internalTimerService).advanceWatermark(timestamp);
            }
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
        public boolean isImmutableType() {
            return true;
        }

        @Override
        public TypeSerializer<TimeWindow> duplicate() {
            return this;
        }

        @Override
        public TimeWindow createInstance() {
            return new TimeWindow(0, 0);
        }

        @Override
        public TimeWindow copy(TimeWindow from) {
            return new TimeWindow(from.getStart(), from.getEnd());
        }

        @Override
        public TimeWindow copy(TimeWindow from, TimeWindow reuse) {
            return new TimeWindow(from.getStart(), from.getEnd());
        }

        @Override
        public int getLength() {
            return -1;
        }
    }

    static class SimpleStringSerializer implements TypeSerializer<String> {
        private static final long serialVersionUID = 1L;

        @Override
        public boolean isImmutableType() {
            return true;
        }

        @Override
        public TypeSerializer<String> duplicate() {
            return this;
        }

        @Override
        public String createInstance() {
            return "";
        }

        @Override
        public String copy(String from) {
            return from;
        }

        @Override
        public String copy(String from, String reuse) {
            return from;
        }

        @Override
        public int getLength() {
            return -1;
        }
    }

    static class TestOutput<T> implements Output<StreamRecord<T>> {
        private final List<StreamRecord<T>> records = new ArrayList<>();

        @Override
        public void collect(StreamRecord<T> record) {
            records.add(record);
        }

        @Override
        public <X> void collect(OutputTag<X> outputTag, StreamRecord<X> record) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void emitWatermark(io.nop.stream.core.streamrecord.watermark.Watermark mark) {
        }

        @Override
        public void emitWatermarkStatus(io.nop.stream.core.streamrecord.watermark.WatermarkStatus watermarkStatus) {
        }

        @Override
        public void emitLatencyMarker(io.nop.stream.core.streamrecord.LatencyMarker latencyMarker) {
        }

        @Override
        public void emitBarrier(io.nop.stream.core.checkpoint.CheckpointBarrier barrier) {
        }

        @Override
        public void close() {
        }

        List<T> getElements() {
            List<T> elements = new ArrayList<>();
            for (StreamRecord<T> record : records) {
                elements.add(record.getValue());
            }
            return java.util.Collections.unmodifiableList(elements);
        }

        int size() {
            return records.size();
        }

        boolean isEmpty() {
            return records.isEmpty();
        }

        void clear() {
            records.clear();
        }
    }
}
