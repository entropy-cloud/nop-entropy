/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 */
package io.nop.stream.connector.file;

import io.nop.stream.core.exceptions.NopStreamErrors;
import io.nop.stream.core.exceptions.StreamException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CRA 8.43 R4-03 (plan 2536) red ④ + red ⑥ — file-side legs.
 *
 * <p>red ④ face ② (W-04, adjudication C.1; S5 small-knob seam): the FILE sink's
 * epoch-line buffer and pendingCommits stack get declared bounds with typed
 * overflow rejects; non-positive knobs fail fast at construction. Pre-fix red
 * = compile face (the 7-arg full constructor did not exist), registered in
 * {@code _tmp/red-2536-GD-compile.log}; the AR-14 mutual-exclusion pin and the
 * C3-002 ManifestPrune pin keep running unchanged (zero-rewrite anchors).
 *
 * <p>red ⑥ (W-06, MA-15=C2-005; adjudication C.4; S7 seam = MANIFEST_JVM_LOCKS
 * package-visible lock table + small-timeout injection):
 * <ul>
 *   <li>{@code jvmLockHoldInterruptibleR403} — the INTERRUPT leg uses the
 *       DEFAULT constructor and is therefore the PRE-FIX-COMPILABLE behavioral
 *       red carrier: pre-fix {@code jvmLock.lock()} ignores interrupt and the
 *       commit path blocks forever (the @Timeout line is the red evidence);
 *       post-fix the bounded-and-interruptible tryLock propagates the cancel
 *       typed within the 30s latch band.</li>
 *   <li>{@code jvmLockHoldFailsTypedWithinBudgetR403} — small-timeout
 *       injection: a held JVM-local lock fails the commit typed inside the
 *       budget (no unbounded wait).</li>
 *   <li>{@code osLockHoldFailsTypedWithinBudgetR403} — the OS-level face made
 *       testable WITHOUT a foreign process: the test itself holds the
 *       {@code .manifest.lock} file lock on a separate FileChannel (OS-level
 *       advisory lock — exactly what a peer JVM writer would hold), the sink's
 *       tryLock polling loop runs its full budget and fails typed. Pre-fix
 *       {@code channel.lock()} blocks forever against this same injection.</li>
 * </ul>
 * Every leg releases its hold in a finally (latch band ≤30s).
 */
class TestFileSinkEnvelopeLockR403 {

    @TempDir
    Path tempDir;

    // ------------------------------------------------------------------
    // red ④ face ② — W-04 envelope knobs (file)
    // ------------------------------------------------------------------

    @Test
    void epochLineBufferOverflowFailsTypedR403() throws Exception {
        Path dir = tempDir.resolve("w04-buffer");
        FileTwoPhaseCommitSink<String> sink = new FileTwoPhaseCommitSink<>(
                dir.toString(), StandardCharsets.UTF_8, 0, 0L,
                3L /* maxEpochLines */, 8L, 60_000L);
        // cap-1 boundary: 3 lines buffer normally.
        for (int i = 0; i < 3; i++) {
            sink.invoke("line-" + i);
        }
        StreamException e = assertThrows(StreamException.class, () -> sink.invoke("overflow"),
                "epoch line buffer overflow must fail typed");
        assertEquals(NopStreamErrors.ERR_STREAM_STATE_ERROR.getErrorCode(), e.getErrorCode());
        String detail = String.valueOf(e.getMessage()) + e;
        assertTrue(detail.contains("epoch buffer"), "the failure must name the envelope: " + detail);
    }

    @Test
    void pendingEpochStackOverflowFailsTypedR403() throws Exception {
        Path dir = tempDir.resolve("w04-pending");
        FileTwoPhaseCommitSink<String> sink = new FileTwoPhaseCommitSink<>(
                dir.toString(), StandardCharsets.UTF_8, 0, 0L,
                1000L, 2L /* maxPendingEpochs */, 60_000L);
        sink.invoke("a");
        sink.saveState(1L);
        sink.invoke("b");
        sink.saveState(2L);
        StreamException e = assertThrows(StreamException.class, () -> {
            sink.invoke("c");
            sink.saveState(3L);
        }, "pendingCommits stack overflow must fail typed (each pending entry pins a "
                + "tmp file — the stack IS the disk-face bound)");
        assertEquals(NopStreamErrors.ERR_STREAM_STATE_ERROR.getErrorCode(), e.getErrorCode());
        String detail = String.valueOf(e.getMessage()) + e;
        assertTrue(detail.contains("pendingCommits"),
                "the failure must name the pendingCommits envelope: " + detail);
    }

