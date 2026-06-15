package io.nop.ai.agent.memory;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import io.nop.ai.agent.engine.NopAiAgentException;

/**
 * Phase 1 unit tests for the default {@link InMemoryMemoryStoreProvider}.
 *
 * <p>Verifies the per-session isolation contract:
 * <ul>
 *     <li>Same sessionId returns the same store instance (idempotent)</li>
 *     <li>Different sessionIds return different store instances (isolation)</li>
 *     <li>Concurrent {@code getOrCreate} for the same sessionId returns one instance</li>
 *     <li>Null / empty sessionId fail fast with descriptive error</li>
 * </ul>
 */
public class TestInMemoryMemoryStoreProvider {

    @Test
    void sameSessionIdReturnsSameInstance() {
        InMemoryMemoryStoreProvider provider = new InMemoryMemoryStoreProvider();

        IAiMemoryStore store1 = provider.getOrCreate("session-1");
        IAiMemoryStore store2 = provider.getOrCreate("session-1");

        assertSame(store1, store2, "Same sessionId must return the same store instance");
        assertEquals(1, provider.sessionCount());
    }

    @Test
    void differentSessionIdsReturnDifferentInstances() {
        InMemoryMemoryStoreProvider provider = new InMemoryMemoryStoreProvider();

        IAiMemoryStore storeA = provider.getOrCreate("session-A");
        IAiMemoryStore storeB = provider.getOrCreate("session-B");

        assertNotSame(storeA, storeB, "Different sessionIds must return different store instances");
        assertEquals(2, provider.sessionCount());
    }

    @Test
    void perSessionIsolationDataDoesNotLeak() {
        InMemoryMemoryStoreProvider provider = new InMemoryMemoryStoreProvider();

        IAiMemoryStore storeA = provider.getOrCreate("session-A");
        AiMemoryItem item = new AiMemoryItem();
        item.setKey("k1");
        item.setContent("private-to-A");
        storeA.add(item);

        IAiMemoryStore storeB = provider.getOrCreate("session-B");

        assertEquals(1, storeA.getAll(null).size());
        assertTrue(storeB.getAll(null).isEmpty(),
                "Session B must not see items written by Session A");
    }

    @Test
    void concurrentGetOrCreateReturnsSameInstance() throws Exception {
        InMemoryMemoryStoreProvider provider = new InMemoryMemoryStoreProvider();
        int threads = 8;

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicReference<IAiMemoryStore> firstSeen = new AtomicReference<>();
        AtomicInteger mismatchCount = new AtomicInteger(0);

        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                try {
                    start.await();
                    IAiMemoryStore s = provider.getOrCreate("shared-session");
                    firstSeen.compareAndSet(null, s);
                    if (s != firstSeen.get()) {
                        mismatchCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        assertTrue(done.await(30, TimeUnit.SECONDS));
        pool.shutdown();

        assertEquals(0, mismatchCount.get(), "All threads must observe the same store instance");
        assertEquals(1, provider.sessionCount());
    }

    @Test
    void nullSessionIdFailsFast() {
        InMemoryMemoryStoreProvider provider = new InMemoryMemoryStoreProvider();
        assertThrows(NopAiAgentException.class, () -> provider.getOrCreate(null));
    }

    @Test
    void emptySessionIdFailsFast() {
        InMemoryMemoryStoreProvider provider = new InMemoryMemoryStoreProvider();
        assertThrows(NopAiAgentException.class, () -> provider.getOrCreate(""));
    }
}
