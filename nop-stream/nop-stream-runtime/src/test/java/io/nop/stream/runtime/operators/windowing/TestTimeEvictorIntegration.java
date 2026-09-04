package io.nop.stream.runtime.operators.windowing;

import io.nop.stream.core.common.functions.KeySelector;
import io.nop.stream.core.common.state.ListStateDescriptor;
import io.nop.stream.core.common.typeutils.TypeSerializer;
import io.nop.stream.core.operators.HeapInternalTimerService;
import io.nop.stream.core.operators.Output;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.test.TestOutput;
import io.nop.stream.core.windowing.assigners.TumblingEventTimeWindows;
import io.nop.stream.core.windowing.evictors.TimeEvictor;
import io.nop.stream.core.windowing.triggers.EventTimeTrigger;
import io.nop.stream.core.windowing.windows.TimeWindow;
import io.nop.stream.runtime.operators.windowing.functions.InternalIterableProcessWindowFunction;
import io.nop.stream.runtime.operators.windowing.functions.InternalWindowFunction;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestTimeEvictorIntegration {

    private TestOutput<String> output;

    @BeforeEach
    void setUp() {
        output = new TestOutput<>();
    }

    @Test
    @SuppressWarnings("unchecked")
    void testTimeEvictorUsesRealTimestamps() throws Exception {
        InternalIterableProcessWindowFunction<Integer, String, String, TimeWindow> windowFn =
                new InternalIterableProcessWindowFunction<>(
                        new TestWindowOperatorBuilder.ConcatProcessWindowFunction());

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
                TimeEvictor.of(Duration.ofMillis(15)),
                null);

        operator.setOutput((Output) output);
        operator.open();

        operator.processElement(new StreamRecord<>(1, 10));
        operator.processElement(new StreamRecord<>(2, 20));
        operator.processElement(new StreamRecord<>(3, 35));
        operator.processElement(new StreamRecord<>(4, 40));

        ((TestableWindowOperator) operator).advanceInternalWatermark(99);

        assertEquals(1, output.size(), "Should have one window output");
        String result = output.getElements().get(0);
        assertNotNull(result);
        // AR-3 (plan 1326-2 Phase 1): the descriptor path now persists REAL element
        // timestamps, so TimeEvictor(15ms) evicts by element time: cutoff =
        // maxTimestamp(40) - 15 = 25 → elements at t=10 and t=20 are evicted. The
        // previous pin ("all elements in output") captured the pre-AR-3 defect where
        // every element was stamped with the current watermark and nothing was evicted.
        assertFalse(result.contains("1"), "Element 1 (t=10 <= cutoff 25) must be evicted by TimeEvictor");
        assertFalse(result.contains("2"), "Element 2 (t=20 <= cutoff 25) must be evicted by TimeEvictor");
        assertTrue(result.contains("3"), "Element 3 (t=35 > cutoff 25) must be kept");
        assertTrue(result.contains("4"), "Element 4 (t=40 > cutoff 25) must be kept");
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
