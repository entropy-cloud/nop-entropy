/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.windowing.triggers;

import io.nop.stream.core.common.accumulators.LongMinimum;
import io.nop.stream.core.common.accumulators.SimpleAccumulator;
import io.nop.stream.core.common.state.StateDescriptor;
import io.nop.stream.core.windowing.windows.TimeWindow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ContinuousEventTimeTrigger}.
 */
public class TestContinuousEventTimeTrigger {

    private ContinuousEventTimeTrigger<TimeWindow> trigger;
    private MockTriggerContext triggerContext;

    @BeforeEach
    public void setUp() {
        trigger = ContinuousEventTimeTrigger.of(Duration.ofMillis(100));
        triggerContext = new MockTriggerContext();
    }

    @Test
    public void testOfFactoryMethod() {
        ContinuousEventTimeTrigger<TimeWindow> t = ContinuousEventTimeTrigger.of(Duration.ofMillis(500));
        assertNotNull(t, "Trigger should be created");
        assertEquals(500L, t.getInterval(), "Interval should be 500");
    }

    @Test
    public void testGetInterval() {
        assertEquals(100L, trigger.getInterval(), "Interval should be 100");
    }

    @Test
    public void testOnElementRegistersTimerAtIntervalBoundary() throws Exception {
        // Window: [0, 1000), maxTimestamp = 999
        // Element timestamp at 250, interval = 100
        // Implementation: timestamp - (timestamp % interval) = 250 - 50 = 200
        // nextFireTimestamp = Math.min(200 + interval, window.maxTimestamp()) = Math.min(300, 999) = 300
        // Also registers window.maxTimestamp() timer at 999
        TimeWindow window = new TimeWindow(0, 1000);
        long elementTimestamp = 250;
        triggerContext.setCurrentWatermark(0);

        trigger.onElement("element", elementTimestamp, window, triggerContext);

        // Both timers should be registered: aligned timer (300) and window.max timestamp (999)
        assertTrue(triggerContext.getRegisteredEventTimeTimers().contains(300L),
            "Aligned timer should be registered at 300 (aligned boundary + interval)");
        assertTrue(triggerContext.getRegisteredEventTimeTimers().contains(999L),
            "Window max timestamp timer should be registered at 999");
    }

    @Test
    public void testOnElementReturnsFireWhenWatermarkPastWindow() throws Exception {
        // Window: [0, 1000), maxTimestamp = 999
        TimeWindow window = new TimeWindow(0, 1000);
        long elementTimestamp = 500;

        // Set watermark past window maxTimestamp
        triggerContext.setCurrentWatermark(1000);

        TriggerResult result = trigger.onElement("element", elementTimestamp, window, triggerContext);

        assertEquals(TriggerResult.FIRE, result, "Should return FIRE when watermark past window");
    }

    @Test
    public void testOnElementRegistersEventTimeTimer() throws Exception {
        // Window: [0, 1000), maxTimestamp = 999
        // Element timestamp at 150, interval = 100
        // Implementation: timestamp - (timestamp % interval) = 150 - 50 = 100
        // nextFireTimestamp = Math.min(100 + interval, window.maxTimestamp()) = Math.min(200, 999) = 200
        // Also registers window.maxTimestamp() timer at 999
        TimeWindow window = new TimeWindow(0, 1000);
        long elementTimestamp = 150;

        triggerContext.setCurrentWatermark(0);

        trigger.onElement("element", elementTimestamp, window, triggerContext);

        // Both timers should be registered
        assertTrue(triggerContext.getRegisteredEventTimeTimers().contains(200L),
            "Aligned timer should be registered at 200");
        assertTrue(triggerContext.getRegisteredEventTimeTimers().contains(999L),
            "Window max timestamp timer should be registered at 999");
    }



