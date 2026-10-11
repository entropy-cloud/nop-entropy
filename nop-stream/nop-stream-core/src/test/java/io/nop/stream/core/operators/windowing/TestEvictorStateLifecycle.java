package io.nop.stream.runtime.operators.windowing;

import io.nop.stream.core.common.functions.AggregateFunction;
import io.nop.stream.core.common.functions.KeySelector;
import io.nop.stream.core.common.typeutils.TypeSerializer;
import io.nop.stream.core.operators.HeapInternalTimerService;
import io.nop.stream.core.operators.Output;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.test.TestOutput;
import io.nop.stream.core.windowing.assigners.GlobalWindows;
import io.nop.stream.core.windowing.assigners.TumblingEventTimeWindows;
import io.nop.stream.core.windowing.evictors.CountEvictor;
import io.nop.stream.core.windowing.evictors.Evictor;
import io.nop.stream.core.windowing.triggers.CountTrigger;
import io.nop.stream.core.windowing.triggers.EventTimeTrigger;
import io.nop.stream.core.windowing.utils.TimestampedValue;
import io.nop.stream.core.windowing.windows.GlobalWindow;
import io.nop.stream.core.windowing.windows.TimeWindow;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AR-2/AR-3 regression proofs (plan 1326-2 Phase 1).
 *
 * <p>AR-2: evictor eviction must physically shrink the window-contents state —
 * pre-fix, eviction only trimmed a local copy and evicted elements returned on
 * every subsequent fire; {@code countWindow(size, slide)} (GlobalWindows +
 * CountEvictor + CountTrigger) grew without bound. Both evictor callbacks must
 * receive the PRE-eviction element count (Flink semantics).
 *
 * <p>AR-3: the descriptor (internal-backend) path must persist real element
 * timestamps — pre-fix the side store was never created on this path, every
 * element was stamped with the current watermark and TimeEvictor never evicted.
 */
class TestEvictorStateLifecycle {

    // ------------------------------------------------------------------
    // AR-2 / AR-2b: GlobalWindows + CountEvictor + CountTrigger (countWindow(size, slide))
    // ------------------------------------------------------------------

    /**
     * Boundedness: after N >> size elements through a countWindow(size, slide)-shaped
     * operator, the window-contents list state holds at most `size` elements — the
     * eviction write-back shrinks the state on every fire (pre-fix: N elements).
     */
    @Test
    void testCountWindowSlideStateBounded() throws Exception {
        int size = 3;
        WindowOperator<String, Integer, long[], String, GlobalWindow> op = countWindowOperator(size, size);

        TestOutput<String> output = new TestOutput<>();
        op.setOutput((Output) output);
        op.open();
        try {
            for (int i = 1; i <= 30; i++) {
                op.processElement(new StreamRecord<>(i, i));
            }

            // 30 elements / slide 3 = 10 fires; state must stay bounded at the evictor
            // capacity (3), not track the total input volume (30).
            List<GlobalWindow> stateElements = readGlobalWindowState(op);
            assertEquals(size, stateElements.size(),
                    "after the last fire the state should hold exactly the last " + size
                            + " elements (bounded by the evictor capacity, not the 30-element input)");
        } finally {
            op.close();
        }
    }

    /**
     * Eviction permanence: each fire aggregates ONLY the elements retained by the
     * previous eviction plus the new arrivals — evicted elements never return
     * (pre-fix fire #2 summed 1..6 = 21 instead of 4..6 = 15).
     */
    @Test
    void testEvictedElementsDoNotReturnToAggregation() throws Exception {
        int size = 3;
        WindowOperator<String, Integer, long[], String, GlobalWindow> op = countWindowOperator(size, size);

        TestOutput<String> output = new TestOutput<>();
        op.setOutput((Output) output);
        op.open();
        try {
            for (int i = 1; i <= 9; i++) {
                op.processElement(new StreamRecord<>(i, i));
            }

            // Fires at elements 3, 6, 9. CountEvictor keeps the LAST 3 elements:
            // fire 1 aggregates {1,2,3} = 6; fire 2 {4,5,6} = 15; fire 3 {7,8,9} = 24.
            assertEquals(3, output.size(), "CountTrigger(slide=3) must fire every 3 elements");
            assertEquals("6", output.getElements().get(0), "fire 1 aggregates the first pane");
            assertEquals("15", output.getElements().get(1),
                    "fire 2 must aggregate only the post-eviction survivors {4,5,6}, not 1..6");
            assertEquals("24", output.getElements().get(2),
                    "fire 3 must aggregate only the post-eviction survivors {7,8,9}");
        } finally {
            op.close();
        }
    }

