package io.nop.stream.core.operators;

import io.nop.api.core.time.CoreMetrics;
import io.nop.commons.test.time.ManualClock;
import io.nop.commons.test.time.ManualScheduler;
import io.nop.stream.core.common.eventtime.WatermarkStrategy;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.streamrecord.watermark.Watermark;
import io.nop.stream.core.test.TestOutput;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Periodic watermark advancement, fully virtualized (plan 2510): the operator's
 * {@link ProcessingTimeService} runs on a {@link ManualScheduler} sharing the test's
 * {@link ManualClock} — periodic timers fire only inside {@code scheduler.advance()}
 * on the test thread, so "fired / not fired" is decided by explicit virtual-time
 * advancement plus the scheduler's pending-task evidence, never by wall-clock windows.
 *
 * <p>The inline emit gate inside {@code processElement} reads {@code CoreMetrics},
 * so the registered ManualClock also freezes it between advances: element (b) can
 * never inline-emit while the clock sits still, which keeps the tested emission on
 * the periodic-timer path deterministically.
 */
public class TestPeriodicWatermarkAdvancement {

    private static final long TEST_WATERMARK_INTERVAL = 50L;

    private ManualClock clock;
    private ManualScheduler scheduler;

    static class TestEvent {
        final String id;
        final long timestamp;

        TestEvent(String id, long timestamp) {
            this.id = id;
            this.timestamp = timestamp;
        }
    }

    /**
     * Virtual-time processing-time service: timers are queued on the manual
     * scheduler and only execute during {@code scheduler.advance()} (on the
     * advancing thread). Callback exceptions are swallowed, matching the
     * original test stub's semantics.
     */
    static class VirtualProcessingTimeService implements ProcessingTimeService {
        private final ManualClock clock;
        private final ManualScheduler scheduler;

        VirtualProcessingTimeService(ManualClock clock, ManualScheduler scheduler) {
            this.clock = clock;
            this.scheduler = scheduler;
        }

        @Override
        public long getCurrentProcessingTime() {
            return clock.peekMillis();
        }

        @Override
        public ScheduledFuture<?> registerTimer(long timestamp, ProcessingTimeCallback target) {
            long delay = Math.max(0, timestamp - clock.peekMillis());
            return scheduler.schedule(() -> {
                try {
                    target.onProcessingTime(timestamp);
                } catch (Exception e) {
                    // ignore（同原测试桩语义）
                }
            }, delay, TimeUnit.MILLISECONDS);
        }

        /** Structural evidence: periodic timers still queued (not yet fired/cancelled). */
        int pendingTimers() {
            return scheduler.pendingTaskCount();
        }

        void shutdown() {
            scheduler.shutdown();
        }
    }

    @BeforeEach
    void setUpClock() {
        clock = new ManualClock(1_000_000L);
        CoreMetrics.registerClock(clock);
        scheduler = new ManualScheduler(clock);
    }

    @AfterEach
    void restoreDefaultClock() {
        CoreMetrics.registerClock(CoreMetrics.defaultClock());
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }

    private WatermarkStrategy<TestEvent> boundedOutOfOrderness() {
        return WatermarkStrategy
                .<TestEvent>forBoundedOutOfOrderness(Duration.ofMillis(10))
                .withTimestampAssigner((event, ts) -> event.timestamp);
    }

    private TimestampsAndWatermarksOperator<TestEvent> newOperator(long watermarkInterval,
                                                                   TestOutput<TestEvent> output,
                                                                   VirtualProcessingTimeService pts)
            throws Exception {
        TimestampsAndWatermarksOperator<TestEvent> op =
                new TimestampsAndWatermarksOperator<>(boundedOutOfOrderness(), watermarkInterval);
        op.processingTimeService = pts;
        op.setOutput((Output) output);
        op.open();
        return op;
    }

    @Test
    void testTimerEmitsWatermarkForElementsWithinInterval() throws Exception {
        TestOutput<TestEvent> output = new TestOutput<>();
        TimestampsAndWatermarksOperator<TestEvent> operator =
                newOperator(TEST_WATERMARK_INTERVAL, output, new VirtualProcessingTimeService(clock, scheduler));

        operator.processElement(new StreamRecord<>(new TestEvent("a", 100L)));

        List<Watermark> afterFirst = output.getWatermarks();
        assertFalse(afterFirst.isEmpty(), "First element should trigger inline periodic emit");

        output.clear();

        operator.processElement(new StreamRecord<>(new TestEvent("b", 500L)));

        // 推进前断言（接线验证前置）：时钟未推进，事件 b 的周期 watermark 尚未产生。
        assertTrue(output.getWatermarks().stream().noneMatch(wm -> wm.getTimestamp() >= 489),
                "no watermark >= 489 before the periodic timer fires");

        // 推进虚拟时间越过周期间隔：ManualScheduler 在推进线程上执行到期的周期回调，
        // 发出事件 b 的周期 watermark（500 - 10 - 1 = 489）。
        scheduler.advance(TEST_WATERMARK_INTERVAL * 2);

        assertTrue(output.getWatermarks().stream().anyMatch(wm -> wm.getTimestamp() >= 489),
                "Periodic timer should emit watermark >= 489 (500-10-1) for second element processed within interval");

        operator.finish();
    }

