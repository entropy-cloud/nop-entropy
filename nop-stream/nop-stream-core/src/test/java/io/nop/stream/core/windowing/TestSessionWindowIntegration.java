package io.nop.stream.core.windowing;

import io.nop.stream.core.common.eventtime.WatermarkStrategy;
import io.nop.stream.core.common.functions.AggregateFunction;
import io.nop.stream.core.common.functions.KeySelector;
import io.nop.stream.core.common.functions.ReduceFunction;
import io.nop.stream.core.common.functions.SinkFunction;
import io.nop.stream.core.common.typeinfo.TypeInformation;
import io.nop.stream.core.common.typeinfo.UnknownTypeInformation;
import io.nop.stream.core.environment.StreamExecutionEnvironment;
import io.nop.stream.core.operators.TimestampsAndWatermarksOperator;
import io.nop.stream.core.operators.WindowAggregationOperator;
import io.nop.stream.core.operators.AggregateAggregationFunction;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.streamrecord.watermark.Watermark;
import io.nop.stream.core.windowing.assigners.EventTimeSessionWindows;
import io.nop.stream.core.windowing.triggers.EventTimeTrigger;
import io.nop.stream.core.windowing.windows.TimeWindow;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import io.nop.stream.core.exceptions.StreamException;

public class TestSessionWindowIntegration {

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
    void testSessionWindowSingleElementProducesOneResult() throws Exception {
        List<Event> events = Collections.singletonList(
                new Event("key1", 42, 100));

        List<Integer> results = Collections.synchronizedList(new ArrayList<>());

        StreamExecutionEnvironment env = StreamExecutionEnvironment.createTestEnvironment();

        WatermarkStrategy<Event> strategy = WatermarkStrategy
                .<Event>forBoundedOutOfOrderness(Duration.ofMillis(5))
                .withTimestampAssigner((e, ts) -> e.timestamp);

        env.fromCollection(events)
                .transform("TimestampsAndWatermarks",
                        (TypeInformation<Event>) UnknownTypeInformation.INSTANCE,
                        new TimestampsAndWatermarksOperator<>(strategy))
                .keyBy((KeySelector<Event, String>) e -> e.key)
                .window(EventTimeSessionWindows.withGap(50))
                .aggregate(new SumAggregateFunction())
                .sink((SinkFunction<Integer>) results::add);

        env.execute("session-single-element");

        assertEquals(1, results.size());
        assertEquals(42, results.get(0));
    }

    @Test
    void testSessionWindowSameTimestampElementsMerge() throws Exception {
        List<Event> events = Arrays.asList(
                new Event("key1", 1, 100),
                new Event("key1", 2, 100),
                new Event("key1", 3, 100));

        List<Integer> results = Collections.synchronizedList(new ArrayList<>());

        StreamExecutionEnvironment env = StreamExecutionEnvironment.createTestEnvironment();

        WatermarkStrategy<Event> strategy = WatermarkStrategy
                .<Event>forBoundedOutOfOrderness(Duration.ofMillis(5))
                .withTimestampAssigner((e, ts) -> e.timestamp);

        env.fromCollection(events)
                .transform("TimestampsAndWatermarks",
                        (TypeInformation<Event>) UnknownTypeInformation.INSTANCE,
                        new TimestampsAndWatermarksOperator<>(strategy))
                .keyBy((KeySelector<Event, String>) e -> e.key)
                .window(EventTimeSessionWindows.withGap(50))
                .aggregate(new SumAggregateFunction())
                .sink((SinkFunction<Integer>) results::add);

        env.execute("session-same-timestamp");

        assertEquals(1, results.size());
        assertEquals(6, results.get(0));
    }

