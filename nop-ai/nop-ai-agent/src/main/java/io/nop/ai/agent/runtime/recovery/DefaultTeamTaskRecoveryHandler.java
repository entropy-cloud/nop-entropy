package io.nop.ai.agent.runtime.recovery;

import io.nop.ai.agent.engine.NopAiAgentException;
import io.nop.ai.agent.security.ITenantResolver;
import io.nop.ai.agent.security.NullTenantResolver;
import io.nop.ai.agent.security.TenantSql;
import io.nop.ai.agent.team.AiAgentTeamTaskTable;
import io.nop.ai.agent.team.TeamTaskStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Functional implementation of {@link ITeamTaskRecoveryHandler} — a
 * self-contained strategy that detects stuck CLAIMED team tasks and acts
 * per the configured {@link TeamTaskRecoveryAction} (plan 240 /
 * L4-team-task-reclaim-and-timeout-abandon).
 *
 * <p><b>Self-contained handler (design 裁定 3, plan 240)</b>: unlike
 * {@link DefaultOrphanRecoveryHandler} and {@link DefaultSessionTimeoutHandler}
 * where {@code ScheduledRecoveryManager.scanOnce} performs the detection
 * SELECT and the handler acts per-item, this handler performs <em>both</em>
 * the detection (SELECT {@code ai_agent_team_task} for stuck CLAIMED tasks)
 * and the per-task action (raw JDBC UPDATE) internally. scanOnce calls
 * {@link #recoverStuckTasks()} once and aggregates the returned outcome list.
 * Rationale: team-task is a different domain table from session recovery;
 * encapsulating the team-task domain logic in the handler keeps the daemon
 * focused on the session domain, makes the handler independently testable
 * (inject an H2 DataSource), and isolates team-task table schema changes
 * from {@code ScheduledRecoveryManager}.
 *
 * <p><b>Time-based detection (design 裁定 2)</b>: a task is "stuck" when its
 * {@code STATUS='CLAIMED'} and its {@code ai_agent_team_task.UPDATED_AT} is
 * older than {@code now - taskTimeoutSeconds*1000}. {@code UPDATED_AT} is
 * the activity-timestamp proxy — when a task transitions to CLAIMED the
 * claim updates UPDATED_AT to the claim time, so a CLAIMED task with an old
 * UPDATED_AT is one whose claimer has stopped making progress (the task
 * has not transitioned to COMPLETED/ABANDONED). This mirrors plan 229
 * session-timeout detection. Claimer-liveness cross-detection (checking
 * whether the claimer's session is orphaned) is an explicit successor.
 *
 * <p><b>Actions (design 裁定 4 + 4a)</b>: for each detected stuck task, the
 * handler applies the configured {@code defaultAction}:
 * <ul>
 *   <li><b>{@link TeamTaskRecoveryAction#RECLAIM}</b> — raw JDBC conditional
 *       {@code UPDATE ai_agent_team_task SET STATUS='CREATED',
 *       CLAIMED_BY=NULL, UPDATED_AT=? WHERE TASK_ID=? AND STATUS='CLAIMED'}
 *       <strong>+ {@code TenantSql.whereTenant(COL_TENANT_ID)}</strong>.
 *       Resets the task to re-claimable state (clears claimedBy). Retry-
 *       friendly.</li>
 *   <li><b>{@link TeamTaskRecoveryAction#ABORT}</b> — raw JDBC conditional
 *       {@code UPDATE ai_agent_team_task SET STATUS='ABANDONED',
 *       UPDATED_AT=? WHERE TASK_ID=? AND STATUS='CLAIMED'}
 *       <strong>+ tenant guard</strong>. Marks the task as terminal failure.
 *       Strict mode.</li>
 * </ul>
 * The conditional {@code WHERE STATUS='CLAIMED'} is the CAS guard — a task
 * that transitioned between detection and action (e.g. the member completed
 * it) yields affected-rows=0 → {@code succeeded=false} (honest, non-silent).
 *
 * <p><b>Multi-tenant isolation (design 裁定 4a, plan 232)</b>: the handler
 * accepts an {@link ITenantResolver} (default {@link NullTenantResolver}).
 * When the resolver reports a non-null tenant, both the detection SELECT
 * and the per-task UPDATE inject {@code TenantSql.whereTenant(COL_TENANT_ID)}
 * so tenant A's daemon never recovers tenant B's tasks. When {@code null},
 * SQL is byte-identical to the single-tenant path (zero regression).
 *
 * <p><b>Fail-fast construction (Minimum Rules #24)</b>: {@code dataSource}
 * null, {@code taskTimeoutSeconds <= 0}, or {@code defaultAction == SKIP}
 * fails fast with {@link IllegalArgumentException}. SKIP integrators should
 * use {@link NoOpTeamTaskRecoveryHandler} directly (it performs zero DB
 * access; constructing this handler with SKIP would be a misuse signal).
 *
 * <p><b>Per-task failure isolation (design 裁定 4, Minimum Rules #24)</b>:
 * the detection SELECT's {@link SQLException} propagates as
 * {@link NopAiAgentException} (detection failure = whole step unavailable,
 * mirroring {@code ScheduledRecoveryManager.selectOrphanSessions}). A
 * per-task RECLAIM/ABORT UPDATE's {@link SQLException} is caught and
 * recorded as a {@code succeeded=false} outcome with a descriptive message
 * (never swallowed) — one bad task's UPDATE failure does not abort the same
 * batch's other tasks, mirroring
 * {@link DefaultOrphanRecoveryHandler#handleAbort} per-session isolation.
 *
 * <p><b>Wiring</b>: integrators construct this handler and inject it via
 * {@code ScheduledRecoveryManager.setTeamTaskRecoveryHandler}.
 *
 * <p>See plan 240 Phase 2 and design
 * {@code nop-ai-agent-team-task-reclaim.md}.
 */
public class DefaultTeamTaskRecoveryHandler implements ITeamTaskRecoveryHandler {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultTeamTaskRecoveryHandler.class);

    private final DataSource dataSource;
    private final long taskTimeoutSeconds;
    private final TeamTaskRecoveryAction defaultAction;
    private final ITenantResolver tenantResolver;

    /**
     * Create a handler with the default {@link NullTenantResolver}
     * (backward-compatible / single-tenant path).
     *
     * @param dataSource         the JDBC data source backing the
     *                           {@code ai_agent_team_task} table; never null
     * @param taskTimeoutSeconds the stuck-task threshold in seconds; a
     *                           CLAIMED task whose UPDATED_AT is older than
     *                           {@code now - taskTimeoutSeconds*1000} is
     *                           considered stuck; must be {@code > 0}
     * @param defaultAction      the action applied to every detected stuck
     *                           task (RECLAIM or ABORT); SKIP is rejected
     *                           (use {@link NoOpTeamTaskRecoveryHandler}
     *                           directly)
     */
    public DefaultTeamTaskRecoveryHandler(DataSource dataSource, long taskTimeoutSeconds,
                                          TeamTaskRecoveryAction defaultAction) {
        this(dataSource, taskTimeoutSeconds, defaultAction, NullTenantResolver.INSTANCE);
    }

    /**
     * Create a handler with a contextual tenant resolver (plan 232 / vision
     * §5.1). When the resolver reports a non-null tenant, the detection
     * SELECT and the per-task UPDATE inject
     * {@code TenantSql.whereTenant(COL_TENANT_ID)} so tenant A's daemon
     * never recovers tenant B's tasks; when {@code null}, SQL is byte-
     * identical to the single-tenant path (zero regression).
     *
     * @param dataSource         the JDBC data source; never null
     * @param taskTimeoutSeconds the stuck-task threshold in seconds; must be
     *                           {@code > 0}
     * @param defaultAction      the action (RECLAIM or ABORT); SKIP rejected
     * @param tenantResolver     the contextual tenant resolver; never null
     */
    public DefaultTeamTaskRecoveryHandler(DataSource dataSource, long taskTimeoutSeconds,
                                          TeamTaskRecoveryAction defaultAction,
                                          ITenantResolver tenantResolver) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
        if (taskTimeoutSeconds <= 0) {
            throw new IllegalArgumentException(
                    "DefaultTeamTaskRecoveryHandler: taskTimeoutSeconds must be > 0 (got "
                            + taskTimeoutSeconds + ")");
        }
        if (defaultAction == null) {
            throw new IllegalArgumentException(
                    "DefaultTeamTaskRecoveryHandler: defaultAction must not be null");
        }
        if (defaultAction == TeamTaskRecoveryAction.SKIP) {
            // SKIP integrators should use NoOpTeamTaskRecoveryHandler directly
            // (it performs zero DB access). Constructing this handler with
            // SKIP would be a misuse signal — fail-fast (Minimum Rules #24).
            throw new IllegalArgumentException(
                    "DefaultTeamTaskRecoveryHandler: defaultAction=SKIP is not allowed "
                            + "(use NoOpTeamTaskRecoveryHandler directly for observe-only)");
        }
        this.taskTimeoutSeconds = taskTimeoutSeconds;
        this.defaultAction = defaultAction;
        this.tenantResolver = Objects.requireNonNull(tenantResolver, "tenantResolver must not be null");
    }

    public TeamTaskRecoveryAction getDefaultAction() {
        return defaultAction;
    }

    public long getTaskTimeoutSeconds() {
        return taskTimeoutSeconds;
    }

    @Override
    public List<TeamTaskRecoveryOutcome> recoverStuckTasks() {
        long now = System.currentTimeMillis();
        long threshold = now - taskTimeoutSeconds * 1000L;
        String tenant = tenantResolver.resolveTenantId();

        List<String> stuckTaskIds = selectStuckTaskIds(threshold, tenant);
        List<TeamTaskRecoveryOutcome> outcomes = new ArrayList<>(stuckTaskIds.size());
        for (String taskId : stuckTaskIds) {
            LOG.warn("DefaultTeamTaskRecoveryHandler: detected stuck team task "
                    + "(status=CLAIMED, UPDATED_AT older than {}s): taskId={}",
                    taskTimeoutSeconds, taskId);
            TeamTaskRecoveryOutcome outcome = applyAction(taskId, defaultAction, now, tenant);
            outcomes.add(outcome);
        }
        return outcomes;
    }

    /**
     * Select the task IDs of all stuck CLAIMED tasks (UPDATED_AT older than
     * the threshold). A SQL failure propagates as {@link NopAiAgentException}
     * (detection failure = whole step unavailable, mirroring
     * {@code ScheduledRecoveryManager.selectOrphanSessions}).
     */
    private List<String> selectStuckTaskIds(long thresholdMillis, String tenant) {
        String sql = "SELECT " + AiAgentTeamTaskTable.COL_TASK_ID
                + " FROM " + AiAgentTeamTaskTable.TABLE_NAME
                + " WHERE " + AiAgentTeamTaskTable.COL_STATUS + " = ?"
                + " AND " + AiAgentTeamTaskTable.COL_UPDATED_AT + " < ?";
        if (tenant != null) {
            sql += TenantSql.whereTenant(AiAgentTeamTaskTable.COL_TENANT_ID);
        }
        List<String> taskIds = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, TeamTaskStatus.CLAIMED.name());
            ps.setLong(2, thresholdMillis);
            if (tenant != null) {
                ps.setString(3, tenant);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    taskIds.add(rs.getString(1));
                }
            }
        } catch (SQLException e) {
            // Detection failure = whole step unavailable (mirrors
            // ScheduledRecoveryManager.selectOrphanSessions). Propagate.
            throw new NopAiAgentException(
                    "DefaultTeamTaskRecoveryHandler: stuck-task detection SELECT failed: "
                            + e.getMessage(), e);
        }
        return taskIds;
    }

    /**
     * Apply the configured action to a single stuck task via a conditional
     * raw JDBC UPDATE (CAS guard on {@code STATUS='CLAIMED'}). Per-task
     * failure isolation: a SQLException is caught and recorded as a
     * {@code succeeded=false} outcome (never swallowed), so one bad task's
     * UPDATE failure does not abort the same batch's other tasks.
     */
    private TeamTaskRecoveryOutcome applyAction(String taskId, TeamTaskRecoveryAction action,
                                                long now, String tenant) {
        String sql;
        switch (action) {
            case RECLAIM:
                sql = "UPDATE " + AiAgentTeamTaskTable.TABLE_NAME
                        + " SET " + AiAgentTeamTaskTable.COL_STATUS + " = ?, "
                        + AiAgentTeamTaskTable.COL_CLAIMED_BY + " = NULL, "
                        + AiAgentTeamTaskTable.COL_UPDATED_AT + " = ? "
                        + "WHERE " + AiAgentTeamTaskTable.COL_TASK_ID + " = ? "
                        + "AND " + AiAgentTeamTaskTable.COL_STATUS + " = ?";
                if (tenant != null) {
                    sql += TenantSql.whereTenant(AiAgentTeamTaskTable.COL_TENANT_ID);
                }
                return executeReclaim(taskId, sql, now, tenant);
            case ABORT:
                sql = "UPDATE " + AiAgentTeamTaskTable.TABLE_NAME
                        + " SET " + AiAgentTeamTaskTable.COL_STATUS + " = ?, "
                        + AiAgentTeamTaskTable.COL_UPDATED_AT + " = ? "
                        + "WHERE " + AiAgentTeamTaskTable.COL_TASK_ID + " = ? "
                        + "AND " + AiAgentTeamTaskTable.COL_STATUS + " = ?";
                if (tenant != null) {
                    sql += TenantSql.whereTenant(AiAgentTeamTaskTable.COL_TENANT_ID);
                }
                return executeAbort(taskId, sql, now, tenant);
            case SKIP:
                // Unreachable: constructor rejects SKIP. Fail-loud rather
                // than silently return an empty outcome.
                throw new IllegalStateException(
                        "DefaultTeamTaskRecoveryHandler: SKIP action is not supported "
                                + "(use NoOpTeamTaskRecoveryHandler directly)");
            default:
                throw new IllegalStateException(
                        "DefaultTeamTaskRecoveryHandler: unhandled action: " + action);
        }
    }

    private TeamTaskRecoveryOutcome executeReclaim(String taskId, String sql, long now, String tenant) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, TeamTaskStatus.CREATED.name());
            ps.setLong(2, now);
            ps.setString(3, taskId);
            ps.setString(4, TeamTaskStatus.CLAIMED.name());
            if (tenant != null) {
                ps.setString(5, tenant);
            }
            int affected = ps.executeUpdate();
            if (affected == 1) {
                return new TeamTaskRecoveryOutcome(taskId, TeamTaskRecoveryAction.RECLAIM, true,
                        "RECLAIM: task status reset to CREATED, claimedBy cleared (re-claimable)");
            }
            // Non-silent (Minimum Rules #24): affected rows=0 means the task
            // already transitioned (member completed/abandoned it between
            // detection and action).
            return new TeamTaskRecoveryOutcome(taskId, TeamTaskRecoveryAction.RECLAIM, false,
                    "RECLAIM skipped: task already transitioned (affected rows=0)");
        } catch (SQLException e) {
            // Per-task isolation: record the SQL failure as a failed outcome
            // so one bad task does not abort the scan.
            LOG.warn("DefaultTeamTaskRecoveryHandler: RECLAIM SQL failed for taskId={}",
                    taskId, e);
            return new TeamTaskRecoveryOutcome(taskId, TeamTaskRecoveryAction.RECLAIM, false,
                    "RECLAIM failed (SQL): " + e.toString());
        }
    }

    private TeamTaskRecoveryOutcome executeAbort(String taskId, String sql, long now, String tenant) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, TeamTaskStatus.ABANDONED.name());
            ps.setLong(2, now);
            ps.setString(3, taskId);
            ps.setString(4, TeamTaskStatus.CLAIMED.name());
            if (tenant != null) {
                ps.setString(5, tenant);
            }
            int affected = ps.executeUpdate();
            if (affected == 1) {
                return new TeamTaskRecoveryOutcome(taskId, TeamTaskRecoveryAction.ABORT, true,
                        "ABORT: task status set to ABANDONED (terminal)");
            }
            return new TeamTaskRecoveryOutcome(taskId, TeamTaskRecoveryAction.ABORT, false,
                    "ABORT skipped: task already transitioned (affected rows=0)");
        } catch (SQLException e) {
            LOG.warn("DefaultTeamTaskRecoveryHandler: ABORT SQL failed for taskId={}",
                    taskId, e);
            return new TeamTaskRecoveryOutcome(taskId, TeamTaskRecoveryAction.ABORT, false,
                    "ABORT failed (SQL): " + e.toString());
        }
    }
}
