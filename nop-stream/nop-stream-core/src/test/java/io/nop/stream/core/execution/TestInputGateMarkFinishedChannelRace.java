package io.nop.stream.core.execution;

import io.nop.stream.core.checkpoint.CheckpointBarrier;
import io.nop.stream.core.checkpoint.CheckpointType;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.streamrecord.StreamElement;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P1-05 (open-audit): {@link InputGate#markFinishedChannel} iterates the
 * in-flight alignments in {@link java.util.concurrent.ConcurrentHashMap} bucket
 * order and only removes/emits the FIRST fully-received alignment it visits —
 * when TWO alignments become fully received in the SAME markFinishedChannel
 * call (a finished channel counts as having delivered every in-flight barrier),
 * the second one leaks: its barrier is never emitted (silently dropped, so
 * downstream never snapshots that checkpoint) and the leaked fully-received
 * item becomes {@code oldestAligning()}, permanently masking the
 * barrier-alignment timeout / aligned→unaligned fallback gates.
 *
 * <p>The trigger needs two alignments in-flight simultaneously, which aligned
 * mode normally prevents (a channel is blocked after delivering its first
 * barrier, so barrier N+1 cannot be consumed while align(N) is aligning). The
 * deterministic construction here unblocks the channels via the public
 * {@link InputGate#resumeConsumptionAll()} API (the same API the production
 * abort path uses) so barrier N+1 IS consumed while align(N) is still
 * in-flight — both alignments end up missing only the finished channel, and the
 * channel-finish completes both in one markFinishedChannel round.
 */
class TestInputGateMarkFinishedChannelRace {

    private static void waitForInFlight(InputGate gate, long id) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000L;
        while (System.currentTimeMillis() < deadline && !gate.getInFlightBarrierIds().contains(id)) {
            Thread.sleep(10);
        }
        assertTrue(gate.getInFlightBarrierIds().contains(id),
                "Barrier " + id + " must be in-flight before continuing");
    }

    /**
     * Core P1-05 scenario: alignments 5 and 6 are both in-flight and both
     * missing only the finished channel; one markFinishedChannel call completes
     * BOTH. Both barriers must be emitted one per read(), in checkpoint-id
     * order (5 before 6), with no alignment left in-flight afterwards.
     *
     * <p>Pre-fix (red): only the bucket-first alignment is emitted; the other
     * barrier is silently dropped and its fully-received alignment leaks.
     */
    @Test
    void testTwoAlignmentsCompletingOnOneChannelFinishEmitInOrderWithoutLeak() throws Exception {
        ResultPartition p0 = new ResultPartition();
        ResultPartition p1 = new ResultPartition();
        ResultPartition p2 = new ResultPartition();
        InputGate gate = new InputGate(Arrays.asList(new InputChannel(p0), new InputChannel(p1), new InputChannel(p2)),
                null, true, 5000L);

        p0.write(new CheckpointBarrier(5, 0, CheckpointType.CHECKPOINT));
        p1.write(new CheckpointBarrier(5, 0, CheckpointType.CHECKPOINT));
        p0.write(new CheckpointBarrier(6, 0, CheckpointType.CHECKPOINT));
        p1.write(new CheckpointBarrier(6, 0, CheckpointType.CHECKPOINT));

        List<Long> emitted = Collections.synchronizedList(new ArrayList<>());
        AtomicReference<Throwable> err = new AtomicReference<>();
        Thread reader = new Thread(() -> {
            try {
                while (true) {
                    Optional<StreamElement> e = gate.read();
                    if (!e.isPresent()) {
                        break;
                    }
                    if (e.get().isCheckpointBarrier()) {
                        emitted.add(e.get().asCheckpointBarrier().getId());
                    }
                }
            } catch (Throwable t) {
                err.set(t);
            }
        }, "input-gate-reader");
        reader.setDaemon(true);
        reader.start();

        // align(5) is in-flight and blocks p0/p1; barrier 6 is queued behind it.
        waitForInFlight(gate, 5L);
        // Unblock p0/p1 (public API, same as the production abort path) so barrier
        // 6 is consumed while align(5) is still aligning -> two in-flight alignments.
        gate.resumeConsumptionAll();
        waitForInFlight(gate, 6L);

        // Finish the channel both alignments are missing: ONE markFinishedChannel
        // call completes both. Then close the other producers so the reader
        // terminates deterministically (no hang) in both the red and green runs.
        p2.close();
        p0.close();
        p1.close();

        reader.join(10000);
        assertNull(err.get(), () -> "Reader thread must not throw: " + err.get());
        assertEquals(Arrays.asList(5L, 6L), emitted,
                "Both barriers must be emitted one per read(), in checkpoint-id order. "
                        + "Pre-fix the second (bucket-order) one leaks and its barrier is dropped: " + emitted);
        assertTrue(gate.getInFlightBarrierIds().isEmpty(),
                "No alignment may leak in-flight after both barriers were emitted: "
                        + gate.getInFlightBarrierIds());
    }

    /**
     * A leaked fully-received alignment must not permanently mask the barrier
     * alignment timeout gate: after both alignments complete, a fresh barrier
     * that can never fully align must still time out with
     * ERR_STREAM_BARRIER_ALIGNMENT_TIMEOUT.
     *
     * <p>Pre-fix (red): the leaked alignment keeps the gate masked — the fresh
     * barrier never times out and the read never throws (the poll below fails).
     */
    @Test
    void testCompletedAlignmentsDoNotMaskBarrierTimeoutGate() throws Exception {
        ResultPartition p0 = new ResultPartition();
        ResultPartition p1 = new ResultPartition();
        ResultPartition p2 = new ResultPartition();
        InputGate gate = new InputGate(Arrays.asList(new InputChannel(p0), new InputChannel(p1), new InputChannel(p2)),
                null, true, 2000L);

        p0.write(new CheckpointBarrier(5, 0, CheckpointType.CHECKPOINT));
        p1.write(new CheckpointBarrier(5, 0, CheckpointType.CHECKPOINT));
        p0.write(new CheckpointBarrier(6, 0, CheckpointType.CHECKPOINT));
        p1.write(new CheckpointBarrier(6, 0, CheckpointType.CHECKPOINT));

        List<Long> emitted = Collections.synchronizedList(new ArrayList<>());
        AtomicReference<Throwable> err = new AtomicReference<>();
        Thread reader = new Thread(() -> {
            try {
                while (true) {
                    Optional<StreamElement> e = gate.read();
                    if (!e.isPresent()) {
                        break;
                    }
                    if (e.get().isCheckpointBarrier()) {
                        emitted.add(e.get().asCheckpointBarrier().getId());
                    }
                }
            } catch (Throwable t) {
                err.set(t);
            }
        }, "input-gate-reader");
        reader.setDaemon(true);
        reader.start();

        waitForInFlight(gate, 5L);
        gate.resumeConsumptionAll();
        waitForInFlight(gate, 6L);
        p2.close();

        // Wait for both barriers to be emitted (post-fix). Pre-fix this poll fails:
        // barrier 6 is dropped and the leaked alignment masks the timeout gate.
        long deadline = System.currentTimeMillis() + 3000L;
        while (System.currentTimeMillis() < deadline
                && !(emitted.contains(5L) && emitted.contains(6L))) {
            Thread.sleep(10);
        }
        assertTrue(emitted.contains(5L) && emitted.contains(6L),
                "Both barriers must be emitted before the timeout-gate check (pre-fix the "
                        + "second one leaks and is never emitted): " + emitted);

        // Fresh barrier 7 on p0 only: p1 never delivers it and never finishes, so
        // alignment cannot complete — the timeout gate must fire (and must not be
        // masked by any leaked alignment).
        p0.write(new CheckpointBarrier(7, 0, CheckpointType.CHECKPOINT));
        deadline = System.currentTimeMillis() + 8000L;
        while (System.currentTimeMillis() < deadline && err.get() == null) {
            Thread.sleep(10);
        }
        assertNotNull(err.get(), "Alignment of barrier 7 must time out (ERR_STREAM_BARRIER_ALIGNMENT_TIMEOUT)");
        assertTrue(err.get() instanceof StreamException
                        && ((StreamException) err.get()).getErrorCode() != null
                        && ((StreamException) err.get()).getErrorCode().toString()
                        .contains("barrier-alignment-timeout"),
                "Expected the barrier alignment timeout error, got: " + err.get());

        // Cleanup so the reader thread terminates.
        p0.close();
        p1.close();
        reader.join(5000);
    }

    /**
     * A fully-received-but-not-yet-emitted alignment (pending emission) that is
     * aborted between collection and emission must be dropped — its barrier must
     * not be emitted afterwards, and a later barrier must still align and emit
     * normally (the pending drop must not block subsequent emissions). The
     * reader pauses after the first emission so the abort deterministically
     * lands while barrier 6 is still pending.
     */
    @Test
    void testPendingCompletedBarrierDroppedOnAbortDoesNotBlockLaterEmissions() throws Exception {
        ResultPartition p0 = new ResultPartition();
        ResultPartition p1 = new ResultPartition();
        ResultPartition p2 = new ResultPartition();
        InputGate gate = new InputGate(Arrays.asList(new InputChannel(p0), new InputChannel(p1), new InputChannel(p2)),
                null, true, 5000L);

        p0.write(new CheckpointBarrier(5, 0, CheckpointType.CHECKPOINT));
        p1.write(new CheckpointBarrier(5, 0, CheckpointType.CHECKPOINT));
        p0.write(new CheckpointBarrier(6, 0, CheckpointType.CHECKPOINT));
        p1.write(new CheckpointBarrier(6, 0, CheckpointType.CHECKPOINT));

        List<Long> emitted = Collections.synchronizedList(new ArrayList<>());
        AtomicReference<Throwable> err = new AtomicReference<>();
        CountDownLatch firstEmitted = new CountDownLatch(1);
        CountDownLatch releaseReader = new CountDownLatch(1);
        Thread reader = new Thread(() -> {
            try {
                while (true) {
                    Optional<StreamElement> e = gate.read();
                    if (!e.isPresent()) {
                        break;
                    }
                    if (e.get().isCheckpointBarrier()) {
                        long id = e.get().asCheckpointBarrier().getId();
                        emitted.add(id);
                        if (id == 5L) {
                            // Pause right after barrier 5 is emitted, so the abort in
                            // the main thread deterministically lands while barrier 6
                            // is collected-but-not-yet-emitted.
                            firstEmitted.countDown();
                            releaseReader.await();
                        }
                    }
                }
            } catch (Throwable t) {
                err.set(t);
            }
        }, "input-gate-reader");
        reader.setDaemon(true);
        reader.start();

        waitForInFlight(gate, 5L);
        gate.resumeConsumptionAll();
        waitForInFlight(gate, 6L);
        p2.close();

        // Barrier 5 is emitted; barrier 6 is collected and pending emission.
        assertTrue(firstEmitted.await(5, TimeUnit.SECONDS),
                "Barrier 5 must be emitted first (barrier 6 pending): " + emitted);
        // Abort checkpoint 6 while its fully-received barrier is still pending.
        gate.abortBarrierAlignment(6L);

        // A fresh barrier must align and emit normally after the abort.
        p0.write(new CheckpointBarrier(7, 0, CheckpointType.CHECKPOINT));
        p1.write(new CheckpointBarrier(7, 0, CheckpointType.CHECKPOINT));
        p0.close();
        p1.close();
        releaseReader.countDown();

        reader.join(10000);
        assertNull(err.get(), () -> "Reader thread must not throw: " + err.get());
        assertEquals(Arrays.asList(5L, 7L), emitted,
                "Aborted pending barrier 6 must be dropped (never emitted); barrier 7 must still emit: " + emitted);
        assertTrue(gate.getInFlightBarrierIds().isEmpty(),
                "No alignment may be left in-flight after abort + drain: " + gate.getInFlightBarrierIds());
    }
}