    @Test
    void testSessionWindowReduceSameTimestamp() throws Exception {
        List<Event> events = Arrays.asList(
                new Event("key1", 10, 100),
                new Event("key1", 20, 100),
                new Event("key1", 30, 100));

        List<Event> results = Collections.synchronizedList(new ArrayList<>());

        StreamExecutionEnvironment env = StreamExecutionEnvironment.createTestEnvironment();

        WatermarkStrategy<Event> strategy = WatermarkStrategy
                .<Event>forBoundedOutOfOrderness(Duration.ofMillis(5))
                .withTimestampAssigner((e, ts) -> e.timestamp);

        env.fromCollection(events)
                .transform("TimestampsAndWatermarks",
                        (TypeInformation<Event>) UnknownTypeInformation.INSTANCE,
                        new TimestampsAndWatermarksOperator<>(strategy))
                .keyBy((KeySelector<Event, String>) e -> e.key)
                .window(EventTimeSessionWindows.withGap(50))
                .reduce((ReduceFunction<Event>) (a, b) -> new Event(a.key, a.value + b.value, a.timestamp))
                .sink((SinkFunction<Event>) results::add);

        env.execute("session-reduce");

        assertEquals(1, results.size());
        assertEquals(60, results.get(0).value);
    }

    @Test
    void testMultiKeySessionWindows() throws Exception {
        List<Event> events = Arrays.asList(
                new Event("a", 1, 100),
                new Event("b", 2, 100),
                new Event("a", 3, 100),
                new Event("b", 4, 100));

        List<Integer> results = Collections.synchronizedList(new ArrayList<>());

        StreamExecutionEnvironment env = StreamExecutionEnvironment.createTestEnvironment();

        WatermarkStrategy<Event> strategy = WatermarkStrategy
                .<Event>forBoundedOutOfOrderness(Duration.ofMillis(5))
                .withTimestampAssigner((e, ts) -> e.timestamp);

        env.fromCollection(events)
                .transform("TimestampsAndWatermarks",
                        (TypeInformation<Event>) UnknownTypeInformation.INSTANCE,
                        new TimestampsAndWatermarksOperator<>(strategy))
                .keyBy((KeySelector<Event, String>) e -> e.key)
                .window(EventTimeSessionWindows.withGap(50))
                .aggregate(new SumAggregateFunction())
                .sink((SinkFunction<Integer>) results::add);

        env.execute("multi-key-session");

        results.sort(Integer::compare);

        assertEquals(2, results.size());
        List<Integer> expected = Arrays.asList(4, 6);
        assertEquals(expected, results);
    }

    @Test
    void testSessionWindowEachTimestampGetsOwnWindow() throws Exception {
        List<Event> events = Arrays.asList(
                new Event("key1", 1, 10),
                new Event("key1", 2, 20),
                new Event("key1", 3, 30));

        List<Integer> results = Collections.synchronizedList(new ArrayList<>());

        StreamExecutionEnvironment env = StreamExecutionEnvironment.createTestEnvironment();

        WatermarkStrategy<Event> strategy = WatermarkStrategy
                .<Event>forBoundedOutOfOrderness(Duration.ofMillis(5))
                .withTimestampAssigner((e, ts) -> e.timestamp);

        env.fromCollection(events)
                .transform("TimestampsAndWatermarks",
                        (TypeInformation<Event>) UnknownTypeInformation.INSTANCE,
                        new TimestampsAndWatermarksOperator<>(strategy))
                .keyBy((KeySelector<Event, String>) e -> e.key)
                .window(EventTimeSessionWindows.withGap(50))
                .aggregate(new SumAggregateFunction())
                .sink((SinkFunction<Integer>) results::add);

        env.execute("session-each-own-window");

        results.sort(Integer::compare);

        assertEquals(3, results.size());
        List<Integer> expected = Arrays.asList(1, 2, 3);
        assertEquals(expected, results);
    }

    @Test
    void testSessionWindowAssignsCorrectWindowBounds() {
        EventTimeSessionWindows assigner = EventTimeSessionWindows.withGap(100);

        Collection<TimeWindow> windows1 = assigner.assignWindows(new Object(), 50, null);
        assertEquals(1, windows1.size());
        TimeWindow w1 = windows1.iterator().next();
        assertEquals(50, w1.getStart());
        assertEquals(150, w1.getEnd());

        Collection<TimeWindow> windows2 = assigner.assignWindows(new Object(), 200, null);
        assertEquals(1, windows2.size());
        TimeWindow w2 = windows2.iterator().next();
        assertEquals(200, w2.getStart());
        assertEquals(300, w2.getEnd());
    }

