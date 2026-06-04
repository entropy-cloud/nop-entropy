package io.nop.stream.core.execution;

import io.nop.stream.core.streamrecord.StreamElement;
import io.nop.stream.core.streamrecord.StreamRecord;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

public class TestResultPartitionDeadlock {

    @Test
    void testCloseOnFullQueueDoesNotDeadlock() throws Exception {
        ResultPartition partition = new ResultPartition(4);

        for (int i = 0; i < 4; i++) {
            partition.write(new StreamRecord<>("elem-" + i));
        }
        assertEquals(0, partition.getAvailableCapacity());

        AtomicBoolean closed = new AtomicBoolean(false);
        CountDownLatch latch = new CountDownLatch(1);
        Thread closer = new Thread(() -> {
            partition.close();
            closed.set(true);
            latch.countDown();
        });
        closer.start();

        assertTrue(latch.await(5, TimeUnit.SECONDS), "close() should not deadlock on full queue");
        assertTrue(closed.get());
        assertTrue(partition.isFinished());
    }

    @Test
    void testReadReturnsNullAfterClose() throws Exception {
        ResultPartition partition = new ResultPartition(8);
        partition.write(new StreamRecord<>("a"));
        partition.write(new StreamRecord<>("b"));

        partition.close();
        assertTrue(partition.isFinished());

        StreamElement e1 = partition.read();
        assertNotNull(e1);
        assertTrue(e1.isRecord());

        StreamElement e2 = partition.read();
        assertNotNull(e2);
        assertTrue(e2.isRecord());

        StreamElement e3 = partition.read();
        assertNull(e3, "read() should return null after close() sentinel is reached");
    }

    @Test
    void testDrainedElementsLostOnClose() throws Exception {
        ResultPartition partition = new ResultPartition(4);

        for (int i = 0; i < 4; i++) {
            partition.write(new StreamRecord<>("elem-" + i));
        }

        partition.close();

        assertNull(partition.read(), "After close() drains full queue, only sentinel remains");
    }

    @Test
    void testCloseOnEmptyQueue() throws Exception {
        ResultPartition partition = new ResultPartition(4);
        partition.close();

        assertTrue(partition.isFinished());
        assertNull(partition.read());
    }

    @Test
    void testInterruptDuringCloseFallsBackToOffer() throws Exception {
        ResultPartition partition = new ResultPartition(4);
        for (int i = 0; i < 4; i++) {
            partition.write(new StreamRecord<>("elem-" + i));
        }

        Thread closer = new Thread(() -> {
            partition.close();
        });
        closer.start();
        closer.interrupt();
        closer.join(3000);
        assertFalse(closer.isAlive(), "Close thread should finish despite interrupt");

        assertTrue(partition.isFinished());
        assertNull(partition.read(), "Sentinel should have been placed via offer fallback");
    }
}
