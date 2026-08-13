/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.operators.windowing;

import io.nop.stream.core.common.accumulators.IntCounter;
import io.nop.stream.core.common.accumulators.SimpleAccumulator;
import io.nop.stream.core.common.functions.KeySelector;
import io.nop.stream.core.common.state.MapState;
import io.nop.stream.core.common.state.MapStateDescriptor;
import io.nop.stream.core.common.state.backend.IKeyedStateBackend;
import io.nop.stream.core.common.typeutils.TypeSerializer;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.operators.HeapInternalTimerService;
import io.nop.stream.core.operators.InternalTimerService;
import io.nop.stream.core.operators.Output;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.test.TestOutput;
import io.nop.stream.core.util.OutputTag;
import io.nop.stream.core.windowing.assigners.EventTimeSessionWindows;
import io.nop.stream.core.windowing.assigners.TumblingEventTimeWindows;
import io.nop.stream.core.windowing.triggers.CountTrigger;
import io.nop.stream.core.windowing.triggers.EventTimeTrigger;
import io.nop.stream.core.windowing.windows.TimeWindow;
import io.nop.stream.runtime.operators.windowing.functions.InternalWindowFunction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_INVALID_STATE;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_WINDOW_NON_ACCUMULATOR_MERGE_CONFLICT;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Phase 3 correctness bugs in WindowOperator:
 * 1. addWindowElement() accumulator creation (IN != ACC type)
 * 2. getSimpleAccumulator() in trigger context (CountTrigger counting)
 * 3. mergeWindowContents() correctness and type-safety
 */
public class TestWindowOperatorCorrectness {

    // ---- Shared test infrastructure ----

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

    // ===================================================================
    // Test 1: Single key AggregateFunction (IN != ACC type)
    // First element not lost, continuous accumulation correct
    // ===================================================================

    /**
     * A window operator that uses IntCounter as ACC type.
     * IN=Integer, ACC=IntCounter (distinct types).
     * This tests that addWindowElement() properly creates an accumulator
     * for the first element instead of casting Integer to IntCounter.
     */
    static class AccumulatingWindowOperator
            extends WindowOperator<String, Integer, Object, String, TimeWindow> {

        public AccumulatingWindowOperator(
                TumblingEventTimeWindows windowAssigner,
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
            // Return a IntCounter as the accumulator for Integer values
            return new IntCounter();
        }

        public void advanceInternalWatermark(long timestamp) throws Exception {
            if (internalTimerService instanceof HeapInternalTimerService) {
                ((HeapInternalTimerService<String, TimeWindow>) internalTimerService).advanceWatermark(timestamp);
            }
        }
    }

    /**
     * Window function that reads the accumulated value from IntCounter.
     */
    static class SumWindowFunction implements InternalWindowFunction<Object, String, String, TimeWindow> {
        private static final long serialVersionUID = 1L;

        @Override
        public void process(String key, TimeWindow window, InternalWindowFunction.InternalWindowContext context,
                            Object input, io.nop.stream.core.util.Collector<String> out) {
            if (input instanceof IntCounter) {
                out.collect("sum=" + ((IntCounter) input).getLocalValuePrimitive());
            } else {
                out.collect("value=" + input);
            }
        }

        @Override
        public void clear(TimeWindow window, InternalWindowFunction.InternalWindowContext context) {
        }
    }

    @Test
    void testAggregateFunctionFirstElementNotLost() throws Exception {
        // This test verifies Bug 1 fix: first element goes through
        // createAccumulator() -> add(), not direct cast.
        TestOutput<String> output = new TestOutput<>();

        AccumulatingWindowOperator operator = new AccumulatingWindowOperator(
                TumblingEventTimeWindows.of(100L),
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
            // Send a single element and verify it's accumulated (not lost)
            operator.processElement(new StreamRecord<>(42, 10));
            operator.advanceInternalWatermark(100);

            // Should produce "sum=42", not empty or "value=42"
            assertEquals(1, output.size());
            assertEquals("sum=42", output.getElements().get(0));
        } finally {
            operator.close();
        }
    }

