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

import static org.junit.jupiter.api.Assertions.*;

public class TestSessionWindowAdvancedMerge {

    static final class Event {
        final String key;
        final int value;
        final long timestamp;

        Event(String key, int value, long timestamp) {
            this.key = key;
            this.value = value;
            this.timestamp = timestamp;
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
    void testLateElementCausesTwoEstablishedSessionsToMerge() throws Exception {
        List<Event> events = Arrays.asList(
                new Event("a", 10, 10),
                new Event("a", 20, 100),
                new Event("a", 30, 55)
        );

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

        env.execute("late-element-session-merge");

        assertEquals(1, results.size(), "All three events should merge into one session");
        assertEquals(60, results.get(0), "Merged session sum should be 10+20+30=60");
    }

    @Test
    void testThreeWayMerge() throws Exception {
        List<Event> events = Arrays.asList(
                new Event("a", 5, 10),
                new Event("a", 10, 50),
                new Event("a", 15, 90)
        );

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

        env.execute("three-way-merge");

        assertEquals(1, results.size(), "Three overlapping sessions should merge into one");
        assertEquals(30, results.get(0), "Three-way merged sum should be 5+10+15=30");
    }

    @Test
    void testMergedSessionTriggersOnWatermarkAdvance() throws Exception {
        List<Event> events = Arrays.asList(
                new Event("a", 10, 10),
                new Event("a", 20, 100),
                new Event("a", 30, 55)
        );

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

        env.execute("merged-session-watermark-trigger");

        assertEquals(1, results.size(), "Merged session should produce one output after watermark advance");
        assertEquals(60, results.get(0), "Merged session should sum all elements: 10+20+30=60");
    }
}
