/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.operators.windowing;

import io.nop.stream.core.checkpoint.CheckpointBarrier;
import io.nop.stream.core.common.functions.KeySelector;
import io.nop.stream.core.common.functions.ProcessWindowFunction;
import io.nop.stream.core.common.state.KeyedStateStore;
import io.nop.stream.core.common.state.ValueState;
import io.nop.stream.core.common.state.ValueStateDescriptor;
import io.nop.stream.core.common.typeutils.TypeSerializer;
import io.nop.stream.core.operators.HeapInternalTimerService;
import io.nop.stream.core.operators.Output;
import io.nop.stream.core.streamrecord.LatencyMarker;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.streamrecord.watermark.Watermark;
import io.nop.stream.core.streamrecord.watermark.WatermarkStatus;
import io.nop.stream.core.util.Collector;
import io.nop.stream.core.util.OutputTag;
import io.nop.stream.core.windowing.assigners.EventTimeSessionWindows;
import io.nop.stream.core.windowing.assigners.TumblingEventTimeWindows;
import io.nop.stream.core.windowing.assigners.WindowAssigner;
import io.nop.stream.core.windowing.triggers.EventTimeTrigger;
import io.nop.stream.core.windowing.triggers.Trigger;
import io.nop.stream.core.windowing.triggers.TriggerResult;
import io.nop.stream.core.windowing.windows.TimeWindow;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * G-3 (plan 369 Phase 2): per-window user state written via {@code ctx.windowState()}
 * inside a {@link ProcessWindowFunction} must be released with the window on ALL discard
 * paths — trigger purge, cleanup-time expiry, and MergingWindowSet merge retirement —
 * through the (previously dead) {@code processContext.clear()} →
 * {@code ProcessWindowFunction.clear()} chain. Pre-fix, no path ever invoked the user
 * clear, so per-window state entries leaked into every subsequent checkpoint.
 */
public class TestWindowOperatorPerWindowStateCleanup {

    private static final ValueStateDescriptor<String> STATE_DESC =
            new ValueStateDescriptor<>("pwf-per-window-state", String.class);

    /** Values observed by the user clear() BEFORE removing the per-window entry. */
    static final List<String> CLEARED_STATE_VALUES = new ArrayList<>();

    /** Simple TimeWindow serializer (mirrors the existing windowing test family). */
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

    /** Recording output (main records only; side outputs are irrelevant here). */
    static class RecordingOutput implements Output<StreamRecord<String>> {
        final List<String> elements = new ArrayList<>();

        @Override
        public void collect(StreamRecord<String> record) {
            elements.add(record.getValue());
        }

        @Override
        public <X> void collect(OutputTag<X> outputTag, StreamRecord<X> record) {
            elements.add("side:" + record.getValue());
        }

        @Override
        public void emitWatermark(Watermark mark) {
        }

        @Override
        public void emitWatermarkStatus(WatermarkStatus watermarkStatus) {
        }

        @Override
        public void emitLatencyMarker(LatencyMarker latencyMarker) {
        }

        @Override
        public void emitBarrier(CheckpointBarrier barrier) {
        }

        @Override
        public void close() {
        }
    }

    /**
     * User ProcessWindowFunction that keeps per-window state via ctx.windowState() and
     * releases it in clear() — the Flink contract. The state store handed to clear() is
     * namespace-scoped to the window being cleared, so the user never needs the window
     * identity itself; the recorded state VALUE encodes it for the assertions.
     */
    static class StatefulProcessWindowFunction
            implements ProcessWindowFunction<Integer, String, String, TimeWindow> {
        private static final long serialVersionUID = 1L;

        @Override
        public void process(String key, TimeWindow window, Iterable<Integer> input,
                            Context context, Collector<String> out) throws Exception {
            context.windowState().getState(STATE_DESC).update("value-of-" + window);
            out.collect("window=" + window);
        }

        @Override
        public void clear(Context context) throws Exception {
            ValueState<String> state = context.windowState().getState(STATE_DESC);
            String before = state.value();
            CLEARED_STATE_VALUES.add(before);
            state.clear();
            assertNull(state.value(), "per-window state entry must be gone after clear()");
        }
    }

    private WindowOperatorBuilder<Integer, String, TimeWindow> newBuilder(
            WindowAssigner<? super Integer, TimeWindow> assigner,
            Trigger<? super Integer, ? super TimeWindow> trigger) {
        return new WindowOperatorBuilder<Integer, String, TimeWindow>()
                .windowAssigner(assigner)
                .trigger(trigger)
                .allowedLateness(0L)
                .keySelector((KeySelector<Integer, String>) v -> "key1")
                .keyClass(String.class)
                .keySerializer(new SimpleStringSerializer())
                .windowSerializer(new SimpleTimeWindowSerializer());
    }

