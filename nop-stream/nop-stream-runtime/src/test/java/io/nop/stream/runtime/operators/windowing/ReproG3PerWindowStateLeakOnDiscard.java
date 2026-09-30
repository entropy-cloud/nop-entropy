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
import io.nop.stream.core.common.state.ValueState;
import io.nop.stream.core.common.state.ValueStateDescriptor;
import io.nop.stream.core.common.state.backend.IKeyedStateBackend;
import io.nop.stream.core.common.state.backend.StateSnapshot;
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
import io.nop.stream.core.windowing.triggers.EventTimeTrigger;
import io.nop.stream.core.windowing.triggers.Trigger;
import io.nop.stream.core.windowing.triggers.TriggerResult;
import io.nop.stream.core.windowing.windows.TimeWindow;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * G-3 pre-fix reproduction (plan 369 Phase 2): per-window user state written through
 * {@code ctx.windowState()} inside a {@link ProcessWindowFunction} used to LEAK on every
 * window-discard path — the operator never invoked {@code processContext.clear()}, so the
 * entry survived cleanup / merge retirement and flowed into every subsequent keyed-state
 * checkpoint snapshot.
 *
 * <p>Unlike {@link TestWindowOperatorPerWindowStateCleanup} (which observes the user
 * {@code clear()} callback), this test asserts the observable defect directly: after the
 * discard, the namespace-scoped entry is GONE from the keyed state store AND the keyed
 * state checkpoint snapshot carries no residue. Written against the PRE-FIX public API
 * surface (no {@code ProcessWindowFunction.clear} reference) so it compiles and fails on
 * unpatched sources.
 */
public class ReproG3PerWindowStateLeakOnDiscard {

    private static final ValueStateDescriptor<String> STATE_DESC =
            new ValueStateDescriptor<>("pwf-g3-repro-probe", String.class);

    /** Namespace string produced by {@code WindowOperator#computeWindowNamespace} for TimeWindow. */
    private static String twNamespace(long start, long end) {
        return "TW:" + start + "," + end;
    }

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

    static class RecordingOutput implements Output<StreamRecord<String>> {
        @Override
        public void collect(StreamRecord<String> record) {
        }