    @Test
    void nonPositiveEnvelopeParamsFailFastR403() {
        Path dir = tempDir.resolve("w04-neg");
        assertThrows(StreamException.class,
                () -> new FileTwoPhaseCommitSink<>(dir.toString(), StandardCharsets.UTF_8, 0,
                        0L, 0L, 8L, 60_000L),
                "maxEpochLines <= 0 must fail fast");
        assertThrows(StreamException.class,
                () -> new FileTwoPhaseCommitSink<>(dir.toString(), StandardCharsets.UTF_8, 0,
                        0L, 1000L, -1L, 60_000L),
                "maxPendingEpochs <= 0 must fail fast");
        StreamException e = assertThrows(StreamException.class,
                () -> new FileTwoPhaseCommitSink<>(dir.toString(), StandardCharsets.UTF_8, 0,
                        0L, 1000L, 8L, 0L),
                "manifestLockTimeoutMs <= 0 must fail fast");
        assertEquals(NopStreamErrors.ERR_STREAM_INVALID_ARG.getErrorCode(), e.getErrorCode());
    }

    // ------------------------------------------------------------------
    // red ⑥ — W-06 manifest lock budget
    // ------------------------------------------------------------------

    /** Reflects the JVM-local manifest lock table (S7 seam — package-visible state). */
    @SuppressWarnings("unchecked")
    private static ReentrantLock jvmLockFor(Path dir) throws Exception {
        Field field = FileTwoPhaseCommitSink.class.getDeclaredField("MANIFEST_JVM_LOCKS");
        field.setAccessible(true);
        Map<String, ReentrantLock> locks =
                (ConcurrentHashMap<String, ReentrantLock>) field.get(null);
        return locks.computeIfAbsent(dir.toAbsolutePath().normalize().toString(),
                k -> new ReentrantLock());
    }

