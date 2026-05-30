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
import io.nop.stream.core.test.TestOutput;
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

public class TestWindowOperatorAccType {

    private TestOutput<String> output;
    private StringAccOperator operator;

    @BeforeEach
    void setUp() throws Exception {
        output = new TestOutput<>();

        operator = new StringAccOperator(
                TumblingEventTimeWindows.of(100L),
                new SimpleTimeWindowSerializer(),
                (KeySelector<String, String>) v -> "key1",
                new SimpleStringSerializer(),
                String.class,
                new StringWindowFunction(),
                EventTimeTrigger.create(),
                0L,
                null,
                String.class
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
    void testAccTypePreservedAfterOpen() {
        assertEquals(String.class, operator.getAccClass());
    }

    @Test
    void testDefaultConstructorUsesObjectClass() throws Exception {
        DefaultAccOperator defaultOp = new DefaultAccOperator(
                TumblingEventTimeWindows.of(100L),
                new SimpleTimeWindowSerializer(),
                (KeySelector<String, String>) v -> "key1",
                new SimpleStringSerializer(),
                String.class,
                new ObjectWindowFunction(),
                EventTimeTrigger.create(),
                0L,
                null
        );

        TestOutput<Object> defaultOutput = new TestOutput<>();
        defaultOp.setOutput((Output) defaultOutput);
        defaultOp.open();

        assertEquals(Object.class, defaultOp.getAccClass());

        defaultOp.close();
    }

    @Test
    void testReconstructedOperatorPreservesAccType() throws Exception {
        operator.close();

        StringAccOperator reconstructed = new StringAccOperator(
                TumblingEventTimeWindows.of(100L),
                new SimpleTimeWindowSerializer(),
                (KeySelector<String, String>) v -> "key1",
                new SimpleStringSerializer(),
                String.class,
                new StringWindowFunction(),
                EventTimeTrigger.create(),
                0L,
                null,
                String.class
        );

        TestOutput<String> reconstructedOutput = new TestOutput<>();
        reconstructed.setOutput((Output) reconstructedOutput);
        reconstructed.open();

        assertEquals(String.class, reconstructed.getAccClass());

        reconstructed.processElement(new StreamRecord<>("hello", 10));
        reconstructed.processElement(new StreamRecord<>("world", 20));

        reconstructed.advanceInternalWatermark(99);
        assertEquals(1, reconstructedOutput.size());
        assertEquals("world", reconstructedOutput.getElements().get(0));

        reconstructed.close();
    }

    static class StringAccOperator
            extends WindowOperator<String, String, String, String, TimeWindow> {

        StringAccOperator(
                TumblingEventTimeWindows windowAssigner,
                TypeSerializer<TimeWindow> windowSerializer,
                KeySelector<String, String> keySelector,
                TypeSerializer<String> keySerializer,
                Class<String> keyClass,
                InternalWindowFunction<String, String, String, TimeWindow> windowFunction,
                EventTimeTrigger trigger,
                long allowedLateness,
                OutputTag<String> lateDataOutputTag,
                Class<String> accClass) {
            super(windowAssigner, windowSerializer, keySelector, keySerializer, keyClass,
                    windowFunction, trigger, allowedLateness, lateDataOutputTag, accClass);
        }

        void advanceInternalWatermark(long timestamp) throws Exception {
            if (internalTimerService instanceof WindowOperatorTimerService) {
                ((WindowOperatorTimerService<String, TimeWindow>) internalTimerService).advanceWatermark(timestamp);
            }
        }

        Class<?> getAccClass() {
            return accClass;
        }
    }

    static class DefaultAccOperator
            extends WindowOperator<String, String, Object, Object, TimeWindow> {

        DefaultAccOperator(
                TumblingEventTimeWindows windowAssigner,
                TypeSerializer<TimeWindow> windowSerializer,
                KeySelector<String, String> keySelector,
                TypeSerializer<String> keySerializer,
                Class<String> keyClass,
                InternalWindowFunction<Object, Object, String, TimeWindow> windowFunction,
                EventTimeTrigger trigger,
                long allowedLateness,
                OutputTag<String> lateDataOutputTag) {
            super(windowAssigner, windowSerializer, keySelector, keySerializer, keyClass,
                    windowFunction, trigger, allowedLateness, lateDataOutputTag);
        }

        Class<?> getAccClass() {
            return accClass;
        }
    }

    static class StringWindowFunction
            implements InternalWindowFunction<String, String, String, TimeWindow> {
        private static final long serialVersionUID = 1L;

        @Override
        public void process(String key, TimeWindow window, InternalWindowContext context,
                            String input, io.nop.stream.core.util.Collector<String> out) {
            out.collect(input);
        }

        @Override
        public void clear(TimeWindow window, InternalWindowContext context) {
        }
    }

    static class ObjectWindowFunction
            implements InternalWindowFunction<Object, Object, String, TimeWindow> {
        private static final long serialVersionUID = 1L;

        @Override
        public void process(String key, TimeWindow window, InternalWindowContext context,
                            Object input, io.nop.stream.core.util.Collector<Object> out) {
            out.collect(input);
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
}
