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
 * W-M1 pre-fix reproduction (plan 369 Phase 2, R5-CEP-03 scheme): {@code
 * numLateRecordsDropped} used to be registered as a single JVM-level UNTAGGED counter, so
 * two parallel WindowOperator instances accumulated their drops into ONE shared meter —
 * neither instance's count was attributable, and the meter could not even be looked up by
 * a deployment scope.
 *
 * <p>Post-fix the counter must be registered under per-job/per-vertex/per-subtask tags
 * carried in through {@code copyForSubtask(TaskLocation)}, and two parallel instances must
 * hold DISTINCT meters with disjoint counts. Written against the PRE-FIX public API surface
 * (interface-default {@code copyForSubtask(TaskLocation)} overload only) so it compiles
 * and fails on unpatched sources.
 */
public class ReproWM1LateDropCounterSharedAcrossInstances {

    private static final String METRIC_NAME = "numLateRecordsDropped";
    private static final String OPERATOR_TAG = "repro-job.repro-pipe.repro-vertex";

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
        public void process(String key, TimeWindow window,
                            InternalWindowFunction.InternalWindowContext context,
                            Object input, io.nop.stream.core.util.Collector<String> out) {
            out.collect(String.valueOf(input));
        }

        @Override
        public void clear(TimeWindow window, InternalWindowFunction.InternalWindowContext context) {
        }
    }

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

    private io.micrometer.core.instrument.Counter taggedCounter(String subtaskTag) {
        return StreamMetricsRegistries.registry()
                .find(METRIC_NAME)
                .tags("operator", OPERATOR_TAG, "subtask", subtaskTag)
                .counter();
    }

    /**
     * Simulates the runtime deployment copy path: two subtask instances of the same
     * window operator each drop their own late records; each must observe ONLY its own
     * drops under its own deployment-scoped meter.
     */
    @Test
    void twoParallelInstancesHoldDistinctScopedMeters() throws Exception {
        WindowOperator<String, Integer, Object, String, TimeWindow> template = newTemplate();

        TaskLocation loc0 = new TaskLocation("repro-job", "repro-pipe", "repro-vertex", 0);
        TaskLocation loc1 = new TaskLocation("repro-job", "repro-pipe", "repro-vertex", 1);
        // Cast is pre-fix-safe: the interface default returns StreamOperator<?>,
        // the post-fix covariant override returns WindowOperator.
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

        try {
            // One on-time record, watermark past the window end, then late records
            // whose window was already skipped -> dropped and counted.
            subtask0.processElement(new StreamRecord<>(10, 10));
            subtask0.processWatermark(new Watermark(100));
            subtask0.processElement(new StreamRecord<>(5, 5));

            subtask1.processElement(new StreamRecord<>(10, 10));
            subtask1.processWatermark(new Watermark(100));
            subtask1.processElement(new StreamRecord<>(5, 5));
            subtask1.processElement(new StreamRecord<>(6, 6));

            io.micrometer.core.instrument.Counter counter0 = taggedCounter("0");
            io.micrometer.core.instrument.Counter counter1 = taggedCounter("1");

            assertNotNull(counter0,
                    "W-M1: numLateRecordsDropped must be registered under the deployment "
                            + "scope tags (operator=" + OPERATOR_TAG + ", subtask=0) — the old "
                            + "JVM-level untagged counter is not retrievable per instance");
            assertNotNull(counter1,
                    "W-M1: numLateRecordsDropped must be registered under the deployment "
                            + "scope tags (operator=" + OPERATOR_TAG + ", subtask=1)");
            assertNotEquals(counter0, counter1,
                    "W-M1: two parallel instances must not share one meter");
            assertEquals(1.0, counter0.count(),
                    "W-M1: subtask 0's meter counts only its own drop");
            assertEquals(2.0, counter1.count(),
                    "W-M1: subtask 1's meter counts only its own drops");
        } finally {
            subtask0.close();
            subtask1.close();
        }
    }
}
