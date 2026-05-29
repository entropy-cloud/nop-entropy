package io.nop.stream.core.integration;

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

public class TestSessionWindowE2E {

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

    @Test
    void testSessionWindowMergeAndFire() throws Exception {
        // Session gap = 50ms. Events close together merge into one session.
        // key1 events: ts=10, ts=30 (within gap of each other -> merged session [10,80))
        // Then watermark advances past 80, fires the merged window.
        List<KeyValue> events = Arrays.asList(
                new KeyValue("key1", 5, 10),
                new KeyValue("key1", 7, 30)
        );

        List<Integer> results = Collections.synchronizedList(new ArrayList<>());

        StreamExecutionEnvironment env = StreamExecutionEnvironment.createTestEnvironment();

        WatermarkStrategy<KeyValue> strategy = WatermarkStrategy
                .<KeyValue>forBoundedOutOfOrderness(Duration.ofMillis(0))
                .withTimestampAssigner((event, ts) -> event.timestamp);

        env.fromCollection(events)
                .transform("TimestampsAndWatermarks",
                        (TypeInformation<KeyValue>) UnknownTypeInformation.INSTANCE,
                        new TimestampsAndWatermarksOperator<>(strategy))
                .keyBy((KeySelector<KeyValue, String>) e -> e.key)
                .window(EventTimeSessionWindows.withGap(50))
                .aggregate(new SumAggregateFunction())
                .sink((SinkFunction<Integer>) results::add);

        env.execute("session-window-merge-e2e");

        assertFalse(results.isEmpty(), "Session window should fire after watermark advances past session end");
        assertEquals(1, results.size(), "Merged session should produce one result");
        assertEquals(12, results.get(0), "Sum of merged session should be 5+7=12");
    }

    @Test
    void testSessionWindowNonOverlapping() throws Exception {
        // Two events far apart -> two separate sessions
        List<KeyValue> events = Arrays.asList(
                new KeyValue("key1", 3, 10),
                new KeyValue("key1", 4, 200)
        );

        List<Integer> results = Collections.synchronizedList(new ArrayList<>());

        StreamExecutionEnvironment env = StreamExecutionEnvironment.createTestEnvironment();

        WatermarkStrategy<KeyValue> strategy = WatermarkStrategy
                .<KeyValue>forBoundedOutOfOrderness(Duration.ofMillis(0))
                .withTimestampAssigner((event, ts) -> event.timestamp);

        env.fromCollection(events)
                .transform("TimestampsAndWatermarks",
                        (TypeInformation<KeyValue>) UnknownTypeInformation.INSTANCE,
                        new TimestampsAndWatermarksOperator<>(strategy))
                .keyBy((KeySelector<KeyValue, String>) e -> e.key)
                .window(EventTimeSessionWindows.withGap(50))
                .aggregate(new SumAggregateFunction())
                .sink((SinkFunction<Integer>) results::add);

        env.execute("session-window-non-overlapping-e2e");

        results.sort(Integer::compare);
        assertEquals(2, results.size(), "Two separate sessions should produce two results");
        assertEquals(Arrays.asList(3, 4), results);
    }
}
