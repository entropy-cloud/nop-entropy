package io.nop.ai.agent.runtime.coordination;

/**
 * Shipped no-op default for {@link IDaemonCoordinator}.
 *
 * <p>Used by {@code TeamTaskSchedulerDaemon} out-of-the-box: single-process
 * deployments keep relying on the per-task correctness floor
 * ({@code claimTask} DB CAS, plan 227 / 240), so daemon behaviour is
 * unchanged — every team is scanned every cycle. Integrators opt into
 * cross-process scan-load coordination by wiring a functional coordinator
 * (e.g. {@link DbDaemonCoordinator}) via
 * {@code TeamTaskSchedulerDaemon.setDaemonCoordinator}.
 *
 * <p>This is an explicit "no cross-process coordination needed" semantic,
 * not a silent no-op (Minimum Rules #24):
 * <ul>
 *   <li>{@link #tryAcquireScanLease} unconditionally returns {@code true} —
 *       the daemon scans the team exactly as it would without a
 *       coordinator (full scan, zero behaviour change).</li>
 *   <li>{@link #releaseScanLease} unconditionally returns {@code false} —
 *       no lease was ever recorded (there is nothing to release). This
 *       differs intentionally from
 *       {@link io.nop.ai.agent.runtime.lock.NoOpSessionTakeoverLock#release}
 *       (which returns {@code true}): a takeover lock's no-op release is
 *       an acknowledgement ("you held it, consider it released"), whereas
 *       a scan lease's no-op release honestly reports "no lease existed".
 *       The daemon treats a {@code false} return as a LOG.warn signal
 *       (defensive) and continues — scan results are unaffected.</li>
 *   <li>{@link #isScanLeaseActive} unconditionally returns {@code false} —
 *       no lease is ever recorded, so no team is ever reported as
 *       lease-held.</li>
 * </ul>
 *
 * <p>See plan 242 Phase 1.
 */
public final class NoOpDaemonCoordinator implements IDaemonCoordinator {

    private static final NoOpDaemonCoordinator INSTANCE = new NoOpDaemonCoordinator();

    private NoOpDaemonCoordinator() {
    }

    public static NoOpDaemonCoordinator noOp() {
        return INSTANCE;
    }

    @Override
    public boolean tryAcquireScanLease(String teamId, String ownerId, long leaseMs) {
        return true;
    }

    @Override
    public boolean releaseScanLease(String teamId, String ownerId) {
        return false;
    }

    @Override
    public boolean isScanLeaseActive(String teamId) {
        return false;
    }
}