    /**
     * Purge path: a FIRE_AND_PURGE trigger discards the window right after the first
     * element — the user clear must run and the per-window entry must be removed.
     */
    @Test
    void testPurgePathClearsPerWindowState() throws Exception {
        CLEARED_STATE_VALUES.clear();
        Trigger<Integer, TimeWindow> fireAndPurge = new Trigger<Integer, TimeWindow>() {
            private static final long serialVersionUID = 1L;

            @Override
            public TriggerResult onElement(Integer element, long timestamp, TimeWindow window,
                                           Trigger.TriggerContext ctx) {
                return TriggerResult.FIRE_AND_PURGE;
            }

            @Override
            public TriggerResult onProcessingTime(long time, TimeWindow window,
                                                  Trigger.TriggerContext ctx) {
                return TriggerResult.CONTINUE;
            }

            @Override
            public TriggerResult onEventTime(long time, TimeWindow window,
                                             Trigger.TriggerContext ctx) {
                return TriggerResult.CONTINUE;
            }

            @Override
            public void clear(TimeWindow window, Trigger.TriggerContext ctx) {
            }
        };

        WindowOperator<String, Integer, Iterable<Integer>, String, TimeWindow> operator =
                newBuilder(TumblingEventTimeWindows.of(100L), fireAndPurge)
                        .process(new StatefulProcessWindowFunction(), Integer.class);

        RecordingOutput output = new RecordingOutput();
        operator.setOutput((Output) output);
        operator.open();
        try {
            operator.processElement(new StreamRecord<>(42, 10));

            assertEquals(1, output.elements.size(), "FIRE_AND_PURGE must emit once");
            assertTrue(CLEARED_STATE_VALUES.contains("value-of-TimeWindow{start=0, end=100}"),
                    "user clear() must be invoked on the purge path and observe the "
                            + "per-window state written by process(); observed: "
                            + CLEARED_STATE_VALUES);
        } finally {
            operator.close();
        }
    }

    /**
     * Cleanup path: the cleanup timer (maxTimestamp + allowedLateness) expires the
     * window after an ordinary FIRE — the user clear must run there too.
     */
    @Test
    void testCleanupPathClearsPerWindowState() throws Exception {
        CLEARED_STATE_VALUES.clear();
        WindowOperator<String, Integer, Iterable<Integer>, String, TimeWindow> operator =
                newBuilder(TumblingEventTimeWindows.of(100L), EventTimeTrigger.create())
                        .process(new StatefulProcessWindowFunction(), Integer.class);

        RecordingOutput output = new RecordingOutput();
        operator.setOutput((Output) output);
        operator.open();
        try {
            operator.processElement(new StreamRecord<>(7, 10));
            assertTrue(CLEARED_STATE_VALUES.isEmpty(), "no clear before the cleanup time");

            // Advance the watermark past maxTimestamp (= cleanup time, allowedLateness=0).
            ((HeapInternalTimerService<String, TimeWindow>) operator.internalTimerService)
                    .advanceWatermark(100);

            assertEquals(1, output.elements.size(), "EventTimeTrigger must fire once");
            assertTrue(CLEARED_STATE_VALUES.contains("value-of-TimeWindow{start=0, end=100}"),
                    "user clear() must be invoked on the cleanup path and observe the "
                            + "per-window state written by process(); observed: "
                            + CLEARED_STATE_VALUES);
        } finally {
            operator.close();
        }
    }

    /**
     * Merge-retire path: session windows merge — the retired source window's per-window
     * user state must be released by the merge-clear path (the source window never sees
     * a purge or cleanup of its own). The state is materialized beforehand by a FIRE
     * trigger so the retired window's entry actually exists at merge time.
     */
    @Test
    void testMergeRetirePathClearsPerWindowState() throws Exception {
        CLEARED_STATE_VALUES.clear();
        Trigger<Integer, TimeWindow> fireOnElement = new Trigger<Integer, TimeWindow>() {
            private static final long serialVersionUID = 1L;

            @Override
            public TriggerResult onElement(Integer element, long timestamp, TimeWindow window,
                                           Trigger.TriggerContext ctx) {
                return TriggerResult.FIRE;
            }

            @Override
            public TriggerResult onProcessingTime(long time, TimeWindow window,
                                                  Trigger.TriggerContext ctx) {
                return TriggerResult.FIRE;
            }

            @Override
            public TriggerResult onEventTime(long time, TimeWindow window,
                                             Trigger.TriggerContext ctx) {
                return TriggerResult.FIRE;
            }

            @Override
            public boolean canMerge() {
                return true;
            }

            @Override
            public void onMerge(TimeWindow window, Trigger.OnMergeContext ctx) {
                // Stateless fire-on-everything trigger: nothing to merge.
            }

            @Override
            public void clear(TimeWindow window, Trigger.TriggerContext ctx) {
            }
        };

        WindowOperator<String, Integer, Iterable<Integer>, String, TimeWindow> operator =
                newBuilder(EventTimeSessionWindows.withGap(100L), fireOnElement)
                        .process(new StatefulProcessWindowFunction(), Integer.class);

        RecordingOutput output = new RecordingOutput();
        operator.setOutput((Output) output);
        operator.open();
        try {
            // Element 1: window [10,110) fires (FIRE) — process() writes the per-window state.
            operator.processElement(new StreamRecord<>(1, 10));
            assertEquals(1, output.elements.size());

            // Element 2: window [50,150) overlaps [10,110) → merge into [10,150).
            // The [10,110) source window is retired by the merge; its per-window
            // state must be released right there.
            operator.processElement(new StreamRecord<>(2, 50));
            assertEquals(2, output.elements.size(), "the merged window fires again (FIRE trigger)");

            assertTrue(CLEARED_STATE_VALUES.contains("value-of-TimeWindow{start=10, end=110}"),
                    "user clear() must be invoked for the merge-retired source window; "
                            + "observed: " + CLEARED_STATE_VALUES);

            // Cleanup of the merged window releases its own per-window state.
            ((HeapInternalTimerService<String, TimeWindow>) operator.internalTimerService)
                    .advanceWatermark(150);

            assertTrue(CLEARED_STATE_VALUES.contains("value-of-TimeWindow{start=10, end=150}"),
                    "user clear() must be invoked on the merged window's cleanup; observed: "
                            + CLEARED_STATE_VALUES);
        } finally {
            operator.close();
        }
    }
}
