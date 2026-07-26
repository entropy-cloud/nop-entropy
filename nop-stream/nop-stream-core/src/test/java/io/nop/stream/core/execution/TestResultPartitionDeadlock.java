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
    void testCloseOnFullQueueBackpressuresUntilConsumerDrains() throws Exception {
        // P1-10: close() now uses a blocking queue.put(END_OF_STREAM).
        // On a full queue it MUST backpressure (block the closer) until the
        // consumer drains enough room — this is the natural backpressure
        // contract that replaces the prior data-discarding queue.clear().
        ResultPartition partition = new ResultPartition(4);

        for (int i = 0; i < 4; i++) {
            partition.write(new StreamRecord<>("elem-" + i));
        }
        assertEquals(0, partition.getAvailableCapacity());

        AtomicBoolean closed = new AtomicBoolean(false);
        CountDownLatch started = new CountDownLatch(1);
        Thread closer = new Thread(() -> {
            started.countDown();
            try {
                partition.close();
                closed.set(true);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        closer.start();
        assertTrue(started.await(2, TimeUnit.SECONDS));

        // Without draining, close() should still be blocked.
        Thread.sleep(200);
        assertFalse(closed.get(), "close() must block while the queue is full and no consumer drains");

        // Drain one element to make room for the sentinel.
        assertNotNull(partition.read());

        // close() should now be able to place the sentinel and return.
        assertTrue(waitForClosed(closer, 5, TimeUnit.SECONDS), "close() should complete once a slot is freed");
        assertTrue(closed.get());
        assertTrue(partition.isFinished());
    }

    private static boolean waitForClosed(Thread t, long timeout, TimeUnit unit) throws InterruptedException {
        long deadlineMillis = System.currentTimeMillis() + unit.toMillis(timeout);
        while (System.currentTimeMillis() < deadlineMillis) {
            if (!t.isAlive()) {
                return true;
            }
            Thread.sleep(20);
        }
        return !t.isAlive();
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
    void testFullQueueDrainsAfterCloseNoDataLoss() throws Exception {
        // P1-10: previously the queue.clear() path dropped every in-flight
        // record the consumer had not yet seen. The new blocking
        // queue.put(END_OF_STREAM) backpressures the close until the consumer
        // drains enough room, so all 4 records remain observable.
        ResultPartition partition = new ResultPartition(4);

        for (int i = 0; i < 4; i++) {
            partition.write(new StreamRecord<>("elem-" + i));
        }

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

        // close() is blocking because the queue is full. Drain all 4 records
        // from the consumer side; each release makes room for the sentinel.
        for (int i = 0; i < 4; i++) {
            StreamElement e = partition.read();
            assertNotNull(e, "no in-flight record should be lost during close");
            assertTrue(e.isRecord());
        }

        assertTrue(closed.await(5, TimeUnit.SECONDS), "close() should complete once the consumer drains");

        // Only the sentinel remains; one more read returns null (EOS).
        assertNull(partition.read());
    }

    @Test
    void testCloseOnEmptyQueue() throws Exception {
        ResultPartition partition = new ResultPartition(4);
        partition.close();

        assertTrue(partition.isFinished());
        assertNull(partition.read());
    }

    @Test
    void testInterruptDuringCloseRethrowsAndDoesNotDropData() throws Exception {
        // P1-10: the blocking close() now propagates InterruptedException
        // instead of falling back to a data-discarding offer(). Records
        // already in the queue must remain observable by the consumer.
        ResultPartition partition = new ResultPartition(4);
        for (int i = 0; i < 4; i++) {
            partition.write(new StreamRecord<>("elem-" + i));
        }

        Thread closer = new Thread(() -> {
            try {
                partition.close();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        closer.start();
        closer.interrupt();
        closer.join(3000);
        assertFalse(closer.isAlive(), "Close thread should finish despite interrupt");

        assertTrue(partition.isFinished());
        // Every previously enqueued record must still be observable — the
        // interrupt path must NOT discard any in-flight data.
        for (int i = 0; i < 4; i++) {
            StreamElement e = partition.read();
            assertNotNull(e, "record " + i + " must remain observable after interrupted close");
            assertTrue(e.isRecord());
        }
    }
}
