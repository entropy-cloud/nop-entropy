/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.windowing.triggers;

 
import io.nop.stream.core.common.accumulators.LongCounter;
import io.nop.stream.core.common.accumulators.SimpleAccumulator;
import io.nop.stream.core.common.state.StateDescriptor;
import io.nop.stream.core.windowing.windows.TimeWindow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
 
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
 
import static org.junit.jupiter.api.Assertions.*;

public class TestProcessingTimeTrigger {
 
    private ProcessingTimeTrigger trigger;
    private MockTriggerContext triggerContext;
 
    @BeforeEach
    public void setUp() {
        trigger = ProcessingTimeTrigger.create();
        triggerContext = new MockTriggerContext();
    }
 
    @Test
    public void testOnElementReturnsContinueAndRegistersTimer() throws Exception {
        TimeWindow window = new TimeWindow(0, 100);
        long timestamp = 50L;
 
        TriggerResult result = trigger.onElement("element", timestamp, window, triggerContext);
 
        assertEquals(TriggerResult.CONTINUE, result);
        assertTrue(triggerContext.getRegisteredProcessingTimeTimers().contains(99L));
    }
 
    @Test
    public void testOnProcessingTimeFires() {
        TimeWindow window = new TimeWindow(0, 100);
        long windowMaxTimestamp = window.maxTimestamp();
 
        TriggerResult result = trigger.onProcessingTime(windowMaxTimestamp, window, triggerContext);
 
        assertEquals(TriggerResult.FIRE, result);
    }
 
    @Test
    public void testOnProcessingTimeFiresForAnyTime() {
        TimeWindow window = new TimeWindow(0, 100);
 
        TriggerResult result = trigger.onProcessingTime(50L, window, triggerContext);
 
        assertEquals(TriggerResult.FIRE, result);
    }
 
    @Test
    public void testOnEventTimeReturnsContinue() {
        TimeWindow window = new TimeWindow(0, 100);
 
        TriggerResult result = trigger.onEventTime(window.maxTimestamp(), window, triggerContext);
 
        assertEquals(TriggerResult.CONTINUE, result);
    }
 
    @Test
    public void testClearDeletesTimer() {
        TimeWindow window = new TimeWindow(0, 100);
        long windowMaxTimestamp = window.maxTimestamp();
 
        triggerContext.registerProcessingTimeTimer(windowMaxTimestamp);
        assertTrue(triggerContext.getRegisteredProcessingTimeTimers().contains(windowMaxTimestamp));
 
        trigger.clear(window, triggerContext);
 
        assertTrue(triggerContext.getDeletedProcessingTimeTimers().contains(windowMaxTimestamp));
    }
 
    @Test
    public void testCanMerge() {
        assertTrue(trigger.canMerge());
    }
 
    @Test
    public void testToString() {
        assertEquals("ProcessingTimeTrigger()", trigger.toString());
    }
 
    @Test
    public void testCreateReturnsNewInstance() {
        ProcessingTimeTrigger trigger1 = ProcessingTimeTrigger.create();
        ProcessingTimeTrigger trigger2 = ProcessingTimeTrigger.create();
 
        assertNotNull(trigger1);
        assertNotSame(trigger1, trigger2);
    }
 
    @Test
    public void testMultipleElementsInSameWindow() throws Exception {
        TimeWindow window = new TimeWindow(0, 100);
 
        trigger.onElement("element1", 10L, window, triggerContext);
        trigger.onElement("element2", 20L, window, triggerContext);
        trigger.onElement("element3", 30L, window, triggerContext);
 
        assertTrue(triggerContext.getRegisteredProcessingTimeTimers().contains(99L));
    }
 
    private static class MockTriggerContext implements Trigger.TriggerContext {
        private final Map<String, SimpleAccumulator<?>> accumulators = new HashMap<>();
        private final Set<Long> registeredEventTimeTimers = new HashSet<>();
        private final Set<Long> deletedEventTimeTimers = new HashSet<>();
        private final Set<Long> registeredProcessingTimeTimers = new HashSet<>();
        private final Set<Long> deletedProcessingTimeTimers = new HashSet<>();
        private long currentProcessingTime = System.currentTimeMillis();
        private long currentWatermark = Long.MIN_VALUE;

        @Override
        public long getCurrentProcessingTime() {
            return currentProcessingTime;
        }

        public void setCurrentProcessingTime(long time) {
            this.currentProcessingTime = time;
        }

        @Override
        public long getCurrentWatermark() {
            return currentWatermark;
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
            registeredProcessingTimeTimers.remove(time);
            deletedProcessingTimeTimers.add(time);
        }
 
        @Override
        public void deleteEventTimeTimer(long time) {
            registeredEventTimeTimers.remove(time);
            deletedEventTimeTimers.add(time);
        }
 
        @SuppressWarnings("unchecked")
        @Override
        public <T> SimpleAccumulator<T> getSimpleAccumulator(StateDescriptor<T> descriptor) {
            String name = descriptor.getName();
            return (SimpleAccumulator<T>) accumulators.computeIfAbsent(name,
                    k -> new LongCounter());
        }
 
        public Set<Long> getRegisteredEventTimeTimers() {
            return registeredEventTimeTimers;
        }
 
        public Set<Long> getDeletedEventTimeTimers() {
            return deletedEventTimeTimers;
        }
 
        public Set<Long> getRegisteredProcessingTimeTimers() {
            return registeredProcessingTimeTimers;
        }
 
        public Set<Long> getDeletedProcessingTimeTimers() {
            return deletedProcessingTimeTimers;
        }
    }
}
