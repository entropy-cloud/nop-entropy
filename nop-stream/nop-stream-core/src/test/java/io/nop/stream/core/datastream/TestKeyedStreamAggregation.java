/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.datastream;

import io.nop.stream.core.common.functions.KeySelector;
import io.nop.stream.core.common.functions.ReduceFunction;
import io.nop.stream.core.common.functions.SinkFunction;
import io.nop.stream.core.common.typeinfo.UnknownTypeInformation;
import io.nop.stream.core.environment.StreamExecutionEnvironment;
import io.nop.stream.core.operators.StreamReduceOperator;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class TestKeyedStreamAggregation {

    public static class Item {
        public String key;
        public int value;

        public Item(String key, int value) {
            this.key = key;
            this.value = value;
        }
    }

    private <T> SinkFunction<T> collectingSink(List<T> list) {
        return value -> list.add(value);
    }

    @Test
    void testSumAggregation() throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.createTestEnvironment();
        List<Integer> results = new ArrayList<>();

        env.fromElements(1, 2, 3, 4, 5, 6)
                .keyBy(i -> i % 2)
                .sum(0)
                .collect(collectingSink(results));

        env.execute();

        assertEquals(Arrays.asList(1, 2, 4, 6, 9, 12), results);
    }

    @Test
    void testMinMaxAggregation() throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.createTestEnvironment();
        List<Integer> minResults = new ArrayList<>();

        env.fromElements(3, 1, 4, 1, 5, 9)
                .keyBy(i -> i % 2)
                .min(0)
                .collect(collectingSink(minResults));

        env.execute();

        // keys: 3%2=1, 1%2=1, 4%2=0, 1%2=1, 5%2=1, 9%2=1
        // key 1: 3,1,1,5,9 -> min: 3,1,1,1,1   key 0: 4 -> min: 4
        assertEquals(Arrays.asList(3, 1, 4, 1, 1, 1), minResults);

        StreamExecutionEnvironment env2 = StreamExecutionEnvironment.createTestEnvironment();
        List<Integer> maxResults = new ArrayList<>();

        env2.fromElements(3, 1, 4, 1, 5, 9)
                .keyBy(i -> i % 2)
                .max(0)
                .collect(collectingSink(maxResults));

        env2.execute();

        // key 1: 3,1,1,5,9 -> max: 3,3,3,5,9   key 0: 4 -> max: 4
        assertEquals(Arrays.asList(3, 3, 4, 3, 5, 9), maxResults);
    }

    @Test
    void testReduceCustomAggregation() throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.createTestEnvironment();
        List<String> results = new ArrayList<>();

        ReduceFunction<String> concatReducer = (v1, v2) -> v1 + "|" + v2;

        env.fromElements("a", "b", "a", "b", "a")
                .keyBy(s -> s)
                .reduce(concatReducer)
                .collect(collectingSink(results));

        env.execute();

        assertEquals(Arrays.asList("a", "b", "a|a", "b|b", "a|a|a"), results);
    }

    @Test
    void testNonKeyedStreamReduceRejected() {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.createTestEnvironment();

        StreamReduceOperator<Integer> reduceOp = new StreamReduceOperator<>((ReduceFunction<Integer>) (v1, v2) -> v1 + v2);
        env.fromElements(1, 2, 3)
                .transform("Reduce",
                        (UnknownTypeInformation<Integer>) (UnknownTypeInformation<?>) UnknownTypeInformation.INSTANCE,
                        reduceOp)
                .collect(v -> {});

        assertThrows(Exception.class, () -> env.execute());
    }

    @Test
    void testSumByFieldName() throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.createTestEnvironment();
        List<Integer> results = new ArrayList<>();

        env.fromElements(
                        new Item("a", 1),
                        new Item("b", 2),
                        new Item("a", 3),
                        new Item("b", 4),
                        new Item("a", 5))
                .keyBy((KeySelector<Item, String>) item -> item.key)
                .sum("value")
                .collect(v -> results.add(((Item) v).value));

        env.execute();

        assertEquals(Arrays.asList(1, 2, 4, 6, 9), results);
    }

    @Test
    void testMinByFieldName() throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.createTestEnvironment();
        List<Integer> results = new ArrayList<>();

        env.fromElements(
                        new Item("x", 5),
                        new Item("y", 3),
                        new Item("x", 2),
                        new Item("y", 7))
                .keyBy((KeySelector<Item, String>) item -> item.key)
                .min("value")
                .collect(v -> results.add(((Item) v).value));

        env.execute();

        assertEquals(Arrays.asList(5, 3, 2, 3), results);
    }

    @Test
    void testMaxByFieldName() throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.createTestEnvironment();
        List<Integer> results = new ArrayList<>();

        env.fromElements(
                        new Item("x", 5),
                        new Item("y", 3),
                        new Item("x", 2),
                        new Item("y", 7))
                .keyBy((KeySelector<Item, String>) item -> item.key)
                .max("value")
                .collect(v -> results.add(((Item) v).value));

        env.execute();

        assertEquals(Arrays.asList(5, 3, 5, 7), results);
    }
}
