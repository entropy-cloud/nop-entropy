/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.operators.windowing;

import io.nop.stream.core.common.functions.KeySelector;
import io.nop.stream.core.metrics.StreamMetricsRegistries;
import io.nop.stream.core.operators.Output;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.test.TestOutput;
import io.nop.stream.core.util.OutputTag;
import io.nop.stream.core.windowing.assigners.TumblingEventTimeWindows;
import io.nop.stream.core.windowing.triggers.EventTimeTrigger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R5-TE-09: the two user-visible late-data paths of {@link WindowOperator} —
 * both previously untested (every existing test passed {@code allowedLateness=0}
 * and a {@code null} late-data output tag):
 * <ol>
 *   <li>{@code allowedLateness > 0}: a record arriving after the window FIRED
 *       but before the cleanup deadline is accepted into the window and the
 *       window fires again — never silently dropped;</li>
 *   <li>a record past {@code window.maxTimestamp + allowedLateness} is routed
 *       to the configured late-data side output (not dropped, not counted as
 *       dropped).</li>
 * </ol>
 */
public class TestWindowOperatorAllowedLateness {

    private static final long WINDOW_SIZE = 100L;
    private static final long ALLOWED_LATENESS = 50L;

    private TestOutput<String> output;
    private WindowingTestSupport.TestableWindowOperator operator;

    @BeforeEach
    void setUp() throws Exception {
        output = new TestOutput<>();
        operator = newOperator(null);
        operator.setOutput((Output) output);
        operator.open();
    }

    private WindowingTestSupport.TestableWindowOperator newOperator(OutputTag<Integer> lateTag) {
        return new WindowingTestSupport.TestableWindowOperator(
                TumblingEventTimeWindows.of(WINDOW_SIZE),
                new WindowingTestSupport.SimpleTimeWindowSerializer(),
                (KeySelector<Integer, String>) v -> "key1",
                new WindowingTestSupport.SimpleStringSerializer(),
                String.class,
                new WindowingTestSupport.ToStringWindowFunction<>(),
                EventTimeTrigger.create(),
                ALLOWED_LATENESS,
                lateTag
        );
    }

    @AfterEach
    void tearDown() throws Exception {
        if (operator != null) {
            operator.close();
        }
    }

    private double lateDroppedCount() {
        io.micrometer.core.instrument.Counter counter =
                StreamMetricsRegistries.registry().find("numLateRecordsDropped").counter();
        return counter == null ? 0.0 : counter.count();
    }

    /**
     * allowedLateness=50: window [0,100) fires at watermark 100, cleanup at 150.
     * A record at ts=60 arriving at watermark 100 satisfies 60+50 &gt; 100, so it
     * must be ACCEPTED — the window re-fires and emits it — instead of being
     * discarded as late.
     */
    @Test
    void recordWithinAllowedLatenessIsAcceptedAfterFire() throws Exception {
        operator.processElement(new StreamRecord<>(5, 10));
        operator.advanceInternalWatermark(100);
        assertEquals(1, output.size(), "window must fire on the watermark");
        assertEquals("5", output.get(0));
        output.clear();

        double beforeDropped = lateDroppedCount();

        // ts=60: 60 + 50 = 110 > watermark 100 → within allowed lateness.
        operator.processElement(new StreamRecord<>(7, 60));
        // The re-created pane's fire timer (maxTimestamp=99, already due) fires on
        // the next watermark advance; cleanup sits at 100+50=150 and must NOT have
        // discarded the pane yet.
        operator.advanceInternalWatermark(149);

        assertTrue(output.getElements().contains("7"),
                "a record within allowedLateness must be accepted after the window fired; "
                        + "output=" + output.getElements());
        assertEquals(beforeDropped, lateDroppedCount(),
                "an accepted within-lateness record must not be counted as dropped");
    }

    /**
     * Contrast (same operator shape, allowedLateness=0): the same record at
     * watermark 100 is late and — with no side output — dropped.
     */
    @Test
    void sameRecordIsLateWhenAllowedLatenessIsZero() throws Exception {
        output.clear();
        operator.close();

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

        operator.processElement(new StreamRecord<>(5, 10));
        operator.advanceInternalWatermark(100);
        assertEquals(1, output.size());
        output.clear();

        // ts=60 + 0 <= watermark 100 → late → dropped.
        operator.processElement(new StreamRecord<>(7, 60));
        operator.advanceInternalWatermark(149);

        assertEquals(0, output.size(),
                "with allowedLateness=0 the same record must be dropped, not emitted");
    }

    /**
     * With a late-data OutputTag configured, a record past
     * {@code ts + allowedLateness <= watermark} is routed to the side output —
     * it neither reaches the main output nor increments the dropped counter.
     */
    @Test
    void recordPastAllowedLatenessGoesToSideOutput() throws Exception {
        operator.close();
        OutputTag<Integer> lateTag = new OutputTag<>("late-records",
                io.nop.stream.core.common.typeinfo.BasicTypeInfo.INT);
        operator = newOperator(lateTag);
        operator.setOutput((Output) output);
        operator.open();

        double beforeDropped = lateDroppedCount();

        operator.processElement(new StreamRecord<>(5, 10));
        operator.advanceInternalWatermark(100);
        assertEquals(1, output.size(), "window must fire");
        output.clear();

        // Advance past the window's cleanup deadline (end 100 + lateness 50 = 150):
        // the window is now closed — it can no longer absorb records.
        operator.advanceInternalWatermark(160);

        // ts=40 in closed window [0,100): isWindowLate (150 <= 160) skips the
        // window; isElementLate (40+50=90 <= 160) routes the record to the
        // configured side output.
        operator.processElement(new StreamRecord<>(9, 40));

        assertEquals(0, output.size(),
                "a past-lateness record must not reach the main output");
        assertEquals(1, output.getSideOutputs().size(),
                "a past-lateness record must be routed to the configured side output");
        StreamRecord<?> sideRecord = output.getSideOutputs().get(0);
        assertNotNull(sideRecord);
        assertEquals(9, sideRecord.getValue(), "side output carries the late record's value");
        assertEquals(40L, sideRecord.getTimestamp(), "side output carries the late record's timestamp");
        assertEquals(beforeDropped, lateDroppedCount(),
                "a side-outputed record is not a drop — the dropped counter must not move");
    }
}