        @Override
        public <X> void collect(OutputTag<X> outputTag, StreamRecord<X> record) {
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
     * Writes a per-window marker through ctx.windowState() when the window fires and
     * releases it in clear() — the Flink contract (the OPERATOR must invoke the user
     * clear on every discard path; the USER must actually drop the entry there).
     * Pre-fix the operator never invoked clear(), so the marker leaked.
     */
    static class StatefulFunction implements ProcessWindowFunction<Integer, String, String, TimeWindow> {
        private static final long serialVersionUID = 1L;

        private final String marker;

        StatefulFunction(String marker) {
            this.marker = marker;
        }

        @Override
        public void process(String key, TimeWindow window, Iterable<Integer> input,
                            Context context, Collector<String> out) throws Exception {
            context.windowState().getState(STATE_DESC).update(marker);
            out.collect(marker);
        }

        @Override
        public void clear(Context context) throws Exception {
            context.windowState().getState(STATE_DESC).clear();
        }
    }

    @SuppressWarnings("unchecked")
    private WindowOperatorBuilder<Integer, String, TimeWindow> newBuilder(
            io.nop.stream.core.windowing.assigners.WindowAssigner<? super Integer, TimeWindow> assigner,
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
     * Precise snapshot probe: {@code true} when the keyed-state checkpoint snapshot
     * still holds an entry of {@code stateName} under {@code namespace} whose value
     * contains {@code valueNeedle}. Scoped to the probed namespace — other windows
     * (e.g. the merged successor that legitimately re-wrote its own state) must not
     * trip the check.
     */
    static boolean snapshotHasUserStateEntry(StateSnapshot snapshot, String stateName,
                                             String namespace, String valueNeedle) {
        Object state = snapshot.getStates().get(stateName);
        if (!(state instanceof Map)) {
            return false;
        }
        Object entries = ((Map<?, ?>) state).get("entries");
        if (!(entries instanceof Collection)) {
            return false;
        }
        for (Object item : (Collection<?>) entries) {
            if (!(item instanceof Map)) {
                continue;
            }
            Map<?, ?> entry = (Map<?, ?>) item;
            if (namespace.equals(String.valueOf(entry.get("namespace")))
                    && String.valueOf(entry.get("value")).contains(valueNeedle)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Cleanup path: after the cleanup timer expires the window, the per-window entry
     * must be gone from the live store AND from the keyed-state checkpoint snapshot.
     */
    @Test
    void cleanupPathDoesNotLeakPerWindowStateIntoCheckpoint() throws Exception {
        String marker = "leak-marker-cleanup-TW-0-100";
        WindowOperator<String, Integer, Iterable<Integer>, String, TimeWindow> operator =
                newBuilder(TumblingEventTimeWindows.of(100L), EventTimeTrigger.create())
                        .process(new StatefulFunction(marker), Integer.class);

        RecordingOutput output = new RecordingOutput();
        operator.setOutput((Output) output);
        operator.open();
        try {
            operator.processElement(new StreamRecord<>(7, 10));
            // Watermark 100 >= fire time (maxTimestamp=99) and cleanup time (99):
            // the window fires (function writes the per-window marker), then cleans up.
            ((HeapInternalTimerService<String, TimeWindow>) operator.internalTimerService)
                    .advanceWatermark(100);

            IKeyedStateBackend<String> backend = operator.getKeyedStateBackend();
            backend.setCurrentKey("key1");

            PerWindowKeyedStateStore<String> probe =
                    new PerWindowKeyedStateStore<>(backend, twNamespace(0, 100));
            ValueState<String> leaked = probe.getState(STATE_DESC);
            assertNull(leaked.value(),
                    "G-3: per-window state entry must not survive window cleanup "
                            + "(leaked value: " + leaked.value() + ")");

            StateSnapshot snapshot = backend.snapshotState();
            assertFalse(
                    snapshotHasUserStateEntry(snapshot, STATE_DESC.getName(),
                            twNamespace(0, 100), marker),
                    "G-3: keyed-state checkpoint snapshot must not contain the leaked "
                            + "per-window entry; snapshot=" + snapshot.getStateData());
        } finally {
            operator.close();
        }
    }

    /**
     * Merge-retire path: when session windows merge, the retired source window's
     * per-window entry must die with the merge (the source window never sees its own
     * cleanup timer afterwards).
     */
    @Test
    void mergeRetirePathDoesNotLeakRetiredWindowState() throws Exception {
        String marker = "leak-marker-merge-TW-10-110";
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
                        .process(new StatefulFunction(marker), Integer.class);

        RecordingOutput output = new RecordingOutput();
        operator.setOutput((Output) output);
        operator.open();
        try {
            // Fires [10,110) — the function writes the per-window marker for it.
            operator.processElement(new StreamRecord<>(1, 10));
            // [50,150) overlaps [10,110) -> merge into [10,150); [10,110) is retired.
            operator.processElement(new StreamRecord<>(2, 50));

            IKeyedStateBackend<String> backend = operator.getKeyedStateBackend();
            backend.setCurrentKey("key1");

            PerWindowKeyedStateStore<String> retiredProbe =
                    new PerWindowKeyedStateStore<>(backend, twNamespace(10, 110));
            ValueState<String> leaked = retiredProbe.getState(STATE_DESC);
            assertNull(leaked.value(),
                    "G-3: the merge-retired source window's per-window state entry must "
                            + "be released with the merge (leaked value: " + leaked.value() + ")");

            StateSnapshot snapshot = backend.snapshotState();
            assertFalse(
                    snapshotHasUserStateEntry(snapshot, STATE_DESC.getName(),
                            twNamespace(10, 110), marker),
                    "G-3: keyed-state checkpoint snapshot must not contain the retired "
                            + "window's per-window entry; snapshot=" + snapshot.getStateData());
        } finally {
            operator.close();
        }
    }
}
