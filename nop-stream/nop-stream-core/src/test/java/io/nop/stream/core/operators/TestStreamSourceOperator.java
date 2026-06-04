package io.nop.stream.core.operators;

import io.nop.stream.core.checkpoint.CheckpointBarrier;
import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.streamrecord.LatencyMarker;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.util.OutputTag;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

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
}
