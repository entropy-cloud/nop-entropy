package io.nop.ai.agent.runtime.lock;

import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.dao.jdbc.datasource.SimpleDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 221 Phase 2 end-to-end test: simulates two JVM processes (instance
 * A and instance B) sharing the same H2 DB via two independent
 * {@link DbSessionTakeoverLock} instances. Each instance represents a
 * distinct engine process — they share nothing in memory, only the DB.
 *
 * <p>This test proves the cross-process correctness property end-to-end:
 * <ol>
 *   <li>A acquires "s1" → B cannot acquire "s1" (cross-process
 *       double-execution is prevented).</li>
 *   <li>A releases "s1" → B can now acquire "s1" (clean handoff).</li>
 *   <li>A acquires "s1" with a short lease → A "crashes" (no release) →
 *       after TTL, B preempts the stale lease (passive fail-safe).</li>
 * </ol>
 *
 * <p>Satisfies Minimum Rules #22 (Anti-Hollow) — the verification uses
 * only public API on two real lock instances sharing one real DB.
 *
 * <p>Value-level verification (MA4.3-13 upgrade): the lock ownership
 * transitions are asserted against the shared DB row's {@code LOCK_OWNER}
 * field (queried via {@link AiAgentSessionLockTable} public constants),
 * not just the boolean return values of the public API.
 */
public class TestDbSessionTakeoverLockDualInstanceE2E {

    private DataSource dataSource;
    private String dbUrl;

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    @BeforeEach
    void setUp() {
        // A single shared DB simulates a multi-instance deployment
        // (instance A and B share one DB).
        dbUrl = "jdbc:h2:mem:test-takeover-e2e-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        SimpleDataSource ds = new SimpleDataSource();
        ds.setDriverClassName("org.h2.Driver");
        ds.setUrl(dbUrl);
        ds.setUsername("sa");
        ds.setPassword("");
        dataSource = ds;
    }

    @AfterEach
    void tearDown() {
        if (dataSource instanceof AutoCloseable) {
            try {
                ((AutoCloseable) dataSource).close();
            } catch (Exception ignored) {
                // best-effort close during teardown
            }
        }
    }

    /**
     * Read the {@code LOCK_OWNER} value of the shared DB row for a session,
     * or {@code null} when no lease row exists. Queries through the
     * {@link AiAgentSessionLockTable} public constants so the assertion
     * target matches the production schema.
     */
    private String readLockOwner(String sessionId) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT " + AiAgentSessionLockTable.COL_LOCK_OWNER
                             + " FROM " + AiAgentSessionLockTable.TABLE_NAME
                             + " WHERE " + AiAgentSessionLockTable.COL_SESSION_ID + " = ?")) {
            ps.setString(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    /**
     * End-to-end scenario: A acquires → B blocked → A releases → B acquires.
     * Two independent lock instances share the same DB (no shared memory
     * state) — only the DB row carries the lease. The {@code LOCK_OWNER}
     * field value transition is asserted at each step.
     */
    @Test
    void dualInstanceAcquireBlockReleaseAcquire() throws SQLException {
        // Two independent lock instances pointing at the same DB — each
        // represents a distinct JVM process.
        DbSessionTakeoverLock instanceA = new DbSessionTakeoverLock(dataSource);
        DbSessionTakeoverLock instanceB = new DbSessionTakeoverLock(dataSource);

        // A acquires first.
        assertTrue(instanceA.tryAcquire("s1", "engine-A", 60_000L),
                "Instance A acquires first → success");
        assertEquals("engine-A", readLockOwner("s1"),
                "DB row ownership must be engine-A after A's acquire");

        // B cannot acquire while A holds the active lease.
        assertFalse(instanceB.tryAcquire("s1", "engine-B", 60_000L),
                "Instance B cannot preempt instance A's active lease");
        assertEquals("engine-A", readLockOwner("s1"),
                "B's failed acquire must NOT transfer ownership (row still engine-A)");

        // isHeld reflects the cross-process reality on both instances.
        assertTrue(instanceB.isHeld("s1"),
                "Instance B observes the lease held by A (shared DB)");
        assertTrue(instanceA.isHeld("s1"),
                "Instance A also observes its own lease");

        // A releases — clean handoff.
        assertTrue(instanceA.release("s1", "engine-A"),
                "Instance A releases its own lease");
        assertNull(readLockOwner("s1"),
                "release must delete the lease row (no owner)");

        // B can now acquire (clean handoff after release).
        assertTrue(instanceB.tryAcquire("s1", "engine-B", 60_000L),
                "After release, instance B can acquire");
        assertEquals("engine-B", readLockOwner("s1"),
                "DB row ownership must transition to engine-B after B's acquire");

        // Cross-process conditional release: A cannot release B's lease.
        assertFalse(instanceA.release("s1", "engine-A"),
                "Instance A can no longer release (lease is now B's)");
        assertEquals("engine-B", readLockOwner("s1"),
                "A's failed release must leave ownership with engine-B");
        assertTrue(instanceB.release("s1", "engine-B"),
                "Instance B releases its own lease");
        assertNull(readLockOwner("s1"),
                "B's release must delete the lease row");
    }

    /**
     * End-to-end stale-lock preemption scenario: A acquires a short lease,
     * then "crashes" (does not call release) — after TTL, B preempts the
     * stale lease. This is the passive fail-safe that bounds the impact of
     * a crashed holder. The {@code LOCK_OWNER} field value transition is
     * asserted across the preemption.
     */
    @Test
    void dualInstanceStaleLockPreemptionAfterTtl() throws InterruptedException, SQLException {
        DbSessionTakeoverLock instanceA = new DbSessionTakeoverLock(dataSource);
        DbSessionTakeoverLock instanceB = new DbSessionTakeoverLock(dataSource);

        // A acquires with a short lease. The lease must be long enough to
        // survive the assertions below even under parallel reactor load
        // (a 100ms lease can expire between tryAcquire and isHeld when the
        // machine is busy, making the pre-TTL held-state assertion flaky).
        assertTrue(instanceA.tryAcquire("s1", "engine-A", 2_000L));
        assertEquals("engine-A", readLockOwner("s1"),
                "DB row ownership must be engine-A before TTL");

        // A "crashes" — no release call. The lease remains in the DB.
        assertTrue(instanceA.isHeld("s1"));

        // Wait past the lease TTL (sleep must exceed the lease duration).
        Thread.sleep(2_100L);

        // B can now preempt the stale (expired) lease.
        assertFalse(instanceB.isHeld("s1"),
                "After TTL, isHeld reflects the expired lease");
        assertEquals("engine-A", readLockOwner("s1"),
                "expired lease row persists (owner still engine-A) until preempted");
        assertTrue(instanceB.tryAcquire("s1", "engine-B", 60_000L),
                "Instance B preempts instance A's stale lease after TTL");
        assertEquals("engine-B", readLockOwner("s1"),
                "preemption must transfer the DB row ownership to engine-B");

        // The DB row's owner is now B; A's conditional release returns
        // false (lease is no longer A's).
        assertFalse(instanceA.release("s1", "engine-A"),
                "Stale-holder A can no longer release (lease is now B's)");
        assertEquals("engine-B", readLockOwner("s1"),
                "A's failed release must leave ownership with engine-B");
        assertTrue(instanceB.release("s1", "engine-B"),
                "Current holder B releases successfully");
        assertNull(readLockOwner("s1"),
                "B's release must delete the lease row");
    }
}
