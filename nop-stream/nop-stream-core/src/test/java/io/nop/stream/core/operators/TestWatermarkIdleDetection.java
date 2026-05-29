/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.operators;

import io.nop.stream.core.common.eventtime.WatermarkGenerator;
import io.nop.stream.core.common.eventtime.WatermarkOutput;
import io.nop.stream.core.common.eventtime.WatermarkStrategy;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.streamrecord.watermark.Watermark;
import io.nop.stream.core.test.TestOutput;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class TestWatermarkIdleDetection {

    @Test
    void testIdleSuppressesWatermarkEmission() throws Exception {
        WatermarkStrategy<String> strategy = WatermarkStrategy
                .<String>forGenerator(ctx -> new IdleTestGenerator())
                .withTimestampAssigner((event, ts) -> Long.parseLong(event.split(":")[1]));

        TestOutput<String> output = new TestOutput<>();
        TimestampsAndWatermarksOperator<String> operator = new TimestampsAndWatermarksOperator<>(strategy, 1);
        operator.setOutput((Output) output);
        operator.open();

        operator.processElement(new StreamRecord<>("e1:100"));
        List<Watermark> wms1 = output.getWatermarks();
        assertFalse(wms1.isEmpty(), "First event should produce a watermark");
        long firstTs = wms1.get(wms1.size() - 1).getTimestamp();
        assertTrue(firstTs > Long.MIN_VALUE, "Watermark should advance past initial value");

        int countBeforeIdle = output.getWatermarks().size();

        operator.processElement(new StreamRecord<>("e2:200"));
        int countAfterIdle = output.getWatermarks().size();
        assertEquals(countBeforeIdle, countAfterIdle,
                "Watermark emission should be suppressed when idle within same event (event 2 calls markIdle)");

        operator.processElement(new StreamRecord<>("e3:300"));
        int countAfterE3 = output.getWatermarks().size();
        assertTrue(countAfterE3 > countAfterIdle,
                "New event resets idle, so event 3 watermark emission should succeed");

        int countBeforeE5 = output.getWatermarks().size();
        operator.processElement(new StreamRecord<>("e4:400"));

        operator.processElement(new StreamRecord<>("e5:500"));
        int countAfterActive = output.getWatermarks().size();
        assertTrue(countAfterActive > countBeforeE5,
                "Watermark should be emitted for event 5");
    }

    @Test
    void testActiveSourceWatermarkAdvances() throws Exception {
        WatermarkStrategy<TestEvent> strategy = WatermarkStrategy
                .<TestEvent>forBoundedOutOfOrderness(Duration.ofMillis(10))
                .withTimestampAssigner((event, ts) -> event.timestamp);

        TestOutput<TestEvent> output = new TestOutput<>();
        TimestampsAndWatermarksOperator<TestEvent> operator = new TimestampsAndWatermarksOperator<>(strategy, 0);
        operator.setOutput((Output) output);
        operator.open();

        operator.processElement(new StreamRecord<>(new TestEvent("a", 100)));
        operator.processElement(new StreamRecord<>(new TestEvent("b", 200)));

        List<Watermark> watermarks = output.getWatermarks();
        assertFalse(watermarks.isEmpty(), "Active source should emit watermarks");
        assertTrue(watermarks.get(watermarks.size() - 1).getTimestamp() > Long.MIN_VALUE,
                "Active source watermark should advance past initial value");
    }

    static class TestEvent {
        final String id;
        final long timestamp;

        TestEvent(String id, long timestamp) {
            this.id = id;
            this.timestamp = timestamp;
        }
    }

    static class IdleTestGenerator implements WatermarkGenerator<String> {
        int eventCount = 0;

        @Override
        public void onEvent(String event, long eventTimestamp, WatermarkOutput output) {
            eventCount++;
            if (eventCount == 1) {
                output.emitWatermark(new Watermark(eventTimestamp));
            } else if (eventCount == 2) {
                output.markIdle();
            } else if (eventCount == 3) {
                output.emitWatermark(new Watermark(eventTimestamp));
            } else if (eventCount == 4) {
                output.markActive();
            } else if (eventCount == 5) {
                output.emitWatermark(new Watermark(eventTimestamp));
            }
        }

        @Override
        public void onPeriodicEmit(WatermarkOutput output) {
        }
    }
}