    /**
     * red ⑥ interrupt leg (DEFAULT constructor — the pre-fix-compilable
     * behavioral red carrier): a held JVM-local lock must NOT turn the commit
     * path into an uninterruptible infinite block. Pre-fix {@code lock()}
     * ignores the interrupt → the @Timeout line fires = the red evidence;
     * post-fix {@code tryLock(budget)} propagates the cancellation typed.
     */
    @org.junit.jupiter.api.Timeout(20)
    @Test
    void jvmLockHoldInterruptibleR403() throws Exception {
        Path dir = tempDir.resolve("w06-interrupt");
        ReentrantLock jvmLock = jvmLockFor(dir);
        CountDownLatch holdAcquired = new CountDownLatch(1);
        CountDownLatch releaseHold = new CountDownLatch(1);
        Thread holder = new Thread(() -> {
            jvmLock.lock();
            try {
                holdAcquired.countDown();
                releaseHold.await(15, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                // fall through to release
            } finally {
                jvmLock.unlock();
            }
        }, "w06-jvmlock-holder");
        holder.start();
        assertTrue(holdAcquired.await(5, TimeUnit.SECONDS), "the holder must acquire");
        FileTwoPhaseCommitSink<String> sink = new FileTwoPhaseCommitSink<>(dir.toString(),
                StandardCharsets.UTF_8);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread committer = new Thread(() -> {
            try {
                sink.invoke("x");
                sink.saveState(7L);
                sink.commit(7L); // enters withManifestLock
            } catch (Throwable t) {
                failure.set(t);
            }
        }, "w06-committer");
        committer.start();
        // Let the committer reach the lock wait, then cancel it.
        Thread.sleep(300);
        committer.interrupt();
        committer.join(15000);
        releaseHold.countDown();
        holder.join(5000);
        assertTrue(failure.get() != null,
                "the interrupted commit wait must surface a typed failure (pre-fix: the "
                        + "test times out on the uninterruptible lock() — the @Timeout red)");
        StreamException e = assertInstanceOf(StreamException.class, failure.get());
        assertEquals(NopStreamErrors.ERR_STREAM_CHECKPOINT_ERROR.getErrorCode(), e.getErrorCode(),
                "the cancel must map to the typed checkpoint-error face");
    }

    private static StreamException assertInstanceOf(Class<StreamException> type, Object value) {
        assertTrue(type.isInstance(value), "expected " + type.getSimpleName() + ", got " + value);
        return (StreamException) value;
    }

    /**
     * red ⑥ jvmLock budget leg (small-timeout injection): a held JVM-local lock
     * fails the commit typed INSIDE the injected budget — the pre-fix shape
     * blocked for the lock holder's lifetime (test-timeout red evidence).
     */
    @org.junit.jupiter.api.Timeout(20)
    @Test
    void jvmLockHoldFailsTypedWithinBudgetR403() throws Exception {
        Path dir = tempDir.resolve("w06-budget");
        ReentrantLock jvmLock = jvmLockFor(dir);
        CountDownLatch holdAcquired = new CountDownLatch(1);
        CountDownLatch releaseHold = new CountDownLatch(1);
        Thread holder = new Thread(() -> {
            jvmLock.lock();
            try {
                holdAcquired.countDown();
                releaseHold.await(15, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                // fall through to release
            } finally {
                jvmLock.unlock();
            }
        }, "w06-budget-holder");
        holder.start();
        assertTrue(holdAcquired.await(5, TimeUnit.SECONDS), "the holder must acquire");
        FileTwoPhaseCommitSink<String> sink = new FileTwoPhaseCommitSink<>(
                dir.toString(), StandardCharsets.UTF_8, 0, 0L,
                1000L, 8L, 500L /* manifestLockTimeoutMs — small injected budget */);
        long start = System.nanoTime();
        StreamException e = assertThrows(StreamException.class, () -> {
            sink.invoke("x");
            sink.saveState(8L);
            sink.commit(8L); // enters withManifestLock
        }, "a held JVM-local manifest lock must fail the commit typed inside the budget");
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        releaseHold.countDown();
        holder.join(5000);
        assertEquals(NopStreamErrors.ERR_STREAM_CHECKPOINT_ERROR.getErrorCode(), e.getErrorCode());
        assertTrue(elapsedMs < 15_000, "the typed failure must arrive inside the band, took "
                + elapsedMs + "ms");
    }

    /**
     * red ⑥ OS-lock face — cross-JVM contention made testable WITHOUT a foreign
     * process: the test itself holds the {@code .manifest.lock} OS lock on a
     * separate channel (what a peer JVM writer holds), and the sink's
     * tryLock polling loop must run its budget and fail typed.
     */
    @org.junit.jupiter.api.Timeout(20)
    @Test
    void osLockHoldFailsTypedWithinBudgetR403() throws Exception {
        Path dir = tempDir.resolve("w06-os");
        FileTwoPhaseCommitSink<String> sink = new FileTwoPhaseCommitSink<>(
                dir.toString(), StandardCharsets.UTF_8, 0, 0L,
                1000L, 8L, 500L /* small budget */);
        try (FileChannel peer = FileChannel.open(dir.resolve(".manifest.lock"),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            // The "peer JVM writer": hold the OS-level advisory lock.
            try (FileLock ignored = peer.lock()) {
                long start = System.nanoTime();
                StreamException e = assertThrows(StreamException.class, () -> {
                    sink.invoke("x");
                    sink.saveState(9L);
                    sink.commit(9L); // enters withManifestLock
                }, "a held OS-level manifest lock must fail the commit typed inside the "
                        + "budget (pre-fix: channel.lock() blocks forever against this "
                        + "same injection — the @Timeout red)");
                long elapsedMs = (System.nanoTime() - start) / 1_000_000;
                assertEquals(NopStreamErrors.ERR_STREAM_CHECKPOINT_ERROR.getErrorCode(),
                        e.getErrorCode());
                String detail = String.valueOf(e.getMessage()) + e;
                assertTrue(detail.contains("OS-level") || detail.contains("manifest lock"),
                        "the typed failure must name the lock face: " + detail);
                assertTrue(elapsedMs < 15_000, "typed failure inside the band, took "
                        + elapsedMs + "ms");
            }
        }
    }
}
