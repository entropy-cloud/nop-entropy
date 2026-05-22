/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.execution;

import io.nop.stream.core.common.functions.SinkFunction;
import io.nop.stream.core.environment.StreamExecutionEnvironment;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

public class TestGraphModelExecution {

    @Test
    public void testSingleChainPipeline() throws Exception {
        List<String> results = new ArrayList<>();
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.fromElements("a", "b", "c")
                .map(String::toUpperCase)
                .sink(results::add);
        env.execute("testSingleChainPipeline");

        assertEquals(Arrays.asList("A", "B", "C"), results);
    }

    @Test
    public void testMultiOperatorChain() throws Exception {
        List<Integer> results = new ArrayList<>();
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.fromElements(1, 2, 3, 4, 5)
                .map(x -> x * 10)
                .map(x -> x + 1)
                .filter(x -> x > 20)
                .sink(results::add);
        env.execute("testMultiOperatorChain");

        assertEquals(Arrays.asList(21, 31, 41, 51), results);
    }

    @Test
    public void testWatermarkPropagation() throws Exception {
        AtomicLong lastWatermark = new AtomicLong(Long.MIN_VALUE);
        List<String> results = new ArrayList<>();

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.fromElements("x", "y")
                .map(String::toUpperCase)
                .sink(new SinkFunction<String>() {
                    private static final long serialVersionUID = 1L;

                    @Override
                    public void consume(String value) {
                        results.add(value);
                    }
                });
        env.execute("testWatermarkPropagation");

        assertEquals(Arrays.asList("X", "Y"), results);
    }

    @Test
    public void testMultiChainPipelineExecutes() throws Exception {
        List<Integer> results = new ArrayList<>();
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.fromElements(1, 2, 3)
                .keyBy(x -> x)
                .map(x -> x)
                .sink(results::add);

        env.execute("testMultiChainPipeline");

        assertEquals(Arrays.asList(1, 2, 3), results);
    }

    @Test
    public void testMapFilterSink() throws Exception {
        List<Integer> results = new ArrayList<>();
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.fromElements(1, 2, 3, 4, 5)
                .map(x -> x * 2)
                .filter(x -> x > 4)
                .sink(results::add);
        env.execute("testMapFilterSink");

        assertEquals(Arrays.asList(6, 8, 10), results);
    }

    @Test
    public void testKeyByMapSink() throws Exception {
        List<String> results = new ArrayList<>();
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.fromElements("a", "b", "c")
                .keyBy(x -> x)
                .map(String::toUpperCase)
                .sink(results::add);

        env.execute("testKeyByMapSink");

        assertEquals(Arrays.asList("A", "B", "C"), results);
    }

    @Test
    public void testKeyByMapMultiplier() throws Exception {
        List<Integer> results = new ArrayList<>();
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.fromElements(1, 2, 3)
                .keyBy(x -> x)
                .map(x -> x * 10)
                .sink(results::add);
        env.execute("testKeyByMapMultiplier");

        assertEquals(Arrays.asList(10, 20, 30), results);
    }
}
