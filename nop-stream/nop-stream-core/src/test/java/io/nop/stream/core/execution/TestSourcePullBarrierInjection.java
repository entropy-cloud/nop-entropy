package io.nop.stream.core.execution;

import io.nop.stream.core.checkpoint.*;
import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.operators.*;
import io.nop.stream.core.streamrecord.StreamRecord;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the mailbox-based source trigger-checkpoint model:
 * trigger mails are delivered to the source task's mailbox by the injector thread,
 * and drained at the SourceContext.collect() emission point on the source task thread,
 * where snapshotState/emitBarrier run.
 *
 * <p>Replaces the legacy cap-1 pendingBarriers handoff. The middle/sink
 * {@code triggerCheckpoint} path remains synchronous on the injector thread (only primes
 * the ack count) and is covered by {@link TestCheckpointBarrierTrackerConcurrency}.
 */
class TestSourcePullBarrierInjection {

    private static final TaskLocation LOC = new TaskLocation("job-1", "pipeline-1", "v0", 0);

    /**
     * Verifies that a trigger-checkpoint mail is consumed at the collect() emission point
     * and that snapshotState/emitBarrier run on the source task thread (not the injector
     * thread), preserving the on-task-thread ordering invariant.
     */
    @Test
    void testTriggerMailConsumedOnSourceTaskThread() throws Exception {
        AtomicReference<String> barrierThreadName = new AtomicReference<>();
        CountDownLatch barrierInjected = new CountDownLatch(1);

        StreamSinkOperator<String> sinkOp = new StreamSinkOperator<>(new io.nop.stream.core.common.functions.SinkFunction<String>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void consume(String value) {
            }
        });

        Output<StreamRecord<String>> capturingOutput = new Output<StreamRecord<String>>() {
            @Override
            public void collect(StreamRecord<String> record) {
                try {
                    sinkOp.processElement(record);
                } catch (Exception e) {
                    throw new StreamException("source test thread failed", e);
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
                barrierThreadName.set(Thread.currentThread().getName());
                barrierInjected.countDown();
            }
        };

        // A source that emits records slowly so a trigger mail can be delivered mid-run.
        CountDownLatch keepRunning = new CountDownLatch(1);
        SourceFunction<String> source = new SourceFunction<String>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void run(SourceContext<String> ctx) throws Exception {
                ctx.collect("a");
                ctx.collect("b");
                // Keep the source alive so the trigger mail is consumed on the task thread
                // (not via the finished-source fallback).
                keepRunning.await(2, TimeUnit.SECONDS);
                ctx.collect("c");
            }

            @Override
            public void cancel() {
                keepRunning.countDown();
            }
        };

        StreamSourceOperator<String> sourceOp = new StreamSourceOperator<>(source);
        sourceOp.setOutput(capturingOutput);
        // Wire the mailbox so offerBarrier delivers a trigger-checkpoint mail.
        MailboxExecutor mailboxExecutor = new MailboxExecutor();
        sourceOp.setMailboxExecutor(mailboxExecutor);
        sourceOp.open();
        sinkOp.open();

        List<StreamOperator<?>> operators = Arrays.asList(sourceOp, sinkOp);
        AtomicInteger ackCount = new AtomicInteger(0);

        CheckpointBarrierTracker tracker = new CheckpointBarrierTracker(LOC, operators, snapshot -> {
            ackCount.incrementAndGet();
        });

        for (int i = 0; i < operators.size(); i++) {
            if (operators.get(i) instanceof AbstractStreamOperator) {
                final int opIndex = i;
                ((AbstractStreamOperator<?>) operators.get(i)).setSnapshotCallback(
                        snapshot -> tracker.acknowledgeOperator(opIndex, snapshot)
                );
            }
        }

        Thread sourceThread = new Thread(() -> {
            try {
                sourceOp.run();
            } catch (Exception e) {
                throw new StreamException("source test thread failed", e);
            }
        }, "source-task-thread");
        sourceThread.start();

        // Give source thread time to emit a/b and enter keepRunning.await()
        Thread.sleep(100);

        // Trigger from current thread (simulating barrier-injector thread). triggerCheckpoint
        // primes ack count synchronously here, then offerBarrier puts a mail.
        boolean triggered = tracker.triggerCheckpoint(1L, System.currentTimeMillis(), CheckpointType.CHECKPOINT);
        assertTrue(triggered, "First trigger should be accepted");

        // Release the source so its next collect() drains the mail.
        keepRunning.countDown();
        sourceThread.join(5000);

