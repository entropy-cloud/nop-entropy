package io.nop.stream.runtime.operators.windowing;

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
import io.nop.stream.core.windowing.triggers.EventTimeTrigger;
import io.nop.stream.core.windowing.windows.TimeWindow;
import io.nop.stream.runtime.operators.windowing.functions.InternalWindowFunction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * I2 WO-2 (R15-AR-8) dynamic verification: the {@link WindowOperator} onEventTime cleanup
 * path (WindowOperator.java:773-782) clears window contents and trigger state but does NOT
 * call {@link MergingWindowSet#retireWindow(Window)}. This test constructs a merging
 * (session) window scenario — trigger fires after merge, then the cleanup timer fires —
 * and verifies whether the {@link MergingWindowSet} converges (no stale window retained).
 *
 * <p>PIN semantics: this test pins the CURRENT behavior (the cleaned window remains in the
 * merging window set = confirmed state leak). Verdict: R15-AR-8 升格 red list. I4 fix:
 * add {@code mergingWindows.retireWindow(stateWindow)} in the cleanup branch.
 */
public class TestWindowOperatorMergingCleanupInvariant {

    /**
     * Session-window operator exposing the merging window set for inspection.
     */
    static class TestableMergingWindowOperator
            extends WindowOperator<String, Integer, Object, String, TimeWindow> {

        TestableMergingWindowOperator(
                EventTimeSessionWindows windowAssigner,
                TypeSerializer<TimeWindow> windowSerializer,
                KeySelector<Integer, String> keySelector,
                TypeSerializer<String> keySerializer,
                Class<String> keyClass,
                InternalWindowFunction<Object, String, String, TimeWindow> windowFunction,
                EventTimeTrigger trigger,
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

        public MergingWindowSet<TimeWindow> mergingWindowSetForTest() throws Exception {
            return getMergingWindowSet();
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
        // R15-AR-8 (I2 WO-2): merging window (session gap 50) + cleanup convergence.
        // Scenario: element at t=10 creates session [10,60); element at t=30 merges into
        // [10,80) (maxTimestamp=79, cleanup timer at 79). Advance watermark to 79:
        //   - EventTimeTrigger fires (sum=30 emitted)
        //   - cleanup branch (WindowOperator.java:773-782) clears contents + trigger,
        //     but does NOT call mergingWindows.retireWindow(stateWindow)
        // Assertion (PIN): the cleaned window [10,80) must NOT remain in the MergingWindowSet
        // mapping (converged, no window state leak). Current code retains it → leak confirmed.
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

            // PIN (R15-AR-8 → red list 升格): after cleanup the window must be retired
            // from the merging set (convergence). Current code retains the stale entry
            // in the checkpointed merging-sets state → unbounded state growth and wrong
            // window ranges for subsequent sessions overlapping the cleaned range.
            assertNotNull(operator.mergingWindowSetForTest().getStateWindow(new TimeWindow(10, 80)),
                    "PIN (R15-AR-8): cleaned session window remains in MergingWindowSet after "
                            + "onEventTime cleanup — window state leak, upgrade to red list");
        } finally {
            operator.close();
        }
    }

    @Test
    void testSessionWindowCleanupAndLaterElementMergesIntoStaleRangeIsPinned() throws Exception {
        // R15-AR-8 consequence (I2 WO-2): because the cleaned window [10,80) is never
        // retired, a LATER element whose window overlaps the stale range merges into it,
        // resurrecting a window that starts at the cleaned window's start (10) instead of
        // the new element's own start (55). PIN: current behavior emits window [10,105).
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

            // PIN: element at t=55 arrives AFTER the session [10,60) was cleaned. In a
            // converged system the stale [10,60) would be retired and the element forms a
            // fresh session [55,105). Current code merges it into the stale window →
            // MergingWindowSet maps [10,105) → [10,60) and the emitted window range
            // resurrects the cleaned prefix.
            operator.processElement(new StreamRecord<>(50, 55));
            operator.advanceInternalWatermark(104);

            MergingWindowSet<TimeWindow> set = operator.mergingWindowSetForTest();
            assertNotNull(set.getStateWindow(new TimeWindow(10, 105)),
                    "PIN (R15-AR-8): later element merged into stale cleaned window range");
            assertEquals(1, output.size());
            assertEquals("sum=50", output.getElements().get(0));
        } finally {
            operator.close();
        }
    }
}
