package io.nop.stream.core.execution.buffer;

import io.nop.stream.core.exceptions.StreamException;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link BufferPool}.
 *
 * <p>Verifies the G53 contract: global aggregate capacity is respected,
 * acquire/release metering is correct, and exhaustion <b>blocks</b> (adjudicated
 * contract — not a RuntimeException) while remaining wakeable by release,
 * interrupt, and close.
 */
public class TestBufferPool {

    @Test
    public void testInvalidCapacityThrows() {
        assertThrows(StreamException.class, () -> new BufferPool(0));
        assertThrows(StreamException.class, () -> new BufferPool(-1));
    }

    @Test
    public void testGlobalCapacityRespected() throws InterruptedException {
        BufferPool pool = new BufferPool(3);
        assertEquals(3, pool.getGlobalTotalCapacity());
        assertEquals(0, pool.getGlobalUsage());
        assertEquals(3, pool.getGlobalAvailableCapacity());
        assertFalse(pool.isGlobalBackpressured());

        pool.acquire();
        pool.acquire();
        pool.acquire();

        assertEquals(3, pool.getGlobalUsage());
        assertEquals(0, pool.getGlobalAvailableCapacity());
        assertTrue(pool.isGlobalBackpressured());
    }

    @Test
    public void testAcquireReleaseCyclesMetering() throws InterruptedException {
        BufferPool pool = new BufferPool(2);
        pool.acquire();
        assertEquals(1, pool.getGlobalUsage());
        pool.release();
        assertEquals(0, pool.getGlobalUsage());
        pool.acquire();
        pool.acquire();
        assertEquals(2, pool.getGlobalUsage());
        pool.release();
        pool.release();
        assertEquals(0, pool.getGlobalUsage());
        assertEquals(2, pool.getGlobalAvailableCapacity());
    }

    @Test
    public void testExhaustionBlocksThenReleasesOnRelease() throws Exception {
        BufferPool pool = new BufferPool(1);
        pool.acquire();
        assertTrue(pool.isGlobalBackpressured());

        AtomicReference<Throwable> error = new AtomicReference<>();
        CountDownLatch blocked = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(1);
        Thread acquirer = new Thread(() -> {
            try {
                blocked.countDown();
                pool.acquire();
                done.countDown();
            } catch (Throwable t) {
                error.set(t);
                done.countDown();
            }
        });
        acquirer.start();
        assertTrue(blocked.await(2, TimeUnit.SECONDS), "acquirer should reach the blocking acquire");

        // Still blocked
        assertFalse(done.await(500, TimeUnit.MILLISECONDS), "acquirer must block while pool exhausted");
        assertEquals(1, pool.getGlobalUsage());

        // Release one permit — the blocked acquirer should proceed
        pool.release();

        assertTrue(done.await(2, TimeUnit.SECONDS), "acquirer should proceed after release");
        assertNull(error.get(), "acquirer must not throw on wake-by-release");
        assertEquals(1, pool.getGlobalUsage(), "permit re-acquired by the woken thread");

        acquirer.join(2000);
    }

    @Test
    public void testExhaustionWakeableByInterrupt() throws Exception {
        BufferPool pool = new BufferPool(1);
        pool.acquire();

        AtomicReference<Throwable> caught = new AtomicReference<>();
        CountDownLatch blocked = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(1);
        Thread acquirer = new Thread(() -> {
            try {
                blocked.countDown();
                pool.acquire();
            } catch (Throwable t) {
                caught.set(t);
                done.countDown();
            }
        });
        acquirer.start();
        assertTrue(blocked.await(2, TimeUnit.SECONDS));

        acquirer.interrupt();

        assertTrue(done.await(2, TimeUnit.SECONDS), "acquirer should wake on interrupt");
        assertNotNull(caught.get());
        assertTrue(caught.get() instanceof InterruptedException,
                "interrupted acquire must throw InterruptedException, got: " + caught.get());

        acquirer.join(2000);
    }

    @Test
    public void testExhaustionWakeableByClose() throws Exception {
        BufferPool pool = new BufferPool(1);
        pool.acquire();

        AtomicReference<Throwable> caught = new AtomicReference<>();
        CountDownLatch blocked = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(1);
        Thread acquirer = new Thread(() -> {
            try {
                blocked.countDown();
                pool.acquire();
            } catch (Throwable t) {
                caught.set(t);
                done.countDown();
            }
        });
        acquirer.start();
        assertTrue(blocked.await(2, TimeUnit.SECONDS));

        assertFalse(done.await(500, TimeUnit.MILLISECONDS), "must block before close");
        pool.close();

        assertTrue(done.await(2, TimeUnit.SECONDS), "blocked acquire must wake on close");
        assertNotNull(caught.get(), "acquire after close must throw (not silently proceed)");
        assertTrue(caught.get() instanceof StreamException,
                "acquire on closed pool must throw StreamException, got: " + caught.get());

        acquirer.join(2000);
    }

    @Test
    public void testTryAcquireTimeoutWhenExhausted() throws Exception {
        BufferPool pool = new BufferPool(1);
        pool.acquire();

        long start = System.nanoTime();
        boolean got = pool.tryAcquire(200, TimeUnit.MILLISECONDS);
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        assertFalse(got, "tryAcquire must return false when exhausted");
        assertTrue(elapsedMs >= 150, "tryAcquire must actually wait the timeout, elapsed=" + elapsedMs);
        assertEquals(1, pool.getGlobalUsage());
    }

    @Test
    public void testTryAcquireSucceedsWhenAvailable() throws Exception {
        BufferPool pool = new BufferPool(2);
        assertTrue(pool.tryAcquire(100, TimeUnit.MILLISECONDS));
        assertTrue(pool.tryAcquire(100, TimeUnit.MILLISECONDS));
        assertFalse(pool.tryAcquire(100, TimeUnit.MILLISECONDS));
        assertEquals(2, pool.getGlobalUsage());
    }

    @Test
    public void testCloseIsIdempotent() {
        BufferPool pool = new BufferPool(4);
        pool.close();
        assertTrue(pool.isClosed());
        pool.close();
        assertTrue(pool.isClosed());
    }

    @Test
    public void testAcquireAfterCloseThrowsFast() {
        BufferPool pool = new BufferPool(4);
        pool.close();
        assertThrows(StreamException.class, pool::acquire);
    }

    @Test
    public void testFairnessAcrossPartitions() throws Exception {
        // Fair semaphore: acquirers are served FIFO, preventing one partition
        // from monopolising permits. We verify FIFO ordering of two waiters.
        BufferPool pool = new BufferPool(1);
        pool.acquire();

        CountDownLatch firstBlocked = new CountDownLatch(1);
        CountDownLatch secondBlocked = new CountDownLatch(1);
        AtomicReference<String> firstAcquiredBy = new AtomicReference<>();

        Thread a = new Thread(() -> {
            try {
                firstBlocked.countDown();
                pool.acquire();
                firstAcquiredBy.compareAndSet(null, "A");
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }, "pool-acquire-A");
        Thread b = new Thread(() -> {
            try {
                secondBlocked.countDown();
                pool.acquire();
                firstAcquiredBy.compareAndSet(null, "B");
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }, "pool-acquire-B");

        a.start();
        assertTrue(firstBlocked.await(2, TimeUnit.SECONDS));
        b.start();
        assertTrue(secondBlocked.await(2, TimeUnit.SECONDS));
        Thread.sleep(200);

        pool.release();
        a.join(2000);
        // A registered first, fair semaphore should grant A first
        assertEquals("A", firstAcquiredBy.get());

        // cleanup
        b.interrupt();
        b.join(2000);
        pool.close();
    }
}