    @Test
    public void testOnEventTimeReturnsFireAtInterval() throws Exception {
        // Window: [0, 1000), maxTimestamp = 999
        // Element timestamp at 50, interval = 100
        // Implementation: timestamp - (timestamp % interval) = 50 - 50 = 0
        // nextFireTimestamp = Math.min(0 + interval, window.maxTimestamp()) = Math.min(100, 999) = 100
        TimeWindow window = new TimeWindow(0, 1000);
        long elementTimestamp = 50;

        triggerContext.setCurrentWatermark(0);
        trigger.onElement("element", elementTimestamp, window, triggerContext);

        // First fire time is the aligned timer at 100
        TriggerResult result = trigger.onEventTime(100, window, triggerContext);

        assertEquals(TriggerResult.FIRE, result, "Should FIRE at interval boundary");
    }

    @Test
    public void testOnEventTimeRegistersNextTimerAfterFire() throws Exception {
        // Window: [0, 1000), maxTimestamp = 999
        // Element timestamp at 50, interval = 100
        // First aligned timer at: 50 - (50 % 100) = 0, + 100 = 100
        TimeWindow window = new TimeWindow(0, 1000);
        long elementTimestamp = 50;

        triggerContext.setCurrentWatermark(0);
        trigger.onElement("element", elementTimestamp, window, triggerContext);

        // Clear registered timers to check new registration after fire
        triggerContext.clearRegisteredEventTimeTimers();

        // Fire at 100
        trigger.onEventTime(100, window, triggerContext);

        // Next timer should be at 200 (Math.min(100 + interval, window.maxTimestamp()))
        assertTrue(triggerContext.getRegisteredEventTimeTimers().contains(200L),
            "Next timer should be registered at 200 after firing at 100");
    }

    @Test
    public void testOnEventTimeReturnsContinueForNonFireTime() throws Exception {
        // Window: [0, 1000), maxTimestamp = 999
        TimeWindow window = new TimeWindow(0, 1000);
        long elementTimestamp = 50;

        triggerContext.setCurrentWatermark(0);
        trigger.onElement("element", elementTimestamp, window, triggerContext);

        // Trigger at non-fire time (not 100, 200, 300, etc.)
        TriggerResult result = trigger.onEventTime(150, window, triggerContext);

        assertEquals(TriggerResult.CONTINUE, result, "Should CONTINUE for non-fire time");
    }

    @Test
    public void testOnEventTimeReturnsFireAtWindowMaxTimestamp() throws Exception {
        // Window: [0, 1000), maxTimestamp = 999
        TimeWindow window = new TimeWindow(0, 1000);

        TriggerResult result = trigger.onEventTime(999, window, triggerContext);

        assertEquals(TriggerResult.FIRE, result, "Should FIRE at window maxTimestamp");
    }

    @Test
    public void testOnProcessingTimeAlwaysReturnsContinue() throws Exception {
        TimeWindow window = new TimeWindow(0, 1000);

        TriggerResult result = trigger.onProcessingTime(System.currentTimeMillis(), window, triggerContext);

        assertEquals(TriggerResult.CONTINUE, result, "Processing time should always return CONTINUE");
    }

    @Test
    public void testClearDeletesTimerAndClearsState() throws Exception {
        // Window: [0, 1000), maxTimestamp = 999
        // Element timestamp at 50, interval = 100
        // Aligned timer at: 50 - (50 % 100) = 0, + 100 = 100
        TimeWindow window = new TimeWindow(0, 1000);
        long elementTimestamp = 50;

        triggerContext.setCurrentWatermark(0);
        trigger.onElement("element", elementTimestamp, window, triggerContext);

        // Both aligned timer (100) and window.max timestamp (999) should be registered
        assertTrue(triggerContext.getRegisteredEventTimeTimers().contains(100L),
            "Aligned timer should be registered");
        assertTrue(triggerContext.getRegisteredEventTimeTimers().contains(999L),
            "Window max timestamp timer should be registered");

        trigger.clear(window, triggerContext);

        // Both timers should be deleted
        assertTrue(triggerContext.getDeletedEventTimeTimers().contains(100L),
            "Aligned timer should be deleted");
        assertTrue(triggerContext.getDeletedEventTimeTimers().contains(999L),
            "Window max timestamp timer should be deleted");
    }

