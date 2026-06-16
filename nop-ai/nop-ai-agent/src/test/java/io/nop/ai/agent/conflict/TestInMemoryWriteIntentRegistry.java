package io.nop.ai.agent.conflict;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 214 focused tests for {@link InMemoryWriteIntentRegistry}: verifies
 * the atomic register-and-get-conflicting semantics, session release, and
 * TOCTOU safety under concurrent registration.
 */
public class TestInMemoryWriteIntentRegistry {

    private static final String PATH = "/workspace/src/main/Foo.java";

    private static WriteIntent intent(String session, String path) {
        return new WriteIntent(session, "agent-" + session, path, "write-file",
                System.currentTimeMillis());
    }

    @Test
    void firstRegistrationReturnsEmptyConflicting() {
        InMemoryWriteIntentRegistry registry = new InMemoryWriteIntentRegistry();

        Set<WriteIntent> conflicting = registry.registerAndGetConflicting(intent("s1", PATH));

        assertTrue(conflicting.isEmpty(),
                "first registration on a path → no conflicting intents");
    }

    @Test
    void crossSessionRegistrationReturnsPriorIntent() {
        InMemoryWriteIntentRegistry registry = new InMemoryWriteIntentRegistry();
        WriteIntent first = intent("s1", PATH);
        registry.registerAndGetConflicting(first);

        Set<WriteIntent> conflicting = registry.registerAndGetConflicting(intent("s2", PATH));

        assertEquals(1, conflicting.size(),
                "second session on same path → must see the first session's intent");
        WriteIntent only = conflicting.iterator().next();
        assertEquals("s1", only.getSessionId());
        assertEquals(PATH, only.getFilePath());
    }

    @Test
    void sameSessionRegistrationReturnsNoConflict() {
        InMemoryWriteIntentRegistry registry = new InMemoryWriteIntentRegistry();
        registry.registerAndGetConflicting(intent("s1", PATH));

        Set<WriteIntent> conflicting = registry.registerAndGetConflicting(intent("s1", PATH));

        assertTrue(conflicting.isEmpty(),
                "same session registering twice → no cross-session conflict");
    }

    @Test
    void differentPathsDoNotConflict() {
        InMemoryWriteIntentRegistry registry = new InMemoryWriteIntentRegistry();
        registry.registerAndGetConflicting(intent("s1", "/a/b.txt"));

        Set<WriteIntent> conflicting = registry.registerAndGetConflicting(intent("s2", "/c/d.txt"));

        assertTrue(conflicting.isEmpty(),
                "different paths → never a conflict");
    }

    @Test
    void releaseSessionClearsItsIntents() {
        InMemoryWriteIntentRegistry registry = new InMemoryWriteIntentRegistry();
        registry.registerAndGetConflicting(intent("s1", PATH));
        registry.registerAndGetConflicting(intent("s1", "/other.txt"));
        assertFalse(registry.isEmpty(), "registry should hold s1 intents before release");

        registry.releaseSession("s1");

        assertTrue(registry.isEmpty(),
                "after releaseSession(s1), no intents remain");
        // After release, a new session can register without seeing a conflict.
        Set<WriteIntent> conflicting = registry.registerAndGetConflicting(intent("s2", PATH));
        assertTrue(conflicting.isEmpty(),
                "after release, a new session sees no conflict on the same path");
    }

    @Test
    void releaseSessionPreservesOtherSessions() {
        InMemoryWriteIntentRegistry registry = new InMemoryWriteIntentRegistry();
        registry.registerAndGetConflicting(intent("s1", PATH));
        registry.registerAndGetConflicting(intent("s2", PATH));

        registry.releaseSession("s1");

        // s2's intent remains; a new session s3 on the same path must see s2.
        Set<WriteIntent> conflicting = registry.registerAndGetConflicting(intent("s3", PATH));
        assertEquals(1, conflicting.size());
        assertEquals("s2", conflicting.iterator().next().getSessionId());
    }

