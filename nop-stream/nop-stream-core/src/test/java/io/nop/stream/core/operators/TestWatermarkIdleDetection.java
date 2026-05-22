/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.operators;

import io.nop.stream.core.common.eventtime.WatermarkOutput;
import io.nop.stream.core.common.eventtime.WatermarkStrategy;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.streamrecord.watermark.Watermark;
import io.nop.stream.core.streamrecord.watermark.WatermarkStatus;
import io.nop.stream.core.test.TestOutput;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Disabled("Bug N46: markIdle/markActive are no-ops. Fix should pass.")
public class TestWatermarkIdleDetection {

    @Test
    void testIdleSourceDoesNotBlockWatermark() throws Exception {
        WatermarkStrategy<TestEvent> strategy = WatermarkStrategy
                .<TestEvent>forBoundedOutOfOrderness(Duration.ofMillis(10))
                .withTimestampAssigner((event, ts) -> event.timestamp);

        TestOutput<TestEvent> activeOutput = new TestOutput<>();
        TimestampsAndWatermarksOperator<TestEvent> activeOp = new TimestampsAndWatermarksOperator<>(strategy, 1);
        activeOp.setOutput((Output) activeOutput);
        activeOp.open();

        TestOutput<TestEvent> idleOutput = new TestOutput<>();
        TimestampsAndWatermarksOperator<TestEvent> idleOp = new TimestampsAndWatermarksOperator<>(strategy, 1);
        idleOp.setOutput((Output) idleOutput);
        idleOp.open();

        idleOp.processElement(new StreamRecord<>(new TestEvent("idle", 50)));

        activeOp.processElement(new StreamRecord<>(new TestEvent("active", 100)));
        activeOp.processElement(new StreamRecord<>(new TestEvent("active", 200)));

        idleOp.processWatermarkStatus(WatermarkStatus.IDLE);

        List<Watermark> activeWatermarks = activeOutput.getWatermarks();
        assertFalse(activeWatermarks.isEmpty(), "Active source should emit watermarks");

        Watermark lastActive = activeWatermarks.get(activeWatermarks.size() - 1);
        assertTrue(lastActive.getTimestamp() > Long.MIN_VALUE,
                "Active source watermark should advance past initial value");

        activeOp.processElement(new StreamRecord<>(new TestEvent("active", 300)));

        List<Watermark> updatedWatermarks = activeOutput.getWatermarks();
        Watermark latest = updatedWatermarks.get(updatedWatermarks.size() - 1);
        assertTrue(latest.getTimestamp() > lastActive.getTimestamp(),
                "Watermark should keep advancing from active source even after idle source goes idle");
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
