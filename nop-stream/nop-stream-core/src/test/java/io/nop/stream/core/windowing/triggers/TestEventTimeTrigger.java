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

public class TestEventTimeTrigger {
 
    private EventTimeTrigger trigger;
    private MockTriggerContext triggerContext;
 
    @BeforeEach
    public void setUp() {
        trigger = EventTimeTrigger.create();
        triggerContext = new MockTriggerContext();
    }

 
    @Test
    public void testOnElementReturnsContinue() throws Exception {
        TimeWindow window = new TimeWindow(0, 100);
        long timestamp = 50L;

        triggerContext.setCurrentWatermark(0L);

        TriggerResult result = trigger.onElement("element", timestamp, window, triggerContext);

 
        assertEquals(TriggerResult.CONTINUE, result);
        assertTrue(triggerContext.getRegisteredEventTimeTimers().contains(99L));
    }

 
    @Test
    public void testOnElementFiresWhenWatermarkPastWindowEnd() throws Exception {
        TimeWindow window = new TimeWindow(0, 100);
        long timestamp = 50L;

        triggerContext.setCurrentWatermark(200L);

        TriggerResult result = trigger.onElement("element", timestamp, window, triggerContext);

 
        assertEquals(TriggerResult.FIRE, result);
        assertFalse(triggerContext.getRegisteredEventTimeTimers().contains(99L));
    }
 
    @Test
    public void testOnEventTimeFiresAtWindowEnd() {
        TimeWindow window = new TimeWindow(0, 100);
        long windowMaxTimestamp = window.maxTimestamp();

 
        TriggerResult result = trigger.onEventTime(windowMaxTimestamp, window, triggerContext);
 
        assertEquals(TriggerResult.FIRE, result);
    }
 
    @Test
    public void testOnEventTimeContinuesForOtherTimes() {
        TimeWindow window = new TimeWindow(0, 100);

 
        TriggerResult result = trigger.onEventTime(50L, window, triggerContext);
 
        assertEquals(TriggerResult.CONTINUE, result);
    }
 
    @Test
    public void testOnProcessingTimeReturnsContinue() {
        TimeWindow window = new TimeWindow(0, 100);
 
        TriggerResult result = trigger.onProcessingTime(System.currentTimeMillis(), window, triggerContext);
 
        assertEquals(TriggerResult.CONTINUE, result);
    }
 
    @Test
    public void testClearDeletesTimer() {
        TimeWindow window = new TimeWindow(0, 100);
        long windowMaxTimestamp = window.maxTimestamp();
 
        triggerContext.registerEventTimeTimer(windowMaxTimestamp);
        assertTrue(triggerContext.getRegisteredEventTimeTimers().contains(windowMaxTimestamp));
 
        trigger.clear(window, triggerContext);
 
        assertTrue(triggerContext.getDeletedEventTimeTimers().contains(windowMaxTimestamp));
    }
 
    @Test
    public void testCanMerge() {
        assertTrue(trigger.canMerge());
    }
 
    @Test
    public void testToString() {
        assertEquals("EventTimeTrigger()", trigger.toString());
    }
 
    @Test
    public void testCreateReturnsNewInstance() {
        EventTimeTrigger trigger1 = EventTimeTrigger.create();
        EventTimeTrigger trigger2 = EventTimeTrigger.create();
 
        assertNotNull(trigger1);
        assertNotSame(trigger1, trigger2);
    }
 
    @Test
    public void testMultipleElementsInSameWindow() throws Exception {
        TimeWindow window = new TimeWindow(0, 100);
        triggerContext.setCurrentWatermark(0L);
 
        TriggerResult result1 = trigger.onElement("element1", 10L, window, triggerContext);
        assertEquals(TriggerResult.CONTINUE, result1);
 
        TriggerResult result2 = trigger.onElement("element2", 20L, window, triggerContext);
        assertEquals(TriggerResult.CONTINUE, result2);
 
        TriggerResult result3 = trigger.onElement("element3", 30L, window, triggerContext);
        assertEquals(TriggerResult.CONTINUE, result3);
 
        assertTrue(triggerContext.getRegisteredEventTimeTimers().contains(99L));
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
