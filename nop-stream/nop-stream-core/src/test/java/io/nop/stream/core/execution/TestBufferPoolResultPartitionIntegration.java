package io.nop.stream.core.execution;

import io.nop.stream.core.execution.buffer.BufferPool;
import io.nop.stream.core.execution.buffer.IBufferPool;
import io.nop.stream.core.streamrecord.StreamRecord;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for {@link ResultPartition} bound to a global {@link IBufferPool}.
 *
 * <p>Verifies the G53 production-path wiring at the partition level:
 * <ul>
 *   <li>write acquires a permit; read releases it (metering observable on the pool)</li>
 *   <li>multiple partitions sharing one pool see an aggregate global usage</li>
 *   <li>global exhaustion blocks the producer (adjudicated contract)</li>
 *   <li>per-partition queue capacity is independently respected</li>
 *   <li>{@code close()} returns permits for discarded elements (no leak)</li>
 *   <li>backward compatibility: pool=null behaves as the legacy bounded queue</li>
 * </ul>
 */
public class TestBufferPoolResultPartitionIntegration {

    @Test
    public void testWriteAcquiresReadReleases() throws InterruptedException {
        BufferPool pool = new BufferPool(8);
        ResultPartition partition = new ResultPartition(8, pool);

        assertEquals(0, pool.getGlobalUsage());
        partition.write(new StreamRecord<>("a"));
        assertEquals(1, pool.getGlobalUsage());
        partition.write(new StreamRecord<>("b"));
        assertEquals(2, pool.getGlobalUsage());

        Object first = partition.read();
        assertNotNull(first);
        assertEquals(1, pool.getGlobalUsage(), "read must release one permit");

        Object second = partition.read();
        assertNotNull(second);
        assertEquals(0, pool.getGlobalUsage(), "all permits returned after draining");

        partition.close();
        assertNull(partition.read(), "end-of-stream sentinel returns null");
        assertEquals(0, pool.getGlobalUsage());
    }

    @Test
    public void testMultiplePartitionsShareGlobalUsage() throws InterruptedException {
        BufferPool pool = new BufferPool(4);
        ResultPartition p0 = new ResultPartition(1024, pool);
        ResultPartition p1 = new ResultPartition(1024, pool);

        // Two partitions each hold some in-flight elements; aggregate is bounded by pool
        assertSame(pool, p0.getBufferPool());
        assertSame(pool, p1.getBufferPool());

        p0.write(new StreamRecord<>(1));
        p0.write(new StreamRecord<>(2));
        p1.write(new StreamRecord<>(3));

        assertEquals(3, pool.getGlobalUsage(), "global usage aggregates across partitions");

        p1.read();
        assertEquals(2, pool.getGlobalUsage());

        p0.read();
        p0.read();
        assertEquals(0, pool.getGlobalUsage());
    }

    @Test
    public void testGlobalExhaustionBlocksProducer() throws Exception {
        // pool capacity smaller than sum of per-partition capacities — demonstrates
        // that the GLOBAL bound (not just per-partition) kicks in for fan-out.
        BufferPool pool = new BufferPool(2);
        ResultPartition p0 = new ResultPartition(1024, pool);
        ResultPartition p1 = new ResultPartition(1024, pool);

        // Exhaust the global pool via both partitions
        p0.write(new StreamRecord<>("a"));
        p1.write(new StreamRecord<>("b"));
        assertTrue(pool.isGlobalBackpressured());

        AtomicReference<Throwable> error = new AtomicReference<>();
        CountDownLatch blocked = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(1);
        Thread producer = new Thread(() -> {
            try {
                blocked.countDown();
                p0.write(new StreamRecord<>("c")); // should block on global exhaustion
                done.countDown();
            } catch (Throwable t) {
                error.set(t);
                done.countDown();
            }
        });
        producer.start();
        assertTrue(blocked.await(2, TimeUnit.SECONDS));
        assertFalse(done.await(500, TimeUnit.MILLISECONDS), "producer must block when global pool exhausted");

        // Consume from p1 to free a global permit
        p1.read();

        assertTrue(done.await(2, TimeUnit.SECONDS), "producer must proceed once a global permit frees");
        assertNull(error.get());
        assertEquals(2, pool.getGlobalUsage(), "p0 now holds two permits (a and c)");

        producer.join(2000);
        pool.close();
    }

