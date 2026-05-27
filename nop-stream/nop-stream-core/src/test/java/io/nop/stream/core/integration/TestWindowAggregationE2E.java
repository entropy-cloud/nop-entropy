/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.integration;

import io.nop.stream.core.common.eventtime.WatermarkStrategy;
import io.nop.stream.core.common.functions.AggregateFunction;
import io.nop.stream.core.common.functions.KeySelector;
import io.nop.stream.core.common.functions.SinkFunction;
import io.nop.stream.core.common.typeinfo.TypeInformation;
import io.nop.stream.core.common.typeinfo.UnknownTypeInformation;
import io.nop.stream.core.environment.StreamExecutionEnvironment;
import io.nop.stream.core.operators.TimestampsAndWatermarksOperator;
import io.nop.stream.core.windowing.assigners.TumblingEventTimeWindows;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end integration test for windowed sum aggregation:
 * source → assignTimestampsAndWatermarks → keyBy → window(TumblingEventTimeWindows) → aggregate(sum) → sink.
 */
public class TestWindowAggregationE2E {

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
    void testWindowSumAggregation() throws Exception {
        // Window size = 100ms (TumblingEventTimeWindows.of(100))
        // Data layout:
        //   Window [0,100):  key1(1,2) sum=3,  key2(3,5) sum=8
        //   Window [100,200): key1(4,6) sum=10, key2(7)   sum=7
        //   Window [200,300): key1(8)   sum=8
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

        WatermarkStrategy<KeyValue> strategy = WatermarkStrategy
                .<KeyValue>forBoundedOutOfOrderness(Duration.ofMillis(10))
                .withTimestampAssigner((event, ts) -> event.timestamp);

        env.fromCollection(events)
                .transform("TimestampsAndWatermarks",
                        (TypeInformation<KeyValue>) UnknownTypeInformation.INSTANCE,
                        new TimestampsAndWatermarksOperator<>(strategy))
                .keyBy((KeySelector<KeyValue, String>) e -> e.key)
                .window(TumblingEventTimeWindows.of(100))
                .aggregate(new SumAggregateFunction())
                .sink((SinkFunction<Integer>) results::add);

        env.execute("window-sum-aggregation-e2e");

        // Sort results for deterministic comparison
        results.sort(Integer::compare);

        assertEquals(5, results.size(), "Should produce 5 window results (3 for key1, 2 for key2)");

        // Sorted aggregate sums: 3(key1@[0,100)), 7(key2@[100,200)), 8(key1@[200,300)), 8(key2@[0,100)), 10(key1@[100,200))
        List<Integer> expected = Arrays.asList(3, 7, 8, 8, 10);
        assertEquals(expected, results);
    }

    @Test
    void testWindowSumSingleKey() throws Exception {
        // Single key, two windows
        List<KeyValue> events = Arrays.asList(
                new KeyValue("a", 10, 10),
                new KeyValue("a", 20, 60),
                new KeyValue("a", 30, 120),
                new KeyValue("a", 40, 180)
        );

        List<Integer> results = Collections.synchronizedList(new ArrayList<>());

        StreamExecutionEnvironment env = StreamExecutionEnvironment.createTestEnvironment();

        WatermarkStrategy<KeyValue> strategy = WatermarkStrategy
                .<KeyValue>forBoundedOutOfOrderness(Duration.ofMillis(10))
                .withTimestampAssigner((event, ts) -> event.timestamp);

        env.fromCollection(events)
                .transform("TimestampsAndWatermarks",
                        (TypeInformation<KeyValue>) UnknownTypeInformation.INSTANCE,
                        new TimestampsAndWatermarksOperator<>(strategy))
                .keyBy((KeySelector<KeyValue, String>) e -> e.key)
                .window(TumblingEventTimeWindows.of(100))
                .aggregate(new SumAggregateFunction())
                .sink((SinkFunction<Integer>) results::add);

        env.execute("window-sum-single-key");

        results.sort(Integer::compare);

        assertEquals(2, results.size());
        // Window [0,100): 10+20=30  Window [100,200): 30+40=70
        assertEquals(Arrays.asList(30, 70), results);
    }

    @Test
    void testWindowSumMultipleKeysDisjoint() throws Exception {
        // Two keys with non-overlapping windows
        List<KeyValue> events = Arrays.asList(
                new KeyValue("x", 5, 10),
                new KeyValue("y", 15, 10),
                new KeyValue("x", 7, 50),
                new KeyValue("y", 3, 50)
        );

        List<Integer> results = Collections.synchronizedList(new ArrayList<>());

        StreamExecutionEnvironment env = StreamExecutionEnvironment.createTestEnvironment();

        WatermarkStrategy<KeyValue> strategy = WatermarkStrategy
                .<KeyValue>forBoundedOutOfOrderness(Duration.ofMillis(10))
                .withTimestampAssigner((event, ts) -> event.timestamp);

        env.fromCollection(events)
                .transform("TimestampsAndWatermarks",
                        (TypeInformation<KeyValue>) UnknownTypeInformation.INSTANCE,
                        new TimestampsAndWatermarksOperator<>(strategy))
                .keyBy((KeySelector<KeyValue, String>) e -> e.key)
                .window(TumblingEventTimeWindows.of(100))
                .aggregate(new SumAggregateFunction())
                .sink((SinkFunction<Integer>) results::add);

        env.execute("window-sum-multiple-keys");

        results.sort(Integer::compare);

        assertEquals(2, results.size());
        // x: 5+7=12, y: 15+3=18
        assertEquals(Arrays.asList(12, 18), results);
    }
}
