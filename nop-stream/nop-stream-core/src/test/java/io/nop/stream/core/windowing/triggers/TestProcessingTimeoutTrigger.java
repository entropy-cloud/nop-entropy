package io.nop.stream.core.windowing.triggers;

import io.nop.api.core.exceptions.NopException;
import io.nop.stream.core.common.accumulators.LongCounter;
import io.nop.stream.core.common.accumulators.SimpleAccumulator;
import io.nop.stream.core.common.state.ReducingStateDescriptor;
import io.nop.stream.core.common.state.StateDescriptor;
import io.nop.stream.core.windowing.windows.TimeWindow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class TestProcessingTimeoutTrigger {

    private MockTriggerContext ctx;

    @BeforeEach
    void setUp() {
        ctx = new MockTriggerContext();
    }

    @Test
    void testTimeoutFiresOnProcessingTime() throws Exception {
        ProcessingTimeoutTrigger<Object, TimeWindow> trigger =
                ProcessingTimeoutTrigger.of(
                        ProcessingTimeTrigger.create(),
                        Duration.ofMillis(100));

        TimeWindow window = new TimeWindow(0, 1000);
        TriggerResult onElementResult = trigger.onElement("a", 0, window, ctx);
        assertEquals(TriggerResult.CONTINUE, onElementResult);
        assertFalse(ctx.registeredProcessingTimers.isEmpty());

        long timerTs = ctx.registeredProcessingTimers.iterator().next();
        TriggerResult timerResult = trigger.onProcessingTime(timerTs, window, ctx);
        assertEquals(TriggerResult.FIRE, timerResult);
    }

    @Test
    void testNestedTriggerFiresBeforeTimeout() throws Exception {
        CountTrigger<TimeWindow> countTrigger = CountTrigger.of(2);
        ProcessingTimeoutTrigger<Object, TimeWindow> trigger =
                ProcessingTimeoutTrigger.of(countTrigger, Duration.ofMillis(1000));

        TimeWindow window = new TimeWindow(0, 1000);

        TriggerResult r1 = trigger.onElement("a", 0, window, ctx);
        assertEquals(TriggerResult.CONTINUE, r1);

        TriggerResult r2 = trigger.onElement("b", 0, window, ctx);
        assertEquals(TriggerResult.FIRE, r2);
        assertTrue(ctx.registeredProcessingTimers.isEmpty());
    }

    @Test
    void testClearRemovesTimer() throws Exception {
        ProcessingTimeoutTrigger<Object, TimeWindow> trigger =
                ProcessingTimeoutTrigger.of(
                        ProcessingTimeTrigger.create(),
                        Duration.ofMillis(100));

        TimeWindow window = new TimeWindow(0, 1000);
        trigger.onElement("a", 0, window, ctx);
        assertFalse(ctx.registeredProcessingTimers.isEmpty());

        trigger.clear(window, ctx);
        assertTrue(ctx.registeredProcessingTimers.isEmpty());
    }

    @Test
    void testOnEventTimeReturnsNestedResultNotForcedFire() throws Exception {
        EventTimeTrigger nested = EventTimeTrigger.create();
        ProcessingTimeoutTrigger<Object, TimeWindow> trigger =
                ProcessingTimeoutTrigger.of(nested, Duration.ofMillis(100));

        TimeWindow window = new TimeWindow(0, 1000);
        MockTriggerContext ctx = new MockTriggerContext();

        TriggerResult result = trigger.onEventTime(500, window, ctx);
        assertEquals(TriggerResult.CONTINUE, result,
                "onEventTime should delegate to nested trigger and return CONTINUE, not forced FIRE");
    }

    @Test
    void testOnEventTimeFireAndPurgeFromNestedNotOverridden() throws Exception {
        ContinuousEventTimeTrigger<TimeWindow> nested =
                ContinuousEventTimeTrigger.of(Duration.ofMillis(100));
        ProcessingTimeoutTrigger<Object, TimeWindow> trigger =
                ProcessingTimeoutTrigger.of(nested, Duration.ofMillis(500));

        TimeWindow window = new TimeWindow(0, 1000);
        MockTriggerContext ctx = new MockTriggerContext();
        ctx.currentWatermark = 500;

        trigger.onElement("a", 0, window, ctx);

        TriggerResult result = trigger.onEventTime(100, window, ctx);
        assertNotNull(result);
        assertEquals(TriggerResult.FIRE, result,
                "onEventTime should delegate to nested trigger (ContinuousEventTimeTrigger returns FIRE for matching fire timestamp)");
    }

    @Test
    void testOnEventTimeDoesNotClearOnTimeout() throws Exception {
        ProcessingTimeoutTrigger<Object, TimeWindow> trigger =
                ProcessingTimeoutTrigger.of(
                        ProcessingTimeTrigger.create(),
                        Duration.ofMillis(100),
                        false,
                        true);

        TimeWindow window = new TimeWindow(0, 1000);
        MockTriggerContext ctx = new MockTriggerContext();

        trigger.onElement("a", 0, window, ctx);
        assertFalse(ctx.registeredProcessingTimers.isEmpty(),
                "Timer should be registered on first element");

        TriggerResult result = trigger.onEventTime(500, window, ctx);
        assertFalse(ctx.registeredProcessingTimers.isEmpty(),
                "onEventTime should not clear processing-time timeout timer");
    }

    private static class MockTriggerContext implements Trigger.OnMergeContext {
        private final Map<String, SimpleAccumulator<?>> accumulators = new HashMap<>();
        private long currentProcessingTime = System.currentTimeMillis();
        long currentWatermark = Long.MIN_VALUE;
        final Set<Long> registeredProcessingTimers = new LinkedHashSet<>();

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
            registeredProcessingTimers.add(time);
        }

        @Override
        public void registerEventTimeTimer(long time) {
        }

        @Override
        public void deleteProcessingTimeTimer(long time) {
            registeredProcessingTimers.remove(time);
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
                        throw NopException.adapt(e);
                    }
                }
                return new LongCounter();
            });
        }
    }
}
