/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.integration;

import io.nop.stream.core.common.eventtime.WatermarkStrategy;
import io.nop.stream.core.common.functions.KeySelector;
import io.nop.stream.core.common.functions.SinkFunction;
import io.nop.stream.core.common.typeinfo.TypeInformation;
import io.nop.stream.core.common.typeinfo.UnknownTypeInformation;
import io.nop.stream.core.datastream.SingleOutputStreamOperator;
import io.nop.stream.core.environment.StreamExecutionEnvironment;
import io.nop.stream.core.operators.AbstractStreamOperator;
import io.nop.stream.core.operators.KeyContext;
import io.nop.stream.core.operators.OneInputStreamOperator;
import io.nop.stream.core.operators.TimestampsAndWatermarksOperator;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.streamrecord.watermark.Watermark;
import io.nop.stream.core.test.TestOutput;
import io.nop.stream.core.windowing.assigners.TumblingEventTimeWindows;
import io.nop.stream.core.windowing.assigners.WindowAssigner;
import io.nop.stream.core.windowing.triggers.EventTimeTrigger;
import io.nop.stream.core.windowing.triggers.Trigger;
import io.nop.stream.core.windowing.triggers.TriggerResult;
import io.nop.stream.core.windowing.windows.TimeWindow;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end test exercising the full event-time window pipeline:
 * assignTimestampsAndWatermarks → keyBy → window(TumblingEventTimeWindows) → aggregate → sink.
 *
 * <p>Because {@code WindowedStream.aggregate()/reduce()/apply()} throw UnsupportedOperationException
 * in the core module (they require the runtime module's WindowOperator), and because
 * {@code assignTimestampsAndWatermarks()} creates a TimestampsAndWatermarksTransformation
 * that the fast-path executor does not yet handle, this test uses {@code DataStream.transform()}
 * to inject a {@link TimestampsAndWatermarksOperator} and a simplified event-time window
 * operator directly into the pipeline chain.
 *
 * <p>Additionally, lower-level operator tests verify timestamp extraction, watermark generation,
 * window assignment, and trigger behavior in isolation.
 */
public class TestEventTimeWindowE2E {

    static final class TimedEvent {
        final String key;
        final int value;
        final long timestamp;

        TimedEvent(String key, int value, long timestamp) {
            this.key = key;
            this.value = value;
            this.timestamp = timestamp;
        }

        @Override
        public String toString() {
            return "TimedEvent{key='" + key + "', value=" + value + ", ts=" + timestamp + '}';
        }
    }

    static final class WindowResult {
        final String key;
        final long windowStart;
        final long windowEnd;
        final int sum;

        WindowResult(String key, long windowStart, long windowEnd, int sum) {
            this.key = key;
            this.windowStart = windowStart;
            this.windowEnd = windowEnd;
            this.sum = sum;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof WindowResult)) return false;
            WindowResult that = (WindowResult) o;
            return windowStart == that.windowStart
                    && windowEnd == that.windowEnd
                    && sum == that.sum
                    && Objects.equals(key, that.key);
        }

        @Override
        public int hashCode() {
            return Objects.hash(key, windowStart, windowEnd, sum);
        }

        @Override
        public String toString() {
            return "WindowResult{key='" + key + "', window=[" + windowStart + ',' + windowEnd
                    + "), sum=" + sum + '}';
        }
    }

    static final class SimpleEventTimeWindowOperator
            extends AbstractStreamOperator<WindowResult>
            implements OneInputStreamOperator<TimedEvent, WindowResult>, KeyContext {

        private static final long serialVersionUID = 1L;

        private final TumblingEventTimeWindows windowAssigner;
        private final long windowSize;

        /** Tracks elements per (key, window). */
        private transient Map<String, Map<TimeWindow, List<Integer>>> windowElements;

        private transient long currentWatermark;
        private transient Object currentKey;

        private transient WindowAssigner.WindowAssignerContext assignerContext;

        SimpleEventTimeWindowOperator(long windowSize) {
            this.windowAssigner = TumblingEventTimeWindows.of(windowSize);
            this.windowSize = windowSize;
        }

        @Override
        public void open() throws Exception {
            super.open();
            this.windowElements = new LinkedHashMap<>();
            this.currentWatermark = Long.MIN_VALUE;
            this.assignerContext = new WindowAssigner.WindowAssignerContext() {
                @Override
                public long getCurrentProcessingTime() {
                    return System.currentTimeMillis();
                }
            };
        }

        @Override
        public void processElement(StreamRecord<TimedEvent> element) throws Exception {
            TimedEvent event = element.getValue();
            long timestamp = element.getTimestamp();

            // Assign windows using TumblingEventTimeWindows
            Collection<TimeWindow> windows = windowAssigner.assignWindows(event, timestamp, assignerContext);

            String key = String.valueOf(currentKey);
            for (TimeWindow window : windows) {
                windowElements
                        .computeIfAbsent(key, k -> new LinkedHashMap<>())
                        .computeIfAbsent(window, w -> new ArrayList<>())
                        .add(event.value);
            }
        }

        @Override
        public void processWatermark(Watermark mark) throws Exception {
            long newWatermark = mark.getTimestamp();
            if (newWatermark <= currentWatermark) {
                return;
            }
            currentWatermark = newWatermark;

            // Fire windows whose maxTimestamp <= currentWatermark
            List<WindowResult> results = new ArrayList<>();
            List<String> keysToRemove = new ArrayList<>();

            for (Map.Entry<String, Map<TimeWindow, List<Integer>>> keyEntry : windowElements.entrySet()) {
                String key = keyEntry.getKey();
                Map<TimeWindow, List<Integer>> windows = keyEntry.getValue();
                List<TimeWindow> windowsToRemove = new ArrayList<>();

                for (Map.Entry<TimeWindow, List<Integer>> windowEntry : windows.entrySet()) {
                    TimeWindow window = windowEntry.getKey();
                    if (window.maxTimestamp() <= currentWatermark) {
                        int sum = 0;
                        for (int v : windowEntry.getValue()) {
                            sum += v;
                        }
                        results.add(new WindowResult(key, window.getStart(), window.getEnd(), sum));
                        windowsToRemove.add(window);
                    }
                }

                for (TimeWindow w : windowsToRemove) {
                    windows.remove(w);
                }

                if (windows.isEmpty()) {
                    keysToRemove.add(key);
                }
            }

            for (String k : keysToRemove) {
                windowElements.remove(k);
            }

            // Emit results sorted for deterministic output
            results.sort(Comparator
                    .comparing((WindowResult r) -> r.key)
                    .thenComparingLong(r -> r.windowStart));

            for (WindowResult result : results) {
                output.collect(new StreamRecord<>(result));
            }

            // Forward watermark downstream
            output.emitWatermark(mark);
        }

        @Override
        public void setCurrentKey(Object key) {
            this.currentKey = key;
        }

        @Override
        public Object getCurrentKey() {
            return currentKey;
        }
    }

    @Test
    void testEventTimeWindowPipeline() throws Exception {
        // Window size = 100ms, bounded out-of-orderness = 10ms
        long windowSize = 100L;
        Duration outOfOrderness = Duration.ofMillis(10);

        // Test data:
        //   Window [0,100): key1(1+2=3), key2(3+5=8)
        //   Window [100,200): key1(4+6=10), key2(7)
        //   Window [200,300): key1(8)
        List<TimedEvent> events = Arrays.asList(
                new TimedEvent("key1", 1, 10),
                new TimedEvent("key1", 2, 50),
                new TimedEvent("key2", 3, 30),
                new TimedEvent("key1", 4, 120),
                new TimedEvent("key2", 5, 90),
                new TimedEvent("key1", 6, 180),
                new TimedEvent("key2", 7, 150),
                new TimedEvent("key1", 8, 250)
        );

        // Collect results
        List<WindowResult> results = Collections.synchronizedList(new ArrayList<>());

        // Build pipeline
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        WatermarkStrategy<TimedEvent> strategy = WatermarkStrategy
                .<TimedEvent>forBoundedOutOfOrderness(outOfOrderness)
                .withTimestampAssigner((event, ts) -> event.timestamp);

        // Use transform() to inject TimestampsAndWatermarksOperator directly
        // (assignTimestampsAndWatermarks() creates TimestampsAndWatermarksTransformation
        // which the fast-path executor does not handle)
        SingleOutputStreamOperator<TimedEvent> timestamped = env
                .fromCollection(events)
                .transform("TimestampsAndWatermarks",
                        (TypeInformation<TimedEvent>) UnknownTypeInformation.INSTANCE,
                        new TimestampsAndWatermarksOperator<>(strategy));

        // keyBy + window via transform (WindowedStream.aggregate() throws UnsupportedOperationException)
        timestamped
                .keyBy((KeySelector<TimedEvent, String>) event -> event.key)
                .transform("EventTimeWindow",
                        (TypeInformation<WindowResult>) UnknownTypeInformation.INSTANCE,
                        new SimpleEventTimeWindowOperator(windowSize))
                .sink((SinkFunction<WindowResult>) results::add);

        env.execute("event-time-window-e2e");

        // Verify results
        // MAX_WATERMARK at end of source fires all windows.
        // Sort results for deterministic comparison.
        results.sort(Comparator
                .comparing((WindowResult r) -> r.key)
                .thenComparingLong(r -> r.windowStart));

        assertEquals(5, results.size());

        // Sorted by key then windowStart: key1 first, then key2
        assertEquals(new WindowResult("key1", 0, 100, 3), results.get(0));
        assertEquals(new WindowResult("key1", 100, 200, 10), results.get(1));
        assertEquals(new WindowResult("key1", 200, 300, 8), results.get(2));
        assertEquals(new WindowResult("key2", 0, 100, 8), results.get(3));
        assertEquals(new WindowResult("key2", 100, 200, 7), results.get(4));
    }

    @Test
    void testTimestampsAndWatermarksOperatorDirect() throws Exception {
        Duration outOfOrderness = Duration.ofMillis(50);
        WatermarkStrategy<TimedEvent> strategy = WatermarkStrategy
                .<TimedEvent>forBoundedOutOfOrderness(outOfOrderness)
                .withTimestampAssigner((event, ts) -> event.timestamp);

        TimestampsAndWatermarksOperator<TimedEvent> tsOperator = new TimestampsAndWatermarksOperator<>(strategy);
        TestOutput<TimedEvent> output = new TestOutput<>();
        tsOperator.setOutput((io.nop.stream.core.operators.Output) output);
        tsOperator.open();

        // Feed elements with timestamps 100, 300, 200 (out of order)
        tsOperator.processElement(new StreamRecord<>(new TimedEvent("a", 1, 100)));
        tsOperator.processElement(new StreamRecord<>(new TimedEvent("b", 2, 300)));
        tsOperator.processElement(new StreamRecord<>(new TimedEvent("c", 3, 200)));

        // Verify timestamps were extracted correctly
        List<StreamRecord<TimedEvent>> records = output.getRecords();
        assertEquals(3, records.size());
        assertEquals(100L, records.get(0).getTimestamp());
        assertEquals(300L, records.get(1).getTimestamp());
        assertEquals(200L, records.get(2).getTimestamp());

        // Verify event values are preserved
        assertEquals("a", records.get(0).getValue().key);
        assertEquals("b", records.get(1).getValue().key);
        assertEquals("c", records.get(2).getValue().key);

        // Verify watermarks were emitted
        // The first element triggers periodic emit because nextWatermarkTime starts at Long.MIN_VALUE+1.
        // BoundedOutOfOrdernessWatermarks emits watermark = maxTimestamp - outOfOrderness - 1
        // After first element: maxTimestamp=100, watermark = 100-50-1 = 49
        List<Watermark> watermarks = output.getWatermarks();
        assertFalse(watermarks.isEmpty(), "At least one watermark should be emitted");

        // First periodic watermark should be 100 - 50 - 1 = 49
        assertEquals(49L, watermarks.get(0).getTimestamp(),
                "First watermark should be maxTs(100) - outOfOrderness(50) - 1");

        // Verify watermark monotonicity
        for (int i = 1; i < watermarks.size(); i++) {
            assertTrue(watermarks.get(i).getTimestamp() >= watermarks.get(i - 1).getTimestamp(),
                    "Watermarks must be monotonically increasing");
        }
    }

    @Test
    void testOperatorChainTimestampsToWindow() throws Exception {
        long windowSize = 100L;
        Duration outOfOrderness = Duration.ofMillis(10);

        WatermarkStrategy<TimedEvent> strategy = WatermarkStrategy
                .<TimedEvent>forBoundedOutOfOrderness(outOfOrderness)
                .withTimestampAssigner((event, ts) -> event.timestamp);

        // Create and wire operators
        TimestampsAndWatermarksOperator<TimedEvent> tsOperator = new TimestampsAndWatermarksOperator<>(strategy);
        SimpleEventTimeWindowOperator windowOperator = new SimpleEventTimeWindowOperator(windowSize);
        TestOutput<WindowResult> finalOutput = new TestOutput<>();

        // Wire: tsOperator → windowOperator → finalOutput
        // KeyContext for window operator (simulate KeyExtractingOutput)
        windowOperator.setOutput((io.nop.stream.core.operators.Output) finalOutput);

        // Create a chaining output that also sets key context
        io.nop.stream.core.operators.Output<StreamRecord<TimedEvent>> chainedOutput =
                new io.nop.stream.core.operators.Output<StreamRecord<TimedEvent>>() {
                    @Override
                    public void collect(StreamRecord<TimedEvent> record) {
                        try {
                            // Set key before forwarding (simulating KeyExtractingOutput)
                            windowOperator.setCurrentKey(record.getValue().key);
                            windowOperator.processElement(record);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }

                    @Override
                    public <X> void collect(io.nop.stream.core.util.OutputTag<X> outputTag, StreamRecord<X> record) {
                    }

                    @Override
                    public void emitWatermark(Watermark mark) {
                        try {
                            windowOperator.processWatermark(mark);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }

                    @Override
                    public void emitWatermarkStatus(io.nop.stream.core.streamrecord.watermark.WatermarkStatus status) {
                    }

                    @Override
                    public void emitLatencyMarker(io.nop.stream.core.streamrecord.LatencyMarker marker) {
                    }

                    @Override
                    public void emitBarrier(io.nop.stream.core.checkpoint.CheckpointBarrier barrier) {
                    }

                    @Override
                    public void close() {
                    }
                };

        tsOperator.setOutput(chainedOutput);

        // Open operators
        tsOperator.open();
        windowOperator.open();

        // Feed elements: two keys, three windows
        // Window [0,100): key1(1,2), key2(3)
        // Window [100,200): key1(4), key2(5)
        // Window [200,300): key1(6)
        tsOperator.processElement(new StreamRecord<>(new TimedEvent("key1", 1, 10)));
        tsOperator.processElement(new StreamRecord<>(new TimedEvent("key1", 2, 50)));
        tsOperator.processElement(new StreamRecord<>(new TimedEvent("key2", 3, 30)));
        tsOperator.processElement(new StreamRecord<>(new TimedEvent("key1", 4, 120)));
        tsOperator.processElement(new StreamRecord<>(new TimedEvent("key2", 5, 150)));
        tsOperator.processElement(new StreamRecord<>(new TimedEvent("key1", 6, 250)));

        // At this point, no windows should have fired yet (watermark hasn't advanced enough
        // to pass any window boundary except possibly via periodic emit).
        // The first element triggers a periodic emit with watermark = 10-10-1 = -1,
        // which won't fire any window.

        // Emit MAX_WATERMARK to fire all windows (simulating end-of-source)
        tsOperator.processWatermark(Watermark.MAX_WATERMARK);

        // Collect and verify results
        List<WindowResult> results = new ArrayList<>(finalOutput.getElements());
        results.sort(Comparator
                .comparing((WindowResult r) -> r.key)
                .thenComparingLong(r -> r.windowStart));

        assertEquals(5, results.size(), "Should have 5 window results (3 for key1, 2 for key2)");

        // Sorted by key then windowStart
        assertEquals(new WindowResult("key1", 0, 100, 3), results.get(0));
        assertEquals(new WindowResult("key1", 100, 200, 4), results.get(1));
        assertEquals(new WindowResult("key1", 200, 300, 6), results.get(2));
        assertEquals(new WindowResult("key2", 0, 100, 3), results.get(3));
        assertEquals(new WindowResult("key2", 100, 200, 5), results.get(4));
    }

    @Test
    void testTumblingWindowAssignment() {
        long windowSize = 100L;
        TumblingEventTimeWindows assigner = TumblingEventTimeWindows.of(windowSize);
        WindowAssigner.WindowAssignerContext ctx = new WindowAssigner.WindowAssignerContext() {
            @Override
            public long getCurrentProcessingTime() {
                return System.currentTimeMillis();
            }
        };

        // Timestamp 50 → window [0, 100)
        Collection<TimeWindow> w1 = assigner.assignWindows("dummy", 50, ctx);
        assertEquals(1, w1.size());
        TimeWindow win1 = w1.iterator().next();
        assertEquals(0, win1.getStart());
        assertEquals(100, win1.getEnd());

        // Timestamp 100 → window [100, 200)
        Collection<TimeWindow> w2 = assigner.assignWindows("dummy", 100, ctx);
        assertEquals(1, w2.size());
        TimeWindow win2 = w2.iterator().next();
        assertEquals(100, win2.getStart());
        assertEquals(200, win2.getEnd());

        // Timestamp 199 → window [100, 200)
        Collection<TimeWindow> w3 = assigner.assignWindows("dummy", 199, ctx);
        assertEquals(1, w3.size());
        assertEquals(100, w3.iterator().next().getStart());
        assertEquals(200, w3.iterator().next().getEnd());

        // Timestamp 250 → window [200, 300)
        Collection<TimeWindow> w4 = assigner.assignWindows("dummy", 250, ctx);
        assertEquals(1, w4.size());
        TimeWindow win4 = w4.iterator().next();
        assertEquals(200, win4.getStart());
        assertEquals(300, win4.getEnd());
    }

    @Test
    void testEventTimeTriggerFiresOnWatermark() throws Exception {
        EventTimeTrigger trigger = EventTimeTrigger.create();
        TimeWindow window = new TimeWindow(0, 100);

        // Minimal TriggerContext that tracks current watermark
        long[] watermarkHolder = {Long.MIN_VALUE};
        Trigger.TriggerContext ctx = new Trigger.TriggerContext() {
            @Override
            public long getCurrentProcessingTime() {
                return System.currentTimeMillis();
            }

            @Override
            public long getCurrentWatermark() {
                return watermarkHolder[0];
            }

            @Override
            public void registerEventTimeTimer(long time) {
            }

            @Override
            public void deleteEventTimeTimer(long time) {
            }

            @Override
            public void registerProcessingTimeTimer(long time) {
            }

            @Override
            public void deleteProcessingTimeTimer(long time) {
            }

            @Override
            public <T> io.nop.stream.core.common.accumulators.SimpleAccumulator<T> getSimpleAccumulator(
                    io.nop.stream.core.common.state.StateDescriptor<T> descriptor) {
                return null;
            }
        };

        // Watermark is at MIN_VALUE → window not yet past → should register timer
        TriggerResult r1 = trigger.onElement("elem", 50, window, ctx);
        assertEquals(TriggerResult.CONTINUE, r1, "Element should continue when watermark below window end");

        // Advance watermark to 98 (just below window.maxTimestamp=99)
        watermarkHolder[0] = 98L;
        TriggerResult r2 = trigger.onElement("elem2", 80, window, ctx);
        assertEquals(TriggerResult.CONTINUE, r2, "Element should continue when watermark at 98 (below maxTimestamp=99)");

        // Advance watermark to 99 → window.maxTimestamp(99) <= watermark(99) → should fire
        watermarkHolder[0] = 99L;
        TriggerResult r2b = trigger.onElement("elem3", 85, window, ctx);
        assertEquals(TriggerResult.FIRE, r2b, "Element should fire when watermark reaches window.maxTimestamp");

        // Advance watermark past window end → onEventTime should fire
        TriggerResult r3 = trigger.onEventTime(window.maxTimestamp(), window, ctx);
        assertEquals(TriggerResult.FIRE, r3, "Trigger should fire when event time reaches window.maxTimestamp");
    }

    @Test
    void testIntermediateWatermarkFiresWindows() throws Exception {
        long windowSize = 100L;
        Duration outOfOrderness = Duration.ofMillis(10);

        WatermarkStrategy<TimedEvent> strategy = WatermarkStrategy
                .<TimedEvent>forBoundedOutOfOrderness(outOfOrderness)
                .withTimestampAssigner((event, ts) -> event.timestamp);

        TimestampsAndWatermarksOperator<TimedEvent> tsOperator = new TimestampsAndWatermarksOperator<>(strategy);
        SimpleEventTimeWindowOperator windowOperator = new SimpleEventTimeWindowOperator(windowSize);
        TestOutput<WindowResult> finalOutput = new TestOutput<>();

        // Wire operators
        windowOperator.setOutput((io.nop.stream.core.operators.Output) finalOutput);
        io.nop.stream.core.operators.Output<StreamRecord<TimedEvent>> chainedOutput =
                new io.nop.stream.core.operators.Output<StreamRecord<TimedEvent>>() {
                    @Override
                    public void collect(StreamRecord<TimedEvent> record) {
                        try {
                            windowOperator.setCurrentKey(record.getValue().key);
                            windowOperator.processElement(record);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }

                    @Override
                    public <X> void collect(io.nop.stream.core.util.OutputTag<X> outputTag, StreamRecord<X> record) {
                    }

                    @Override
                    public void emitWatermark(Watermark mark) {
                        try {
                            windowOperator.processWatermark(mark);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }

                    @Override
                    public void emitWatermarkStatus(io.nop.stream.core.streamrecord.watermark.WatermarkStatus s) {
                    }

                    @Override
                    public void emitLatencyMarker(io.nop.stream.core.streamrecord.LatencyMarker m) {
                    }

                    @Override
                    public void emitBarrier(io.nop.stream.core.checkpoint.CheckpointBarrier b) {
                    }

                    @Override
                    public void close() {
                    }
                };

        tsOperator.setOutput(chainedOutput);
        tsOperator.open();
        windowOperator.open();

        // Feed elements in window [0,100)
        tsOperator.processElement(new StreamRecord<>(new TimedEvent("key1", 10, 10)));
        tsOperator.processElement(new StreamRecord<>(new TimedEvent("key1", 20, 50)));
        tsOperator.processElement(new StreamRecord<>(new TimedEvent("key2", 5, 30)));

        // No windows fired yet — watermark is at most maxTs(50)-10-1=39 from periodic emit,
        // but window [0,100) requires watermark >= 99 to fire.
        List<WindowResult> resultsBefore = finalOutput.getElements();
        assertTrue(resultsBefore.isEmpty() || resultsBefore.stream()
                        .noneMatch(r -> r.windowStart == 0 && r.windowEnd == 100),
                "Window [0,100) should not have fired before watermark reaches 99");

        // Advance watermark past window [0,100) boundary
        // window.maxTimestamp() = 99, so watermark >= 99 should fire it
        windowOperator.processWatermark(new Watermark(200));

        List<WindowResult> resultsAfter = finalOutput.getElements();
        assertTrue(resultsAfter.size() >= 2, "At least 2 results (key1 and key2 for window [0,100))");

        boolean hasKey1 = resultsAfter.stream().anyMatch(r ->
                r.key.equals("key1") && r.windowStart == 0 && r.windowEnd == 100 && r.sum == 30);
        boolean hasKey2 = resultsAfter.stream().anyMatch(r ->
                r.key.equals("key2") && r.windowStart == 0 && r.windowEnd == 100 && r.sum == 5);
        assertTrue(hasKey1, "key1@[0,100) should have sum=30");
        assertTrue(hasKey2, "key2@[0,100) should have sum=5");
    }
}
