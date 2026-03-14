/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.windowing;

import io.nop.stream.core.common.accumulators.LongCounter;
import io.nop.stream.core.common.accumulators.SimpleAccumulator;
import io.nop.stream.core.common.state.StateDescriptor;
import io.nop.stream.core.windowing.triggers.CountTrigger;
import io.nop.stream.core.windowing.triggers.Trigger;
import io.nop.stream.core.windowing.triggers.TriggerResult;
import io.nop.stream.core.windowing.windows.GlobalWindow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end test for windowing functionality.
 * 
 * <p>This test demonstrates the basic windowing flow:
 * <ol>
 *   <li>Elements arrive at the window</li>
 *   <li>Trigger tracks element count</li>
 *   <li>Window fires when count threshold is reached</li>
 *   <li>Results are collected</li>
 * </ol>
 */
public class TestWindowEndToEnd {

    private CountTrigger<GlobalWindow> trigger;
    private MockTriggerContext triggerContext;
    private static final long MAX_COUNT = 3;

    @BeforeEach
    public void setUp() {
        trigger = CountTrigger.of(MAX_COUNT);
        triggerContext = new MockTriggerContext();
    }

    /**
     * Test that window fires when count threshold is reached.
     */
    @Test
    public void testCountWindowFires() throws Exception {
        GlobalWindow window = GlobalWindow.get();
        long timestamp = System.currentTimeMillis();

        // First element - should continue
        TriggerResult result1 = trigger.onElement("element1", timestamp, window, triggerContext);
        assertEquals(TriggerResult.CONTINUE, result1, "First element should continue");
        assertEquals(1L, triggerContext.getCount(), "Count should be 1");

        // Second element - should continue
        TriggerResult result2 = trigger.onElement("element2", timestamp, window, triggerContext);
        assertEquals(TriggerResult.CONTINUE, result2, "Second element should continue");
        assertEquals(2L, triggerContext.getCount(), "Count should be 2");

        // Third element - should fire (reaches maxCount)
        TriggerResult result3 = trigger.onElement("element3", timestamp, window, triggerContext);
        assertEquals(TriggerResult.FIRE, result3, "Third element should fire the window");
        assertEquals(0L, triggerContext.getCount(), "Count should be reset to 0 after fire");
    }

    /**
     * Test that window continues to fire after reset.
     */
    @Test
    public void testCountWindowFiresMultipleTimes() throws Exception {
        GlobalWindow window = GlobalWindow.get();
        long timestamp = System.currentTimeMillis();

        // First batch: 3 elements
        for (int i = 1; i <= 3; i++) {
            TriggerResult result = trigger.onElement("element" + i, timestamp, window, triggerContext);
            if (i == 3) {
                assertEquals(TriggerResult.FIRE, result, "Element " + i + " should fire");
            } else {
                assertEquals(TriggerResult.CONTINUE, result, "Element " + i + " should continue");
            }
        }

        // Second batch: 3 more elements
        for (int i = 4; i <= 6; i++) {
            TriggerResult result = trigger.onElement("element" + i, timestamp, window, triggerContext);
            if (i == 6) {
                assertEquals(TriggerResult.FIRE, result, "Element " + i + " should fire second batch");
            } else {
                assertEquals(TriggerResult.CONTINUE, result, "Element " + i + " should continue");
            }
        }
    }

    /**
     * Test trigger clear functionality.
     */
    @Test
    public void testTriggerClear() throws Exception {
        GlobalWindow window = GlobalWindow.get();
        long timestamp = System.currentTimeMillis();

        // Add some elements
        trigger.onElement("element1", timestamp, window, triggerContext);
        trigger.onElement("element2", timestamp, window, triggerContext);

        assertEquals(2L, triggerContext.getCount(), "Count should be 2 before clear");

        // Clear the trigger state
        trigger.clear(window, triggerContext);

        assertEquals(0L, triggerContext.getCount(), "Count should be 0 after clear");
    }

