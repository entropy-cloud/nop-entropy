package io.nop.stream.runtime.operators.windowing;

import io.nop.stream.core.checkpoint.OperatorSnapshotResult;
import io.nop.stream.core.checkpoint.StateSnapshotContext;
import io.nop.stream.core.common.accumulators.LongCounter;
import io.nop.stream.core.common.functions.KeySelector;
import io.nop.stream.core.common.state.ReducingStateDescriptor;
import io.nop.stream.core.common.typeutils.TypeSerializer;
import io.nop.stream.core.operators.Output;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.streamrecord.watermark.Watermark;
import io.nop.stream.core.test.TestOutput;
import io.nop.stream.core.util.OutputTag;
import io.nop.stream.core.windowing.assigners.EventTimeSessionWindows;
import io.nop.stream.core.windowing.assigners.GlobalWindows;
import io.nop.stream.core.windowing.assigners.TumblingEventTimeWindows;
import io.nop.stream.core.windowing.assigners.TumblingProcessingTimeWindows;
import io.nop.stream.core.windowing.triggers.ContinuousProcessingTimeTrigger;
import io.nop.stream.core.windowing.triggers.Trigger;
import io.nop.stream.core.windowing.triggers.TriggerResult;
import io.nop.stream.core.windowing.windows.GlobalWindow;
import io.nop.stream.core.windowing.windows.TimeWindow;
import io.nop.stream.core.windowing.windows.Window;
import io.nop.stream.runtime.operators.windowing.functions.InternalWindowFunction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P1-INV-1 (AR-02 Phase 4) regression tests: {@code triggerAccumulators} entries
 * must be removed when their window is purged, cleaned up, or merged away — the
 * map previously only ever grew (single write point, zero removals), leaking
 * entries into the checkpoint and the restored operator forever.
 *
 * <p><b>Assertion timing (the rebuild trap):</b> every assertion runs AFTER the
 * cleanup completes (purge / timer fire / merge). {@code CountTrigger.clear} /
 * {@code ContinuousProcessingTimeTrigger.clear} call
 * {@code getSimpleAccumulator()}, which re-creates a deleted entry on miss, so
 * asserting inside {@code clearWindowContents} would be masked by the rebuild.
 */
class TestWindowOperatorTriggerAccumulatorCleanup {

    private static final String KEY = "key1";
    private static final String STATE_KEY_SEPARATOR = "\u0000";

    private TestOutput<String> output;
    private TestableWindowOperator<?> operator;

    @AfterEach
    void tearDown() throws Exception {
        if (operator != null) {
            operator.close();
        }
    }

    private void openWindowOperator(WindowOperator<?, ?, ?, ?, ?> op) throws Exception {
        output = new TestOutput<>();
        op.setOutput((Output) output);
        op.open();
        operator = (TestableWindowOperator<?>) op;
    }

    private static String triggerKeyPrefix(Window window) {
        return "trigger_" + KEY + STATE_KEY_SEPARATOR + window + STATE_KEY_SEPARATOR;
    }

