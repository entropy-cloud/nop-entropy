package io.nop.job.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CRA 8.46 R4-06 (plan 2539) W-05 / 1.4-C1-003 — driver 红⑤ (latch-interleaved
 * deterministic red): an external {@code scanOnce} call racing an IN-FLIGHT timer
 * cycle used to reset the in-flight cursors at the public entry (duplicate
 * processing / torn cursors). Post-fix the in-flight guard REJECTS the second
 * entrant (WARN + conflict counter, zero cursor/chain disturbance). The 2496
 * insulation boundary stays untouched — this pin also asserts the rejection does
 * NOT leak into the insulation counters (rejection ≠ cycle failure).
 */
public class TestScannerInFlightGuardR406 {

    private CountDownLatch inFlight;
    private CountDownLatch releaseInFlight;

    @AfterEach
    void tearDown() {
        // latch-bound release: never leave a parked cycle behind
        if (releaseInFlight != null) {
            releaseInFlight.countDown();
        }
    }

    private AbstractBatchScanner newBlockingScanner(AtomicInteger fetchCount) {
        inFlight = new CountDownLatch(1);
        releaseInFlight = new CountDownLatch(1);
        return new AbstractBatchScanner() {
            final Cursor cursor = newCursor();

            @Override
            protected void onCycleStart() {
                inFlight.countDown();
            }

            @Override
            protected boolean scanBatch() {
                fetchCount.incrementAndGet();
                try {
                    // park the in-flight cycle: reads done, cycle NOT finished
                    if (!releaseInFlight.await(20, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("test seam: in-flight release timeout");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("test seam interrupted", e);
                }
                return false; // single batch per cycle
            }
        };
    }

    /** reflective read so the pin compiles against BOTH the pre-fix and post-fix base */
    private static long conflictCount(AbstractBatchScanner scanner) {
        try {
            java.lang.reflect.Method m = scanner.getClass().getMethod("getScanCycleConflictCount");
            return ((Number) m.invoke(scanner)).longValue();
        } catch (NoSuchMethodException e) {
            return -1L; // pre-fix base: the guard face does not exist yet
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static long cycleCount(AbstractBatchScanner scanner) {
        try {
            java.lang.reflect.Method m = scanner.getClass().getMethod("getScanCycleCount");
            return ((Number) m.invoke(scanner)).longValue();
        } catch (NoSuchMethodException e) {
            return -1L;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    public void concurrentSecondEntrantIsRejectedInFlightCycleUnaffected() throws Exception {
        AtomicInteger fetchCount = new AtomicInteger();
        AbstractBatchScanner scanner = newBlockingScanner(fetchCount);

        AtomicReference<Throwable> inFlightFailure = new AtomicReference<>();
        Thread inFlightCycle = new Thread(() -> {
            try {
                scanner.scanOnce();
            } catch (Throwable t) {
                inFlightFailure.set(t);
            }
        });
        inFlightCycle.start();
        assertTrue(inFlight.await(10, TimeUnit.SECONDS), "the in-flight cycle must start");
        assertEquals(1, fetchCount.get(), "the in-flight cycle entered its batch");

        // concurrent external entrant — pre-fix this RESET the in-flight cursor and
        // the parked cycle re-fetched the same window (duplicate processing);
        // post-fix it is rejected (WARN + conflict count) with zero disturbance
        long t0 = System.currentTimeMillis();
        scanner.scanOnce();
        long rejectionMillis = System.currentTimeMillis() - t0;
        assertTrue(rejectionMillis < 5000,
                "the rejected entrant must return immediately (not block on the in-flight cycle)");

        assertEquals(1L, conflictCount(scanner),
                "the concurrent entrant must be counted as a conflict (W-05 guard face)");
        assertEquals(1, fetchCount.get(),
                "the in-flight cycle must be undisturbed — no cursor reset, no re-fetch "
                        + "(pre-fix the external reset inflated the fetch count)");
        assertEquals(1L, cycleCount(scanner),
                "only the REAL cycle counts (rejection must not leak into insulation counters)");
        assertEquals(0, scanner.getScanFailureCount(),
                "rejection ≠ cycle failure: the insulation failure counter stays zero");

        // release the in-flight cycle; it finishes cleanly
        releaseInFlight.countDown();
        inFlightCycle.join(10000);
        assertTrue(!inFlightCycle.isAlive(), "the in-flight cycle must finish");
        if (inFlightFailure.get() != null) {
            throw new AssertionError("in-flight cycle failed", inFlightFailure.get());
        }
    }
}
