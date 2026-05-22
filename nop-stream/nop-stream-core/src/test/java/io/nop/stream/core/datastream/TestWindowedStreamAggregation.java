package io.nop.stream.core.datastream;

import io.nop.stream.core.common.eventtime.WatermarkStrategy;
import io.nop.stream.core.common.functions.AggregateFunction;
import io.nop.stream.core.common.functions.KeySelector;
import io.nop.stream.core.common.functions.ReduceFunction;
import io.nop.stream.core.common.functions.SinkFunction;
import io.nop.stream.core.common.functions.WindowFunction;
import io.nop.stream.core.common.typeinfo.TypeInformation;
import io.nop.stream.core.common.typeinfo.UnknownTypeInformation;
import io.nop.stream.core.environment.StreamExecutionEnvironment;
import io.nop.stream.core.operators.TimestampsAndWatermarksOperator;
import io.nop.stream.core.operators.WindowAggregationOperator;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.util.Collector;
import io.nop.stream.core.windowing.assigners.TumblingEventTimeWindows;
import io.nop.stream.core.windowing.windows.TimeWindow;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class TestWindowedStreamAggregation {

    static final class Event {
        final String key;
        final int value;
        final long timestamp;

        Event(String key, int value, long timestamp) {
            this.key = key;
            this.value = value;
            this.timestamp = timestamp;
        }

        @Override
        public String toString() {
            return "Event{key='" + key + "', value=" + value + ", ts=" + timestamp + '}';
        }
    }

    @Test
    void testReduceDoesNotThrow() {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        List<Event> events = Arrays.asList(
                new Event("a", 1, 10),
                new Event("a", 2, 20));

        assertDoesNotThrow(() -> {
            env.fromCollection(events)
                    .transform("TimestampsAndWatermarks",
                            (TypeInformation<Event>) UnknownTypeInformation.INSTANCE,
                            new TimestampsAndWatermarksOperator<>(WatermarkStrategy
                                    .<Event>forBoundedOutOfOrderness(Duration.ofMillis(10))
                                    .withTimestampAssigner((e, ts) -> e.timestamp)))
                    .keyBy((KeySelector<Event, String>) e -> e.key)
                    .window(TumblingEventTimeWindows.of(100))
                    .reduce((ReduceFunction<Event>) (a, b) -> new Event(a.key, a.value + b.value, a.timestamp))
                    .sink(e -> {});
        });
    }

    @Test
    void testAggregateDoesNotThrow() {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        List<Event> events = Arrays.asList(
                new Event("a", 1, 10),
                new Event("a", 2, 20));

        assertDoesNotThrow(() -> {
            env.fromCollection(events)
                    .transform("TimestampsAndWatermarks",
                            (TypeInformation<Event>) UnknownTypeInformation.INSTANCE,
                            new TimestampsAndWatermarksOperator<>(WatermarkStrategy
                                    .<Event>forBoundedOutOfOrderness(Duration.ofMillis(10))
                                    .withTimestampAssigner((e, ts) -> e.timestamp)))
                    .keyBy((KeySelector<Event, String>) e -> e.key)
                    .window(TumblingEventTimeWindows.of(100))
                    .aggregate(new SumAggregateFunction())
                    .sink(e -> {});
        });
    }

    @Test
    void testApplyDoesNotThrow() {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        List<Event> events = Arrays.asList(
                new Event("a", 1, 10),
                new Event("a", 2, 20));

        assertDoesNotThrow(() -> {
            env.fromCollection(events)
                    .transform("TimestampsAndWatermarks",
                            (TypeInformation<Event>) UnknownTypeInformation.INSTANCE,
                            new TimestampsAndWatermarksOperator<>(WatermarkStrategy
                                    .<Event>forBoundedOutOfOrderness(Duration.ofMillis(10))
                                    .withTimestampAssigner((e, ts) -> e.timestamp)))
                    .keyBy((KeySelector<Event, String>) e -> e.key)
                    .window(TumblingEventTimeWindows.of(100))
                    .apply(new SumWindowFunction())
                    .sink(e -> {});
        });
    }

    @Test
    void testReduceE2EWithTumblingWindows() throws Exception {
        List<Event> events = Arrays.asList(
                new Event("key1", 1, 10),
                new Event("key1", 2, 50),
                new Event("key2", 3, 30),
                new Event("key1", 4, 120),
                new Event("key2", 5, 90),
                new Event("key1", 6, 180),
                new Event("key2", 7, 150),
                new Event("key1", 8, 250));

        List<Event> results = Collections.synchronizedList(new ArrayList<>());

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        WatermarkStrategy<Event> strategy = WatermarkStrategy
                .<Event>forBoundedOutOfOrderness(Duration.ofMillis(10))
                .withTimestampAssigner((e, ts) -> e.timestamp);

        env.fromCollection(events)
                .transform("TimestampsAndWatermarks",
                        (TypeInformation<Event>) UnknownTypeInformation.INSTANCE,
                        new TimestampsAndWatermarksOperator<>(strategy))
                .keyBy((KeySelector<Event, String>) e -> e.key)
                .window(TumblingEventTimeWindows.of(100))
                .reduce((ReduceFunction<Event>) (a, b) -> new Event(a.key, a.value + b.value, a.timestamp))
                .sink((SinkFunction<Event>) results::add);

        env.execute("reduce-e2e");

        results.sort(Comparator.comparing((Event e) -> e.key).thenComparingLong(e -> e.timestamp));

        assertEquals(5, results.size());

        assertEquals("key1", results.get(0).key);
        assertEquals(3, results.get(0).value);

        assertEquals("key1", results.get(1).key);
        assertEquals(10, results.get(1).value);

        assertEquals("key1", results.get(2).key);
        assertEquals(8, results.get(2).value);

        assertEquals("key2", results.get(3).key);
        assertEquals(8, results.get(3).value);

        assertEquals("key2", results.get(4).key);
        assertEquals(7, results.get(4).value);
    }

    @Test
    void testAggregateE2EWithTumblingWindows() throws Exception {
        List<Event> events = Arrays.asList(
                new Event("key1", 1, 10),
                new Event("key1", 2, 50),
                new Event("key2", 3, 30),
                new Event("key1", 4, 120),
                new Event("key2", 5, 90),
                new Event("key1", 6, 180),
                new Event("key2", 7, 150),
                new Event("key1", 8, 250));

        List<Integer> results = Collections.synchronizedList(new ArrayList<>());

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        WatermarkStrategy<Event> strategy = WatermarkStrategy
                .<Event>forBoundedOutOfOrderness(Duration.ofMillis(10))
                .withTimestampAssigner((e, ts) -> e.timestamp);

        env.fromCollection(events)
                .transform("TimestampsAndWatermarks",
                        (TypeInformation<Event>) UnknownTypeInformation.INSTANCE,
                        new TimestampsAndWatermarksOperator<>(strategy))
                .keyBy((KeySelector<Event, String>) e -> e.key)
                .window(TumblingEventTimeWindows.of(100))
                .aggregate(new SumAggregateFunction())
                .sink((SinkFunction<Integer>) results::add);

        env.execute("aggregate-e2e");

        results.sort(Integer::compare);

        assertEquals(5, results.size());

        List<Integer> expected = Arrays.asList(3, 7, 8, 8, 10);
        assertEquals(expected, results);
    }

    @Test
    void testApplyE2EWithTumblingWindows() throws Exception {
        List<Event> events = Arrays.asList(
                new Event("key1", 1, 10),
                new Event("key1", 2, 50),
                new Event("key2", 3, 30),
                new Event("key1", 4, 120),
                new Event("key2", 5, 90),
                new Event("key1", 6, 180),
                new Event("key2", 7, 150),
                new Event("key1", 8, 250));

        List<String> results = Collections.synchronizedList(new ArrayList<>());

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        WatermarkStrategy<Event> strategy = WatermarkStrategy
                .<Event>forBoundedOutOfOrderness(Duration.ofMillis(10))
                .withTimestampAssigner((e, ts) -> e.timestamp);

        env.fromCollection(events)
                .transform("TimestampsAndWatermarks",
                        (TypeInformation<Event>) UnknownTypeInformation.INSTANCE,
                        new TimestampsAndWatermarksOperator<>(strategy))
                .keyBy((KeySelector<Event, String>) e -> e.key)
                .window(TumblingEventTimeWindows.of(100))
                .apply(new WindowFunction<Event, String, String, TimeWindow>() {
                    private static final long serialVersionUID = 1L;

                    @Override
                    public void apply(String key, TimeWindow window, Iterable<Event> input,
                                      Collector<String> out) {
                        int sum = 0;
                        for (Event e : input) {
                            sum += e.value;
                        }
                        out.collect(key + ":" + sum);
                    }
                })
                .sink((SinkFunction<String>) results::add);

        env.execute("apply-e2e");

        results.sort(String::compareTo);

        assertEquals(5, results.size());

        List<String> expected = Arrays.asList("key1:10", "key1:3", "key1:8", "key2:7", "key2:8");
        Collections.sort(expected);
        assertEquals(expected, results);
    }

    @Test
    void testReduceCreatesCorrectTransformation() {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        List<Event> events = Collections.singletonList(new Event("a", 1, 10));

        SingleOutputStreamOperator<Event> result = env.fromCollection(events)
                .keyBy((KeySelector<Event, String>) e -> e.key)
                .window(TumblingEventTimeWindows.of(100))
                .reduce((ReduceFunction<Event>) (a, b) -> a);

        assertNotNull(result);
        assertTrue(result instanceof DataStreamImpl);
        io.nop.stream.core.transformation.Transformation<?> tx =
                ((DataStreamImpl<Event>) result).getTransformation();
        assertNotNull(tx);
        assertEquals("WindowReduce", tx.getName());
    }

    @Test
    void testAggregateCreatesCorrectTransformation() {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        List<Event> events = Collections.singletonList(new Event("a", 1, 10));

        SingleOutputStreamOperator<Integer> result = env.fromCollection(events)
                .keyBy((KeySelector<Event, String>) e -> e.key)
                .window(TumblingEventTimeWindows.of(100))
                .aggregate(new SumAggregateFunction());

        assertNotNull(result);
        assertTrue(result instanceof DataStreamImpl);
        io.nop.stream.core.transformation.Transformation<?> tx =
                ((DataStreamImpl<Integer>) result).getTransformation();
        assertNotNull(tx);
        assertEquals("WindowAggregate", tx.getName());
    }

    private static class SumAggregateFunction implements AggregateFunction<Event, int[], Integer> {
        private static final long serialVersionUID = 1L;

        @Override
        public int[] createAccumulator() {
            return new int[]{0};
        }

        @Override
        public int[] add(Event value, int[] accumulator) {
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

    private static class SumWindowFunction implements WindowFunction<Event, Integer, String, TimeWindow> {
        private static final long serialVersionUID = 1L;

        @Override
        public void apply(String key, TimeWindow window, Iterable<Event> input,
                          Collector<Integer> out) {
            int sum = 0;
            for (Event e : input) {
                sum += e.value;
            }
            out.collect(sum);
        }
    }
}
