package io.nop.stream.runtime.operators.windowing;

import io.nop.stream.core.checkpoint.OperatorSnapshotResult;
import io.nop.stream.core.checkpoint.StateSnapshotContext;
import io.nop.stream.core.common.functions.KeySelector;
import io.nop.stream.core.common.typeutils.TypeSerializer;
import io.nop.stream.core.operators.Output;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.test.TestOutput;
import io.nop.stream.core.util.Collector;
import io.nop.stream.core.util.OutputTag;
import io.nop.stream.core.windowing.AccumulationMode;
import io.nop.stream.core.windowing.PaneInfo;
import io.nop.stream.core.windowing.assigners.TumblingEventTimeWindows;
import io.nop.stream.core.windowing.evictors.Evictor;
import io.nop.stream.core.windowing.triggers.Trigger;
import io.nop.stream.core.windowing.triggers.TriggerResult;
import io.nop.stream.core.windowing.utils.TimestampedValue;
import io.nop.stream.core.windowing.windows.TimeWindow;
import io.nop.stream.core.operators.HeapInternalTimerService;
import io.nop.stream.runtime.operators.windowing.functions.InternalWindowFunction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestPaneInfoAndAccumulationMode {

    private static final long WINDOW_SIZE = 100L;

    private TestOutput<String> output;
    private PaneInfoRecorder windowFunction;
    private TestableWindowOperator operator;

    @BeforeEach
    void setUp() throws Exception {
        output = new TestOutput<>();
        windowFunction = new PaneInfoRecorder();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (operator != null) {
            operator.close();
        }
    }

    private void createOperator(AccumulationMode mode) throws Exception {
        createOperator(mode, 0L);
    }

    private void createOperator(AccumulationMode mode, long allowedLateness) throws Exception {
        operator = new TestableWindowOperator(
                TumblingEventTimeWindows.of(WINDOW_SIZE),
                new SimpleTimeWindowSerializer(),
                (KeySelector<Integer, String>) v -> "key1",
                new SimpleStringSerializer(),
                String.class,
                windowFunction,
                new FiringTrigger(),
                allowedLateness,
                null,
                mode
        );
        operator.setOutput((Output) output);
        operator.open();
    }

    private void advanceWatermark(long timestamp) throws Exception {
        operator.advanceInternalWatermark(timestamp);
    }

    private void processElement(int value, long timestamp) throws Exception {
        operator.processElement(new StreamRecord<>(value, timestamp));
    }

    @Test
    void testEarlyFiringPaneTiming() throws Exception {
        createOperator(AccumulationMode.ACCUMULATING);
        advanceWatermark(50);

        processElement(10, 10);
        PaneInfo info = windowFunction.lastPaneInfo;
        assertNotNull(info, "PaneInfo should be populated on fire");
        assertEquals(PaneInfo.PaneTiming.EARLY, info.getTiming(),
                "Watermark=50 < window.maxTimestamp=100 should be EARLY");
        assertTrue(info.isFirst(), "First firing should have isFirst=true");
        assertEquals(0, info.getIndex(), "First firing should have index=0");
    }

    @Test
    void testOnTimeFiringPaneTiming() throws Exception {
        createOperator(AccumulationMode.ACCUMULATING);
        advanceWatermark(50);

        processElement(10, 10);
        PaneInfo first = windowFunction.lastPaneInfo;
        assertEquals(PaneInfo.PaneTiming.EARLY, first.getTiming(),
                "Watermark=50 < window.maxTimestamp=100 -> EARLY");

        advanceWatermark(100);
        processElement(20, 20);

        PaneInfo second = windowFunction.lastPaneInfo;
        assertEquals(PaneInfo.PaneTiming.ON_TIME, second.getTiming(),
                "Watermark=100 >= window.maxTimestamp=100 and first crossing -> ON_TIME");
        assertFalse(second.isFirst(), "Second firing should have isFirst=false");
        assertEquals(1, second.getIndex(), "Second firing should have index=1");
    }

    @Test
    void testLateFiringPaneTiming() throws Exception {
        createOperator(AccumulationMode.ACCUMULATING, WINDOW_SIZE);

        advanceWatermark(50);
        processElement(10, 10);
        PaneInfo first = windowFunction.lastPaneInfo;
        assertEquals(PaneInfo.PaneTiming.EARLY, first.getTiming(),
                "Watermark=50 < window.maxTimestamp=99 -> EARLY");

        advanceWatermark(100);

        processElement(20, 20);
        PaneInfo second = windowFunction.lastPaneInfo;
        assertEquals(PaneInfo.PaneTiming.ON_TIME, second.getTiming(),
                "First crossing at watermark=100 >= window.maxTimestamp=99 -> ON_TIME");
        assertFalse(second.isFirst(), "ON_TIME firing should have isFirst=false");

        processElement(30, 30);
        PaneInfo third = windowFunction.lastPaneInfo;
        assertEquals(PaneInfo.PaneTiming.LATE, third.getTiming(),
                "After ON_TIME, next firing for same window should be LATE");
        assertFalse(third.isFirst(), "LATE firing should have isFirst=false");
    }

    @Test
    void testAccumulationModeDiscardingClearsState() throws Exception {
        createOperator(AccumulationMode.DISCARDING);

        processElement(10, 10);
        assertEquals("10", output.getElements().get(0),
                "DISCARDING: first element should emit");
        output.clear();

        processElement(20, 50);
        assertEquals(1, output.size(),
                "DISCARDING: second element after clear should emit alone");
        assertEquals("20", output.getElements().get(0),
                "DISCARDING: state was cleared, only second element should be in output");
    }

    @Test
    void testAccumulationModeAccumulatingKeepsState() throws Exception {
        createOperator(AccumulationMode.ACCUMULATING);

        processElement(10, 10);
        assertEquals("10", output.getElements().get(0),
                "ACCUMULATING: first element should emit");
        output.clear();

        processElement(20, 50);
        assertEquals(1, output.size(),
                "ACCUMULATING: second element should emit");
        assertEquals("20", output.getElements().get(0),
                "ACCUMULATING: both elements present, last-write-wins shows second");
    }

    @Test
    void testEvictorEvictAfterCalled() throws Exception {
        TrackingEvictor trackingEvictor = new TrackingEvictor();
        operator = new TestableWindowOperator(
                TumblingEventTimeWindows.of(WINDOW_SIZE),
                new SimpleTimeWindowSerializer(),
                (KeySelector<Integer, String>) v -> "key1",
                new SimpleStringSerializer(),
                String.class,
                new ConcatWindowFunction(),
                EventTimeTrigger.create(),
                0L,
                null,
                (Class) Object.class,
                null,
                null,
                trackingEvictor,
                AccumulationMode.ACCUMULATING
        );
        operator.setOutput((Output) output);
        operator.open();

        operator.processElement(new StreamRecord<>(1, 10));
        operator.processElement(new StreamRecord<>(2, 20));
        operator.processElement(new StreamRecord<>(3, 30));

        advanceWatermark(99);

        assertTrue(trackingEvictor.evictAfterCalled,
                "Evictor.evictAfter() should have been called during emit");
    }

    @Test
    void testAntiHollowPaneInfoReadWrite() throws Exception {
        createOperator(AccumulationMode.ACCUMULATING);
        advanceWatermark(50);
        processElement(42, 10);
        assertNotNull(windowFunction.lastPaneInfo,
                "PaneInfo must be accessible via context.getPaneInfo() in window function");
        assertEquals(42, Integer.parseInt(output.getElements().get(0)),
                "Window function should receive correct element value");
        assertEquals(PaneInfo.PaneTiming.EARLY, windowFunction.lastPaneInfo.getTiming(),
                "PaneInfo must have correct EARLY timing (anti-hollow check)");
    }

    /**
     * G48: proves paneTracking participates in checkpoint/restore so that post-recovery
     * window firings are not mistaken for ON_TIME / isFirst.
     *
     * <p>Scenario: register EARLY (paneIndex=0, isFirst=true) then ON_TIME (paneIndex=1,
     * onTimeEmitted=true) on operator-1 → snapshot → restore into operator-2 → fire the
     * same window again. Without paneTracking restore, the post-recovery firing would be
     * misclassified as ON_TIME/isFirst. With restore, it correctly continues as LATE with
     * paneIndex=2.
     */
    @Test
    void testPaneTrackingSurvivesCheckpointRestore() throws Exception {
        // --- Operator 1: register EARLY + ON_TIME panes ---
        createOperator(AccumulationMode.ACCUMULATING, WINDOW_SIZE);

        advanceWatermark(50);
        processElement(10, 10);
        PaneInfo earlyPane = windowFunction.lastPaneInfo;
        assertEquals(PaneInfo.PaneTiming.EARLY, earlyPane.getTiming());
        assertTrue(earlyPane.isFirst());
        assertEquals(0, earlyPane.getIndex());

        advanceWatermark(100);
        processElement(20, 20);
        PaneInfo onTimePane = windowFunction.lastPaneInfo;
        assertEquals(PaneInfo.PaneTiming.ON_TIME, onTimePane.getTiming());
        assertFalse(onTimePane.isFirst());
        assertEquals(1, onTimePane.getIndex());

        // --- Snapshot operator-1 ---
        OperatorSnapshotResult snapshot = operator.snapshotState(
                new StateSnapshotContext(1L, System.currentTimeMillis()));
        assertNotNull(snapshot.getOperatorState("pane-tracking"),
                "pane-tracking state must be present in the snapshot");

        // --- Operator 2: restoreState (before open, as per lifecycle) then open ---
        operator.close();
        operator = null;

        TestableWindowOperator restoredOperator = new TestableWindowOperator(
                TumblingEventTimeWindows.of(WINDOW_SIZE),
                new SimpleTimeWindowSerializer(),
                (KeySelector<Integer, String>) v -> "key1",
                new SimpleStringSerializer(),
                String.class,
                windowFunction,
                new FiringTrigger(),
                WINDOW_SIZE,
                null,
                AccumulationMode.ACCUMULATING
        );
        TestOutput<String> restoredOutput = new TestOutput<>();
        restoredOperator.setOutput((Output) restoredOutput);
        restoredOperator.restoreState(snapshot);
        restoredOperator.open();
        operator = restoredOperator; // so tearDown() closes it

        // --- Fire the same window again (watermark still >= window.maxTimestamp) ---
        advanceWatermark(100);
        processElement(30, 30);

        PaneInfo restoredPane = windowFunction.lastPaneInfo;
        assertNotNull(restoredPane, "A pane info must be produced after restore");
        assertEquals(PaneInfo.PaneTiming.LATE, restoredPane.getTiming(),
                "After restore, onTimeEmitted=true must persist => next firing is LATE, not ON_TIME");
        assertEquals(2, restoredPane.getIndex(),
                "After restore, paneIndex must continue from 2 (was 1 before snapshot)");
        assertFalse(restoredPane.isFirst(),
                "After restore, isFirst must be false (paneTracking was restored)");
    }

    /**
     * G48: ACCUMULATING_AND_RETRACTING is spec-only and must fail fast on open, not silently
     * behave as ACCUMULATING.
     */
    @Test
    void testRetractingModeFailsFastOnOpen() throws Exception {
        TestableWindowOperator retractOp = new TestableWindowOperator(
                TumblingEventTimeWindows.of(WINDOW_SIZE),
                new SimpleTimeWindowSerializer(),
                (KeySelector<Integer, String>) v -> "key1",
                new SimpleStringSerializer(),
                String.class,
                windowFunction,
                new FiringTrigger(),
                0L,
                null,
                AccumulationMode.ACCUMULATING_AND_RETRACTING
        );
        TestOutput<String> retractOutput = new TestOutput<>();
        retractOp.setOutput((Output) retractOutput);

        assertThrows(Exception.class, retractOp::open,
                "ACCUMULATING_AND_RETRACTING must fail fast on open (spec-only, not implemented)");
        retractOp.close();
    }

    @Test
    void testDiscardingWithMultipleFirings() throws Exception {
        createOperator(AccumulationMode.DISCARDING);

        processElement(10, 10);
        assertEquals("10", output.getElements().get(0));
        output.clear();

        processElement(20, 20);
        assertEquals("20", output.getElements().get(0));
        output.clear();

        processElement(30, 30);
        assertEquals("30", output.getElements().get(0),
                "DISCARDING: after multiple firings, each should only contain that element");
    }

    static class FiringTrigger extends Trigger<Object, TimeWindow> {
        @Override
        public TriggerResult onElement(Object element, long timestamp, TimeWindow window, TriggerContext ctx) {
            return TriggerResult.FIRE;
        }

        @Override
        public TriggerResult onProcessingTime(long time, TimeWindow window, TriggerContext ctx) {
            return TriggerResult.CONTINUE;
        }

        @Override
        public TriggerResult onEventTime(long time, TimeWindow window, TriggerContext ctx) {
            return TriggerResult.FIRE;
        }

        @Override
        public void clear(TimeWindow window, TriggerContext ctx) {
        }
    }

    static class EventTimeTrigger extends Trigger<Object, TimeWindow> {
        static EventTimeTrigger create() {
            return new EventTimeTrigger();
        }

        @Override
        public TriggerResult onElement(Object element, long timestamp, TimeWindow window, TriggerContext ctx) {
            if (window.maxTimestamp() <= ctx.getCurrentWatermark()) {
                return TriggerResult.FIRE;
            }
            ctx.registerEventTimeTimer(window.maxTimestamp());
            return TriggerResult.CONTINUE;
        }

        @Override
        public TriggerResult onEventTime(long time, TimeWindow window, TriggerContext ctx) {
            return time == window.maxTimestamp() ? TriggerResult.FIRE : TriggerResult.CONTINUE;
        }

        @Override
        public TriggerResult onProcessingTime(long time, TimeWindow window, TriggerContext ctx) {
            return TriggerResult.CONTINUE;
        }

        @Override
        public void clear(TimeWindow window, TriggerContext ctx) {
            ctx.deleteEventTimeTimer(window.maxTimestamp());
        }
    }

    static class PaneInfoRecorder implements InternalWindowFunction<Object, String, String, TimeWindow> {
        PaneInfo lastPaneInfo;

        @Override
        public void process(String key, TimeWindow window, InternalWindowContext context,
                            Object input, Collector<String> out) {
            lastPaneInfo = context.getPaneInfo();
            out.collect(String.valueOf(input));
        }

        @Override
        public void clear(TimeWindow window, InternalWindowContext context) {
        }
    }

    static class ConcatWindowFunction implements InternalWindowFunction<Object, String, String, TimeWindow> {
        @Override
        public void process(String key, TimeWindow window, InternalWindowContext context,
                            Object input, Collector<String> out) {
            out.collect(String.valueOf(input));
        }

        @Override
        public void clear(TimeWindow window, InternalWindowContext context) {
        }
    }

    static class TrackingEvictor implements Evictor<Object, TimeWindow> {
        boolean evictAfterCalled = false;
        boolean evictBeforeCalled = false;

        @Override
        public void evictBefore(Iterable<TimestampedValue<Object>> elements, int size,
                                TimeWindow window, EvictorContext ctx) {
            evictBeforeCalled = true;
        }

        @Override
        public void evictAfter(Iterable<TimestampedValue<Object>> elements, int size,
                               TimeWindow window, EvictorContext ctx) {
            evictAfterCalled = true;
        }
    }

    static class TestableWindowOperator extends WindowOperator<String, Integer, Object, String, TimeWindow> {

        TestableWindowOperator(
                TumblingEventTimeWindows windowAssigner,
                TypeSerializer<TimeWindow> windowSerializer,
                KeySelector<Integer, String> keySelector,
                TypeSerializer<String> keySerializer,
                Class<String> keyClass,
                InternalWindowFunction<Object, String, String, TimeWindow> windowFunction,
                Trigger<? super Integer, ? super TimeWindow> trigger,
                long allowedLateness,
                OutputTag<Integer> lateDataOutputTag,
                AccumulationMode accumulationMode) {
            super(windowAssigner, windowSerializer, keySelector, keySerializer, keyClass,
                    windowFunction, trigger, allowedLateness, lateDataOutputTag,
                    (Class<Object>) (Class<?>) Object.class, null, null, null, accumulationMode);
        }

        TestableWindowOperator(
                TumblingEventTimeWindows windowAssigner,
                TypeSerializer<TimeWindow> windowSerializer,
                KeySelector<Integer, String> keySelector,
                TypeSerializer<String> keySerializer,
                Class<String> keyClass,
                InternalWindowFunction windowFunction,
                io.nop.stream.core.windowing.triggers.Trigger trigger,
                long allowedLateness,
                OutputTag lateDataOutputTag,
                Class accClass,
                io.nop.stream.core.common.state.StateDescriptor windowStateDescriptor,
                java.util.function.BiFunction mergeFunction,
                io.nop.stream.core.windowing.evictors.Evictor evictor,
                AccumulationMode accumulationMode) {
            super(windowAssigner, windowSerializer, keySelector, keySerializer, keyClass,
                    windowFunction, trigger, allowedLateness, lateDataOutputTag,
                    accClass, windowStateDescriptor, mergeFunction, evictor, accumulationMode);
        }

        void advanceInternalWatermark(long timestamp) throws Exception {
            if (internalTimerService instanceof HeapInternalTimerService) {
                ((HeapInternalTimerService<String, TimeWindow>) internalTimerService).advanceWatermark(timestamp);
            }
        }
    }

    static class SimpleTimeWindowSerializer implements TypeSerializer<TimeWindow> {
        @Override
        public boolean isImmutableType() { return true; }
        @Override
        public TypeSerializer<TimeWindow> duplicate() { return this; }
        @Override
        public TimeWindow createInstance() { return new TimeWindow(0, 0); }
        @Override
        public TimeWindow copy(TimeWindow from) { return new TimeWindow(from.getStart(), from.getEnd()); }
        @Override
        public TimeWindow copy(TimeWindow from, TimeWindow reuse) { return new TimeWindow(from.getStart(), from.getEnd()); }
        @Override
        public int getLength() { return -1; }
    }

    static class SimpleStringSerializer implements TypeSerializer<String> {
        @Override
        public boolean isImmutableType() { return true; }
        @Override
        public TypeSerializer<String> duplicate() { return this; }
        @Override
        public String createInstance() { return ""; }
        @Override
        public String copy(String from) { return from; }
        @Override
        public String copy(String from, String reuse) { return from; }
        @Override
        public int getLength() { return -1; }
    }
}