    /**
     * Flink size-parameter semantics: evictBefore and evictAfter both receive the
     * PRE-eviction element count. Pre-fix, evictAfter received the count after
     * evictBefore had already shrunk the list (8 → 5), skewing size-aware evictors.
     */
    @Test
    void testPreEvictionSizePassedToBothEvictorCallbacks() throws Exception {
        List<Integer> beforeSizes = new ArrayList<>();
        List<Integer> afterSizes = new ArrayList<>();
        Evictor<Integer, TimeWindow> recordingEvictor = new Evictor<Integer, TimeWindow>() {
            @Override
            public void evictBefore(Iterable<TimestampedValue<Integer>> elements, int size,
                                    TimeWindow window, EvictorContext ctx) {
                beforeSizes.add(size);
                // keep the last 5 of 8: remove the first 3
                int toRemove = size - 5;
                Iterator<TimestampedValue<Integer>> it = elements.iterator();
                int removed = 0;
                while (it.hasNext() && removed < toRemove) {
                    it.next();
                    it.remove();
                    removed++;
                }
            }

            @Override
            public void evictAfter(Iterable<TimestampedValue<Integer>> elements, int size,
                                   TimeWindow window, EvictorContext ctx) {
                afterSizes.add(size);
            }
        };

        WindowOperator<String, Integer, long[], String, TimeWindow> op =
                new WindowOperatorBuilder<Integer, String, TimeWindow>()
                        .windowAssigner(TumblingEventTimeWindows.of(200L))
                        .trigger(EventTimeTrigger.create())
                        .evictor(recordingEvictor)
                        .keySelector((KeySelector<Integer, String>) v -> "key1")
                        .keyClass(String.class)
                        .keySerializer(new TestWindowOperatorBuilder.SimpleStringSerializer())
                        .windowSerializer(new TestWindowOperatorBuilder.SimpleTimeWindowSerializer())
                        .aggregate(new SumAggregate(), long[].class, Integer.class);

        TestOutput<String> output = new TestOutput<>();
        op.setOutput((Output) output);
        op.open();
        try {
            for (int i = 1; i <= 8; i++) {
                op.processElement(new StreamRecord<>(i, i));
            }
            advanceWatermark(op, 199L);

            assertEquals(1, beforeSizes.size(), "window must fire once");
            assertEquals(8, beforeSizes.get(0), "evictBefore receives the pre-eviction count");
            assertEquals(8, afterSizes.get(0),
                    "evictAfter must also receive the PRE-eviction count (Flink semantics), "
                            + "not the post-evictBefore size (5)");
            // (the tumbling window's cleanup timer fires at the same timestamp as the
            // trigger and legitimately clears the state afterwards — the write-back
            // itself is asserted on the multi-fire GlobalWindows path above)
        } finally {
            op.close();
        }
    }

    // ------------------------------------------------------------------
    // AR-3: real element timestamps on the descriptor path (TimeEvictor)
    // ------------------------------------------------------------------

    /**
     * TimeEvictor on the builder/descriptor path must evict by ELEMENT timestamp
     * (pre-fix: every element was stamped with the current watermark → the cutoff
     * comparison never evicted anything → wrong aggregation results, state never
     * shrank).
     */
    @Test
    void testTimeEvictorEvictsByElementTimestampOnDescriptorPath() throws Exception {
        long keepWindow = 50L;
        WindowOperator<String, Integer, long[], String, TimeWindow> op =
                new WindowOperatorBuilder<Integer, String, TimeWindow>()
                        .windowAssigner(TumblingEventTimeWindows.of(200L))
                        .trigger(EventTimeTrigger.create())
                        .evictor(new io.nop.stream.core.windowing.evictors.TimeEvictor<>(keepWindow))
                        .keySelector((KeySelector<Integer, String>) v -> "key1")
                        .keyClass(String.class)
                        .keySerializer(new TestWindowOperatorBuilder.SimpleStringSerializer())
                        .windowSerializer(new TestWindowOperatorBuilder.SimpleTimeWindowSerializer())
                        .aggregate(new SumAggregate(), long[].class, Integer.class);

        TestOutput<String> output = new TestOutput<>();
        op.setOutput((Output) output);
        op.open();
        try {
            // Window [0,200): elements at t=100, 150, 190. TimeEvictor(50) cutoff =
            // maxTimestamp(190) - 50 = 140 → the element at t=100 (value 1) must be
            // evicted; 2 and 3 survive → sum = 5 (pre-fix: fake watermark stamps
            // evicted nothing → sum = 6).
            op.processElement(new StreamRecord<>(1, 100));
            op.processElement(new StreamRecord<>(2, 150));
            op.processElement(new StreamRecord<>(3, 190));
            advanceWatermark(op, 199L);

            assertEquals(1, output.size());
            assertEquals("5", output.getElements().get(0),
                    "TimeEvictor must evict the element older than maxTs-50 by its ELEMENT "
                            + "timestamp; pre-fix it never evicted (sum would be 6)");
            // (the tumbling window's cleanup timer fires at the same timestamp and
            // legitimately clears the state afterwards; permanent eviction across
            // fires is proven by testEvictedElementsDoNotReturnToAggregation)
        } finally {
            op.close();
        }
    }

