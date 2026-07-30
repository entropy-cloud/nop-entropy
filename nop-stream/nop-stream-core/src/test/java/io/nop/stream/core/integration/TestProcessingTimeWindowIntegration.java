/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.integration;

import io.nop.stream.core.common.functions.SinkFunction;
import io.nop.stream.core.common.typeinfo.TypeInformation;
import io.nop.stream.core.common.typeinfo.UnknownTypeInformation;
import io.nop.stream.core.environment.StreamExecutionEnvironment;
import io.nop.stream.core.operators.AbstractStreamOperator;
import io.nop.stream.core.operators.OneInputStreamOperator;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.test.TestOutput;
import io.nop.stream.core.windowing.assigners.TumblingProcessingTimeWindows;
import io.nop.stream.core.windowing.assigners.WindowAssigner;
import io.nop.stream.core.windowing.triggers.ProcessingTimeTrigger;
import io.nop.stream.core.windowing.triggers.Trigger;
import io.nop.stream.core.windowing.triggers.TriggerResult;
import io.nop.stream.core.windowing.windows.TimeWindow;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end test for processing-time window pipelines using TumblingProcessingTimeWindows.
 * Uses direct operator wiring to control processing time advancement since the core module
 * does not provide a MockProcessingTimeService.
 */
public class TestProcessingTimeWindowIntegration {

    static final class IntEvent {
        final String key;
        final int value;

