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
import io.nop.stream.core.common.state.ReducingStateDescriptor;
import io.nop.stream.core.common.state.StateDescriptor;
import io.nop.stream.core.windowing.windows.GlobalWindow;
import io.nop.stream.core.windowing.windows.TimeWindow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link PurgingTrigger}.
 */
public class TestPurgingTrigger {

    private PurgingTrigger<Object, GlobalWindow> purgingTrigger;
    private MockTriggerContext triggerContext;

    @BeforeEach
    public void setUp() {
        CountTrigger<GlobalWindow> nestedTrigger = CountTrigger.of(3);
        purgingTrigger = PurgingTrigger.of(nestedTrigger);
        triggerContext = new MockTriggerContext();
    }

    @Test
    public void testOfFactoryMethod() {
        CountTrigger<GlobalWindow> nestedTrigger = CountTrigger.of(5);
        PurgingTrigger<Object, GlobalWindow> trigger = PurgingTrigger.of(nestedTrigger);
        
        assertNotNull(trigger);
        assertSame(nestedTrigger, trigger.getNestedTrigger());
    }

    @Test
    public void testGetNestedTrigger() {
        CountTrigger<GlobalWindow> nestedTrigger = CountTrigger.of(3);
        PurgingTrigger<Object, GlobalWindow> trigger = PurgingTrigger.of(nestedTrigger);
        
        assertSame(nestedTrigger, trigger.getNestedTrigger());
    }

    @Test
    public void testOnElementReturnsContinueWhenNestedTriggerContinues() throws Exception {
        GlobalWindow window = GlobalWindow.get();
        long timestamp = System.currentTimeMillis();

        TriggerResult result = purgingTrigger.onElement("element1", timestamp, window, triggerContext);
        
        assertEquals(TriggerResult.CONTINUE, result);
    }

    @Test
    public void testOnElementReturnsFireAndPurgeWhenNestedTriggerFires() throws Exception {
        GlobalWindow window = GlobalWindow.get();
        long timestamp = System.currentTimeMillis();

        purgingTrigger.onElement("element1", timestamp, window, triggerContext);
        purgingTrigger.onElement("element2", timestamp, window, triggerContext);
        TriggerResult result = purgingTrigger.onElement("element3", timestamp, window, triggerContext);
        
        assertEquals(TriggerResult.FIRE_AND_PURGE, result);
        assertTrue(result.isFire());
        assertTrue(result.isPurge());
    }

    @Test
    public void testOnEventTimeReturnsContinueWhenNestedTriggerContinues() throws Exception {
        GlobalWindow window = GlobalWindow.get();
        long time = System.currentTimeMillis();

        TriggerResult result = purgingTrigger.onEventTime(time, window, triggerContext);
        
        assertEquals(TriggerResult.CONTINUE, result);
    }

    @Test
    public void testOnEventTimeReturnsFireAndPurgeWhenNestedTriggerFires() throws Exception {
        TimeWindow window = new TimeWindow(0, 1000);
        long time = 999;
        triggerContext.setCurrentWatermark(2000);

        EventTimeTrigger eventTimeTrigger = EventTimeTrigger.create();
        PurgingTrigger<Object, TimeWindow> trigger = PurgingTrigger.of(eventTimeTrigger);

        TriggerResult result = trigger.onEventTime(time, window, triggerContext);
        
        assertEquals(TriggerResult.FIRE_AND_PURGE, result);
    }

    @Test
    public void testOnProcessingTimeReturnsContinueWhenNestedTriggerContinues() throws Exception {
        GlobalWindow window = GlobalWindow.get();
        long time = System.currentTimeMillis();

        TriggerResult result = purgingTrigger.onProcessingTime(time, window, triggerContext);
        
        assertEquals(TriggerResult.CONTINUE, result);
    }

    @Test
    public void testOnProcessingTimeReturnsFireAndPurgeWhenNestedTriggerFires() throws Exception {
        TimeWindow window = new TimeWindow(0, 1000);
        long time = 1000;

        ProcessingTimeTrigger processingTimeTrigger = ProcessingTimeTrigger.create();
        PurgingTrigger<Object, TimeWindow> trigger = PurgingTrigger.of(processingTimeTrigger);

        TriggerResult result = trigger.onProcessingTime(time, window, triggerContext);
        
        assertEquals(TriggerResult.FIRE_AND_PURGE, result);
    }

    @Test
    public void testClearDelegatesToNestedTrigger() throws Exception {
        GlobalWindow window = GlobalWindow.get();
        long timestamp = System.currentTimeMillis();

        purgingTrigger.onElement("element1", timestamp, window, triggerContext);
        purgingTrigger.onElement("element2", timestamp, window, triggerContext);
        
        assertEquals(2L, triggerContext.getCount());

        purgingTrigger.clear(window, triggerContext);
        
        assertEquals(0L, triggerContext.getCount());
    }

    @Test
    public void testCanMergeDelegatesToNestedTrigger() {
        assertTrue(purgingTrigger.canMerge());

        EventTimeTrigger eventTimeTrigger = EventTimeTrigger.create();
        PurgingTrigger<Object, TimeWindow> trigger = PurgingTrigger.of(eventTimeTrigger);

        assertTrue(trigger.canMerge());
    }

    @Test
    public void testOnMergeDelegatesToNestedTrigger() throws Exception {
        GlobalWindow window = GlobalWindow.get();
        
        assertDoesNotThrow(() -> purgingTrigger.onMerge(window, triggerContext));
    }

    @Test
    public void testToString() {
        String str = purgingTrigger.toString();
        
        assertTrue(str.contains("PurgingTrigger"));
        assertTrue(str.contains("CountTrigger"));
    }

    @Test
    public void testMultipleFiresAllConvertToFireAndPurge() throws Exception {
        GlobalWindow window = GlobalWindow.get();
        long timestamp = System.currentTimeMillis();

        for (int i = 1; i <= 3; i++) {
            TriggerResult result = purgingTrigger.onElement("element" + i, timestamp, window, triggerContext);
            if (i == 3) {
                assertEquals(TriggerResult.FIRE_AND_PURGE, result);
            } else {
                assertEquals(TriggerResult.CONTINUE, result);
            }
        }

        for (int i = 4; i <= 6; i++) {
            TriggerResult result = purgingTrigger.onElement("element" + i, timestamp, window, triggerContext);
            if (i == 6) {
                assertEquals(TriggerResult.FIRE_AND_PURGE, result);
            } else {
                assertEquals(TriggerResult.CONTINUE, result);
            }
        }
    }

    private static class MockTriggerContext implements Trigger.OnMergeContext {
        private final Map<String, SimpleAccumulator<?>> accumulators = new HashMap<>();
        private long currentProcessingTime = System.currentTimeMillis();
        private long currentWatermark = Long.MIN_VALUE;

        public void setCurrentWatermark(long watermark) {
            this.currentWatermark = watermark;
        }

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
            return (SimpleAccumulator<T>) accumulators.computeIfAbsent(name, k -> {
                if (descriptor instanceof ReducingStateDescriptor) {
                    ReducingStateDescriptor<T> reducingDesc = (ReducingStateDescriptor<T>) descriptor;
                    Class<? extends SimpleAccumulator<T>> accumulatorType = reducingDesc.getAccumulatorType();
                    try {
                        return accumulatorType.getDeclaredConstructor().newInstance();
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to create accumulator instance", e);
                    }
                }
                return new LongCounter();
            });
        }

        public long getCount() {
            LongCounter counter = (LongCounter) accumulators.get("count");
            return counter != null ? counter.getLocalValuePrimitive() : 0L;
        }
    }
}
