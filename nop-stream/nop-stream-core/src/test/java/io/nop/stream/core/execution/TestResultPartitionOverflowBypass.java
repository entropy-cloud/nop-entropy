package io.nop.stream.core.execution;

import io.nop.stream.core.execution.materialization.IMaterializationPoint;
import io.nop.stream.core.execution.materialization.InMemoryMaterializationPoint;
import io.nop.stream.core.execution.materialization.MaterializedElement;
import io.nop.stream.core.streamrecord.StreamRecord;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Stage 44 successor 4 Phase 2: producer overflow-bypass (解除死锁 1,
 * failover-design.md §9.4).
 *
 * <p>Verifies that when materialization is enabled, {@link ResultPartition#write}
 * does NOT block on {@code queue.put} when the queue is full — the producer
 * thread remains RUNNABLE (not WAITING/BLOCKED) and the overflow data is safely
 * held in the materialization store (ready for consumer-side replay).
 *
 * <p>Zero-regression: with no materialization point, the original blocking
 * {@code queue.put} is preserved.
 */
public class TestResultPartitionOverflowBypass {

    /**
     * (a) + (b): queue full + materialization enabled → write does not block;
     * overflow elements land in the materialization store.
     */
    @Test
    void overflowBypass_producerDoesNotBlockAndDataLandsInMaterializationStore() throws Exception {
        IMaterializationPoint point = new InMemoryMaterializationPoint("p-overflow");
        // Small capacity so it fills quickly.
        ResultPartition partition = new ResultPartition(4);
        partition.setMaterializationPoint(point);

        // Fill the queue (4 elements = capacity). Each is dual-written to the
        // materialization store at epoch 0.
        for (int i = 0; i < 4; i++) {
            partition.write(new StreamRecord<>("fill-" + i));
        }
        assertEquals(0, partition.getAvailableCapacity(), "Queue should be full");
        assertEquals(4, point.size(), "Fill elements dual-written to materialization store");

        // Now write 10 MORE elements in a separate thread. Without overflow-bypass,
        // queue.put would block forever (no consumer draining). With overflow-bypass,
        // each write returns promptly (offer fails, data lands in the store).
        int extra = 10;
        AtomicReference<Throwable> error = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        Thread producer = new Thread(() -> {
            try {
                for (int i = 0; i < extra; i++) {
                    partition.write(new StreamRecord<>("overflow-" + i));
                }
            } catch (Throwable t) {
                error.set(t);
            } finally {
                done.countDown();
            }
        });
        producer.start();

        // The producer should finish within a short bound. If write() were
        // blocking on queue.put, this await would time out.
        assertTrue(done.await(5, TimeUnit.SECONDS),
                "Producer should NOT block when materialization is enabled (overflow-bypass)");
        assertNull(error.get(), "Producer thread should complete without error");

        // The producer thread must NOT be in WAITING/BLOCKED state — it exited.
        assertFalse(producer.isAlive(), "Producer thread should have exited cleanly");

        // (b) Overflow data is in the materialization store.
        List<MaterializedElement> all = point.replayAll();
        assertEquals(4 + extra, all.size(),
                "All elements (fill + overflow) should be in the materialization store");

        // Verify the overflow elements specifically.
        for (int i = 0; i < extra; i++) {
            assertEquals("overflow-" + i,
                    all.get(4 + i).getElement().<String>asRecord().getValue(),
                    "Overflow element " + i + " should be in the materialization store");
        }

        // The main queue still has only the original 4 elements (overflow went
        // to the store only).
        assertEquals(4, partition.size(),
                "Main queue should still hold only the fill elements (overflow bypassed)");
    }

    /**
     * Zero-regression: with NO materialization point, write() blocks on queue.put
     * when full (legacy behavior preserved).
     */
    @Test
    void noMaterialization_writeBlocksWhenFull_zeroRegression() throws Exception {
        ResultPartition partition = new ResultPartition(2);
        // No materialization point → legacy blocking path.
        partition.write(new StreamRecord<>("a"));
        partition.write(new StreamRecord<>("b"));
        assertEquals(0, partition.getAvailableCapacity());

        AtomicReference<Boolean> completed = new AtomicReference<>(null);
        Thread producer = new Thread(() -> {
            try {
                partition.write(new StreamRecord<>("c"));
                completed.set(true);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                completed.set(false);
            }
        });
        producer.start();
        Thread.sleep(300);
        // The producer should still be blocked (legacy blocking behavior).
        assertNull(completed.get(), "Without materialization, write() must block when queue is full");
        assertTrue(producer.isAlive(), "Producer thread should still be blocked");

        // Drain one to unblock; producer should then complete.
        assertNotNull(partition.read());
        producer.join(3000);
        assertEquals(true, completed.get(), "Producer should complete once a slot frees");
    }

    /**
     * Epoch tagging still works correctly under overflow: overflow elements
     * inherit the current epoch (post-barrier if a barrier preceded them).
     */
    @Test
    void overflowElementsInheritCurrentEpoch() throws Exception {
        IMaterializationPoint point = new InMemoryMaterializationPoint("p-epoch");
        ResultPartition partition = new ResultPartition(2);
        partition.setMaterializationPoint(point);

        // Fill at epoch 0.
        partition.write(new StreamRecord<>("pre-1"));
        partition.write(new StreamRecord<>("pre-2"));

        // Bump epoch via a barrier (barrier is filtered from store but advances epoch).
        partition.write(new io.nop.stream.core.checkpoint.CheckpointBarrier(
                7L, System.currentTimeMillis(), io.nop.stream.core.checkpoint.CheckpointType.CHECKPOINT));

        // Overflow writes should be tagged with epoch 7.
        partition.write(new StreamRecord<>("overflow-1"));
        partition.write(new StreamRecord<>("overflow-2"));

        // replay(epoch >= 7) returns only the overflow elements (barrier filtered).
        List<MaterializedElement> from7 = point.replay(7L);
        assertEquals(2, from7.size(), "Post-barrier overflow elements tagged with epoch 7");
        assertEquals("overflow-1", from7.get(0).getElement().<String>asRecord().getValue());
        assertEquals("overflow-2", from7.get(1).getElement().<String>asRecord().getValue());
    }
}