    @Test
    void testTimerStoppedOnFinish() throws Exception {
        TestOutput<TestEvent> out = new TestOutput<>();
        VirtualProcessingTimeService pts = new VirtualProcessingTimeService(clock, scheduler);
        TimestampsAndWatermarksOperator<TestEvent> op = newOperator(TEST_WATERMARK_INTERVAL, out, pts);

        op.processElement(new StreamRecord<>(new TestEvent("a", 500L)));
        out.clear();

        op.finish();

        int watermarkCountBefore = out.getWatermarks().size();
        // 推进越过周期间隔：若 finish() 未停掉周期定时器，到期的回调会执行并重排下一轮
        // ——调度器上必然残留待执行任务；正确 cancel 后推进无事发生。
        scheduler.advance(TEST_WATERMARK_INTERVAL * 3);

        assertEquals(0, pts.pendingTimers(),
                "finish() must stop the periodic watermark timer (no pending task survives a "
                        + "virtual-time advance past the interval)");
        assertEquals(watermarkCountBefore, out.getWatermarks().size(),
                "No new watermarks should be emitted after finish()");
    }

    @Test
    void testNoTimerWhenIntervalZero() throws Exception {
        TestOutput<TestEvent> out = new TestOutput<>();
        VirtualProcessingTimeService pts = new VirtualProcessingTimeService(clock, scheduler);
        TimestampsAndWatermarksOperator<TestEvent> op = newOperator(0L, out, pts);

        // 结构性证据：interval=0 时 open() 根本不注册周期定时器（调度器无待执行任务）。
        assertEquals(0, pts.pendingTimers(),
                "interval=0 must not schedule any periodic watermark timer");

        op.processElement(new StreamRecord<>(new TestEvent("a", 100L)));
        out.clear();

        // 推进虚拟时间给调度器一切触发机会：无任务可执行，仍不得出现 watermark。
        scheduler.advance(TEST_WATERMARK_INTERVAL * 2);

        assertTrue(out.getWatermarks().isEmpty(),
                "No periodic watermark should be emitted when interval is 0");

        op.finish();
    }

    @Test
    void testEventTimeWindowTriggersDuringStreamExecution() throws Exception {
        TestOutput<TestEvent> out = new TestOutput<>();
        TimestampsAndWatermarksOperator<TestEvent> op =
                newOperator(TEST_WATERMARK_INTERVAL, out, new VirtualProcessingTimeService(clock, scheduler));

        final long[] maxWatermark = {Long.MIN_VALUE};

        Output<StreamRecord<TestEvent>> trackingOutput = new Output<StreamRecord<TestEvent>>() {
            @Override
            public void collect(StreamRecord<TestEvent> record) {
            }

            @Override
            public <X> void collect(io.nop.stream.core.util.OutputTag<X> outputTag, StreamRecord<X> record) {
            }

            @Override
            public void emitWatermark(Watermark mark) {
                if (mark.getTimestamp() > maxWatermark[0]) {
                    maxWatermark[0] = mark.getTimestamp();
                }
            }

            @Override
            public void emitWatermarkStatus(io.nop.stream.core.streamrecord.watermark.WatermarkStatus status) {
            }

            @Override
            public void emitLatencyMarker(io.nop.stream.core.streamrecord.LatencyMarker marker) {
            }

            @Override
            public void emitBarrier(io.nop.stream.core.checkpoint.CheckpointBarrier barrier) {
            }

            @Override
            public void close() {
            }
        };
        op.setOutput(trackingOutput);

        op.processElement(new StreamRecord<>(new TestEvent("a", 100L)));
        op.processElement(new StreamRecord<>(new TestEvent("b", 500L)));

        // 推进前断言（接线验证前置）：事件 b 的周期 watermark 尚未发出。
        assertTrue(maxWatermark[0] < 489,
                "no watermark >= 489 before the periodic timer fires, got " + maxWatermark[0]);

        // 推进虚拟时间越过周期间隔：周期回调在推进线程上执行并发出 489。
        scheduler.advance(TEST_WATERMARK_INTERVAL * 2);

        assertTrue(maxWatermark[0] >= 489,
                "Watermark should reach >= 489 from periodic timer during stream execution, got " + maxWatermark[0]);

        op.finish();
    }
}
