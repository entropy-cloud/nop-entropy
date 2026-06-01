/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.nop.stream.runtime.operators.windowing;

import io.nop.stream.core.common.functions.AggregateFunction;
import io.nop.stream.core.common.functions.KeySelector;
import io.nop.stream.core.common.functions.ReduceFunction;
import io.nop.stream.core.common.functions.WindowFunction;
import io.nop.stream.core.common.typeutils.TypeSerializer;
import io.nop.stream.core.operators.InternalTimerService;
import io.nop.stream.core.operators.Output;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.test.TestOutput;
import io.nop.stream.core.util.Collector;
import io.nop.stream.core.windowing.assigners.TumblingEventTimeWindows;
import io.nop.stream.core.windowing.evictors.CountEvictor;
import io.nop.stream.core.windowing.triggers.EventTimeTrigger;
import io.nop.stream.core.windowing.windows.TimeWindow;
import io.nop.stream.runtime.operators.WindowOperatorTimerService;
import io.nop.stream.runtime.operators.windowing.functions.InternalIterableProcessWindowFunction;
import io.nop.stream.runtime.operators.windowing.functions.InternalIterableWindowFunction;
import io.nop.stream.runtime.operators.windowing.functions.InternalSingleValueWindowFunction;
import io.nop.stream.runtime.operators.windowing.functions.InternalWindowFunction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.*;

public class TestWindowOperatorBuilder {

    private static final long WINDOW_SIZE = 100L;

    private TestOutput<String> output;
    private TestableWindowOperator operator;

    @BeforeEach
    void setUp() {
        output = new TestOutput<>();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (operator != null) {
            operator.close();
        }
    }

    @Test
    void testAggregateBuildsCorrectOperator() {
        WindowOperator<String, Integer, long[], String, TimeWindow> op = new WindowOperatorBuilder<Integer, String, TimeWindow>()
                .windowAssigner(TumblingEventTimeWindows.of(WINDOW_SIZE))
                .trigger(EventTimeTrigger.create())
                .keySelector((KeySelector<Integer, String>) v -> "key1")
                .keyClass(String.class)
                .keySerializer(new SimpleStringSerializer())
                .windowSerializer(new SimpleTimeWindowSerializer())
                .aggregate(new SumAggregateFunction(), long[].class);

        assertNotNull(op);
    }

    @Test
    void testReduceBuildsCorrectOperator() {
        WindowOperator<String, Integer, Integer, Integer, TimeWindow> op = new WindowOperatorBuilder<Integer, String, TimeWindow>()
                .windowAssigner(TumblingEventTimeWindows.of(WINDOW_SIZE))
                .trigger(EventTimeTrigger.create())
                .keySelector((KeySelector<Integer, String>) v -> "key1")
                .keyClass(String.class)
                .keySerializer(new SimpleStringSerializer())
                .windowSerializer(new SimpleTimeWindowSerializer())
                .reduce((ReduceFunction<Integer>) (a, b) -> a + b, Integer.class);

        assertNotNull(op);
    }

    @Test
    void testApplyBuildsCorrectOperator() {
        WindowOperator<String, Integer, Iterable<Integer>, String, TimeWindow> op = new WindowOperatorBuilder<Integer, String, TimeWindow>()
                .windowAssigner(TumblingEventTimeWindows.of(WINDOW_SIZE))
                .trigger(EventTimeTrigger.create())
                .keySelector((KeySelector<Integer, String>) v -> "key1")
                .keyClass(String.class)
                .keySerializer(new SimpleStringSerializer())
                .windowSerializer(new SimpleTimeWindowSerializer())
                .apply(new ConcatWindowFunction(), Integer.class);

        assertNotNull(op);
    }

    @Test
    void testAggregateWithEvictorBuildsCorrectOperator() {
        WindowOperator<String, Integer, long[], String, TimeWindow> op = new WindowOperatorBuilder<Integer, String, TimeWindow>()
                .windowAssigner(TumblingEventTimeWindows.of(WINDOW_SIZE))
                .trigger(EventTimeTrigger.create())
                .evictor(CountEvictor.of(3))
                .keySelector((KeySelector<Integer, String>) v -> "key1")
                .keyClass(String.class)
                .keySerializer(new SimpleStringSerializer())
                .windowSerializer(new SimpleTimeWindowSerializer())
                .aggregate(new SumAggregateFunction(), long[].class);

        assertNotNull(op);
    }

