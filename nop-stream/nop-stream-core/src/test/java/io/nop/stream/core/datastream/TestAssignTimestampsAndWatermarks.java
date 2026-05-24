/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.datastream;

import io.nop.stream.core.common.eventtime.WatermarkStrategy;
import io.nop.stream.core.environment.StreamExecutionEnvironment;
import io.nop.stream.core.transformation.TimestampsAndWatermarksTransformation;
import io.nop.stream.core.transformation.Transformation;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

public class TestAssignTimestampsAndWatermarks {

    @Test
    void testAssignTimestampsAndWatermarksCreatesTransformation() {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.createTestEnvironment();

        WatermarkStrategy<String> strategy = WatermarkStrategy
                .<String>forBoundedOutOfOrderness(Duration.ofMillis(100))
                .withTimestampAssigner((event, ts) -> System.currentTimeMillis());

        DataStream<String> source = env.fromElements("a", "b", "c");
        SingleOutputStreamOperator<String> withTimestamps = source.assignTimestampsAndWatermarks(strategy);

        assertInstanceOf(SingleOutputStreamOperatorImpl.class, withTimestamps);

        Transformation<String> transform = ((SingleOutputStreamOperatorImpl<String>) withTimestamps).getTransformation();
        assertInstanceOf(TimestampsAndWatermarksTransformation.class, transform);

        TimestampsAndWatermarksTransformation<String> tsTransform =
                (TimestampsAndWatermarksTransformation<String>) transform;
        assertSame(strategy, tsTransform.getWatermarkStrategy());
    }

    @Test
    void testTransformationChainLinksToInput() {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.createTestEnvironment();

        WatermarkStrategy<String> strategy = WatermarkStrategy
                .<String>forBoundedOutOfOrderness(Duration.ofMillis(100));

        DataStream<String> source = env.fromElements("a", "b", "c");
        Transformation<String> sourceTransform = ((DataStreamImpl<String>) source).getTransformation();

        SingleOutputStreamOperator<String> withTimestamps = source.assignTimestampsAndWatermarks(strategy);
        TimestampsAndWatermarksTransformation<String> tsTransform =
                (TimestampsAndWatermarksTransformation<String>) ((SingleOutputStreamOperatorImpl<String>) withTimestamps).getTransformation();

        assertSame(sourceTransform, tsTransform.getInput());
    }

    @Test
    void testPipelineWithTimestampsAndWatermarks() throws Exception {
        java.util.List<String> results = new java.util.ArrayList<>();
        StreamExecutionEnvironment env = StreamExecutionEnvironment.createTestEnvironment();

        WatermarkStrategy<String> strategy = WatermarkStrategy
                .<String>forBoundedOutOfOrderness(Duration.ofMillis(100))
                .withTimestampAssigner((event, ts) -> System.currentTimeMillis());

        env.fromElements("hello", "world")
                .assignTimestampsAndWatermarks(strategy)
                .map(String::toUpperCase)
                .sink(results::add);

        env.execute("Test Pipeline with Watermarks");
        assertEquals(2, results.size());
        assertTrue(results.contains("HELLO"));
        assertTrue(results.contains("WORLD"));
    }
}