    @Test
    public void testCanMergeReturnsTrue() {
        assertTrue(trigger.canMerge(), "ContinuousEventTimeTrigger should support merging");
    }

    @Test
    public void testOnMergeRegistersTimerIfStateExists() throws Exception {
        // Window: [0, 1000), maxTimestamp = 999
        TimeWindow window = new TimeWindow(0, 1000);

        // Set up state with a fire timestamp
        LongMinimum minState = new LongMinimum(300);
        triggerContext.setFireTimestampState(minState);

        trigger.onMerge(window, triggerContext);

        assertTrue(triggerContext.getRegisteredEventTimeTimers().contains(300L),
            "Timer should be registered for existing fire timestamp");
    }

    @Test
    public void testOnMergeDoesNotRegisterTimerIfStateNull() throws Exception {
        // Window: [0, 1000), maxTimestamp = 999
        TimeWindow window = new TimeWindow(0, 1000);

        // State is null by default
        trigger.onMerge(window, triggerContext);

        assertTrue(triggerContext.getRegisteredEventTimeTimers().isEmpty(),
            "No timer should be registered when state is null");
    }

    @Test
    public void testContinuousFiringAtIntervals() throws Exception {
        // Window: [0, 1000), maxTimestamp = 999
        // Element timestamp at 50, interval = 100
        // First aligned timer at: 50 - (50 % 100) = 0, + 100 = 100
        TimeWindow window = new TimeWindow(0, 1000);
        long elementTimestamp = 50;

        triggerContext.setCurrentWatermark(0);
        trigger.onElement("element", elementTimestamp, window, triggerContext);

        // Simulate continuous firing at intervals 100, 200, 300
        for (long expectedFireTime = 100; expectedFireTime <= 300; expectedFireTime += 100) {
            // Clear previous timers to check new registration
            triggerContext.clearRegisteredEventTimeTimers();

            TriggerResult result = trigger.onEventTime(expectedFireTime, window, triggerContext);
            assertEquals(TriggerResult.FIRE, result, "Should FIRE at " + expectedFireTime);

            // Verify next timer is registered (unless we're at the last interval)
            if (expectedFireTime < 999) {
                long nextExpectedFireTime = expectedFireTime + 100;
                assertTrue(triggerContext.getRegisteredEventTimeTimers().contains(nextExpectedFireTime),
                    "Next timer should be registered at " + nextExpectedFireTime);
            }
        }
    }

    @Test
    public void testTimerNotExceedsWindowMaxTimestamp() throws Exception {
        // Window: [0, 150), maxTimestamp = 149
        // Interval = 100, element timestamp at 50
        // Aligned timer at: 50 - (50 % 100) = 0, + 100 = 100
        // But window maxTimestamp is 149, so aligned timer is at 100 (since Math.min(100, 149) = 100)
        TimeWindow window = new TimeWindow(0, 150);
        long elementTimestamp = 50;

        triggerContext.setCurrentWatermark(0);
        trigger.onElement("element", elementTimestamp, window, triggerContext);

        // Should have two timers registered: aligned timer (100) and window max timestamp (149)
        assertEquals(2, triggerContext.getRegisteredEventTimeTimers().size(),
            "Should have two timers registered (aligned timer + window max)");

        assertTrue(triggerContext.getRegisteredEventTimeTimers().contains(100L),
            "Aligned timer should be at 100");
        assertTrue(triggerContext.getRegisteredEventTimeTimers().contains(149L),
            "Window max timestamp timer should be at 149");

        triggerContext.clearRegisteredEventTimeTimers();
        trigger.onEventTime(100, window, triggerContext);

        // Next timer should be at window maxTimestamp (149) since Math.min(200, 149) = 149
        assertTrue(triggerContext.getRegisteredEventTimeTimers().contains(149L),
            "Next timer should be registered at window maxTimestamp (149)");
    }

