package io.nop.ai.agent.runtime.coordination;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 242 Phase 2 focused tests for {@link NoOpDaemonCoordinator} — the
 * shipped default that preserves single-instance daemon behaviour (zero
 * regression) when no cross-process coordinator is wired.
 *
 * <p>Coverage map:
 * <ul>
 *   <li>{@link #tryAcquireScanLeaseAlwaysTrue} — never blocks a scan</li>
 *   <li>{@link #releaseScanLeaseAlwaysFalse} — no lease was ever recorded</li>
 *   <li>{@link #isScanLeaseActiveAlwaysFalse} — no lease state exists</li>
 *   <li>{@link #noOpFactoryReturnsSingleton} — singleton identity</li>
 * </ul>
 */
public class TestNoOpDaemonCoordinator {

    @Test
    void tryAcquireScanLeaseAlwaysTrue() {
        NoOpDaemonCoordinator coord = NoOpDaemonCoordinator.noOp();
        assertTrue(coord.tryAcquireScanLease("team-1", "daemon-A", 60_000L),
                "NoOp acquire must always return true (single-instance behaviour unchanged)");
        assertTrue(coord.tryAcquireScanLease("team-1", "daemon-A", 1L),
                "NoOp acquire is leaseMs-agnostic (no validation needed — no DB hit)");
        assertTrue(coord.tryAcquireScanLease("team-1", "daemon-B", 60_000L),
                "NoOp acquire ignores ownerId contention — every caller scans");
        assertTrue(coord.tryAcquireScanLease("another-team", "daemon-A", 60_000L),
                "NoOp acquire is teamId-agnostic — every team scanned every cycle");
    }

    @Test
    void releaseScanLeaseAlwaysFalse() {
        NoOpDaemonCoordinator coord = NoOpDaemonCoordinator.noOp();
        assertFalse(coord.releaseScanLease("team-1", "daemon-A"),
                "NoOp release returns false — no lease was ever recorded (honest report, "
                        + "not the takeover-lock-style 'consider it released' acknowledgement)");
        assertFalse(coord.releaseScanLease("never-acquired", "daemon-X"),
                "NoOp release of a non-existent lease also returns false (consistent)");
    }

    @Test
    void isScanLeaseActiveAlwaysFalse() {
        NoOpDaemonCoordinator coord = NoOpDaemonCoordinator.noOp();
        assertFalse(coord.isScanLeaseActive("team-1"),
                "NoOp reports no active lease — no team is ever reported as lease-held");
        // Even after a "successful" acquire, isScanLeaseActive stays false
        // (no DB row was written — the acquire was an in-memory no-op).
        coord.tryAcquireScanLease("team-1", "daemon-A", 60_000L);
        assertFalse(coord.isScanLeaseActive("team-1"),
                "isScanLeaseActive still false after NoOp acquire (no state recorded)");
    }

    @Test
    void noOpFactoryReturnsSingleton() {
        assertSame(NoOpDaemonCoordinator.noOp(), NoOpDaemonCoordinator.noOp(),
                "noOp() factory returns the same singleton instance (stateless, safe to share)");
    }
}
