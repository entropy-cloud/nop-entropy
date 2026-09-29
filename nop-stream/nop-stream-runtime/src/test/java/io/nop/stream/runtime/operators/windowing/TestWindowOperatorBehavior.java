/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:   https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.operators.windowing;

import io.nop.stream.core.common.functions.KeySelector;
import io.nop.stream.core.operators.Output;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.test.TestOutput;
import io.nop.stream.core.windowing.assigners.TumblingEventTimeWindows;
import io.nop.stream.core.windowing.triggers.EventTimeTrigger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestWindowOperatorBehavior {

    private static final long WINDOW_SIZE = 200L;

    private TestOutput<String> output;
    private WindowingTestSupport.TestableWindowOperator operator;

    @BeforeEach
    void setUp() throws Exception {
        output = new TestOutput<>();

        operator = new WindowingTestSupport.TestableWindowOperator(
                TumblingEventTimeWindows.of(WINDOW_SIZE),
                new WindowingTestSupport.SimpleTimeWindowSerializer(),
                (KeySelector<Integer, String>) v -> "key1",
                new WindowingTestSupport.SimpleStringSerializer(),
                String.class,
                new WindowingTestSupport.ToStringWindowFunction<>(),
                EventTimeTrigger.create(),
                0L,
                null
        );

        operator.setOutput((Output) output);
        operator.open();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (operator != null) {
            operator.close();
        }
    }

    private void processElement(int value, long timestamp) throws Exception {
        operator.processElement(new StreamRecord<>(value, timestamp));
    }

    private void advanceWatermark(long timestamp) throws Exception {
        operator.advanceInternalWatermark(timestamp);
    }

    @Test
    void testTumblingWindowTriggersAtBoundary() throws Exception {
        processElement(10, 50);
        processElement(20, 100);
        processElement(30, 150);

        assertTrue(output.isEmpty());

        advanceWatermark(199);
        assertEquals(1, output.size());
        assertEquals("30", output.getElements().get(0));
    }

    @Test
    void testWindowCleanupPreventsReFiring() throws Exception {
        processElement(42, 50);
        advanceWatermark(199);
        assertEquals(1, output.size());
        assertEquals("42", output.getElements().get(0));

        output.clear();

        processElement(99, 50);
        advanceWatermark(399);
        assertTrue(output.isEmpty(), "Late element in already-fired window should not produce output");
    }

    @Test
    void testSuccessiveWindowsFireIndependently() throws Exception {
        processElement(1, 10);
        processElement(2, 150);
        processElement(3, 210);

        advanceWatermark(199);
        assertEquals(1, output.size());
        assertEquals("2", output.getElements().get(0));

        output.clear();

        advanceWatermark(399);
        assertEquals(1, output.size());
        assertEquals("3", output.getElements().get(0));
    }

    @Test
    void testNoOutputBeforeWatermarkCrossesBoundary() throws Exception {
        processElement(5, 10);
        processElement(15, 100);

        advanceWatermark(150);
        assertTrue(output.isEmpty(), "Watermark below window.maxTimestamp should not trigger");

        advanceWatermark(198);
        assertTrue(output.isEmpty(), "Watermark at maxTimestamp-1 should not trigger");

        advanceWatermark(199);
        assertFalse(output.isEmpty(), "Watermark at maxTimestamp should trigger");
    }

    @Test
    void testEmptyWindowProducesNoOutput() throws Exception {
        advanceWatermark(199);
        assertTrue(output.isEmpty());

        advanceWatermark(399);
        assertTrue(output.isEmpty());
    }

    @Test
    void testElementExactlyAtWindowBoundary() throws Exception {
        processElement(100, 0);
        processElement(200, 200);

        advanceWatermark(199);
        assertEquals(1, output.size());
        assertEquals("100", output.getElements().get(0));

        output.clear();

        advanceWatermark(399);
        assertEquals(1, output.size());
        assertEquals("200", output.getElements().get(1 - 1));
    }
}