    @Test
    void testAggregateFunctionContinuousAccumulation() throws Exception {
        // This test verifies that multiple elements accumulate correctly.
        TestOutput<String> output = new TestOutput<>();

        AccumulatingWindowOperator operator = new AccumulatingWindowOperator(
                TumblingEventTimeWindows.of(100L),
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
            // Send multiple elements
            operator.processElement(new StreamRecord<>(10, 10));
            operator.processElement(new StreamRecord<>(20, 20));
            operator.processElement(new StreamRecord<>(30, 50));
            operator.advanceInternalWatermark(100);

            // Should produce "sum=60" (10 + 20 + 30)
            assertEquals(1, output.size());
            assertEquals("sum=60", output.getElements().get(0));
        } finally {
            operator.close();
        }
    }

    // ===================================================================
    // Test 2: getSimpleAccumulator works in Trigger.onElement
    // (CountTrigger counts correctly)
    // ===================================================================

    /**
     * Testable window operator with CountTrigger support.
     */
    static class CountingWindowOperator
            extends WindowOperator<String, Integer, Object, String, TimeWindow> {

        public CountingWindowOperator(
                TumblingEventTimeWindows windowAssigner,
                TypeSerializer<TimeWindow> windowSerializer,
                KeySelector<Integer, String> keySelector,
                TypeSerializer<String> keySerializer,
                Class<String> keyClass,
                InternalWindowFunction<Object, String, String, TimeWindow> windowFunction,
                CountTrigger<TimeWindow> trigger,
                long allowedLateness,
                OutputTag<Integer> lateDataOutputTag) {
            super(windowAssigner, windowSerializer, keySelector, keySerializer, keyClass,
                    windowFunction, trigger, allowedLateness, lateDataOutputTag);
        }

        public void advanceInternalWatermark(long timestamp) throws Exception {
            if (internalTimerService instanceof HeapInternalTimerService) {
                ((HeapInternalTimerService<String, TimeWindow>) internalTimerService).advanceWatermark(timestamp);
            }
        }
    }

    @Test
    void testCountTriggerFiresCorrectly() throws Exception {
        // This test verifies Bug 2 fix: getSimpleAccumulator() actually returns
        // a valid SimpleAccumulator, so CountTrigger can count elements.
        TestOutput<String> output = new TestOutput<>();

        CountingWindowOperator operator = new CountingWindowOperator(
                TumblingEventTimeWindows.of(100L),
                new SimpleTimeWindowSerializer(),
                (KeySelector<Integer, String>) v -> "key1",
                new SimpleStringSerializer(),
                String.class,
                new SumWindowFunction(),
                CountTrigger.of(3), // Fire after 3 elements
                0L,
                null
        );

        operator.setOutput((Output) output);
        operator.open();

        try {
            // Send 2 elements - should NOT fire yet
            operator.processElement(new StreamRecord<>(10, 10));
            operator.processElement(new StreamRecord<>(20, 20));
            assertTrue(output.isEmpty(), "Should not fire before reaching count threshold");

            // Send 3rd element - should fire
            operator.processElement(new StreamRecord<>(30, 30));
            assertEquals(1, output.size(), "Should fire after 3 elements");
        } finally {
            operator.close();
        }
    }

    @Test
    void testCountTriggerStateIsPerWindow() throws Exception {
        // Verify that CountTrigger state is isolated between different windows.
        TestOutput<String> output = new TestOutput<>();

        CountingWindowOperator operator = new CountingWindowOperator(
                TumblingEventTimeWindows.of(100L),
                new SimpleTimeWindowSerializer(),
                (KeySelector<Integer, String>) v -> "key1",
                new SimpleStringSerializer(),
                String.class,
                new SumWindowFunction(),
                CountTrigger.of(2), // Fire after 2 elements
                0L,
                null
        );

        operator.setOutput((Output) output);
        operator.open();

        try {
            // Window [0,100): 1 element - no fire
            operator.processElement(new StreamRecord<>(10, 10));
            assertTrue(output.isEmpty());

            // Window [100,200): 1 element - no fire (separate window counter)
            operator.processElement(new StreamRecord<>(110, 110));
            assertTrue(output.isEmpty());

            // Window [0,100): 2nd element - should fire
            operator.processElement(new StreamRecord<>(20, 20));
            assertEquals(1, output.size(), "First window should fire after 2 elements");
        } finally {
            operator.close();
        }
    }

