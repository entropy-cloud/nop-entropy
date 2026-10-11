/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.operators.windowing;

import io.nop.stream.core.common.functions.KeySelector;
import io.nop.stream.core.common.typeinfo.UnknownTypeInformation;
import io.nop.stream.core.checkpoint.CheckpointBarrier;
import io.nop.stream.core.operators.HeapInternalTimerService;
import io.nop.stream.core.operators.Output;
import io.nop.stream.core.streamrecord.LatencyMarker;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.streamrecord.watermark.Watermark;
import io.nop.stream.core.streamrecord.watermark.WatermarkStatus;
import io.nop.stream.core.util.OutputTag;
import io.nop.stream.core.windowing.AccumulationMode;
import io.nop.stream.core.windowing.WindowLifecycleEvent;
import io.nop.stream.core.windowing.assigners.EventTimeSessionWindows;
import io.nop.stream.core.windowing.assigners.TumblingEventTimeWindows;
import io.nop.stream.core.windowing.assigners.WindowAssigner;
import io.nop.stream.core.windowing.triggers.EventTimeTrigger;
import io.nop.stream.core.windowing.triggers.Trigger;
import io.nop.stream.core.windowing.windows.TimeWindow;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI8 (plan 2332) phase 1: window lifecycle event emission — OPEN on first registry
 * insertion, CLOSE on the cleanup branch, IDLE/RESUME on the watermark-status channel,
 * merge transfers without re-OPEN, and zero events when the surface is not enabled.
 */
public class TestWindowLifecycleEvents {

    private static final long WINDOW_SIZE = 100L;

    private RecordingOutput output;
    private WindowOperator<String, Integer, Object, String, TimeWindow> operator;

    @AfterEach
    void tearDown() throws Exception {
        if (operator != null) {
            operator.close();
        }
    }

    /**
     * Records main records, side-output lifecycle events, watermark statuses and a
     * coarse timeline so event ORDER relative to main output can be asserted.
     */
    static class RecordingOutput implements Output<StreamRecord<String>> {
        final List<String> main = new ArrayList<>();
        final List<WindowLifecycleEvent> events = new ArrayList<>();
        final List<WatermarkStatus> statuses = new ArrayList<>();
        final List<String> timeline = new ArrayList<>();

        @Override
        public void collect(StreamRecord<String> record) {
            main.add(record.getValue());
            timeline.add("MAIN:" + record.getValue());
        }

        @Override
        public <X> void collect(OutputTag<X> outputTag, StreamRecord<X> record) {
            if (record.getValue() instanceof WindowLifecycleEvent) {
                WindowLifecycleEvent event = (WindowLifecycleEvent) record.getValue();
                events.add(event);
                timeline.add("EVENT:" + event.getType());
            }
        }

        @Override
        public void emitWatermark(Watermark mark) {
        }

