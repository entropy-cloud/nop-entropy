package io.nop.stream.core.execution;

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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P1-INV-2 (AR-02 Phase 3) regression tests: the alignment-timeout / unaligned
 * escape evaluation must be decoupled from data return. The legacy sweep-level
 * check only ran after a full read round with zero returns, so a channel that
 * kept delivering data during alignment starved the gates forever — failure
 * detection degraded to the coordinator-side checkpoint timeout (600s default).
 *
 * <p>Each test keeps the non-aligned channel busy with a continuous stream of
 * records: every {@code read()} returns data, yet the entry-level elapsed check
 * must still fire the escape (unaligned) / fail-fast (timeout) at the configured
 * thresholds.
 */
class TestInputGateAlignmentStarvationFix {

    /**
     * 墙钟断言容忍带：测试侧与实现侧各自采样 System.currentTimeMillis()，
     * 负载/时钟步进下两侧采样存在偏差；下界断言需容忍该偏差（主判定用异常携带的
     * 网关自身 elapsed 样本，见各用例）。
     */
    private static final long CLOCK_TOLERANCE_MS = 200L;

    @Test
    void testAlignmentTimeoutFiresWithContinuousTraffic() throws Exception {
        ResultPartition p0 = new ResultPartition();
        ResultPartition p1 = new ResultPartition();
        List<InputChannel> channels = Arrays.asList(new InputChannel(p0), new InputChannel(p1));

        long alignmentTimeoutMs = 300L;
        InputGate gate = new InputGate(channels, null, true, alignmentTimeoutMs);

        // Channel 0 delivers its barrier and aligns (blocks ch0); channel 1 keeps
        // delivering records — the alignment can never complete.
        p0.write(new CheckpointBarrier(1, 0, CheckpointType.CHECKPOINT));

        long start = System.currentTimeMillis();
        int[] traffic = {0};
        StreamException thrown = assertThrows(StreamException.class, () -> {
            while (System.currentTimeMillis() - start < alignmentTimeoutMs + 5000L) {
                p1.write(new StreamRecord<>("busy-" + (traffic[0]++)));
                gate.read();
            }
        }, "Alignment timeout must fire while the other channel keeps delivering data");
        long elapsed = System.currentTimeMillis() - start;

        assertEquals("nop.err.stream.barrier-alignment-timeout", thrown.getErrorCode().toString(),
                "Must be the alignment-timeout error, not a silent hang");
        // 主判定用网关自身采样的 elapsed（异常携带的 timeoutMs 参数）：墙钟在负载/时钟步进下
        // 与实现侧采样点之间存在偏差，下界墙钟断言在全量 reactor 下偶发假红（2026-08-28）
        long gateElapsed = ((Number) thrown.getParam("timeoutMs")).longValue();
        assertTrue(gateElapsed >= alignmentTimeoutMs,
                "Gate must fire on its own elapsed sample no earlier than the timeout");
        assertTrue(elapsed >= alignmentTimeoutMs - CLOCK_TOLERANCE_MS,
                "Wall-clock elapsed must be at least the timeout (minus clock tolerance)");
        assertTrue(traffic[0] > 0, "The traffic channel must actually have delivered records "
                + "(the starvation scenario is real, not a stuck channel)");
    }

    @Test
    void testUnalignedEscapeFiresWithContinuousTraffic() throws Exception {
        ResultPartition p0 = new ResultPartition();
        ResultPartition p1 = new ResultPartition();
        List<InputChannel> channels = Arrays.asList(new InputChannel(p0), new InputChannel(p1));

        long unalignedThreshold = 200L;
        long alignmentTimeout = 5000L;
        InputGate gate = new InputGate(channels, null, true, alignmentTimeout,
                true, unalignedThreshold);

        // Channel 0 aligns (blocks ch0); channel 1 never delivers its barrier but
        // keeps delivering records. The escape must fire at the threshold despite
        // the continuous traffic (pre-fix: starved, never switches).
        p0.write(new CheckpointBarrier(1, 0, CheckpointType.CHECKPOINT));

        long start = System.currentTimeMillis();
        CheckpointBarrier emitted = null;
        int i = 0;
        while (System.currentTimeMillis() - start < alignmentTimeout) {
            p1.write(new StreamRecord<>("busy-" + (i++)));
            Optional<StreamElement> opt = gate.read();
            if (opt.isPresent() && opt.get().isCheckpointBarrier()) {
                emitted = opt.get().asCheckpointBarrier();
                break;
            }
        }
        long elapsed = System.currentTimeMillis() - start;

        assertNotNull(emitted,
                "Unaligned escape must fire while the other channel keeps delivering data "
                        + "(pre-fix the sweep-level check was starved and never switched)");
        assertEquals(1L, emitted.getId());
        assertTrue(elapsed >= unalignedThreshold - CLOCK_TOLERANCE_MS,
                "Must wait at least the escape threshold (minus clock tolerance)");
        assertTrue(elapsed < alignmentTimeout, "Must NOT reach the fail-fast timeout");
        assertTrue(i > 0, "The traffic channel must actually have delivered records");
    }

    /**
     * Idle-period companion (Phase 2 scenario): with the alignment stalled and ALL
     * channels idle, the entry-level elapsed check must still fire — the idle-return
     * of {@code read()} must not skip the timeout evaluation. Pre-fix the sweep-level
     * check also fired here; this pins the Phase 2 + Phase 3 combination.
     */
    @Test
    void testAlignmentTimeoutFiresDuringIdleAlignment() throws Exception {
        ResultPartition p0 = new ResultPartition();
        ResultPartition p1 = new ResultPartition();
        List<InputChannel> channels = Arrays.asList(new InputChannel(p0), new InputChannel(p1));

        long alignmentTimeoutMs = 300L;
        InputGate gate = new InputGate(channels, null, true, alignmentTimeoutMs);

        p0.write(new CheckpointBarrier(1, 0, CheckpointType.CHECKPOINT));

        long start = System.currentTimeMillis();
        StreamException thrown = assertThrows(StreamException.class, () -> {
            while (System.currentTimeMillis() - start < alignmentTimeoutMs + 5000L) {
                gate.read(); // idle-returns (Phase 2) — the entry check must still fire
            }
        });
        long elapsed = System.currentTimeMillis() - start;

        assertEquals("nop.err.stream.barrier-alignment-timeout", thrown.getErrorCode().toString());
        long gateElapsed = ((Number) thrown.getParam("timeoutMs")).longValue();
        assertTrue(gateElapsed >= alignmentTimeoutMs,
                "Gate must fire on its own elapsed sample no earlier than the timeout");
        assertTrue(elapsed >= alignmentTimeoutMs - CLOCK_TOLERANCE_MS,
                "Wall-clock elapsed must be at least the timeout (minus clock tolerance)");
        assertTrue(elapsed < alignmentTimeoutMs + 5000L,
                "Must not hang past the timeout (idle-return must not skip the elapsed check)");
    }
}
