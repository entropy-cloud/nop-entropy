package io.nop.stream.core.operators;

import io.nop.stream.core.checkpoint.CheckpointBarrier;
import io.nop.stream.core.checkpoint.CheckpointType;
import io.nop.stream.core.checkpoint.OperatorSnapshotResult;
import io.nop.stream.core.checkpoint.TaskStateSnapshot;
import io.nop.stream.core.common.functions.source.CheckpointedSourceFunction;
import io.nop.stream.core.common.functions.source.ReplayableSourceFunction;
import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.streamrecord.LatencyMarker;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.util.OutputTag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class TestStreamSourceOperator {

    private static Output<StreamRecord<String>> nopOutput() {
        return new Output<>() {
            @Override public void collect(StreamRecord<String> record) {}
            @Override public void close() {}
            @Override public void emitWatermark(io.nop.stream.core.streamrecord.watermark.Watermark watermark) {}
            @Override public void emitWatermarkStatus(io.nop.stream.core.streamrecord.watermark.WatermarkStatus status) {}
            @Override public <X> void collect(OutputTag<X> outputTag, StreamRecord<X> record) {}
            @Override public void emitLatencyMarker(LatencyMarker latencyMarker) {}
            @Override public void emitBarrier(CheckpointBarrier barrier) {}
        };
    }

    @Test
    void testCloseCancelsSourceFunction() throws Exception {
        AtomicBoolean cancelCalled = new AtomicBoolean(false);
        SourceFunction<String> source = new SourceFunction<>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void run(SourceContext<String> ctx) {
            }

            @Override
            public void cancel() {
                cancelCalled.set(true);
            }
        };

        StreamSourceOperator<String> operator = new StreamSourceOperator<>(source);
        operator.close();
        assertTrue(cancelCalled.get(), "close() should call sourceFunction.cancel()");
    }

    @Test
    void testCloseCancelsSourceFunctionEvenWhenCancelThrows() throws Exception {
        AtomicInteger closeCallCount = new AtomicInteger(0);
        SourceFunction<String> source = new SourceFunction<>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void run(SourceContext<String> ctx) {
            }

            @Override
            public void cancel() {
                closeCallCount.incrementAndGet();
                throw new StreamException(io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_UNSUPPORTED);
            }
        };

        StreamSourceOperator<String> operator = new StreamSourceOperator<>(source);
        assertDoesNotThrow(() -> operator.close());
        assertEquals(1, closeCallCount.get(), "cancel() should have been called once");
    }

    @Test
    void testSourceFunctionCancelCancelsRunningSource() throws Exception {
        CountDownLatch runStarted = new CountDownLatch(1);
        AtomicBoolean running = new AtomicBoolean(true);

        SourceFunction<String> source = new SourceFunction<>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void run(SourceContext<String> ctx) throws Exception {
                runStarted.countDown();
                while (running.get()) {
                    Thread.sleep(50);
                }
            }

            @Override
            public void cancel() {
                running.set(false);
            }
        };

        StreamSourceOperator<String> operator = new StreamSourceOperator<>(source);
        operator.setOutput(nopOutput());

        Thread runner = new Thread(() -> {
            try {
                operator.run();
            } catch (Exception e) {
                // expected
            }
        });
        runner.start();
        assertTrue(runStarted.await(2, TimeUnit.SECONDS));
        operator.close();
        runner.join(3000);
        assertFalse(runner.isAlive(), "Source thread should exit after close() calls cancel()");
    }

    @Test
    void testBarrierInjectedDuringSourceRun() throws Exception {
        List<Object> emitted = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch ready = new CountDownLatch(1);
        AtomicBoolean running = new AtomicBoolean(true);
        AtomicBoolean barrierOffered = new AtomicBoolean(false);

        SourceFunction<String> source = new SourceFunction<>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void run(SourceContext<String> ctx) throws Exception {
                ctx.collect("element-1");
                ready.countDown();
                while (running.get()) {
                    Thread.sleep(50);
                    if (barrierOffered.get()) {
                        ctx.collect("element-after-barrier");
                        break;
                    }
                }
            }

            @Override
            public void cancel() {
                running.set(false);
            }
        };

        Output<StreamRecord<String>> output = new Output<>() {
            @Override public void collect(StreamRecord<String> record) { emitted.add(record.getValue()); }
            @Override public void close() {}
            @Override public void emitWatermark(io.nop.stream.core.streamrecord.watermark.Watermark watermark) {}
            @Override public void emitWatermarkStatus(io.nop.stream.core.streamrecord.watermark.WatermarkStatus status) {}
            @Override public <X> void collect(OutputTag<X> outputTag, StreamRecord<X> record) {}
            @Override public void emitLatencyMarker(LatencyMarker latencyMarker) {}
            @Override public void emitBarrier(CheckpointBarrier barrier) { emitted.add("barrier-" + barrier.getId()); }
        };

        StreamSourceOperator<String> operator = new StreamSourceOperator<>(source);
        operator.setOutput(output);

        Thread runner = new Thread(() -> {
            try { operator.run(); } catch (Exception e) { /* expected */ }
        });
        runner.start();
        assertTrue(ready.await(2, TimeUnit.SECONDS));

        CheckpointBarrier barrier = new CheckpointBarrier(1L, System.currentTimeMillis(), CheckpointType.CHECKPOINT);
        assertTrue(operator.offerBarrier(barrier));
        barrierOffered.set(true);

        Thread.sleep(500);
        assertTrue(emitted.contains("barrier-1"), "Barrier should have been emitted on next collect");

        operator.close();
        runner.join(3000);
    }

    @Test
    void testBarrierOfferedAfterSourceFinishes() throws Exception {
        List<String> emitted = new ArrayList<>();

        SourceFunction<String> source = new SourceFunction<>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void run(SourceContext<String> ctx) {
                ctx.collect("done");
            }

            @Override
            public void cancel() {}
        };

        Output<StreamRecord<String>> output = new Output<>() {
            @Override public void collect(StreamRecord<String> record) { emitted.add(record.getValue()); }
            @Override public void close() {}
            @Override public void emitWatermark(io.nop.stream.core.streamrecord.watermark.Watermark watermark) {}
            @Override public void emitWatermarkStatus(io.nop.stream.core.streamrecord.watermark.WatermarkStatus status) {}
            @Override public <X> void collect(OutputTag<X> outputTag, StreamRecord<X> record) {}
            @Override public void emitLatencyMarker(LatencyMarker latencyMarker) {}
            @Override public void emitBarrier(CheckpointBarrier barrier) { emitted.add("barrier-" + barrier.getId()); }
        };

        StreamSourceOperator<String> operator = new StreamSourceOperator<>(source);
        operator.setOutput(output);
        operator.run();

        assertTrue(operator.hasPendingBarrier() == false, "No pending barriers expected after run");
        CheckpointBarrier barrier = new CheckpointBarrier(42L, System.currentTimeMillis(), CheckpointType.SAVEPOINT);
        assertTrue(operator.offerBarrier(barrier), "Barrier should be accepted after source finished");
        assertTrue(emitted.contains("barrier-42"), "Barrier should have been directly injected");

        operator.close();
    }

    @Test
    void testOffsetSnapshotAndRestore() throws Exception {
        AtomicLong currentOffset = new AtomicLong(0);
        AtomicLong restoredOffset = new AtomicLong(-1);

        ReplayableSourceFunction<String> source = new ReplayableSourceFunction<>() {
            private static final long serialVersionUID = 1L;

            @Override public void run(SourceContext<String> ctx) { currentOffset.incrementAndGet(); }
            @Override public void cancel() {}
            @Override public long getCurrentOffset() { return currentOffset.get(); }
            @Override public void seek(long offset) { restoredOffset.set(offset); }
        };

        currentOffset.set(42);

        StreamSourceOperator<String> operator = new StreamSourceOperator<>(source);
        operator.setOutput(nopOutput());

        io.nop.stream.core.checkpoint.StateSnapshotContext ctx =
                new io.nop.stream.core.checkpoint.StateSnapshotContext(1L, System.currentTimeMillis());
        OperatorSnapshotResult snapshot = operator.snapshotState(ctx);

        assertEquals(42L, snapshot.getOperatorState(StreamSourceOperator.SOURCE_OFFSET_KEY),
                "Snapshot should contain current offset");

        StreamSourceOperator<String> restored = new StreamSourceOperator<>(source);
        restored.setOutput(nopOutput());
        restored.restoreState(snapshot);

        assertEquals(42L, restoredOffset.get(), "seek() should have been called with snapshot offset");

        operator.close();
    }
}
