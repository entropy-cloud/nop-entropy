package io.nop.ai.agent.runtime.recovery;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Immutable snapshot of a single {@link IRecoveryManager#scanOnce}
 * invocation, for observability and testing. Returned by every scan
 * (including {@link NoOpRecoveryManager}, which returns an all-zero
 * instance — an explicit "no recovery scanning" semantic, not a silent
 * no-op; Minimum Rules #24).
 *
 * <p><b>Field semantics</b>:
 * <ul>
 *   <li>{@code staleLocksCleaned} — number of expired takeover-lock rows
 *       ({@code LOCK_EXPIRES_AT <= now}) deleted from
 *       {@code ai_agent_session_lock} during this scan. Idempotent cleanup:
 *       DELETE of already-absent rows is a no-op, so concurrent scans from
 *       multiple instances are safe.</li>
 *   <li>{@code orphanSessionsDetected} — number of sessions with
 *       {@code STATUS IN ('running','pending')} that have <em>no</em>
 *       active (non-expired) takeover lock. Each is LOG.warn'd by
 *       {@code ScheduledRecoveryManager} to give downstream recovery
 *       strategy successors an observation basis. This version does not
 *       auto-recover orphans (resume/retry/abort is an explicit
 *       successor).</li>
 *   <li>{@code orphanSessionIds} — the session IDs backing
 *       {@code orphanSessionsDetected}; empty list when none. Bounded by
 *       the scan result set.</li>
 *   <li>{@code scanDurationMs} — wall-clock duration of the scan (both the
 *       stale-lock cleanup and orphan-detection SQL), in milliseconds.</li>
 *   <li>{@code scannedAt} — epoch millisecond timestamp captured at scan
 *       start.</li>
 *   <li>{@code recoveryActions} — the per-orphan-session {@link RecoveryOutcome}
 *       list produced by the configured {@link IOrphanRecoveryHandler} during
 *       this scan (plan 226). One outcome per detected orphan session, in
 *       detection order. Empty list when no orphans were detected or when the
 *       shipped {@link NoOpRecoveryManager} default returns an all-zero result.
 *       Every orphan has an outcome (Minimum Rules #24 — non-silent).</li>
 *   <li>{@code timeoutActions} — the per-timed-out-session {@link TimeoutOutcome}
 *       list produced by the configured {@link ISessionTimeoutHandler} during
 *       this scan (plan 229). One outcome per detected timed-out session
 *       (status=running/pending + activity timestamp older than the configured
 *       {@code timeoutSeconds}), in detection order. Empty list when no
 *       timed-out sessions were detected or when the shipped
 *       {@link NoOpRecoveryManager} default returns an all-zero result. Every
 *       timed-out session has an outcome (Minimum Rules #24 — non-silent).</li>
 *   <li>{@code teamTaskRecoveryActions} — the per-stuck-task
 *       {@link TeamTaskRecoveryOutcome} list produced by the configured
 *       {@link ITeamTaskRecoveryHandler} during this scan (plan 240). One
 *       outcome per detected stuck CLAIMED team task (status=CLAIMED +
 *       UPDATED_AT older than the handler's configured threshold), in
 *       detection order. Empty list when no stuck tasks were detected or
 *       when the shipped {@link NoOpTeamTaskRecoveryHandler} default returns
 *       an empty list (SKIP semantic — zero DB access). Every acted-upon
 *       task has an outcome (Minimum Rules #24 — non-silent).</li>
 * </ul>
 *
 * <p>See plan 222 (L4-8-P4-RecoveryDaemon), plan 226
 * (L4-8-P4-RecoveryStrategy), plan 229 (L4-8-P4-TimeoutAbort), plan 240
 * (L4-team-task-reclaim-and-timeout-abandon), and design
 * {@code nop-ai-agent-actor-runtime-vision.md} §6.3 / §10 Phase 4.
 */
public final class RecoveryScanResult {

    private final int staleLocksCleaned;
    private final int orphanSessionsDetected;
    private final List<String> orphanSessionIds;
    private final long scanDurationMs;
    private final long scannedAt;
    private final List<RecoveryOutcome> recoveryActions;
    private final List<TimeoutOutcome> timeoutActions;
    private final List<TeamTaskRecoveryOutcome> teamTaskRecoveryActions;

    public RecoveryScanResult(int staleLocksCleaned, int orphanSessionsDetected,
                              List<String> orphanSessionIds, long scanDurationMs, long scannedAt,
                              List<RecoveryOutcome> recoveryActions,
                              List<TimeoutOutcome> timeoutActions,
                              List<TeamTaskRecoveryOutcome> teamTaskRecoveryActions) {
        this.staleLocksCleaned = staleLocksCleaned;
        this.orphanSessionsDetected = orphanSessionsDetected;
        this.orphanSessionIds = Collections.unmodifiableList(
                Objects.requireNonNull(orphanSessionIds, "orphanSessionIds must not be null"));
        this.scanDurationMs = scanDurationMs;
        this.scannedAt = scannedAt;
        this.recoveryActions = Collections.unmodifiableList(
                Objects.requireNonNull(recoveryActions, "recoveryActions must not be null"));
        this.timeoutActions = Collections.unmodifiableList(
                Objects.requireNonNull(timeoutActions, "timeoutActions must not be null"));
        this.teamTaskRecoveryActions = Collections.unmodifiableList(
                Objects.requireNonNull(teamTaskRecoveryActions, "teamTaskRecoveryActions must not be null"));
    }

    /**
     * All-zero result for the {@link NoOpRecoveryManager} default — an
     * explicit "no recovery scanning" semantic (empty id list, zero counts,
     * zero duration, zero timestamp, empty recovery actions, empty timeout
     * actions, empty team-task recovery actions), not a silent no-op.
     */
    public static RecoveryScanResult empty() {
        return new RecoveryScanResult(0, 0, Collections.emptyList(), 0L, 0L,
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
    }

    public int getStaleLocksCleaned() {
        return staleLocksCleaned;
    }

    public int getOrphanSessionsDetected() {
        return orphanSessionsDetected;
    }

    public List<String> getOrphanSessionIds() {
        return orphanSessionIds;
    }

    public long getScanDurationMs() {
        return scanDurationMs;
    }

    public long getScannedAt() {
        return scannedAt;
    }

    /**
     * The per-orphan-session recovery outcomes produced by the configured
     * {@link IOrphanRecoveryHandler} during this scan (plan 226). One
     * outcome per detected orphan session, in detection order. Empty when
     * no orphans were detected.
     *
     * @return an unmodifiable, non-null list of {@link RecoveryOutcome}
     */
    public List<RecoveryOutcome> getRecoveryActions() {
        return recoveryActions;
    }

    /**
     * The per-timed-out-session outcomes produced by the configured
     * {@link ISessionTimeoutHandler} during this scan (plan 229). One
     * outcome per detected timed-out session (status=running/pending +
     * activity timestamp older than the configured {@code timeoutSeconds}),
     * in detection order. Empty when no timed-out sessions were detected.
     *
     * @return an unmodifiable, non-null list of {@link TimeoutOutcome}
     */
    public List<TimeoutOutcome> getTimeoutActions() {
        return timeoutActions;
    }

    /**
     * The per-stuck-task outcomes produced by the configured
     * {@link ITeamTaskRecoveryHandler} during this scan (plan 240). One
     * outcome per detected stuck CLAIMED team task (status=CLAIMED +
     * UPDATED_AT older than the handler's configured threshold), in
     * detection order. Empty when no stuck tasks were detected, or when the
     * shipped {@link NoOpTeamTaskRecoveryHandler} default is wired (SKIP
     * semantic — zero DB access).
     *
     * @return an unmodifiable, non-null list of
     *         {@link TeamTaskRecoveryOutcome}
     */
    public List<TeamTaskRecoveryOutcome> getTeamTaskRecoveryActions() {
        return teamTaskRecoveryActions;
    }

    @Override
    public String toString() {
        return "RecoveryScanResult{staleLocksCleaned=" + staleLocksCleaned
                + ", orphanSessionsDetected=" + orphanSessionsDetected
                + ", orphanSessionIds=" + orphanSessionIds
                + ", scanDurationMs=" + scanDurationMs
                + ", scannedAt=" + scannedAt
                + ", recoveryActions=" + recoveryActions
                + ", timeoutActions=" + timeoutActions
                + ", teamTaskRecoveryActions=" + teamTaskRecoveryActions + '}';
    }
}
