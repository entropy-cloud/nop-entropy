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

/**
 * E2E tests verifying that graph model path produces the same output as fast path.
 */
public class TestE2ESimplePipeline {

    @Test
    public void testSourceMapSink() throws Exception {
        // Fast path
        List<String> fastResults = new ArrayList<>();
        StreamExecutionEnvironment env1 = StreamExecutionEnvironment.getExecutionEnvironment();
        env1.fromElements("a", "b", "c").map(String::toUpperCase).sink(fastResults::add);
        env1.execute("fast");

        // Graph model path
        List<String> graphResults = new ArrayList<>();
        StreamExecutionEnvironment env2 = StreamExecutionEnvironment.getExecutionEnvironment();
        env2.fromElements("a", "b", "c").map(String::toUpperCase).sink(graphResults::add);
        env2.executeWithGraphModel("graph");

        assertEquals(fastResults, graphResults);
        assertEquals(Arrays.asList("A", "B", "C"), graphResults);
    }

    @Test
    public void testSourceFilterSink() throws Exception {
        // Fast path
        List<Integer> fastResults = new ArrayList<>();
        StreamExecutionEnvironment env1 = StreamExecutionEnvironment.getExecutionEnvironment();
        env1.fromElements(1, 2, 3, 4, 5, 6)
                .filter(x -> x % 2 == 0)
                .sink(fastResults::add);
        env1.execute("fast");

        // Graph model path
        List<Integer> graphResults = new ArrayList<>();
        StreamExecutionEnvironment env2 = StreamExecutionEnvironment.getExecutionEnvironment();
        env2.fromElements(1, 2, 3, 4, 5, 6)
                .filter(x -> x % 2 == 0)
                .sink(graphResults::add);
        env2.executeWithGraphModel("graph");

        assertEquals(fastResults, graphResults);
        assertEquals(Arrays.asList(2, 4, 6), graphResults);
    }

    @Test
    public void testMultiOperatorChain() throws Exception {
        // Fast path
        List<Integer> fastResults = new ArrayList<>();
        StreamExecutionEnvironment env1 = StreamExecutionEnvironment.getExecutionEnvironment();
        env1.fromElements(1, 2, 3, 4, 5)
                .map(x -> x * 10)
                .map(x -> x + 1)
                .filter(x -> x > 20)
                .sink(fastResults::add);
        env1.execute("fast");

        // Graph model path
        List<Integer> graphResults = new ArrayList<>();
        StreamExecutionEnvironment env2 = StreamExecutionEnvironment.getExecutionEnvironment();
        env2.fromElements(1, 2, 3, 4, 5)
                .map(x -> x * 10)
                .map(x -> x + 1)
                .filter(x -> x > 20)
                .sink(graphResults::add);
        env2.executeWithGraphModel("graph");

        assertEquals(fastResults, graphResults);
        assertEquals(Arrays.asList(21, 31, 41, 51), graphResults);
    }

    @Test
    public void testEmptySource() throws Exception {
        // Fast path - use fromCollection with a single placeholder and filter all
        List<String> fastResults = new ArrayList<>();
        StreamExecutionEnvironment env1 = StreamExecutionEnvironment.getExecutionEnvironment();
        env1.fromElements("keep", "drop")
                .filter(x -> false)
                .sink(fastResults::add);
        env1.execute("fast");

        // Graph model path
        List<String> graphResults = new ArrayList<>();
        StreamExecutionEnvironment env2 = StreamExecutionEnvironment.getExecutionEnvironment();
        env2.fromElements("keep", "drop")
                .filter(x -> false)
                .sink(graphResults::add);
        env2.executeWithGraphModel("graph");

        assertEquals(fastResults, graphResults);
        assertEquals(Collections.emptyList(), graphResults);
    }
}