    /**
     * Test that processing time trigger does nothing for CountTrigger.
     */
    @Test
    public void testProcessingTimeTriggerDoesNothing() throws Exception {
        GlobalWindow window = GlobalWindow.get();

        // Add an element first
        trigger.onElement("element1", System.currentTimeMillis(), window, triggerContext);
        assertEquals(1L, triggerContext.getCount(), "Count should be 1");

        // Processing time trigger should not affect count-based trigger
        TriggerResult result = trigger.onProcessingTime(System.currentTimeMillis(), window, triggerContext);
        assertEquals(TriggerResult.CONTINUE, result, "Processing time should continue");
        assertEquals(1L, triggerContext.getCount(), "Count should still be 1");
    }

    /**
     * Test that event time trigger does nothing for CountTrigger.
     */
    @Test
    public void testEventTimeTriggerDoesNothing() throws Exception {
        GlobalWindow window = GlobalWindow.get();

        // Add an element first
        trigger.onElement("element1", System.currentTimeMillis(), window, triggerContext);
        assertEquals(1L, triggerContext.getCount(), "Count should be 1");

        // Event time trigger should not affect count-based trigger
        TriggerResult result = trigger.onEventTime(System.currentTimeMillis(), window, triggerContext);
        assertEquals(TriggerResult.CONTINUE, result, "Event time should continue");
        assertEquals(1L, triggerContext.getCount(), "Count should still be 1");
    }

    /**
     * Test trigger can merge (CountTrigger supports merging).
     */
    @Test
    public void testTriggerCanMerge() {
        assertTrue(trigger.canMerge(), "CountTrigger should support merging");
    }

    /**
     * Simulated end-to-end window aggregation test.
     * This demonstrates Source -> Count Window -> Sink flow.
     */
    @Test
    public void testEndToEndWindowAggregation() throws Exception {
        GlobalWindow window = GlobalWindow.get();
        long timestamp = System.currentTimeMillis();

        // Simulate source: emit 10 elements
        String[] sourceData = {"a", "b", "c", "d", "e", "f", "g", "h", "i", "j"};

        // Track window fires
        int windowFires = 0;
        StringBuilder collectedResults = new StringBuilder();

        for (int i = 0; i < sourceData.length; i++) {
            String element = sourceData[i];
            TriggerResult result = trigger.onElement(element, timestamp, window, triggerContext);

            if (result.isFire()) {
                windowFires++;
                // Simulate collecting window result
                collectedResults.append("[Window ").append(windowFires).append(" fired at element ")
                        .append(i + 1).append("] ");
            }
        }

        // Verify: with maxCount=3 and 10 elements, we should have 3 window fires
        // (elements 3, 6, 9 trigger fires, element 10 remains in state)
        assertEquals(3, windowFires, "Should have 3 window fires for 10 elements with maxCount=3");
        assertTrue(collectedResults.toString().contains("fired at element 3"), "First fire at element 3");
        assertTrue(collectedResults.toString().contains("fired at element 6"), "Second fire at element 6");
        assertTrue(collectedResults.toString().contains("fired at element 9"), "Third fire at element 9");

        // Remaining element count should be 1 (element 10)
        assertEquals(1L, triggerContext.getCount(), "One element should remain in state");
    }

    /**
     * Mock TriggerContext for testing.
     * Provides a simple in-memory accumulator storage.
     */
    private static class MockTriggerContext implements Trigger.TriggerContext {
        private final Map<String, SimpleAccumulator<?>> accumulators = new HashMap<>();
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

        @Override
        public void registerProcessingTimeTimer(long time) {
            // Not implemented for this test
        }

        @Override
        public void registerEventTimeTimer(long time) {
            // Not implemented for this test
        }

        @Override
        public void deleteProcessingTimeTimer(long time) {
            // Not implemented for this test
        }

        @Override
        public void deleteEventTimeTimer(long time) {
            // Not implemented for this test
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> SimpleAccumulator<T> getSimpleAccumulator(StateDescriptor<T> descriptor) {
            String name = descriptor.getName();
            return (SimpleAccumulator<T>) accumulators.computeIfAbsent(name, 
                k -> new LongCounter());
        }

        /**
         * Get current count value for testing.
         */
        public long getCount() {
            LongCounter counter = (LongCounter) accumulators.get("count");
            return counter != null ? counter.getLocalValuePrimitive() : 0L;
        }
    }
}