        IntEvent(String key, int value) {
            this.key = key;
            this.value = value;
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

    static final class SimpleProcessingTimeWindowOperator
            extends AbstractStreamOperator<WindowResult>
            implements OneInputStreamOperator<IntEvent, WindowResult> {

        private static final long serialVersionUID = 1L;

        private final TumblingProcessingTimeWindows windowAssigner;

        private transient Map<String, Map<TimeWindow, List<Integer>>> windowElements;
        private transient long currentProcessingTime;
        private transient WindowAssigner.WindowAssignerContext assignerContext;

        SimpleProcessingTimeWindowOperator(long windowSize) {
            this.windowAssigner = TumblingProcessingTimeWindows.of(windowSize);
        }

        @Override
        public void open() throws Exception {
            super.open();
            this.windowElements = new LinkedHashMap<>();
            this.currentProcessingTime = 0;
            this.assignerContext = () -> currentProcessingTime;
        }

        void advanceProcessingTime(long newTime) throws Exception {
            if (newTime <= currentProcessingTime) {
                return;
            }
            currentProcessingTime = newTime;

            List<WindowResult> results = new ArrayList<>();
            List<String> keysToRemove = new ArrayList<>();

            for (Map.Entry<String, Map<TimeWindow, List<Integer>>> keyEntry : windowElements.entrySet()) {
                String key = keyEntry.getKey();
                Map<TimeWindow, List<Integer>> windows = keyEntry.getValue();
                List<TimeWindow> windowsToRemove = new ArrayList<>();

                for (Map.Entry<TimeWindow, List<Integer>> windowEntry : windows.entrySet()) {
                    TimeWindow window = windowEntry.getKey();
                    if (window.maxTimestamp() <= currentProcessingTime) {
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

            results.sort(Comparator
                    .comparing((WindowResult r) -> r.key)
                    .thenComparingLong(r -> r.windowStart));

            for (WindowResult result : results) {
                output.collect(new StreamRecord<>(result));
            }
        }

        @Override
        public void processElement(StreamRecord<IntEvent> element) throws Exception {
            IntEvent event = element.getValue();
            long now = currentProcessingTime;

            Collection<TimeWindow> windows = windowAssigner.assignWindows(event, now, assignerContext);

            String key = event.key;
            for (TimeWindow window : windows) {
                windowElements
                        .computeIfAbsent(key, k -> new LinkedHashMap<>())
                        .computeIfAbsent(window, w -> new ArrayList<>())
                        .add(event.value);
            }
        }

        long getCurrentProcessingTime() {
            return currentProcessingTime;
        }
    }

    @Test
    void testProcessingTimeWindowPipeline() throws Exception {
        long windowSize = 100L;

        SimpleProcessingTimeWindowOperator windowOp = new SimpleProcessingTimeWindowOperator(windowSize);
        TestOutput<WindowResult> output = new TestOutput<>();
        windowOp.setOutput((io.nop.stream.core.operators.Output) output);
        windowOp.open();

        // All elements at processing time 0 → window [0,100)
        windowOp.processElement(new StreamRecord<>(new IntEvent("key1", 1)));
        windowOp.processElement(new StreamRecord<>(new IntEvent("key1", 2)));
        windowOp.processElement(new StreamRecord<>(new IntEvent("key2", 3)));

        windowOp.advanceProcessingTime(50);

        // Elements at processing time 50 → window [0,100) still (50 is in [0,100))
        windowOp.processElement(new StreamRecord<>(new IntEvent("key1", 4)));
        windowOp.processElement(new StreamRecord<>(new IntEvent("key2", 5)));

        windowOp.advanceProcessingTime(99);
        // Window [0,100) fires: maxTimestamp=99 <= processingTime=99
        // key1: 1+2+4 = 7, key2: 3+5 = 8
        assertEquals(2, output.getElements().size(),
                "Window [0,100) fires when processing time reaches maxTimestamp=99");
        assertTrue(output.getElements().stream().anyMatch(r ->
                r.key.equals("key1") && r.windowStart == 0 && r.windowEnd == 100 && r.sum == 7));
        assertTrue(output.getElements().stream().anyMatch(r ->
                r.key.equals("key2") && r.windowStart == 0 && r.windowEnd == 100 && r.sum == 8));

        // Elements at processing time 99 → window [0,100) is gone, new window [100,200)
        // Actually at processing time 99, window [0,100) has already fired and been removed.
        // getWindowStartWithOffset(99, 0, 100) = 0, so this would go to a new window [0,100)...
        // Wait, let me think: the window [0,100) was removed. But getWindowStartWithOffset(99, 0, 100)
        // returns floor(99/100)*100 = 0. So this element is assigned to window [0,100) but
        // that window was already fired. In a real system the trigger would handle this,
        // but our simple operator doesn't re-fire. Let me advance to 100 first.
        windowOp.advanceProcessingTime(100);

        // Now elements at processing time 100 → window [100,200)
        windowOp.processElement(new StreamRecord<>(new IntEvent("key1", 6)));
        windowOp.processElement(new StreamRecord<>(new IntEvent("key2", 7)));
        windowOp.processElement(new StreamRecord<>(new IntEvent("key1", 8)));

        windowOp.advanceProcessingTime(200);

        List<WindowResult> results = new ArrayList<>(output.getElements());
        results.sort(Comparator
                .comparing((WindowResult r) -> r.key)
                .thenComparingLong(r -> r.windowStart));

        assertEquals(4, results.size());

        assertEquals(new WindowResult("key1", 0, 100, 7), results.get(0));
        assertEquals(new WindowResult("key1", 100, 200, 14), results.get(1));
        assertEquals(new WindowResult("key2", 0, 100, 8), results.get(2));
        assertEquals(new WindowResult("key2", 100, 200, 7), results.get(3));
    }

    @Test
    void testProcessingTimeWindowInterleavedKeys() throws Exception {
        long windowSize = 100L;

        SimpleProcessingTimeWindowOperator windowOp = new SimpleProcessingTimeWindowOperator(windowSize);
        TestOutput<WindowResult> output = new TestOutput<>();
        windowOp.setOutput((io.nop.stream.core.operators.Output) output);
        windowOp.open();

        windowOp.processElement(new StreamRecord<>(new IntEvent("a", 10)));
        windowOp.processElement(new StreamRecord<>(new IntEvent("b", 20)));

        windowOp.advanceProcessingTime(50);
        windowOp.processElement(new StreamRecord<>(new IntEvent("a", 5)));

        windowOp.advanceProcessingTime(99);

        assertEquals(2, output.getElements().size(),
                "Window [0,100) fires at maxTimestamp=99 for both keys");
        assertTrue(output.getElements().stream().anyMatch(r ->
                r.key.equals("a") && r.windowStart == 0 && r.windowEnd == 100 && r.sum == 15));
        assertTrue(output.getElements().stream().anyMatch(r ->
                r.key.equals("b") && r.windowStart == 0 && r.windowEnd == 100 && r.sum == 20));
    }

    @Test
    void testTumblingProcessingTimeWindowAssignment() {
        TumblingProcessingTimeWindows assigner = TumblingProcessingTimeWindows.of(100);
        WindowAssigner.WindowAssignerContext ctx = () -> 50;

        Collection<TimeWindow> w1 = assigner.assignWindows("dummy", 50, ctx);
        assertEquals(1, w1.size());
        TimeWindow win1 = w1.iterator().next();
        assertEquals(0, win1.getStart());
        assertEquals(100, win1.getEnd());

        WindowAssigner.WindowAssignerContext ctx2 = () -> 100;
        Collection<TimeWindow> w2 = assigner.assignWindows("dummy", 100, ctx2);
        assertEquals(1, w2.size());
        TimeWindow win2 = w2.iterator().next();
        assertEquals(100, win2.getStart());
        assertEquals(200, win2.getEnd());

        WindowAssigner.WindowAssignerContext ctx3 = () -> 199;
        Collection<TimeWindow> w3 = assigner.assignWindows("dummy", 199, ctx3);
        assertEquals(1, w3.size());
        assertEquals(100, w3.iterator().next().getStart());
        assertEquals(200, w3.iterator().next().getEnd());

        WindowAssigner.WindowAssignerContext ctx4 = () -> 250;
        Collection<TimeWindow> w4 = assigner.assignWindows("dummy", 250, ctx4);
        assertEquals(1, w4.size());
        TimeWindow win4 = w4.iterator().next();
        assertEquals(200, win4.getStart());
        assertEquals(300, win4.getEnd());
    }

    @Test
    void testProcessingTimeTriggerBehavior() throws Exception {
        ProcessingTimeTrigger trigger = ProcessingTimeTrigger.create();
        TimeWindow window = new TimeWindow(0, 100);

        long[] processingTime = {0};
        Trigger.TriggerContext ctx = new Trigger.TriggerContext() {
            @Override
            public long getCurrentProcessingTime() {
                return processingTime[0];
            }

            @Override
            public long getCurrentWatermark() {
                return Long.MIN_VALUE;
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

        TriggerResult r1 = trigger.onElement("elem", 50, window, ctx);
        assertEquals(TriggerResult.CONTINUE, r1,
                "Element should continue when processing time is below window end");

        TriggerResult r2 = trigger.onProcessingTime(99, window, ctx);
        assertEquals(TriggerResult.FIRE, r2,
                "Trigger should fire on processing time before window maxTimestamp");

        TriggerResult r3 = trigger.onProcessingTime(50, window, ctx);
        assertEquals(TriggerResult.FIRE, r3,
                "Trigger should fire on any processing time callback");
    }
}
