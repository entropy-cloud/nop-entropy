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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Phase 3 of plan 172: InputGate barrier alignment cumulative timeout.
 *
 * <p>Validates that when barrier alignment exceeds the configured timeout,
 * readMultiChannel throws ERR_STREAM_BARRIER_ALIGNMENT_TIMEOUT instead of
 * blocking indefinitely.
 */
public class TestInputGateAlignmentTimeout {

    /**
     * Slow channel scenario: one channel sends a barrier, the other never does.
     * Alignment should time out after the configured timeout.
     */
    @Test
    public void testAlignmentTimeoutOnSlowChannel() throws Exception {
        ResultPartition p0 = new ResultPartition();
        ResultPartition p1 = new ResultPartition();
        List<InputChannel> channels = Arrays.asList(new InputChannel(p0), new InputChannel(p1));

        long alignmentTimeoutMs = 500L;
        InputGate gate = new InputGate(channels, null, true, alignmentTimeoutMs);

        // Write a barrier on channel 0 only — channel 1 is "stuck" (never sends barrier)
        p0.write(new CheckpointBarrier(1, 0, CheckpointType.CHECKPOINT));

        long startTime = System.currentTimeMillis();
        StreamException thrown = assertThrows(StreamException.class, () -> {
            // read() will enter readMultiChannel, receive barrier on channel 0,
            // then loop waiting for channel 1's barrier until timeout
            while (true) {
                gate.read();
            }
        });
        long elapsed = System.currentTimeMillis() - startTime;

        // Should time out after ~alignmentTimeoutMs, not hang forever
        assertTrue(elapsed >= alignmentTimeoutMs,
                "Should wait at least the timeout duration before throwing");
        assertTrue(elapsed < alignmentTimeoutMs + 5000,
                "Should not hang much longer than the timeout, took " + elapsed + "ms");

        // Verify it's the alignment timeout error
        assertEquals("nop.err.stream.barrier-alignment-timeout", thrown.getErrorCode().toString(),
                "Should be alignment timeout error: " + thrown.getMessage());
    }

    /**
     * Normal alignment completes before timeout — no exception thrown.
     */
    @Test
    public void testNormalAlignmentCompletesBeforeTimeout() throws Exception {
        ResultPartition p0 = new ResultPartition();
        ResultPartition p1 = new ResultPartition();
        List<InputChannel> channels = Arrays.asList(new InputChannel(p0), new InputChannel(p1));

        // Long timeout — alignment should complete well before
        InputGate gate = new InputGate(channels, null, true, 30000L);

        // Write barriers on both channels
        p0.write(new CheckpointBarrier(1, 0, CheckpointType.CHECKPOINT));
        p1.write(new CheckpointBarrier(1, 0, CheckpointType.CHECKPOINT));
        p0.close();
        p1.close();

        // Should read the aligned barrier without timeout
        boolean foundBarrier = false;
        while (true) {
            Optional<StreamElement> element = gate.read();
            if (!element.isPresent()) break;
            if (element.get().isCheckpointBarrier()) {
                foundBarrier = true;
                assertEquals(1L, element.get().asCheckpointBarrier().getId());
            }
        }

        assertTrue(foundBarrier, "Should have received the aligned barrier");
    }

    /**
     * Finished channel is treated as implicit barrier — does not trigger timeout.
     */
    @Test
    public void testFinishedChannelDoesNotTriggerTimeout() throws Exception {
        ResultPartition p0 = new ResultPartition();
        ResultPartition p1 = new ResultPartition();
        List<InputChannel> channels = Arrays.asList(new InputChannel(p0), new InputChannel(p1));

        long alignmentTimeoutMs = 500L;
        InputGate gate = new InputGate(channels, null, true, alignmentTimeoutMs);

        // Channel 0: send barrier
        p0.write(new CheckpointBarrier(1, 0, CheckpointType.CHECKPOINT));
        // Channel 1: close (finish) without sending a barrier — treated as implicit barrier
        p1.close();
        p0.close();

        // Should complete alignment (channel 1 is finished = implicit barrier)
        boolean foundBarrier = false;
        while (true) {
            Optional<StreamElement> element = gate.read();
            if (!element.isPresent()) break;
            if (element.get().isCheckpointBarrier()) {
                foundBarrier = true;
            }
        }

        assertTrue(foundBarrier, "Should have received aligned barrier (finished channel = implicit barrier)");
    }

    /**
     * No barrier alignment in progress → no timeout check. Normal data reads are unaffected.
     */
    @Test
    public void testNoTimeoutWhenNotAligning() throws Exception {
        ResultPartition p0 = new ResultPartition();
        ResultPartition p1 = new ResultPartition();
        List<InputChannel> channels = Arrays.asList(new InputChannel(p0), new InputChannel(p1));

        long alignmentTimeoutMs = 200L;
        InputGate gate = new InputGate(channels, null, true, alignmentTimeoutMs);

        // Write data only, no barriers — no alignment timeout should apply
        p0.write(new StreamRecord<>("data-0"));
        p1.write(new StreamRecord<>("data-1"));
        p0.close();
        p1.close();

        // Read data — should not timeout even though alignmentTimeout is short
        int recordsRead = 0;
        long startTime = System.currentTimeMillis();
        while (true) {
            Optional<StreamElement> element = gate.read();
            if (!element.isPresent()) break;
            if (element.get().isRecord()) {
                recordsRead++;
            }
        }
        long elapsed = System.currentTimeMillis() - startTime;

        // Should read both records without throwing
        // Note: might hang briefly waiting for more data before isAllFinished becomes true,
        // but should NOT throw alignment timeout since no barrier was received
        assertEquals(2, recordsRead, "Should read both records");
        assertTrue(elapsed < alignmentTimeoutMs + 10000,
                "Should complete quickly without alignment timeout, took " + elapsed + "ms");
    }
}
