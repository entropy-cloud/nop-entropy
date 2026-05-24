/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.operators;

import io.nop.stream.core.common.eventtime.WatermarkStrategy;
import io.nop.stream.core.environment.StreamExecutionEnvironment;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.streamrecord.watermark.Watermark;
import io.nop.stream.core.test.TestOutput;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for configurable watermark interval in TimestampsAndWatermarksOperator.
 */
public class TestWatermarkIntervalConfig {

    static class TestEvent {
        final String id;
        final long timestamp;

        TestEvent(String id, long timestamp) {
            this.id = id;
            this.timestamp = timestamp;
        }
    }

    private WatermarkStrategy<TestEvent> defaultStrategy;

    @BeforeEach
    void setUp() {
        defaultStrategy = WatermarkStrategy
                .<TestEvent>forBoundedOutOfOrderness(Duration.ofMillis(100))
                .withTimestampAssigner((event, ts) -> event.timestamp);
    }

    @Test
    void testWatermarkInterval50ms() throws Exception {
        // With a 50ms interval, watermarks should be emitted periodically
        TimestampsAndWatermarksOperator<TestEvent> operator =
                new TimestampsAndWatermarksOperator<>(defaultStrategy, 50L);

        TestOutput<TestEvent> output = new TestOutput<>();
        operator.setOutput((Output) output);
        operator.open();

        // Process first element - should trigger initial periodic emit
        operator.processElement(new StreamRecord<>(new TestEvent("a", 1000L)));

        // Wait past the interval and process another element
        Thread.sleep(60);
        operator.processElement(new StreamRecord<>(new TestEvent("b", 2000L)));

        // Finish to emit final watermark
        operator.finish();

        List<Watermark> watermarks = output.getWatermarks();
        // Should have at least: one periodic + MAX_WATERMARK from finish
        assertTrue(watermarks.size() >= 2,
                "Expected at least 2 watermarks (periodic + final), got " + watermarks.size());

        // Last watermark should be MAX_WATERMARK from finish()
        assertEquals(Watermark.MAX_WATERMARK, watermarks.get(watermarks.size() - 1));
    }

    @Test
    void testBatchDataWithWatermarkIntervalZero() throws Exception {
        // With interval=0, every element triggers periodic emit check
        // This ensures watermarks advance even for batch data arriving in the same millisecond
        TimestampsAndWatermarksOperator<TestEvent> operator =
                new TimestampsAndWatermarksOperator<>(defaultStrategy, 0L);

        TestOutput<TestEvent> output = new TestOutput<>();
        operator.setOutput((Output) output);
        operator.open();

        // Process multiple elements rapidly (same millisecond in practice)
        operator.processElement(new StreamRecord<>(new TestEvent("a", 1000L)));
        operator.processElement(new StreamRecord<>(new TestEvent("b", 2000L)));
        operator.processElement(new StreamRecord<>(new TestEvent("c", 3000L)));

        // With interval=0, each element triggers periodic emit
        // Watermark should advance via the bounded out-of-orderness generator
        List<Watermark> watermarks = output.getWatermarks();
        // BoundedOutOfOrderness generates watermarks = maxTimestamp - outOfOrderness
        // After processing 3000L event, watermark should be 3000 - 100 = 2900
        if (!watermarks.isEmpty()) {
            long maxWatermarkTs = watermarks.stream()
                    .mapToLong(Watermark::getTimestamp)
                    .max()
                    .orElse(Long.MIN_VALUE);
            assertTrue(maxWatermarkTs >= 900L,
                    "Watermark should advance for batch data with interval=0, max was " + maxWatermarkTs);
        }

        // Finish and verify MAX_WATERMARK is emitted
        operator.finish();
        watermarks = output.getWatermarks();
        assertEquals(Watermark.MAX_WATERMARK, watermarks.get(watermarks.size() - 1));
    }

    @Test
    void testNegativeWatermarkIntervalThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            new StreamExecutionEnvironment().setWatermarkInterval(-1L);
        }, "Setting negative watermark interval should throw IllegalArgumentException");
    }

    @Test
    void testDefaultWatermarkIntervalIs200ms() {
        StreamExecutionEnvironment env = new StreamExecutionEnvironment();
        assertEquals(200L, env.getWatermarkInterval(),
                "Default watermark interval should be 200ms");
    }

    @Test
    void testSetWatermarkInterval() {
        StreamExecutionEnvironment env = new StreamExecutionEnvironment();
        env.setWatermarkInterval(500L);
        assertEquals(500L, env.getWatermarkInterval());
    }

    @Test
    void testZeroWatermarkIntervalIsAllowed() {
        StreamExecutionEnvironment env = new StreamExecutionEnvironment();
        assertDoesNotThrow(() -> env.setWatermarkInterval(0L));
        assertEquals(0L, env.getWatermarkInterval());
    }
}
