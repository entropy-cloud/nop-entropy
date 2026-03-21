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
 * Unit tests for {@link ContinuousProcessingTimeTrigger}.
 */
public class TestContinuousProcessingTimeTrigger {

    private ContinuousProcessingTimeTrigger<TimeWindow> trigger;
    private MockTriggerContext triggerContext;

    @BeforeEach
    public void setUp() {
        trigger = ContinuousProcessingTimeTrigger.of(Duration.ofMillis(100));
        triggerContext = new MockTriggerContext();
    }

    @Test
    public void testOfFactoryMethod() {
        ContinuousProcessingTimeTrigger<TimeWindow> t = ContinuousProcessingTimeTrigger.of(Duration.ofMillis(500));
        assertNotNull(t, "Trigger should be created");
        assertEquals(500L, t.getInterval(), "Interval should be 500");
    }

    @Test
    public void testGetInterval() {
        assertEquals(100L, trigger.getInterval(), "Interval should be 100");
    }

    @Test
    public void testOnElementReturnsContinue() throws Exception {
        // Window: [0, 1000), maxTimestamp = 999
        TimeWindow window = new TimeWindow(0, 1000);

        triggerContext.setCurrentProcessingTime(50);

        TriggerResult result = trigger.onElement("element", 50, window, triggerContext);

        assertEquals(TriggerResult.CONTINUE, result, "Should return CONTINUE");
    }

    @Test
    public void testOnElementRegistersProcessingTimeTimer() throws Exception {
        // Window: [0, 1000), maxTimestamp = 999
        TimeWindow window = new TimeWindow(0, 1000);

        triggerContext.setCurrentProcessingTime(50);

        trigger.onElement("element", 50, window, triggerContext);

        // Both timers should be registered: aligned timer (100) and window.max timestamp (999)
        assertTrue(triggerContext.getRegisteredProcessingTimeTimers().contains(100L),
            "Aligned timer should be registered at 100");
        assertTrue(triggerContext.getRegisteredProcessingTimeTimers().contains(999L),
            "Window max timestamp timer should be registered at 999");
    }

    @Test
    public void testOnElementOnlyRegistersTimerOnce() throws Exception {
        // Window: [0, 1000), maxTimestamp = 999
        // First element at 50: aligned timer at 0 + 100 = 100
        // State prevents duplicate aligned timer registration, but window.max timer is registered each time
        TimeWindow window = new TimeWindow(0, 1000);

        triggerContext.setCurrentProcessingTime(50);

        trigger.onElement("element1", 50, window, triggerContext);
        trigger.onElement("element2", 150, window, triggerContext);
        trigger.onElement("element3", 250, window, triggerContext);

        // Aligned timer (100) is registered once (state prevents duplicates)
        // Window max timestamp (999) is registered 3 times (once per element)
        assertTrue(triggerContext.getRegisteredProcessingTimeTimers().contains(100L),
            "Aligned timer should be registered");
        assertTrue(triggerContext.getRegisteredProcessingTimeTimers().contains(999L),
            "Window max timestamp timer should be registered");
    }

