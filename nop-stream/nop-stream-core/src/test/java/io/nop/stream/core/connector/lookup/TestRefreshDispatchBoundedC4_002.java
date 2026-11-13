package io.nop.stream.core.connector.lookup;

import io.nop.stream.core.testsupport.TestAwait;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * C4-002 / C10-003 (plan 2510 R2-07, adjudication 3): the background refresh
 * dispatch must be bounded and de-duplicated — a hung/slow delegate must not
 * re-enqueue the same aged key on every scan round (unbounded queue growth = OOM
 * candidate + repeated DB probing).
 *
 * <p>Red driver (design 3.11 seam 3): one aged key whose refresh parks in the
 * delegate across multiple scan rounds. On the unfixed code every round enqueues
 * another {@code refreshOne} task for the same key (delegate invocation count grows
 * linearly with the number of rounds = red); on the fixed code the in-flight key is
 * not re-dispatched (exactly one delegate invocation = green) and the pending
 * dispatch depth stays bounded with an explicit drop counter (no silent backlog).
 */
class TestRefreshDispatchBoundedC4_002 {

    /** Stub whose lookups for {@code holdKeys} park until released. */
    static class HoldingStub implements ITableLookup {
        final Map<Object, String> values = new ConcurrentHashMap<>();
        final AtomicInteger loadCount = new AtomicInteger();
        final AtomicInteger inFlight = new AtomicInteger();
        final java.util.Set<Object> holdKeys = ConcurrentHashMap.newKeySet();
        final CountDownLatch holdRelease = new CountDownLatch(1);

        @Override
        public Object lookup(Object key) {
            inFlight.incrementAndGet();
            try {
                if (holdKeys.contains(key)) {
                    try {
                        holdRelease.await(30, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                loadCount.incrementAndGet();
                return values.getOrDefault(key, "v-" + key);
            } finally {
                inFlight.decrementAndGet();
            }
        }
    }

    /**
     * Core red driver: the same aged key must not be re-dispatched while its
     * refresh is still in flight (in-flight key de-duplication).
     */
    @Test
    void c4_002_inFlightKeyIsNotRedispatchedAcrossScanRounds() throws Exception {
        HoldingStub stub = new HoldingStub();
        stub.holdKeys.add("k");
        // refreshIntervalMs = 300: the scan period AND the aging threshold are both
        // 300ms, so after the background refresh renews the entry the key stays
        // fresh well beyond the drain window — any further delegate invocation for
        // the key in this test is an in-flight de-duplication violation, not
        // legitimate re-aging.
        CachingTableLookup lookup = new CachingTableLookup(stub, 60_000L, 300L, 1, 100);

        // Load the entry synchronously (this one is not held: holdKeys only gates
        // the background refresh, which starts after the entry has aged).
        stub.holdKeys.remove("k");
        stub.values.put("k", "v-k");
        assertEquals("v-k", lookup.lookup("k"));
        stub.holdKeys.add("k");

        // Let the entry age past refreshIntervalMs, then wait for the first scan
        // round to dispatch the refresh (it parks in the delegate).
        Thread.sleep(500);
        long observeUntil = System.currentTimeMillis() + 1000;
        while (System.currentTimeMillis() < observeUntil && stub.inFlight.get() == 0) {
            Thread.sleep(20);
        }
        assertTrue(stub.inFlight.get() > 0, "a background refresh must have been dispatched");
        assertEquals(1, lookup.getInFlightRefreshKeyCountForTest(),
                "the in-flight slot must be taken exactly once for the parked key");

        // Let more scan rounds elapse while the refresh is parked: none of them may
        // re-dispatch the in-flight key (zero new pending dispatches, zero drops).
        Thread.sleep(700);
        assertEquals(0, lookup.getPendingRefreshDispatchCountForTest(),
                "C4-002: an in-flight key must not be re-dispatched by later scan rounds "
                        + "(pending dispatches would accumulate on a hung delegate)");
        assertEquals(0, lookup.getDroppedRefreshDispatchCountForTest(),
                "C4-002: an in-flight key must not even reach the bounded queue");

        stub.holdRelease.countDown(); // unblock the delegate
        // Wait for the refresh to drain; the renewed entry stays fresh (300ms
        // threshold), so no further refresh may be dispatched for the key.
        long drainUntil = System.currentTimeMillis() + 3_000;
        while (System.currentTimeMillis() < drainUntil && stub.inFlight.get() > 0) {
            Thread.sleep(20);
        }

        assertEquals(2, stub.loadCount.get(),
                "C4-002 red driver: an in-flight key must not be re-dispatched by later scan "
                        + "rounds (1 sync load + exactly 1 background refresh expected); "
                        + "a linear count = unbounded re-enqueue on a hung delegate");
        assertEquals(0, stub.inFlight.get(), "no refresh task may remain parked after release");

        lookup.close();
    }

    /**
     * Bounded dispatch queue: with a slow delegate and more aged keys than the
     * queue capacity, the excess dispatches are dropped with an explicit counter
     * (fail-loud degradation, no silent unbounded backlog).
     */
    @Test
    void c4_002_dispatchQueueIsBoundedWithExplicitDropCount() throws Exception {
        HoldingStub stub = new HoldingStub();
        CachingTableLookup lookup = new CachingTableLookup(stub, 60_000L, 50L, 1, 100);

        // 20 distinct keys, all aged; concurrency=1 → queue capacity = 1 * 2.
        // The first dispatched refresh parks (hold every key), so the remaining
        // keys can only be dropped, never queued without bound.
        for (int i = 0; i < 20; i++) {
            stub.values.put("key" + i, "v" + i);
            lookup.lookup("key" + i);
        }
        stub.holdKeys.addAll(stub.values.keySet());
        Thread.sleep(150);

        // Several scan rounds against a parked delegate.
        TestAwait.elapsed("scan rounds against parked delegate", 500);

        assertEquals(1, stub.inFlight.get(),
                "the single-thread pool must have exactly one parked refresh");
        assertTrue(lookup.getDroppedRefreshDispatchCountForTest() > 0,
                "C4-002: excess dispatches must be dropped with an explicit counter "
                        + "(fail-loud degradation), not silently queued without bound");
        assertTrue(lookup.getPendingRefreshDispatchCountForTest() <= 2,
                "the pending dispatch depth must stay within refreshMaxConcurrency * 2");
        // The in-flight set holds dispatched-but-unfinished keys: at most one key
        // per running task (concurrency = 1) plus one key per queued task (queue
        // capacity = refreshMaxConcurrency * 2 = 2).
        assertTrue(lookup.getInFlightRefreshKeyCountForTest() <= 3,
                "the in-flight key set must stay within concurrency + queue capacity");

        stub.holdRelease.countDown();
        lookup.close();
    }
}
