package io.nop.ai.agent.session;

import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestInMemorySessionStore {

    @Test
    void testGetOrCreateIdempotent() {
        InMemorySessionStore store = new InMemorySessionStore();

        AgentSession s1 = store.getOrCreate("sess-1", "agent-a");
        assertNotNull(s1);
        assertEquals("sess-1", s1.getSessionId());
        assertEquals("agent-a", s1.getAgentName());

        AgentSession s2 = store.getOrCreate("sess-1", "agent-b");
        assertSame(s1, s2);
        assertEquals("agent-a", s2.getAgentName());
    }

    @Test
    void testGetReturnsNullForMissing() {
        InMemorySessionStore store = new InMemorySessionStore();

        assertNull(store.get("nonexistent"));
    }

    @Test
    void testGetReturnsExisting() {
        InMemorySessionStore store = new InMemorySessionStore();
        AgentSession created = store.getOrCreate("sess-1", "agent");

        AgentSession retrieved = store.get("sess-1");
        assertSame(created, retrieved);
    }

    @Test
    void testRemoveDeletesSession() {
        InMemorySessionStore store = new InMemorySessionStore();
        store.getOrCreate("sess-1", "agent");

        store.remove("sess-1");
        assertNull(store.get("sess-1"));
    }

    @Test
    void testGetAllReturnsAllSessions() {
        InMemorySessionStore store = new InMemorySessionStore();
        store.getOrCreate("sess-1", "agent-a");
        store.getOrCreate("sess-2", "agent-b");

        Collection<AgentSession> all = store.getAll();
        assertEquals(2, all.size());
    }

    @Test
    void testConcurrentAccessSafety() throws InterruptedException {
        InMemorySessionStore store = new InMemorySessionStore();
        int threadCount = 20;
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger sameInstanceCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    AgentSession s = store.getOrCreate("shared-session", "agent");
                    AgentSession fetched = store.get("shared-session");
                    if (s == fetched) {
                        sameInstanceCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        assertEquals(threadCount, sameInstanceCount.get());

        executor.shutdown();
    }
}
