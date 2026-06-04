package io.nop.stream.runtime.checkpoint.barrier;

import io.nop.stream.core.checkpoint.CheckpointBarrier;
import io.nop.stream.core.checkpoint.CheckpointType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestBarrierAlignerLoopComplete {

    @Test
    void testProcessBarrierTriggersCompleteForAllCompletedCheckpoints() {
        BarrierAligner aligner = new BarrierAligner(2);

        CheckpointBarrier b1 = new CheckpointBarrier(1, 0, CheckpointType.CHECKPOINT);
        CheckpointBarrier b2 = new CheckpointBarrier(2, 0, CheckpointType.CHECKPOINT);

        assertTrue(aligner.processBarrier(b1, 0));
        assertNull(aligner.pollAlignedBarrier());

        assertTrue(aligner.processBarrier(b1, 1));
        AlignedBarrier first = aligner.pollAlignedBarrier();
        assertNotNull(first);
        assertEquals(1, first.getCheckpointId());

        assertTrue(aligner.processBarrier(b2, 0));
        assertNull(aligner.pollAlignedBarrier());

        assertTrue(aligner.processBarrier(b2, 1));
        AlignedBarrier second = aligner.pollAlignedBarrier();
        assertNotNull(second);
        assertEquals(2, second.getCheckpointId());

        aligner.close();
    }

    @Test
    void testNoCompletionUntilAllInputsReceived() {
        BarrierAligner aligner = new BarrierAligner(3);
        CheckpointBarrier b1 = new CheckpointBarrier(1, 0, CheckpointType.CHECKPOINT);

        assertFalse(aligner.processBarrier(b1, 0));
        assertFalse(aligner.processBarrier(b1, 1));
        assertTrue(aligner.processBarrier(b1, 2));
        assertNotNull(aligner.pollAlignedBarrier());

        aligner.close();
    }
}
