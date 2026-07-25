package io.nop.stream.runtime.operators.windowing;

import io.nop.stream.core.common.functions.*;
import io.nop.stream.core.common.typeutils.TypeSerializer;
import io.nop.stream.core.operators.Output;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.test.TestOutput;
import io.nop.stream.core.util.Collector;
import io.nop.stream.core.common.state.ListStateDescriptor;
import io.nop.stream.core.windowing.AccumulationMode;
import io.nop.stream.core.windowing.assigners.TumblingEventTimeWindows;
import io.nop.stream.core.windowing.evictors.CountEvictor;
import io.nop.stream.core.windowing.evictors.Evictor;
import io.nop.stream.core.windowing.triggers.EventTimeTrigger;
import io.nop.stream.core.windowing.triggers.Trigger;
import io.nop.stream.core.windowing.triggers.TriggerResult;
import io.nop.stream.core.windowing.utils.TimestampedValue;
import io.nop.stream.core.windowing.windows.TimeWindow;
import io.nop.stream.runtime.operators.windowing.functions.InternalIterableProcessWindowFunction;
import io.nop.stream.runtime.operators.windowing.functions.InternalWindowFunction;
import io.nop.stream.core.operators.HeapInternalTimerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.*;

public class TestEvictorIntegration {

    private TestOutput<String> output;

    @BeforeEach
    void setUp() {
        output = new TestOutput<>();
    }

    @Test
    @SuppressWarnings("unchecked")
    void testCountEvictorKeepsOnlyLastNElements() throws Exception {
        InternalIterableProcessWindowFunction<Integer, String, String, TimeWindow> windowFn =
                new InternalIterableProcessWindowFunction<>(new TestWindowOperatorBuilder.ConcatProcessWindowFunction());

        TestableWindowOperator operator = new TestableWindowOperator(
                        TumblingEventTimeWindows.of(100),
                        new TestWindowOperatorBuilder.SimpleTimeWindowSerializer(),
                        (KeySelector<Integer, String>) v -> "key1",
                        new TestWindowOperatorBuilder.SimpleStringSerializer(),
                        String.class,
                        windowFn,
                        EventTimeTrigger.create(),
                        0L,
                        null,
                        (Class) Object.class,
                        new ListStateDescriptor<>("window-contents", Integer.class),
                        null,
                        CountEvictor.of(2),
                        null);

        operator.setOutput((Output) output);
        operator.open();

        operator.processElement(new StreamRecord<>(1, 10));
        operator.processElement(new StreamRecord<>(2, 20));
        operator.processElement(new StreamRecord<>(3, 30));

        assertTrue(output.isEmpty());

        ((TestableWindowOperator) operator).advanceInternalWatermark(99);

        assertEquals(1, output.size(), "Should have one window output");
        String result = output.getElements().get(0);

        assertTrue(result.contains("2"), "CountEvictor.of(2) should keep element 2");
        assertTrue(result.contains("3"), "CountEvictor.of(2) should keep element 3");
        assertFalse(result.contains("1"), "CountEvictor.of(2) should evict element 1");
    }

    @Test
    @SuppressWarnings("unchecked")
    void testNoEvictorKeepsAllElements() throws Exception {
        InternalIterableProcessWindowFunction<Integer, String, String, TimeWindow> windowFn =
                new InternalIterableProcessWindowFunction<>(new TestWindowOperatorBuilder.ConcatProcessWindowFunction());

        TestableWindowOperator operator = new TestableWindowOperator(
                        TumblingEventTimeWindows.of(100),
                        new TestWindowOperatorBuilder.SimpleTimeWindowSerializer(),
                        (KeySelector<Integer, String>) v -> "key1",
                        new TestWindowOperatorBuilder.SimpleStringSerializer(),
                        String.class,
                        windowFn,
                        EventTimeTrigger.create(),
                        0L,
                        null,
                        (Class) Object.class,
                        new ListStateDescriptor<>("window-contents", Integer.class),
                        null,
                        null,
                        null);

        operator.setOutput((Output) output);
        operator.open();

        operator.processElement(new StreamRecord<>(1, 10));
        operator.processElement(new StreamRecord<>(2, 20));
        operator.processElement(new StreamRecord<>(3, 30));

        ((TestableWindowOperator) operator).advanceInternalWatermark(99);

        assertEquals(1, output.size());
        String result = output.getElements().get(0);
        assertTrue(result.contains("1"));
        assertTrue(result.contains("2"));
        assertTrue(result.contains("3"));
    }

