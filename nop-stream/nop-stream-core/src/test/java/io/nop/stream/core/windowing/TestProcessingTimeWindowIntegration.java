package io.nop.stream.core.windowing;

import io.nop.stream.core.common.functions.AggregateFunction;
import io.nop.stream.core.common.functions.KeySelector;
import io.nop.stream.core.common.functions.SinkFunction;
import io.nop.stream.core.common.typeinfo.TypeInformation;
import io.nop.stream.core.common.typeinfo.UnknownTypeInformation;
import io.nop.stream.core.environment.StreamExecutionEnvironment;
import io.nop.stream.core.common.eventtime.WatermarkStrategy;
import io.nop.stream.core.operators.AggregateAggregationFunction;
import io.nop.stream.core.operators.TimestampsAndWatermarksOperator;
import io.nop.stream.core.operators.WindowAggregationOperator;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.streamrecord.watermark.Watermark;
import io.nop.stream.core.windowing.assigners.SlidingProcessingTimeWindows;
import io.nop.stream.core.windowing.assigners.TumblingProcessingTimeWindows;
import io.nop.stream.core.windowing.triggers.ProcessingTimeTrigger;
import io.nop.stream.core.windowing.windows.TimeWindow;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import io.nop.stream.core.exceptions.StreamException;

public class TestProcessingTimeWindowIntegration {

    static final class Event {
        final String key;
        final int value;
        final long timestamp;

        Event(String key, int value, long timestamp) {
            this.key = key;
            this.value = value;
            this.timestamp = timestamp;
        }

        @Override
        public String toString() {
            return "Event{key='" + key + "', value=" + value + ", ts=" + timestamp + '}';
        }
    }

    private static final class SumAggregateFunction implements AggregateFunction<Event, int[], Integer> {
        private static final long serialVersionUID = 1L;

        @Override
        public int[] createAccumulator() {
            return new int[]{0};
        }

        @Override
        public int[] add(Event value, int[] accumulator) {
            accumulator[0] += value.value;
            return accumulator;
        }

        @Override
        public Integer getResult(int[] accumulator) {
            return accumulator[0];
        }

        @Override
        public int[] merge(int[] a, int[] b) {
            a[0] += b[0];
            return a;
        }
    }

    @Test
    void testTumblingProcessingTimeWindowAssigner() {
        AtomicLong processingTime = new AtomicLong(1000);
        TumblingProcessingTimeWindows assigner = TumblingProcessingTimeWindows.of(100);

        io.nop.stream.core.windowing.assigners.WindowAssigner.WindowAssignerContext ctx =
                new io.nop.stream.core.windowing.assigners.WindowAssigner.WindowAssignerContext() {
                    @Override
                    public long getCurrentProcessingTime() {
                        return processingTime.get();
                    }
                };

        Collection<TimeWindow> windows1 = assigner.assignWindows(new Object(), 0, ctx);
        assertEquals(1, windows1.size());
        TimeWindow w1 = windows1.iterator().next();
        assertEquals(1000, w1.getStart());
        assertEquals(1100, w1.getEnd());

        processingTime.set(1050);
        Collection<TimeWindow> windows2 = assigner.assignWindows(new Object(), 0, ctx);
        assertEquals(1, windows2.size());
        TimeWindow w2 = windows2.iterator().next();
        assertEquals(1000, w2.getStart());
        assertEquals(1100, w2.getEnd());

        processingTime.set(1100);
        Collection<TimeWindow> windows3 = assigner.assignWindows(new Object(), 0, ctx);
        assertEquals(1, windows3.size());
        TimeWindow w3 = windows3.iterator().next();
        assertEquals(1100, w3.getStart());
        assertEquals(1200, w3.getEnd());
    }

    @Test
    void testTumblingProcessingTimeWindowSize() {
        TumblingProcessingTimeWindows assigner = TumblingProcessingTimeWindows.of(500);
        assertNotNull(assigner);
        assertFalse(assigner.isEventTime());
    }

