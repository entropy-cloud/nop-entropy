package io.nop.stream.runtime.checkpoint.barrier;

import io.nop.stream.core.checkpoint.CheckpointBarrier;
import io.nop.stream.core.checkpoint.CheckpointType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestBarrierAlignerStarvation {

    @Test
    void testPartialBarrierNeverCompletes() {
        BarrierAligner aligner = new BarrierAligner(3);

        CheckpointBarrier barrier1 = new CheckpointBarrier(1L, System.currentTimeMillis(), CheckpointType.CHECKPOINT);
        CheckpointBarrier barrier2 = new CheckpointBarrier(1L, System.currentTimeMillis(), CheckpointType.CHECKPOINT);

        assertFalse(aligner.processBarrier(barrier1, 0));
        assertFalse(aligner.processBarrier(barrier2, 1));

        assertTrue(aligner.hasPendingBarriers());
        assertTrue(aligner.getPendingBarrierCount() > 0);

        assertNull(aligner.pollAlignedBarrier(),
                "No aligned barrier should be produced when one channel is missing");

        assertTrue(aligner.hasPendingBarriers(),
                "Pending barriers should remain when alignment is incomplete");

        aligner.close();
    }

    @Test
    void testDelayedThirdBarrierCompletesAlignment() {
        BarrierAligner aligner = new BarrierAligner(3);

        CheckpointBarrier barrier1 = new CheckpointBarrier(1L, System.currentTimeMillis(), CheckpointType.CHECKPOINT);
        CheckpointBarrier barrier2 = new CheckpointBarrier(1L, System.currentTimeMillis(), CheckpointType.CHECKPOINT);
        CheckpointBarrier barrier3 = new CheckpointBarrier(1L, System.currentTimeMillis(), CheckpointType.CHECKPOINT);

        assertFalse(aligner.processBarrier(barrier1, 0));
        assertFalse(aligner.processBarrier(barrier2, 1));
        assertTrue(aligner.hasPendingBarriers());

        assertTrue(aligner.processBarrier(barrier3, 2));
        assertFalse(aligner.hasPendingBarriers());

        AlignedBarrier aligned = aligner.pollAlignedBarrier();
        assertNotNull(aligned);
        assertEquals(1L, aligned.getCheckpointId());
        assertEquals(3, aligned.getInputCount());

        aligner.close();
    }

    @Test
    void testOverlappingCheckpointsIncompleteDoesNotBlockLaterComplete() {
        BarrierAligner aligner = new BarrierAligner(3);

        CheckpointBarrier cp1_ch0 = new CheckpointBarrier(1L, System.currentTimeMillis(), CheckpointType.CHECKPOINT);
        CheckpointBarrier cp1_ch1 = new CheckpointBarrier(1L, System.currentTimeMillis(), CheckpointType.CHECKPOINT);

        aligner.processBarrier(cp1_ch0, 0);
        aligner.processBarrier(cp1_ch1, 1);
        assertTrue(aligner.hasPendingBarriers());
        assertNull(aligner.pollAlignedBarrier(),
                "Checkpoint 1 should not complete with only 2 of 3 barriers");

        CheckpointBarrier cp2_ch0 = new CheckpointBarrier(2L, System.currentTimeMillis(), CheckpointType.CHECKPOINT);
        CheckpointBarrier cp2_ch1 = new CheckpointBarrier(2L, System.currentTimeMillis(), CheckpointType.CHECKPOINT);
        CheckpointBarrier cp2_ch2 = new CheckpointBarrier(2L, System.currentTimeMillis(), CheckpointType.CHECKPOINT);

        aligner.processBarrier(cp2_ch0, 0);
        aligner.processBarrier(cp2_ch1, 1);
        boolean completed = aligner.processBarrier(cp2_ch2, 2);

        assertTrue(completed, "Checkpoint 2 should complete even though checkpoint 1 is incomplete");

        AlignedBarrier aligned = aligner.pollAlignedBarrier();
        assertNotNull(aligned);
        assertEquals(2L, aligned.getCheckpointId(),
                "The aligned barrier should be for checkpoint 2");

        assertTrue(aligner.hasPendingBarriers(),
                "Checkpoint 1 barriers should still be pending for input 2");

        aligner.close();
    }
}
