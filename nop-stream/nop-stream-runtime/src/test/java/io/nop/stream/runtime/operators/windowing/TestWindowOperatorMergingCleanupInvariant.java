package io.nop.stream.runtime.operators.windowing;

import java.util.Collection;
import java.util.Collections;

import io.nop.core.context.IServiceContext;
import io.nop.stream.core.common.accumulators.IntCounter;
import io.nop.stream.core.common.accumulators.SimpleAccumulator;
import io.nop.stream.core.common.functions.KeySelector;
import io.nop.stream.core.common.typeutils.TypeSerializer;
import io.nop.stream.core.operators.HeapInternalTimerService;
import io.nop.stream.core.operators.Output;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.test.TestOutput;
import io.nop.stream.core.util.OutputTag;
import io.nop.stream.core.windowing.assigners.EventTimeSessionWindows;
import io.nop.stream.core.windowing.assigners.MergingWindowAssigner;
import io.nop.stream.core.windowing.assigners.WindowAssigner;
import io.nop.stream.core.windowing.triggers.EventTimeTrigger;
import io.nop.stream.core.windowing.triggers.ProcessingTimeTrigger;
import io.nop.stream.core.windowing.triggers.Trigger;
import io.nop.stream.core.windowing.windows.TimeWindow;
import io.nop.stream.runtime.operators.windowing.functions.InternalWindowFunction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * I2 WO-2 (R15-AR-8) dynamic verification → I4 RL-6 fix verification: the {@link WindowOperator}
 * onEventTime cleanup path (WindowOperator.java:773-782) clears window contents and trigger state
 * but (pre-I4) did NOT call {@link MergingWindowSet#retireWindow(Window)}. This test constructs a
 * merging (session) window scenario — trigger fires after merge, then the cleanup timer fires —
 * and verifies the {@link MergingWindowSet} converges (no stale window retained).
 *
 * <p>I1/I2 PIN semantics: this test pinned the CURRENT leak behavior (the cleaned window remained
 * in the merging window set = confirmed state leak, red-list upgrade). I4 fix: added
 * {@code mergingWindows.retireWindow(triggerContext.window)} in the cleanup branches — mapping key
 * (cleanup timer namespace), NOT the state-window value. The pins are now flipped: cleanup must
 * converge the mapping, and a later overlapping element must form a fresh window range.
 */
public class TestWindowOperatorMergingCleanupInvariant {

    /**
     * Session-window operator exposing the merging window set for inspection.
     */
    static class TestableMergingWindowOperator
            extends WindowOperator<String, Integer, Object, String, TimeWindow> {

        TestableMergingWindowOperator(
                WindowAssigner<Object, TimeWindow> windowAssigner,
                TypeSerializer<TimeWindow> windowSerializer,
                KeySelector<Integer, String> keySelector,
                TypeSerializer<String> keySerializer,
                Class<String> keyClass,
                InternalWindowFunction<Object, String, String, TimeWindow> windowFunction,
                Trigger<Object, TimeWindow> trigger,
                long allowedLateness,
                OutputTag<Integer> lateDataOutputTag) {
            super(windowAssigner, windowSerializer, keySelector, keySerializer, keyClass,
                    windowFunction, trigger, allowedLateness, lateDataOutputTag);
        }

        @Override
        protected SimpleAccumulator<Integer> createAccumulatorForWindow() {
            return new IntCounter();
        }

        public void advanceInternalWatermark(long timestamp) throws Exception {
            if (internalTimerService instanceof HeapInternalTimerService) {
                ((HeapInternalTimerService<String, TimeWindow>) internalTimerService).advanceWatermark(timestamp);
            }
        }

        public void fireProcessingTimeTimers(long timestamp) throws Exception {
            if (internalTimerService instanceof HeapInternalTimerService) {
                ((HeapInternalTimerService<String, TimeWindow>) internalTimerService)
                        .fireProcessingTimeTimers(timestamp);
            }
        }

        public MergingWindowSet<TimeWindow> mergingWindowSetForTest() throws Exception {
            return getMergingWindowSet();
        }
    }

    /**
     * Test-local non-event-time merging assigner (no production processing-time merging assigner
     * exists — only {@link EventTimeSessionWindows}). Windows are derived from the element
     * timestamp so the scenario is fully deterministic; {@code isEventTime()=false} routes the
     * cleanup timer through the processing-time timer path.
     */
    static class ProcessingTimeMergingSessionWindows extends MergingWindowAssigner<Object, TimeWindow> {
        private static final long serialVersionUID = 1L;

        private final long sessionTimeout;

        ProcessingTimeMergingSessionWindows(long sessionTimeout) {
            this.sessionTimeout = sessionTimeout;
        }

        @Override
        public Collection<TimeWindow> assignWindows(Object element, long timestamp, WindowAssignerContext assignerContext) {
            return Collections.singletonList(new TimeWindow(timestamp, timestamp + sessionTimeout));
        }

        @Override
        public Trigger<Object, TimeWindow> getDefaultTrigger(IServiceContext serviceContext) {
            return ProcessingTimeTrigger.create();
        }

        @Override
        public boolean isEventTime() {
            return false;
        }

        @Override
        public void mergeWindows(Collection<TimeWindow> windows, MergeCallback<TimeWindow> callback) {
            TimeWindow.mergeWindows(windows, callback);
        }
    }

    static class SumWindowFunction implements InternalWindowFunction<Object, String, String, TimeWindow> {
        private static final long serialVersionUID = 1L;

        @Override
        public void process(String key, TimeWindow window, InternalWindowContext context,
                            Object input, io.nop.stream.core.util.Collector<String> out) {
            if (input instanceof IntCounter) {
                out.collect("sum=" + ((IntCounter) input).getLocalValuePrimitive());
            } else {
                out.collect("value=" + input);
            }
        }

        @Override
        public void clear(TimeWindow window, InternalWindowContext context) {
        }
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

    @Test
    void testSessionWindowCleanupRetainsMergingWindowIsPinned() throws Exception {
        // RL-6 (R15-AR-8): merging window (session gap 50) + cleanup convergence.
        // Scenario: element at t=10 creates session [10,60); element at t=30 merges into
        // [10,80) (maxTimestamp=79, cleanup timer at 79). Advance watermark to 79:
        //   - EventTimeTrigger fires (sum=30 emitted)
        //   - cleanup branch (WindowOperator.java:773-782) clears contents + trigger
        //     AND must retire the in-flight window from the MergingWindowSet
        // Assertion (FLIPPED from I2 pin): the cleaned window [10,80) must NOT remain in
        // the MergingWindowSet mapping (converged, no window state leak).
        TestOutput<String> output = new TestOutput<>();

        TestableMergingWindowOperator operator = new TestableMergingWindowOperator(
                EventTimeSessionWindows.withGap(50L),
                new SimpleTimeWindowSerializer(),
                (KeySelector<Integer, String>) v -> "key1",
                new SimpleStringSerializer(),
                String.class,
                new SumWindowFunction(),
                EventTimeTrigger.create(),
                0L,
                null
        );

        operator.setOutput((Output) output);
        operator.open();

        try {
            // Element at t=10 → session [10, 60)
            operator.processElement(new StreamRecord<>(10, 10));
            // Element at t=30 → merges into [10, 80)
            operator.processElement(new StreamRecord<>(20, 30));

            // Window must be tracked in the merging set before cleanup.
            assertNotNull(operator.mergingWindowSetForTest().getStateWindow(new TimeWindow(10, 80)),
                    "window [10,80) must be in the merging set while active");

            // Advance watermark to cleanup time 79: trigger fires + cleanup branch runs.
            operator.advanceInternalWatermark(79);

            // Trigger fired with merged accumulator.
            assertEquals(1, output.size());
            assertEquals("sum=30", output.getElements().get(0));

            // RL-6 fixed (pin flipped): after cleanup the window must be retired from the
            // merging set (convergence) — no unbounded growth of the checkpointed
            // merging-sets state, no resurrected stale ranges.
            assertNull(operator.mergingWindowSetForTest().getStateWindow(new TimeWindow(10, 80)),
                    "RL-6 fixed: cleaned session window must be retired from MergingWindowSet "
                            + "after onEventTime cleanup (convergence, no window state leak)");
        } finally {
            operator.close();
        }
    }

    @Test
    void testSessionWindowCleanupAndLaterElementMergesIntoStaleRangeIsPinned() throws Exception {
        // RL-6 consequence: because the cleaned window [10,60) is now retired, a LATER
        // element whose window overlaps the cleaned range forms a FRESH window starting at
        // its own start (55) instead of resurrecting the cleaned window's prefix.
        // Assertion (FLIPPED from I2 pin): later element must produce [55,105), never [10,105).
        TestOutput<String> output = new TestOutput<>();

        TestableMergingWindowOperator operator = new TestableMergingWindowOperator(
                EventTimeSessionWindows.withGap(50L),
                new SimpleTimeWindowSerializer(),
                (KeySelector<Integer, String>) v -> "key1",
                new SimpleStringSerializer(),
                String.class,
                new SumWindowFunction(),
                EventTimeTrigger.create(),
                0L,
                null
        );

        operator.setOutput((Output) output);
        operator.open();

        try {
            // Element at t=10 → session [10, 60), then advance past cleanup (59).
            operator.processElement(new StreamRecord<>(10, 10));
            operator.advanceInternalWatermark(59);
            assertEquals(1, output.size());
            assertEquals("sum=10", output.getElements().get(0));
            output.clear();

            // RL-6: element at t=55 arrives AFTER the session [10,60) was cleaned. The stale
            // [10,60) must be retired, so the element forms a fresh session [55,105) instead
            // of merging into the cleaned prefix.
            operator.processElement(new StreamRecord<>(50, 55));

            MergingWindowSet<TimeWindow> set = operator.mergingWindowSetForTest();
            assertNull(set.getStateWindow(new TimeWindow(10, 105)),
                    "RL-6 fixed: later element must NOT merge into the cleaned (retired) window range");
            assertNotNull(set.getStateWindow(new TimeWindow(55, 105)),
                    "RL-6 fixed: later element must form a fresh session window [55,105)");

            operator.advanceInternalWatermark(104);
            assertEquals(1, output.size());
            assertEquals("sum=50", output.getElements().get(0));
        } finally {
            operator.close();
        }
    }

    /**
     * RL-6 long-run: repeated session open/close cycles must converge after every cleanup —
     * the checkpointed merging-sets state must not grow across cycles.
     */
    @Test
    void testRepeatedSessionOpenCloseCheckpointStateConverges() throws Exception {
        TestOutput<String> output = new TestOutput<>();

        TestableMergingWindowOperator operator = new TestableMergingWindowOperator(
                EventTimeSessionWindows.withGap(50L),
                new SimpleTimeWindowSerializer(),
                (KeySelector<Integer, String>) v -> "key1",
                new SimpleStringSerializer(),
                String.class,
                new SumWindowFunction(),
                EventTimeTrigger.create(),
                0L,
                null
        );

        operator.setOutput((Output) output);
        operator.open();

        try {
            for (int round = 0; round < 5; round++) {
                long base = 1000L * round;
                // Open a session window (two elements merging into [base, base+80)).
                operator.processElement(new StreamRecord<>(10 + round, base));
                operator.processElement(new StreamRecord<>(20 + round, base + 30));
                MergingWindowSet<TimeWindow> set = operator.mergingWindowSetForTest();
                assertNotNull(set.getStateWindow(new TimeWindow(base, base + 80)),
                        "session window must be tracked while active (round " + round + ")");

                // Close the session: advance past cleanup time (maxTimestamp = base + 79).
                operator.advanceInternalWatermark(base + 79);

                // RL-6: after cleanup the whole cycle's windows must be retired (no growth).
                set = operator.mergingWindowSetForTest();
                assertNull(set.getStateWindow(new TimeWindow(base, base + 80)),
                        "RL-6 fixed: cleaned window must be retired each cycle (round " + round + ")");
                assertNull(set.getStateWindow(new TimeWindow(base, base + 50)),
                        "pre-merge window [base,base+50) must be gone (round " + round + ")");
                assertNull(set.getStateWindow(new TimeWindow(base + 30, base + 80)),
                        "pre-merge window [base+30,base+80) must be gone (round " + round + ")");
                output.clear();
            }
        } finally {
            operator.close();
        }
    }

    /**
     * RL-6 onProcessingTime branch (WindowOperator.java:834-843): the same missing retireWindow
     * existed in the processing-time cleanup path. Exercised with a test-local non-event-time
     * merging assigner + {@link HeapInternalTimerService#fireProcessingTimeTimers}.
     */
    @Test
    void testProcessingTimeCleanupRetiresMergingWindow() throws Exception {
        TestOutput<String> output = new TestOutput<>();

        TestableMergingWindowOperator operator = new TestableMergingWindowOperator(
                new ProcessingTimeMergingSessionWindows(50L),
                new SimpleTimeWindowSerializer(),
                (KeySelector<Integer, String>) v -> "key1",
                new SimpleStringSerializer(),
                String.class,
                new SumWindowFunction(),
                ProcessingTimeTrigger.create(),
                0L,
                null
        );

        operator.setOutput((Output) output);
        operator.open();

        try {
            // Far-future deterministic element timestamps (non-event-time windows are never
            // late; real wall-clock never reaches them).
            long t0 = System.currentTimeMillis() + 10_000_000L;
            operator.processElement(new StreamRecord<>(10, t0));      // [t0, t0+50)
            operator.processElement(new StreamRecord<>(20, t0 + 20)); // merges into [t0, t0+70)

            MergingWindowSet<TimeWindow> set = operator.mergingWindowSetForTest();
            assertNotNull(set.getStateWindow(new TimeWindow(t0, t0 + 70)),
                    "merged window must be tracked while active");

            // Fire all processing-time timers (trigger + cleanup are both registered at
            // maxTimestamp = t0 + 69).
            operator.fireProcessingTimeTimers(t0 + 69);

            // Re-fetch: each getMergingWindowSet() call re-reads the persisted state, so the
            // pre-fire instance would be stale.
            set = operator.mergingWindowSetForTest();
            assertNull(set.getStateWindow(new TimeWindow(t0, t0 + 70)),
                    "RL-6 fixed: processing-time cleanup must retire the merged in-flight window");
            assertNull(set.getStateWindow(new TimeWindow(t0, t0 + 50)),
                    "pre-merge window must be gone after processing-time cleanup");
            assertNull(set.getStateWindow(new TimeWindow(t0 + 20, t0 + 70)),
                    "pre-merge window must be gone after processing-time cleanup");
        } finally {
            operator.close();
        }
    }
}
