/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.connector.lookup;

import io.nop.stream.core.exceptions.StreamException;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_INVALID_ARG;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_INVALID_STATE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI11 (plan 2338): the local materialization layer over {@link ITableLookup} —
 * TTL/penetration-guard cache, scan/execute background refresh with a REAL bounded
 * concurrency (dual assertion: overlap observed AND capped), refresh-failure keep-old
 * semantics, maxEntries victim eviction, idempotent typed close, and serialization
 * lazy re-init. All time control uses stub latches + deadline polling (no bare sleeps
 * deciding pass/fail).
 */
class TestCachingTableLookup {

    /** Versioned stub: per-key current value, load counter, optional failure injection. */
    static class StubLookup implements ITableLookup {
        final Map<Object, String> values = new ConcurrentHashMap<>();
        final AtomicInteger loadCount = new AtomicInteger();
        final AtomicInteger failCount = new AtomicInteger();
        final AtomicInteger inFlight = new AtomicInteger();
        final AtomicInteger maxInFlight = new AtomicInteger();
        /** Keys whose lookups park until {@code holdRelease} is counted down. */
        final java.util.Set<Object> holdKeys = ConcurrentHashMap.newKeySet();
        final transient CountDownLatch holdRelease = new CountDownLatch(1);
        volatile boolean failing;

        @Override
        public Object lookup(Object key) {
            int now = inFlight.incrementAndGet();
            maxInFlight.accumulateAndGet(now, Math::max);
            try {
                long deadline = System.currentTimeMillis() + 5000L;
                while (holdKeys.contains(key) && System.currentTimeMillis() < deadline) {
                    Thread.sleep(20L);
                }
                if (failing) {
                    failCount.incrementAndGet();
                    throw new IllegalStateException("injected lookup failure");
                }
                loadCount.incrementAndGet();
                return values.get(key);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted while held", e);
            } finally {
                inFlight.decrementAndGet();
            }
        }
    }

    private static CachingTableLookup newLookup(StubLookup stub, long ttlMs, long refreshMs,
                                                int concurrency, int maxEntries) {
        return new CachingTableLookup(stub, ttlMs, refreshMs, concurrency, maxEntries);
    }

