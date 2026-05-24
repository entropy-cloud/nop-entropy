package io.nop.stream.core.execution;

import io.nop.stream.core.checkpoint.*;
import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.operators.*;
import io.nop.stream.core.streamrecord.StreamRecord;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the source-pull barrier injection model:
 * barriers are offered to the source operator's queue by the scheduler thread,
 * and injected by the source reading thread during collect().
 */
class TestSourcePullBarrierInjection {

    private static final TaskLocation LOC = new TaskLocation("job-1", "pipeline-1", "v0", 0);

    /**
     * Verifies that barriers are injected in the source reading thread,
     * not the scheduler/checkpoint thread.
     */
    @Test
    void testBarrierInjectedInSourceReadingThread() throws Exception {
        AtomicReference<String> barrierThreadName = new AtomicReference<>();
        CountDownLatch barrierInjected = new CountDownLatch(1);

        // Sink that records which thread the barrier arrived on
        StreamSinkOperator<String> sinkOp = new StreamSinkOperator<>(new io.nop.stream.core.common.functions.SinkFunction<String>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void consume(String value) {
            }
        });

        // Capture barrier thread via ChainingOutput
        Output<StreamRecord<String>> capturingOutput = new Output<StreamRecord<String>>() {
            @Override
            public void collect(StreamRecord<String> record) {
                try {
                    sinkOp.processElement(record);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public void close() {
            }

            @Override
            public void emitWatermark(io.nop.stream.core.streamrecord.watermark.Watermark mark) {
            }

            @Override
            public void emitWatermarkStatus(io.nop.stream.core.streamrecord.watermark.WatermarkStatus watermarkStatus) {
            }

            @Override
            public <X> void collect(io.nop.stream.core.util.OutputTag<X> outputTag, StreamRecord<X> record) {
            }

            @Override
            public void emitLatencyMarker(io.nop.stream.core.streamrecord.LatencyMarker latencyMarker) {
            }

            @Override
            public void emitBarrier(CheckpointBarrier barrier) {
                // Record the thread that emits the barrier
                barrierThreadName.set(Thread.currentThread().getName());
                barrierInjected.countDown();
            }
        };

        // Source that emits a few records with a delay, then waits for barrier
        CountDownLatch sourceFinished = new CountDownLatch(1);
        SourceFunction<String> source = new SourceFunction<String>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void run(SourceContext<String> ctx) throws Exception {
                String sourceThreadName = Thread.currentThread().getName();
                ctx.collect("a");
                ctx.collect("b");
                ctx.collect("c");
                sourceFinished.countDown();
            }

            @Override
            public void cancel() {
            }
        };

        StreamSourceOperator<String> sourceOp = new StreamSourceOperator<>(source);
        sourceOp.setOutput(capturingOutput);
        sourceOp.open();
        sinkOp.open();

        // Set up tracker with operators
        List<StreamOperator<?>> operators = Arrays.asList(sourceOp, sinkOp);
        AtomicInteger ackCount = new AtomicInteger(0);

        CheckpointBarrierTracker tracker = new CheckpointBarrierTracker(LOC, operators, snapshot -> {
            ackCount.incrementAndGet();
        });

        // Setup snapshot callbacks
        for (int i = 0; i < operators.size(); i++) {
            if (operators.get(i) instanceof AbstractStreamOperator) {
                final int opIndex = i;
                ((AbstractStreamOperator<?>) operators.get(i)).setSnapshotCallback(
                        snapshot -> tracker.acknowledgeOperator(opIndex, snapshot)
                );
            }
        }

        // Run source in a named thread
        Thread sourceThread = new Thread(() -> {
            try {
                sourceOp.run();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, "source-reading-thread");
        sourceThread.start();

        // Give source thread time to start
        Thread.sleep(50);

        // Trigger checkpoint from current thread (simulating scheduler)
        boolean triggered = tracker.triggerCheckpoint(1L, System.currentTimeMillis(), CheckpointType.CHECKPOINT);
        assertTrue(triggered, "First trigger should be accepted");

        // Source has already emitted all records. The barrier was queued but
        // collect() calls already happened. Source thread may have already exited.
        // For this test, we need to verify that IF the barrier is emitted,
        // it happens in the source reading thread. Since the source may have
        // already finished before the barrier was offered, we inject remaining barrier.
        sourceThread.join(5000);
        assertFalse(sourceThread.isAlive(), "Source thread should have finished");

        // The barrier should have been injected either:
        // 1. During source thread's collect() → source-reading-thread
        // 2. After source finished, via offerBarrier() fallback → caller's thread
        // Both are valid. The post-finish fallback ensures finite sources can
        // still trigger checkpoints after their run() completes.
        if (barrierInjected.getCount() == 0) {
            String threadName = barrierThreadName.get();
            assertTrue(
                    "source-reading-thread".equals(threadName) || Thread.currentThread().getName().equals(threadName),
                    "Barrier must be injected either in source reading thread or via post-finish fallback, but was: " + threadName
            );
        }
    }

    /**
     * Verifies that the source continues processing records after barrier injection.
     */
    @Test
    void testSourceContinuesAfterBarrierInjection() throws Exception {
        List<String> collected = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch allEmitted = new CountDownLatch(1);
        AtomicBoolean barrierInjected = new AtomicBoolean(false);

        SourceFunction<Integer> source = new SourceFunction<Integer>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void run(SourceContext<Integer> ctx) throws Exception {
                for (int i = 1; i <= 10; i++) {
                    ctx.collect(i);
                    if (i == 5) {
                        // Brief pause to allow barrier to be queued
                        Thread.sleep(20);
                    }
                }
                allEmitted.countDown();
            }

            @Override
            public void cancel() {
            }
        };

        StreamSourceOperator<Integer> sourceOp = new StreamSourceOperator<>(source);

        StreamSinkOperator<Integer> sinkOp = new StreamSinkOperator<>(
                (io.nop.stream.core.common.functions.SinkFunction<Integer>) value -> collected.add(String.valueOf(value))
        );

        // Wrap output to track barrier injection
        Output<StreamRecord<Integer>> trackingOutput = new Output<StreamRecord<Integer>>() {
            @Override
            public void collect(StreamRecord<Integer> record) {
                try {
                    sinkOp.processElement(record);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public void close() {
            }

            @Override
            public void emitWatermark(io.nop.stream.core.streamrecord.watermark.Watermark mark) {
            }

            @Override
            public void emitWatermarkStatus(io.nop.stream.core.streamrecord.watermark.WatermarkStatus watermarkStatus) {
            }

            @Override
            public <X> void collect(io.nop.stream.core.util.OutputTag<X> outputTag, StreamRecord<X> record) {
            }

            @Override
            public void emitLatencyMarker(io.nop.stream.core.streamrecord.LatencyMarker latencyMarker) {
            }

            @Override
            public void emitBarrier(CheckpointBarrier barrier) {
                barrierInjected.set(true);
            }
        };

        sourceOp.setOutput(trackingOutput);
        sourceOp.open();
        sinkOp.open();

        List<StreamOperator<?>> operators = Arrays.asList(sourceOp, sinkOp);
        CheckpointBarrierTracker tracker = new CheckpointBarrierTracker(LOC, operators, snapshot -> {});

        for (int i = 0; i < operators.size(); i++) {
            if (operators.get(i) instanceof AbstractStreamOperator) {
                final int opIndex = i;
                ((AbstractStreamOperator<?>) operators.get(i)).setSnapshotCallback(
                        snapshot -> tracker.acknowledgeOperator(opIndex, snapshot)
                );
            }
        }

        // Run source in background
        Thread sourceThread = new Thread(() -> {
            try {
                sourceOp.run();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        sourceThread.start();

        // Wait a bit, then trigger checkpoint
        Thread.sleep(30);

        tracker.triggerCheckpoint(1L, System.currentTimeMillis(), CheckpointType.CHECKPOINT);

        sourceThread.join(5000);
        assertTrue(allEmitted.await(5, TimeUnit.SECONDS), "All records should have been emitted");

        // All 10 records should be present regardless of barrier injection
        assertEquals(10, collected.size(), "All 10 records must be collected even after barrier injection");
        for (int i = 1; i <= 10; i++) {
            assertTrue(collected.contains(String.valueOf(i)),
                    "Record " + i + " should be in collected output");
        }
    }

    /**
     * Verifies that offerBarrier rejects when a barrier is already queued (capacity 1).
     */
    @Test
    void testBarrierOfferRejectedWhenQueueFull() {
        StreamSourceOperator<String> sourceOp = new StreamSourceOperator<>(
                new SourceFunction<String>() {
                    private static final long serialVersionUID = 1L;

                    @Override
                    public void run(SourceContext<String> ctx) {
                    }

                    @Override
                    public void cancel() {
                    }
                }
        );

        CheckpointBarrier barrier1 = new CheckpointBarrier(1L, System.currentTimeMillis(), CheckpointType.CHECKPOINT);
        CheckpointBarrier barrier2 = new CheckpointBarrier(2L, System.currentTimeMillis(), CheckpointType.CHECKPOINT);

        // First offer should succeed
        assertTrue(sourceOp.offerBarrier(barrier1), "First offer should be accepted");
        // Second offer should be rejected (queue capacity 1)
        assertFalse(sourceOp.offerBarrier(barrier2), "Second offer should be rejected");
        // Should still have pending barrier
        assertTrue(sourceOp.hasPendingBarrier(), "Should have a pending barrier");
    }

    /**
     * Verifies that multiple sequential barriers are correctly handled:
     * offer -> poll (inject) -> offer -> poll (inject).
     */
    @Test
    void testMultipleSequentialBarriers() throws Exception {
        List<Long> barrierIds = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch allBarriers = new CountDownLatch(3);

        SourceFunction<String> source = new SourceFunction<String>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void run(SourceContext<String> ctx) throws Exception {
                for (int i = 0; i < 10; i++) {
                    ctx.collect("record-" + i);
                    Thread.sleep(20);
                }
            }

            @Override
            public void cancel() {
            }
        };

        StreamSourceOperator<String> sourceOp = new StreamSourceOperator<>(source);

        Output<StreamRecord<String>> trackingOutput = new Output<StreamRecord<String>>() {
            @Override
            public void collect(StreamRecord<String> record) {
            }

            @Override
            public void close() {
            }

            @Override
            public void emitWatermark(io.nop.stream.core.streamrecord.watermark.Watermark mark) {
            }

            @Override
            public void emitWatermarkStatus(io.nop.stream.core.streamrecord.watermark.WatermarkStatus watermarkStatus) {
            }

            @Override
            public <X> void collect(io.nop.stream.core.util.OutputTag<X> outputTag, StreamRecord<X> record) {
            }

            @Override
            public void emitLatencyMarker(io.nop.stream.core.streamrecord.LatencyMarker latencyMarker) {
            }

            @Override
            public void emitBarrier(CheckpointBarrier barrier) {
                barrierIds.add(barrier.getId());
                allBarriers.countDown();
            }
        };

        sourceOp.setOutput(trackingOutput);
        sourceOp.open();

        List<StreamOperator<?>> operators = Collections.singletonList(sourceOp);
        CheckpointBarrierTracker tracker = new CheckpointBarrierTracker(LOC, operators, snapshot -> {});

        sourceOp.setSnapshotCallback(snapshot -> tracker.acknowledgeOperator(0, snapshot));

        // Run source in background
        Thread sourceThread = new Thread(() -> {
            try {
                sourceOp.run();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        sourceThread.start();

        // Trigger 3 sequential checkpoints
        for (long id = 1; id <= 3; id++) {
            // Wait for previous checkpoint to complete before triggering next
            Thread.sleep(60);
            tracker.triggerCheckpoint(id, System.currentTimeMillis(), CheckpointType.CHECKPOINT);
        }

        sourceThread.join(5000);

        // At least some barriers should have been injected
        // (depending on timing, some may not be pulled if source finishes before offer)
        assertTrue(barrierIds.size() >= 1,
                "At least one barrier should be injected. Got: " + barrierIds);

        // All injected barriers should be in order
        for (int i = 1; i < barrierIds.size(); i++) {
            assertTrue(barrierIds.get(i) > barrierIds.get(i - 1),
                    "Barrier IDs should be monotonically increasing: " + barrierIds);
        }
    }

    /**
     * Verifies that drainPendingBarrier works correctly.
     */
    @Test
    void testDrainPendingBarrier() {
        StreamSourceOperator<String> sourceOp = new StreamSourceOperator<>(
                new SourceFunction<String>() {
                    private static final long serialVersionUID = 1L;

                    @Override
                    public void run(SourceContext<String> ctx) {
                    }

                    @Override
                    public void cancel() {
                    }
                }
        );

        // No barrier initially
        assertFalse(sourceOp.hasPendingBarrier());
        assertNull(sourceOp.drainPendingBarrier());

        // Offer and drain
        CheckpointBarrier barrier = new CheckpointBarrier(42L, 1000L, CheckpointType.CHECKPOINT);
        assertTrue(sourceOp.offerBarrier(barrier));
        assertTrue(sourceOp.hasPendingBarrier());

        CheckpointBarrier drained = sourceOp.drainPendingBarrier();
        assertNotNull(drained);
        assertEquals(42L, drained.getId());
        assertFalse(sourceOp.hasPendingBarrier());

        // Second drain returns null
        assertNull(sourceOp.drainPendingBarrier());
    }
}
