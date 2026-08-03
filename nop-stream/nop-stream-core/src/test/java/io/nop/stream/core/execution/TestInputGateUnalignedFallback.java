package io.nop.stream.core.execution;

import io.nop.stream.core.checkpoint.ChannelState;
import io.nop.stream.core.checkpoint.CheckpointBarrier;
import io.nop.stream.core.checkpoint.CheckpointType;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.streamrecord.StreamElement;
import io.nop.stream.core.streamrecord.StreamRecord;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Stage 43, Phase 3: aligned→unaligned fallback in {@link InputGate}.
 *
 * <p>Verifies that when alignment does not complete within {@code unalignedThreshold}
 * and unaligned is enabled, the gate (a) captures in-flight channel data with the
 * correct per-channel semantics, (b) emits the barrier without throwing
 * {@code ERR_STREAM_BARRIER_ALIGNMENT_TIMEOUT}, and (c) exposes the captured state
 * via {@link InputGate#consumePendingChannelState()} for the task thread to forward
 * to {@link CheckpointBarrierTracker} (plan guide #23 接线验证).
 *
 * <p><strong>Note on non-aligned channel semantics</strong>: in nop-stream's
 * STRICT_EXACTLY_ONCE aligned mode, records from <em>non-blocked</em> (non-aligned)
 * channels continue to flow to the operator during alignment — they are read and
 * delivered, not buffered. Therefore at the unit-test level (no slow operator),
 * a non-aligned channel's buffer is typically empty by the time the switch fires.
 * The non-aligned <em>under backpressure</em> capture (records pile up faster than
 * the read loop drains) is proven by the Phase 4 E2E backpressure test. The
 * per-channel capture mechanism itself is unit-tested in
 * {@code TestChannelStateCapture}.
 */
class TestInputGateUnalignedFallback {

    /**
     * Under sustained alignment stall (channel 1 never delivers its barrier), the
     * gate switches to unaligned mode after unalignedThreshold and completes the
     * barrier — it does NOT throw alignment-timeout. Records on the aligned
     * channel (post-barrier) are captured.
     */
    @Test
    void testUnalignedFallbackEmitsBarrierInsteadOfTimeout() throws Exception {
        ResultPartition p0 = new ResultPartition();
        ResultPartition p1 = new ResultPartition();
        List<InputChannel> channels = Arrays.asList(new InputChannel(p0), new InputChannel(p1));

        long unalignedThreshold = 100L;
        long alignmentTimeout = 5000L;
        InputGate gate = new InputGate(channels, null, true, alignmentTimeout,
                true, unalignedThreshold);

        // Channel 0 delivers its barrier then a post-barrier (new-epoch) record.
        p0.write(new CheckpointBarrier(1, 0, CheckpointType.CHECKPOINT));
        p0.write(new StreamRecord<>("post-barrier-on-aligned"));
        // Channel 1 is "stuck" (never sends barrier).
        long start = System.currentTimeMillis();
        CheckpointBarrier emitted = null;
        ChannelState cs = null;
        while (System.currentTimeMillis() - start < alignmentTimeout) {
            Optional<StreamElement> opt = gate.read();
            if (opt.isPresent() && opt.get().isCheckpointBarrier()) {
                emitted = opt.get().asCheckpointBarrier();
                cs = gate.consumePendingChannelState();
                break;
            }
        }
        long elapsed = System.currentTimeMillis() - start;

        assertNotNull(emitted, "Should emit the (unaligned) barrier instead of throwing");
        assertEquals(1L, emitted.getId());
        assertTrue(elapsed >= unalignedThreshold, "Should wait at least the threshold");
        assertTrue(elapsed < alignmentTimeout,
                "Should NOT reach alignment timeout (took " + elapsed + "ms)");

        // Channel 0 (aligned, blocked): its post-barrier record was buffered and
        // is captured on the unaligned switch.
        assertNotNull(cs, "Channel state should be captured on unaligned switch");
        List<StreamElement> ch0 = cs.getRecords(0);
        assertEquals(1, ch0.size(), "Post-barrier record on aligned channel must be captured");
        assertEquals("post-barrier-on-aligned", ch0.get(0).asRecord().getValue());

        // After consume, the pending state is cleared.
        assertNull(gate.consumePendingChannelState());
    }

    /**
     * 接线验证 (plan guide #23): proves {@link InputGate} actually invokes
     * {@link InputChannel#captureInFlightData(boolean)} on each of its channels at
     * the unaligned switch, with the correct {@code barrierReceived} flag per
     * channel (true for aligned channels, false for non-aligned). Uses a recording
     * InputChannel subclass so the assertion is deterministic (no timing race).
     *
     * <p>This is the unit-level proof that the unaligned-switch → channel-state-
     * capture wiring is connected. The end-to-end capture-under-backpressure is
     * proven by the Phase 4 E2E test.
     */
    @Test
    void testUnalignedSwitchInvokesCaptureOnEachChannelWithCorrectFlag() throws Exception {
        // Aligned channel: delivers its barrier so the gate marks it aligned.
        RecordingChannel aligned = new RecordingChannel();
        aligned.deliverBarrier(11L);

        // Non-aligned channel: never delivers a barrier.
        RecordingChannel nonAligned = new RecordingChannel();

        InputGate gate = new InputGate(Arrays.asList(aligned, nonAligned), null, true,
                5000L, true, 100L);

        long start = System.currentTimeMillis();
        CheckpointBarrier emitted = null;
        while (System.currentTimeMillis() - start < 3000L) {
            Optional<StreamElement> opt = gate.read();
            if (opt.isPresent() && opt.get().isCheckpointBarrier()) {
                emitted = opt.get().asCheckpointBarrier();
                break;
            }
        }
        assertNotNull(emitted, "Unaligned barrier should be emitted");

        // The aligned channel must be captured with barrierReceived=true.
        assertTrue(aligned.captureCalled, "Aligned channel must have captureInFlightData invoked");
        assertTrue(aligned.lastBarrierReceivedFlag,
                "Aligned channel capture must receive barrierReceived=true");

        // The non-aligned channel must be captured with barrierReceived=false.
        assertTrue(nonAligned.captureCalled,
                "Non-aligned channel must have captureInFlightData invoked");
        assertFalse(nonAligned.lastBarrierReceivedFlag,
                "Non-aligned channel capture must receive barrierReceived=false");

        ChannelState cs = gate.consumePendingChannelState();
        assertNotNull(cs);
        // Aligned channel returned its post-barrier record; non-aligned returned
        // its buffered pre-barrier record (the recording channel returns canned data).
        assertTrue(cs.getRecords(0).size() >= 1, "Aligned channel post-barrier data captured");
        assertTrue(cs.getRecords(1).size() >= 1, "Non-aligned channel buffered data captured");
    }

    /**
     * A recording InputChannel that tracks captureInFlightData invocations and
     * returns canned in-flight data so the InputGate wiring can be asserted
     * deterministically.
     */
    private static class RecordingChannel extends InputChannel {
        boolean captureCalled = false;
        boolean lastBarrierReceivedFlag = false;

        RecordingChannel() {
            super(new ResultPartition(16));
        }

        /**
         * Delivers a barrier on this channel so the InputGate marks it aligned and
         * blocks it. The post-barrier capture is simulated via the canned return of
         * {@link #captureInFlightData(boolean)} so the real buffer need not hold it
         * (avoids a blocking write into a small queue before the gate starts reading).
         */
        void deliverBarrier(long checkpointId) throws InterruptedException {
            getPartition().write(new CheckpointBarrier(checkpointId, 0, CheckpointType.CHECKPOINT));
        }

        @Override
        public java.util.List<StreamElement> captureInFlightData(boolean barrierReceived) {
            captureCalled = true;
            lastBarrierReceivedFlag = barrierReceived;
            // Return canned data so channel state is non-empty regardless of the
            // read loop having drained the real buffer, and so the assertion is
            // deterministic.
            java.util.List<StreamElement> canned = new ArrayList<>();
            canned.add(new StreamRecord<>(barrierReceived ? "captured-post-barrier"
                    : "captured-pre-barrier"));
            return canned;
        }
    }

    /**
     * When unaligned is disabled, the legacy behavior holds: alignment timeout
     * throws ERR_STREAM_BARRIER_ALIGNMENT_TIMEOUT (no mode switch).
     */
    @Test
    void testUnalignedDisabledPreservesTimeoutBehavior() throws Exception {
        ResultPartition p0 = new ResultPartition();
        ResultPartition p1 = new ResultPartition();
        List<InputChannel> channels = Arrays.asList(new InputChannel(p0), new InputChannel(p1));

        long alignmentTimeout = 300L;
        InputGate gate = new InputGate(channels, null, true, alignmentTimeout,
                false, 1000L); // unaligned disabled

        p0.write(new CheckpointBarrier(1, 0, CheckpointType.CHECKPOINT));

        StreamException thrown = assertThrows(StreamException.class, () -> {
            while (true) {
                gate.read();
            }
        });
        assertEquals("nop.err.stream.barrier-alignment-timeout", thrown.getErrorCode().toString());
        // No channel state captured when unaligned is disabled.
        assertNull(gate.consumePendingChannelState());
    }

    /**
     * Single-input task: no cross-channel alignment, unaligned never triggers
     * (the barrier completes immediately). Channel state is null/empty.
     */
    @Test
    void testSingleChannelNeverSwitchesToUnaligned() throws Exception {
        ResultPartition p0 = new ResultPartition();
        InputGate gate = new InputGate(Arrays.asList(new InputChannel(p0)), null, true,
                5000L, true, 100L);

        p0.write(new StreamRecord<>("data"));
        p0.write(new CheckpointBarrier(3, 0, CheckpointType.CHECKPOINT));
        p0.close();

        List<StreamElement> delivered = new ArrayList<>();
        boolean foundBarrier = false;
        while (true) {
            Optional<StreamElement> opt = gate.read();
            if (!opt.isPresent()) break;
            if (opt.get().isCheckpointBarrier()) {
                foundBarrier = true;
                assertNull(gate.consumePendingChannelState(),
                        "Single channel → no unaligned switch → no channel state");
            } else if (opt.get().isRecord()) {
                delivered.add(opt.get());
            }
        }
        assertTrue(foundBarrier, "Single-channel barrier should complete aligned (no switch)");
        assertEquals(1, delivered.size(), "Data record should be delivered");
    }

    /**
     * Stage 45 D4 / Stage 47 focused test: the unaligned + multi-in-flight
     * guard at {@code InputGate.switchToUnalignedAndEmit} fails fast with
     * {@code ERR_STREAM_INVALID_STATE} when the unaligned threshold fires while
     * more than one barrier is in-flight. This is the direct assertion test for
     * the guard Stage 45 left as a successor placeholder (Stage 47 closes the
     * follow-up).
     *
     * <p>Setup: two channels, unaligned enabled. Channel 0 delivers barrier 1
     * (aligns on channel 0, blocks it). Channel 1 delivers barrier 2 — a
     * different epoch — which creates a second in-flight alignment. With both
     * channels blocked and two alignments pending, the unaligned threshold
     * fires the D4 guard on the oldest alignment.
     */
    @Test
    void testUnalignedMultiInFlightFailsFastD4Guard() throws Exception {
        ResultPartition p0 = new ResultPartition();
        ResultPartition p1 = new ResultPartition();
        List<InputChannel> channels = Arrays.asList(new InputChannel(p0), new InputChannel(p1));

        long unalignedThreshold = 100L;
        long alignmentTimeout = 5000L;
        InputGate gate = new InputGate(channels, null, true, alignmentTimeout,
                true, unalignedThreshold);

        // Channel 0 delivers barrier 1; channel 1 delivers barrier 2 (a
        // different epoch). Aligned serialization lets both become in-flight:
        // barrier 1 is received on channel 0 (blocks ch0, waits for ch1);
        // barrier 2 is then read on channel 1 (blocks ch1, waits for ch0).
        // Result: two in-flight alignments, both channels blocked.
        p0.write(new CheckpointBarrier(1, 0, CheckpointType.CHECKPOINT));
        p1.write(new CheckpointBarrier(2, 0, CheckpointType.CHECKPOINT));

        StreamException thrown = assertThrows(StreamException.class, () -> {
            long start = System.currentTimeMillis();
            while (System.currentTimeMillis() - start < alignmentTimeout) {
                gate.read();
            }
        });
        assertEquals("nop.err.stream.invalid-state", thrown.getErrorCode().toString(),
                "D4 guard: unaligned + multi-in-flight must fail-fast with ERR_STREAM_INVALID_STATE "
                        + "(not silently capture state for the wrong epoch)");
        // No channel state was captured — the guard fires BEFORE capture.
        assertNull(gate.consumePendingChannelState(),
                "D4 guard must fire before any channel state is captured");
    }
}
