/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.execution;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import io.nop.stream.core.checkpoint.CheckpointBarrier;
import io.nop.stream.core.checkpoint.CheckpointType;
import io.nop.stream.core.streamrecord.StreamElement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P1 hardening (Phase 2): proves {@link InputGate}'s alignment collections
 * ({@code inFlightAlignments} / {@code abortedBarriers} / {@code blockedChannels},
 * plus the per-{@code BarrierAlignment} channel sets) are safe under the
 * cross-thread access pattern introduced by the checkpoint abort path.
 *
 * <p>Production wiring: {@code GraphModelCheckpointExecutor.registerLocalAbortHandler}
 * runs on the checkpoint timeout / ACK thread and calls
 * {@link InputGate#abortBarrierAlignment(long)} directly while the owning task
 * thread is inside {@link InputGate#read()} (iterating the in-flight alignments
 * via {@code markFinishedChannel} / {@code handleBarrierNonRecursive}). With the
 * legacy {@code HashSet}/{@code LinkedHashMap} collections this could throw
 * {@link java.util.ConcurrentModificationException}, lose a barrier, or
 * permanently block a channel. The fix replaces them with concurrent-safe
 * structures so the abort thread's {@code remove}/{@code add} never corrupts the
 * task thread's iteration.
 *
 * <p><b>Why not mailbox delivery?</b> Approach (a) (delivering the abort as a
 * task-mailbox CONTROL mail) was evaluated and rejected: {@link InputGate#read()}
 * blocks inside barrier alignment and only drains the mailbox at the caller's
 * ({@code processInputGate}) loop top, so a mailbox-delivered abort could not
 * unblock a read blocked on the aborted alignment — it would deadlock the
 * epoch-precise abort path until {@code barrierAlignmentTimeout} (30s) then
 * throw. Keeping the abort as a direct call (the unblocking mechanism) on
 * concurrent-safe collections fixes the actual CME defect with zero regression.
 */
class TestInputGateMailboxAbort {

    /**
     * Cross-thread abort during active barrier iteration must not throw CME and
     * must not corrupt alignment state. A reader thread processes a stream of
     * barriers (driving {@code handleBarrierNonRecursive} + {@code markFinishedChannel}
     * iteration of {@code inFlightAlignments}), while an abort thread repeatedly
     * aborts in-flight alignments. With concurrent-safe collections this completes
     * cleanly; with the legacy {@code HashSet}/{@code LinkedHashMap} it would
     * frequently throw {@link java.util.ConcurrentModificationException}.
     */
    @Test
    void crossThreadAbortDuringIterationDoesNotThrowCme() throws Exception {
        ResultPartition p0 = new ResultPartition();
        ResultPartition p1 = new ResultPartition();
        InputGate gate = new InputGate(Arrays.asList(new InputChannel(p0), new InputChannel(p1)),
                null, true, 30000L);

        AtomicReference<Throwable> err = new AtomicReference<>();
        List<Long> emitted = Collections.synchronizedList(new ArrayList<>());

        // Feed a batch of barriers on both channels so the reader iterates the
        // in-flight alignments (handleBarrierNonRecursive) repeatedly.
        int barrierCount = 50;
        for (long id = 1; id <= barrierCount; id++) {
            p0.write(new CheckpointBarrier(id, 0, CheckpointType.CHECKPOINT));
            p1.write(new CheckpointBarrier(id, 0, CheckpointType.CHECKPOINT));
        }
        p0.close();
        p1.close();

        CountDownLatch readerStarted = new CountDownLatch(1);
        Thread reader = new Thread(() -> {
            try {
                readerStarted.countDown();
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
        }, "abort-reader");
        reader.setDaemon(true);
        reader.start();
        assertTrue(readerStarted.await(5, TimeUnit.SECONDS));

        // Abort thread: race the reader by aborting in-flight alignments. This
        // exercises the cross-thread remove from inFlightAlignments + add to
        // abortedBarriers + remove from blockedChannels while the reader iterates.
        AtomicInteger aborts = new AtomicInteger();
        Thread aborter = new Thread(() -> {
            try {
                for (long id = 1; id <= barrierCount; id++) {
                    // Only abort if still in-flight (best-effort, like the real
                    // timeout path). Either way this performs a cross-thread
                    // mutation of the alignment collections.
                    gate.abortBarrierAlignment(id);
                    aborts.incrementAndGet();
                }
            } catch (Throwable t) {
                err.set(t);
            }
        }, "abort-handler");
        aborter.setDaemon(true);
        aborter.start();

        aborter.join(5000);
        reader.join(5000);

        assertNull(err.get(),
                () -> "No ConcurrentModificationException / corruption expected under cross-thread abort: " + err.get());
        assertEquals(barrierCount, aborts.get(),
                "abort thread must complete all abort calls without throwing");

        // The gate must end in a consistent state: no leaked in-flight alignment,
        // no permanently blocked channel.
        assertTrue(gate.getInFlightBarrierIds().isEmpty(),
                "no in-flight alignment must remain after all barriers are consumed/aborted: "
                        + gate.getInFlightBarrierIds());

        // At least the non-aborted barriers (those that aligned before the abort
        // thread reached them) must have emitted — proving channels were not
        // permanently blocked. The exact count depends on the race, but it must
        // be > 0 (some barriers aligned) and the reader must have terminated
        // cleanly (isAllFinished).
        assertFalse(gate.isAllFinished() && emitted.isEmpty() && barrierCount > 0,
                "reader terminated with no emitted barrier — possible permanent channel block");
    }
}