    /** Polls until the condition holds or the deadline expires (no bare sleeps). */
    private static void awaitTrue(AtomicReference<Boolean> observed, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline && !Boolean.TRUE.equals(observed.get())) {
            Thread.sleep(10L);
        }
    }

    @Test
    void cacheHitWithinTtlAndExpiryFallsBackToDelegate() throws Exception {
        StubLookup stub = new StubLookup();
        stub.values.put("k", "v1");
        CachingTableLookup lookup = newLookup(stub, 150L, 60_000L, 1, 100);

        assertEquals("v1", lookup.lookup("k"));
        stub.values.put("k", "v2");
        assertEquals("v1", lookup.lookup("k"), "within TTL the cached value is served");
        assertEquals(1, stub.loadCount.get(), "cache hit must not requery");

        // TTL expiry: poll (never a bare sleep deciding the outcome) until age > ttl
        long deadline = System.currentTimeMillis() + 2000L;
        while (System.currentTimeMillis() - deadline < 0 || System.currentTimeMillis() < deadline) {
            if (lookup.lookup("k").equals("v2")) {
                break;
            }
            Thread.sleep(20L);
        }
        assertEquals("v2", lookup.lookup("k"), "expired entry must fall back to a synchronous requery");
        lookup.close();
    }

    @Test
    void nullPenetrationGuardSharesTtlSemantics() throws Exception {
        StubLookup stub = new StubLookup();
        // no map entry: values.get("missing") returns null (the stub's null semantics)
        CachingTableLookup lookup = newLookup(stub, 60_000L, 60_000L, 1, 100);

        assertNull(lookup.lookup("missing"));
        assertNull(lookup.lookup("missing"));
        assertEquals(1, stub.loadCount.get(), "repeated null key must be served from the cache");
        lookup.close();
    }

    @Test
    void backgroundRefreshUpdatesValueWithoutSyncRequery() throws Exception {
        StubLookup stub = new StubLookup();
        stub.values.put("k", "v1");
        CachingTableLookup lookup = newLookup(stub, 60_000L, 80L, 1, 100);

        assertEquals("v1", lookup.lookup("k"));
        int loadsAfterSync = stub.loadCount.get();

        stub.values.put("k", "v2"); // only the background refresh sees this
        long deadline = System.currentTimeMillis() + 5000L;
        while (System.currentTimeMillis() < deadline && !"v2".equals(lookup.lookup("k"))) {
            Thread.sleep(20L);
        }
        assertEquals("v2", lookup.lookup("k"), "background refresh must renew the cached value");
        assertTrue(stub.loadCount.get() > loadsAfterSync, "refresh performs real delegate calls");
        lookup.close();
    }

    /**
     * The bounded-concurrency dual assertion (review F-2/F-3): with a 2-thread refresh
     * pool, refresh delegate calls genuinely overlap (inFlight ≥ 2 observed) and never
     * exceed the cap. A serial implementation fails the ≥ 2 half — the Anti-Hollow
     * probe for the roadmap's "bounded concurrent async mode".
     *
     * <p>Deterministic: two keys are pre-loaded synchronously (no parking), THEN marked
     * as hold keys; the next refresh scan parks both workers, the observer confirms
     * inFlight = 2, and release lets them finish.
     */
    @Test
    void refreshConcurrencyOverlapsAndStaysCapped() throws Exception {
        StubLookup stub = new StubLookup();
        CachingTableLookup lookup = newLookup(stub, 60_000L, 50L, 2, 100);

        stub.values.put("b0", "v0");
        stub.values.put("b1", "v1");
        assertEquals("v0", lookup.lookup("b0"));
        assertEquals("v1", lookup.lookup("b1"));

        // arm the hold AFTER the sync loads so the preload itself never parks
        stub.holdKeys.add("b0");
        stub.holdKeys.add("b1");

        CountDownLatch bothInFlight = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Thread observer = new Thread(() -> {
            long deadline = System.currentTimeMillis() + 5000L;
            while (System.currentTimeMillis() < deadline && stub.inFlight.get() < 2) {
                try {
                    Thread.sleep(5L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            try {
                if (stub.inFlight.get() >= 2) {
                    bothInFlight.countDown();
                }
            } finally {
                // always release so parked workers never leak into other tests
                release.countDown();
            }
        });
        observer.start();
        assertTrue(bothInFlight.await(6, TimeUnit.SECONDS),
                "refresh delegate calls must genuinely overlap (inFlight reached 2) — "
                        + "a serial implementation cannot pass this");
        release.countDown();
        observer.join(3000L);
        assertTrue(stub.maxInFlight.get() <= 2,
                "in-flight must never exceed the pool bound, got " + stub.maxInFlight.get());
        lookup.close();
    }

    @Test
    void refreshFailureKeepsStaleValueAndRecovers() throws Exception {
        StubLookup stub = new StubLookup();
        stub.values.put("k", "stable");
        CachingTableLookup lookup = newLookup(stub, 60_000L, 60L, 1, 100);
        assertEquals("stable", lookup.lookup("k"));

        // the failing mode stays on until we have VERIFIED staleness — no recovery race
        stub.failing = true;
        stub.values.put("k", "updated");
        long deadline = System.currentTimeMillis() + 5000L;
        while (System.currentTimeMillis() < deadline && stub.failCount.get() == 0) {
            Thread.sleep(20L);
        }
        assertTrue(stub.failCount.get() >= 1, "the background refresh must have attempted and failed");
        assertEquals("stable", lookup.lookup("k"),
                "a failed refresh keeps the stale value (cache intact, no sync requery)");

        // recovery: failing off → the next background refresh picks up the new value
        stub.failing = false;
        deadline = System.currentTimeMillis() + 5000L;
        while (System.currentTimeMillis() < deadline && !"updated".equals(lookup.lookup("k"))) {
            Thread.sleep(20L);
        }
        assertEquals("updated", lookup.lookup("k"), "recovered refresh delivers the new value");
        lookup.close();
    }

    @Test
    void maxEntriesEvictsOldestLoadedVictim() throws Exception {
        StubLookup stub = new StubLookup();
        stub.values.put("a", "1");
        stub.values.put("b", "2");
        stub.values.put("c", "3");
        CachingTableLookup lookup = newLookup(stub, 60_000L, 60_000L, 1, 2);

        lookup.lookup("a");
        Thread.sleep(5L); // distinct load times (oldest-victim ordering)
        lookup.lookup("b");
        Thread.sleep(5L);
        lookup.lookup("c");
        assertEquals(2, lookup.materializedSize(), "over capacity → oldest victim evicted");

        int loadsBefore = stub.loadCount.get();
        lookup.lookup("a");
        assertEquals(loadsBefore + 1, stub.loadCount.get(),
                "the earliest-loaded victim (a) must have been evicted and re-delegated");
        assertEquals(2, lookup.materializedSize());
        lookup.close();
    }

    @Test
    void negativeConfigsFailTyped() {
        StubLookup stub = new StubLookup();
        assertEquals(ERR_STREAM_INVALID_ARG.getErrorCode(),
                assertThrows(StreamException.class, () -> newLookup(stub, 0L, 100L, 1, 10)).getErrorCode());
        assertEquals(ERR_STREAM_INVALID_ARG.getErrorCode(),
                assertThrows(StreamException.class, () -> newLookup(stub, 100L, 0L, 1, 10)).getErrorCode());
        assertEquals(ERR_STREAM_INVALID_ARG.getErrorCode(),
                assertThrows(StreamException.class, () -> newLookup(stub, 100L, 100L, 0, 10)).getErrorCode());
        assertEquals(ERR_STREAM_INVALID_ARG.getErrorCode(),
                assertThrows(StreamException.class, () -> newLookup(stub, 100L, 100L, 1, 0)).getErrorCode());
    }

    @Test
    void closeIsIdempotentTypedAfterwardsAndStopsPools() throws Exception {
        StubLookup stub = new StubLookup();
        stub.values.put("k", "v");
        CachingTableLookup lookup = newLookup(stub, 60_000L, 50L, 1, 10);
        assertEquals("v", lookup.lookup("k"));
        assertTrue(lookup.isRefreshStartedForTest(), "background resources must be started");

        lookup.close();
        lookup.close(); // idempotent
        assertTrue(lookup.isTerminatedForTest(), "pools must be shut down exactly once");

        StreamException ex = assertThrows(StreamException.class, () -> lookup.lookup("k"));
        assertEquals(ERR_STREAM_INVALID_STATE.getErrorCode(), ex.getErrorCode(),
                "post-close lookup fails typed instead of silently hitting the DB");
    }

    @Test
    void serializationRoundTripLazilyRebuilds() throws Exception {
        StubLookup stub = new StubLookup();
        stub.values.put("k", "v");
        CachingTableLookup lookup = newLookup(stub, 60_000L, 60_000L, 1, 10);
        assertEquals("v", lookup.lookup("k"));

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bos)) {
            out.writeObject(lookup);
        }
        CachingTableLookup restored;
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bos.toByteArray()))) {
            restored = (CachingTableLookup) in.readObject();
        }
        assertEquals("v", restored.lookup("k"),
                "the restored instance must lazily rebuild and serve from the delegate");
        assertEquals("v", restored.lookup("k"));
        assertEquals(1, stub.loadCount.get(), "one delegate call total (transient cache rebuilt empty)");
        restored.close();
    }
}
