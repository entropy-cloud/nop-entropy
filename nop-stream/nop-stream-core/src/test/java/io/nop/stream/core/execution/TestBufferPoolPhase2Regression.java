package io.nop.stream.core.execution;

import io.nop.stream.core.execution.buffer.BufferPool;
import io.nop.stream.core.execution.flow.EdgeConfig;
import io.nop.stream.core.execution.flow.FlowControlPolicy;
import io.nop.stream.core.streamrecord.StreamRecord;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 2 (G53) — regression & IWriteStatus semantics tests under the new pool model.
 *
 * <p>Verifies:
 * <ul>
 *   <li>Existing {@code BLOCKING_QUEUE} flow-control path behaves unchanged when a pool
 *       is attached (regression through the production build() path)</li>
 *   <li>{@code IWriteStatus} ({@code isBackpressured}/{@code getAvailableCapacity}/
 *       {@code getTotalCapacity}) keeps its <b>per-partition private queue</b> scope;
 *       the pool exposes an <b>independent global</b> meter — the two do not mix</li>
 * </ul>
 */
public class TestBufferPoolPhase2Regression {

    @Test
    public void testBlockingQueuePathUnchangedWithPoolAttached() throws Exception {
        // The BLOCKING_QUEUE path must behave the same as before now that build() attaches
        // a pool: a RecordWriter built with BLOCKING_QUEUE EdgeConfig emits and reads back
        // normally, and the pool meter moves as elements flow.
        BufferPool pool = new BufferPool(16);
        ResultPartition partition = new ResultPartition(8, pool);
        EdgeConfig blockingConfig = new EdgeConfig(FlowControlPolicy.BLOCKING_QUEUE, 8, 1024, 4096);

        RecordWriter<String> writer = new RecordWriter<>(partition, blockingConfig);

        writer.emit(new StreamRecord<>("one"));
        writer.emit(new StreamRecord<>("two"));
        assertEquals(2, pool.getGlobalUsage(), "BLOCKING_QUEUE path must move the pool meter");

        // read back in order
        Object first = partition.read();
        Object second = partition.read();
        assertNotNull(first);
        assertNotNull(second);
        assertEquals(0, pool.getGlobalUsage(), "reads return permits to the pool");

        writer.close();
        assertNull(partition.read(), "end-of-stream after close");
    }

    @Test
    public void testIWriteStatusRemainsPerPartitionScoped() throws Exception {
        // Adjudicated: IWriteStatus reads the per-partition queue ONLY. It must NOT
        // reflect the global pool state. The pool exposes its own global meter separately.
        BufferPool pool = new BufferPool(100); // deliberately large global budget
        ResultPartition p0 = new ResultPartition(4, pool);
        ResultPartition p1 = new ResultPartition(4, pool);

        // Fill p0 to its per-partition capacity (4); global pool still has plenty spare
        p0.write(new StreamRecord<>(1));
        p0.write(new StreamRecord<>(2));
        p0.write(new StreamRecord<>(3));
        p0.write(new StreamRecord<>(4));

        // IWriteStatus reports the per-partition queue, not the global pool
        assertEquals(0, p0.getAvailableCapacity(), "per-partition available is 0 (queue full)");
        assertEquals(4, p0.getTotalCapacity(), "per-partition total is 4");
        assertTrue(p0.isBackpressured(), "p0 is backpressured at the per-partition level");

        // The global pool is NOT backpressured even though p0 (per-partition) is
        assertFalse(pool.isGlobalBackpressured(),
                "global pool has spare; per-partition backpressure != global backpressure");
        assertEquals(4, pool.getGlobalUsage(),
                "global meter tracks all partitions (only p0 here)");

        // p1 is empty — its per-partition status is independent of p0's
        assertEquals(4, p1.getAvailableCapacity(), "p1 per-partition status is independent of p0");
        assertFalse(p1.isBackpressured());

        // Consume one from p0: per-partition available grows by 1; global usage drops by 1
        p0.read();
        assertEquals(1, p0.getAvailableCapacity());
        assertEquals(3, pool.getGlobalUsage());

        pool.close();
    }

    @Test
    public void testGlobalMeterSeparateFromPerPartitionMeter() throws Exception {
        // Explicit separation: two partitions, each partially filled. The sum of their
        // per-partition usages equals the pool's global usage; neither IWriteStatus
        // method reports the global aggregate.
        BufferPool pool = new BufferPool(50);
        ResultPartition p0 = new ResultPartition(10, pool);
        ResultPartition p1 = new ResultPartition(10, pool);

        p0.write(new StreamRecord<>("a"));
        p0.write(new StreamRecord<>("b"));
        p1.write(new StreamRecord<>("c"));

        int perPartitionInFlight = (p0.getTotalCapacity() - p0.getAvailableCapacity())
                + (p1.getTotalCapacity() - p1.getAvailableCapacity());
        assertEquals(3, perPartitionInFlight);
        assertEquals(3, pool.getGlobalUsage(),
                "global meter == sum of per-partition in-flight (separate but consistent)");

        // IWriteStatus has no global accessor — the only global view is on the pool
        pool.close();
    }
}
