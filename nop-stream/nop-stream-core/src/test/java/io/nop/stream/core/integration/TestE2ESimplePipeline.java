/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.integration;

import io.nop.stream.core.environment.StreamExecutionEnvironment;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class TestE2ESimplePipeline {

    @Test
    public void testSourceMapSink() throws Exception {
        List<String> results = new ArrayList<>();
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.fromElements("a", "b", "c").map(String::toUpperCase).sink(results::add);
        env.execute("testSourceMapSink");

        assertEquals(Arrays.asList("A", "B", "C"), results);
    }

    @Test
    public void testSourceFilterSink() throws Exception {
        List<Integer> results = new ArrayList<>();
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.fromElements(1, 2, 3, 4, 5, 6)
                .filter(x -> x % 2 == 0)
                .sink(results::add);
        env.execute("testSourceFilterSink");

        assertEquals(Arrays.asList(2, 4, 6), results);
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
    public void testEmptySource() throws Exception {
        List<String> results = new ArrayList<>();
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.fromElements("keep", "drop")
                .filter(x -> false)
                .sink(results::add);
        env.execute("testEmptySource");

        assertEquals(Collections.emptyList(), results);
    }
}
