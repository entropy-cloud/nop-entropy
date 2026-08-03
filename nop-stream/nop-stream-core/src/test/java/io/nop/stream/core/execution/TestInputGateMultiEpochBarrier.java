package io.nop.stream.core.execution;

import io.nop.stream.core.checkpoint.CheckpointBarrier;
import io.nop.stream.core.checkpoint.CheckpointType;
import io.nop.stream.core.streamrecord.StreamElement;
import io.nop.stream.core.streamrecord.StreamRecord;
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
 * Stage 45 (multi-epoch): proves {@link InputGate} can simultaneously track the
 * alignment of multiple in-flight barrier ids without throwing, that completing
 * one barrier's alignment does not disturb another's state, and that aborting an
 * epoch mid-alignment lets the next epoch align cleanly (design §2.8.1 D1).
 *
 * <p>Aligned barriers serialize via channel blocking, so the abort-precision test
 * drives the read loop in a background thread (the abort arrives via the control
 * channel while alignment is blocked — mirroring the production local-abort path).
 */
class TestInputGateMultiEpochBarrier {

    @Test
    void testTwoBarrierIdsCanAlignSequentiallyWithoutThrowing() throws Exception {
        // Aligned mode: barrier 1 aligns and emits, then barrier 2 aligns and emits.
        // No ERR_STREAM_CHECKPOINT_ABORTED on the second id (legacy single-in-flight throw).
        ResultPartition p0 = new ResultPartition();
        ResultPartition p1 = new ResultPartition();
        InputGate gate = new InputGate(Arrays.asList(new InputChannel(p0), new InputChannel(p1)),
                null, true);

        // Barrier 1 on both channels -> aligns, emits.
        p0.write(new CheckpointBarrier(1, 0, CheckpointType.CHECKPOINT));
        p1.write(new CheckpointBarrier(1, 0, CheckpointType.CHECKPOINT));
        // Barrier 2 on both channels -> aligns, emits.
        p0.write(new CheckpointBarrier(2, 0, CheckpointType.CHECKPOINT));
        p1.write(new CheckpointBarrier(2, 0, CheckpointType.CHECKPOINT));
        p0.close();
        p1.close();

        List<Long> emitted = drainBarriers(gate);

        assertEquals(Arrays.asList(1L, 2L), emitted,
                "Both barriers must align and emit in id order without throwing");
    }

    @Test
    void testAbortedAlignmentLetsNextBarrierAlignCleanly() throws Exception {
        // Precision path: barrier 1 is mid-aligning (ch0 only) when the control
        // channel aborts it. The late straggler barrier 1 on ch1 must be discarded,
        // and barrier 2 must then align without corruption or throw.
        ResultPartition p0 = new ResultPartition();
        ResultPartition p1 = new ResultPartition();
        InputGate gate = new InputGate(Arrays.asList(new InputChannel(p0), new InputChannel(p1)),
                null, true, 30000L);

        // Only ch0 delivers barrier 1 -> alignment will block on ch1.
        p0.write(new CheckpointBarrier(1, 0, CheckpointType.CHECKPOINT));

        AtomicReference<Throwable> err = new AtomicReference<>();
        List<Long> emitted = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch barrierObserved = new CountDownLatch(1);
        Thread reader = new Thread(() -> {
            try {
                while (true) {
                    Optional<StreamElement> e = gate.read();
                    if (!e.isPresent()) break;
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

        // Wait until barrier 1 is observed in-flight by the reader loop.
        long deadline = System.currentTimeMillis() + 5000L;
        while (System.currentTimeMillis() < deadline && !gate.getInFlightBarrierIds().contains(1L)) {
            Thread.sleep(10);
        }
        assertTrue(gate.getInFlightBarrierIds().contains(1L),
                "Barrier 1 must be in-flight before abort");

        // Abort epoch 1 via the control channel (epoch-precise abort).
        gate.abortBarrierAlignment(1L);
        assertFalse(gate.getInFlightBarrierIds().contains(1L), "Barrier 1 removed after abort");

        // ch1 delivers barrier 1 LATE (straggler) -> must be discarded.
        p1.write(new CheckpointBarrier(1, 0, CheckpointType.CHECKPOINT));
        // Both channels deliver barrier 2 -> must align and emit.
        p0.write(new CheckpointBarrier(2, 0, CheckpointType.CHECKPOINT));
        p1.write(new CheckpointBarrier(2, 0, CheckpointType.CHECKPOINT));
        p0.close();
        p1.close();

        reader.join(5000);
        assertNull(err.get(), () -> "Reader thread should not throw: " + err.get());
        assertEquals(Arrays.asList(2L), emitted,
                "Straggler barrier 1 must be discarded; only barrier 2 emits");
    }

    @Test
    void testCompletingOneBarrierDoesNotCorruptAnotherState() throws Exception {
        // After barrier 1 fully aligns (emitted, state removed), barrier 2's
        // alignment starts fresh: barrierReceived/channels are not stale.
        ResultPartition p0 = new ResultPartition();
        ResultPartition p1 = new ResultPartition();
        InputGate gate = new InputGate(Arrays.asList(new InputChannel(p0), new InputChannel(p1)),
                null, true);

        p0.write(new CheckpointBarrier(1, 0, CheckpointType.CHECKPOINT));
        p1.write(new CheckpointBarrier(1, 0, CheckpointType.CHECKPOINT));
        // A record between barrier 1 and barrier 2 on ch0 (post-barrier for epoch 1).
        p0.write(new StreamRecord<>("after-1"));
        p0.write(new CheckpointBarrier(2, 0, CheckpointType.CHECKPOINT));
        p1.write(new CheckpointBarrier(2, 0, CheckpointType.CHECKPOINT));
        p0.close();
        p1.close();

        List<Object> emitted = new ArrayList<>();
        while (true) {
            Optional<StreamElement> e = gate.read();
            if (!e.isPresent()) break;
            if (e.get().isCheckpointBarrier()) {
                emitted.add(e.get().asCheckpointBarrier().getId());
            } else if (e.get().isRecord()) {
                emitted.add(e.get().asRecord().getValue());
            }
        }

        // Barrier 1, then its post-barrier record, then barrier 2 — all in order.
        assertEquals(Arrays.asList(1L, "after-1", 2L), emitted,
                "Completing barrier 1 must not leave stale state for barrier 2");
    }

    private List<Long> drainBarriers(InputGate gate) throws Exception {
        List<Long> emitted = new ArrayList<>();
        while (true) {
            Optional<StreamElement> e = gate.read();
            if (!e.isPresent()) break;
            if (e.get().isCheckpointBarrier()) {
                emitted.add(e.get().asCheckpointBarrier().getId());
            }
        }
        return emitted;
    }
}