    @Test
    public void testMultipleElementsOnlyRegisterTimerOnce() throws Exception {
        // Window: [0, 1000), maxTimestamp = 999
        // First element at 50: aligned timer at 0 + 100 = 100
        // State prevents duplicate aligned timer registration, but window.max timer is registered each time
        TimeWindow window = new TimeWindow(0, 1000);

        triggerContext.setCurrentWatermark(0);

        trigger.onElement("element1", 50, window, triggerContext);
        trigger.onElement("element2", 150, window, triggerContext);
        trigger.onElement("element3", 250, window, triggerContext);

        // Mock context records all registrations
        // Aligned timer (100) is registered once (state prevents duplicates)
        // Window max timestamp (999) is registered 3 times (once per element)
        assertTrue(triggerContext.getRegisteredEventTimeTimers().contains(100L),
            "Aligned timer should be registered");
        assertTrue(triggerContext.getRegisteredEventTimeTimers().contains(999L),
            "Window max timestamp timer should be registered");
    }

    @Test
    public void testToString() {
        String str = trigger.toString();
        assertTrue(str.contains("ContinuousEventTimeTrigger"), "toString should contain trigger name");
        assertTrue(str.contains("100"), "toString should contain interval");
    }

    /**
     * Mock TriggerContext for testing
     */
    private static class MockTriggerContext implements Trigger.OnMergeContext {
        private final Map<String, SimpleAccumulator<?>> accumulators = new HashMap<>();
        private final List<Long> registeredProcessingTimeTimers = new ArrayList<>();
        private final List<Long> registeredEventTimeTimers = new ArrayList<>();
        private final List<Long> deletedProcessingTimeTimers = new ArrayList<>();
        private final List<Long> deletedEventTimeTimers = new ArrayList<>();
        private long currentProcessingTime = System.currentTimeMillis();
        private long currentWatermark = Long.MIN_VALUE;

        @Override
        public long getCurrentProcessingTime() {
            return currentProcessingTime;
        }

        @Override
        public long getCurrentWatermark() {
            return currentWatermark;
        }

        public void setCurrentWatermark(long watermark) {
            this.currentWatermark = watermark;
        }

        @Override
        public void registerProcessingTimeTimer(long time) {
            registeredProcessingTimeTimers.add(time);
        }

        @Override
        public void registerEventTimeTimer(long time) {
            registeredEventTimeTimers.add(time);
        }

        @Override
        public void deleteProcessingTimeTimer(long time) {
            deletedProcessingTimeTimers.add(time);
        }

        @Override
        public void deleteEventTimeTimer(long time) {
            deletedEventTimeTimers.add(time);
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> SimpleAccumulator<T> getSimpleAccumulator(StateDescriptor<T> descriptor) {
            String name = descriptor.getName();
            return (SimpleAccumulator<T>) accumulators.computeIfAbsent(name, k -> new LongMinimum());
        }

        public void setFireTimestampState(LongMinimum state) {
            accumulators.put("fire-time", state);
        }

        public List<Long> getRegisteredEventTimeTimers() {
            return registeredEventTimeTimers;
        }

        public List<Long> getRegisteredProcessingTimeTimers() {
            return registeredProcessingTimeTimers;
        }

        public List<Long> getDeletedEventTimeTimers() {
            return deletedEventTimeTimers;
        }

        public List<Long> getDeletedProcessingTimeTimers() {
            return deletedProcessingTimeTimers;
        }

        public void clearRegisteredEventTimeTimers() {
            registeredEventTimeTimers.clear();
        }

        public void clearRegisteredProcessingTimeTimers() {
            registeredProcessingTimeTimers.clear();
        }
    }
}
