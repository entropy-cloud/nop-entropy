/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.operators.windowing;

import io.nop.stream.core.common.functions.KeySelector;
import io.nop.stream.core.metrics.StreamMetricsRegistries;
import io.nop.stream.core.operators.Output;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.test.TestOutput;
import io.nop.stream.core.windowing.assigners.TumblingEventTimeWindows;
import io.nop.stream.core.windowing.triggers.EventTimeTrigger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Plan 366 Phase 3: the {@code numLateRecordsDropped} counter must be wired to
 * the real drop path — a record that is skipped and past the allowed-lateness
 * bound with no late-data side output configured is dropped and counted.
 */
public class TestWindowOperatorLateRecordsDroppedMetric {

    private static final long WINDOW_SIZE = 100L;

    private TestOutput<String> output;
    private WindowingTestSupport.TestableWindowOperator operator;

    @BeforeEach
    void setUp() throws Exception {
        output = new TestOutput<>();

        // No late-data output tag: late records are dropped (the counted path).
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

    private double lateDroppedCount() {
        io.micrometer.core.instrument.Counter counter =
                StreamMetricsRegistries.registry().find("numLateRecordsDropped").counter();
        return counter == null ? 0.0 : counter.count();
    }

    @Test
    void lateRecordWithoutSideOutputIsDroppedAndCounted() throws Exception {
        // On-time window fires normally.
        operator.processElement(new StreamRecord<>(5, 10));
        operator.advanceInternalWatermark(100);
        assertEquals(1, output.size(), "on-time window must fire");
        output.clear();

        double before = lateDroppedCount();

        // Late record: ts=50 + allowedLateness(0) <= watermark 100, no side
        // output configured → dropped, counter must increment by exactly 1.
        operator.processElement(new StreamRecord<>(99, 50));

        assertEquals(0, output.size(), "late record must not reach the main output");
        assertEquals(1.0, lateDroppedCount() - before,
                "a dropped late record must increment numLateRecordsDropped");
    }
}
