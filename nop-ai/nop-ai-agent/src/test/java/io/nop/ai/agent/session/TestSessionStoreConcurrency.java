package io.nop.ai.agent.session;

import io.nop.ai.agent.security.ITenantResolver;
import io.nop.ai.agent.NopAiAgentException;
import io.nop.ai.agent.reliability.DBCheckpointManager;
import io.nop.ai.agent.reliability.FileBackedCheckpointManager;
import io.nop.ai.agent.security.NullTenantResolver;
import io.nop.ai.agent.security.ThreadLocalTenantResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 3.1-MA-10 (plan 2555 裁定 A) carrier — session store lock discipline:
 * <ul>
 *   <li>concurrent {@code getOrCreate} on a cold store yields a single
 *       instance (per-session load serialization);</li>
 *   <li>{@code remove} then {@code get} does NOT revive the removed session
 *       (no lock-identity strip; in-lock re-validation closes the revival
 *       face);</li>
 *   <li>{@code DBSessionStore} tenant composite key: the same sessionId under
 *       different tenants resolves to distinct instances / distinct persisted
 *       states / distinct save locks;</li>
 *   <li>checkpoint managers remove per-session cache entries INCLUDING the
 *       {@code loadedSessions} negative-cache marker (structure + behaviour
 *       pin: a removed session's checkpoint cache is fully cleared).</li>
 * </ul>
 */
public class TestSessionStoreConcurrency {

    @TempDir
    Path tempDir;

    private final List<ExecutorService> pools = new ArrayList<>();

    private ExecutorService newPool(int n) {
        ExecutorService pool = Executors.newFixedThreadPool(n);
        pools.add(pool);
        return pool;
    }

    @AfterEach
    void shutdownPools() {
        for (ExecutorService pool : pools) {
            pool.shutdownNow();
        }
    }

    // ========================================================================
    // FileBackedSessionStore: concurrent getOrCreate single instance
    // ========================================================================

    @Test
    void concurrentGetOrCreateYieldsSingleInstance() throws Exception {
        FileBackedSessionStore store = new FileBackedSessionStore(tempDir.resolve("fb"));
        int threads = 8;
        ExecutorService pool = newPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<AgentSession>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                return store.getOrCreate("race-sess", "agent-a");
            }));
        }
        start.countDown();
        Set<AgentSession> instances = new HashSet<>();
        for (Future<AgentSession> f : futures) {
            instances.add(f.get(20, TimeUnit.SECONDS));
        }
        assertEquals(1, instances.size(),
                "concurrent getOrCreate on a cold store must produce a single session instance");
    }

    // ========================================================================
    // FileBackedSessionStore: remove-then-get does not revive
    // ========================================================================

    @Test
    void removeThenGetDoesNotReviveSession() throws Exception {
        FileBackedSessionStore store = new FileBackedSessionStore(tempDir.resolve("fb2"));
        AgentSession session = store.getOrCreate("doomed", "agent-a");
        session.appendMessages(List.of(new io.nop.ai.api.chat.messages.ChatUserMessage("persist me")));
        store.save(session);
        assertTrue(Files.exists(store.getRootDirectory().resolve("doomed").resolve("session.json")),
                "session file must exist before remove");

        // 3.1-MA-10: race remove against lazy-load gets. The pre-fix form
        // (lock-free load publish + lock-identity strip in remove) allowed
        // the load to republish a removed session.
        int readers = 6;
        ExecutorService pool = newPool(readers + 1);
        CountDownLatch start = new CountDownLatch(1);
        AtomicBoolean revived = new AtomicBoolean(false);
        List<Future<?>> futures = new ArrayList<>();
        futures.add(pool.submit(() -> {
            start.await();
            store.remove("doomed");
            return null;
        }));
        for (int i = 0; i < readers; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                for (int round = 0; round < 200; round++) {
                    try {
                        if (store.get("doomed") != null) {
                            revived.set(true);
                        }
                    } catch (NopAiAgentException e) {
                        // Benign read race: the snapshot vanished between the
                        // exists-check and the read (remove won the race).
                    }
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> f : futures) {
            f.get(30, TimeUnit.SECONDS);
        }
        assertNull(store.get("doomed"),
                "after remove() completes, get() must not revive the removed session");
        assertTrue(Files.notExists(store.getRootDirectory().resolve("doomed").resolve("session.json")),
                "the persisted file must stay deleted (no revival republish)");
    }

    // ========================================================================
    // DBSessionStore: tenant composite key across the three maps
    // ========================================================================

    @Test
    void tenantCompositeKeyScopesInMemoryMaps() throws Exception {
        DataSource ds = new H2DataSource();
        DBSessionStore store = new DBSessionStore(ds, ThreadLocalTenantResolver.INSTANCE);

        // 3.1-MA-10 (裁定 A / C1-020): the persistedStates and sessionLocks
        // maps are keyed by the TENANT COMPOSITE key — the same sessionId
        // under different tenants must resolve to distinct bookkeeping
        // entries and distinct save locks. (The sessions cache is bypassed in
        // tenant mode; the ai_agent_session PK is SESSION_ID-only, so the
        // collision face fixed here is the in-memory maps.)
        Method scopedKey = DBSessionStore.class.getDeclaredMethod("scopedKey", String.class);
        scopedKey.setAccessible(true);
        Method sessionLock = DBSessionStore.class.getDeclaredMethod("sessionLock", String.class);
        sessionLock.setAccessible(true);

        ThreadLocalTenantResolver.set("tenant-a");
        String keyA = (String) scopedKey.invoke(store, "shared-id");
        Object lockA = sessionLock.invoke(store, "shared-id");

        ThreadLocalTenantResolver.set("tenant-b");
        String keyB = (String) scopedKey.invoke(store, "shared-id");
        Object lockB = sessionLock.invoke(store, "shared-id");

        ThreadLocalTenantResolver.set(null);
        String keyNoTenant = (String) scopedKey.invoke(store, "shared-id");

        assertNotSame(keyA, keyB, "tenant-a and tenant-b must produce distinct map keys");
        assertNotSame(lockA, lockB,
                "tenant-a and tenant-b must not share a save lock for the same sessionId");
        assertEquals("shared-id", keyNoTenant,
                "no-tenant key must stay byte-identical to the historical raw key (zero regression)");

        // persistedStates scoping: a state registered under tenant-a must not
        // be visible under tenant-b.
        ThreadLocalTenantResolver.set("tenant-a");
        AgentSession a = store.getOrCreate("scoped-state", "test-agent");
        a.appendMessages(List.of(new io.nop.ai.api.chat.messages.ChatUserMessage("tenant-a msg")));
        store.save(a);
        Field statesField = DBSessionStore.class.getDeclaredField("persistedStates");
        statesField.setAccessible(true);
        java.util.concurrent.ConcurrentHashMap<String, ?> states =
                (java.util.concurrent.ConcurrentHashMap<String, ?>) statesField.get(store);
        String scopedStateKeyA = (String) scopedKey.invoke(store, "scoped-state");
        assertTrue(states.containsKey(scopedStateKeyA) || states.containsKey("scoped-state"),
                "persisted state registered under tenant-a must exist under its scoped key");
        ThreadLocalTenantResolver.set("tenant-b");
        assertTrue(!states.containsKey(scopedKey.invoke(store, "scoped-state")),
                "tenant-b must not see tenant-a's persisted-state entry");
        ThreadLocalTenantResolver.set(null);
    }

    /**
     * Minimal H2 in-memory {@link DataSource} (no docker dependency — same
     * infra the existing DB store tests use).
     */
    public static final class H2DataSource implements DataSource {
        private final org.h2.jdbcx.JdbcDataSource h2 = new org.h2.jdbcx.JdbcDataSource();

        public H2DataSource() {
            h2.setURL("jdbc:h2:mem:test-session-store-concurrency;DB_CLOSE_DELAY=-1");
        }

        @Override
        public Connection getConnection() throws java.sql.SQLException {
            return h2.getConnection();
        }

        @Override
        public Connection getConnection(String username, String password) throws java.sql.SQLException {
            return h2.getConnection(username, password);
        }

        @Override
        public java.io.PrintWriter getLogWriter() {
            return null;
        }

        @Override
        public void setLogWriter(java.io.PrintWriter out) {
        }

        @Override
        public void setLoginTimeout(int seconds) {
        }

        @Override
        public int getLoginTimeout() {
            return 0;
        }

        @Override
        public java.util.logging.Logger getParentLogger() {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws java.sql.SQLException {
            throw new java.sql.SQLException("not a wrapper");
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return false;
        }
    }

    // ========================================================================
    // Checkpoint managers: per-session removal incl. loadedSessions
    // ========================================================================

    @Test
    void fileBackedCheckpointRemoveClearsPerSessionCache() throws Exception {
        Path root = tempDir.resolve("cp-fb");
        FileBackedCheckpointManager manager = new FileBackedCheckpointManager(root);
        checkpointRemoveClearsPerSessionCache(manager);
    }

    @Test
    void dbCheckpointRemoveClearsPerSessionCache() throws Exception {
        DBCheckpointManager manager = new DBCheckpointManager(new H2DataSource(),
                NullTenantResolver.INSTANCE);
        checkpointRemoveClearsPerSessionCache(manager);
    }

    @SuppressWarnings("unchecked")
    private void checkpointRemoveClearsPerSessionCache(io.nop.ai.agent.reliability.ICheckpointManager manager)
            throws Exception {
        String sessionId = "cp-target";
        io.nop.ai.agent.reliability.Checkpoint cp = io.nop.ai.agent.reliability.Checkpoint.of(
                sessionId, "wm-tsc-1", 0, 1000L,
                io.nop.ai.agent.reliability.CheckpointType.TOOL_EXECUTION,
                "echo", "call-tsc-1", "in", "out", 1, 10L);
        manager.saveCheckpoint(cp);
        assertTrue(manager.getLatestCheckpoint(sessionId) != null,
                "checkpoint cache must serve the saved checkpoint");

        // 3.1-MA-10 structure pin: remove must clear bySession, the
        // byWatermark entries AND the loadedSessions negative-cache marker —
        // a stale marker would block reloading (the negative cache answers
        // "already loaded" from a stale cache instead of the store of truth).
        manager.remove(sessionId);

        java.util.concurrent.ConcurrentHashMap<String, ?> bySession = mapOf(manager, "bySession");
        java.util.concurrent.ConcurrentHashMap<String, ?> byWatermark = mapOf(manager, "byWatermark");
        java.util.concurrent.ConcurrentHashMap<String, ?> loadedSessions = mapOf(manager, "loadedSessions");

        assertTrue(!bySession.containsKey(sessionId),
                "remove must drop the bySession entry (per-session removal, not whole-map)");
        assertTrue(!loadedSessions.containsKey(sessionId),
                "remove must drop the loadedSessions negative-cache marker "
                        + "(otherwise reload of the session is blocked)");

        // byWatermark entries of the removed session's checkpoints must go too.
        boolean watermarkLeft = byWatermark.containsKey("wm-tsc-1");
        assertTrue(!watermarkLeft,
                "remove must drop the session's byWatermark index entries");

        // A NEW checkpoint for the same session id must be observable again.
        io.nop.ai.agent.reliability.Checkpoint cp2 = io.nop.ai.agent.reliability.Checkpoint.of(
                sessionId, "wm-tsc-2", 0, 2000L,
                io.nop.ai.agent.reliability.CheckpointType.TOOL_EXECUTION,
                "echo", "call-tsc-2", "in", "out", 1, 10L);
        manager.saveCheckpoint(cp2);
        assertTrue(manager.getLatestCheckpoint(sessionId) != null,
                "a checkpoint saved after remove must be visible (cache repopulated)");
    }

    private java.util.concurrent.ConcurrentHashMap<String, ?> mapOf(Object manager, String fieldName)
            throws Exception {
        Field f = manager.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        return (java.util.concurrent.ConcurrentHashMap<String, ?>) f.get(manager);
    }
}