    @Test
    void testTumblingProcessingTimeDefaultTrigger() {
        TumblingProcessingTimeWindows assigner = TumblingProcessingTimeWindows.of(100);
        assertInstanceOf(ProcessingTimeTrigger.class, assigner.getDefaultTrigger(null));
    }

    @Test
    void testTumblingProcessingTimeInvalidSizeThrows() {
        assertThrows(StreamException.class, () -> TumblingProcessingTimeWindows.of(0));
        assertThrows(StreamException.class, () -> TumblingProcessingTimeWindows.of(-100));
    }

    @Test
    void testSlidingProcessingTimeWindowAssigner() {
        AtomicLong processingTime = new AtomicLong(1000);
        SlidingProcessingTimeWindows assigner = SlidingProcessingTimeWindows.of(200, 100);

        io.nop.stream.core.windowing.assigners.WindowAssigner.WindowAssignerContext ctx =
                new io.nop.stream.core.windowing.assigners.WindowAssigner.WindowAssignerContext() {
                    @Override
                    public long getCurrentProcessingTime() {
                        return processingTime.get();
                    }
                };

        Collection<TimeWindow> windows = assigner.assignWindows(new Object(), 0, ctx);
        assertEquals(2, windows.size(), "Sliding window (size=200, slide=100) should produce 2 overlapping windows");

        for (TimeWindow w : windows) {
            assertEquals(200, w.getEnd() - w.getStart());
        }
    }

    @Test
    void testSlidingProcessingTimeWindowOverlapping() {
        AtomicLong processingTime = new AtomicLong(1000);
        SlidingProcessingTimeWindows assigner = SlidingProcessingTimeWindows.of(300, 100);

        io.nop.stream.core.windowing.assigners.WindowAssigner.WindowAssignerContext ctx =
                new io.nop.stream.core.windowing.assigners.WindowAssigner.WindowAssignerContext() {
                    @Override
                    public long getCurrentProcessingTime() {
                        return processingTime.get();
                    }
                };

        Collection<TimeWindow> windows = assigner.assignWindows(new Object(), 0, ctx);
        assertTrue(windows.size() >= 2, "Sliding windows should produce overlapping windows");

        List<TimeWindow> sorted = new ArrayList<>(windows);
        sorted.sort(Comparator.comparingLong(TimeWindow::getStart));

        for (TimeWindow w : sorted) {
            assertEquals(300, w.getEnd() - w.getStart());
        }
    }

    @Test
    void testSlidingProcessingTimeWindowProperties() {
        SlidingProcessingTimeWindows assigner = SlidingProcessingTimeWindows.of(200, 100);
        assertFalse(assigner.isEventTime());
        assertInstanceOf(ProcessingTimeTrigger.class, assigner.getDefaultTrigger(null));
        assertEquals(200, assigner.getSize());
        assertEquals(100, assigner.getSlide());
    }

    @Test
    void testSlidingProcessingTimeInvalidParamsThrow() {
        assertThrows(StreamException.class, () -> SlidingProcessingTimeWindows.of(0, 100));
        assertThrows(StreamException.class, () -> SlidingProcessingTimeWindows.of(100, 0));
        assertThrows(StreamException.class, () -> SlidingProcessingTimeWindows.of(100, 50, -1));
        assertThrows(StreamException.class, () -> SlidingProcessingTimeWindows.of(100, 50, 50));
    }