    /**
     * G46: proves eviction is transient-per-fire on the production InternalListState path.
     *
     * <p>ACCUMULATING mode + an evictor that removes the first element each fire. Because
     * eviction acts on a local copy and is NOT written back to state, every fire sees the
     * full accumulated set (counts [1, 2, 3, 4]). If eviction were persisted (a regression),
     * the first element would be permanently removed and subsequent fires would see a
     * smaller/shrinking set. This matches Flink's {@code EvictingWindowOperator}
     * transient-per-fire semantics.
     */
    @Test
    @SuppressWarnings("unchecked")
    void testEvictionIsTransientPerFireOnProductionListStatePath() throws Exception {
        FirstElementEvictor evictor = new FirstElementEvictor();
        InternalIterableProcessWindowFunction<Integer, String, String, TimeWindow> windowFn =
                new InternalIterableProcessWindowFunction<>(new TestWindowOperatorBuilder.ConcatProcessWindowFunction());

        // ListStateDescriptor => production InternalListState path (NOT the MapState test path)
        TestableWindowOperator operator = new TestableWindowOperator(
                        TumblingEventTimeWindows.of(100),
                        new TestWindowOperatorBuilder.SimpleTimeWindowSerializer(),
                        (KeySelector<Integer, String>) v -> "key1",
                        new TestWindowOperatorBuilder.SimpleStringSerializer(),
                        String.class,
                        windowFn,
                        new AlwaysFiringTrigger(),
                        0L,
                        null,
                        (Class) Object.class,
                        new ListStateDescriptor<>("window-contents", Integer.class),
                        null,
                        evictor,
                        AccumulationMode.ACCUMULATING);

        operator.setOutput((Output) output);
        operator.open();

        operator.processElement(new StreamRecord<>(1, 10));
        operator.processElement(new StreamRecord<>(2, 20));
        operator.processElement(new StreamRecord<>(3, 30));
        operator.processElement(new StreamRecord<>(4, 40));

        // Each fire saw the FULL accumulated set — eviction did not persist.
        assertEquals(java.util.Arrays.asList(1, 2, 3, 4), evictor.sizesSeen,
                "Each firing must see the full accumulated element set (transient-per-fire, "
                        + "matching Flink). If eviction were persisted, counts would be smaller.");
    }

    /**
     * Trigger that fires on every element, so we can observe multiple firings within one window.
     */
    static class AlwaysFiringTrigger extends Trigger<Object, TimeWindow> {
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
            return TriggerResult.CONTINUE;
        }

        @Override
        public void clear(TimeWindow window, TriggerContext ctx) {
        }
    }

    /**
     * Evictor that removes the first element of the local copy and records the count seen
     * on each fire. The removal exercises the eviction path; recording the count lets us
     * assert whether eviction persisted to state (it must NOT).
     */
    static class FirstElementEvictor implements Evictor<Object, TimeWindow> {
        final List<Integer> sizesSeen = new ArrayList<>();

        @Override
        public void evictBefore(Iterable<TimestampedValue<Object>> elements, int size,
                                TimeWindow window, EvictorContext ctx) {
            java.util.Iterator<TimestampedValue<Object>> it = elements.iterator();
            int count = 0;
            while (it.hasNext()) {
                it.next();
                count++;
            }
            sizesSeen.add(count);
            // Remove the first element from the local copy (exercises eviction write path).
            it = elements.iterator();
            if (it.hasNext()) {
                it.next();
                it.remove();
            }
        }

        @Override
        public void evictAfter(Iterable<TimestampedValue<Object>> elements, int size,
                               TimeWindow window, EvictorContext ctx) {
        }
    }

    static class TestableWindowOperator extends WindowOperator {
        @SuppressWarnings("rawtypes")
        TestableWindowOperator(
                io.nop.stream.core.windowing.assigners.WindowAssigner windowAssigner,
                TypeSerializer windowSerializer,
                KeySelector keySelector,
                TypeSerializer keySerializer,
                Class keyClass,
                InternalWindowFunction windowFunction,
                io.nop.stream.core.windowing.triggers.Trigger trigger,
                long allowedLateness,
                io.nop.stream.core.util.OutputTag lateDataOutputTag,
                Class accClass,
                io.nop.stream.core.common.state.StateDescriptor windowStateDescriptor,
                BiFunction mergeFunction,
                io.nop.stream.core.windowing.evictors.Evictor evictor,
                io.nop.stream.core.windowing.AccumulationMode accumulationMode) {
            super(windowAssigner, windowSerializer, keySelector, keySerializer, keyClass,
                    windowFunction, trigger, allowedLateness, lateDataOutputTag,
                    accClass, windowStateDescriptor, mergeFunction, evictor, accumulationMode);
        }

        void advanceInternalWatermark(long timestamp) throws Exception {
            if (internalTimerService instanceof HeapInternalTimerService) {
                ((HeapInternalTimerService<?, ?>) internalTimerService).advanceWatermark(timestamp);
            }
        }
    }
}
