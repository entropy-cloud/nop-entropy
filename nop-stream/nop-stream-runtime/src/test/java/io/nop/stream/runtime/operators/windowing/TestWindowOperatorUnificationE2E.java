/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.operators.windowing;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import io.nop.stream.core.common.eventtime.WatermarkStrategy;
import io.nop.stream.core.common.functions.AggregateFunction;
import io.nop.stream.core.common.functions.KeySelector;
import io.nop.stream.core.common.functions.ProcessWindowFunction;
import io.nop.stream.core.common.functions.ReduceFunction;
import io.nop.stream.core.common.functions.SinkFunction;
import io.nop.stream.core.common.functions.WindowFunction;
import io.nop.stream.core.common.typeinfo.TypeInformation;
import io.nop.stream.core.common.typeinfo.UnknownTypeInformation;
import io.nop.stream.core.datastream.DataStreamImpl;
import io.nop.stream.core.datastream.KeyedStream;
import io.nop.stream.core.datastream.KeyedStreamImpl;
import io.nop.stream.core.datastream.SingleOutputStreamOperator;
import io.nop.stream.core.datastream.WindowedStreamImpl;
import io.nop.stream.core.environment.StreamExecutionEnvironment;
import io.nop.stream.core.model.StreamComponents;
import io.nop.stream.core.operators.IWindowOperatorFactory;
import io.nop.stream.core.operators.TimestampsAndWatermarksOperator;
import io.nop.stream.core.util.Collector;
import io.nop.stream.core.windowing.assigners.TumblingEventTimeWindows;
import io.nop.stream.core.windowing.windows.TimeWindow;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TestWindowOperatorUnificationE2E {

    static final class KeyValue {
        final String key;
        final int value;
        final long timestamp;

        KeyValue(String key, int value, long timestamp) {
            this.key = key;
            this.value = value;
            this.timestamp = timestamp;
        }

        @Override
        public String toString() {
            return "KeyValue{key='" + key + "', value=" + value + ", ts=" + timestamp + '}';
        }
    }

    private static class SumAggregateFunction implements AggregateFunction<KeyValue, int[], Integer> {
        private static final long serialVersionUID = 1L;

        @Override
        public int[] createAccumulator() {
            return new int[]{0};
        }

        @Override
        public int[] add(KeyValue value, int[] accumulator) {
            accumulator[0] += value.value;
            return accumulator;
        }

        @Override
        public Integer getResult(int[] accumulator) {
            return accumulator[0];
        }

        @Override
        public int[] merge(int[] a, int[] b) {
            a[0] += b[0];
            return a;
        }
    }

    private static class SumReduceFunction implements ReduceFunction<Integer> {
        private static final long serialVersionUID = 1L;

        @Override
        public Integer reduce(Integer a, Integer b) {
            return a + b;
        }
    }

    private static class ConcatWindowFunction implements WindowFunction<Integer, String, String, TimeWindow> {
        private static final long serialVersionUID = 1L;

        @Override
        public void apply(String key, TimeWindow window, Iterable<Integer> input, Collector<String> out) {
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

    private static class ConcatProcessWindowFunction implements ProcessWindowFunction<Integer, String, String, TimeWindow> {
        private static final long serialVersionUID = 1L;

        @Override
        public void process(String key, TimeWindow window, Iterable<Integer> input,
                            io.nop.stream.core.common.functions.ProcessWindowFunction.Context context, Collector<String> out) {
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

    @Test
    void testAggregateViaFactory() throws Exception {
        List<KeyValue> events = Arrays.asList(
                new KeyValue("key1", 1, 10),
                new KeyValue("key1", 2, 50),
                new KeyValue("key2", 3, 30),
                new KeyValue("key1", 4, 120),
                new KeyValue("key2", 5, 90),
                new KeyValue("key1", 6, 180),
                new KeyValue("key2", 7, 150),
                new KeyValue("key1", 8, 250)
        );

        List<Integer> results = Collections.synchronizedList(new ArrayList<>());

        StreamExecutionEnvironment env = StreamExecutionEnvironment.createTestEnvironment();
        IWindowOperatorFactory factory = new WindowOperatorFactoryImpl();

        WatermarkStrategy<KeyValue> strategy = WatermarkStrategy
                .<KeyValue>forBoundedOutOfOrderness(Duration.ofMillis(10))
                .withTimestampAssigner((event, ts) -> event.timestamp);

        SingleOutputStreamOperator<KeyValue> timestamped = env.fromCollection(events)
                .transform("TimestampsAndWatermarks",
                        (TypeInformation<KeyValue>) UnknownTypeInformation.INSTANCE,
                        new TimestampsAndWatermarksOperator<>(strategy));

        DataStreamImpl<KeyValue> timestampedImpl = (DataStreamImpl<KeyValue>) timestamped;

        KeyedStream<KeyValue, String> keyed = new KeyedStreamImpl<>(
                timestampedImpl.getEnvironment(),
                timestampedImpl.getTransformation(),
                (KeySelector<KeyValue, String>) e -> e.key);

        StreamComponents components = new StreamComponents();
        components.setWindowOperatorFactory(factory);

        new WindowedStreamImpl<>(keyed, TumblingEventTimeWindows.of(100))
                .withComponents(components)
                .aggregate(new SumAggregateFunction())
                .sink((SinkFunction<Integer>) results::add);

        env.execute("factory-aggregate-e2e");

        results.sort(Integer::compare);

        assertEquals(5, results.size(), "Should produce 5 window results");
        assertEquals(Arrays.asList(3, 7, 8, 8, 10), results);
    }

    @Test
    void testReduceViaFactory() throws Exception {
        List<Integer> results = Collections.synchronizedList(new ArrayList<>());

        StreamExecutionEnvironment env = StreamExecutionEnvironment.createTestEnvironment();
        IWindowOperatorFactory factory = new WindowOperatorFactoryImpl();

        WatermarkStrategy<Integer> strategy = WatermarkStrategy
                .<Integer>forBoundedOutOfOrderness(Duration.ofMillis(10))
                .withTimestampAssigner((event, ts) -> event * 10L);

        SingleOutputStreamOperator<Integer> timestamped = env.fromCollection(Arrays.asList(1, 2, 3, 4, 5))
                .transform("TimestampsAndWatermarks",
                        (TypeInformation<Integer>) UnknownTypeInformation.INSTANCE,
                        new TimestampsAndWatermarksOperator<>(strategy));

        DataStreamImpl<Integer> timestampedImpl = (DataStreamImpl<Integer>) timestamped;

        KeyedStream<Integer, String> keyed = new KeyedStreamImpl<>(
                timestampedImpl.getEnvironment(),
                timestampedImpl.getTransformation(),
                (KeySelector<Integer, String>) v -> "all");

        StreamComponents components = new StreamComponents();
        components.setWindowOperatorFactory(factory);

        new WindowedStreamImpl<>(keyed, TumblingEventTimeWindows.of(100))
                .withComponents(components)
                .reduce(new SumReduceFunction())
                .sink((SinkFunction<Integer>) results::add);

        env.execute("factory-reduce-e2e");

        assertEquals(1, results.size());
        assertEquals(15, results.get(0));
    }

    @Test
    void testApplyViaFactory() throws Exception {
        List<String> results = Collections.synchronizedList(new ArrayList<>());

        StreamExecutionEnvironment env = StreamExecutionEnvironment.createTestEnvironment();
        IWindowOperatorFactory factory = new WindowOperatorFactoryImpl();

        WatermarkStrategy<Integer> strategy = WatermarkStrategy
                .<Integer>forBoundedOutOfOrderness(Duration.ofMillis(10))
                .withTimestampAssigner((event, ts) -> event * 10L);

        SingleOutputStreamOperator<Integer> timestamped = env.fromCollection(Arrays.asList(5, 3, 7))
                .transform("TimestampsAndWatermarks",
                        (TypeInformation<Integer>) UnknownTypeInformation.INSTANCE,
                        new TimestampsAndWatermarksOperator<>(strategy));

        DataStreamImpl<Integer> timestampedImpl = (DataStreamImpl<Integer>) timestamped;

        KeyedStream<Integer, String> keyed = new KeyedStreamImpl<>(
                timestampedImpl.getEnvironment(),
                timestampedImpl.getTransformation(),
                (KeySelector<Integer, String>) v -> "key1");

        StreamComponents components = new StreamComponents();
        components.setWindowOperatorFactory(factory);

        new WindowedStreamImpl<>(keyed, TumblingEventTimeWindows.of(100))
                .withComponents(components)
                .apply(new ConcatWindowFunction())
                .sink((SinkFunction<String>) results::add);

        env.execute("factory-apply-e2e");

        assertEquals(1, results.size());
        assertEquals("5,3,7", results.get(0));
    }

    @Test
    void testProcessViaFactory() throws Exception {
        List<String> results = Collections.synchronizedList(new ArrayList<>());

        StreamExecutionEnvironment env = StreamExecutionEnvironment.createTestEnvironment();
        IWindowOperatorFactory factory = new WindowOperatorFactoryImpl();

        WatermarkStrategy<Integer> strategy = WatermarkStrategy
                .<Integer>forBoundedOutOfOrderness(Duration.ofMillis(10))
                .withTimestampAssigner((event, ts) -> event * 10L);

        SingleOutputStreamOperator<Integer> timestamped = env.fromCollection(Arrays.asList(5, 3, 7))
                .transform("TimestampsAndWatermarks",
                        (TypeInformation<Integer>) UnknownTypeInformation.INSTANCE,
                        new TimestampsAndWatermarksOperator<>(strategy));

        DataStreamImpl<Integer> timestampedImpl = (DataStreamImpl<Integer>) timestamped;

        KeyedStream<Integer, String> keyed = new KeyedStreamImpl<>(
                timestampedImpl.getEnvironment(),
                timestampedImpl.getTransformation(),
                (KeySelector<Integer, String>) v -> "key1");

        StreamComponents components = new StreamComponents();
        components.setWindowOperatorFactory(factory);

        new WindowedStreamImpl<Integer, String, TimeWindow>(keyed, TumblingEventTimeWindows.of(100))
                .withComponents(components)
                .<String>process(new ConcatProcessWindowFunction())
                .sink((SinkFunction<String>) results::add);

        env.execute("factory-process-e2e");

        assertEquals(1, results.size());
        assertEquals("5,3,7", results.get(0));
    }

    @Test
    void testFallbackToOldPathWithoutFactory() throws Exception {
        List<Integer> results = Collections.synchronizedList(new ArrayList<>());

        StreamExecutionEnvironment env = StreamExecutionEnvironment.createTestEnvironment();

        WatermarkStrategy<Integer> strategy = WatermarkStrategy
                .<Integer>forBoundedOutOfOrderness(Duration.ofMillis(10))
                .withTimestampAssigner((event, ts) -> event * 10L);

        env.fromCollection(Arrays.asList(1, 2, 3))
                .transform("TimestampsAndWatermarks",
                        (TypeInformation<Integer>) UnknownTypeInformation.INSTANCE,
                        new TimestampsAndWatermarksOperator<>(strategy))
                .keyBy((KeySelector<Integer, String>) v -> "all")
                .window(TumblingEventTimeWindows.of(100))
                .aggregate(new AggregateFunction<Integer, int[], Integer>() {
                    private static final long serialVersionUID = 1L;

                    @Override
                    public int[] createAccumulator() { return new int[]{0}; }

                    @Override
                    public int[] add(Integer value, int[] accumulator) {
                        accumulator[0] += value;
                        return accumulator;
                    }

                    @Override
                    public Integer getResult(int[] accumulator) { return accumulator[0]; }

                    @Override
                    public int[] merge(int[] a, int[] b) {
                        a[0] += b[0];
                        return a;
                    }
                })
                .sink((SinkFunction<Integer>) results::add);

        env.execute("fallback-old-path");

        assertEquals(1, results.size());
        assertEquals(6, results.get(0));
    }

    @Test
    void testFactoryCreatesCorrectOperatorType() {
        IWindowOperatorFactory factory = new WindowOperatorFactoryImpl();

        io.nop.stream.core.operators.OneInputStreamOperator<Object, Object> operator =
                factory.createAggregateOperator(
                        TumblingEventTimeWindows.of(100),
                        io.nop.stream.core.windowing.triggers.EventTimeTrigger.create(),
                        null,
                        0L,
                        new AggregateFunction<Object, Object, Object>() {
                            private static final long serialVersionUID = 1L;
                            @Override public Object createAccumulator() { return null; }
                            @Override public Object add(Object value, Object accumulator) { return null; }
                            @Override public Object getResult(Object accumulator) { return null; }
                            @Override public Object merge(Object a, Object b) { return null; }
                        },
                        Object.class,
                        (KeySelector<Object, Object>) v -> v,
                        Object.class);

        assertNotNull(operator);
        assertTrue(operator instanceof WindowOperator,
                "Factory should produce WindowOperator instances, got: " + operator.getClass().getName());
    }
}
