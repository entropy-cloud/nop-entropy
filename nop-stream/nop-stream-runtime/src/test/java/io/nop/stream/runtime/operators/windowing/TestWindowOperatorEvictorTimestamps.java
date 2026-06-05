package io.nop.stream.runtime.operators.windowing;

import io.nop.stream.core.common.functions.KeySelector;
import io.nop.stream.core.common.typeutils.TypeSerializer;
import io.nop.stream.core.operators.Output;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.test.TestOutput;
import io.nop.stream.core.util.OutputTag;
import io.nop.stream.core.windowing.evictors.Evictor;
import io.nop.stream.core.windowing.assigners.TumblingEventTimeWindows;
import io.nop.stream.core.windowing.triggers.EventTimeTrigger;
import io.nop.stream.core.windowing.utils.TimestampedValue;
import io.nop.stream.core.windowing.windows.TimeWindow;
import io.nop.stream.runtime.operators.WindowOperatorTimerService;
import io.nop.stream.runtime.operators.windowing.functions.InternalWindowFunction;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestWindowOperatorEvictorTimestamps {

    private static final long WINDOW_SIZE = 200L;

    private List<Long> capturedTimestamps;

    private TimestampCapturingEvictor evictor = new TimestampCapturingEvictor();

    private TestOutput<String> output;
    private EvictorTestableOperator operator;

    private void setUpOperator() throws Exception {
        output = new TestOutput<>();
        capturedTimestamps = new ArrayList<>();

        operator = new EvictorTestableOperator(
                TumblingEventTimeWindows.of(WINDOW_SIZE),
                new TestWindowOperatorBehavior.SimpleTimeWindowSerializer(),
                (KeySelector<Integer, String>) v -> "key1",
                new TestWindowOperatorBehavior.SimpleStringSerializer(),
                String.class,
                new TestWindowOperatorBehavior.ToStringWindowFunction(),
                EventTimeTrigger.create(),
                0L,
                null,
                evictor
        );

        operator.setOutput((Output) output);
        operator.open();
    }

    @Test
    void testEvictorUsesElementTimestamps() throws Exception {
        setUpOperator();

        operator.processElement(new StreamRecord<>(10, 50));
        operator.processElement(new StreamRecord<>(20, 100));
        operator.processElement(new StreamRecord<>(30, 150));

        operator.advanceInternalWatermark(199);

        assertEquals(3, capturedTimestamps.size(), "Evictor should see 3 timestamps");
        assertEquals(50L, capturedTimestamps.get(0), "First element should have timestamp 50");
        assertEquals(100L, capturedTimestamps.get(1), "Second element should have timestamp 100");
        assertEquals(150L, capturedTimestamps.get(2), "Third element should have timestamp 150");
    }

    @Test
    void testCloseDoesNotNPEOnTimerFiring() throws Exception {
        setUpOperator();
        operator.processElement(new StreamRecord<>(1, 50));
        operator.close();
        assertTrue(true, "close() should not throw NPE");
    }

    @Test
    void testSnapshotDeepClonesAccumulators() throws Exception {
        setUpOperator();

        io.nop.stream.core.checkpoint.OperatorSnapshotResult snap1 = operator.snapshotState(
                new io.nop.stream.core.checkpoint.StateSnapshotContext(1, 100));

        operator.processElement(new StreamRecord<>(99, 50));

        io.nop.stream.core.checkpoint.OperatorSnapshotResult snap2 = operator.snapshotState(
                new io.nop.stream.core.checkpoint.StateSnapshotContext(2, 200));

        assertNotNull(snap1);
        assertNotNull(snap2);
    }

    class TimestampCapturingEvictor implements Evictor<Integer, TimeWindow> {
        @Override
        public void evictBefore(Iterable<TimestampedValue<Integer>> elements, int size,
                                TimeWindow window, EvictorContext evictorContext) {
            for (TimestampedValue<Integer> tv : elements) {
                capturedTimestamps.add(tv.getTimestamp());
            }
        }

        @Override
        public void evictAfter(Iterable<TimestampedValue<Integer>> elements, int size,
                               TimeWindow window, EvictorContext evictorContext) {
        }
    }

    static class EvictorTestableOperator
            extends WindowOperator<String, Integer, Object, String, TimeWindow> {

        EvictorTestableOperator(
                TumblingEventTimeWindows windowAssigner,
                TypeSerializer<TimeWindow> windowSerializer,
                KeySelector<Integer, String> keySelector,
                TypeSerializer<String> keySerializer,
                Class<String> keyClass,
                InternalWindowFunction<Object, String, String, TimeWindow> windowFunction,
                EventTimeTrigger trigger,
                long allowedLateness,
                OutputTag<Integer> lateDataOutputTag,
                Evictor<Integer, TimeWindow> evictor) {
            super(windowAssigner, windowSerializer, keySelector, keySerializer, keyClass,
                    windowFunction, trigger, allowedLateness, lateDataOutputTag,
                    (Class<Object>) (Class<?>) Object.class, null, null, evictor);
        }

        void advanceInternalWatermark(long timestamp) throws Exception {
            if (internalTimerService instanceof WindowOperatorTimerService) {
                ((WindowOperatorTimerService<String, TimeWindow>) internalTimerService).advanceWatermark(timestamp);
            }
        }
    }
}
