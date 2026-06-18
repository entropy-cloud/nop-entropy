package io.nop.ai.agent.runtime.coordination;

import io.nop.ai.agent.engine.NopAiAgentException;
import io.nop.ai.agent.security.ThreadLocalTenantResolver;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.dao.jdbc.datasource.SimpleDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 242 Phase 2 focused tests for {@link DbDaemonCoordinator}: each
 * CAS / lease / conditional-release semantic is verified against a real
 * H2 DB (not a mock), satisfying Minimum Rules #22 (Anti-Hollow) and #23
 * (Wiring Verification).
 *
 * <p>Coverage map:
 * <ul>
 *   <li>{@link #tryAcquireSucceedsOnFreshTeam} — INSERT-path acquire</li>
 *   <li>{@link #tryAcquireBlockedByActiveLease} — UPDATE-path coordination signal</li>
 *   <li>{@link #tryAcquireSameOwnerIsIdempotentRenew} — same-owner renew</li>
 *   <li>{@link #tryAcquirePreemptsStaleLeaseAfterTtl} — passive TTL expiry</li>
 *   <li>{@link #releaseOnlyReleasesOwnLease} — conditional release</li>
 *   <li>{@link #releaseReturnsFalseWhenNotHeld} — release idempotency</li>
 *   <li>{@link #isScanLeaseActiveReflectsActiveVsExpiredLease} — active check</li>
 *   <li>{@link #tenantGuardIsolatesLeases} — multi-tenant WHERE isolation</li>
 *   <li>{@link #concurrentTryAcquireOnlyOneWinner} — concurrent CAS safety</li>
 *   <li>{@link #argumentsValidated} — input contract</li>
 * </ul>
 */
public class TestDbDaemonCoordinator {

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
        dbUrl = "jdbc:h2:mem:test-daemon-coord-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
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

    // ========================================================================
    // tryAcquire — INSERT path
    // ========================================================================

    @Test
    void tryAcquireSucceedsOnFreshTeam() {
        DbDaemonCoordinator coord = new DbDaemonCoordinator(dataSource);
        assertTrue(coord.tryAcquireScanLease("team-1", "daemon-A", 60_000L),
                "First acquire on a fresh team must succeed (INSERT path)");
    }

    // ========================================================================
    // tryAcquire — UPDATE-path coordination signal (active lease, different owner)
    // ========================================================================

    @Test
    void tryAcquireBlockedByActiveLease() {
        DbDaemonCoordinator coord = new DbDaemonCoordinator(dataSource);
        assertTrue(coord.tryAcquireScanLease("team-1", "daemon-A", 60_000L));
        assertFalse(coord.tryAcquireScanLease("team-1", "daemon-B", 60_000L),
                "A different owner cannot preempt an active lease (UPDATE affects 0 rows)");
    }

    // ========================================================================
    // tryAcquire — same owner re-acquire (idempotent renew)
    // ========================================================================

    @Test
    void tryAcquireSameOwnerIsIdempotentRenew() {
        DbDaemonCoordinator coord = new DbDaemonCoordinator(dataSource);
        assertTrue(coord.tryAcquireScanLease("team-1", "daemon-A", 60_000L));
        assertTrue(coord.tryAcquireScanLease("team-1", "daemon-A", 60_000L),
                "Same-owner re-acquire is idempotent (UPDATE OWNER_ID=?)");
        assertTrue(coord.isScanLeaseActive("team-1"),
                "Lease still active after same-owner re-acquire");
    }

    // ========================================================================
    // tryAcquire — stale lease preemption (lease/TTL passive expiry)
    // ========================================================================

    @Test
    void tryAcquirePreemptsStaleLeaseAfterTtl() throws InterruptedException {
        DbDaemonCoordinator coord = new DbDaemonCoordinator(dataSource);
        assertTrue(coord.tryAcquireScanLease("team-1", "daemon-A", 100L));
        // Wait until the lease expires (> leaseMs). Pad by 100ms to avoid
        // wall-clock granularity at the boundary.
        Thread.sleep(200L);
        assertFalse(coord.isScanLeaseActive("team-1"),
                "After TTL elapses, isScanLeaseActive must return false (lease expired)");
        assertTrue(coord.tryAcquireScanLease("team-1", "daemon-B", 60_000L),
                "A different owner can preempt an expired (stale) lease");
    }

    // ========================================================================
    // release — conditional (own lease only)
    // ========================================================================

    @Test
    void releaseOnlyReleasesOwnLease() {
        DbDaemonCoordinator coord = new DbDaemonCoordinator(dataSource);
        assertTrue(coord.tryAcquireScanLease("team-1", "daemon-A", 60_000L));
        assertFalse(coord.releaseScanLease("team-1", "daemon-B"),
                "release must not free another owner's lease (conditional DELETE WHERE OWNER_ID=?)");
        assertTrue(coord.isScanLeaseActive("team-1"),
                "Lease is still active after a different owner attempted release");
        assertTrue(coord.releaseScanLease("team-1", "daemon-A"),
                "release succeeds when the lease owner matches");
        assertFalse(coord.isScanLeaseActive("team-1"),
                "After own release, the lease is gone");
    }

    @Test
    void releaseReturnsFalseWhenNotHeld() {
        DbDaemonCoordinator coord = new DbDaemonCoordinator(dataSource);
        assertFalse(coord.releaseScanLease("never-acquired", "daemon-A"),
                "release of a non-existent lease returns false (no row matched)");
    }

    // ========================================================================
    // isScanLeaseActive — active vs expired
    // ========================================================================

    @Test
    void isScanLeaseActiveReflectsActiveVsExpiredLease() throws InterruptedException {
        DbDaemonCoordinator coord = new DbDaemonCoordinator(dataSource);
        assertFalse(coord.isScanLeaseActive("team-1"), "No lease yet → isScanLeaseActive=false");
        assertTrue(coord.tryAcquireScanLease("team-1", "daemon-A", 100L));
        assertTrue(coord.isScanLeaseActive("team-1"), "Active lease → isScanLeaseActive=true");
        Thread.sleep(200L);
        assertFalse(coord.isScanLeaseActive("team-1"), "Expired lease → isScanLeaseActive=false");
    }

    // ========================================================================
    // Multi-tenant guard — non-null tenant makes a lease invisible /
    // untouchable to other tenants (WHERE TENANT_ID isolation).
    //
    // Note: with PK = TEAM_ID (one lease row per team, globally unique teamId
    // in practice), the tenant guard provides read/visibility isolation:
    // tenant-B's isScanLeaseActive returns false for tenant-A's lease, and
    // tenant-B's releaseScanLease / conditional UPDATE acquire cannot match
    // tenant-A's row (the tenant WHERE filters it out).
    // ========================================================================

    @Test
    void tenantGuardIsolatesLeases() {
        DbDaemonCoordinator coord = new DbDaemonCoordinator(dataSource, ThreadLocalTenantResolver.INSTANCE);

        // tenant-A acquires team-1 (INSERT writes TENANT_ID='tenant-A').
        ThreadLocalTenantResolver.set("tenant-A");
        try {
            assertTrue(coord.tryAcquireScanLease("team-1", "daemon-A", 60_000L));
            assertTrue(coord.isScanLeaseActive("team-1"),
                    "tenant-A sees its own active lease");
        } finally {
            ThreadLocalTenantResolver.clear();
        }

        // tenant-B cannot observe or touch tenant-A's lease.
        ThreadLocalTenantResolver.set("tenant-B");
        try {
            assertFalse(coord.isScanLeaseActive("team-1"),
                    "tenant-B does not observe tenant-A's lease (TENANT_ID WHERE isolation)");

            // tenant-B's conditional UPDATE acquire cannot match tenant-A's
            // row (the tenant WHERE filters it out → 0 rows → false).
            assertFalse(coord.tryAcquireScanLease("team-1", "daemon-B", 60_000L),
                    "tenant-B cannot acquire tenant-A's team lease (tenant WHERE on UPDATE "
                            + "filters out the cross-tenant row)");

            // tenant-B's release also cannot touch tenant-A's row.
            assertFalse(coord.releaseScanLease("team-1", "daemon-A"),
                    "tenant-B cannot release tenant-A's lease (tenant WHERE on DELETE "
                            + "filters out the cross-tenant row)");
        } finally {
            ThreadLocalTenantResolver.clear();
        }

        // Back to tenant-A: lease is untouched by tenant-B's attempts.
        ThreadLocalTenantResolver.set("tenant-A");
        try {
            assertTrue(coord.isScanLeaseActive("team-1"),
                    "tenant-A's lease still active (tenant-B's acquire/release attempts "
                            + "did not clobber it — WHERE TENANT_ID isolation held)");
            assertTrue(coord.releaseScanLease("team-1", "daemon-A"),
                    "tenant-A releases its own lease successfully");
        } finally {
            ThreadLocalTenantResolver.clear();
        }
    }

    // ========================================================================
    // Concurrent tryAcquire — only one of two threads wins (CAS safety)
    // ========================================================================

    @Test
    void concurrentTryAcquireOnlyOneWinner() throws InterruptedException {
        DbDaemonCoordinator coord = new DbDaemonCoordinator(dataSource);
        int threads = 2;
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger(0);

        Thread[] workers = new Thread[threads];
        for (int i = 0; i < threads; i++) {
            final String ownerId = "daemon-" + i;
            workers[i] = new Thread(() -> {
                ready.countDown();
                try {
                    start.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                if (coord.tryAcquireScanLease("team-race", ownerId, 60_000L)) {
                    successes.incrementAndGet();
                }
            });
            workers[i].start();
        }
        ready.await();
        // Give both threads the green light simultaneously to maximise CAS
        // contention at the INSERT/UPDATE boundary.
        start.countDown();
        for (Thread t : workers) {
            t.join();
        }

        assertEquals(1, successes.get(),
                "Exactly one of the two concurrent acquires must win (PK uniqueness + conditional UPDATE CAS)");
        assertTrue(coord.isScanLeaseActive("team-race"),
                "After the race, team-race has an active lease held by the winner");
    }

    // ========================================================================
    // Argument validation (fail-fast on misuse, no silent no-op)
    // ========================================================================

    @Test
    void argumentsValidated() {
        DbDaemonCoordinator coord = new DbDaemonCoordinator(dataSource);
        assertThrows(NopAiAgentException.class, () -> coord.tryAcquireScanLease(null, "o", 1L));
        assertThrows(NopAiAgentException.class, () -> coord.tryAcquireScanLease("t", null, 1L));
        assertThrows(NopAiAgentException.class, () -> coord.tryAcquireScanLease("t", "o", 0L));
        assertThrows(NopAiAgentException.class, () -> coord.tryAcquireScanLease("t", "o", -1L));
        assertThrows(NopAiAgentException.class, () -> coord.releaseScanLease(null, "o"));
        assertThrows(NopAiAgentException.class, () -> coord.releaseScanLease("t", null));
        assertThrows(NopAiAgentException.class, () -> coord.isScanLeaseActive(null));
    }
}