    @Test
    public void testPerPartitionCapacityStillRespected() throws Exception {
        // Pool has plenty of capacity, but per-partition queue is the tighter bound
        BufferPool pool = new BufferPool(1000);
        ResultPartition partition = new ResultPartition(3, pool);

        partition.write(new StreamRecord<>(1));
        partition.write(new StreamRecord<>(2));
        partition.write(new StreamRecord<>(3));
        assertEquals(0, partition.getAvailableCapacity(), "per-partition queue full");

        AtomicReference<Throwable> error = new AtomicReference<>();
        CountDownLatch blocked = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(1);
        Thread producer = new Thread(() -> {
            try {
                blocked.countDown();
                partition.write(new StreamRecord<>(4)); // blocks on per-partition queue
                done.countDown();
            } catch (Throwable t) {
                error.set(t);
                done.countDown();
            }
        });
        producer.start();
        assertTrue(blocked.await(2, TimeUnit.SECONDS));
        assertFalse(done.await(500, TimeUnit.MILLISECONDS), "producer must block on full per-partition queue");

        // pool is NOT exhausted (only 3/1000 used); the block is per-partition
        assertFalse(pool.isGlobalBackpressured(), "pool has spare capacity; block is per-partition");

        partition.read();
        assertTrue(done.await(2, TimeUnit.SECONDS), "producer proceeds once per-partition queue frees a slot");
        assertNull(error.get());

        producer.join(2000);
        pool.close();
    }

    @Test
    public void testCloseOnFullQueueBackpressuresThenReleasesPoolPermits() throws Exception {
        // P1-10: close() now uses a blocking queue.put(END_OF_STREAM). On a full
        // queue it backpressures until the consumer drains enough room — it no
        // longer discards in-flight elements. This test verifies that after the
        // consumer drains all records and close() completes, every permit is
        // returned to the global pool (no leak).
        BufferPool pool = new BufferPool(8);
        ResultPartition partition = new ResultPartition(3, pool);

        partition.write(new StreamRecord<>(1));
        partition.write(new StreamRecord<>(2));
        partition.write(new StreamRecord<>(3));
        assertEquals(3, pool.getGlobalUsage());

        // close() on a full queue blocks; run it in a separate thread.
        CountDownLatch closed = new CountDownLatch(1);
        Thread closer = new Thread(() -> {
            try {
                partition.close();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            closed.countDown();
        });
        closer.start();

        // Drain all 3 records from the consumer side; each release frees a permit
        // and makes room for the sentinel.
        for (int i = 0; i < 3; i++) {
            assertNotNull(partition.read(), "no in-flight record should be lost during close");
        }
        assertEquals(0, pool.getGlobalUsage(), "draining must release all permits");

        // close() should now complete once there is room for the sentinel.
        assertTrue(closed.await(5, TimeUnit.SECONDS), "close() should complete once the consumer drains");

        assertTrue(partition.isFinished());
        assertNull(partition.read(), "sentinel after close");
        assertEquals(0, pool.getGlobalUsage(), "reading sentinel must not consume a permit");
    }

    @Test
    public void testInterruptedWriteReleasesPermit() throws Exception {
        BufferPool pool = new BufferPool(1);
        ResultPartition partition = new ResultPartition(1, pool);
        partition.write(new StreamRecord<>("fill")); // pool + queue now full

        AtomicReference<Throwable> caught = new AtomicReference<>();
        CountDownLatch blocked = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(1);
        Thread producer = new Thread(() -> {
            try {
                blocked.countDown();
                partition.write(new StreamRecord<>("blocked"));
            } catch (Throwable t) {
                caught.set(t);
                done.countDown();
            }
        });
        producer.start();
        assertTrue(blocked.await(2, TimeUnit.SECONDS));
        producer.interrupt();

        assertTrue(done.await(2, TimeUnit.SECONDS), "interrupted producer must throw");
        // The producer acquired a permit then was interrupted at queue.put; permit released.
        // Global usage stays at 1 (the single in-flight element), not 2.
        assertEquals(1, pool.getGlobalUsage(),
                "interrupted write must release the acquired permit, got usage=" + pool.getGlobalUsage());

        producer.join(2000);
        pool.close();
    }

    @Test
    public void testBackwardCompatNullPoolBehavesAsLegacy() throws InterruptedException {
        // pool=null: no global aggregation, only per-partition bounded queue (legacy)
        ResultPartition partition = new ResultPartition(4);
        assertNull(partition.getBufferPool());

        for (int i = 0; i < 4; i++) {
            partition.write(new StreamRecord<>(i));
        }
        assertEquals(0, partition.getAvailableCapacity());
        assertEquals(4, partition.getTotalCapacity());

        // drains normally
        for (int i = 0; i < 4; i++) {
            assertNotNull(partition.read());
        }
        partition.close();
        assertNull(partition.read());
    }
}
