/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.operators;

import io.nop.stream.core.common.eventtime.WatermarkStrategy;
import io.nop.stream.core.common.eventtime.BoundedOutOfOrdernessWatermarks;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.streamrecord.watermark.Watermark;
import io.nop.stream.core.test.TestOutput;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class TestTimestampsAndWatermarksOperator {

    private TimestampsAndWatermarksOperator<TestEvent> operator;
    private TestOutput<String> rawOutput;
    private Output<StreamRecord<String>> chainedOutput;

    @BeforeEach
    void setUp() throws Exception {
        // Simple strategy: extract timestamp from event, bounded out-of-orderness 100ms
        WatermarkStrategy<TestEvent> strategy = WatermarkStrategy
                .<TestEvent>forBoundedOutOfOrderness(Duration.ofMillis(100))
                .withTimestampAssigner((event, ts) -> event.timestamp);

        operator = new TimestampsAndWatermarksOperator<>(strategy);

        // Use an output that maps TestEvent -> String for testing
        rawOutput = new TestOutput<>();
        operator.setOutput((Output) rawOutput);
        operator.open();
    }

    @Test
    void testTimestampExtraction() throws Exception {
        TestOutput<TestEvent> output = new TestOutput<>();
        operator.setOutput((Output) output);

        TestEvent event = new TestEvent("a", 1000L);
        StreamRecord<TestEvent> record = new StreamRecord<>(event);
        operator.processElement(record);

        List<StreamRecord<TestEvent>> records = output.getRecords();
        assertEquals(1, records.size());
        assertEquals(1000L, records.get(0).getTimestamp());
        assertEquals(event, records.get(0).getValue());
    }

    @Test
    void testWatermarkMonotonicity() throws Exception {
        TestOutput<TestEvent> output = new TestOutput<>();
        operator.setOutput((Output) output);

        operator.processElement(new StreamRecord<>(new TestEvent("a", 1000L)));
        operator.processElement(new StreamRecord<>(new TestEvent("b", 2000L)));

        List<Watermark> watermarks = output.getWatermarks();
        for (int i = 1; i < watermarks.size(); i++) {
            assertTrue(watermarks.get(i).getTimestamp() >= watermarks.get(i - 1).getTimestamp(),
                    "Watermarks must be monotonically increasing");
        }
    }

    @Test
    void testFinishEmitsMaxWatermark() throws Exception {
        TestOutput<TestEvent> output = new TestOutput<>();
        operator.setOutput((Output) output);

        operator.processElement(new StreamRecord<>(new TestEvent("a", 1000L)));
        operator.finish();

        List<Watermark> watermarks = output.getWatermarks();
        assertFalse(watermarks.isEmpty());
        assertEquals(Watermark.MAX_WATERMARK, watermarks.get(watermarks.size() - 1));
    }

    @Test
    void testProcessWatermarkForwards() throws Exception {
        TestOutput<TestEvent> output = new TestOutput<>();
        operator.setOutput((Output) output);

        operator.processWatermark(new Watermark(5000L));
        operator.processWatermark(new Watermark(6000L));

        List<Watermark> watermarks = output.getWatermarks();
        assertTrue(watermarks.stream().anyMatch(w -> w.getTimestamp() == 5000L));
        assertTrue(watermarks.stream().anyMatch(w -> w.getTimestamp() == 6000L));
    }

    @Test
    void testWatermarkDoesNotDecrease() throws Exception {
        TestOutput<TestEvent> output = new TestOutput<>();
        operator.setOutput((Output) output);

        operator.processWatermark(new Watermark(5000L));
        operator.processWatermark(new Watermark(3000L));

        List<Watermark> watermarks = output.getWatermarks();
        assertEquals(1, watermarks.size());
        assertEquals(5000L, watermarks.get(0).getTimestamp());
    }

    static class TestEvent {
        final String id;
        final long timestamp;

        TestEvent(String id, long timestamp) {
            this.id = id;
            this.timestamp = timestamp;
        }
    }
}
