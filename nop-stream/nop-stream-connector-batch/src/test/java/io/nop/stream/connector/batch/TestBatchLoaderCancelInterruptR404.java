/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 */
package io.nop.stream.connector.batch;

import io.nop.batch.core.IBatchChunkContext;
import io.nop.batch.core.IBatchLoaderProvider;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.nop.stream.core.common.functions.source.SourceFunction;

/**
 * CRA 8.44 R4-04 (plan 2537) red③ acceptance pin — W-03 (finding 1.2-C3-010;
 * adjudication A.2 / seam S3): {@code cancel()} must deliver a thread interrupt
 * to the run thread so an in-flight {@code loader.load(...)} (the legacy
 * function's single blocking point; JDBC-shaped loaders respond to interrupt)
 * no longer holds cancellation hostage without any upper bound.
 *
 * <p>Zero broker / zero JDBC: the loader is a hand-written stub whose
 * {@code load} parks on a latch and samples the interrupt flag — delivery is
 * observed directly (the interrupt bit inside {@code load}), not inferred from
 * an exception type. Per the plan's R-G boundary this pin deliberately lives in
 * its own test class: {@code TestBatchLoaderSourceFunction} is C10-016-owned
 * and must not be touched.
 *
 * <p>Driver response differences are a declared boundary (owner doc, E5):
 * interrupt delivery ≠ unblock for loaders that ignore interrupts — the
 * delivery CHANNEL is the defect surface this pin closes.
 */
class TestBatchLoaderCancelInterruptR404 {

    /** red③ core leg: cancel() while {@code load} is parked must set the interrupt
     *  flag inside the loader (delivery observed) and the run must terminate within
     *  the pin budget. Pre-fix cancel() only flips the volatile flag, so the parked
     *  load never observes an interrupt = red. */
    @Test
    void cancelDeliversInterruptToInFlightLoad() throws Exception {
        CountDownLatch loadParked = new CountDownLatch(1);
        CountDownLatch releaseLoad = new CountDownLatch(1);
        AtomicBoolean interruptObservedInLoad = new AtomicBoolean(false);
        AtomicReference<Thread> loadThread = new AtomicReference<>();
        AtomicReference<Throwable> runFailure = new AtomicReference<>();

        BatchLoaderSourceFunction<String> f = new BatchLoaderSourceFunction<>(
                ctx -> new IBatchLoaderProvider.IBatchLoader<String>() {
                    @Override
                    public List<String> load(int batchSize, IBatchChunkContext chunkContext) {
                        loadThread.set(Thread.currentThread());
                        loadParked.countDown();
                        try {
                            releaseLoad.await();
                        } catch (InterruptedException e) {
                            // the parked load observes the interrupt DELIVERY directly
                            interruptObservedInLoad.set(true);
                            Thread.currentThread().interrupt();
                            throw new io.nop.stream.core.exceptions.StreamException(
                                    io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_JOB_EXECUTE_FAILED)
                                    .param(io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL,
                                            "load aborted by interrupt (CRA 8.44 R4-04 W-03 red③)");
                        }
                        return List.of();
                    }
                }, 1);

        Thread runThread = new Thread(() -> {
            try {
                f.run(sinkContext());
            } catch (Throwable t) {
                runFailure.set(t);
            }
        });
        runThread.start();
        assertTrue(loadParked.await(5, TimeUnit.SECONDS),
                "run must reach the blocking loader.load");
        assertTrue(loadThread.get() == runThread,
                "load must run on the recorded run thread");

        long cancelAt = System.currentTimeMillis();
        f.cancel();

        // budget-bounded release: interrupt delivery must make the parked load exit
        // BEFORE the latch is released — give it 5s of observation, then release and
        // let the run finish regardless
        boolean deliveredWithinBudget = waitFor(interruptObservedInLoad, 5_000L);
        releaseLoad.countDown();
        runThread.join(5_000L);
        assertFalse(runThread.isAlive(), "run must terminate after cancel within budget");
        assertTrue(deliveredWithinBudget,
                "cancel() must deliver the interrupt to the in-flight load (observed inside "
                        + "loader.load within " + (System.currentTimeMillis() - cancelAt)
                        + "ms budget) — pre-fix only the volatile flag flips and the parked "
                        + "load never observes an interrupt (CRA 8.44 R4-04 W-03 red③)");
    }

    /** cancellation before run(): run() resets the lifecycle flag to true (region
     *  restart contract), so this leg only pins that the reset itself stays intact —
     *  the run proceeds normally (no spurious interrupt delivery to the fresh run). */
    @Test
    void cancelBeforeRunDoesNotBreakTheFreshRunEpoch() throws Exception {
        BatchLoaderSourceFunction<String> f = new BatchLoaderSourceFunction<>(
                ctx -> new IBatchLoaderProvider.IBatchLoader<String>() {
                    @Override
                    public List<String> load(int batchSize, IBatchChunkContext chunkContext) {
                        return List.of(); // immediate EOS: traversal ends naturally
                    }
                }, 1);

        f.cancel();
        AtomicReference<Throwable> runFailure = new AtomicReference<>();
        Thread runThread = new Thread(() -> {
            try {
                f.run(sinkContext());
            } catch (Throwable t) {
                runFailure.set(t);
            }
        });
        runThread.start();
        runThread.join(5_000L);
        assertFalse(runThread.isAlive(), "fresh run epoch must proceed to natural EOS");
        assertTrue(runFailure.get() == null
                        || !(runFailure.get() instanceof RuntimeException
                                && runFailure.get().getMessage() != null
                                && runFailure.get().getMessage().contains("interrupt")),
                "a cancel that predates the run epoch must not sabotage the fresh run");
    }


    private static SourceFunction.SourceContext<String> sinkContext() {
        return new SourceFunction.SourceContext<>() {
            @Override
            public void collect(String element) {
            }

            @Override
            public void collectWithTimestamp(String element, long timestamp) {
            }

            @Override
            public void emitWatermark(long mark) {
            }

            @Override
            public void markAsTemporarilyIdle() {
            }

            @Override
            public long getProcessingTime() {
                return System.currentTimeMillis();
            }

            @Override
            public boolean isCancelled() {
                return false;
            }
        };
    }

    private static boolean waitFor(AtomicBoolean flag, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (flag.get()) {
                return true;
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return flag.get();
            }
        }
        return flag.get();
    }
}
