package io.nop.stream.runtime.operators.windowing;

import io.nop.stream.core.common.eventtime.WatermarkStrategy;
import io.nop.stream.core.common.functions.AggregateFunction;
import io.nop.stream.core.common.functions.KeySelector;
import io.nop.stream.core.common.functions.SinkFunction;
import io.nop.stream.core.common.typeinfo.TypeInformation;
import io.nop.stream.core.common.typeinfo.UnknownTypeInformation;
import io.nop.stream.core.environment.StreamExecutionEnvironment;
import io.nop.stream.core.operators.TimestampsAndWatermarksOperator;
import io.nop.stream.core.windowing.assigners.EventTimeSessionWindows;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class TestSessionWindowWithPeriodicWatermark {

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

    static final class SessionResult {
        final String key;
        final int sum;
        final long windowStart;
        final long windowEnd;

        SessionResult(String key, int sum, long windowStart, long windowEnd) {
            this.key = key;
            this.sum = sum;
            this.windowStart = windowStart;
            this.windowEnd = windowEnd;
        }

        @Override
        public String toString() {
            return key + ":[" + windowStart + "," + windowEnd + ")=" + sum;
        }
    }

    private static class SumAggregateFunction implements AggregateFunction<Event, int[], Integer> {
        @Override
        public int[] createAccumulator() { return new int[]{0}; }

        @Override
        public int[] add(Event value, int[] accumulator) {
            accumulator[0] += value.value;
            return accumulator;
        }

        @Override
        public Integer getResult(int[] accumulator) { return accumulator[0]; }

        @Override
        public int[] merge(int[] a, int[] b) {
            a[0] += b[0];
            return a;
        }
    }

    @Test
    void testSingleElementSession() throws Exception {
        List<Event> events = Collections.singletonList(
                new Event("key1", 42, 100));

        List<Integer> results = Collections.synchronizedList(new ArrayList<>());

        StreamExecutionEnvironment env = StreamExecutionEnvironment.createTestEnvironment();
        WatermarkStrategy<Event> strategy = WatermarkStrategy
                .<Event>forBoundedOutOfOrderness(Duration.ofMillis(0))
                .withTimestampAssigner((e, ts) -> e.timestamp);

        env.fromCollection(events)
                .transform("TimestampsAndWatermarks",
                        (TypeInformation<Event>) UnknownTypeInformation.INSTANCE,
                        new TimestampsAndWatermarksOperator<>(strategy, 50))
                .keyBy((KeySelector<Event, String>) e -> e.key)
                .window(EventTimeSessionWindows.withGap(50))
                .aggregate(new SumAggregateFunction())
                .sink((SinkFunction<Integer>) results::add);

        env.execute("single-element-session");

        assertEquals(1, results.size());
        assertEquals(42, results.get(0));
    }

    @Test
    void testAdjacentEventsMerge() throws Exception {
        List<Event> events = Arrays.asList(
                new Event("key1", 3, 100),
                new Event("key1", 5, 130),
                new Event("key1", 2, 160));

        List<Integer> results = Collections.synchronizedList(new ArrayList<>());

        StreamExecutionEnvironment env = StreamExecutionEnvironment.createTestEnvironment();
        WatermarkStrategy<Event> strategy = WatermarkStrategy
                .<Event>forBoundedOutOfOrderness(Duration.ofMillis(0))
                .withTimestampAssigner((e, ts) -> e.timestamp);

        env.fromCollection(events)
                .transform("TimestampsAndWatermarks",
                        (TypeInformation<Event>) UnknownTypeInformation.INSTANCE,
                        new TimestampsAndWatermarksOperator<>(strategy, 50))
                .keyBy((KeySelector<Event, String>) e -> e.key)
                .window(EventTimeSessionWindows.withGap(50))
                .aggregate(new SumAggregateFunction())
                .sink((SinkFunction<Integer>) results::add);

        env.execute("adjacent-events-merge");

        assertEquals(1, results.size(), "Adjacent events within gap should merge into one session");
        assertEquals(10, results.get(0), "Sum should be 3+5+2=10");
    }

    @Test
    void testMultiKeyIndependentSessions() throws Exception {
        List<Event> events = Arrays.asList(
                new Event("key1", 5, 100),
                new Event("key2", 3, 100),
                new Event("key1", 7, 140),
                new Event("key2", 4, 110));

        List<Integer> results = Collections.synchronizedList(new ArrayList<>());

        StreamExecutionEnvironment env = StreamExecutionEnvironment.createTestEnvironment();
        WatermarkStrategy<Event> strategy = WatermarkStrategy
                .<Event>forBoundedOutOfOrderness(Duration.ofMillis(0))
                .withTimestampAssigner((e, ts) -> e.timestamp);

        env.fromCollection(events)
                .transform("TimestampsAndWatermarks",
                        (TypeInformation<Event>) UnknownTypeInformation.INSTANCE,
                        new TimestampsAndWatermarksOperator<>(strategy, 50))
                .keyBy((KeySelector<Event, String>) e -> e.key)
                .window(EventTimeSessionWindows.withGap(50))
                .aggregate(new SumAggregateFunction())
                .sink((SinkFunction<Integer>) results::add);

        env.execute("multi-key-sessions");

        results.sort(Integer::compare);
        assertEquals(2, results.size(), "Two keys should produce two merged session results");
        assertTrue(results.contains(12), "key1 session should sum 5+7=12");
        assertTrue(results.contains(7), "key2 session should sum 3+4=7");
    }

    @Test
    void testSessionWindowStateMigrationOnMerge() throws Exception {
        List<Event> events = Arrays.asList(
                new Event("key1", 10, 100),
                new Event("key1", 20, 140),
                new Event("key1", 30, 170));

        List<Integer> results = Collections.synchronizedList(new ArrayList<>());

        StreamExecutionEnvironment env = StreamExecutionEnvironment.createTestEnvironment();
        WatermarkStrategy<Event> strategy = WatermarkStrategy
                .<Event>forBoundedOutOfOrderness(Duration.ofMillis(0))
                .withTimestampAssigner((e, ts) -> e.timestamp);

        env.fromCollection(events)
                .transform("TimestampsAndWatermarks",
                        (TypeInformation<Event>) UnknownTypeInformation.INSTANCE,
                        new TimestampsAndWatermarksOperator<>(strategy, 50))
                .keyBy((KeySelector<Event, String>) e -> e.key)
                .window(EventTimeSessionWindows.withGap(50))
                .aggregate(new SumAggregateFunction())
                .sink((SinkFunction<Integer>) results::add);

        env.execute("session-state-merge");

        assertEquals(1, results.size(), "All events within gap should merge into one session");
        assertEquals(60, results.get(0), "Merged session state should preserve all values: 10+20+30=60");
    }
}