    /**
     * The timestamp side store is only registered when an evictor is present —
     * non-evictor jobs keep a byte-identical checkpoint payload.
     */
    @Test
    void testNoTimestampSideStoreWithoutEvictor() throws Exception {
        WindowOperator<String, Integer, long[], String, TimeWindow> op =
                new WindowOperatorBuilder<Integer, String, TimeWindow>()
                        .windowAssigner(TumblingEventTimeWindows.of(200L))
                        .trigger(EventTimeTrigger.create())
                        .keySelector((KeySelector<Integer, String>) v -> "key1")
                        .keyClass(String.class)
                        .keySerializer(new TestWindowOperatorBuilder.SimpleStringSerializer())
                        .windowSerializer(new TestWindowOperatorBuilder.SimpleTimeWindowSerializer())
                        .aggregate(new SumAggregate(), long[].class, Integer.class);
        op.setOutput((Output) new TestOutput<String>());
        op.open();
        try {
            assertTrue(op.elementTimestampsStateForTest() == null,
                    "element-timestamps side store must not be registered for non-evictor jobs");
        } finally {
            op.close();
        }
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private static WindowOperator<String, Integer, long[], String, GlobalWindow> countWindowOperator(
            long size, long slide) {
        return new WindowOperatorBuilder<Integer, String, GlobalWindow>()
                .windowAssigner(GlobalWindows.create())
                .trigger(CountTrigger.of(slide))
                .evictor(CountEvictor.of(size))
                .keySelector((KeySelector<Integer, String>) v -> "key1")
                .keyClass(String.class)
                .keySerializer(new TestWindowOperatorBuilder.SimpleStringSerializer())
                .windowSerializer(new WindowOperatorFactoryImpl.GlobalWindowSerializer())
                .aggregate(new SumAggregate(), long[].class, Integer.class);
    }

    @SuppressWarnings("unchecked")
    private static List<GlobalWindow> readGlobalWindowState(
            WindowOperator<String, Integer, ?, ?, GlobalWindow> op) throws Exception {
        op.getKeyedStateBackend().setCurrentKey("key1");
        op.newListWindowStateForTest().setCurrentNamespace(GlobalWindow.get());
        Iterable<?> elements = op.newListWindowStateForTest().get();
        List<Object> result = new ArrayList<>();
        if (elements != null) {
            for (Object e : elements) {
                result.add(e);
            }
        }
        return (List<GlobalWindow>) (List<?>) result;
    }

    private static int readTimeWindowStateSize(WindowOperator<String, Integer, ?, ?, TimeWindow> op,
                                               TimeWindow window) throws Exception {
        op.getKeyedStateBackend().setCurrentKey("key1");
        op.newListWindowStateForTest().setCurrentNamespace(window);
        Iterable<?> elements = op.newListWindowStateForTest().get();
        List<Object> result = new ArrayList<>();
        if (elements != null) {
            for (Object e : elements) {
                result.add(e);
            }
        }
        return result.size();
    }

    private static void advanceWatermark(WindowOperator<?, ?, ?, ?, ?> op, long ts) throws Exception {
        java.lang.reflect.Field f = WindowOperator.class.getDeclaredField("internalTimerService");
        f.setAccessible(true);
        Object svc = f.get(op);
        if (svc instanceof HeapInternalTimerService) {
            ((HeapInternalTimerService<?, ?>) svc).advanceWatermark(ts);
        }
    }

    /** Narrow read helper over the operator's internal list state. */
    private static final class InternalListStateForRead<K, W> {
        private InternalListStateForRead() {
        }
    }

    static class SumAggregate implements AggregateFunction<Integer, long[], String> {
        private static final long serialVersionUID = 1L;

        @Override
        public long[] createAccumulator() {
            return new long[]{0};
        }

        @Override
        public long[] add(Integer value, long[] accumulator) {
            accumulator[0] += value;
            return accumulator;
        }

        @Override
        public String getResult(long[] accumulator) {
            return String.valueOf(accumulator[0]);
        }

        @Override
        public long[] merge(long[] a, long[] b) {
            a[0] += b[0];
            return a;
        }
    }
}
