package io.nop.stream.core.operators;

import io.nop.stream.core.checkpoint.CheckpointBarrier;
import io.nop.stream.core.checkpoint.CheckpointType;
import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.execution.MailboxExecutor;
import io.nop.stream.core.streamrecord.LatencyMarker;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.util.OutputTag;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 1 (item 29) wiring verification for the {@link SourceFunction.SourceContext}
 * cancel accessor: the production context wired by {@link StreamSourceOperator#run()}
 * must reflect the task {@link MailboxExecutor}'s cancel flag — the same truth source
 * as the cooperative checkpoint-abort exception — so an inline xpl source body can
 * observe cancellation by polling {@code ctx.isCancelled()} without calling collect().
 */
class TestSourceContextCancelAccessor {

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

    /**
     * The accessor flips from false to true around {@code signalCancel()} on the SAME
     * context instance: it reads the live mailbox flag, not a constant captured at
     * wiring time. The source body here never calls collect(), proving the accessor
     * is observable on the pure-polling path (no cooperative-exception dependency).
     */
    @Test
    void testContextCancelAccessorReflectsMailboxFlagWithoutCollect() throws Exception {
        MailboxExecutor exec = new MailboxExecutor();
        CountDownLatch beforeSignal = new CountDownLatch(1);
        CountDownLatch cancelSignalled = new CountDownLatch(1);
        AtomicReference<Boolean> observedBefore = new AtomicReference<>();
        AtomicReference<Boolean> observedAfter = new AtomicReference<>();

        SourceFunction<String> source = new SourceFunction<>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void run(SourceContext<String> ctx) throws Exception {
                // Pure polling path: no collect() call anywhere in this body.
                observedBefore.set(ctx.isCancelled());
                beforeSignal.countDown();
                // Poll until the main thread has raised the mailbox cancel flag.
                assertTrue(cancelSignalled.await(5, TimeUnit.SECONDS),
                        "test driver must signal cancel while the source is running");
                observedAfter.set(ctx.isCancelled());
            }

            @Override
            public void cancel() {
            }
        };

        StreamSourceOperator<String> operator = new StreamSourceOperator<>(source);
        operator.setOutput(nopOutput());
        operator.setMailboxExecutor(exec);
        operator.open();

        Thread sourceThread = new Thread(() -> {
            try {
                operator.run();
            } catch (Exception e) {
                throw new IllegalStateException("cancel-accessor test source failed", e);
            }
        }, "cancel-accessor-source");
        sourceThread.start();
        assertTrue(beforeSignal.await(2, TimeUnit.SECONDS), "source must start and read the accessor");

        // Wiring verification: before signalCancel the accessor reads false.
        assertFalse(exec.isCancelled(), "mailbox flag must start unset");
        assertFalse(observedBefore.get(), "accessor must read false before signalCancel");

        exec.signalCancel();
        cancelSignalled.countDown();
        sourceThread.join(5000);
        assertFalse(sourceThread.isAlive(), "source must return after observing the cancel flag");

        assertTrue(exec.isCancelled(), "mailbox flag must be set after signalCancel");
        assertTrue(observedAfter.get(),
                "accessor must read true after signalCancel (same context, live flag — not a constant)");
    }

    /**
     * Without a mailbox wired (isolated operator usage, e.g. direct unit tests), the
     * production context reports not-cancelled rather than throwing or faking a signal.
     */
    @Test
    void testContextCancelAccessorWithoutMailboxReportsNotCancelled() throws Exception {
        AtomicReference<Boolean> observed = new AtomicReference<>();

        SourceFunction<String> source = new SourceFunction<>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void run(SourceContext<String> ctx) {
                observed.set(ctx.isCancelled());
            }

            @Override
            public void cancel() {
            }
        };

        StreamSourceOperator<String> operator = new StreamSourceOperator<>(source);
        operator.setOutput(nopOutput());
        operator.run();

        assertFalse(observed.get(), "no-mailbox context must report not-cancelled");
    }

    /**
     * Documented default-method semantics: a context that does not override
     * {@code isCancelled()} (a plain test double) reports not-cancelled — it never
     * fakes a cancel signal.
     */
    @Test
    void testDefaultContextCancelAccessorReturnsFalse() {
        SourceFunction.SourceContext<String> plain = new SourceFunction.SourceContext<>() {
            @Override public void collect(String element) {}
            @Override public void collectWithTimestamp(String element, long timestamp) {}
            @Override public void emitWatermark(long mark) {}
            @Override public void markAsTemporarilyIdle() {}
            @Override public long getProcessingTime() { return 0L; }
        };
        assertFalse(plain.isCancelled(), "default isCancelled() must return false");
    }
}
