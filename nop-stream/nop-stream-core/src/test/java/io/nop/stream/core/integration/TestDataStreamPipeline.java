/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.integration;

import io.nop.stream.core.common.functions.FlatMapFunction;
import io.nop.stream.core.environment.StreamExecutionEnvironment;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class TestDataStreamPipeline {

    @Test
    public void testSourceMapFilterSink() throws Exception {
        List<String> results = new ArrayList<>();
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.fromElements("hello", "world", "hi")
                .map(String::toUpperCase)
                .filter(s -> s.length() > 2)
                .sink(results::add);
        env.execute("Test Pipeline");

        assertEquals(Arrays.asList("HELLO", "WORLD"), results);
    }

    @Test
    public void testSourceFilterSink() throws Exception {
        List<Integer> results = new ArrayList<>();
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.fromElements(1, 2, 3, 4, 5)
                .filter(x -> x % 2 == 0)
                .sink(results::add);
        env.execute("Filter Test");

        assertEquals(Arrays.asList(2, 4), results);
    }

    @Test
    public void testSourceFlatMapSink() throws Exception {
        List<String> results = new ArrayList<>();
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.fromElements("hello world", "foo bar")
                .flatMap((FlatMapFunction<String, String>) (value, out) -> {
                    for (String word : value.split(" ")) {
                        out.collect(word);
                    }
                })
                .sink(results::add);
        env.execute("FlatMap Test");

        assertEquals(Arrays.asList("hello", "world", "foo", "bar"), results);
    }

    @Test
    public void testSourceMapMapFilterSink() throws Exception {
        List<Integer> results = new ArrayList<>();
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.fromElements(1, 2, 3, 4, 5)
                .map(x -> x * 10)
                .map(x -> x + 1)
                .filter(x -> x > 25)
                .sink(results::add);
        env.execute("Chain Test");

        assertEquals(Arrays.asList(31, 41, 51), results);
    }

    @Test
    public void testFromCollection() throws Exception {
        List<String> results = new ArrayList<>();
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.fromCollection(Arrays.asList("a", "b", "c"))
                .map(String::toUpperCase)
                .sink(results::add);
        env.execute("Collection Source Test");

        assertEquals(Arrays.asList("A", "B", "C"), results);
    }

    @Test
    public void testExecutionResult() throws Exception {
        List<Integer> results = new ArrayList<>();
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.fromElements(1, 2, 3).sink(results::add);
        var result = env.execute("Result Test");

        assertNotNull(result);
        assertEquals("Result Test", result.getJobName());
        assertTrue(result.getExecutionTime() >= 0);
        assertEquals(Arrays.asList(1, 2, 3), results);
    }

    @Test
    public void testCannotExecuteTwice() throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.fromElements(1).sink(x -> {});
        env.execute("First");
        assertThrows(IllegalStateException.class, () -> env.execute("Second"));
    }
}
