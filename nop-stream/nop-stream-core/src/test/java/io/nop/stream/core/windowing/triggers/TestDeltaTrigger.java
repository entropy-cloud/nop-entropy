/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.windowing.triggers;

import io.nop.stream.core.common.accumulators.LastValue;
import io.nop.stream.core.common.accumulators.SimpleAccumulator;
import io.nop.stream.core.common.state.StateDescriptor;
import io.nop.stream.core.windowing.delta.DeltaFunction;
import io.nop.stream.core.windowing.windows.GlobalWindow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link DeltaTrigger}.
 */
public class TestDeltaTrigger {

    private static final double THRESHOLD = 10.0;

    private DeltaTrigger<Double, GlobalWindow> trigger;
    private MockTriggerContext triggerContext;

    @BeforeEach
    public void setUp() {
        DeltaFunction<Double> deltaFunction = (oldVal, newVal) -> Math.abs(newVal - oldVal);
        trigger = DeltaTrigger.of(THRESHOLD, deltaFunction, Double.class);
        triggerContext = new MockTriggerContext();
    }

    @Test
    public void testOfFactoryMethod() {
        DeltaFunction<Double> deltaFunction = (oldVal, newVal) -> Math.abs(newVal - oldVal);
        DeltaTrigger<Double, GlobalWindow> t = DeltaTrigger.of(5.0, deltaFunction, Double.class);
        
        assertNotNull(t, "DeltaTrigger should be created");
    }

    @Test
    public void testFirstElementContinues() throws Exception {
        GlobalWindow window = GlobalWindow.get();
        long timestamp = System.currentTimeMillis();

        TriggerResult result = trigger.onElement(5.0, timestamp, window, triggerContext);
        
        assertEquals(TriggerResult.CONTINUE, result, "First element should continue");
        assertEquals(5.0, triggerContext.getLastValue(), "First element should be stored");
    }

    @Test
    public void testFiresWhenDeltaExceedsThreshold() throws Exception {
        GlobalWindow window = GlobalWindow.get();
        long timestamp = System.currentTimeMillis();

        trigger.onElement(5.0, timestamp, window, triggerContext);
        
        TriggerResult result = trigger.onElement(20.0, timestamp, window, triggerContext);
        
        assertEquals(TriggerResult.FIRE, result, "Should fire when delta (15.0) exceeds threshold (10.0)");
        assertEquals(20.0, triggerContext.getLastValue(), "New element should be stored after fire");
    }

    @Test
    public void testContinuesWhenDeltaBelowThreshold() throws Exception {
        GlobalWindow window = GlobalWindow.get();
        long timestamp = System.currentTimeMillis();

        trigger.onElement(5.0, timestamp, window, triggerContext);
        
        TriggerResult result = trigger.onElement(12.0, timestamp, window, triggerContext);
        
        assertEquals(TriggerResult.CONTINUE, result, "Should continue when delta (7.0) is below threshold (10.0)");
        assertEquals(5.0, triggerContext.getLastValue(), "State should remain 5.0 when delta below threshold");
    }

    @Test
    public void testContinuesWhenDeltaEqualsThreshold() throws Exception {
        GlobalWindow window = GlobalWindow.get();
        long timestamp = System.currentTimeMillis();

        trigger.onElement(5.0, timestamp, window, triggerContext);
        
        TriggerResult result = trigger.onElement(15.0, timestamp, window, triggerContext);
        
        assertEquals(TriggerResult.CONTINUE, result, "Should continue when delta (10.0) equals threshold (10.0)");
        assertEquals(5.0, triggerContext.getLastValue(), "State should remain 5.0 when delta equals threshold");
    }

