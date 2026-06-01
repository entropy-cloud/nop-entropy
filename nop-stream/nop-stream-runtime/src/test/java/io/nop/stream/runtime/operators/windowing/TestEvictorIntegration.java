package io.nop.stream.runtime.operators.windowing;

import io.nop.stream.core.common.functions.*;
import io.nop.stream.core.common.typeutils.TypeSerializer;
import io.nop.stream.core.operators.Output;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.test.TestOutput;
import io.nop.stream.core.util.Collector;
import io.nop.stream.core.common.state.ListStateDescriptor;
import io.nop.stream.core.windowing.assigners.TumblingEventTimeWindows;
import io.nop.stream.core.windowing.evictors.CountEvictor;
import io.nop.stream.core.windowing.triggers.EventTimeTrigger;
import io.nop.stream.core.windowing.windows.TimeWindow;
import io.nop.stream.runtime.operators.windowing.functions.InternalIterableProcessWindowFunction;
import io.nop.stream.runtime.operators.windowing.functions.InternalWindowFunction;
import io.nop.stream.runtime.operators.WindowOperatorTimerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
                        CountEvictor.of(2));

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
                io.nop.stream.core.windowing.evictors.Evictor evictor) {
            super(windowAssigner, windowSerializer, keySelector, keySerializer, keyClass,
                    windowFunction, trigger, allowedLateness, lateDataOutputTag,
                    accClass, windowStateDescriptor, mergeFunction, evictor);
        }

        void advanceInternalWatermark(long timestamp) throws Exception {
            if (internalTimerService instanceof WindowOperatorTimerService) {
                ((WindowOperatorTimerService) internalTimerService).advanceWatermark(timestamp);
            }
        }
    }
}
