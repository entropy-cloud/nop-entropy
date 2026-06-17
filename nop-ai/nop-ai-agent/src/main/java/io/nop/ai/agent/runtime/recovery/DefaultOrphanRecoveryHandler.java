package io.nop.ai.agent.runtime.recovery;

import io.nop.ai.agent.engine.IAgentEngine;
import io.nop.ai.agent.engine.NopAiAgentException;
import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.security.ITenantResolver;
import io.nop.ai.agent.security.NullTenantResolver;
import io.nop.ai.agent.security.TenantSql;
import io.nop.ai.agent.session.AiAgentSessionTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Functional implementation of {@link IOrphanRecoveryHandler} — a
 * pluggable strategy that decides what to do with each orphaned session
 * detected by {@code ScheduledRecoveryManager.scanOnce} (plan 226 /
 * L4-8-P4-RecoveryStrategy).
 *
 * <p>The handler is configured with a {@link RecoveryMode} at
 * construction and dispatches {@link #handleOrphan} accordingly:
 * <ul>
 *   <li><b>{@link RecoveryMode#RESUME}</b> — delegate to
 *       {@link IAgentEngine#restoreSession} (fire-and-forget: the daemon
 *       scan loop does not block on the potentially long-running restore).
 *       Safety against double-execution is provided by the cross-process
 *       takeover lock (the restore path internally calls
 *       {@code tryAcquire}; if another instance already owns the lock,
 *       the synchronous exception is caught and a non-silent SKIPPED
 *       outcome is returned — design 裁定 1).</li>
 *   <li><b>{@link RecoveryMode#ABORT}</b> — a conditional raw JDBC
 *       {@code UPDATE ai_agent_session SET STATUS='failed' WHERE
 *       SESSION_ID=? AND STATUS IN ('running','pending')} (design 裁定 2).
 *       The conditional {@code WHERE} prevents aborting a session that
 *       has already transitioned (e.g. another instance resumed it);
 *       affected rows=0 returns a non-silent failed outcome.</li>
 *   <li><b>{@link RecoveryMode#SKIP}</b> — LOG.warn the orphan session
 *       ID and take no recovery action (the shipped-default
 *       {@link NoOpOrphanRecoveryHandler} semantic).</li>
 * </ul>
 *
 * <p><b>Fail-fast construction (Minimum Rules #24)</b>: a RESUME-mode
 * handler with a null {@code engine} fails fast with
 * {@link NullPointerException}; an ABORT-mode handler with a null
 * {@code dataSource} likewise. This prevents silent misuse (e.g. a RESUME
 * handler that can never resume because no engine was wired).
 *
 * <p><b>Per-session failure isolation (No Silent No-Op)</b>: every
 * exception path inside {@link #handleOrphan} is caught and recorded as a
 * {@code succeeded=false} outcome with a descriptive message (never
 * swallowed). This ensures one bad session does not abort the entire
 * scan, and every orphan has an observable outcome in
 * {@link RecoveryScanResult#getRecoveryActions()}.
 *
 * <p><b>Wiring</b>: integrators construct this handler and inject it via
 * {@code ScheduledRecoveryManager.setOrphanRecoveryHandler}. The engine
 * does not hold an orphan-recovery-handler field — the handler is the
 * recovery manager's internal strategy.
 *
 * <p>See plan 226 Phase 2 and design
 * {@code nop-ai-agent-actor-runtime-vision.md} §6.3.
 */
public class DefaultOrphanRecoveryHandler implements IOrphanRecoveryHandler {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultOrphanRecoveryHandler.class);

    /**
     * Approver identity recorded on RESUME-mode {@code restoreSession}
     * audit events and ABORT is intentionally a pure DB transition
     * (no audit approver).
     */
    static final String RECOVERY_APPROVER = "recovery-daemon";
    static final String RECOVERY_REASON = "orphan auto-recovery";

    private final RecoveryMode mode;
    private final IAgentEngine engine;
    private final DataSource dataSource;
    private final ITenantResolver tenantResolver;

    /**
     * Create a handler with the given recovery mode. Uses the
     * backward-compatible {@link NullTenantResolver}.
     *
     * @param mode       the recovery strategy to apply; never null
     * @param engine     the agent engine used by {@link RecoveryMode#RESUME};
     *                   must be non-null when {@code mode == RESUME}
     *                   (fail-fast {@link NullPointerException} otherwise);
     *                   may be null for ABORT/SKIP modes
     * @param dataSource the JDBC data source used by {@link RecoveryMode#ABORT};
     *                   must be non-null when {@code mode == ABORT}
     *                   (fail-fast {@link NullPointerException} otherwise);
     *                   may be null for RESUME/SKIP modes
     */
    public DefaultOrphanRecoveryHandler(RecoveryMode mode, IAgentEngine engine, DataSource dataSource) {
        this(mode, engine, dataSource, NullTenantResolver.INSTANCE);
    }

    /**
     * Create a handler with a contextual tenant resolver (plan 232 / vision
     * §5.1). When the resolver reports a non-null tenant, the ABORT
     * {@code UPDATE ai_agent_session} injects the tenant {@code WHERE} so only
     * the current tenant's sessions are aborted; when {@code null}, SQL is
     * byte-identical to the original (zero regression).
     *
     * @param mode           the recovery strategy to apply; never null
     * @param engine         the agent engine used by {@link RecoveryMode#RESUME}
     * @param dataSource     the JDBC data source used by {@link RecoveryMode#ABORT}
     * @param tenantResolver the contextual tenant resolver; never null
     */
    public DefaultOrphanRecoveryHandler(RecoveryMode mode, IAgentEngine engine,
                                        DataSource dataSource, ITenantResolver tenantResolver) {
        this.mode = java.util.Objects.requireNonNull(mode, "mode must not be null");
        // Fail-fast: prevent silent misuse (Minimum Rules #24).
        if (mode == RecoveryMode.RESUME && engine == null) {
            throw new NullPointerException(
                    "DefaultOrphanRecoveryHandler: engine must not be null for RESUME mode");
        }
        if (mode == RecoveryMode.ABORT && dataSource == null) {
            throw new NullPointerException(
                    "DefaultOrphanRecoveryHandler: dataSource must not be null for ABORT mode");
        }
        this.engine = engine;
        this.dataSource = dataSource;
        this.tenantResolver = java.util.Objects.requireNonNull(tenantResolver, "tenantResolver must not be null");
    }

    public RecoveryMode getMode() {
        return mode;
    }

    @Override
    public RecoveryOutcome handleOrphan(String sessionId) {
        switch (mode) {
            case RESUME:
                return handleResume(sessionId);
            case ABORT:
                return handleAbort(sessionId);
            case SKIP:
                return handleSkip(sessionId);
            default:
                // Unreachable: enum exhaustive. Fail-loud rather than silent.
                throw new IllegalStateException("DefaultOrphanRecoveryHandler: unhandled mode: " + mode);
        }
    }

    /**
     * RESUME mode: delegate to {@code engine.restoreSession}
     * (fire-and-forget — does not block on the returned future). A
     * synchronous exception from {@code restoreSession} (takeover-lock
     * conflict / session not found / terminal session) is caught and
     * recorded as a non-silent failed outcome.
     */
    private RecoveryOutcome handleResume(String sessionId) {
        try {
            // Fire-and-forget (design 裁定 1): do NOT .join() — the daemon
            // scan loop must not block on a potentially long-running LLM
            // restore. The takeover lock (tryAcquire inside restoreSession)
            // guarantees no double-execution.
            engine.restoreSession(sessionId, RECOVERY_APPROVER, RECOVERY_REASON);
            return new RecoveryOutcome(sessionId, RecoveryMode.RESUME, true,
                    "RESUME: restoreSession triggered (fire-and-forget)");
        } catch (RuntimeException e) {
            // Non-silent (Minimum Rules #24): capture the synchronous
            // failure (e.g. tryAcquire conflict, session not found,
            // terminal session) as a failed outcome with exception detail.
            LOG.warn("DefaultOrphanRecoveryHandler: RESUME failed for sessionId={}",
                    sessionId, e);
            return new RecoveryOutcome(sessionId, RecoveryMode.RESUME, false,
                    "RESUME failed: " + e.toString());
        }
    }

    /**
     * ABORT mode: conditional raw JDBC UPDATE
     * (design 裁定 2). {@code WHERE STATUS IN ('running','pending')}
     * prevents aborting a session that has already transitioned.
     */
    private RecoveryOutcome handleAbort(String sessionId) {
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
                return new RecoveryOutcome(sessionId, RecoveryMode.ABORT, true,
                        "ABORT: session status set to failed");
            }
            // Non-silent (Minimum Rules #24): affected rows=0 means the
            // session already transitioned (another instance resumed it,
            // or it reached a terminal state between detection and abort).
            return new RecoveryOutcome(sessionId, RecoveryMode.ABORT, false,
                    "ABORT skipped: session already transitioned (affected rows=0)");
        } catch (SQLException e) {
            // Non-silent (Minimum Rules #24): record the SQL failure as a
            // failed outcome so one bad session does not abort the scan.
            LOG.warn("DefaultOrphanRecoveryHandler: ABORT SQL failed for sessionId={}",
                    sessionId, e);
            return new RecoveryOutcome(sessionId, RecoveryMode.ABORT, false,
                    "ABORT failed (SQL): " + e.toString());
        }
    }

    /**
     * SKIP mode: LOG.warn the orphan session ID and take no recovery
     * action (the shipped-default {@link NoOpOrphanRecoveryHandler}
     * semantic).
     */
    private RecoveryOutcome handleSkip(String sessionId) {
        LOG.warn("DefaultOrphanRecoveryHandler: orphan session observed (SKIP mode, no recovery action): sessionId={}",
                sessionId);
        return new RecoveryOutcome(sessionId, RecoveryMode.SKIP, true,
                "SKIP: orphan session observed, no recovery action taken");
    }
}