    @Test
    public void testMultipleElementsFiringMultipleTimes() throws Exception {
        GlobalWindow window = GlobalWindow.get();
        long timestamp = System.currentTimeMillis();

        assertEquals(TriggerResult.CONTINUE, 
            trigger.onElement(0.0, timestamp, window, triggerContext));
        assertEquals(TriggerResult.CONTINUE, 
            trigger.onElement(5.0, timestamp, window, triggerContext));
        assertEquals(TriggerResult.FIRE, 
            trigger.onElement(20.0, timestamp, window, triggerContext));
        assertEquals(TriggerResult.CONTINUE, 
            trigger.onElement(25.0, timestamp, window, triggerContext));
        assertEquals(TriggerResult.FIRE, 
            trigger.onElement(50.0, timestamp, window, triggerContext));
    }

    @Test
    public void testEventTimeAlwaysContinues() throws Exception {
        GlobalWindow window = GlobalWindow.get();

        TriggerResult result = trigger.onEventTime(System.currentTimeMillis(), window, triggerContext);
        
        assertEquals(TriggerResult.CONTINUE, result, "Event time should always continue");
    }

    @Test
    public void testProcessingTimeAlwaysContinues() throws Exception {
        GlobalWindow window = GlobalWindow.get();

        TriggerResult result = trigger.onProcessingTime(System.currentTimeMillis(), window, triggerContext);
        
        assertEquals(TriggerResult.CONTINUE, result, "Processing time should always continue");
    }

    @Test
    public void testClearResetsState() throws Exception {
        GlobalWindow window = GlobalWindow.get();
        long timestamp = System.currentTimeMillis();

        trigger.onElement(5.0, timestamp, window, triggerContext);
        assertNotNull(triggerContext.getLastValue(), "State should have value before clear");

        trigger.clear(window, triggerContext);
        
        assertNull(triggerContext.getLastValue(), "State should be null after clear");
    }

    @Test
    public void testToString() {
        String str = trigger.toString();
        
        assertTrue(str.contains("DeltaTrigger"), "toString should contain DeltaTrigger");
        assertTrue(str.contains(String.valueOf(THRESHOLD)), "toString should contain threshold");
    }

    @Test
    public void testNegativeValues() throws Exception {
        GlobalWindow window = GlobalWindow.get();
        long timestamp = System.currentTimeMillis();

        trigger.onElement(-10.0, timestamp, window, triggerContext);
        
        TriggerResult result = trigger.onElement(5.0, timestamp, window, triggerContext);
        
        assertEquals(TriggerResult.FIRE, result, "Should fire with delta from -10.0 to 5.0 (delta=15.0 > threshold=10.0)");
    }

    @Test
    public void testZeroThresholdAlwaysFires() throws Exception {
        DeltaFunction<Double> deltaFunction = (oldVal, newVal) -> Math.abs(newVal - oldVal);
        DeltaTrigger<Double, GlobalWindow> zeroThresholdTrigger = 
            DeltaTrigger.of(0.0, deltaFunction, Double.class);
        MockTriggerContext ctx = new MockTriggerContext();
        GlobalWindow window = GlobalWindow.get();
        long timestamp = System.currentTimeMillis();

        zeroThresholdTrigger.onElement(5.0, timestamp, window, ctx);
        
        TriggerResult result = zeroThresholdTrigger.onElement(5.0001, timestamp, window, ctx);
        
        assertEquals(TriggerResult.FIRE, result, "Should fire with zero threshold when any delta > 0");
    }

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
        }

        @Override
        public void registerEventTimeTimer(long time) {
        }

        @Override
        public void deleteProcessingTimeTimer(long time) {
        }

        @Override
        public void deleteEventTimeTimer(long time) {
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> SimpleAccumulator<T> getSimpleAccumulator(StateDescriptor<T> descriptor) {
            String name = descriptor.getName();
            return (SimpleAccumulator<T>) accumulators.computeIfAbsent(name, 
                k -> new LastValue<T>());
        }

        @SuppressWarnings("unchecked")
        public Double getLastValue() {
            LastValue<Double> accumulator = (LastValue<Double>) accumulators.get("last-element");
            return accumulator != null ? accumulator.getLocalValue() : null;
        }
    }
}