        @Override
        public void emitWatermarkStatus(WatermarkStatus watermarkStatus) {
            statuses.add(watermarkStatus);
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

    private WindowOperator<String, Integer, Object, String, TimeWindow> newOperator(
            WindowAssigner<? super Integer, TimeWindow> assigner,
            Trigger<? super Integer, ? super TimeWindow> trigger,
            long allowedLateness,
            boolean events) {
        OutputTag<WindowLifecycleEvent> tag = events ? lifecycleTag() : null;
        WindowOperator<String, Integer, Object, String, TimeWindow> op = new WindowOperator<>(
                assigner,
                new WindowingTestSupport.SimpleTimeWindowSerializer(),
                (KeySelector<Integer, String>) v -> "key1",
                new WindowingTestSupport.SimpleStringSerializer(),
                String.class,
                new WindowingTestSupport.ToStringWindowFunction<>(),
                trigger,
                allowedLateness,
                null,
                Object.class,
                null,
                null,
                null,
                AccumulationMode.ACCUMULATING,
                0L,
                events,
                tag);
        output = new RecordingOutput();
        op.setOutput((Output) output);
        return op;
    }

    private void fireProcessingTimersUpTo(long time) throws Exception {
        ((HeapInternalTimerService<String, TimeWindow>) operator.internalTimerService)
                .fireProcessingTimeTimers(time);
    }

    private void advanceWatermark(long timestamp) throws Exception {
        ((HeapInternalTimerService<String, TimeWindow>) operator.internalTimerService)
                .advanceWatermark(timestamp);
    }

    /**
     * OPEN is announced when the first record enters the window, the FIRE is data-plane
     * main output, and CLOSE is announced when the cleanup branch retires the window —
     * in that order, on the dedicated tag only.
     */
    @Test
    void openDataFireCloseSequence() throws Exception {
        operator = newOperator(TumblingEventTimeWindows.of(WINDOW_SIZE),
                EventTimeTrigger.create(), 50L, true);
        operator.open();

        operator.processElement(new StreamRecord<>(5, 10));
        assertEquals(1, output.events.size(), "first record must announce OPEN");
        WindowLifecycleEvent open = output.events.get(0);
        assertEquals(WindowLifecycleEvent.Type.OPEN, open.getType());
        assertEquals("key1", open.getKey());
        assertEquals(0L, open.getWindowStart());
        assertEquals(100L, open.getWindowEnd());
        assertTrue(output.main.isEmpty(), "OPEN is a side-output event, not main output");

        advanceWatermark(100);
        assertEquals(1, output.main.size(), "window must fire on the watermark");
        assertEquals("5", output.main.get(0));

        advanceWatermark(160);
        assertEquals(2, output.events.size(), "cleanup must announce CLOSE");
        WindowLifecycleEvent close = output.events.get(1);
        assertEquals(WindowLifecycleEvent.Type.CLOSE, close.getType());
        assertEquals(0L, close.getWindowStart());
        assertEquals(100L, close.getWindowEnd());

        assertEquals("EVENT:OPEN", output.timeline.get(0), "OPEN precedes all data-plane output");
        assertEquals("EVENT:CLOSE", output.timeline.get(output.timeline.size() - 1),
                "CLOSE is the last lifecycle signal");

        // The window is retired — the registry must no longer hold it.
        assertTrue(operator.activeWindowsForTest().isEmpty(),
                "cleaned-up window must leave the active-window registry");
    }

    /**
     * R-registry: a DISCARDING-surface re-fire (late record accepted within
     * allowedLateness) must NOT re-announce OPEN — registry entries survive fires.
     */
    @Test
    void refireAfterFireDoesNotReopenWindow() throws Exception {
        operator = newOperator(TumblingEventTimeWindows.of(WINDOW_SIZE),
                EventTimeTrigger.create(), 50L, true);
        operator.open();

        operator.processElement(new StreamRecord<>(5, 10));
        advanceWatermark(100);
        assertEquals(1, output.main.size());
        output.main.clear();

        // Late record within lateness: accepted, window re-fires.
        operator.processElement(new StreamRecord<>(7, 60));
        advanceWatermark(149);

        assertTrue(output.main.contains("7"), "late record must be accepted and emitted");
        long opens = output.events.stream()
                .filter(e -> e.getType() == WindowLifecycleEvent.Type.OPEN).count();
        assertEquals(1, opens, "a fired-but-active window must not re-announce OPEN");
    }

    /**
     * R-status: IDLE/RESUME ride the watermark-status channel; the status is always
     * forwarded downstream. RESUME fires on ACTIVE status arrival OR on the first
     * record after idle.
     */
    @Test
    void idleResumeCycle() throws Exception {
        operator = newOperator(TumblingEventTimeWindows.of(WINDOW_SIZE),
                EventTimeTrigger.create(), 0L, true);
        operator.open();

        operator.processElement(new StreamRecord<>(5, 10));
        assertEquals(1, output.events.size());

        operator.processWatermarkStatus(WatermarkStatus.IDLE);
        assertEquals(WatermarkStatus.IDLE, output.statuses.get(0),
                "IDLE status must be forwarded downstream");
        assertEquals(WindowLifecycleEvent.Type.IDLE, output.events.get(1).getType());

        operator.processWatermarkStatus(WatermarkStatus.ACTIVE);
        assertEquals(WatermarkStatus.ACTIVE, output.statuses.get(1));
        assertEquals(WindowLifecycleEvent.Type.RESUME, output.events.get(2).getType());

        // Data after resume: no duplicate RESUME.
        operator.processElement(new StreamRecord<>(6, 20));
        assertEquals(3, output.events.size());

        // Second idle cycle: RESUME arrives with the first RECORD, not a status.
        operator.processWatermarkStatus(WatermarkStatus.IDLE);
        assertEquals(4, output.events.size());
        operator.processElement(new StreamRecord<>(7, 30));
        assertEquals(WindowLifecycleEvent.Type.RESUME, output.events.get(4).getType(),
                "first record after idle is the resume signal");

        List<WindowLifecycleEvent.Type> types = new ArrayList<>();
        for (WindowLifecycleEvent e : output.events) {
            types.add(e.getType());
        }
        assertEquals(List.of(WindowLifecycleEvent.Type.OPEN, WindowLifecycleEvent.Type.IDLE,
                WindowLifecycleEvent.Type.RESUME, WindowLifecycleEvent.Type.IDLE,
                WindowLifecycleEvent.Type.RESUME), types);
    }

    /**
     * R-registry merge semantics: a session merge transfers the surviving entry to the
     * merged window WITHOUT re-announcing OPEN; the merged window CLOSEs with its own
     * (merged) span.
     */
    @Test
    void sessionMergeDoesNotReopen() throws Exception {
        operator = newOperator(EventTimeSessionWindows.withGap(50),
                EventTimeTrigger.create(), 0L, true);
        operator.open();

        operator.processElement(new StreamRecord<>(1, 0));
        assertEquals(1, output.events.size(), "first session window announces OPEN");
        assertEquals(0L, output.events.get(0).getWindowStart());
        assertEquals(50L, output.events.get(0).getWindowEnd());

        // Overlapping record merges [25,75) into [0,75) — no second OPEN.
        operator.processElement(new StreamRecord<>(2, 25));
        long opens = output.events.stream()
                .filter(e -> e.getType() == WindowLifecycleEvent.Type.OPEN).count();
        assertEquals(1, opens, "a merge continues the existing window instance");

        // Registry now holds exactly the merged window entry.
        assertEquals(1, operator.activeWindowsForTest().size());

        // Fire at the merged window's maxTimestamp (74) — with allowedLateness=0 the
        // fire timer and the cleanup timer share one timestamp and one invocation.
        advanceWatermark(200);
        assertTrue(output.main.contains("2"),
                "merged window must emit; the raw fallback state stores by replacement, "
                        + "so the merged window's last value is emitted; output=" + output.main);

        WindowLifecycleEvent close = output.events.get(output.events.size() - 1);
        assertEquals(WindowLifecycleEvent.Type.CLOSE, close.getType());
        assertEquals(0L, close.getWindowStart(), "CLOSE carries the merged window span");
        assertEquals(75L, close.getWindowEnd());
        assertTrue(operator.activeWindowsForTest().isEmpty());
    }

    /**
     * Default-off hard gate: an operator built without the event surface produces zero
     * side outputs across the full OPEN->FIRE->CLOSE lifecycle, while watermark status
     * forwarding stays intact.
     */
    @Test
    void eventsDisabledEmitsNothing() throws Exception {
        operator = newOperator(TumblingEventTimeWindows.of(WINDOW_SIZE),
                EventTimeTrigger.create(), 50L, false);
        operator.open();

        operator.processElement(new StreamRecord<>(5, 10));
        operator.processWatermarkStatus(WatermarkStatus.IDLE);
        advanceWatermark(100);
        advanceWatermark(160);
        operator.processWatermarkStatus(WatermarkStatus.ACTIVE);

        assertTrue(output.events.isEmpty(),
                "with the surface disabled no lifecycle event may be emitted");
        assertEquals(1, output.main.size(), "data-plane output must be unaffected");
        assertEquals(2, output.statuses.size(), "status forwarding must be unaffected");
    }
    @SuppressWarnings({"unchecked", "rawtypes"})
    static OutputTag<WindowLifecycleEvent> lifecycleTag() {
        return new OutputTag<>("test-op:window-events",
                (io.nop.stream.core.common.typeinfo.TypeInformation<WindowLifecycleEvent>)
                        io.nop.stream.core.common.typeinfo.UnknownTypeInformation.INSTANCE);
    }

}
