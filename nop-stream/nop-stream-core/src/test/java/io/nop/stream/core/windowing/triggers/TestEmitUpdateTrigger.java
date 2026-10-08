/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.windowing.triggers;

import io.nop.stream.core.common.accumulators.SimpleAccumulator;
import io.nop.stream.core.common.state.StateDescriptor;
import io.nop.stream.core.windowing.windows.TimeWindow;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI37 (D11 release): the update-tier trigger fires on every element (the
 * ON_WINDOW_UPDATE shape) and once more at the window end; the cleanup timer's
 * onEventTime callback (past the window end) must NOT trigger a redundant emission.
 */
public class TestEmitUpdateTrigger {

    private EmitUpdateTrigger<String, TimeWindow> trigger;
    private MockTriggerContext triggerContext;

    @BeforeEach
    public void setUp() {
        trigger = new EmitUpdateTrigger<>();
        triggerContext = new MockTriggerContext();
    }

    @Test
    void onElementFiresAndRegistersWindowEndTimer() throws Exception {
        TimeWindow window = new TimeWindow(0, 100);
        triggerContext.setCurrentWatermark(0L);

        TriggerResult result = trigger.onElement("e1", 50L, window, triggerContext);

        assertEquals(TriggerResult.FIRE, result,
                "every window update must fire (ON_WINDOW_UPDATE semantics)");
        assertTrue(triggerContext.getRegisteredEventTimeTimers().contains(99L),
                "the window-end timer must be registered for the final emission");
    }

    @Test
    void onElementFiresImmediatelyWhenWatermarkPastWindowEnd() throws Exception {
        TimeWindow window = new TimeWindow(0, 100);
        triggerContext.setCurrentWatermark(200L);

        TriggerResult result = trigger.onElement("e1", 50L, window, triggerContext);

        assertEquals(TriggerResult.FIRE, result);
        assertFalse(triggerContext.getRegisteredEventTimeTimers().contains(99L),
                "no timer registration when the watermark already passed the window end");
    }

    @Test
    void onEventTimeFiresAtWindowEnd() {
        TimeWindow window = new TimeWindow(0, 100);

        assertEquals(TriggerResult.FIRE, trigger.onEventTime(window.maxTimestamp(), window,
                triggerContext), "the final value must be emitted at the window end");
    }

    @Test
    void onEventTimeContinuesForCleanupTimer() {
        // the WindowOperator cleanup timer (window end + allowedLateness) arrives
        // through the same trigger context — it must not cause a redundant emission
        TimeWindow window = new TimeWindow(0, 100);

        assertEquals(TriggerResult.CONTINUE, trigger.onEventTime(150L, window, triggerContext));
    }

    @Test
    void onProcessingTimeContinues() {
        TimeWindow window = new TimeWindow(0, 100);

        assertEquals(TriggerResult.CONTINUE, trigger.onProcessingTime(50L, window, triggerContext));
    }

    @Test
    void clearDeletesWindowEndTimer() {
        TimeWindow window = new TimeWindow(0, 100);
        triggerContext.registerEventTimeTimer(window.maxTimestamp());

        trigger.clear(window, triggerContext);

        assertFalse(triggerContext.getRegisteredEventTimeTimers().contains(window.maxTimestamp()));
    }

    @Test
    void supportsMerging() {
        assertTrue(trigger.canMerge(), "session/merged windows must be expressible");
    }

    @Test
    void triggerIsSerializable() throws Exception {
        // window operators serialize the trigger with the task configuration
        assertTrue(trigger instanceof java.io.Serializable,
                "triggers must be Serializable (WindowOperator field contract)");
        try (java.io.ObjectOutputStream out = new java.io.ObjectOutputStream(
                new java.io.ByteArrayOutputStream())) {
            out.writeObject(trigger);
        }
    }

    /** Minimal TriggerContext: records event-time timers, holds the current watermark. */
    private static class MockTriggerContext implements Trigger.TriggerContext {
        private final Set<Long> eventTimeTimers = new HashSet<>();
        private long currentWatermark;

        void setCurrentWatermark(long watermark) {
            this.currentWatermark = watermark;
        }

        Set<Long> getRegisteredEventTimeTimers() {
            return eventTimeTimers;
        }

        @Override
        public long getCurrentWatermark() {
            return currentWatermark;
        }

        @Override
        public long getCurrentProcessingTime() {
            return 0L;
        }

        @Override
        public void registerEventTimeTimer(long time) {
            eventTimeTimers.add(time);
        }

        @Override
        public void registerProcessingTimeTimer(long time) {
            throw new UnsupportedOperationException("not needed in this test");
        }

        @Override
        public void deleteEventTimeTimer(long time) {
            eventTimeTimers.remove(time);
        }

        @Override
        public void deleteProcessingTimeTimer(long time) {
            throw new UnsupportedOperationException("not needed in this test");
        }

        @Override
        public <T> SimpleAccumulator<T> getSimpleAccumulator(StateDescriptor<T> descriptor) {
            throw new UnsupportedOperationException("not needed in this test");
        }
    }
}