    @Test
    void releaseUnknownSessionIsNoOp() {
        InMemoryWriteIntentRegistry registry = new InMemoryWriteIntentRegistry();
        registry.registerAndGetConflicting(intent("s1", PATH));

        // Must not throw and must not corrupt state.
        registry.releaseSession("nonexistent");

        Set<WriteIntent> conflicting = registry.registerAndGetConflicting(intent("s2", PATH));
        assertEquals(1, conflicting.size(),
                "release of an unknown session must not affect existing intents");
    }

    @Test
    void registerRejectsNullIntent() {
        InMemoryWriteIntentRegistry registry = new InMemoryWriteIntentRegistry();
        assertThrows(IllegalArgumentException.class,
                () -> registry.registerAndGetConflicting(null));
    }

    @Test
    void registerRejectsNullFilePath() {
        InMemoryWriteIntentRegistry registry = new InMemoryWriteIntentRegistry();
        assertThrows(IllegalArgumentException.class,
                () -> registry.registerAndGetConflicting(new WriteIntent("s1", "a", null, "op", 1L)));
    }

    @Test
    void getIntentsForPathReturnsSnapshot() {
        InMemoryWriteIntentRegistry registry = new InMemoryWriteIntentRegistry();
        registry.registerAndGetConflicting(intent("s1", PATH));

        Set<WriteIntent> snapshot = registry.getIntentsForPath(PATH);

        assertEquals(1, snapshot.size());
        // The snapshot must be unmodifiable.
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.add(intent("s9", PATH)));
    }

    // ========================================================================
    // TOCTOU safety: concurrent registration of two different sessions on
    // the same path must not let both observe an empty conflict set.
    // At least one (typically the loser of the race) must see the other's
    // intent. This is the atomicity guarantee that prevents two concurrent
    // agents from both believing they have exclusive write access.
    // ========================================================================

    @Test
    void concurrentRegistrationIsAtomic() throws InterruptedException {
        // Run several trials — a non-atomic implementation would let both
        // callers see empty at least occasionally. We assert that in EVERY
        // trial at least one of the two concurrent registrations observed
        // a conflict.
        final int trials = 50;
        for (int t = 0; t < trials; t++) {
            final InMemoryWriteIntentRegistry registry = new InMemoryWriteIntentRegistry();
            // Use a unique path per trial to avoid any cross-trial state.
            final String path = "/concurrent/trial-" + t + "/file.txt";
            final CountDownLatch ready = new CountDownLatch(2);
            final CountDownLatch fire = new CountDownLatch(1);
            final AtomicReference<Set<WriteIntent>> s1Conflicts = new AtomicReference<>();
            final AtomicReference<Set<WriteIntent>> s2Conflicts = new AtomicReference<>();

            Runnable r1 = () -> {
                ready.countDown();
                try { fire.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
                s1Conflicts.set(registry.registerAndGetConflicting(
                        new WriteIntent("s1", "a1", path, "write-file", System.nanoTime())));
            };
            Runnable r2 = () -> {
                ready.countDown();
                try { fire.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
                s2Conflicts.set(registry.registerAndGetConflicting(
                        new WriteIntent("s2", "a2", path, "write-file", System.nanoTime())));
            };

            ExecutorService pool = Executors.newFixedThreadPool(2);
            try {
                pool.submit(r1);
                pool.submit(r2);
                assertTrue(ready.await(2, TimeUnit.SECONDS), "both threads must reach the barrier");
                fire.countDown(); // release both simultaneously
                pool.shutdown();
                assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS), "pool must terminate");
            } finally {
                if (!pool.isTerminated()) {
                    pool.shutdownNow();
                }
            }

            Set<WriteIntent> c1 = s1Conflicts.get();
            Set<WriteIntent> c2 = s2Conflicts.get();
            assertNotNull(c1, "s1 must have produced a result");
            assertNotNull(c2, "s2 must have produced a result");

            boolean s1SawConflict = !c1.isEmpty();
            boolean s2SawConflict = !c2.isEmpty();
            assertTrue(s1SawConflict || s2SawConflict,
                    "TOCTOU violated in trial " + t + ": both concurrent registrations saw no conflict");
        }
    }

    @Test
    void concurrentSameSessionRegistrationsAreSafe() throws InterruptedException {
        // Same-session concurrent registrations never conflict with each
        // other (the registry filters by sessionId). This test guards
        // against a regression where the per-path monitor accidentally
        // deadlocks same-session callers.
        final InMemoryWriteIntentRegistry registry = new InMemoryWriteIntentRegistry();
        final String path = "/concurrent/same-session.txt";
        final String session = "shared-session";
        final int n = 8;
        final CountDownLatch done = new CountDownLatch(n);
        List<Runnable> tasks = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            tasks.add(() -> {
                try {
                    registry.registerAndGetConflicting(
                            new WriteIntent(session, "a", path, "write-file", System.nanoTime()));
                } finally {
                    done.countDown();
                }
            });
        }
        ExecutorService pool = Executors.newFixedThreadPool(n);
        try {
            tasks.forEach(pool::submit);
            assertTrue(done.await(5, TimeUnit.SECONDS), "all same-session tasks must complete without deadlock");
        } finally {
            pool.shutdownNow();
        }

        // All n intents from the same session are now registered on the path.
        Set<WriteIntent> snapshot = registry.getIntentsForPath(path);
        assertEquals(n, snapshot.size(),
                "all same-session concurrent registrations must be recorded");
    }

    @Test
    void randomSessionConcurrentRegistrationsAllRecordedOrObserved() throws InterruptedException {
        // M sessions concurrently registering on the same path. The total
        // number of recorded intents must equal M (atomicity: every
        // register call appends exactly once). For each call that returned
        // an empty conflict set, every later call must have observed it.
        // We assert the simpler invariant: total registered == M, and at
        // most one call saw an empty conflict set.
        final InMemoryWriteIntentRegistry registry = new InMemoryWriteIntentRegistry();
        final String path = "/concurrent/multi-session.txt";
        final int m = 12;
        final CountDownLatch ready = new CountDownLatch(m);
        final CountDownLatch fire = new CountDownLatch(1);
        final List<java.util.concurrent.atomic.AtomicReference<Set<WriteIntent>>> results = new ArrayList<>();
        final List<String> sessions = new ArrayList<>();
        for (int i = 0; i < m; i++) {
            String sid = UUID.randomUUID().toString();
            sessions.add(sid);
            results.add(new java.util.concurrent.atomic.AtomicReference<>());
        }
        ExecutorService pool = Executors.newFixedThreadPool(m);
        try {
            for (int i = 0; i < m; i++) {
                final int idx = i;
                final String sid = sessions.get(idx);
                pool.submit(() -> {
                    ready.countDown();
                    try { fire.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
                    results.get(idx).set(registry.registerAndGetConflicting(
                            new WriteIntent(sid, "a", path, "write-file", System.nanoTime())));
                });
            }
            assertTrue(ready.await(3, TimeUnit.SECONDS), "all threads must reach the barrier");
            fire.countDown();
            pool.shutdown();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS), "pool must terminate");
        } finally {
            if (!pool.isTerminated()) {
                pool.shutdownNow();
            }
        }

        // Invariant 1: total recorded intents == m
        Set<WriteIntent> snapshot = registry.getIntentsForPath(path);
        assertEquals(m, snapshot.size(),
                "every concurrent registration must be recorded exactly once");

        // Invariant 2: at most one caller saw an empty conflict set
        // (the very first one to win the race). All others must see at
        // least one conflict.
        long emptyCount = results.stream()
                .map(java.util.concurrent.atomic.AtomicReference::get)
                .filter(java.util.Objects::nonNull)
                .filter(Set::isEmpty)
                .count();
        assertTrue(emptyCount <= 1,
                "at most one concurrent caller may see an empty conflict set; saw: " + emptyCount);
    }
}