    private boolean hasEntryFor(Window window) {
        Map<String, ?> accums = operator.getTriggerAccumulators();
        if (accums == null || accums.isEmpty()) {
            return false;
        }
        String prefix = triggerKeyPrefix(window);
        for (String stateKey : accums.keySet()) {
            if (stateKey.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    // ==================== element-path purge (regular window) ====================

    /**
     * A trigger that creates accumulator state on every element and answers
     * FIRE_AND_PURGE — the regular element path's purge branch
     * ({@code clearWindowContents + triggerContext.clear() + removeTriggerAccumulators}).
     * After the purge the entry must be GONE (pre-fix: it stayed forever).
     */
    @Test
    void testElementPurgeRemovesTriggerAccumulators() throws Exception {
        TestableWindowOperator<GlobalWindow> op = new TestableWindowOperator<>(
                GlobalWindows.create(),
                new SimpleGlobalWindowSerializer(),
                (KeySelector<Integer, String>) v -> KEY,
                new SimpleStringSerializer(),
                String.class,
                new PassthroughWindowFunction<>(),
                new PurgeOnElementTrigger(),
                0L,
                null);
        openWindowOperator(op);

        op.processElement(new StreamRecord<>(1, 10));

        assertNotNull(op.getTriggerAccumulators());
        assertTrue(op.getTriggerAccumulators().isEmpty(),
                "Element-path purge must remove the trigger accumulator entry (pre-fix: leaked forever)");
        assertEquals(1, output.size(), "FIRE_AND_PURGE still emits the window contents");
    }

    // ==================== event-time timer PURGE ====================

    /**
     * A trigger that registers an event-time fire timer and answers FIRE_AND_PURGE
     * when it fires — the {@code onEventTime} timer PURGE branch. Pre-fix the
     * branch cleared window contents but neither the trigger state nor the
     * accumulator entry.
     */
    @Test
    void testEventTimeTimerPurgeRemovesTriggerAccumulators() throws Exception {
        TestableWindowOperator<TimeWindow> op = new TestableWindowOperator<>(
                TumblingEventTimeWindows.of(100),
                new SimpleTimeWindowSerializer(),
                (KeySelector<Integer, String>) v -> KEY,
                new SimpleStringSerializer(),
                String.class,
                new PassthroughWindowFunction<>(),
                new PurgeOnEventTimeTrigger(),
                0L,
                null);
        openWindowOperator(op);

        op.processElement(new StreamRecord<>(1, 10));
        TimeWindow window = new TimeWindow(0, 100);
        assertTrue(hasEntryFor(window), "The trigger must have created accumulator state");

        op.processWatermark(new Watermark(Long.MAX_VALUE));

        assertFalse(hasEntryFor(window),
                "Event-time timer PURGE must remove the trigger accumulator entry");
        assertEquals(1, output.size(), "FIRE_AND_PURGE still emits the window contents");
    }

    // ==================== processing-time timer PURGE ====================

    /**
     * The {@code onProcessingTime} timer PURGE branch (mirror of the event-time
     * one). The operator's registered processing-time cleanup timer fires via the
     * real {@code HeapInternalTimerService.fireProcessingTimeTimers} path.
     */
    @Test
    void testProcessingTimeTimerPurgeRemovesTriggerAccumulators() throws Exception {
        TestableWindowOperator<TimeWindow> op = new TestableWindowOperator<>(
                TumblingProcessingTimeWindows.of(100),
                new SimpleTimeWindowSerializer(),
                (KeySelector<Integer, String>) v -> KEY,
                new SimpleStringSerializer(),
                String.class,
                new PassthroughWindowFunction<>(),
                new PurgeOnProcessingTimeTrigger(),
                0L,
                null);
        openWindowOperator(op);

        op.processElement(new StreamRecord<>(1));
        assertFalse(op.getTriggerAccumulators().isEmpty(),
                "The trigger must have created accumulator state");

        op.internalTimerService.fireProcessingTimeTimers(Long.MAX_VALUE);

        assertTrue(op.getTriggerAccumulators().isEmpty(),
                "Processing-time timer PURGE must remove the trigger accumulator entry");
    }

    // ==================== merge path ====================

    /**
     * Merge path: two session windows merge; the merged-away window's accumulator
     * entry must be removed (key baseline = the MERGED window m, not the
     * stateWindow). Pre-fix the merged-away entry stayed in the map forever.
     */
    @Test
    void testMergedAwayWindowTriggerAccumulatorRemoved() throws Exception {
        TestableWindowOperator<TimeWindow> op = new TestableWindowOperator<>(
                EventTimeSessionWindows.withGap(Duration.ofMillis(100)),
                new SimpleTimeWindowSerializer(),
                (KeySelector<Integer, String>) v -> KEY,
                new SimpleStringSerializer(),
                String.class,
                new PassthroughWindowFunction<>(),
                ContinuousProcessingTimeTrigger.of(Duration.ofSeconds(1)),
                0L,
                null);
        openWindowOperator(op);

        // Element 1 → session window [0,100); element 2 (ts=50) → [50,150),
        // overlapping → the two merge into [0,150) and [0,100) is merged away.
        op.processElement(new StreamRecord<>(1, 0));
        op.processElement(new StreamRecord<>(2, 50));

        TimeWindow mergedAway = new TimeWindow(0, 100);
        assertFalse(hasEntryFor(mergedAway),
                "The merged-away window's trigger accumulator entry must be removed "
                        + "(pre-fix: leaked forever). Entries: " + op.getTriggerAccumulators().keySet());
        assertEquals(1, op.getTriggerAccumulators().size(),
                "The only live entry is the merged-window one (the trigger's onMerge created it "
                        + "for the merge result and the second element's onElement reuses it). "
                        + "Entries: " + op.getTriggerAccumulators().keySet());
    }

    // ==================== snapshot / restore no-dead-entries ====================

    /**
     * After a purge, the checkpointed {@code trigger-accumulators} operator state
     * must not contain dead entries, and a restored operator must not resurrect
     * them. Pre-fix the entry was snapshotted and restored forever.
     */
    @Test
    void testSnapshotAndRestoreContainNoDeadEntriesAfterPurge() throws Exception {
        TestableWindowOperator<GlobalWindow> op = new TestableWindowOperator<>(
                GlobalWindows.create(),
                new SimpleGlobalWindowSerializer(),
                (KeySelector<Integer, String>) v -> KEY,
                new SimpleStringSerializer(),
                String.class,
                new PassthroughWindowFunction<>(),
                new PurgeOnElementTrigger(),
                0L,
                null);
        openWindowOperator(op);

        op.processElement(new StreamRecord<>(1, 10));
        assertTrue(op.getTriggerAccumulators().isEmpty(),
                "The purge must have completed before the snapshot is taken");

        OperatorSnapshotResult snapshot =
                op.snapshotState(new StateSnapshotContext(1, System.currentTimeMillis()));
        @SuppressWarnings("unchecked")
        Map<String, Object> triggerAccState =
                (Map<String, Object>) snapshot.getOperatorState("trigger-accumulators");
        assertNotNull(triggerAccState);
        assertTrue(triggerAccState.isEmpty(),
                "Snapshot must not contain dead trigger accumulator entries");
        op.close();

        TestableWindowOperator<GlobalWindow> restored = new TestableWindowOperator<>(
                GlobalWindows.create(),
                new SimpleGlobalWindowSerializer(),
                (KeySelector<Integer, String>) v -> KEY,
                new SimpleStringSerializer(),
                String.class,
                new PassthroughWindowFunction<>(),
                new PurgeOnElementTrigger(),
                0L,
                null);
        restored.setOutput((Output) new TestOutput<>());
        restored.restoreState(snapshot);
        restored.open();

        assertNotNull(restored.getTriggerAccumulators());
        assertTrue(restored.getTriggerAccumulators().isEmpty(),
                "Restored operator must not resurrect dead trigger accumulator entries");
        restored.close();
    }

    // ==================== helpers ====================

    static class TestableWindowOperator<W extends Window>
            extends WindowOperator<String, Integer, Object, String, W> {

        public TestableWindowOperator(
                io.nop.stream.core.windowing.assigners.WindowAssigner<? super Integer, W> windowAssigner,
                TypeSerializer<W> windowSerializer,
                KeySelector<Integer, String> keySelector,
                TypeSerializer<String> keySerializer,
                Class<String> keyClass,
                InternalWindowFunction<Object, String, String, W> windowFunction,
                Trigger<? super Integer, ? super W> trigger,
                long allowedLateness,
                OutputTag<Integer> lateDataOutputTag) {
            super(windowAssigner, windowSerializer, keySelector, keySerializer, keyClass,
                    windowFunction, trigger, allowedLateness, lateDataOutputTag);
        }

        public Map<String, ?> getTriggerAccumulators() {
            return triggerAccumulators;
        }
    }

    /** Fires AND purges on every element; creates accumulator state in onElement. */
    static class PurgeOnElementTrigger extends Trigger<Object, GlobalWindow> {
        private static final long serialVersionUID = 1L;
        private final ReducingStateDescriptor<Long> desc =
                new ReducingStateDescriptor<>("purge-count", Long.class, LongCounter.class);

        @Override
        public TriggerResult onElement(Object element, long timestamp, GlobalWindow window, TriggerContext ctx) {
            ctx.getSimpleAccumulator(desc).add(1L);
            return TriggerResult.FIRE_AND_PURGE;
        }

        @Override
        public TriggerResult onEventTime(long time, GlobalWindow window, TriggerContext ctx) {
            return TriggerResult.CONTINUE;
        }

        @Override
        public TriggerResult onProcessingTime(long time, GlobalWindow window, TriggerContext ctx) {
            return TriggerResult.CONTINUE;
        }

        @Override
        public void clear(GlobalWindow window, TriggerContext ctx) {
            ctx.getSimpleAccumulator(desc).clear();
        }

        @Override
        public boolean canMerge() {
            return false;
        }

        @Override
        public void onMerge(GlobalWindow window, OnMergeContext ctx) {
        }
    }

    /** Registers an event-time fire timer; answers FIRE_AND_PURGE when it fires. */
    static class PurgeOnEventTimeTrigger extends Trigger<Object, TimeWindow> {
        private static final long serialVersionUID = 1L;
        private final ReducingStateDescriptor<Long> desc =
                new ReducingStateDescriptor<>("purge-count", Long.class, LongCounter.class);

        @Override
        public TriggerResult onElement(Object element, long timestamp, TimeWindow window, TriggerContext ctx) {
            ctx.getSimpleAccumulator(desc).add(1L);
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
            ctx.getSimpleAccumulator(desc).clear();
        }

        @Override
        public boolean canMerge() {
            return false;
        }

        @Override
        public void onMerge(TimeWindow window, OnMergeContext ctx) {
        }
    }

    /** Answers FIRE_AND_PURGE on processing-time timer fire. */
    static class PurgeOnProcessingTimeTrigger extends Trigger<Object, TimeWindow> {
        private static final long serialVersionUID = 1L;
        private final ReducingStateDescriptor<Long> desc =
                new ReducingStateDescriptor<>("purge-count", Long.class, LongCounter.class);

        @Override
        public TriggerResult onElement(Object element, long timestamp, TimeWindow window, TriggerContext ctx) {
            ctx.getSimpleAccumulator(desc).add(1L);
            return TriggerResult.CONTINUE;
        }

        @Override
        public TriggerResult onEventTime(long time, TimeWindow window, TriggerContext ctx) {
            return TriggerResult.CONTINUE;
        }

        @Override
        public TriggerResult onProcessingTime(long time, TimeWindow window, TriggerContext ctx) {
            return TriggerResult.FIRE_AND_PURGE;
        }

        @Override
        public void clear(TimeWindow window, TriggerContext ctx) {
            ctx.getSimpleAccumulator(desc).clear();
        }

        @Override
        public boolean canMerge() {
            return false;
        }

        @Override
        public void onMerge(TimeWindow window, OnMergeContext ctx) {
        }
    }

    static class PassthroughWindowFunction<W extends Window>
            implements InternalWindowFunction<Object, String, String, W> {
        private static final long serialVersionUID = 1L;

        @Override
        public void process(String key, W window, InternalWindowContext context,
                            Object input, io.nop.stream.core.util.Collector<String> out) {
            out.collect(String.valueOf(input));
        }

        @Override
        public void clear(W window, InternalWindowContext context) {
        }
    }

    static class SimpleGlobalWindowSerializer implements TypeSerializer<GlobalWindow> {
        private static final long serialVersionUID = 1L;

        @Override
        public boolean isImmutableType() { return true; }

        @Override
        public TypeSerializer<GlobalWindow> duplicate() { return this; }

        @Override
        public GlobalWindow createInstance() { return GlobalWindow.get(); }

        @Override
        public GlobalWindow copy(GlobalWindow from) { return from; }

        @Override
        public GlobalWindow copy(GlobalWindow from, GlobalWindow reuse) { return from; }

        @Override
        public int getLength() { return -1; }
    }

    static class SimpleTimeWindowSerializer implements TypeSerializer<TimeWindow> {
        private static final long serialVersionUID = 1L;

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
        private static final long serialVersionUID = 1L;

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