    @Test
    public void testOnProcessingTimeReturnsFireAtIntervalBoundary() throws Exception {
        // Window: [0, 1000), maxTimestamp = 999
        // Element timestamp at 50, interval = 100
        // First aligned timer at: 50 - (50 % 100) = 0, + 100 = 100
        TimeWindow window = new TimeWindow(0, 1000);

        triggerContext.setCurrentProcessingTime(50);
        trigger.onElement("element", 50, window, triggerContext);

        // Clear timers list
        triggerContext.clearRegisteredProcessingTimeTimers();

        // Fire at interval time
        long fireTime = 100L;
        TriggerResult result = trigger.onProcessingTime(fireTime, window, triggerContext);
        assertEquals(TriggerResult.FIRE, result, "Should FIRE at interval time " + fireTime);

        // Verify next timer is registered at 200 (aligned timer)
        assertTrue(triggerContext.getRegisteredProcessingTimeTimers().contains(200L),
            "Next aligned timer should be registered at 200");
    }
    @Test
    public void testOnProcessingTimeReturnsContinueAtNonFireTime() throws Exception {
        // Window: [0, 1000), maxTimestamp = 999
        TimeWindow window = new TimeWindow(0, 1000);

        triggerContext.setCurrentProcessingTime(50);
        trigger.onElement("element", 50, window, triggerContext);

        // Clear timers
        triggerContext.clearRegisteredProcessingTimeTimers();

        // Non-fire time (not matching state)
        TriggerResult result = trigger.onProcessingTime(50L, window, triggerContext);
        assertEquals(TriggerResult.CONTINUE, result, "Should CONTINUE at non-fire time");
    }
    @Test
    public void testOnEventTimeAlwaysReturnsContinue() throws Exception {
        TimeWindow window = new TimeWindow(0, 1000);

        TriggerResult result = trigger.onEventTime(System.currentTimeMillis(), window, triggerContext);
        assertEquals(TriggerResult.CONTINUE, result, "Event time should always return CONTINUE");
    }
    @Test
    public void testClearDeletesTimerAndClearsState() throws Exception {
        // Window: [0, 1000), maxTimestamp = 999
        // Element timestamp at 50, interval = 100
        // Aligned timer at: 50 - (50 % 100) = 0, + 100 = 100
        TimeWindow window = new TimeWindow(0, 1000);

        triggerContext.setCurrentProcessingTime(50);
        trigger.onElement("element", 50, window, triggerContext);

        // Both aligned timer (100) and window.max timestamp (999) should be registered
        assertTrue(triggerContext.getRegisteredProcessingTimeTimers().contains(100L),
            "Aligned timer should be registered");
        assertTrue(triggerContext.getRegisteredProcessingTimeTimers().contains(999L),
            "Window max timestamp timer should be registered");

        trigger.clear(window, triggerContext);

        // Both timers should be deleted
        assertTrue(triggerContext.getDeletedProcessingTimeTimers().contains(100L),
            "Aligned timer should be deleted");
        assertTrue(triggerContext.getDeletedProcessingTimeTimers().contains(999L),
            "Window max timestamp timer should be deleted");
    }
    @Test
    public void testCanMergeReturnsTrue() {
        assertTrue(trigger.canMerge(), "ContinuousProcessingTimeTrigger should support merging");
    }
    @Test
    public void testOnMergeRegistersTimerIfStateExists() throws Exception {
        // Window: [0, 1000), maxTimestamp = 999
        TimeWindow window = new TimeWindow(0, 1000);

        // Set up state with a fire timestamp
        LongMinimum minState = new LongMinimum(300);
        triggerContext.setFireTimestampState(minState);

        trigger.onMerge(window, triggerContext);

        assertTrue(triggerContext.getRegisteredProcessingTimeTimers().contains(300L),
            "Timer should be registered for existing fire timestamp");
    }
    @Test
    public void testOnMergeDoesNotRegisterTimerIfStateUninitialized() throws Exception {
        // Window: [0, 1000), maxTimestamp = 999
        TimeWindow window = new TimeWindow(0, 1000);

        // State is uninitialized (Long.MAX_VALUE) by default
        trigger.onMerge(window, triggerContext);

        // No aligned timer should be registered when state is uninitialized
        // (Long.MAX_VALUE is treated as uninitialized)
        assertFalse(triggerContext.getRegisteredProcessingTimeTimers().contains(Long.MAX_VALUE),
            "No aligned timer should be registered when state is uninitialized");
    }
    @Test
    public void testContinuousFiringAtIntervals() throws Exception {
        // Window: [0, 1000), maxTimestamp = 999
        // Element timestamp at 50, interval = 100
        // First aligned timer at: 50 - (50 % 100) = 0, + 100 = 100
        TimeWindow window = new TimeWindow(0, 1000);

        triggerContext.setCurrentProcessingTime(50);
        trigger.onElement("element", 50, window, triggerContext);

        // Simulate continuous firing at intervals 100, 200, 300
        for (long expectedFireTime = 100; expectedFireTime <= 300; expectedFireTime += 100) {
            // Clear previous timers to check new registration
            triggerContext.clearRegisteredProcessingTimeTimers();

            TriggerResult result = trigger.onProcessingTime(expectedFireTime, window, triggerContext);
            assertEquals(TriggerResult.FIRE, result, "Should FIRE at " + expectedFireTime);

            // Verify next timer is registered (unless we're at the last interval)
            if (expectedFireTime < 999) {
                long nextExpectedFireTime = expectedFireTime + 100;
                assertTrue(triggerContext.getRegisteredProcessingTimeTimers().contains(nextExpectedFireTime),
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

        triggerContext.setCurrentProcessingTime(50);
        trigger.onElement("element", 50, window, triggerContext);

        // Should have two timers registered: aligned timer (100) and window max timestamp (149)
        assertEquals(2, triggerContext.getRegisteredProcessingTimeTimers().size(),
            "Should have two timers registered (aligned timer + window max)");

        assertTrue(triggerContext.getRegisteredProcessingTimeTimers().contains(100L),
            "Aligned timer should be at 100");
        assertTrue(triggerContext.getRegisteredProcessingTimeTimers().contains(149L),
            "Window max timestamp timer should be at 149");

        triggerContext.clearRegisteredProcessingTimeTimers();
        trigger.onProcessingTime(100, window, triggerContext);

        // Next timer should be at window maxTimestamp (149) since Math.min(200, 149) = 149
        assertTrue(triggerContext.getRegisteredProcessingTimeTimers().contains(149L),
            "Next timer should be registered at window maxTimestamp (149)");
    }
    @Test
    public void testToString() {
        String str = trigger.toString();
        assertTrue(str.contains("ContinuousProcessingTimeTrigger"), "toString should contain trigger name");
        assertTrue(str.contains("100"), "toString should contain interval");
    }

    @Test
    public void testProcessingTimeAlignsWithInterval() throws Exception {
        // Test that processing time is properly aligned to interval boundaries
        // processing time = 200, interval = 100
        // Aligned base time: 200 - (200 % 100) = 200
        // Next fire time: min(200 + 100, 999) = 300
        long alignedTime = 200;
        triggerContext.setCurrentProcessingTime(alignedTime);
        trigger.onElement("element", alignedTime, new TimeWindow(0, 1000), triggerContext);

        // Both timers should be registered: aligned timer (300) and window.max timestamp (999)
        assertTrue(triggerContext.getRegisteredProcessingTimeTimers().contains(300L),
            "First timer should be at 300 (aligned boundary + interval)");
        assertTrue(triggerContext.getRegisteredProcessingTimeTimers().contains(999L),
            "Window max timestamp timer should be registered at 999");
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
 
        public void setCurrentProcessingTime(long time) {
            this.currentProcessingTime = time;
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
 
        public List<Long> getRegisteredProcessingTimeTimers() {
            return registeredProcessingTimeTimers;
        }
 
        public List<Long> getRegisteredEventTimeTimers() {
            return registeredEventTimeTimers;
        }
 
        public List<Long> getDeletedProcessingTimeTimers() {
            return deletedProcessingTimeTimers;
        }
 
        public List<Long> getDeletedEventTimeTimers() {
            return deletedEventTimeTimers;
        }
 
        public void clearRegisteredProcessingTimeTimers() {
            registeredProcessingTimeTimers.clear();
        }
 
        public void clearRegisteredEventTimeTimers() {
            registeredEventTimeTimers.clear();
        }
    }
}