    @Test
    void testSessionWindowMergingOverlapping() {
        EventTimeSessionWindows assigner = EventTimeSessionWindows.withGap(100);

        List<TimeWindow> windows = Arrays.asList(
                new TimeWindow(0, 100),
                new TimeWindow(50, 150));

        List<TimeWindow> mergedResults = new ArrayList<>();
        assigner.mergeWindows(windows, (toBeMerged, mergeResult) -> {
            mergedResults.add(mergeResult);
        });

        assertEquals(1, mergedResults.size());
        assertEquals(new TimeWindow(0, 150), mergedResults.get(0));
    }

    @Test
    void testSessionWindowNoMergeWhenGapExceeded() {
        EventTimeSessionWindows assigner = EventTimeSessionWindows.withGap(100);

        List<TimeWindow> windows = Arrays.asList(
                new TimeWindow(0, 100),
                new TimeWindow(200, 300));

        List<TimeWindow> mergedResults = new ArrayList<>();
        assigner.mergeWindows(windows, (toBeMerged, mergeResult) -> {
            mergedResults.add(mergeResult);
        });

        assertTrue(mergedResults.isEmpty());
    }

    @Test
    void testSessionWindowDefaultTriggerIsEventTime() {
        EventTimeSessionWindows assigner = EventTimeSessionWindows.withGap(100);
        assertTrue(assigner.isEventTime());
        assertInstanceOf(EventTimeTrigger.class, assigner.getDefaultTrigger(null));
    }

    @Test
    void testSessionWindowOperatorDirectly() throws Exception {
        WindowAggregationOperator<Event, int[], Integer, String, TimeWindow> operator =
                new WindowAggregationOperator<>(
                        EventTimeSessionWindows.withGap(50),
                        EventTimeTrigger.create(),
                        new AggregateAggregationFunction<>(new SumAggregateFunction()),
                        (KeySelector<Event, String>) e -> e.key);

        List<Integer> results = new ArrayList<>();
        operator.setOutput(new io.nop.stream.core.operators.Output<io.nop.stream.core.streamrecord.StreamRecord<Integer>>() {
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
        operator.processElement(new StreamRecord<>(new Event("key1", 2, 10), 10));
        operator.setCurrentKey("key1");
        operator.processElement(new StreamRecord<>(new Event("key1", 3, 10), 10));

        assertTrue(results.isEmpty());

        operator.processWatermark(new Watermark(Long.MAX_VALUE));

        assertEquals(1, results.size());
        assertEquals(6, results.get(0));
    }

    @Test
    void testSessionWindowNegativeGapThrows() {
        assertThrows(StreamException.class, () -> EventTimeSessionWindows.withGap(-1));
    }

    @Test
    void testSessionWindowZeroGapThrows() {
        assertThrows(StreamException.class, () -> EventTimeSessionWindows.withGap(0));
    }

    @Test
    void testSessionWindowWithLargeGapSingleSession() throws Exception {
        List<Event> events = Arrays.asList(
                new Event("key1", 1, 10),
                new Event("key1", 2, 20));

        List<Integer> results = Collections.synchronizedList(new ArrayList<>());

        StreamExecutionEnvironment env = StreamExecutionEnvironment.createTestEnvironment();

        WatermarkStrategy<Event> strategy = WatermarkStrategy
                .<Event>forBoundedOutOfOrderness(Duration.ofMillis(5))
                .withTimestampAssigner((e, ts) -> e.timestamp);

        env.fromCollection(events)
                .transform("TimestampsAndWatermarks",
                        (TypeInformation<Event>) UnknownTypeInformation.INSTANCE,
                        new TimestampsAndWatermarksOperator<>(strategy))
                .keyBy((KeySelector<Event, String>) e -> e.key)
                .window(EventTimeSessionWindows.withGap(10000))
                .aggregate(new SumAggregateFunction())
                .sink((SinkFunction<Integer>) results::add);

        env.execute("session-window-large-gap");

        assertEquals(2, results.size());
        results.sort(Integer::compare);
        List<Integer> expected = Arrays.asList(1, 2);
        assertEquals(expected, results);
    }
}