    @Test
    @SuppressWarnings("unchecked")
    void testAggregateWindowFiresCorrectly() throws Exception {
        SumAggregateFunction aggFn = new SumAggregateFunction();
        InternalSingleValueWindowFunction<long[], String, String, TimeWindow> windowFn =
                new InternalSingleValueWindowFunction<>((acc, ignored) -> aggFn.getResult(acc));
        BiFunction<long[], long[], long[]> mergeFn = aggFn::merge;

        operator = new TestableWindowOperator(
                TumblingEventTimeWindows.of(WINDOW_SIZE),
                new SimpleTimeWindowSerializer(),
                (KeySelector<Integer, String>) v -> "key1",
                new SimpleStringSerializer(),
                String.class,
                (InternalWindowFunction) windowFn,
                EventTimeTrigger.create(),
                0L,
                null,
                new io.nop.stream.core.common.state.AggregatingStateDescriptor<>("window-contents", aggFn, long[].class),
                (BiFunction) mergeFn);

        operator.setOutput((Output) output);
        operator.open();

        operator.processElement(new StreamRecord<>(5, 10));
        operator.processElement(new StreamRecord<>(3, 20));
        operator.processElement(new StreamRecord<>(7, 50));

        assertTrue(output.isEmpty());

        operator.advanceInternalWatermark(99);
        assertEquals(1, output.size());
        assertEquals("15", output.getElements().get(0));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testReduceWindowFiresCorrectly() throws Exception {
        ReduceFunction<Integer> reduceFn = (a, b) -> a + b;
        AggregateFunction<Integer, Integer, Integer> reduceAsAgg = new AggregateFunction<Integer, Integer, Integer>() {
            private static final long serialVersionUID = 1L;

            @Override
            public Integer createAccumulator() { return null; }

            @Override
            public Integer add(Integer value, Integer accumulator) {
                return accumulator == null ? value : accumulator + value;
            }

            @Override
            public Integer getResult(Integer accumulator) { return accumulator; }

            @Override
            public Integer merge(Integer a, Integer b) {
                if (a == null) return b;
                if (b == null) return a;
                return a + b;
            }
        };

        InternalSingleValueWindowFunction<Integer, String, String, TimeWindow> windowFn =
                new InternalSingleValueWindowFunction<>((acc, ignored) -> String.valueOf(acc));

        operator = new TestableWindowOperator(
                TumblingEventTimeWindows.of(WINDOW_SIZE),
                new SimpleTimeWindowSerializer(),
                (KeySelector<Integer, String>) v -> "key1",
                new SimpleStringSerializer(),
                String.class,
                (InternalWindowFunction) windowFn,
                EventTimeTrigger.create(),
                0L,
                null,
                new io.nop.stream.core.common.state.AggregatingStateDescriptor<>("window-contents", reduceAsAgg, Integer.class),
                (BiFunction) (BiFunction<Integer, Integer, Integer>) (a, b) -> a + b);

        operator.setOutput((Output) output);
        operator.open();

        operator.processElement(new StreamRecord<>(5, 10));
        operator.processElement(new StreamRecord<>(3, 20));
        operator.processElement(new StreamRecord<>(7, 50));

        assertTrue(output.isEmpty());

        operator.advanceInternalWatermark(99);
        assertEquals(1, output.size());
        assertEquals("15", output.getElements().get(0));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testProcessWindowFunctionWithListState() throws Exception {
        ConcatProcessWindowFunction pwf = new ConcatProcessWindowFunction();
        InternalIterableProcessWindowFunction<Integer, String, String, TimeWindow> windowFn =
                new InternalIterableProcessWindowFunction<>(pwf);

        operator = new TestableWindowOperator(
                TumblingEventTimeWindows.of(WINDOW_SIZE),
                new SimpleTimeWindowSerializer(),
                (KeySelector<Integer, String>) v -> "key1",
                new SimpleStringSerializer(),
                String.class,
                (InternalWindowFunction) windowFn,
                EventTimeTrigger.create(),
                0L,
                null,
                new io.nop.stream.core.common.state.ListStateDescriptor<>("window-contents", Integer.class),
                null);

        operator.setOutput((Output) output);
        operator.open();

        operator.processElement(new StreamRecord<>(5, 10));
        operator.processElement(new StreamRecord<>(3, 20));

        assertTrue(output.isEmpty());

        operator.advanceInternalWatermark(99);
        assertEquals(1, output.size());
        assertEquals("5,3", output.getElements().get(0));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testApplyWindowFunctionWithListState() throws Exception {
        ConcatWindowFunction wf = new ConcatWindowFunction();
        InternalIterableWindowFunction<Integer, String, String, TimeWindow> windowFn =
                new InternalIterableWindowFunction<>(wf);

        operator = new TestableWindowOperator(
                TumblingEventTimeWindows.of(WINDOW_SIZE),
                new SimpleTimeWindowSerializer(),
                (KeySelector<Integer, String>) v -> "key1",
                new SimpleStringSerializer(),
                String.class,
                (InternalWindowFunction) windowFn,
                EventTimeTrigger.create(),
                0L,
                null,
                new io.nop.stream.core.common.state.ListStateDescriptor<>("window-contents", Integer.class),
                null);

        operator.setOutput((Output) output);
        operator.open();

        operator.processElement(new StreamRecord<>(5, 10));
        operator.processElement(new StreamRecord<>(3, 20));

        assertTrue(output.isEmpty());

        operator.advanceInternalWatermark(99);
        assertEquals(1, output.size());
        assertEquals("5,3", output.getElements().get(0));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testMultipleWindowsAggregate() throws Exception {
        SumAggregateFunction aggFn = new SumAggregateFunction();
        InternalSingleValueWindowFunction<long[], String, String, TimeWindow> windowFn =
                new InternalSingleValueWindowFunction<>((acc, ignored) -> aggFn.getResult(acc));

        operator = new TestableWindowOperator(
                TumblingEventTimeWindows.of(WINDOW_SIZE),
                new SimpleTimeWindowSerializer(),
                (KeySelector<Integer, String>) v -> "key1",
                new SimpleStringSerializer(),
                String.class,
                (InternalWindowFunction) windowFn,
                EventTimeTrigger.create(),
                0L,
                null,
                new io.nop.stream.core.common.state.AggregatingStateDescriptor<>("window-contents", aggFn, long[].class),
                (BiFunction) (BiFunction<long[], long[], long[]>) aggFn::merge);

        operator.setOutput((Output) output);
        operator.open();

        operator.processElement(new StreamRecord<>(5, 10));
        operator.processElement(new StreamRecord<>(15, 110));

        assertTrue(output.isEmpty());

        operator.advanceInternalWatermark(99);
        assertEquals(1, output.size());
        assertEquals("5", output.getElements().get(0));

        operator.advanceInternalWatermark(199);
        assertEquals(2, output.size());
        assertEquals("15", output.getElements().get(1));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testAggregateWithEvictorBuffersAndFires() throws Exception {
        SumAggregateFunction aggFn = new SumAggregateFunction();
        SumBufferingFunction bufferingFn = new SumBufferingFunction(aggFn);
        InternalIterableProcessWindowFunction<Integer, String, String, TimeWindow> windowFn =
                new InternalIterableProcessWindowFunction<>(bufferingFn);

        operator = new TestableWindowOperator(
                TumblingEventTimeWindows.of(WINDOW_SIZE),
                new SimpleTimeWindowSerializer(),
                (KeySelector<Integer, String>) v -> "key1",
                new SimpleStringSerializer(),
                String.class,
                (InternalWindowFunction) windowFn,
                EventTimeTrigger.create(),
                0L,
                null,
                new io.nop.stream.core.common.state.ListStateDescriptor<>("window-contents", Integer.class),
                null);

        operator.setOutput((Output) output);
        operator.open();

        operator.processElement(new StreamRecord<>(5, 10));
        operator.processElement(new StreamRecord<>(3, 20));
        operator.processElement(new StreamRecord<>(7, 50));

        assertTrue(output.isEmpty());

        operator.advanceInternalWatermark(99);
        assertEquals(1, output.size());
        assertEquals("15", output.getElements().get(0));
    }

    static class TestableWindowOperator extends WindowOperator<String, Integer, Object, String, TimeWindow> {

        @SuppressWarnings("rawtypes")
        public TestableWindowOperator(
                io.nop.stream.core.windowing.assigners.WindowAssigner<? super Integer, TimeWindow> windowAssigner,
                TypeSerializer<TimeWindow> windowSerializer,
                KeySelector<Integer, String> keySelector,
                TypeSerializer<String> keySerializer,
                Class<String> keyClass,
                InternalWindowFunction windowFunction,
                io.nop.stream.core.windowing.triggers.Trigger<? super Integer, ? super TimeWindow> trigger,
                long allowedLateness,
                io.nop.stream.core.util.OutputTag<Integer> lateDataOutputTag,
                io.nop.stream.core.common.state.StateDescriptor<?> windowStateDescriptor,
                BiFunction mergeFunction) {
            super(windowAssigner, windowSerializer, keySelector, keySerializer, keyClass,
                    windowFunction, trigger, allowedLateness, lateDataOutputTag,
                    windowStateDescriptor, mergeFunction);
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

    static class SumAggregateFunction implements AggregateFunction<Integer, long[], String> {
        private static final long serialVersionUID = 1L;

        @Override
        public long[] createAccumulator() {
            return new long[]{0};
        }

        @Override
        public long[] add(Integer value, long[] accumulator) {
            accumulator[0] += value;
            return accumulator;
        }

        @Override
        public String getResult(long[] accumulator) {
            return String.valueOf(accumulator[0]);
        }

        @Override
        public long[] merge(long[] a, long[] b) {
            a[0] += b[0];
            return a;
        }
    }

    static class ConcatWindowFunction implements WindowFunction<Integer, String, String, TimeWindow> {
        private static final long serialVersionUID = 1L;

        @Override
        public void apply(String key, TimeWindow window, Iterable<Integer> input, Collector<String> out) {
            StringBuilder sb = new StringBuilder();
            boolean first = true;
            for (Integer v : input) {
                if (!first) {
                    sb.append(",");
                }
                sb.append(v);
                first = false;
            }
            out.collect(sb.toString());
        }
    }

    static class SumBufferingFunction implements io.nop.stream.core.common.functions.ProcessWindowFunction<Integer, String, String, TimeWindow> {
        private static final long serialVersionUID = 1L;

        private final AggregateFunction<Integer, long[], String> aggregateFunction;

        SumBufferingFunction(AggregateFunction<Integer, long[], String> aggregateFunction) {
            this.aggregateFunction = aggregateFunction;
        }

        @Override
        public void process(String key, TimeWindow window, Iterable<Integer> input,
                            io.nop.stream.core.common.functions.ProcessWindowFunction.Context context,
                            Collector<String> out) {
            long[] acc = aggregateFunction.createAccumulator();
            for (Integer element : input) {
                acc = aggregateFunction.add(element, acc);
            }
            String result = aggregateFunction.getResult(acc);
            if (result != null) {
                out.collect(result);
            }
        }
    }

    static class ConcatProcessWindowFunction implements io.nop.stream.core.common.functions.ProcessWindowFunction<Integer, String, String, TimeWindow> {
        private static final long serialVersionUID = 1L;

        @Override
        public void process(String key, TimeWindow window, Iterable<Integer> input,
                            io.nop.stream.core.common.functions.ProcessWindowFunction.Context context,
                            Collector<String> out) {
            StringBuilder sb = new StringBuilder();
            boolean first = true;
            for (Integer v : input) {
                if (!first) sb.append(",");
                sb.append(v);
                first = false;
            }
            out.collect(sb.toString());
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
