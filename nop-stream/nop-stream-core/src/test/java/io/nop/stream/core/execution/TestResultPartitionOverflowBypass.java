package io.nop.stream.core.execution;

import io.nop.stream.core.testsupport.TestAwait;
import io.nop.stream.core.execution.buffer.BufferPool;
import io.nop.stream.core.execution.buffer.IBufferPool;
import io.nop.stream.core.execution.materialization.IMaterializationPoint;
import io.nop.stream.core.execution.materialization.InMemoryMaterializationPoint;
import io.nop.stream.core.execution.materialization.MaterializedElement;
import io.nop.stream.core.streamrecord.StreamElement;
import io.nop.stream.core.streamrecord.StreamRecord;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FAM-1 / C10-005 (plan 2510 R2-07, adjudication 10d): this test class previously
 * pinned the overflow-bypass DEFECT as delivered semantics ("producer does not
 * block + store receives all overflow elements") — the fake protection identified
 * by C10-test-protection M7, rewritten in the same diff as the fix.
 *
 * <p>New contract (adjudication 10b/10c): pool + materialization queue-full → the
 * producer BLOCKS (backpressure restored), the permit is NOT released (no silent
 * bypass of the IBufferPool bound), the store (checkpoint-scoped recovery plane,
 * unbounded by default) mirrors every in-flight record,
 * and after the consumer drains, every element arrives exactly-once. The
 * zero-regression test {@code noMaterialization_writeBlocksWhenFull_zeroRegression}
 * is preserved unchanged.
 */
public class TestResultPartitionOverflowBypass {

    /**
     * (FAM-1 rewritten, was {@code overflowBypass_producerDoesNotBlockAndDataLandsInMaterializationStore})
     * queue full + pool + materialization → producer BLOCKS, permits retained, and
     * after the consumer drains the element sequence arrives exactly-once.
     */
    @Test
    void fam1_poolPlusMaterializationQueueFullBlocksProducerRetainsPermitExactlyOnce() throws Exception {
        IBufferPool pool = new BufferPool(8);
        IMaterializationPoint point = new InMemoryMaterializationPoint("p-fam1-rewrite");
        ResultPartition partition = new ResultPartition(4, pool);
        partition.setMaterializationPoint(point);

        // Fill the queue (4 elements = capacity), each dual-written to the store.
        for (int i = 0; i < 4; i++) {
            partition.write(new StreamRecord<>("fill-" + i));
        }
        assertEquals(0, partition.getAvailableCapacity(), "Queue should be full");
        assertEquals(4, point.size(), "Fill elements dual-written to materialization store");
        assertEquals(4, pool.getGlobalUsage(), "fill elements hold their permits");

        // 10 MORE elements in a separate thread: the producer must BLOCK on the
        // full queue (permit-retaining put) — silently completing (the old fake
        // protection) is the defect this rewrite forbids.
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
        }, "fam1-producer");
        producer.start();

        // Negative window: nobody drains → the producer stays blocked.
        TestAwait.staysTrue("producer stays blocked on full materialization queue",
                () -> done.getCount() > 0 && producer.isAlive(), 300);
        assertTrue(producer.isAlive(),
                "FAM-1: producer must BLOCK when the queue is full (backpressure restored)");
        // Permit retention: 4 permits for the queued fill elements + 1 permit held
        // by the blocked put (a single producer thread has exactly one element in
        // flight). The old bypass released permits on queue-full — usage stayed 4.
        assertEquals(5, pool.getGlobalUsage(),
                "FAM-1: permits must be retained (4 queued + 1 held by the blocked put) — "
                        + "the old bypass released them on queue-full");
        // Store mirrors the writes issued so far (dual-write precedes the blocking
        // put): 4 fill + the one in-flight overflow element.

        // Consumer drains everything; the producer unblocks and every element
        // reaches the consumer exactly-once, in order.
        List<String> received = new ArrayList<>();
        Thread consumer = new Thread(() -> {
            try {
                StreamElement e;
                while ((e = partition.read(5, TimeUnit.SECONDS)) != null) {
                    received.add(e.<String>asRecord().getValue());
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }, "fam1-consumer");
        consumer.start();

        assertTrue(done.await(30, TimeUnit.SECONDS),
                "producer must unblock once the consumer drains (bounded backpressure, not deadlock)");

        // Finish the partition only AFTER the producer is done — closing earlier
        // would reject the producer's remaining writes (finished partition).
        partition.close();
        assertNull(error.get(), "producer must complete without error after the drain");
        consumer.join(30_000);
        assertFalse(consumer.isAlive(), "consumer must reach EOS");

        List<String> expected = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            expected.add("fill-" + i);
        }
        for (int i = 0; i < extra; i++) {
            expected.add("overflow-" + i);
        }
        assertEquals(expected, received,
                "FAM-1: every element must reach the runtime consumer exactly-once, in order "
                        + "(zero silent store-only skipping)");
        assertEquals(14, point.size(),
                "FAM-1: the store mirrors all in-flight records (checkpoint-scoped "
                        + "recovery plane, unbounded default)");
        assertEquals(0, pool.getGlobalUsage(), "all permits released after the drain");
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
        // 时间确定化（plan 2510）：负向断言以结构判据替代观察窗——producer 线程已
        // 到达写满队列的阻塞点（PARKED 于 put 内部条件等待）且未完成写入，
        // 「仍阻塞」由线程状态结构性证明，不再用 300ms 墙钟不变量窗口。
        TestAwait.untilThreadParked("producer blocked on the materialization-full queue write", producer);
        // The producer should still be blocked (legacy blocking behavior).
        assertNull(completed.get(), "Without materialization, write() must block when queue is full");
        assertTrue(producer.isAlive(), "Producer thread should still be blocked");

        // Drain one to unblock; producer should then complete.
        assertNotNull(partition.read());
        producer.join(3000);
        assertEquals(true, completed.get(), "Producer should complete once a slot frees");
    }

    /**
     * Epoch tagging (rewritten): records written after a barrier inherit the
     * barrier's checkpoint id. With the FAM-1 fix the main queue carries every
     * element, so this no longer depends on overflow — the epoch flow is asserted
     * through the normal dual-write path.
     */
    @Test
    void fam1_elementsAfterBarrierInheritCurrentEpoch() throws Exception {
        IMaterializationPoint point = new InMemoryMaterializationPoint("p-epoch");
        // Capacity 8: 2 pre-records + 1 barrier + 2 post-records all flow through
        // the main queue (FAM-1: the barrier is enqueued on the queue too, only the
        // materialization store filters it) — no consumer drains here, so the
        // permit-retaining blocking put requires headroom for all 5 elements.
        ResultPartition partition = new ResultPartition(8);
        partition.setMaterializationPoint(point);

        // Write at epoch 0.
        partition.write(new StreamRecord<>("pre-1"));
        partition.write(new StreamRecord<>("pre-2"));

        // Bump epoch via a barrier (barrier is filtered from store but advances epoch).
        partition.write(new io.nop.stream.core.checkpoint.CheckpointBarrier(
                7L, System.currentTimeMillis(), io.nop.stream.core.checkpoint.CheckpointType.CHECKPOINT));

        // Post-barrier writes are tagged with epoch 7.
        partition.write(new StreamRecord<>("post-1"));
        partition.write(new StreamRecord<>("post-2"));

        // replay(epoch >= 7) returns only the post-barrier elements (barrier filtered).
        List<MaterializedElement> from7 = point.replay(7L);
        assertEquals(2, from7.size(), "Post-barrier elements tagged with epoch 7");
        assertEquals("post-1", from7.get(0).getElement().<String>asRecord().getValue());
        assertEquals("post-2", from7.get(1).getElement().<String>asRecord().getValue());

        // All four records AND the barrier reached the runtime queue (no store-only
        // bypass) — the queue carries control elements, the store filters them.
        assertEquals(5, partition.size());
    }
}
