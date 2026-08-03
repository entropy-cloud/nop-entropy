package io.nop.stream.core.execution;

import io.nop.stream.core.checkpoint.CheckpointBarrier;
import io.nop.stream.core.checkpoint.CheckpointType;
import io.nop.stream.core.execution.materialization.IMaterializationPoint;
import io.nop.stream.core.execution.materialization.InMemoryMaterializationPoint;
import io.nop.stream.core.execution.materialization.MaterializedElement;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.streamrecord.watermark.Watermark;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Stage 44 successor 4 Phase 1: consistent-cut epoch alignment + barrier/control
 * event filtering in {@link ResultPartition#write}.
 *
 * <p>Verifies:
 * <ul>
 *   <li>(a) When a {@link CheckpointBarrier} flows through {@code write()}, the
 *       materialization epoch advances to {@code barrier.getId()} and subsequent
 *       data records are tagged with that epoch.</li>
 *   <li>(b) Control events (barrier, watermark) are NOT written to the
 *       materialization store — only data records are dual-written.</li>
 * </ul>
 */
public class TestResultPartitionConsistentCut {

    /**
     * (a) barrier经过后物化数据epoch tag递增：barrier(id=5) flows through write →
     * subsequent records tagged with epoch 5; barrier(id=9) → subsequent tagged
     * with epoch 9.
     */
    @Test
    void barrierAdvancesMaterializationEpoch() throws Exception {
        IMaterializationPoint point = new InMemoryMaterializationPoint("p1");
        ResultPartition partition = new ResultPartition(16);
        partition.setMaterializationPoint(point);

        // Pre-barrier record: epoch 0 (default).
        partition.write(new StreamRecord<>("pre-barrier-A"));
        assertEquals(0L, partition.getCurrentMaterializationEpoch());

        // Barrier id=5 flows through write(): epoch advances to 5.
        partition.write(new CheckpointBarrier(5L, System.currentTimeMillis(), CheckpointType.CHECKPOINT));
        assertEquals(5L, partition.getCurrentMaterializationEpoch());

        // Post-barrier-5 records: tagged with epoch 5.
        partition.write(new StreamRecord<>("post-barrier-5-B"));
        assertEquals(5L, partition.getCurrentMaterializationEpoch());

        // Barrier id=9: epoch advances to 9.
        partition.write(new CheckpointBarrier(9L, System.currentTimeMillis(), CheckpointType.CHECKPOINT));
        assertEquals(9L, partition.getCurrentMaterializationEpoch());

        // Post-barrier-9 record: tagged with epoch 9.
        partition.write(new StreamRecord<>("post-barrier-9-C"));

        // Verify epoch tagging in the materialization store.
        List<MaterializedElement> all = point.replayAll();
        // 3 records (pre-barrier, post-5, post-9). Barriers are NOT stored.
        assertEquals(3, all.size(), "Only data records should be in the store (barriers filtered)");

        assertEquals("pre-barrier-A", all.get(0).getElement().<String>asRecord().getValue());
        assertEquals(0L, all.get(0).getEpoch(), "pre-barrier record tagged with epoch 0");

        assertEquals("post-barrier-5-B", all.get(1).getElement().<String>asRecord().getValue());
        assertEquals(5L, all.get(1).getEpoch(), "post-barrier-5 record tagged with epoch 5");

        assertEquals("post-barrier-9-C", all.get(2).getElement().<String>asRecord().getValue());
        assertEquals(9L, all.get(2).getEpoch(), "post-barrier-9 record tagged with epoch 9");

        // Consistent-cut replay: replay(epoch >= 5) returns post-barrier-5 records only.
        List<MaterializedElement> from5 = point.replay(5L);
        assertEquals(2, from5.size(), "replay(>=5) returns post-barrier-5 and post-barrier-9 records");
        assertEquals("post-barrier-5-B", from5.get(0).getElement().<String>asRecord().getValue());
        assertEquals("post-barrier-9-C", from5.get(1).getElement().<String>asRecord().getValue());

        // Consistent-cut replay: replay(epoch >= 9) returns post-barrier-9 only.
        List<MaterializedElement> from9 = point.replay(9L);
        assertEquals(1, from9.size(), "replay(>=9) returns post-barrier-9 record only");
        assertEquals("post-barrier-9-C", from9.get(0).getElement().<String>asRecord().getValue());
    }

    /**
     * (b) barrier/watermark不入物化store：control events are filtered from the
     * materialization store; only data records are dual-written.
     */
    @Test
    void controlEventsFilteredFromMaterializationStore() throws Exception {
        IMaterializationPoint point = new InMemoryMaterializationPoint("p2");
        ResultPartition partition = new ResultPartition(16);
        partition.setMaterializationPoint(point);

        partition.write(new StreamRecord<>("record-1"));
        partition.write(new Watermark(100L));
        partition.write(new CheckpointBarrier(7L, System.currentTimeMillis(), CheckpointType.CHECKPOINT));
        partition.write(new StreamRecord<>("record-2"));
        partition.write(new Watermark(200L));

        // Only the two data records should be in the materialization store.
        List<MaterializedElement> all = point.replayAll();
        assertEquals(2, all.size(), "Only data records (not watermark/barrier) should be dual-written");

        assertTrue(all.get(0).getElement().isRecord());
        assertEquals("record-1", all.get(0).getElement().<String>asRecord().getValue());
        assertTrue(all.get(1).getElement().isRecord());
        assertEquals("record-2", all.get(1).getElement().<String>asRecord().getValue());

        // The watermark and barrier went through the main queue (not filtered there),
        // but were filtered from the materialization store.
        // Read from the main queue to verify they DID flow through (control events
        // must still propagate through the main queue for the consumer to see them).
        assertNotNull(partition.read()); // record-1
        assertNotNull(partition.read()); // watermark(100)
        assertNotNull(partition.read()); // barrier(7)
        assertNotNull(partition.read()); // record-2
        assertNotNull(partition.read()); // watermark(200)
    }

    /**
     * Zero-regression: with no materialization point attached, barrier epoch
     * advancement still happens (cheap) but no dual-write occurs. Existing jobs
     * (no materialization) are unaffected.
     */
    @Test
    void noMaterializationPoint_zeroRegression() throws Exception {
        ResultPartition partition = new ResultPartition(8);
        // No materialization point → legacy by-reference path.
        partition.write(new StreamRecord<>("a"));
        // Barrier still advances the epoch field (harmless, no dual-write consumer).
        partition.write(new CheckpointBarrier(3L, System.currentTimeMillis(), CheckpointType.CHECKPOINT));
        assertEquals(3L, partition.getCurrentMaterializationEpoch());
        partition.write(new StreamRecord<>("b"));

        // Main queue has all elements (barrier included, as before).
        assertNotNull(partition.read());
        assertNotNull(partition.read());
        assertNotNull(partition.read());
    }
}
