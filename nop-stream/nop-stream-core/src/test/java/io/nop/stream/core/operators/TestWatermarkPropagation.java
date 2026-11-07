/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.operators;

import io.nop.api.core.time.CoreMetrics;
import io.nop.commons.test.time.ManualClock;
import io.nop.stream.core.common.eventtime.WatermarkStrategy;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.streamrecord.watermark.Watermark;
import io.nop.stream.core.test.TestOutput;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Watermark propagation tests (plan 2510 时间确定化)：processElement 的 inline 周期 emit
 * 门经 {@code CoreMetrics} 读取，测试注册 {@link ManualClock}——相邻元素的处理时刻错开由
 * 显式 {@code clock.advance(delta)} 完成，不再等待真实时间流逝。
 */
public class TestWatermarkPropagation {

    static final long OUT_OF_ORDERNESS_MS = 10;

    private TimestampsAndWatermarksOperator<Event> tsOperator;
    private TestOutput<Event> output;
    private ManualClock clock;

    static class Event {
        final String key;
        final long timestamp;

        Event(String key, long timestamp) {
            this.key = key;
            this.timestamp = timestamp;
        }

        long getTimestamp() {
            return timestamp;
        }
    }

    private WatermarkStrategy<Event> createStrategy() {
        return WatermarkStrategy
                .<Event>forBoundedOutOfOrderness(Duration.ofMillis(OUT_OF_ORDERNESS_MS))
                .withTimestampAssigner((event, ts) -> event.getTimestamp());
    }

    @BeforeEach
    void setUp() throws Exception {
        WatermarkStrategy<Event> strategy = createStrategy();
        tsOperator = new TimestampsAndWatermarksOperator<>(strategy, 1);
        output = new TestOutput<>();
        tsOperator.setOutput((Output) output);
        clock = new ManualClock(1_000_000L);
        CoreMetrics.registerClock(clock);
        tsOperator.open();
    }

    @AfterEach
    void restoreDefaultClock() {
        CoreMetrics.registerClock(CoreMetrics.defaultClock());
    }

    @Test
    void testWatermarkMonotonicallyIncreasing() throws Exception {
        tsOperator.processElement(new StreamRecord<>(new Event("a", 100)));
        tsOperator.processElement(new StreamRecord<>(new Event("b", 200)));
        tsOperator.processElement(new StreamRecord<>(new Event("c", 300)));

        List<Watermark> watermarks = output.getWatermarks();
        for (int i = 1; i < watermarks.size(); i++) {
            assertTrue(watermarks.get(i).getTimestamp() >= watermarks.get(i - 1).getTimestamp(),
                    "Watermarks must be monotonically increasing");
        }
    }

    @Test
    void testWatermarkForwardedToDownstream() throws Exception {
        // 处理时间语义：相邻元素的处理时刻错开由虚拟时钟推进完成（inline emit 门读
        // CoreMetrics，时钟不推进则间隔门必然关闭），不再等待真实时间流逝。
        tsOperator.processElement(new StreamRecord<>(new Event("a", 100)));
        clock.advance(2);
        tsOperator.processElement(new StreamRecord<>(new Event("b", 200)));
        clock.advance(2);
        tsOperator.processElement(new StreamRecord<>(new Event("c", 300)));
        clock.advance(2);

        List<Watermark> watermarks = output.getWatermarks();
        assertFalse(watermarks.isEmpty(), "Should have emitted at least one watermark");

        for (Watermark wm : watermarks) {
            assertTrue(wm.getTimestamp() <= 300 - OUT_OF_ORDERNESS_MS - 1,
                    "Watermark should be <= (maxTimestamp - outOfOrderness - 1)");
        }

        Watermark last = watermarks.get(watermarks.size() - 1);
        assertEquals(300 - OUT_OF_ORDERNESS_MS - 1, last.getTimestamp(),
                "Last watermark should be maxTimestamp - outOfOrderness - 1");
    }

    @Test
    void testNoDuplicateWatermarks() throws Exception {
        tsOperator.processElement(new StreamRecord<>(new Event("a", 100)));
        tsOperator.processElement(new StreamRecord<>(new Event("b", 100)));
        tsOperator.processElement(new StreamRecord<>(new Event("c", 100)));

        List<Watermark> watermarks = output.getWatermarks();
        if (watermarks.size() > 1) {
            for (int i = 1; i < watermarks.size(); i++) {
                assertTrue(watermarks.get(i).getTimestamp() > watermarks.get(i - 1).getTimestamp(),
                        "Watermarks with same event timestamps should not produce duplicates");
            }
        }
    }
}
