package io.nop.stream.core.execution;

import io.nop.stream.core.checkpoint.ChannelState;
import io.nop.stream.core.checkpoint.CheckpointBarrier;
import io.nop.stream.core.checkpoint.CheckpointType;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.streamrecord.StreamElement;
import io.nop.stream.core.streamrecord.StreamRecord;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Plan 369 Phase 1 C1: savepoint barriers are exempt from the aligned→unaligned
 * fallback. Under backpressure (a channel never delivers its barrier):
 * <ul>
 *   <li>SAVEPOINT / TERMINAL_SAVEPOINT / EXPORTED_SAVEPOINT barriers are NOT
 *       degraded to unaligned mode (no channel-state capture, no early emit) —
 *       they stay aligned until the absolute {@code barrierAlignmentTimeout},
 *       which fails loud with {@code ERR_STREAM_BARRIER_ALIGNMENT_TIMEOUT}
 *       (Flink {@code CheckpointOptions.alignedNoTimeout} semantics).</li>
 *   <li>A plain CHECKPOINT barrier under the same backpressure still degrades
 *       to unaligned after {@code unalignedThreshold} (Stage 43 behavior
 *       preserved).</li>
 * </ul>
 *
 * <p>Pre-fix repro (silent savepoint degrade) recorded in
 * {@code _tmp/r6-p1-c1-repro.log}: the gate switched a SAVEPOINT barrier to
 * unaligned mode and captured in-flight data after the threshold.
 */
class TestInputGateSavepointAligned {

    private static final long UNALIGNED_THRESHOLD = 100L;
    private static final long ALIGNMENT_TIMEOUT = 700L;

    private interface BarrierScenario {
        void writeBarriers(ResultPartition p0) throws Exception;
    }

    /**
     * Shared body: channel 0 delivers the barrier then a post-barrier record;
     * channel 1 is stuck (never delivers). Asserts the barrier is NOT emitted
     * (no unaligned degrade) within the threshold window and that the read
     * ultimately throws the loud alignment timeout.
     */
    private void assertSavepointStaysAlignedAndTimesOut(BarrierScenario scenario) throws Exception {
        ResultPartition p0 = new ResultPartition();
        ResultPartition p1 = new ResultPartition();
        List<InputChannel> channels = Arrays.asList(new InputChannel(p0), new InputChannel(p1));

        InputGate gate = new InputGate(channels, null, true, ALIGNMENT_TIMEOUT,
                true, UNALIGNED_THRESHOLD);

        scenario.writeBarriers(p0);

        long start = System.currentTimeMillis();
        try {
            while (System.currentTimeMillis() - start < ALIGNMENT_TIMEOUT * 3) {
                gate.read();
            }
            fail("Expected ERR_STREAM_BARRIER_ALIGNMENT_TIMEOUT for a savepoint under backpressure");
        } catch (StreamException e) {
            assertEquals("nop.err.stream.barrier-alignment-timeout", e.getErrorCode().toString(),
                    "Savepoint alignment stall must fail loud with the typed alignment timeout");
        }

        // No unaligned degrade ever happened: no channel state was captured and
        // the threshold window passed without an early barrier emission.
        assertNull(gate.consumePendingChannelState(),
                "Savepoint barrier must NOT capture channel state (no unaligned degrade)");
        assertTrue(System.currentTimeMillis() - start >= ALIGNMENT_TIMEOUT,
                "The savepoint must stay aligned until the absolute alignment timeout");
    }

    @Test
    void testSavepointNotDegradedTimesOutLoud() throws Exception {
        assertSavepointStaysAlignedAndTimesOut(
                p0 -> p0.write(new CheckpointBarrier(1, 0, CheckpointType.SAVEPOINT)));
    }

    @Test
    void testTerminalSavepointNotDegradedTimesOutLoud() throws Exception {
        assertSavepointStaysAlignedAndTimesOut(
                p0 -> p0.write(new CheckpointBarrier(1, 0, CheckpointType.TERMINAL_SAVEPOINT)));
    }

    @Test
    void testExportedSavepointNotDegradedTimesOutLoud() throws Exception {
        assertSavepointStaysAlignedAndTimesOut(
                p0 -> p0.write(new CheckpointBarrier(1, 0, CheckpointType.EXPORTED_SAVEPOINT)));
    }

    @Test
    void testCompletedPointNotDegradedTimesOutLoud() throws Exception {
        // COMPLETED_POINT_TYPE is a final consistent cut — same savepoint
        // semantics (isCheckpoint() == false ⇒ no degrade).
        assertSavepointStaysAlignedAndTimesOut(
                p0 -> p0.write(new CheckpointBarrier(1, 0, CheckpointType.COMPLETED_POINT_TYPE)));
    }

    /**
     * Control: a plain CHECKPOINT barrier under the same backpressure still
     * degrades to unaligned after the threshold (Stage 43 behavior unchanged).
     */
    @Test
    void testPlainCheckpointStillDegrades() throws Exception {
        ResultPartition p0 = new ResultPartition();
        ResultPartition p1 = new ResultPartition();
        List<InputChannel> channels = Arrays.asList(new InputChannel(p0), new InputChannel(p1));

        InputGate gate = new InputGate(channels, null, true, ALIGNMENT_TIMEOUT,
                true, UNALIGNED_THRESHOLD);

        p0.write(new CheckpointBarrier(1, 0, CheckpointType.CHECKPOINT));
        p0.write(new StreamRecord<>("post-barrier-on-aligned"));

        long start = System.currentTimeMillis();
        CheckpointBarrier emitted = null;
        while (System.currentTimeMillis() - start < ALIGNMENT_TIMEOUT) {
            Optional<StreamElement> opt = gate.read();
            if (opt.isPresent() && opt.get().isCheckpointBarrier()) {
                emitted = opt.get().asCheckpointBarrier();
                break;
            }
        }

        assertNotNull(emitted, "Plain CHECKPOINT should still degrade to unaligned");
        assertEquals(1L, emitted.getId());
        ChannelState cs = gate.consumePendingChannelState();
        assertNotNull(cs, "CHECKPOINT degrade captures channel state");
        assertEquals(1, cs.getRecords(0).size(), "Post-barrier record on the aligned channel captured");
    }

    /**
     * A savepoint whose alignment completes normally (all channels deliver)
     * is emitted aligned — the exemption does not disturb the healthy path.
     */
    @Test
    void testSavepointHealthyAlignmentStillEmits() throws Exception {
        ResultPartition p0 = new ResultPartition();
        ResultPartition p1 = new ResultPartition();
        List<InputChannel> channels = Arrays.asList(new InputChannel(p0), new InputChannel(p1));

        InputGate gate = new InputGate(channels, null, true, ALIGNMENT_TIMEOUT,
                true, UNALIGNED_THRESHOLD);

        p0.write(new CheckpointBarrier(1, 0, CheckpointType.SAVEPOINT));
        p1.write(new CheckpointBarrier(1, 0, CheckpointType.SAVEPOINT));

        long start = System.currentTimeMillis();
        CheckpointBarrier emitted = null;
        while (System.currentTimeMillis() - start < 2000L) {
            Optional<StreamElement> opt = gate.read();
            if (opt.isPresent() && opt.get().isCheckpointBarrier()) {
                emitted = opt.get().asCheckpointBarrier();
                break;
            }
        }

        assertNotNull(emitted, "Healthy savepoint alignment emits the barrier");
        assertNull(gate.consumePendingChannelState(),
                "Aligned completion captures no channel state");
    }
}
