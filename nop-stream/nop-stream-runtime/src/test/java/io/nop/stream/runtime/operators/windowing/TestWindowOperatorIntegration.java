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
import io.nop.stream.core.operators.InternalTimerService;
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

public class TestWindowOperatorIntegration {

    private static final long WINDOW_SIZE = 100L;
    private static final long ALLOWED_LATENESS = 0L;

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
                ALLOWED_LATENESS,
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
    void testWatermarkTriggersWindow() throws Exception {
        processElement(5, 10);
        processElement(3, 20);
        processElement(7, 50);

        assertTrue(output.isEmpty());

        advanceWatermark(98);
        assertTrue(output.isEmpty());

        advanceWatermark(99);
        assertEquals(1, output.size());
        assertEquals("7", output.getElements().get(0));
    }

    @Test
    void testLastWriteWinsForNonAccumulator() throws Exception {
        processElement(5, 10);
        processElement(3, 20);
        processElement(7, 50);

        advanceWatermark(100);

        assertEquals(1, output.size());
        assertEquals("7", output.getElements().get(0));
    }

    @Test
    void testMultipleWindows() throws Exception {
        processElement(5, 10);
        processElement(15, 110);

        assertTrue(output.isEmpty());

        advanceWatermark(100);
        assertEquals(1, output.size());
        assertEquals("5", output.getElements().get(0));

        advanceWatermark(200);
        assertEquals(2, output.size());
        assertEquals("15", output.getElements().get(1));
    }

    @Test
    void testLateDataDropped() throws Exception {
        processElement(5, 10);
        advanceWatermark(100);
        assertEquals(1, output.size());

        output.clear();

        processElement(99, 50);
        advanceWatermark(150);

        assertTrue(output.isEmpty());
    }

    @Test
    void testAllowedLateness() throws Exception {
        operator.close();

        operator = new TestableWindowOperator(
                TumblingEventTimeWindows.of(WINDOW_SIZE),
                new SimpleTimeWindowSerializer(),
                (KeySelector<Integer, String>) v -> "key1",
                new SimpleStringSerializer(),
                String.class,
                new ToStringWindowFunction(),
                EventTimeTrigger.create(),
                50L,
                null
        );
        operator.setOutput((Output) output);
        operator.open();

        processElement(5, 10);
        advanceWatermark(100);
        assertEquals(1, output.size());
        assertEquals("5", output.getElements().get(0));
        output.clear();

        processElement(99, 50);
        advanceWatermark(149);
        assertFalse(output.isEmpty());

        output.clear();
        processElement(88, 50);
        advanceWatermark(200);
        assertTrue(output.isEmpty());
    }

    @Test
    void testMultiKeyIsolation() throws Exception {
        processElement(10, 10);
        processElement(20, 110);

        advanceWatermark(99);
        assertEquals(1, output.size());
        assertEquals("10", output.getElements().get(0));

        advanceWatermark(199);
        assertEquals(2, output.size());
        assertEquals("20", output.getElements().get(1));
    }

    @Test
    void testWindowCleanupAfterFiring() throws Exception {
        processElement(42, 10);
        advanceWatermark(100);
        assertEquals(1, output.size());
        assertEquals("42", output.getElements().get(0));
        output.clear();

        processElement(99, 10);
        advanceWatermark(200);
        assertTrue(output.isEmpty());
    }

    @Test
    void testNoOutputWithoutWatermark() throws Exception {
        processElement(5, 10);
        processElement(15, 50);
        processElement(25, 90);

        assertTrue(output.isEmpty());

        advanceWatermark(98);
        assertTrue(output.isEmpty());

        advanceWatermark(99);
        assertFalse(output.isEmpty());
    }

    static class TestableWindowOperator extends WindowOperator<String, Integer, Object, String, TimeWindow> {

        public TestableWindowOperator(
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

        public void advanceInternalWatermark(long timestamp) throws Exception {
            if (internalTimerService instanceof WindowOperatorTimerService) {
                ((WindowOperatorTimerService<String, TimeWindow>) internalTimerService).advanceWatermark(timestamp);
            }
        }

        public InternalTimerService<TimeWindow> getInternalTimerService() {
            return internalTimerService;
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
        private final List<StreamRecord<?>> sideOutputs = new ArrayList<>();

        @Override
        public void collect(StreamRecord<T> record) {
            records.add(record);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <X> void collect(OutputTag<X> outputTag, StreamRecord<X> record) {
            sideOutputs.add(record);
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

        public List<T> getElements() {
            List<T> elements = new ArrayList<>();
            for (StreamRecord<T> record : records) {
                elements.add(record.getValue());
            }
            return java.util.Collections.unmodifiableList(elements);
        }

        public int size() {
            return records.size();
        }

        public boolean isEmpty() {
            return records.isEmpty();
        }

        public void clear() {
            records.clear();
            sideOutputs.clear();
        }

        public List<StreamRecord<?>> getSideOutputs() {
            return sideOutputs;
        }
    }

    @Test
    void testLateDataOutputTag() throws Exception {
        operator.close();

        OutputTag<Integer> lateTag = new OutputTag<>("late-data",
                io.nop.stream.core.common.typeinfo.BasicTypeInfo.INT);

        operator = new TestableWindowOperator(
                TumblingEventTimeWindows.of(WINDOW_SIZE),
                new SimpleTimeWindowSerializer(),
                (KeySelector<Integer, String>) v -> "key1",
                new SimpleStringSerializer(),
                String.class,
                new ToStringWindowFunction(),
                EventTimeTrigger.create(),
                0L,
                lateTag
        );
        operator.setOutput((Output) output);
        operator.open();

        processElement(5, 10);
        advanceWatermark(100);
        assertEquals(1, output.size());
        assertEquals("5", output.getElements().get(0));
        output.clear();

        processElement(99, 50);
        advanceWatermark(150);

        assertTrue(output.isEmpty(), "Late data should not appear in main output");
        assertFalse(output.getSideOutputs().isEmpty(),
                "Late data should be collected in side output");
        assertEquals(99, output.getSideOutputs().get(0).getValue());
    }
}