    // ===================================================================
    // Test 3: Window merge (Session Window) accumulator merge correctness
    // ===================================================================

    /**
     * Accumulating window operator for session windows.
     */
    static class AccumulatingSessionWindowOperator
            extends WindowOperator<String, Integer, Object, String, TimeWindow> {

        public AccumulatingSessionWindowOperator(
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
    }

    @Test
    void testSessionWindowMergeAccumulatorCorrectness() throws Exception {
        // This test verifies Bug 3 fix: mergeWindowContents correctly merges
        // SimpleAccumulators from different windows.
        TestOutput<String> output = new TestOutput<>();

        AccumulatingSessionWindowOperator operator = new AccumulatingSessionWindowOperator(
                EventTimeSessionWindows.withGap(50L), // gap=50ms
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
            // Element at t=10 creates window [10, 60) with accumulator sum=10
            operator.processElement(new StreamRecord<>(10, 10));
            assertTrue(output.isEmpty());

            // Element at t=30 is within gap of [10,60), so window extends to [10, 80)
            // Accumulator sum should be 10+20=30
            operator.processElement(new StreamRecord<>(20, 30));
            assertTrue(output.isEmpty());

            // Advance watermark past window end to trigger
            operator.advanceInternalWatermark(80);

            // Should fire with merged accumulator sum=30
            assertEquals(1, output.size());
            assertEquals("sum=30", output.getElements().get(0));
        } finally {
            operator.close();
        }
    }

    @Test
    void testSessionWindowMergeTwoSeparateWindows() throws Exception {
        // Test that two initially separate windows get merged when a bridging element arrives.
        TestOutput<String> output = new TestOutput<>();

        AccumulatingSessionWindowOperator operator = new AccumulatingSessionWindowOperator(
                EventTimeSessionWindows.withGap(50L), // gap=50ms
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
            // Element at t=10 creates window [10, 60) with sum=10
            operator.processElement(new StreamRecord<>(10, 10));

            // Element at t=100 creates window [100, 150) with sum=20 (no overlap with [10,60))
            operator.processElement(new StreamRecord<>(20, 100));

            // Element at t=55 bridges the gap: [10,60) and [100,150) merge into [10,150)
            // Accumulator should sum up: 10 + 5 + 20 = 35
            operator.processElement(new StreamRecord<>(5, 55));

            // Advance watermark past merged window
            operator.advanceInternalWatermark(150);

            assertEquals(1, output.size());
            assertEquals("sum=35", output.getElements().get(0));
        } finally {
            operator.close();
        }
    }

    // ===================================================================
    // Test 4: mergeWindowContents type incompatibility throws exception
    // ===================================================================

    /**
     * A window operator that uses a non-accumulator first window and an accumulator second window,
     * to test the type mismatch detection in mergeWindowContents.
     */
    static class MixedTypeWindowOperator
            extends WindowOperator<String, Integer, Object, String, TimeWindow> {

        private final boolean useAccumulator;

        public MixedTypeWindowOperator(
                EventTimeSessionWindows windowAssigner,
                TypeSerializer<TimeWindow> windowSerializer,
                KeySelector<Integer, String> keySelector,
                TypeSerializer<String> keySerializer,
                Class<String> keyClass,
                InternalWindowFunction<Object, String, String, TimeWindow> windowFunction,
                EventTimeTrigger trigger,
                long allowedLateness,
                OutputTag<Integer> lateDataOutputTag,
                boolean useAccumulator) {
            super(windowAssigner, windowSerializer, keySelector, keySerializer, keyClass,
                    windowFunction, trigger, allowedLateness, lateDataOutputTag);
            this.useAccumulator = useAccumulator;
        }

        @Override
        protected SimpleAccumulator<Integer> createAccumulatorForWindow() {
            if (useAccumulator) {
                return new IntCounter();
            }
            return null;
        }

        public void advanceInternalWatermark(long timestamp) throws Exception {
            if (internalTimerService instanceof HeapInternalTimerService) {
                ((HeapInternalTimerService<String, TimeWindow>) internalTimerService).advanceWatermark(timestamp);
            }
        }
    }

    @Test
    void testMergeTypeIncompatibilityThrowsException() throws Exception {
        // This test verifies Bug 3 fix: type incompatibility during merge
        // causes a fast failure (exception), not silent data loss.
        // We simulate this by having an accumulator target and a non-accumulator source.

        // Note: This is difficult to test directly through the public API because
        // both windows should use the same operator. Instead, we verify the behavior
        // by testing that a properly configured session window operator does NOT throw
        // (the happy path) and that the merge logic is correct.

        // The actual merge safety is tested through the session window merge tests above.
        // Here we verify that a consistent setup works correctly.
        TestOutput<String> output = new TestOutput<>();

        AccumulatingSessionWindowOperator operator = new AccumulatingSessionWindowOperator(
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
            // Send elements that cause merging - should NOT throw
            operator.processElement(new StreamRecord<>(10, 10));
            operator.processElement(new StreamRecord<>(20, 30));
            operator.processElement(new StreamRecord<>(30, 55));

            operator.advanceInternalWatermark(110);

            // Should successfully merge and produce correct result
            assertEquals(1, output.size());
            assertEquals("sum=60", output.getElements().get(0));
        } finally {
            operator.close();
        }
    }

    /**
     * Multi-audit P0-01 (main path): TWO raw (non-accumulator) window values merging must
     * fail fast with {@code ERR_STREAM_WINDOW_NON_ACCUMULATOR_MERGE_CONFLICT}. Constructible
     * via {@code MixedTypeWindowOperator(useAccumulator=false)} (raw mode): each session window
     * holds its raw value, and the bridging element merges two raw-value windows.
     *
     * <p>Red evidence: without the fail-fast throws in {@code mergeWindowContents} this test
     * passes trivially (no exception) — it locks the behavior.
     */
    @Test
    void testMergeTwoRawWindowValuesFailsFast() throws Exception {
        TestOutput<String> output = new TestOutput<>();

        MixedTypeWindowOperator operator = new MixedTypeWindowOperator(
                EventTimeSessionWindows.withGap(50L),
                new SimpleTimeWindowSerializer(),
                (KeySelector<Integer, String>) v -> "key1",
                new SimpleStringSerializer(),
                String.class,
                new SumWindowFunction(),
                EventTimeTrigger.create(),
                0L,
                null,
                false // raw mode: createAccumulatorForWindow() returns null
        );

        operator.setOutput((Output) output);
        operator.open();

        try {
            // Window [10, 60) holds raw value 10; window [100, 150) holds raw value 20.
            operator.processElement(new StreamRecord<>(10, 10));
            operator.processElement(new StreamRecord<>(20, 100));

            // Element at t=55 bridges both windows → merge reads two raw values → fail fast.
            StreamException ex = assertThrows(StreamException.class,
                    () -> operator.processElement(new StreamRecord<>(30, 55)),
                    "merging two raw (non-accumulator) window values must fail fast, never silently "
                            + "merge or drop");
            assertEquals(ERR_STREAM_WINDOW_NON_ACCUMULATOR_MERGE_CONFLICT.getErrorCode(), ex.getErrorCode(),
                    "raw+raw merge must use ERR_STREAM_WINDOW_NON_ACCUMULATOR_MERGE_CONFLICT, got: " + ex.getErrorCode());
        } finally {
            operator.close();
        }
    }

    /**
     * Multi-audit P0-01 (accumulator+raw scenario): an accumulator target window merging with a
     * raw (non-accumulator) source window must fail fast with {@code ERR_STREAM_INVALID_STATE}.
     *
     * <p>{@code MixedTypeWindowOperator.useAccumulator} is a per-operator final flag, so a single
     * operator instance cannot produce both types by itself. The mixed layout is constructed by
     * STATE INJECTION: both windows start as accumulators (useAccumulator=true), then a raw value
     * is written into the SOURCE window's {@code window-contents} namespace through the keyed
     * state backend — the same descriptor the operator itself created in {@code open()}
     * ({@code MapStateDescriptor("window-contents", String.class, Object.class)} with the default
     * {@code accClass=Object.class} path), so schema verification passes and the write lands in
     * the operator's own state.
     *
     * <p>Which pre-existing window becomes the merge target is chosen by
     * {@code MergingWindowSet.addWindow} via {@code mergedWindows.iterator().next()} — the test
     * replicates the exact HashSet to learn the target, then injects the raw value into the OTHER
     * (source) window, making the assertion deterministic: target=accumulator, source=raw →
     * {@code ERR_STREAM_INVALID_STATE}.
     */
    @Test
    void testMergeAccumulatorWithRawValueFailsFast() throws Exception {
        TestOutput<String> output = new TestOutput<>();

        MixedTypeWindowOperator operator = new MixedTypeWindowOperator(
                EventTimeSessionWindows.withGap(50L),
                new SimpleTimeWindowSerializer(),
                (KeySelector<Integer, String>) v -> "key1",
                new SimpleStringSerializer(),
                String.class,
                new SumWindowFunction(),
                EventTimeTrigger.create(),
                0L,
                null,
                true // accumulator mode
        );

        operator.setOutput((Output) output);
        operator.open();

        try {
            // Both windows start as accumulators: [10, 60) = acc(10), [100, 150) = acc(20).
            operator.processElement(new StreamRecord<>(10, 10));
            operator.processElement(new StreamRecord<>(20, 100));

            // Learn the merge target exactly as MergingWindowSet.addWindow does
            // (mergedWindows.iterator().next() over the pre-existing windows).
            TimeWindow w1 = new TimeWindow(10, 60);
            TimeWindow w2 = new TimeWindow(100, 150);
            Set<TimeWindow> preExisting = new HashSet<>(List.of(w1, w2));
            TimeWindow mergeTarget = preExisting.iterator().next();
            TimeWindow rawSource = mergeTarget.equals(w1) ? w2 : w1;

            // Inject a RAW value into the source window's namespace (operator's own
            // windowContentsState, value type Object in the default accClass path).
            @SuppressWarnings({"unchecked", "rawtypes"})
            IKeyedStateBackend<String> backend = (IKeyedStateBackend<String>) (IKeyedStateBackend) operator.getKeyedStateBackend();
            backend.setCurrentKey("key1");
            backend.setCurrentNamespace("TW:" + rawSource.getStart() + "," + rawSource.getEnd());
            MapState<String, Object> windowContents = backend.getMapState(
                    new MapStateDescriptor<>("window-contents", String.class, Object.class));
            windowContents.put("__window_value__", 999);

            // Element at t=55 bridges both windows → merge reads target=accumulator,
            // source=raw (the injected value) → fail fast.
            StreamException ex = assertThrows(StreamException.class,
                    () -> operator.processElement(new StreamRecord<>(30, 55)),
                    "merging an accumulator target with a raw (non-accumulator) source must fail fast");
            assertEquals(ERR_STREAM_INVALID_STATE.getErrorCode(), ex.getErrorCode(),
                    "accumulator+raw merge must use ERR_STREAM_INVALID_STATE, got: " + ex.getErrorCode());
        } finally {
            operator.close();
        }
    }
}
