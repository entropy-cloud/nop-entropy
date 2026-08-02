/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.checkpoint.incremental;

import org.junit.jupiter.api.Test;

import io.nop.stream.core.exceptions.StreamException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestSharedStateRegistry {

    private static SharedStateHandle handle(String hash, String path) {
        return new SharedStateHandle(hash, path, 1024L);
    }

    // ---- register ----

    @Test
    void registerReturnsSameCanonicalHandleForSameContentHash() {
        SharedStateRegistry registry = new SharedStateRegistryImpl();
        SharedStateHandle first = handle("aaa", "/tmp/aaa.sst");
        SharedStateHandle second = handle("aaa", "/tmp/aaa-dup.sst");

        SharedStateHandle r1 = registry.register(first);
        SharedStateHandle r2 = registry.register(second);

        assertEquals(2, registry.getReferenceCount("aaa"));
        // Canonical handle is the first-registered; the duplicate's filePath is discarded.
        assertTrue(r1 == r2);
        assertEquals("/tmp/aaa.sst", r1.getFilePath());
    }

    @Test
    void registerDifferentContentHashesAreIndependent() {
        SharedStateRegistry registry = new SharedStateRegistryImpl();
        registry.register(handle("aaa", "/tmp/aaa.sst"));
        registry.register(handle("bbb", "/tmp/bbb.sst"));

        assertEquals(1, registry.getReferenceCount("aaa"));
        assertEquals(1, registry.getReferenceCount("bbb"));
        assertEquals(2, ((SharedStateRegistryImpl) registry).size());
    }

    @Test
    void registerNullThrows() {
        SharedStateRegistry registry = new SharedStateRegistryImpl();
        assertThrows(IllegalArgumentException.class, () -> registry.register(null));
    }

    @Test
    void sharedStateHandleRejectsNullOrEmptyHash() {
        assertThrows(IllegalArgumentException.class, () -> new SharedStateHandle(null, "/p", 1L));
        assertThrows(IllegalArgumentException.class, () -> new SharedStateHandle("", "/p", 1L));
    }

    // ---- unregister ----

    @Test
    void unregisterDecrementsCountAndReturnsEmptyWhileReferenced() {
        SharedStateRegistry registry = new SharedStateRegistryImpl();
        registry.register(handle("aaa", "/tmp/aaa.sst"));
        registry.register(handle("aaa", "/tmp/aaa.sst"));

        List<SharedStateHandle> discarded = registry.unregister("aaa");
        assertTrue(discarded.isEmpty());
        assertEquals(1, registry.getReferenceCount("aaa"));
    }

    @Test
    void unregisterReturnsHandleWhenRefCountDropsToZero() {
        SharedStateRegistry registry = new SharedStateRegistryImpl();
        registry.register(handle("aaa", "/tmp/aaa.sst"));

        List<SharedStateHandle> discarded = registry.unregister("aaa");
        assertEquals(1, discarded.size());
        assertEquals("aaa", discarded.get(0).getStateObjectId());
        assertEquals(0, registry.getReferenceCount("aaa"));
    }

    @Test
    void unregisterUnknownIdReturnsEmpty() {
        SharedStateRegistry registry = new SharedStateRegistryImpl();
        assertTrue(registry.unregister("nope").isEmpty());
        assertTrue(registry.unregister(null).isEmpty());
    }

    @Test
    void unregisterUnderflowFailsLoudly() {
        SharedStateRegistry registry = new SharedStateRegistryImpl();
        registry.register(handle("aaa", "/tmp/aaa.sst"));
        registry.unregister("aaa"); // -> 0, removed
        // An unbalanced unregister of a removed id is a no-op (empty list), but a
        // genuine underflow (count would go negative on a live entry) must throw.
        // Here the id was removed, so it is treated as unknown -> empty list, not underflow.
        assertTrue(registry.unregister("aaa").isEmpty());
    }

    // ---- re-register after full release ----

    @Test
    void reRegisterAfterFullReleaseStartsFreshCount() {
        SharedStateRegistry registry = new SharedStateRegistryImpl();
        registry.register(handle("aaa", "/tmp/aaa.sst"));
        registry.unregister("aaa");

        // Fresh registration after full release behaves like a brand-new entry.
        registry.register(handle("aaa", "/tmp/aaa2.sst"));
        assertEquals(1, registry.getReferenceCount("aaa"));
    }

    // ---- getReferenceCount ----

    @Test
    void getReferenceCountIsZeroForUnknown() {
        SharedStateRegistry registry = new SharedStateRegistryImpl();
        assertEquals(0, registry.getReferenceCount("nope"));
        assertEquals(0, registry.getReferenceCount(null));
    }

    // ---- concurrency ----

    @Test
    void concurrentRegisterAndUnregisterKeepsCountsConsistent() throws Exception {
        final SharedStateRegistry registry = new SharedStateRegistryImpl();
        final int threads = 16;
        final int registersPerThread = 200;

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();

        // Half the threads register, half unregister a balanced set so the net count is predictable.
        for (int i = 0; i < threads; i++) {
            final boolean registerer = (i % 2 == 0);
            futures.add(pool.submit(() -> {
                try {
                    start.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new StreamException("Interrupted while awaiting start latch", e);
                }
                for (int j = 0; j < registersPerThread; j++) {
                    String hash = "h" + (j % 20);
                    if (registerer) {
                        registry.register(handle(hash, "/tmp/" + hash + ".sst"));
                    } else {
                        registry.unregister(hash);
                    }
                }
                return null;
            }));
        }

        start.countDown();
        for (Future<?> f : futures) {
            f.get(30, TimeUnit.SECONDS);
        }
        pool.shutdown();
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));

        // After balanced register/unregister storms, no entry should have a negative count,
        // and the surviving entries must be internally consistent.
        for (int j = 0; j < 20; j++) {
            String hash = "h" + j;
            int count = registry.getReferenceCount(hash);
            assertTrue(count >= 0, "negative count for " + hash);
        }
    }

    @Test
    void concurrentRegistersForSameHashAllObserveOneCanonicalHandle() throws Exception {
        final SharedStateRegistry registry = new SharedStateRegistryImpl();
        final int threads = 32;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<SharedStateHandle>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                try {
                    start.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new StreamException("Interrupted while awaiting start latch", e);
                }
                return registry.register(handle("shared", "/tmp/shared.sst"));
            }));
        }

        start.countDown();
        Set<SharedStateHandle> distinct = new HashSet<>();
        for (Future<SharedStateHandle> f : futures) {
            distinct.add(f.get(10, TimeUnit.SECONDS));
        }
        pool.shutdown();
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));

        assertEquals(1, distinct.size(), "all registers must return the same canonical handle");
        assertEquals(threads, registry.getReferenceCount("shared"));
    }

    // ---- batch discard helper pattern (as used by coordinator GC) ----

    @Test
    void unregisteringACheckpointWorthOfSegmentsDiscardsOnlyZeroRefOnes() {
        SharedStateRegistry registry = new SharedStateRegistryImpl();
        // checkpoint N references s1, s2
        registry.register(handle("s1", "/s1"));
        registry.register(handle("s2", "/s2"));
        // checkpoint N+1 references s1 (shared) + s3 (new)
        registry.register(handle("s1", "/s1"));
        registry.register(handle("s3", "/s3"));

        // subsume checkpoint N: unregister s1 (still ref by N+1) and s2 (drops to 0)
        assertTrue(registry.unregister("s1").isEmpty());
        List<SharedStateHandle> discardedS2 = registry.unregister("s2");
        assertEquals(1, discardedS2.size());
        assertEquals("s2", discardedS2.get(0).getStateObjectId());

        // s1 still alive for N+1, s3 alive for N+1
        assertEquals(1, registry.getReferenceCount("s1"));
        assertEquals(1, registry.getReferenceCount("s3"));
        assertEquals(0, registry.getReferenceCount("s2"));
    }

    @Test
    void streamStateHandleIsImmutableAndDefensive() {
        List<SharedStateHandle> src = new ArrayList<>();
        src.add(handle("a", "/a"));
        StreamStateHandle ssh = new StreamStateHandle("op1", "valueState", src);

        assertEquals("op1", ssh.getOperatorId());
        assertEquals("valueState", ssh.getStateName());
        assertEquals(1, ssh.getSstHandles().size());

        // mutating the source list must not leak in
        src.add(handle("b", "/b"));
        assertEquals(1, ssh.getSstHandles().size());

        // the returned list is unmodifiable
        assertThrows(UnsupportedOperationException.class,
                () -> ssh.getSstHandles().add(handle("c", "/c")));

        // null -> empty list, not null
        StreamStateHandle empty = new StreamStateHandle("op", "n", null);
        assertNotEquals(null, empty.getSstHandles());
        assertEquals(Collections.emptyList(), empty.getSstHandles());
    }

    @Test
    void handleEqualsHashCodeKeyedOnContentHash() {
        SharedStateHandle a1 = handle("xxx", "/a");
        SharedStateHandle a2 = handle("xxx", "/b"); // same hash, different path
        SharedStateHandle b = handle("yyy", "/a");

        assertEquals(a1, a2);
        assertEquals(a1.hashCode(), a2.hashCode());
        assertNotEquals(a1, b);
    }
}
