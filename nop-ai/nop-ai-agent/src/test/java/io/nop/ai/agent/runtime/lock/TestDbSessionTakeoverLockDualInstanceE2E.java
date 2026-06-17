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

import static org.junit.jupiter.api.Assertions.assertFalse;
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
     * End-to-end scenario: A acquires → B blocked → A releases → B acquires.
     * Two independent lock instances share the same DB (no shared memory
     * state) — only the DB row carries the lease.
     */
    @Test
    void dualInstanceAcquireBlockReleaseAcquire() {
        // Two independent lock instances pointing at the same DB — each
        // represents a distinct JVM process.
        DbSessionTakeoverLock instanceA = new DbSessionTakeoverLock(dataSource);
        DbSessionTakeoverLock instanceB = new DbSessionTakeoverLock(dataSource);

        // A acquires first.
        assertTrue(instanceA.tryAcquire("s1", "engine-A", 60_000L),
                "Instance A acquires first → success");

        // B cannot acquire while A holds the active lease.
        assertFalse(instanceB.tryAcquire("s1", "engine-B", 60_000L),
                "Instance B cannot preempt instance A's active lease");

        // isHeld reflects the cross-process reality on both instances.
        assertTrue(instanceB.isHeld("s1"),
                "Instance B observes the lease held by A (shared DB)");
        assertTrue(instanceA.isHeld("s1"),
                "Instance A also observes its own lease");

        // A releases — clean handoff.
        assertTrue(instanceA.release("s1", "engine-A"),
                "Instance A releases its own lease");

        // B can now acquire (clean handoff after release).
        assertTrue(instanceB.tryAcquire("s1", "engine-B", 60_000L),
                "After release, instance B can acquire");

        // Cross-process conditional release: A cannot release B's lease.
        assertFalse(instanceA.release("s1", "engine-A"),
                "Instance A can no longer release (lease is now B's)");
        assertTrue(instanceB.release("s1", "engine-B"),
                "Instance B releases its own lease");
    }

    /**
     * End-to-end stale-lock preemption scenario: A acquires a short lease,
     * then "crashes" (does not call release) — after TTL, B preempts the
     * stale lease. This is the passive fail-safe that bounds the impact of
     * a crashed holder.
     */
    @Test
    void dualInstanceStaleLockPreemptionAfterTtl() throws InterruptedException {
        DbSessionTakeoverLock instanceA = new DbSessionTakeoverLock(dataSource);
        DbSessionTakeoverLock instanceB = new DbSessionTakeoverLock(dataSource);

        // A acquires with a short lease.
        assertTrue(instanceA.tryAcquire("s1", "engine-A", 100L));

        // A "crashes" — no release call. The lease remains in the DB.
        assertTrue(instanceA.isHeld("s1"));

        // Wait past the lease TTL.
        Thread.sleep(200L);

        // B can now preempt the stale (expired) lease.
        assertFalse(instanceB.isHeld("s1"),
                "After TTL, isHeld reflects the expired lease");
        assertTrue(instanceB.tryAcquire("s1", "engine-B", 60_000L),
                "Instance B preempts instance A's stale lease after TTL");

        // The DB row's owner is now B; A's conditional release returns
        // false (lease is no longer A's).
        assertFalse(instanceA.release("s1", "engine-A"),
                "Stale-holder A can no longer release (lease is now B's)");
        assertTrue(instanceB.release("s1", "engine-B"),
                "Current holder B releases successfully");
    }
}
