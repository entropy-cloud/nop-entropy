package io.nop.ai.agent.runtime.lock;

import io.nop.ai.agent.engine.NopAiAgentException;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 221 Phase 2 focused tests for {@link DbSessionTakeoverLock}: each
 * CAS / lease / conditional-release semantic is verified against a real
 * H2 DB (not a mock), satisfying Minimum Rules #22 (Anti-Hollow) and #23
 * (Wiring Verification).
 *
 * <p>Coverage map:
 * <ul>
 *   <li>{@link #tryAcquireSucceedsOnFreshSession} — INSERT-path acquire</li>
 *   <li>{@link #tryAcquireBlockedByActiveLock} — UPDATE-path fail-fast</li>
 *   <li>{@link #tryAcquireSameOwnerIsIdempotentRenew} — same-owner renew</li>
 *   <li>{@link #tryAcquirePreemptsStaleLockAfterTtl} — passive TTL expiry</li>
 *   <li>{@link #releaseOnlyReleasesOwnLock} — conditional release</li>
 *   <li>{@link #releaseReturnsFalseWhenNotHeld} — release idempotency</li>
 *   <li>{@link #isHeldReflectsActiveVsExpiredLease} — active check</li>
 *   <li>{@link #tryRenewExtendsLeaseAndPreventsPreemption} — manual renew</li>
 *   <li>{@link #tryRenewReturnsFalseForOtherOwner} — renew conditional</li>
 *   <li>{@link #argumentsValidated} — input contract</li>
 * </ul>
 */
public class TestDbSessionTakeoverLock {

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
        dbUrl = "jdbc:h2:mem:test-takeover-lock-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
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
    void tryAcquireSucceedsOnFreshSession() {
        DbSessionTakeoverLock lock = new DbSessionTakeoverLock(dataSource);
        assertTrue(lock.tryAcquire("s1", "owner-A", 60_000L),
                "First acquire on a fresh session must succeed (INSERT path)");
    }

    // ========================================================================
    // tryAcquire — UPDATE-path fail-fast (active lock, different owner)
    // ========================================================================

    @Test
    void tryAcquireBlockedByActiveLock() {
        DbSessionTakeoverLock lock = new DbSessionTakeoverLock(dataSource);
        assertTrue(lock.tryAcquire("s1", "owner-A", 60_000L));
        assertFalse(lock.tryAcquire("s1", "owner-B", 60_000L),
                "A different owner cannot preempt an active lease (UPDATE affects 0 rows)");
    }

    // ========================================================================
    // tryAcquire — same owner re-acquire (idempotent renew)
    // ========================================================================

    @Test
    void tryAcquireSameOwnerIsIdempotentRenew() {
        DbSessionTakeoverLock lock = new DbSessionTakeoverLock(dataSource);
        assertTrue(lock.tryAcquire("s1", "owner-A", 60_000L));
        assertTrue(lock.tryAcquire("s1", "owner-A", 60_000L),
                "Same-owner re-acquire is idempotent (UPDATE LOCK_OWNER=?)");
        assertTrue(lock.isHeld("s1"),
                "Lease still active after same-owner re-acquire");
    }

    // ========================================================================
    // tryAcquire — stale lock preemption (lease/TTL passive expiry)
    // ========================================================================

    @Test
    void tryAcquirePreemptsStaleLockAfterTtl() throws InterruptedException {
        DbSessionTakeoverLock lock = new DbSessionTakeoverLock(dataSource);
        assertTrue(lock.tryAcquire("s1", "owner-A", 100L));
        // Wait until the lease expires (> leaseMs). Pad by 100ms to avoid
        // wall-clock granularity at the boundary.
        Thread.sleep(200L);
        assertFalse(lock.isHeld("s1"),
                "After TTL elapses, isHeld must return false (lease expired)");
        assertTrue(lock.tryAcquire("s1", "owner-B", 60_000L),
                "A different owner can preempt an expired (stale) lease");
    }

    // ========================================================================
    // release — conditional (own lock only)
    // ========================================================================

    @Test
    void releaseOnlyReleasesOwnLock() {
        DbSessionTakeoverLock lock = new DbSessionTakeoverLock(dataSource);
        assertTrue(lock.tryAcquire("s1", "owner-A", 60_000L));
        assertFalse(lock.release("s1", "owner-B"),
                "release must not free another owner's lease (conditional DELETE WHERE LOCK_OWNER=?)");
        assertTrue(lock.isHeld("s1"),
                "Lease is still active after a different owner attempted release");
        assertTrue(lock.release("s1", "owner-A"),
                "release succeeds when the lock owner matches");
        assertFalse(lock.isHeld("s1"),
                "After own release, the lease is gone");
    }

    @Test
    void releaseReturnsFalseWhenNotHeld() {
        DbSessionTakeoverLock lock = new DbSessionTakeoverLock(dataSource);
        assertFalse(lock.release("never-acquired", "owner-A"),
                "release of a non-existent lease returns false (no row matched)");
    }

    // ========================================================================
    // isHeld — active vs expired
    // ========================================================================

    @Test
    void isHeldReflectsActiveVsExpiredLease() throws InterruptedException {
        DbSessionTakeoverLock lock = new DbSessionTakeoverLock(dataSource);
        assertFalse(lock.isHeld("s1"), "No lease yet → isHeld=false");
        assertTrue(lock.tryAcquire("s1", "owner-A", 100L));
        assertTrue(lock.isHeld("s1"), "Active lease → isHeld=true");
        Thread.sleep(200L);
        assertFalse(lock.isHeld("s1"), "Expired lease → isHeld=false");
    }

    // ========================================================================
    // tryRenew — manual lease extension
    // ========================================================================

    @Test
    void tryRenewExtendsLeaseAndPreventsPreemption() throws InterruptedException {
        DbSessionTakeoverLock lock = new DbSessionTakeoverLock(dataSource);
        assertTrue(lock.tryAcquire("s1", "owner-A", 100L));
        // Renew before the original lease expires.
        assertTrue(lock.tryRenew("s1", "owner-A", 60_000L),
                "Same-owner renew must succeed (conditional UPDATE)");
        // Wait past the ORIGINAL leaseMs — the renewed lease should still
        // be active.
        Thread.sleep(200L);
        assertTrue(lock.isHeld("s1"),
                "Renewed lease survives past the original leaseMs");
        assertFalse(lock.tryAcquire("s1", "owner-B", 60_000L),
                "A different owner still cannot preempt the renewed lease");
    }

    @Test
    void tryRenewReturnsFalseForOtherOwner() {
        DbSessionTakeoverLock lock = new DbSessionTakeoverLock(dataSource);
        assertTrue(lock.tryAcquire("s1", "owner-A", 60_000L));
        assertFalse(lock.tryRenew("s1", "owner-B", 60_000L),
                "tryRenew must fail for a different owner (conditional WHERE LOCK_OWNER=?)");
    }

    // ========================================================================
    // Argument validation (fail-fast on misuse, no silent no-op)
    // ========================================================================

    @Test
    void argumentsValidated() {
        DbSessionTakeoverLock lock = new DbSessionTakeoverLock(dataSource);
        assertThrows(NopAiAgentException.class, () -> lock.tryAcquire(null, "o", 1L));
        assertThrows(NopAiAgentException.class, () -> lock.tryAcquire("s", null, 1L));
        assertThrows(NopAiAgentException.class, () -> lock.tryAcquire("s", "o", 0L));
        assertThrows(NopAiAgentException.class, () -> lock.tryAcquire("s", "o", -1L));
        assertThrows(NopAiAgentException.class, () -> lock.release(null, "o"));
        assertThrows(NopAiAgentException.class, () -> lock.release("s", null));
        assertThrows(NopAiAgentException.class, () -> lock.isHeld(null));
        assertThrows(NopAiAgentException.class, () -> lock.tryRenew("s", null, 1L));
        assertThrows(NopAiAgentException.class, () -> lock.tryRenew("s", "o", 0L));
    }
}
