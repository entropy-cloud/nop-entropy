package io.nop.stream.core.operators;

import io.nop.core.context.IServiceContext;
import io.nop.stream.core.common.functions.KeySelector;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.streamrecord.watermark.Watermark;
import io.nop.stream.core.util.Collector;
import io.nop.stream.core.windowing.assigners.WindowAssigner;
import io.nop.stream.core.windowing.triggers.Trigger;
import io.nop.stream.core.windowing.triggers.TriggerResult;
import io.nop.stream.core.windowing.windows.GlobalWindow;
import jakarta.annotation.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class TestWindowAggregationOperatorProcessingTimeTimer {

    private static final class TestAggFunction
            implements WindowAggregationFunction<String, List<String>, String, String, GlobalWindow> {
        @Override
        public List<String> createAccumulator() {
            return new ArrayList<>();
        }

        @Override
        public List<String> add(String value, List<String> accumulator) {
            accumulator.add(value);
            return accumulator;
        }

        @Override
        public void emitResult(String key, GlobalWindow window, List<String> acc, Collector<String> out) {
            out.collect(key + ":" + String.join(",", acc));
        }
    }

    private static final class ProcessingTimeTestTrigger extends Trigger<String, GlobalWindow> {
        private final long fireAtTimestamp;

        ProcessingTimeTestTrigger(long fireAtTimestamp) {
            this.fireAtTimestamp = fireAtTimestamp;
        }

        @Override
        public TriggerResult onElement(String element, long timestamp, GlobalWindow window, TriggerContext ctx) {
            ctx.registerProcessingTimeTimer(fireAtTimestamp);
            return TriggerResult.CONTINUE;
        }

        @Override
        public TriggerResult onProcessingTime(long time, GlobalWindow window, TriggerContext ctx) {
            return TriggerResult.FIRE_AND_PURGE;
        }

        @Override
        public TriggerResult onEventTime(long time, GlobalWindow window, TriggerContext ctx) {
            return TriggerResult.CONTINUE;
        }

        @Override
        public void clear(GlobalWindow window, TriggerContext ctx) {
        }
    }

    private static final class GlobalWindowAssigner extends WindowAssigner<String, GlobalWindow> {
        @Override
        public Collection<GlobalWindow> assignWindows(String element, long timestamp, WindowAssignerContext ctx) {
            return Collections.singleton(GlobalWindow.get());
        }

        @Override
        public Trigger<String, GlobalWindow> getDefaultTrigger(@Nullable IServiceContext env) {
            return null;
        }

        @Override
        public boolean isEventTime() {
            return false;
        }
    }

    private List<String> collected;
    private WindowAggregationOperator<String, List<String>, String, String, GlobalWindow> operator;

    @BeforeEach
    void setUp() throws Exception {
        collected = new ArrayList<>();

        KeySelector<String, String> keySelector = String::toString;

        operator = new WindowAggregationOperator<>(
                new GlobalWindowAssigner(),
                null,
                new TestAggFunction(),
                keySelector);

        operator.open();
        operator.output = new Output<StreamRecord<String>>() {
            @Override
            public void collect(StreamRecord<String> record) {
                collected.add(record.getValue());
            }

            @Override
            public void close() {
            }

            @Override
            public void emitWatermark(Watermark mark) {
            }

            @Override
            public void emitWatermarkStatus(io.nop.stream.core.streamrecord.watermark.WatermarkStatus status) {
            }

            @Override
            public <X> void collect(io.nop.stream.core.util.OutputTag<X> outputTag, StreamRecord<X> record) {
            }

            @Override
            public void emitLatencyMarker(io.nop.stream.core.streamrecord.LatencyMarker latencyMarker) {
            }

            @Override
            public void emitBarrier(io.nop.stream.core.checkpoint.CheckpointBarrier barrier) {
            }
        };
    }

    @Test
    void testProcessingTimeTimerFiresOnAdvanceProcessingTime() throws Exception {
        long futureFireTime = System.currentTimeMillis() + 100_000L;
        operator.trigger = new ProcessingTimeTestTrigger(futureFireTime);

        operator.setCurrentKey("key1");
        operator.processElement(new StreamRecord<>("a", 1000L));

        assertTrue(collected.isEmpty(), "No output before advancing processing time");

        operator.advanceProcessingTime(futureFireTime);

        assertEquals(1, collected.size(), "Processing time timer should fire and produce output");
        assertTrue(collected.get(0).startsWith("key1:"), "Output should be for key1");
    }

    @Test
    void testProcessingTimeTimerNotFiredBeforeTimestamp() throws Exception {
        long fireTime = System.currentTimeMillis() + 100_000L;
        operator.trigger = new ProcessingTimeTestTrigger(fireTime);

        operator.setCurrentKey("key1");
        operator.processElement(new StreamRecord<>("a", 1000L));

        operator.advanceProcessingTime(System.currentTimeMillis());

        assertTrue(collected.isEmpty(), "Timer should not fire before its timestamp");
    }

    @Test
    void testDeleteProcessingTimeTimer() throws Exception {
        long fireTime = System.currentTimeMillis() + 100_000L;

        Trigger<String, GlobalWindow> registerThenDelete = new Trigger<String, GlobalWindow>() {
            @Override
            public TriggerResult onElement(String element, long timestamp, GlobalWindow window, TriggerContext ctx) {
                ctx.registerProcessingTimeTimer(fireTime);
                ctx.deleteProcessingTimeTimer(fireTime);
                return TriggerResult.CONTINUE;
            }

            @Override
            public TriggerResult onProcessingTime(long time, GlobalWindow window, TriggerContext ctx) {
                return TriggerResult.FIRE_AND_PURGE;
            }

            @Override
            public TriggerResult onEventTime(long time, GlobalWindow window, TriggerContext ctx) {
                return TriggerResult.CONTINUE;
            }

            @Override
            public void clear(GlobalWindow window, TriggerContext ctx) {
            }
        };

        operator.trigger = registerThenDelete;

        operator.setCurrentKey("key1");
        operator.processElement(new StreamRecord<>("a", 1000L));

        operator.advanceProcessingTime(fireTime);

        assertTrue(collected.isEmpty(), "Deleted timer should not fire");
    }

    @Test
    void testProcessElementAutoAdvanceFiresPastTimers() throws Exception {
        long pastTime = System.currentTimeMillis() - 100_000L;
        operator.trigger = new ProcessingTimeTestTrigger(pastTime);

        operator.setCurrentKey("key1");
        operator.processElement(new StreamRecord<>("a", 1000L));

        assertTrue(collected.isEmpty(), "processElement alone should not fire timers");

        operator.advanceProcessingTime(System.currentTimeMillis());

        assertFalse(collected.isEmpty(), "advanceProcessingTime should fire past timers");
    }
}
