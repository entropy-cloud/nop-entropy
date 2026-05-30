package io.nop.stream.core.operators;

import io.nop.core.context.IServiceContext;
import io.nop.stream.core.common.functions.KeySelector;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.streamrecord.watermark.Watermark;
import io.nop.stream.core.util.Collector;
import io.nop.stream.core.windowing.assigners.WindowAssigner;
import io.nop.stream.core.windowing.triggers.Trigger;
import io.nop.stream.core.windowing.triggers.TriggerResult;
import io.nop.stream.core.windowing.windows.GlobalWindow;
import io.nop.stream.core.windowing.windows.TimeWindow;
import io.nop.stream.core.windowing.windows.Window;
import jakarta.annotation.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class TestWindowAggregationOperatorLateData {

    // --- Test helpers ---

    private static final class ListAggFunction
            implements WindowAggregationFunction<String, List<String>, String, String, TimeWindow> {
        @Override
        public List<String> createAccumulator() {
            return new ArrayList<>();
        }

        @Override
        public List<String> add(String value, List<String> accumulator) {
            accumulator.add(value);
            return accumulator;
        }

        @Override
        public void emitResult(String key, TimeWindow window, List<String> acc, Collector<String> out) {
            out.collect(key + ":[" + window.getStart() + "-" + window.getEnd() + "]:" + String.join(",", acc));
        }
    }

    private static final class GlobalListAggFunction
            implements WindowAggregationFunction<String, List<String>, String, String, GlobalWindow> {
        @Override
        public List<String> createAccumulator() {
            return new ArrayList<>();
        }

        @Override
        public List<String> add(String value, List<String> accumulator) {
            accumulator.add(value);
            return accumulator;
        }

        @Override
        public void emitResult(String key, GlobalWindow window, List<String> acc, Collector<String> out) {
            out.collect(key + ":" + String.join(",", acc));
        }
    }

    /**
     * Assigns tumbling time windows of a given size based on element timestamp.
     */
    private static final class TumblingTimeWindowAssigner extends WindowAssigner<String, TimeWindow> {
        private final long size;

        TumblingTimeWindowAssigner(long size) {
            this.size = size;
        }

        @Override
        public Collection<TimeWindow> assignWindows(String element, long timestamp, WindowAssignerContext ctx) {
            long start = TimeWindow.getWindowStartWithOffset(timestamp, 0, size);
            return Collections.singleton(new TimeWindow(start, start + size));
        }

        @Override
        public Trigger<String, TimeWindow> getDefaultTrigger(@Nullable IServiceContext env) {
            return null;
        }

        @Override
        public boolean isEventTime() {
            return true;
        }
    }

    private static final class GlobalWindowAssigner extends WindowAssigner<String, GlobalWindow> {
        @Override
        public Collection<GlobalWindow> assignWindows(String element, long timestamp, WindowAssignerContext ctx) {
            return Collections.singleton(GlobalWindow.get());
        }

        @Override
        public Trigger<String, GlobalWindow> getDefaultTrigger(@Nullable IServiceContext env) {
            return null;
        }

        @Override
        public boolean isEventTime() {
            return false;
        }
    }

    /**
     * Trigger that fires at the window maxTimestamp (event-time timer).
     */
    private static final class EventTimeFireTrigger extends Trigger<String, TimeWindow> {
        @Override
        public TriggerResult onElement(String element, long timestamp, TimeWindow window, TriggerContext ctx) {
            ctx.registerEventTimeTimer(window.maxTimestamp());
            return TriggerResult.CONTINUE;
        }

        @Override
        public TriggerResult onEventTime(long time, TimeWindow window, TriggerContext ctx) {
            return TriggerResult.FIRE_AND_PURGE;
        }

        @Override
        public TriggerResult onProcessingTime(long time, TimeWindow window, TriggerContext ctx) {
            return TriggerResult.CONTINUE;
        }

        @Override
        public void clear(TimeWindow window, TriggerContext ctx) {
        }
    }

    /**
     * Trigger that registers multiple event-time timers for the same window
     * (used for testing the windowTimerLookup fix).
     */
    private static final class MultiTimerTrigger extends Trigger<String, TimeWindow> {
        private final long[] timerTimestamps;

        MultiTimerTrigger(long... timerTimestamps) {
            this.timerTimestamps = timerTimestamps;
        }

        @Override
        public TriggerResult onElement(String element, long timestamp, TimeWindow window, TriggerContext ctx) {
            for (long t : timerTimestamps) {
                ctx.registerEventTimeTimer(t);
            }
            return TriggerResult.CONTINUE;
        }

        @Override
        public TriggerResult onEventTime(long time, TimeWindow window, TriggerContext ctx) {
            // Fire only on the last timer, continue on earlier ones
            if (time == timerTimestamps[timerTimestamps.length - 1]) {
                return TriggerResult.FIRE_AND_PURGE;
            }
            return TriggerResult.CONTINUE;
        }

        @Override
        public TriggerResult onProcessingTime(long time, TimeWindow window, TriggerContext ctx) {
            return TriggerResult.CONTINUE;
        }

        @Override
        public void clear(TimeWindow window, TriggerContext ctx) {
        }
    }

    /**
     * Trigger for processing time mode - never fires on event time.
     */
    private static final class ProcessingTimeContinueTrigger extends Trigger<String, GlobalWindow> {
        private final long fireAtTimestamp;

        ProcessingTimeContinueTrigger(long fireAtTimestamp) {
            this.fireAtTimestamp = fireAtTimestamp;
        }

        @Override
        public TriggerResult onElement(String element, long timestamp, GlobalWindow window, TriggerContext ctx) {
            ctx.registerProcessingTimeTimer(fireAtTimestamp);
            return TriggerResult.CONTINUE;
        }

        @Override
        public TriggerResult onProcessingTime(long time, GlobalWindow window, TriggerContext ctx) {
            return TriggerResult.FIRE_AND_PURGE;
        }

        @Override
        public TriggerResult onEventTime(long time, GlobalWindow window, TriggerContext ctx) {
            return TriggerResult.CONTINUE;
        }

        @Override
        public void clear(GlobalWindow window, TriggerContext ctx) {
        }
    }

    // --- Test fields ---

    private List<String> collected;

    private <ACC, W extends Window> WindowAggregationOperator<String, ACC, String, String, W> createOperator(
            WindowAssigner<? super String, W> assigner,
            Trigger<? super String, ? super W> trigger,
            WindowAggregationFunction<String, ACC, String, String, W> aggFn) throws Exception {
        KeySelector<String, String> keySelector = String::toString;
        WindowAggregationOperator<String, ACC, String, String, W> op =
                new WindowAggregationOperator<>(assigner, trigger, aggFn, keySelector);
        op.open();
        op.output = new Output<StreamRecord<String>>() {
            @Override
            public void collect(StreamRecord<String> record) {
                collected.add(record.getValue());
            }

            @Override
            public void close() {
            }

            @Override
            public void emitWatermark(Watermark mark) {
            }

            @Override
            public void emitWatermarkStatus(io.nop.stream.core.streamrecord.watermark.WatermarkStatus status) {
            }

            @Override
            public <X> void collect(io.nop.stream.core.util.OutputTag<X> outputTag, StreamRecord<X> record) {
            }

            @Override
            public void emitLatencyMarker(io.nop.stream.core.streamrecord.LatencyMarker latencyMarker) {
            }

            @Override
            public void emitBarrier(io.nop.stream.core.checkpoint.CheckpointBarrier barrier) {
            }
        };
        return op;
    }

    @BeforeEach
    void setUp() {
        collected = new ArrayList<>();
    }

    // --- Tests ---

    @Test
    void testLateDataDiscarded_NoWindowCreated() throws Exception {
        // Window size = 1000, tumbling windows at [0,1000), [1000,2000), etc.
        WindowAggregationOperator<String, List<String>, String, String, TimeWindow> op =
                createOperator(new TumblingTimeWindowAssigner(1000), new EventTimeFireTrigger(), new ListAggFunction());

        // Advance watermark past timestamp 500
        op.setCurrentKey("k1");
        op.processWatermark(new Watermark(2000));

        // Process element with timestamp 500 - this is late (500 < 2000)
        op.processElement(new StreamRecord<>("late-element", 500));

        // No output, and no window state created (no timers to fire)
        assertTrue(collected.isEmpty(), "Late element should be discarded, no output");

        // Advance watermark to trigger any windows that might have been created
        op.processWatermark(new Watermark(3000));
        assertTrue(collected.isEmpty(), "No window should have been created for late element");
    }

    @Test
    void testAfterWatermarkAdvance_LateElementsDontCreateNewWindows() throws Exception {
        WindowAggregationOperator<String, List<String>, String, String, TimeWindow> op =
                createOperator(new TumblingTimeWindowAssigner(1000), new EventTimeFireTrigger(), new ListAggFunction());

        // Process a normal element at timestamp 500 (window [0,1000))
        op.setCurrentKey("k1");
        op.processElement(new StreamRecord<>("normal", 500));

        // Advance watermark to fire the window
        op.processWatermark(new Watermark(1500));
        assertEquals(1, collected.size(), "Normal element's window should fire");
        assertTrue(collected.get(0).contains("normal"));

        // Now try processing a late element with timestamp 300 (also in window [0,1000))
        // This window was already purged, and the element is late (300 < 1500)
        collected.clear();
        op.processElement(new StreamRecord<>("late", 300));

        // Late element should be discarded
        assertTrue(collected.isEmpty(), "Late element after watermark advance should be discarded");

        // Also verify no new window was created by advancing watermark further
        op.processWatermark(new Watermark(5000));
        assertTrue(collected.isEmpty(), "No new window should be created from late element");
    }

    @Test
    void testProcessingTimeMode_ElementsNotDiscardedAfterWatermarkAdvance() throws Exception {
        long futureFireTime = System.currentTimeMillis() + 100_000L;

        WindowAggregationOperator<String, List<String>, String, String, GlobalWindow> op =
                createOperator(new GlobalWindowAssigner(),
                        new ProcessingTimeContinueTrigger(futureFireTime),
                        new GlobalListAggFunction());

        // Advance watermark - in processing time mode, elements without timestamps should still work
        op.setCurrentKey("k1");
        op.processWatermark(new Watermark(5000));

        // Element created without timestamp (no-timestamp constructor)
        op.processElement(new StreamRecord<>("no-timestamp-element"));

        // Advance processing time to trigger
        collected.clear();
        op.advanceProcessingTime(futureFireTime);

        // The element should NOT have been discarded - it has no timestamp
        assertEquals(1, collected.size(), "Element without timestamp should not be discarded as late data");
        assertTrue(collected.get(0).contains("no-timestamp-element"));
    }

    @Test
    void testNormalElementsStillWorkAfterLateDataFiltering() throws Exception {
        WindowAggregationOperator<String, List<String>, String, String, TimeWindow> op =
                createOperator(new TumblingTimeWindowAssigner(1000), new EventTimeFireTrigger(), new ListAggFunction());

        // Advance watermark to 2000
        op.setCurrentKey("k1");
        op.processWatermark(new Watermark(2000));

        // Try a late element (should be discarded)
        op.processElement(new StreamRecord<>("late", 500));
        assertTrue(collected.isEmpty(), "Late element discarded");

        // Now process a normal element with timestamp 3000 (window [3000,4000))
        op.processElement(new StreamRecord<>("normal", 3000));
        assertTrue(collected.isEmpty(), "No output yet - window not triggered");

        // Advance watermark to trigger window [3000,4000)
        op.processWatermark(new Watermark(4500));
        assertEquals(1, collected.size(), "Normal element should produce output after watermark advances");
        assertTrue(collected.get(0).contains("normal"), "Output should contain the normal element");
    }

    @Test
    void testMultipleTimersPerWindowKey_AfterTimerLookupFix() throws Exception {
        // This test verifies the windowTimerLookup fix: when a window has multiple timers,
        // firing one timer should not remove all timer entries for that window key.
        WindowAggregationOperator<String, List<String>, String, String, TimeWindow> op =
                createOperator(new TumblingTimeWindowAssigner(10000),
                        new MultiTimerTrigger(2000, 5000, 9000),
                        new ListAggFunction());

        // Element at timestamp 1000, window [0, 10000)
        // Trigger will register event-time timers at 2000, 5000, 9000
        op.setCurrentKey("k1");
        op.processElement(new StreamRecord<>("elem1", 1000));

        // Advance watermark to 2000 - first timer fires, but trigger returns CONTINUE
        op.processWatermark(new Watermark(3000));
        assertTrue(collected.isEmpty(), "First timer fires but trigger returns CONTINUE, no output");

        // Advance watermark to 5000 - second timer fires, trigger returns CONTINUE
        op.processWatermark(new Watermark(6000));
        assertTrue(collected.isEmpty(), "Second timer fires but trigger returns CONTINUE, no output");

        // Advance watermark past 9000 - third timer fires, trigger returns FIRE_AND_PURGE
        op.processWatermark(new Watermark(10000));
        assertEquals(1, collected.size(), "Third timer fires, trigger returns FIRE_AND_PURGE");
        assertTrue(collected.get(0).contains("elem1"), "Output should contain elem1");
    }

    @Test
    void testMultipleProcessingTimeTimersPerWindowKey() throws Exception {
        // Tests that the processingTimeTimerLookup fix works for multiple timers per key
        long fireTime1 = 1000L;
        long fireTime2 = 2000L;

        Trigger<String, GlobalWindow> multiPtTrigger = new Trigger<String, GlobalWindow>() {
            @Override
            public TriggerResult onElement(String element, long timestamp, GlobalWindow window, TriggerContext ctx) {
                ctx.registerProcessingTimeTimer(fireTime1);
                ctx.registerProcessingTimeTimer(fireTime2);
                return TriggerResult.CONTINUE;
            }

            @Override
            public TriggerResult onProcessingTime(long time, GlobalWindow window, TriggerContext ctx) {
                if (time == fireTime2) {
                    return TriggerResult.FIRE_AND_PURGE;
                }
                return TriggerResult.CONTINUE;
            }

            @Override
            public TriggerResult onEventTime(long time, GlobalWindow window, TriggerContext ctx) {
                return TriggerResult.CONTINUE;
            }

            @Override
            public void clear(GlobalWindow window, TriggerContext ctx) {
            }
        };

        WindowAggregationOperator<String, List<String>, String, String, GlobalWindow> op =
                createOperator(new GlobalWindowAssigner(), multiPtTrigger, new GlobalListAggFunction());

        op.setCurrentKey("k1");
        op.processElement(new StreamRecord<>("a", 0));

        // Advance past first timer - should not fire
        op.advanceProcessingTime(1500);
        assertTrue(collected.isEmpty(), "First timer should return CONTINUE");

        // Advance past second timer - should fire
        op.advanceProcessingTime(2500);
        assertEquals(1, collected.size(), "Second timer should fire and produce output");
        assertTrue(collected.get(0).contains("a"));
    }

    @Test
    void testElementNotDroppedBeforeWatermarkInitialized() throws Exception {
        WindowAggregationOperator<String, List<String>, String, String, TimeWindow> op =
                createOperator(new TumblingTimeWindowAssigner(1000), new EventTimeFireTrigger(), new ListAggFunction());
        op.setAllowedLateness(500);

        op.setCurrentKey("k1");
        op.processElement(new StreamRecord<>("early", 500));

        op.processWatermark(new Watermark(1500));
        assertEquals(1, collected.size(), "Element before watermark initialization should not be dropped");
        assertTrue(collected.get(0).contains("early"));
    }

    @Test
    void testNonAdvancingWatermarkNotEmittedDownstream() throws Exception {
        List<Watermark> emittedWatermarks = new ArrayList<>();
        WindowAggregationOperator<String, List<String>, String, String, TimeWindow> op =
                createOperator(new TumblingTimeWindowAssigner(1000), new EventTimeFireTrigger(), new ListAggFunction());
        op.output = new Output<StreamRecord<String>>() {
            @Override public void collect(StreamRecord<String> record) { collected.add(record.getValue()); }
            @Override public void close() {}
            @Override public void emitWatermark(Watermark mark) { emittedWatermarks.add(mark); }
            @Override public void emitWatermarkStatus(io.nop.stream.core.streamrecord.watermark.WatermarkStatus status) {}
            @Override public <X> void collect(io.nop.stream.core.util.OutputTag<X> outputTag, StreamRecord<X> record) {}
            @Override public void emitLatencyMarker(io.nop.stream.core.streamrecord.LatencyMarker latencyMarker) {}
            @Override public void emitBarrier(io.nop.stream.core.checkpoint.CheckpointBarrier barrier) {}
        };

        op.setCurrentKey("k1");
        op.processWatermark(new Watermark(1000));
        assertEquals(1, emittedWatermarks.size());

        op.processWatermark(new Watermark(500));
        assertEquals(1, emittedWatermarks.size(), "Non-advancing watermark should not be emitted");

        op.processWatermark(new Watermark(2000));
        assertEquals(2, emittedWatermarks.size(), "Advancing watermark should be emitted");
    }

    @Test
    void testRestoreStateRebuildsActiveWindowsPerKey() throws Exception {
        WindowAggregationOperator<String, List<String>, String, String, TimeWindow> op =
                createOperator(new TumblingTimeWindowAssigner(1000), new EventTimeFireTrigger(), new ListAggFunction());

        op.setCurrentKey("k1");
        op.processElement(new StreamRecord<>("a", 100));
        op.setCurrentKey("k2");
        op.processElement(new StreamRecord<>("b", 200));

        io.nop.stream.core.checkpoint.OperatorSnapshotResult snapshot = op.snapshotState(
                new io.nop.stream.core.checkpoint.StateSnapshotContext(0, 0));

        WindowAggregationOperator<String, List<String>, String, String, TimeWindow> restored =
                createOperator(new TumblingTimeWindowAssigner(1000), new EventTimeFireTrigger(), new ListAggFunction());
        restored.restoreState(snapshot);

        java.lang.reflect.Field awpField = WindowAggregationOperator.class.getDeclaredField("activeWindowsPerKey");
        awpField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Set<TimeWindow>> awp = (Map<String, Set<TimeWindow>>) awpField.get(restored);
        assertNotNull(awp, "activeWindowsPerKey should be rebuilt after restoreState");
        assertFalse(awp.isEmpty(), "activeWindowsPerKey should not be empty after restore");
    }
}
