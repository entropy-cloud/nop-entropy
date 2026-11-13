/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.connector.lookup;

import io.nop.api.core.time.CoreMetrics;
import io.nop.commons.test.time.ManualClock;
import io.nop.commons.test.time.ManualScheduler;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.testsupport.TestAwait;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
 * lazy re-init.
 *
 * <p>时间确定化（plan 2510）：TTL 判定经 {@code CoreMetrics.nanoTime()}，测试注册
 * {@link ManualClock} 推进过期；后台扫描调度经注入的 {@link ManualScheduler} 推进触发，
 * 不再等待真实毫秒定时器；跨线程交接用 latch / 条件等待。
 */
class TestCachingTableLookup {

    private ManualClock clock;

    @BeforeEach
    void registerManualClock() {
        clock = new ManualClock(1_000_000L);
        CoreMetrics.registerClock(clock);
    }

    @AfterEach
    void restoreDefaultClock() {
        CoreMetrics.registerClock(CoreMetrics.defaultClock());
    }

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
                // 显式同步：held lookup 等 release latch（超时仅作挂死保护），不再轮询墙钟。
                if (holdKeys.contains(key)) {
                    holdRelease.await(5, TimeUnit.SECONDS);
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

    private CachingTableLookup newLookup(StubLookup stub, long ttlMs, long refreshMs,
                                         int concurrency, int maxEntries) {
        return newLookup(stub, ttlMs, refreshMs, concurrency, maxEntries, null);
    }

    private CachingTableLookup newLookup(StubLookup stub, long ttlMs, long refreshMs,
                                         int concurrency, int maxEntries,
                                         ManualScheduler scanScheduler) {
        return new CachingTableLookup(stub, ttlMs, refreshMs, concurrency, maxEntries, scanScheduler);
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

        // TTL 过期由虚拟时钟推进触发：推进越过 ttl 后下一次 lookup 必然回源
        clock.advance(200);
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
        ManualScheduler scan = new ManualScheduler(clock);
        stub.values.put("k", "v1");
        CachingTableLookup lookup = newLookup(stub, 60_000L, 80L, 1, 100, scan);

        assertEquals("v1", lookup.lookup("k"));
        int loadsAfterSync = stub.loadCount.get();

        stub.values.put("k", "v2"); // only the background refresh sees this
        // 扫描调度由虚拟时间推进触发（不再等真实 80ms 周期）；刷新本体仍在有界线程池异步执行，
        // 以条件等待观测其完成。
        scan.advance(200);
        TestAwait.until("background refresh renewed the cached value",
                () -> "v2".equals(lookup.lookup("k")));
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
     * as hold keys; the scan (advanced on the manual scheduler) parks both workers, the
     * observer confirms inFlight = 2, and release lets them finish.
     */
    @Test
    void refreshConcurrencyOverlapsAndStaysCapped() throws Exception {
        StubLookup stub = new StubLookup();
        ManualScheduler scan = new ManualScheduler(clock);
        CachingTableLookup lookup = newLookup(stub, 60_000L, 50L, 2, 100, scan);

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
            try {
                if (stub.inFlight.get() >= 2) {
                    bothInFlight.countDown();
                }
            } finally {
                // always release so parked workers never leak into other tests
                release.countDown();
            }
        });
        scan.advance(100);
        observer.start();
        TestAwait.until("refresh workers parked in the stub (inFlight reached 2)",
                () -> stub.inFlight.get() >= 2);
        bothInFlight.countDown();
        release.countDown();
        observer.join(3000L);
        assertTrue(stub.maxInFlight.get() <= 2,
                "in-flight must never exceed the pool bound, got " + stub.maxInFlight.get());
        lookup.close();
    }

    @Test
    void refreshFailureKeepsStaleValueAndRecovers() throws Exception {
        StubLookup stub = new StubLookup();
        ManualScheduler scan = new ManualScheduler(clock);
        stub.values.put("k", "stable");
        CachingTableLookup lookup = newLookup(stub, 60_000L, 60L, 1, 100, scan);
        assertEquals("stable", lookup.lookup("k"));

        // the failing mode stays on until we have VERIFIED staleness — no recovery race.
        // refreshOne runs on the async refresh pool while scans run on the manual
        // scheduler: a single advance can land while the previous refresh still holds
        // the in-flight slot (its dispatch is then skipped) — so each verification
        // advance is re-armed inside the polling predicate (deterministic convergence,
        // deadline still enforced by TestAwait).
        stub.failing = true;
        stub.values.put("k", "updated");
        TestAwait.until("the background refresh attempted and failed", () -> {
            scan.advance(200);
            return stub.failCount.get() >= 1;
        });
        assertEquals("stable", lookup.lookup("k"),
                "a failed refresh keeps the stale value (cache intact, no sync requery)");

        // recovery: failing off → a later scan round picks up the new value
        stub.failing = false;
        TestAwait.until("recovered refresh delivers the new value", () -> {
            scan.advance(200);
            return "updated".equals(lookup.lookup("k"));
        });
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

        // 虚拟时钟制造不同的 load 时刻（最老 victim 排序），不再用真实 sleep 间隔
        lookup.lookup("a");
        clock.advance(1);
        lookup.lookup("b");
        clock.advance(1);
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
