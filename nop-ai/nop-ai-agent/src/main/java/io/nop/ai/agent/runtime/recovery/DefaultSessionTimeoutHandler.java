package io.nop.ai.agent.runtime.recovery;

import io.nop.ai.agent.engine.IAgentEngine;
import io.nop.ai.agent.engine.NopAiAgentException;
import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.runtime.lock.AiAgentSessionLockTable;
import io.nop.ai.agent.security.ITenantResolver;
import io.nop.ai.agent.security.NullTenantResolver;
import io.nop.ai.agent.security.TenantSql;
import io.nop.ai.agent.session.AiAgentSessionTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Functional implementation of {@link ISessionTimeoutHandler} — a
 * pluggable strategy that decides what to do with each timed-out session
 * detected by {@code ScheduledRecoveryManager.scanOnce} (plan 229 /
 * L4-8-P4-TimeoutAbort).
 *
 * <p>For each timed-out session (DB status {@code 'running'}/{@code 'pending'}
 * + {@code ai_agent_session.UPDATED_AT} older than the configured
 * {@code timeoutSeconds}), the handler performs the three-way
 * lock-ownership classification (design 裁定 1) by reading the lock table
 * directly via raw JDBC:
 *
 * <ul>
 *   <li><b>{@link TimeoutAction#LOCAL_CANCELLED}</b> — the lock table has
 *       an active row with {@code LOCK_OWNER == instanceId} and
 *       {@code LOCK_EXPIRES_AT > now}: the session is being executed by
 *       this engine instance. Delegate to
 *       {@link IAgentEngine#cancelSession(String, String, boolean)
 *       engine.cancelSession(sessionId, "timeout", true)} (forced=true
 *       encapsulates graceful + thread interrupt — plan 197). The session
 *       transitions to {@code cancelled} / {@code forced_stopped} via the
 *       existing cancel path. The call is fire-and-forget at the scan-loop
 *       level (the handler does not block on the returned future), and
 *       synchronous exceptions (session not found / already terminal) are
 *       caught and recorded as a non-silent failed outcome.</li>
 *   <li><b>{@link TimeoutAction#FORCE_FAILED}</b> — no active lock row
 *       exists, or {@code LOCK_EXPIRES_AT <= now} (orphaned — stale lock
 *       already cleaned by scan step 1, or never held): no process is
 *       executing the session. Raw JDBC conditional
 *       {@code UPDATE ai_agent_session SET STATUS='failed' WHERE
 *       SESSION_ID=? AND STATUS IN ('running','pending')} (conditional
 *       WHERE prevents marking a session that already transitioned —
 *       mirroring plan 226 ABORT). affected rows=1 → succeeded=true;
 *       affected rows=0 → succeeded=false (already transitioned).</li>
 *   <li><b>{@link TimeoutAction#SKIPPED_REMOTE}</b> — the lock table has
 *       an active row with {@code LOCK_OWNER != instanceId} and
 *       {@code LOCK_EXPIRES_AT > now}: a remote instance owns the
 *       session. LOG.warn the remote owner and take no DB / engine
 *       action — intervening would cause cross-instance status
 *       contention. Always succeeded=true (deliberate non-intervention
 *       is an observed, logged decision).</li>
 * </ul>
 *
 * <p><b>Why raw JDBC on the lock table, not
 * {@code ISessionTakeoverLock}</b> (design 裁定 1): the
 * {@code ISessionTakeoverLock.isHeld(sessionId)} contract returns only a
 * boolean and its Javadoc explicitly states "Does not distinguish
 * owners". LOCAL vs REMOTE classification requires knowing the
 * {@code LOCK_OWNER}, which the interface does not expose. Direct raw
 * JDBC SELECT on {@code ai_agent_session_lock} is the only way to obtain
 * the owner without expanding the {@code ISessionTakeoverLock} public
 * API (which would ripple to {@code NoOpSessionTakeoverLock} and all
 * integrators). This mirrors the existing
 * {@code ScheduledRecoveryManager} stale-lock-cleanup pattern that also
 * visits the lock table directly via raw JDBC. Deployment assumption:
 * when the functional recovery stack is deployed, the
 * {@code ai_agent_session_lock} table already exists (created by
 * {@code DbSessionTakeoverLock} construction-time {@code initSchema()},
 * the same assumption {@code ScheduledRecoveryManager} relies on for
 * stale-lock cleanup).
 *
 * <p><b>Fail-fast construction (Minimum Rules #24)</b>: any null
 * dependency fails fast with {@link NullPointerException}, preventing
 * silent misuse (e.g. a handler that can never classify because no
 * DataSource was wired).
 *
 * <p><b>Per-session failure isolation (No Silent No-Op)</b>: every
 * exception path inside {@link #handleTimeout} is caught and recorded as
 * a {@code succeeded=false} outcome with a descriptive message (never
 * swallowed). This ensures one bad session does not abort the entire
 * scan, and every timed-out session has an observable outcome in
 * {@link RecoveryScanResult#getTimeoutActions()}.
 *
 * <p><b>Wiring</b>: integrators construct this handler and inject it via
 * {@code ScheduledRecoveryManager.setSessionTimeoutHandler}. The engine
 * does not hold a session-timeout-handler field — the handler is the
 * recovery manager's internal strategy.
 *
 * <p>See plan 229 Phase 2 and design
 * {@code nop-ai-agent-actor-runtime-vision.md} §6.3.
 */
public class DefaultSessionTimeoutHandler implements ISessionTimeoutHandler {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultSessionTimeoutHandler.class);

    /**
     * Reason string passed to {@code cancelSession} for LOCAL_CANCELLED
     * actions. Also recorded in the resulting outcome's message.
     */
    static final String TIMEOUT_REASON = "timeout";

    private final long timeoutSeconds;
    private final IAgentEngine engine;
    private final DataSource dataSource;
    private final String instanceId;
    private final ITenantResolver tenantResolver;

    /**
     * Create a timeout handler with the given configuration. Uses the
     * backward-compatible {@link NullTenantResolver}.
     */
    public DefaultSessionTimeoutHandler(long timeoutSeconds, IAgentEngine engine,
                                        DataSource dataSource, String instanceId) {
        this(timeoutSeconds, engine, dataSource, instanceId, NullTenantResolver.INSTANCE);
    }

    /**
     * Create a timeout handler with a contextual tenant resolver (plan 232 /
     * vision §5.1). When the resolver reports a non-null tenant, the
     * FORCE_FAILED {@code UPDATE ai_agent_session} injects the tenant
     * {@code WHERE} so only the current tenant's sessions are marked failed;
     * when {@code null}, SQL is byte-identical to the original (zero
     * regression).
     */
    public DefaultSessionTimeoutHandler(long timeoutSeconds, IAgentEngine engine,
                                        DataSource dataSource, String instanceId,
                                        ITenantResolver tenantResolver) {
        if (timeoutSeconds <= 0) {
            throw new IllegalArgumentException(
                    "DefaultSessionTimeoutHandler: timeoutSeconds must be > 0 (got " + timeoutSeconds + ")");
        }
        this.timeoutSeconds = timeoutSeconds;
        this.engine = java.util.Objects.requireNonNull(engine, "engine must not be null");
        this.dataSource = java.util.Objects.requireNonNull(dataSource, "dataSource must not be null");
        this.instanceId = java.util.Objects.requireNonNull(instanceId, "instanceId must not be null");
        this.tenantResolver = java.util.Objects.requireNonNull(tenantResolver, "tenantResolver must not be null");
    }

    public long getTimeoutSeconds() {
        return timeoutSeconds;
    }

    @Override
    public TimeoutOutcome handleTimeout(String sessionId) {
        // Three-way classification via a single SELECT on the lock table.
        // Returns (owner, expiresAt) in one round-trip; classification is
        // fully determined by the result.
        String selectSql = "SELECT " + AiAgentSessionLockTable.COL_LOCK_OWNER
                + ", " + AiAgentSessionLockTable.COL_LOCK_EXPIRES_AT
                + " FROM " + AiAgentSessionLockTable.TABLE_NAME
                + " WHERE " + AiAgentSessionLockTable.COL_SESSION_ID + " = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(selectSql)) {
            ps.setString(1, sessionId);
            String lockOwner = null;
            long lockExpiresAt = 0L;
            boolean hasRow = false;
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    hasRow = true;
                    lockOwner = rs.getString(1);
                    lockExpiresAt = rs.getLong(2);
                }
            }
            long now = System.currentTimeMillis();
            if (!hasRow || lockExpiresAt <= now) {
                // No active lock (no row, or stale/expired lock already
                // cleaned by scan step 1 or never held) → orphaned →
                // FORCE_FAILED. An expired lock is treated as equivalent
                // to no active lock (design 裁定 1).
                return forceFailed(sessionId);
            }
            // Active lock row exists — classify by owner.
            if (instanceId.equals(lockOwner)) {
                // This instance owns the active lock → LOCAL_CANCELLED.
                return localCancelled(sessionId);
            }
            // A remote instance owns the active lock → SKIPPED_REMOTE.
            return skippedRemote(sessionId, lockOwner);
        } catch (SQLException e) {
            // Non-silent (Minimum Rules #24): a SELECT failure is recorded
            // as a failed outcome so one bad session does not abort the
            // scan. The classification is indeterminate without the lock
            // row, so no DB / engine action is taken.
            LOG.warn("DefaultSessionTimeoutHandler: lock-table SELECT failed for sessionId={}",
                    sessionId, e);
            return new TimeoutOutcome(sessionId, TimeoutAction.SKIPPED, false,
                    "classification failed (lock-table SELECT SQL error): " + e.toString());
        }
    }

    /**
     * LOCAL_CANCELLED: delegate to {@code engine.cancelSession} with
     * forced=true (encapsulates graceful + thread interrupt — plan 197).
     * Fire-and-forget at the scan-loop level: the handler does not join
     * the returned future, so the scan loop is not blocked by a
     * potentially long cancel-completion wait. Synchronous exceptions
     * (session not found / already terminal / engine error) are caught
     * and recorded as a non-silent failed outcome.
     */
    private TimeoutOutcome localCancelled(String sessionId) {
        try {
            // forced=true = graceful + thread interrupt (plan 197).
            // The returned future is intentionally not joined: the daemon
            // scan loop must not block on cancel completion.
            engine.cancelSession(sessionId, TIMEOUT_REASON, true);
            return new TimeoutOutcome(sessionId, TimeoutAction.LOCAL_CANCELLED, true,
                    "LOCAL_CANCELLED: engine.cancelSession(forced=true) invoked (terminal cancelled/forced_stopped)");
        } catch (RuntimeException e) {
            // Non-silent (Minimum Rules #24): capture the synchronous
            // failure (e.g. session not found, already terminal) as a
            // failed outcome with exception detail.
            LOG.warn("DefaultSessionTimeoutHandler: LOCAL_CANCELLED cancelSession threw for sessionId={}",
                    sessionId, e);
            return new TimeoutOutcome(sessionId, TimeoutAction.LOCAL_CANCELLED, false,
                    "LOCAL_CANCELLED failed: " + e.toString());
        }
    }

    /**
     * FORCE_FAILED: conditional raw JDBC UPDATE on
     * {@code ai_agent_session} (design 裁定 1, mirroring plan 226 ABORT).
     * {@code WHERE STATUS IN ('running','pending')} prevents marking a
     * session that has already transitioned to a terminal state between
     * detection and UPDATE.
     */
    private TimeoutOutcome forceFailed(String sessionId) {
        String tenant = tenantResolver.resolveTenantId();
        String updateSql = "UPDATE " + AiAgentSessionTable.TABLE_NAME
                + " SET " + AiAgentSessionTable.COL_STATUS + " = ?"
                + " WHERE " + AiAgentSessionTable.COL_SESSION_ID + " = ?"
                + " AND " + AiAgentSessionTable.COL_STATUS + " IN ('running','pending')";
        if (tenant != null) {
            updateSql += TenantSql.whereTenant(AiAgentSessionTable.COL_TENANT_ID);
        }
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(updateSql)) {
            ps.setString(1, AgentExecStatus.failed.name());
            ps.setString(2, sessionId);
            if (tenant != null) {
                ps.setString(3, tenant);
            }
            int affected = ps.executeUpdate();
            if (affected == 1) {
                return new TimeoutOutcome(sessionId, TimeoutAction.FORCE_FAILED, true,
                        "FORCE_FAILED: session status set to failed");
            }
            // Non-silent (Minimum Rules #24): affected rows=0 means the
            // session already transitioned (another handler / instance
            // already marked it terminal between detection and UPDATE).
            return new TimeoutOutcome(sessionId, TimeoutAction.FORCE_FAILED, false,
                    "FORCE_FAILED skipped: session already transitioned (affected rows=0)");
        } catch (SQLException e) {
            // Non-silent (Minimum Rules #24): record the SQL failure as a
            // failed outcome so one bad session does not abort the scan.
            LOG.warn("DefaultSessionTimeoutHandler: FORCE_FAILED SQL failed for sessionId={}",
                    sessionId, e);
            return new TimeoutOutcome(sessionId, TimeoutAction.FORCE_FAILED, false,
                    "FORCE_FAILED failed (SQL): " + e.toString());
        }
    }

    /**
     * SKIPPED_REMOTE: LOG.warn the remote owner and take no DB / engine
     * action. Always succeeded=true (deliberate non-intervention is an
     * observed, logged decision).
     */
    private TimeoutOutcome skippedRemote(String sessionId, String lockOwner) {
        LOG.warn("DefaultSessionTimeoutHandler: timed-out session is owned by a remote instance "
                + "(SKIPPED_REMOTE, no intervention): sessionId={}, lockOwner={}", sessionId, lockOwner);
        return new TimeoutOutcome(sessionId, TimeoutAction.SKIPPED_REMOTE, true,
                "SKIPPED_REMOTE: timed-out session owned by remote instance (lockOwner=" + lockOwner
                        + "), no intervention");
    }
}
