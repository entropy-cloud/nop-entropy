package io.nop.stream.core.operators;

import io.nop.stream.core.common.eventtime.WatermarkStrategy;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.streamrecord.watermark.Watermark;
import io.nop.stream.core.test.TestOutput;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

public class TestPeriodicWatermarkAdvancement {

    private static final long TEST_WATERMARK_INTERVAL = 50L;

    private TimestampsAndWatermarksOperator<TestEvent> operator;
    private TestOutput<TestEvent> output;
    private TestProcessingTimeService processingTimeService;

    static class TestEvent {
        final String id;
        final long timestamp;

        TestEvent(String id, long timestamp) {
            this.id = id;
            this.timestamp = timestamp;
        }
    }

    static class TestProcessingTimeService implements ProcessingTimeService {
        private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "test-processing-time");
            t.setDaemon(true);
            return t;
        });

        @Override
        public long getCurrentProcessingTime() {
            return System.currentTimeMillis();
        }

        @Override
        public ScheduledFuture<?> registerTimer(long timestamp, ProcessingTimeCallback target) {
            long delay = Math.max(0, timestamp - getCurrentProcessingTime());
            return executor.schedule(() -> {
                try {
                    target.onProcessingTime(timestamp);
                } catch (Exception e) {
                    // ignore
                }
            }, delay, TimeUnit.MILLISECONDS);
        }

        void shutdown() {
            executor.shutdownNow();
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        WatermarkStrategy<TestEvent> strategy = WatermarkStrategy
                .<TestEvent>forBoundedOutOfOrderness(Duration.ofMillis(10))
                .withTimestampAssigner((event, ts) -> event.timestamp);

        operator = new TimestampsAndWatermarksOperator<>(strategy, TEST_WATERMARK_INTERVAL);
        processingTimeService = new TestProcessingTimeService();
        operator.processingTimeService = processingTimeService;
        output = new TestOutput<>();
        operator.setOutput((Output) output);
        operator.open();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (operator != null) {
            operator.finish();
        }
        if (processingTimeService != null) {
            processingTimeService.shutdown();
        }
    }

    @Test
    void testTimerEmitsWatermarkForElementsWithinInterval() throws Exception {
        output.clear();

        operator.processElement(new StreamRecord<>(new TestEvent("a", 100L)));

        List<Watermark> afterFirst = output.getWatermarks();
        assertFalse(afterFirst.isEmpty(), "First element should trigger inline periodic emit");

        output.clear();

        operator.processElement(new StreamRecord<>(new TestEvent("b", 500L)));

        CountDownLatch latch = new CountDownLatch(1);
        Thread checker = new Thread(() -> {
            long deadline = System.currentTimeMillis() + TEST_WATERMARK_INTERVAL * 10;
            while (System.currentTimeMillis() < deadline) {
                List<Watermark> wms = output.getWatermarks();
                for (Watermark wm : wms) {
                    if (wm.getTimestamp() >= 489) {
                        latch.countDown();
                        return;
                    }
                }
                try {
                    Thread.sleep(5);
                } catch (InterruptedException e) {
                    return;
                }
            }
        });
        checker.start();

        boolean found = latch.await(TEST_WATERMARK_INTERVAL * 10, TimeUnit.MILLISECONDS);
        checker.join(1000);

        assertTrue(found,
                "Periodic timer should emit watermark >= 489 (500-10-1) for second element processed within interval");
    }

    @Test
    void testTimerStoppedOnFinish() throws Exception {
        WatermarkStrategy<TestEvent> strategy = WatermarkStrategy
                .<TestEvent>forBoundedOutOfOrderness(Duration.ofMillis(10))
                .withTimestampAssigner((event, ts) -> event.timestamp);

        TimestampsAndWatermarksOperator<TestEvent> op = new TimestampsAndWatermarksOperator<>(strategy, 50L);
        TestProcessingTimeService pts = new TestProcessingTimeService();
        op.processingTimeService = pts;
        TestOutput<TestEvent> out = new TestOutput<>();
        op.setOutput((Output) out);
        op.open();

        op.processElement(new StreamRecord<>(new TestEvent("a", 500L)));
        out.clear();

        op.finish();

        int watermarkCountBefore = out.getWatermarks().size();
        Thread.sleep(TEST_WATERMARK_INTERVAL * 3);
        int watermarkCountAfter = out.getWatermarks().size();

        assertEquals(watermarkCountBefore, watermarkCountAfter,
                "No new watermarks should be emitted after finish()");

        pts.shutdown();
    }

    @Test
    void testNoTimerWhenIntervalZero() throws Exception {
        WatermarkStrategy<TestEvent> strategy = WatermarkStrategy
                .<TestEvent>forBoundedOutOfOrderness(Duration.ofMillis(10))
                .withTimestampAssigner((event, ts) -> event.timestamp);

        TimestampsAndWatermarksOperator<TestEvent> op = new TimestampsAndWatermarksOperator<>(strategy, 0L);
        TestOutput<TestEvent> out = new TestOutput<>();
        op.setOutput((Output) out);
        op.open();

        op.processElement(new StreamRecord<>(new TestEvent("a", 100L)));
        out.clear();

        Thread.sleep(100);

        assertTrue(out.getWatermarks().isEmpty(),
                "No periodic watermark should be emitted when interval is 0");

        op.finish();
    }

    @Test
    void testEventTimeWindowTriggersDuringStreamExecution() throws Exception {
        WatermarkStrategy<TestEvent> strategy = WatermarkStrategy
                .<TestEvent>forBoundedOutOfOrderness(Duration.ofMillis(10))
                .withTimestampAssigner((event, ts) -> event.timestamp);

        TimestampsAndWatermarksOperator<TestEvent> op = new TimestampsAndWatermarksOperator<>(strategy, 50L);
        TestProcessingTimeService pts = new TestProcessingTimeService();
        op.processingTimeService = pts;
        TestOutput<TestEvent> out = new TestOutput<>();
        op.setOutput((Output) out);
        op.open();

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

        CountDownLatch latch = new CountDownLatch(1);
        Thread checker = new Thread(() -> {
            long deadline = System.currentTimeMillis() + 500;
            while (System.currentTimeMillis() < deadline) {
                if (maxWatermark[0] >= 489) {
                    latch.countDown();
                    return;
                }
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    return;
                }
            }
        });
        checker.start();

        boolean reached = latch.await(500, TimeUnit.MILLISECONDS);
        checker.join(1000);

        assertTrue(reached,
                "Watermark should reach >= 489 from periodic timer during stream execution, got " + maxWatermark[0]);

        op.finish();
        pts.shutdown();
    }
}
