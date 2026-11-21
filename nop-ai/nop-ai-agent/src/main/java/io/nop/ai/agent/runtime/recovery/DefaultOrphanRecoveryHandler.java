package io.nop.ai.agent.runtime.recovery;

import static io.nop.ai.agent.NopAiAgentErrors.ERR_AGENT_INVALID_ARGUMENT;
import static io.nop.ai.agent.NopAiAgentErrors.ERR_AGENT_INVALID_STATE;
import static io.nop.ai.agent.NopAiAgentErrors.ARG_DETAIL;
import io.nop.ai.api.secure.SecureDefault;
import io.nop.ai.agent.engine.IAgentEngine;
import io.nop.ai.agent.NopAiAgentException;
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

/** * <p><b>store-layer 边界（审计 AI-2/AI-19 裁定）</b>：自建表/本地文件为引擎内部运行时状态，
 * 保留独立 store 层（不注册 ORM）；租户/软删不适用理由与表清单见
 * {@code ai-dev/design/nop-ai-agent/store-layer-contract.md}。
 */
@SecureDefault
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
     * 3.1-MA-18 (plan 2555 裁定 G): retry budget for RESUME. The pre-fix
     * form fired restoreSession exactly once — a transient synchronous
     * failure (takeover-lock conflict with an in-flight terminal cleanup,
     * momentary store unavailability) permanently skipped the orphan until
     * the next scan, and the scan loop offered no attempt accounting. The
     * budget: at most {@link #resumeMaxAttempts} attempts with exponential
     * backoff ({@code resumeBackoffBaseMs * 2^(attempt-1)} between attempts),
     * each attempt observed via LOG.info, and a typed budget-exhausted
     * escalation in the returned outcome message. Config keys (setter
     * injection, module convention): {@code nop.ai.agent.orphan-resume-max-attempts},
     * {@code nop.ai.agent.orphan-resume-backoff-base-ms}.
     */
    private int resumeMaxAttempts = 3;
    private long resumeBackoffBaseMs = 200;

    public int getResumeMaxAttempts() {
        return resumeMaxAttempts;
    }

    public void setResumeMaxAttempts(int resumeMaxAttempts) {
        // Plan 2584 Phase 2 (audit nop-ai Minor): typed error, no bare IAE
        if (resumeMaxAttempts <= 0) {
            throw new NopAiAgentException(ERR_AGENT_INVALID_ARGUMENT).param(ARG_DETAIL,
                    "DefaultOrphanRecoveryHandler: resumeMaxAttempts must be positive, got: " + resumeMaxAttempts);
        }
        this.resumeMaxAttempts = resumeMaxAttempts;
    }

    public long getResumeBackoffBaseMs() {
        return resumeBackoffBaseMs;
    }

    public void setResumeBackoffBaseMs(long resumeBackoffBaseMs) {
        if (resumeBackoffBaseMs < 0) {
            throw new NopAiAgentException(ERR_AGENT_INVALID_ARGUMENT).param(ARG_DETAIL,
                    "DefaultOrphanRecoveryHandler: resumeBackoffBaseMs must be >= 0, got: " + resumeBackoffBaseMs);
        }
        this.resumeBackoffBaseMs = resumeBackoffBaseMs;
    }

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
                throw new NopAiAgentException(ERR_AGENT_INVALID_STATE).param(ARG_DETAIL, "DefaultOrphanRecoveryHandler: unhandled mode: " + mode);
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
        // 3.1-MA-18 (裁定 G): bounded retry with exponential backoff +
        // per-attempt observation + budget-exhausted escalation. The
        // restoreSession call stays fire-and-forget (design 裁定 1 — no
        // .join()); only the SYNCHRONOUS failure face is retried here.
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= resumeMaxAttempts; attempt++) {
            try {
                // Fire-and-forget (design 裁定 1): do NOT .join() — the daemon
                // scan loop must not block on a potentially long-running LLM
                // restore. The takeover lock (tryAcquire inside restoreSession)
                // guarantees no double-execution.
                engine.restoreSession(sessionId, RECOVERY_APPROVER, RECOVERY_REASON);
                if (attempt > 1) {
                    LOG.info("DefaultOrphanRecoveryHandler: RESUME succeeded for sessionId={} on attempt {}/{}",
                            sessionId, attempt, resumeMaxAttempts);
                }
                return new RecoveryOutcome(sessionId, RecoveryMode.RESUME, true,
                        "RESUME: restoreSession triggered (fire-and-forget"
                        + (attempt > 1 ? ", attempt=" + attempt : "") + ")");
            } catch (RuntimeException e) {
                // Non-silent (Minimum Rules #24): observe every failed attempt.
                lastFailure = e;
                LOG.warn("DefaultOrphanRecoveryHandler: RESUME attempt {}/{} failed for sessionId={}",
                        attempt, resumeMaxAttempts, sessionId, e);
                if (attempt < resumeMaxAttempts) {
                    long backoffMs = resumeBackoffBaseMs * (1L << (attempt - 1));
                    if (backoffMs > 0) {
                        try {
                            Thread.sleep(backoffMs);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            return new RecoveryOutcome(sessionId, RecoveryMode.RESUME, false,
                                    "RESUME failed: interrupted during backoff after attempt "
                                            + attempt + ": " + e.toString());
                        }
                    }
                }
            }
        }
        // Budget exhausted — typed escalation (裁定 G), never a bare rethrow
        // (one bad session must not abort the scan).
        LOG.warn("nop.ai.agent.orphan-resume-budget-exhausted: RESUME retry budget exhausted for "
                + "sessionId={} after {} attempts — escalating (outcome marked failed; "
                + "the orphan stays for the next scan)", sessionId, resumeMaxAttempts, lastFailure);
        return new RecoveryOutcome(sessionId, RecoveryMode.RESUME, false,
                "RESUME failed: retry budget exhausted (" + resumeMaxAttempts
                        + " attempts): " + lastFailure);
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
