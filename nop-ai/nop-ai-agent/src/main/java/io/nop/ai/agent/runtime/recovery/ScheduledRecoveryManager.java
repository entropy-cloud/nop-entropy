package io.nop.ai.agent.runtime.recovery;

import io.nop.ai.agent.engine.NopAiAgentException;
import io.nop.ai.agent.runtime.lock.AiAgentSessionLockTable;
import io.nop.ai.agent.session.AiAgentSessionTable;
import io.nop.commons.concurrent.executor.IScheduledExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Functional implementation of {@link IRecoveryManager} — a continuously
 * running periodic sweep daemon that cleans up stale takeover locks and
 * detects orphan sessions in multi-instance unattended deployments
 * (plan 222 / L4-8-P4-RecoveryDaemon).
 *
 * <p><b>Scheduling</b>: {@link #start} registers a fixed-delay periodic
 * task on the configured {@link IScheduledExecutor} (default 60s); {@link #stop}
 * cancels it. The {@code IScheduledExecutor} abstraction (nop-commons) is
 * used instead of the heavier nop-job {@code IJobScheduler} — for a simple
 * "run scanOnce every 60s" periodic task, {@code IScheduledExecutor} is
 * sufficient and requires no new Maven dependency (already transitively
 * available). nop-job integration (DB-backed job persistence / cluster
 * coordination / cron expression) is an explicit successor. Both
 * {@code start}/{@code stop} are idempotent.
 *
 * <p><b>scanOnce operations</b> (design 裁定 1):
 * <ol>
 *   <li><b>Stale lock cleanup</b>: {@code DELETE FROM ai_agent_session_lock
 *       WHERE LOCK_EXPIRES_AT <= now}. Idempotent — DELETE of absent rows
 *       is a no-op, so concurrent scans from multiple instances are safe.
 *       The deleted row count is recorded as {@code staleLocksCleaned}.</li>
 *   <li><b>Timeout detection (plan 229)</b>: {@code SELECT SESSION_ID FROM
 *       ai_agent_session WHERE STATUS IN ('running','pending') AND
 *       UPDATED_AT < ?} (parameter = {@code now - timeoutSeconds*1000},
 *       design 裁定 2 — {@code UPDATED_AT} as the activity-timestamp
 *       proxy). Each timed-out session is passed to the configured
 *       {@link ISessionTimeoutHandler} (shipped default
 *       {@link NoOpSessionTimeoutHandler} — SKIPPED action, LOG.warn only,
 *       zero regression). Outcomes are aggregated into
 *       {@link RecoveryScanResult#getTimeoutActions()}.</li>
 *   <li><b>Orphan session detection</b>: {@code SELECT s.SESSION_ID FROM
 *       ai_agent_session s WHERE s.STATUS IN ('running','pending') AND NOT
 *       EXISTS (SELECT 1 FROM ai_agent_session_lock l WHERE l.SESSION_ID =
 *       s.SESSION_ID AND l.LOCK_EXPIRES_AT > now)}. Each orphan session ID
 *       is LOG.warn'd (non-silent observation; gives downstream recovery
 *       strategy successors an observation basis).</li>
 *   <li><b>Orphan recovery (plan 226)</b>: each detected orphan session is
 *       passed to the configured {@link IOrphanRecoveryHandler} (shipped
 *       default {@link NoOpOrphanRecoveryHandler} — SKIP mode, LOG.warn
 *       only, zero regression). Integrators inject a functional handler
 *       (e.g. {@link DefaultOrphanRecoveryHandler} for RESUME/ABORT) via
 *       {@link #setOrphanRecoveryHandler}. Outcomes are aggregated into
 *       {@link RecoveryScanResult#getRecoveryActions()}.</li>
 * </ol>
 *
 * <p><b>Step ordering (plan 229 design 裁定 3)</b>: timeout detection runs
 * <em>after</em> stale-lock cleanup and <em>before</em> orphan detection.
 * This ensures a timed-out orphan session force-marked {@code failed}
 * (terminal) by the timeout handler is automatically excluded by the
 * subsequent orphan-detection SQL (which filters
 * {@code STATUS IN ('running','pending')}), avoiding double-handling
 * between the timeout handler and the orphan handler.
 *
 * <p><b>No silent no-op</b> (Minimum Rules #24): a failed stale-lock
 * DELETE propagates as {@link NopAiAgentException} (never swallowed); a
 * failed orphan-detection SELECT propagates likewise. Orphan sessions are
 * LOG.warn'd, never silently ignored.
 *
 * <p><b>Thread safety</b>: {@code start}/{@code stop} are
 * {@code synchronized} and guard a {@code volatile} {@link Future} handle.
 * {@code scanOnce} is stateless w.r.t. the handle and safe for concurrent
 * invocation (the SQL is idempotent).
 *
 * <p><b>Schema</b>: requires the existing {@code ai_agent_session} and
 * {@code ai_agent_session_lock} tables. The lock table is auto-created at
 * construction (mirrors {@code DbSessionTakeoverLock}). The session table
 * is assumed to already exist (created by {@code DBSessionStore}).
 *
 * <p>See plan 222, plan 226, plan 229 and design
 * {@code nop-ai-agent-actor-runtime-vision.md} §6.3 / §10 Phase 4.
 */
public class ScheduledRecoveryManager implements IRecoveryManager {

    private static final Logger LOG = LoggerFactory.getLogger(ScheduledRecoveryManager.class);

    /**
     * Default scan interval (60 seconds), matching the RecoveryManager
     * workflow cadence (vision §6.3).
     */
    public static final long DEFAULT_SCAN_INTERVAL_SEC = 60L;

    /**
     * Default wall-clock timeout threshold for the built-in timeout
     * detection step, in seconds. Used only when no explicit
     * {@code timeoutSeconds} is supplied via
     * {@link #setTimeoutSeconds(long)} (plan 229). 30 minutes matches the
     * default {@code lockLeaseMs} (30min) of {@code DefaultAgentEngine},
     * so the timeout fires well before a healthy long task's lock lease
     * would otherwise expire. Integrators tune per deployment.
     */
    public static final long DEFAULT_TIMEOUT_SECONDS = 30L * 60L;

    private final DataSource dataSource;
    private final IScheduledExecutor scheduledExecutor;
    private final long scanIntervalSec;

    private volatile Future<?> scheduleHandle;

    /**
     * Pluggable orphan-recovery strategy (plan 226 /
     * L4-8-P4-RecoveryStrategy). Invoked once per detected orphan session
     * inside {@link #scanOnce}. Shipped default is
     * {@link NoOpOrphanRecoveryHandler} (SKIP mode — LOG.warn, zero
     * behaviour regression with plan 222). Integrators inject a
     * functional handler (e.g. {@link DefaultOrphanRecoveryHandler}) via
     * {@link #setOrphanRecoveryHandler}.
     */
    private IOrphanRecoveryHandler orphanRecoveryHandler = NoOpOrphanRecoveryHandler.noOp();

    /**
     * Pluggable session-timeout strategy (plan 229 /
     * L4-8-P4-TimeoutAbort). Invoked once per detected timed-out session
     * inside {@link #scanOnce}. Shipped default is
     * {@link NoOpSessionTimeoutHandler} (SKIPPED action — LOG.warn, zero
     * behaviour regression with plan 226). Integrators inject a
     * functional handler (e.g. {@link DefaultSessionTimeoutHandler} for
     * LOCAL_CANCELLED / FORCE_FAILED / SKIPPED_REMOTE three-way
     * classification) via {@link #setSessionTimeoutHandler}.
     */
    private ISessionTimeoutHandler sessionTimeoutHandler = NoOpSessionTimeoutHandler.noOp();

    /**
     * Wall-clock timeout threshold in seconds, applied by the
     * timeout-detection step in {@link #scanOnce} (plan 229 design
     * 裁定 2). A session whose {@code UPDATED_AT} is older than
     * {@code now - timeoutSeconds*1000} is considered timed-out and is
     * passed to {@link #sessionTimeoutHandler}. Defaults to
     * {@link #DEFAULT_TIMEOUT_SECONDS}.
     */
    private long timeoutSeconds = DEFAULT_TIMEOUT_SECONDS;

    /**
     * Create a daemon with the default 60s scan interval.
     *
     * @param dataSource         the JDBC data source backing the session
     *                           and lock tables; never null
     * @param scheduledExecutor  the scheduler used to register the periodic
     *                           task; never null. Tests inject a mock
     *                           scheduler; deployments inject a real
     *                           executor (e.g. {@code GlobalExecutors.globalTimer()})
     */
    public ScheduledRecoveryManager(DataSource dataSource, IScheduledExecutor scheduledExecutor) {
        this(dataSource, scheduledExecutor, DEFAULT_SCAN_INTERVAL_SEC);
    }

    /**
     * Create a daemon with a custom scan interval.
     *
     * @param scanIntervalSec the fixed delay between scans, in seconds;
     *                         must be {@code > 0}
     */
    public ScheduledRecoveryManager(DataSource dataSource, IScheduledExecutor scheduledExecutor,
                                    long scanIntervalSec) {
        if (dataSource == null) {
            throw new NopAiAgentException("ScheduledRecoveryManager: dataSource must not be null");
        }
        if (scheduledExecutor == null) {
            throw new NopAiAgentException("ScheduledRecoveryManager: scheduledExecutor must not be null");
        }
        if (scanIntervalSec <= 0) {
            throw new NopAiAgentException(
                    "ScheduledRecoveryManager: scanIntervalSec must be > 0 (got " + scanIntervalSec + ")");
        }
        this.dataSource = dataSource;
        this.scheduledExecutor = scheduledExecutor;
        this.scanIntervalSec = scanIntervalSec;
        initLockSchema();
    }

    /**
     * Set the orphan-recovery handler invoked for each orphan session
     * detected by {@link #scanOnce} (plan 226). When not set, the shipped
     * default {@link NoOpOrphanRecoveryHandler} (SKIP mode — LOG.warn, no
     * recovery action) is used, preserving zero behaviour regression with
     * plan 222.
     *
     * @param handler the orphan-recovery strategy; never null (null is
     *                rejected to prevent silent fallback to the default)
     */
    public void setOrphanRecoveryHandler(IOrphanRecoveryHandler handler) {
        if (handler == null) {
            throw new NopAiAgentException(
                    "ScheduledRecoveryManager.setOrphanRecoveryHandler: handler must not be null");
        }
        this.orphanRecoveryHandler = handler;
    }

    /**
     * Return the currently configured orphan-recovery handler (exposed for
     * testability and integrator inspection).
     *
     * @return the non-null handler (shipped default
     *         {@link NoOpOrphanRecoveryHandler} unless overridden)
     */
    public IOrphanRecoveryHandler getOrphanRecoveryHandler() {
        return orphanRecoveryHandler;
    }

    /**
     * Set the session-timeout handler invoked for each timed-out session
     * detected by {@link #scanOnce} (plan 229 / L4-8-P4-TimeoutAbort). When
     * not set, the shipped default {@link NoOpSessionTimeoutHandler}
     * (SKIPPED action — LOG.warn, no timeout enforcement) is used,
     * preserving zero behaviour regression with plan 226.
     *
     * @param handler the session-timeout strategy; never null (null is
     *                rejected to prevent silent fallback to the default)
     */
    public void setSessionTimeoutHandler(ISessionTimeoutHandler handler) {
        if (handler == null) {
            throw new NopAiAgentException(
                    "ScheduledRecoveryManager.setSessionTimeoutHandler: handler must not be null");
        }
        this.sessionTimeoutHandler = handler;
    }

    /**
     * Return the currently configured session-timeout handler (exposed for
     * testability and integrator inspection).
     *
     * @return the non-null handler (shipped default
     *         {@link NoOpSessionTimeoutHandler} unless overridden)
     */
    public ISessionTimeoutHandler getSessionTimeoutHandler() {
        return sessionTimeoutHandler;
    }

    /**
     * Set the wall-clock timeout threshold (plan 229 design 裁定 2) applied
     * by the timeout-detection step in {@link #scanOnce}. A session whose
     * {@code UPDATED_AT} is older than {@code now - timeoutSeconds*1000}
     * is considered timed-out.
     *
     * @param timeoutSeconds the timeout threshold in seconds; must be {@code > 0}
     */
    public void setTimeoutSeconds(long timeoutSeconds) {
        if (timeoutSeconds <= 0) {
            throw new NopAiAgentException(
                    "ScheduledRecoveryManager.setTimeoutSeconds: timeoutSeconds must be > 0 (got "
                            + timeoutSeconds + ")");
        }
        this.timeoutSeconds = timeoutSeconds;
    }

    /**
     * Return the currently configured wall-clock timeout threshold in
     * seconds (exposed for testability and integrator inspection).
     */
    public long getTimeoutSeconds() {
        return timeoutSeconds;
    }

    private void initLockSchema() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(AiAgentSessionLockTable.DDL_CREATE_TABLE);
        } catch (SQLException e) {
            throw new NopAiAgentException(
                    "ScheduledRecoveryManager: failed to initialize lock schema: " + e.getMessage(), e);
        }
    }

    // ========================================================================
    // Lifecycle (idempotent start / stop)
    // ========================================================================

    @Override
    public synchronized void start() {
        if (scheduleHandle != null) {
            // Idempotent: already running.
            return;
        }
        scheduleHandle = scheduledExecutor.scheduleWithFixedDelay(
                this::scanOnceSafe, scanIntervalSec, scanIntervalSec, TimeUnit.SECONDS);
        LOG.info("ScheduledRecoveryManager: started periodic recovery scan (intervalSec={})", scanIntervalSec);
    }

    @Override
    public synchronized void stop() {
        if (scheduleHandle == null) {
            // Idempotent: not running.
            return;
        }
        scheduleHandle.cancel(false);
        scheduleHandle = null;
        LOG.info("ScheduledRecoveryManager: stopped periodic recovery scan");
    }

    /**
     * Wrapper invoked by the scheduler: runs {@link #scanOnce} and logs any
     * unexpected failure at WARN so the periodic task is never silently
     * killed by an exception (the scheduler's own exception handling
     * varies by implementation). A failed scan is observable, not silent.
     */
    private void scanOnceSafe() {
        try {
            RecoveryScanResult result = scanOnce();
            if (result.getStaleLocksCleaned() > 0 || result.getOrphanSessionsDetected() > 0
                    || !result.getTimeoutActions().isEmpty()) {
                LOG.info("ScheduledRecoveryManager: scan complete: {}", result);
            } else {
                LOG.debug("ScheduledRecoveryManager: scan complete (no stale locks / orphans / timeouts): {}", result);
            }
        } catch (RuntimeException e) {
            LOG.warn("ScheduledRecoveryManager: periodic scan failed (will retry next interval): {}",
                    e.toString());
        }
    }

    // ========================================================================
    // scanOnce — stale lock cleanup + orphan session detection
    // ========================================================================

    @Override
    public RecoveryScanResult scanOnce() {
        long scannedAt = System.currentTimeMillis();
        long start = scannedAt;

        long now = System.currentTimeMillis();
        int staleLocksCleaned = deleteStaleLocks(now);

        // Timeout detection (plan 229 design 裁定 3) — runs AFTER stale-lock
        // cleanup and BEFORE orphan detection. This ordering ensures a
        // timed-out orphan session force-marked 'failed' (terminal) by the
        // timeout handler is excluded by the subsequent orphan-detection
        // SQL (which filters STATUS IN ('running','pending')), avoiding
        // double-handling between the timeout handler and the orphan
        // handler. Per-session isolation: a handler that returns a failed
        // outcome does not abort the scan.
        long timeoutThresholdMillis = now - timeoutSeconds * 1000L;
        List<String> timedOutSessionIds = selectTimedOutSessions(timeoutThresholdMillis);
        List<TimeoutOutcome> timeoutActions = new ArrayList<>(timedOutSessionIds.size());
        for (String timedOutId : timedOutSessionIds) {
            // Non-silent observation (Minimum Rules #24): timed-out
            // sessions are LOG.warn'd (also logged by the handler), never
            // silently ignored. Every timed-out session gets an outcome.
            LOG.warn("ScheduledRecoveryManager: detected timed-out session "
                    + "(status=running/pending, UPDATED_AT older than {}s): sessionId={}",
                    timeoutSeconds, timedOutId);
            TimeoutOutcome outcome = sessionTimeoutHandler.handleTimeout(timedOutId);
            timeoutActions.add(outcome);
        }

        List<String> orphanSessionIds = selectOrphanSessions(now);

        // Invoke the orphan-recovery handler once per detected orphan
        // session (plan 226). Every orphan gets an outcome — non-silent
        // (Minimum Rules #24). Per-session isolation: a handler that
        // returns a failed outcome does not abort the scan.
        List<RecoveryOutcome> recoveryActions = new ArrayList<>(orphanSessionIds.size());
        for (String orphanId : orphanSessionIds) {
            // Non-silent observation (Minimum Rules #24): orphan sessions
            // are LOG.warn'd (also logged by the handler), never silently
            // ignored. Gives downstream recovery strategy successors an
            // observation basis.
            LOG.warn("ScheduledRecoveryManager: detected orphan session (status=running/pending, "
                    + "no active takeover lock): sessionId={}", orphanId);
            RecoveryOutcome outcome = orphanRecoveryHandler.handleOrphan(orphanId);
            recoveryActions.add(outcome);
        }

        long scanDurationMs = System.currentTimeMillis() - start;
        return new RecoveryScanResult(
                staleLocksCleaned,
                orphanSessionIds.size(),
                orphanSessionIds,
                scanDurationMs,
                scannedAt,
                recoveryActions,
                timeoutActions);
    }

    /**
     * Idempotent stale-lock cleanup. DELETE rows from
     * {@code ai_agent_session_lock} whose {@code LOCK_EXPIRES_AT <= now}.
     * DELETE of absent rows is a no-op, so concurrent scans from multiple
     * instances are safe. A SQL failure propagates as
     * {@link NopAiAgentException} (never swallowed).
     */
    private int deleteStaleLocks(long now) {
        String deleteSql = "DELETE FROM " + AiAgentSessionLockTable.TABLE_NAME
                + " WHERE " + AiAgentSessionLockTable.COL_LOCK_EXPIRES_AT + " <= ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(deleteSql)) {
            ps.setLong(1, now);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new NopAiAgentException(
                    "ScheduledRecoveryManager: stale-lock cleanup DELETE failed: " + e.getMessage(), e);
        }
    }

    /**
     * Orphan session detection. SELECT sessions with
     * {@code STATUS IN ('running','pending')} that have no active
     * (non-expired) takeover lock. A SQL failure propagates as
     * {@link NopAiAgentException} (never swallowed).
     */
    private List<String> selectOrphanSessions(long now) {
        String selectSql = "SELECT s." + AiAgentSessionTable.COL_SESSION_ID
                + " FROM " + AiAgentSessionTable.TABLE_NAME + " s"
                + " WHERE s." + AiAgentSessionTable.COL_STATUS + " IN ('running','pending')"
                + " AND NOT EXISTS (SELECT 1 FROM " + AiAgentSessionLockTable.TABLE_NAME + " l"
                + " WHERE l." + AiAgentSessionLockTable.COL_SESSION_ID + " = s."
                + AiAgentSessionTable.COL_SESSION_ID
                + " AND l." + AiAgentSessionLockTable.COL_LOCK_EXPIRES_AT + " > ?)";
        List<String> orphanIds = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(selectSql)) {
            ps.setLong(1, now);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    orphanIds.add(rs.getString(1));
                }
            }
        } catch (SQLException e) {
            throw new NopAiAgentException(
                    "ScheduledRecoveryManager: orphan-session detection SELECT failed: " + e.getMessage(), e);
        }
        return orphanIds;
    }

    /**
     * Timeout detection (plan 229 design 裁定 2). SELECT sessions with
     * {@code STATUS IN ('running','pending')} whose {@code UPDATED_AT}
     * (the activity-timestamp proxy) is older than the threshold. A SQL
     * failure propagates as {@link NopAiAgentException} (never swallowed).
     *
     * @param thresholdMillis the cutoff epoch-millis value; sessions with
     *                        {@code UPDATED_AT < thresholdMillis} are
     *                        considered timed-out
     */
    private List<String> selectTimedOutSessions(long thresholdMillis) {
        String selectSql = "SELECT " + AiAgentSessionTable.COL_SESSION_ID
                + " FROM " + AiAgentSessionTable.TABLE_NAME
                + " WHERE " + AiAgentSessionTable.COL_STATUS + " IN ('running','pending')"
                + " AND " + AiAgentSessionTable.COL_UPDATED_AT + " < ?";
        List<String> timedOutIds = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(selectSql)) {
            ps.setLong(1, thresholdMillis);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    timedOutIds.add(rs.getString(1));
                }
            }
        } catch (SQLException e) {
            throw new NopAiAgentException(
                    "ScheduledRecoveryManager: timed-out-session detection SELECT failed: "
                            + e.getMessage(), e);
        }
        return timedOutIds;
    }
}