    @Test
    void testProcessingTimeNotAffectedByWatermark() throws Exception {
        AtomicLong processingTime = new AtomicLong(1000);

        WindowAggregationOperator<Event, int[], Integer, String, TimeWindow> operator =
                new WindowAggregationOperator<>(
                        new TestProcessingTimeWindowAssigner(processingTime),
                        ProcessingTimeTrigger.create(),
                        new AggregateAggregationFunction<>(new SumAggregateFunction()),
                        (KeySelector<Event, String>) e -> e.key);

        List<Integer> results = new ArrayList<>();
        operator.setOutput(new io.nop.stream.core.operators.Output<StreamRecord<Integer>>() {
            @Override
            public void emitWatermark(Watermark mark) {
            }

            @Override
            public void emitWatermarkStatus(io.nop.stream.core.streamrecord.watermark.WatermarkStatus watermarkStatus) {
            }

            @Override
            public <X> void collect(io.nop.stream.core.util.OutputTag<X> outputTag, StreamRecord<X> record) {
            }

            @Override
            public void emitLatencyMarker(io.nop.stream.core.streamrecord.LatencyMarker latencyMarker) {
            }

            @Override
            public void emitBarrier(io.nop.stream.core.checkpoint.CheckpointBarrier barrier) {
            }

            @Override
            public void collect(StreamRecord<Integer> record) {
                results.add(record.getValue());
            }

            @Override
            public void close() {
            }
        });

        operator.open();

        operator.setCurrentKey("key1");
        operator.processElement(new StreamRecord<>(new Event("key1", 1, 10), 10));
        operator.setCurrentKey("key1");
        operator.processElement(new StreamRecord<>(new Event("key1", 2, 20), 20));

        operator.processWatermark(new Watermark(500));
        operator.processWatermark(new Watermark(1000));
        operator.processWatermark(new Watermark(Long.MAX_VALUE));

        assertTrue(results.isEmpty(),
                "Processing time windows should not fire based on watermarks alone");
    }

    @Test
    void testTumblingProcessingTimeElementsInSameWindow() {
        AtomicLong processingTime = new AtomicLong(1000);
        TumblingProcessingTimeWindows assigner = TumblingProcessingTimeWindows.of(100);

        io.nop.stream.core.windowing.assigners.WindowAssigner.WindowAssignerContext ctx =
                new io.nop.stream.core.windowing.assigners.WindowAssigner.WindowAssignerContext() {
                    @Override
                    public long getCurrentProcessingTime() {
                        return processingTime.get();
                    }
                };

        Collection<TimeWindow> w1 = assigner.assignWindows(new Object(), 0, ctx);
        processingTime.set(1050);
        Collection<TimeWindow> w2 = assigner.assignWindows(new Object(), 0, ctx);

        assertEquals(w1.iterator().next(), w2.iterator().next(),
                "Elements in same processing time window should get same window");
    }

    @Test
    void testTumblingProcessingTimeElementsInDifferentWindows() {
        AtomicLong processingTime = new AtomicLong(1000);
        TumblingProcessingTimeWindows assigner = TumblingProcessingTimeWindows.of(100);

        io.nop.stream.core.windowing.assigners.WindowAssigner.WindowAssignerContext ctx =
                new io.nop.stream.core.windowing.assigners.WindowAssigner.WindowAssignerContext() {
                    @Override
                    public long getCurrentProcessingTime() {
                        return processingTime.get();
                    }
                };

        Collection<TimeWindow> w1 = assigner.assignWindows(new Object(), 0, ctx);
        processingTime.set(1100);
        Collection<TimeWindow> w2 = assigner.assignWindows(new Object(), 0, ctx);

        assertNotEquals(w1.iterator().next(), w2.iterator().next(),
                "Elements in different processing time windows should get different windows");
    }

    private static class TestProcessingTimeWindowAssigner
            extends io.nop.stream.core.windowing.assigners.WindowAssigner<Object, TimeWindow> {
        private static final long serialVersionUID = 1L;
        private final AtomicLong processingTime;

        TestProcessingTimeWindowAssigner(AtomicLong processingTime) {
            this.processingTime = processingTime;
        }

        @Override
        public Collection<TimeWindow> assignWindows(Object element, long timestamp,
                                                     WindowAssignerContext assignerContext) {
            long now = processingTime.get();
            long start = TimeWindow.getWindowStartWithOffset(now, 0, 100);
            return Collections.singletonList(new TimeWindow(start, start + 100));
        }

        @Override
        public io.nop.stream.core.windowing.triggers.Trigger<Object, TimeWindow> getDefaultTrigger(
                io.nop.core.context.IServiceContext serviceContext) {
            return ProcessingTimeTrigger.create();
        }

        @Override
        public boolean isEventTime() {
            return false;
        }
    }
}