        assertTrue(barrierInjected.await(2, TimeUnit.SECONDS),
                "Barrier should have been emitted after the trigger mail was drained");
        assertEquals("source-task-thread", barrierThreadName.get(),
                "snapshotState/emitBarrier must run on the source task thread (mailbox drain), not the injector thread");
    }

    /**
     * Verifies that the source continues processing records after barrier injection.
     */
    @Test
    void testSourceContinuesAfterBarrierInjection() throws Exception {
        List<String> collected = Collections.synchronizedList(new ArrayList<>());
        AtomicBoolean barrierInjected = new AtomicBoolean(false);

        SourceFunction<Integer> source = new SourceFunction<Integer>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void run(SourceContext<Integer> ctx) throws Exception {
                for (int i = 1; i <= 10; i++) {
                    ctx.collect(i);
                    if (i == 5) {
                        Thread.sleep(20);
                    }
                }
            }

            @Override
            public void cancel() {
            }
        };

        StreamSourceOperator<Integer> sourceOp = new StreamSourceOperator<>(source);

        StreamSinkOperator<Integer> sinkOp = new StreamSinkOperator<>(
                (io.nop.stream.core.common.functions.SinkFunction<Integer>) value -> collected.add(String.valueOf(value))
        );

        Output<StreamRecord<Integer>> trackingOutput = new Output<StreamRecord<Integer>>() {
            @Override
            public void collect(StreamRecord<Integer> record) {
                try {
                    sinkOp.processElement(record);
                } catch (Exception e) {
                    throw new StreamException("source test thread failed", e);
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
        sourceOp.setMailboxExecutor(new MailboxExecutor());
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

        Thread sourceThread = new Thread(() -> {
            try {
                sourceOp.run();
            } catch (Exception e) {
                throw new StreamException("source test thread failed", e);
            }
        });
        sourceThread.start();

        Thread.sleep(30);

        tracker.triggerCheckpoint(1L, System.currentTimeMillis(), CheckpointType.CHECKPOINT);

        sourceThread.join(5000);

        assertEquals(10, collected.size(), "All 10 records must be collected even after barrier injection");
        for (int i = 1; i <= 10; i++) {
            assertTrue(collected.contains(String.valueOf(i)),
                    "Record " + i + " should be in collected output");
        }
    }

    /**
     * Verifies that multiple sequential trigger-checkpoint mails are handled in order.
     * Overlap protection is at the tracker level (operatorsToAck guard), so the mailbox
     * itself accepts each delivered mail.
     */
    @Test
    void testMultipleSequentialTriggers() throws Exception {
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
        sourceOp.setMailboxExecutor(new MailboxExecutor());
        sourceOp.open();

        List<StreamOperator<?>> operators = Collections.singletonList(sourceOp);
        CheckpointBarrierTracker tracker = new CheckpointBarrierTracker(LOC, operators, snapshot -> {});

        sourceOp.setSnapshotCallback(snapshot -> tracker.acknowledgeOperator(0, snapshot));

        Thread sourceThread = new Thread(() -> {
            try {
                sourceOp.run();
            } catch (Exception e) {
                throw new StreamException("source test thread failed", e);
            }
        });
        sourceThread.start();

        // Trigger 3 sequential checkpoints; each must complete (ack) before the next is
        // accepted (tracker operatorsToAck guard prevents overlap).
        for (long id = 1; id <= 3; id++) {
            Thread.sleep(60);
            tracker.triggerCheckpoint(id, System.currentTimeMillis(), CheckpointType.CHECKPOINT);
        }

        sourceThread.join(5000);

        assertTrue(barrierIds.size() >= 1, "At least one barrier should be injected. Got: " + barrierIds);
        for (int i = 1; i < barrierIds.size(); i++) {
            assertTrue(barrierIds.get(i) > barrierIds.get(i - 1),
                    "Barrier IDs should be monotonically increasing: " + barrierIds);
        }
    }

    /**
     * Verifies the finished-source final-checkpoint exception: after the source has
     * finished (task thread gone), offerBarrier directly injects on the caller thread.
     */
    @Test
    void testFinishedSourceInjectsDirectlyOnCallerThread() throws Exception {
        AtomicReference<String> injectThread = new AtomicReference<>();
        CountDownLatch injected = new CountDownLatch(1);

        SourceFunction<String> source = new SourceFunction<String>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void run(SourceContext<String> ctx) {
                ctx.collect("only");
            }

            @Override
            public void cancel() {
            }
        };

        StreamSourceOperator<String> sourceOp = new StreamSourceOperator<>(source);
        sourceOp.setOutput(new Output<StreamRecord<String>>() {
            @Override public void collect(StreamRecord<String> record) {}
            @Override public void close() {}
            @Override public void emitWatermark(io.nop.stream.core.streamrecord.watermark.Watermark watermark) {}
            @Override public void emitWatermarkStatus(io.nop.stream.core.streamrecord.watermark.WatermarkStatus status) {}
            @Override public <X> void collect(io.nop.stream.core.util.OutputTag<X> outputTag, StreamRecord<X> record) {}
            @Override public void emitLatencyMarker(io.nop.stream.core.streamrecord.LatencyMarker latencyMarker) {}
            @Override public void emitBarrier(CheckpointBarrier barrier) {
                injectThread.set(Thread.currentThread().getName());
                injected.countDown();
            }
        });
        sourceOp.setMailboxExecutor(new MailboxExecutor());
        sourceOp.open();

        // Run the source to completion on a task thread, then join it (task thread gone).
        Thread taskThread = new Thread(() -> {
            try { sourceOp.run(); } catch (Exception e) { throw new StreamException("source test thread failed", e); }
        }, "finished-source-task");
        taskThread.start();
        taskThread.join(2000);

        // Now offerBarrier from the "injector" thread: finished path → direct inject here.
        Thread injectorThread = new Thread(() -> {
            sourceOp.offerBarrier(new CheckpointBarrier(7L, System.currentTimeMillis(), CheckpointType.CHECKPOINT));
        }, "barrier-injector");
        injectorThread.start();
        injectorThread.join(2000);

        assertTrue(injected.await(2, TimeUnit.SECONDS));
        assertEquals("barrier-injector", injectThread.get(),
                "finished-source final checkpoint must run on the injector thread (explicit exception)");
    }
}
