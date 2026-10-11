/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.operators.windowing;

import io.nop.stream.core.checkpoint.TaskLocation;
import io.nop.stream.core.common.functions.KeySelector;
import io.nop.stream.core.common.typeutils.TypeSerializer;
import io.nop.stream.core.metrics.StreamMetricsRegistries;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.streamrecord.watermark.Watermark;
import io.nop.stream.core.util.OutputTag;
import io.nop.stream.core.windowing.assigners.TumblingEventTimeWindows;
import io.nop.stream.core.windowing.triggers.EventTimeTrigger;
import io.nop.stream.core.windowing.windows.TimeWindow;
import io.nop.stream.runtime.operators.windowing.functions.InternalWindowFunction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * W-M1 (plan 369 Phase 2, R5-CEP-03 scheme): {@code numLateRecordsDropped} must be
 * registered under a per-job/per-vertex/per-subtask scope carried in through
 * {@code copyForSubtask(TaskLocation)} — two parallel instances must register DISTINCT
 * meters so drops observed by one instance never appear in the other's counter (the old
 * JVM-level untagged counter merged them).
 */
public class TestWindowOperatorLateDropMetricScope {

    private static final String METRIC_NAME = "numLateRecordsDropped";
    private static final String OPERATOR_TAG = "wm-job.wm-pipe.wm-vertex";

    private WindowOperator<String, Integer, Object, String, TimeWindow> newTemplate() {
        return new WindowOperator<String, Integer, Object, String, TimeWindow>(
                TumblingEventTimeWindows.of(100L),
                new SimpleTimeWindowSerializer(),
                (KeySelector<Integer, String>) v -> "key1",
                new SimpleStringSerializer(),
                String.class,
                new SumWindowFunction(),
                EventTimeTrigger.create(),
                0L,
                (OutputTag<Integer>) null);
    }

    private double lateDroppedCount(String subtaskTag) {
        io.micrometer.core.instrument.Counter counter = StreamMetricsRegistries.registry()
                .find(METRIC_NAME)
                .tags("operator", OPERATOR_TAG, "subtask", subtaskTag)
                .counter();
        assertNotNull(counter,
                "numLateRecordsDropped must be registered under the deployment scope tags "
                        + "(operator=" + OPERATOR_TAG + ", subtask=" + subtaskTag + ")");
        return counter.count();
    }

    /**
     * Simulates the runtime deployment copy path: each subtask gets its own operator
     * instance carrying its TaskLocation identity, then drops exactly its own late
     * records. Neither instance's counter may see the other's drops.
     */
    @Test
    void twoParallelInstancesDoNotCrossCount() throws Exception {
        WindowOperator<String, Integer, Object, String, TimeWindow> template = newTemplate();

        TaskLocation loc0 = new TaskLocation("wm-job", "wm-pipe", "wm-vertex", 0);
        TaskLocation loc1 = new TaskLocation("wm-job", "wm-pipe", "wm-vertex", 1);
        // Cast keeps the test compilable against pre-fix sources too (the interface
        // default returns StreamOperator<?>; the post-fix override is covariant).
        @SuppressWarnings("unchecked")
        WindowOperator<String, Integer, Object, String, TimeWindow> subtask0 =
                (WindowOperator<String, Integer, Object, String, TimeWindow>) template.copyForSubtask(loc0);
        @SuppressWarnings("unchecked")
        WindowOperator<String, Integer, Object, String, TimeWindow> subtask1 =
                (WindowOperator<String, Integer, Object, String, TimeWindow>) template.copyForSubtask(loc1);

        subtask0.setOutput(new io.nop.stream.core.test.TestOutput<>());
        subtask1.setOutput(new io.nop.stream.core.test.TestOutput<>());
        subtask0.open();
        subtask1.open();

        // Subtask 0: one on-time record, then push the watermark past the window
        // end (cleanup time = maxTimestamp, allowedLateness=0), then one late record
        // whose window was already skipped → dropped, counted.
        subtask0.processElement(new StreamRecord<>(10, 10));
        subtask0.processWatermark(new Watermark(100));
        subtask0.processElement(new StreamRecord<>(5, 5));

        // Subtask 1: same setup, two distinct late records.
        subtask1.processElement(new StreamRecord<>(10, 10));
        subtask1.processWatermark(new Watermark(100));
        subtask1.processElement(new StreamRecord<>(5, 5));
        subtask1.processElement(new StreamRecord<>(6, 6));

        io.micrometer.core.instrument.Counter counter0 = StreamMetricsRegistries.registry()
                .find(METRIC_NAME)
                .tags("operator", OPERATOR_TAG, "subtask", "0")
                .counter();
        io.micrometer.core.instrument.Counter counter1 = StreamMetricsRegistries.registry()
                .find(METRIC_NAME)
                .tags("operator", OPERATOR_TAG, "subtask", "1")
                .counter();
        assertNotEquals(counter0, counter1,
                "two parallel instances must not share one meter");

        assertEquals(1.0, counter0.count(),
                "subtask 0's counter counts only its own drop");
        assertEquals(2.0, counter1.count(),
                "subtask 1's counter counts only its own drops");

        subtask0.close();
        subtask1.close();
    }

    // Reuse of the windowing test family's trivial scaffolding (kept local to avoid
    // widening WindowingTestSupport visibility beyond its current package contract).

    static class SimpleTimeWindowSerializer implements TypeSerializer<TimeWindow> {
        private static final long serialVersionUID = 1L;

        @Override
        public boolean isImmutableType() {
            return true;
        }

        @Override
        public TypeSerializer<TimeWindow> duplicate() {
            return this;
        }

        @Override
        public TimeWindow createInstance() {
            return new TimeWindow(0, 0);
        }

        @Override
        public TimeWindow copy(TimeWindow from) {
            return new TimeWindow(from.getStart(), from.getEnd());
        }

        @Override
        public TimeWindow copy(TimeWindow from, TimeWindow reuse) {
            return new TimeWindow(from.getStart(), from.getEnd());
        }

        @Override
        public int getLength() {
            return -1;
        }
    }

    static class SimpleStringSerializer implements TypeSerializer<String> {
        private static final long serialVersionUID = 1L;

        @Override
        public boolean isImmutableType() {
            return true;
        }

        @Override
        public TypeSerializer<String> duplicate() {
            return this;
        }

        @Override
        public String createInstance() {
            return "";
        }

        @Override
        public String copy(String from) {
            return from;
        }

        @Override
        public String copy(String from, String reuse) {
            return from;
        }

        @Override
        public int getLength() {
            return -1;
        }
    }

    static class SumWindowFunction implements InternalWindowFunction<Object, String, String, TimeWindow> {
        private static final long serialVersionUID = 1L;

        @Override
        public void process(String key, TimeWindow window, InternalWindowFunction.InternalWindowContext context,
                            Object input, io.nop.stream.core.util.Collector<String> out) {
            out.collect(String.valueOf(input));
        }

        @Override
        public void clear(TimeWindow window, InternalWindowFunction.InternalWindowContext context) {
        }
    }
}
