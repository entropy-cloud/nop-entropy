/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 */
package io.nop.stream.fraud.scenario;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.exceptions.StreamException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * CRA 8.45 R4-05 (plan 2538) W-06 / 1.3-C1-003 — the directory source's
 * single-reader guard must NOT translate a detected re-entry into a silent
 * zero-event EOS: a loser inside the guard-held window now typed-fails
 * ({@code ERR_STREAM_STATE_ERROR} + "re-entered" detail) and the recovery path owns
 * the retry.
 *
 * <p>Driver 红⑥, two deterministic constructions of the guard-held window:
 * <ul>
 *   <li>the finish-linger window (attempt-1 emitted everything, guard still held
 *       while it lingers before the finally reset);</li>
 *   <li>the emitRemaining barrier seam (design B.2 seam ruling — the widened
 *       protected seam lets a test subclass park attempt-1 AFTER its reads, before
 *       the finally reset — the exact micro-window).</li>
 * </ul>
 * Both legs are deterministic. The pre-existing reverse pin
 * {@code TestDirectoryFileSourceFunction#runEnteredGuardPreventsDoubleEmission}
 * ("a parallel first-entry loser must not read") stays untouched and green — this
 * fix must not flip the "loser never reads" contract.
 */
public class TestDirectoryFileGuardTypedR405 {

    @TempDir
    Path tempDir;

    private static final class CollectingContext
            implements SourceFunction.SourceContext<String> {
        final List<String> collected = new ArrayList<>();

        @Override
        public void collect(String element) {
            collected.add(element);
        }

        @Override
        public void collectWithTimestamp(String element, long timestamp) {
            collected.add(element);
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
    }

    private Path writeInput(String... lines) throws Exception {
        // the source takes a DIRECTORY and lists the files inside it
        Path dir = tempDir.resolve("input-" + System.nanoTime());
        Files.createDirectories(dir);
        Files.write(dir.resolve("part-000.txt"), String.join("\n", lines).getBytes());
        return dir;
    }

    private static void awaitCondition(long timeoutMs, java.util.function.BooleanSupplier cond,
                                       String what) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (cond.getAsBoolean()) {
                return;
            }
            Thread.sleep(5);
        }
        fail("timed out waiting for: " + what);
    }

    /** 红⑥ leg 1 — the finish-linger guard-held window. */
    @Test
    public void secondRunInsideLingerWindowFailsTypedNotFakeEos() throws Exception {
        Path input = writeInput("x,1,1", "y,2,2");
        // attempt-1: emits both lines, then lingers 1500ms BEFORE the finally reset
        DirectoryFileSourceFunction attempt1 =
                new DirectoryFileSourceFunction(input.toString(), 0L, 1500L);
        CollectingContext ctx1 = new CollectingContext();
        AtomicReference<Throwable> attempt1Error = new AtomicReference<>();
        Thread t1 = new Thread(() -> {
            try {
                attempt1.run(ctx1);
            } catch (Throwable t) {
                attempt1Error.set(t);
            }
        });
        t1.start();
        // deterministic gate: attempt-1 has emitted everything (it is inside the
        // linger sleep, guard held, finally not yet run)
        awaitCondition(10000, () -> ctx1.collected.size() == 2,
                "attempt-1 must emit both lines before the probe");

        // attempt-2 (the SAME instance — the slot-replacement defect scenario reuses
        // the shared UDF across a region restart) enters while the guard is held —
        // typed rejection, NOT a silent zero-event EOS
        StreamException ex = assertThrows(StreamException.class,
                () -> attempt1.run(new CollectingContext()),
                "attempt-2 inside the guard-held window must typed-fail (refusing fake EOS)");
        assertTrue(ex.getMessage().contains("re-entered"),
                () -> "the rejection must identify the re-entry: " + ex.getMessage());

        // attempt-1 finishes normally (owns the guard to the end)
        t1.join(10000);
        assertTrue(!t1.isAlive(), "attempt-1 must finish its linger and reset");
        assertNull(attempt1Error.get(), "attempt-1 must complete normally");

        // recovery lifeline: after attempt-1 released the guard, a retry re-enters
        // the SAME instance (region-restart reuse) and completes cleanly — the guard
        // is re-acquirable (zero lines: attempt-1's cursor advanced to EOF, so the
        // resumed run correctly has nothing left to read)
        CollectingContext ctx3 = new CollectingContext();
        attempt1.run(ctx3);
        assertEquals(0, ctx3.collected.size(),
                "the resumed run completes cleanly from the advanced cursor (re-entry works)");
    }

    /**
     * 红⑥ leg 2 — the emitRemaining barrier seam: attempt-1 parked AFTER its reads
     * (guard held, finally not run), the exact [reads done → finally] micro-window.
     */
    @Test
    public void secondRunInsideBarrierSeamFailsTypedAndAttempt1Completes() throws Exception {
        Path input = writeInput("x,1,1", "y,2,2");
        CountDownLatch readsDone = new CountDownLatch(1);
        CountDownLatch releaseAttempt1 = new CountDownLatch(1);

        DirectoryFileSourceFunction attempt1 = new DirectoryFileSourceFunction(
                input.toString(), 0L, 0L) {
            @Override
            protected void emitRemaining(java.io.File file,
                                         SourceFunction.SourceContext<String> ctx) throws Exception {
                super.emitRemaining(file, ctx);
                // park AFTER the reads: guard held, finally reset not yet executed
                readsDone.countDown();
                if (!releaseAttempt1.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("test seam: attempt-1 release timeout");
                }
            }
        };
        CollectingContext ctx1 = new CollectingContext();
        AtomicReference<Throwable> attempt1Error = new AtomicReference<>();
        Thread t1 = new Thread(() -> {
            try {
                attempt1.run(ctx1);
            } catch (Throwable t) {
                attempt1Error.set(t);
            }
        });
        t1.start();
        assertTrue(readsDone.await(10, TimeUnit.SECONDS),
                "attempt-1 must reach the parked seam");

        // attempt-2 (the SAME instance — slot replacement reuses the shared UDF)
        // races the [reads done → finally reset] micro-window
        StreamException ex = assertThrows(StreamException.class,
                () -> attempt1.run(new CollectingContext()),
                "attempt-2 inside the micro-window must typed-fail");
        assertTrue(ex.getMessage().contains("re-entered"),
                () -> "the rejection must identify the re-entry: " + ex.getMessage());

        releaseAttempt1.countDown();
        t1.join(10000);
        assertTrue(!t1.isAlive(), "attempt-1 must complete after the release");
        assertNull(attempt1Error.get(), "attempt-1 must complete normally");
        assertEquals(2, ctx1.collected.size(), "attempt-1 emitted its full input");
    }
}
