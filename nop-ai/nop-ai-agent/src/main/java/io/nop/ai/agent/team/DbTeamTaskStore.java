package io.nop.ai.agent.team;

import io.nop.ai.agent.engine.NopAiAgentException;
import io.nop.ai.agent.security.ITenantResolver;
import io.nop.ai.agent.security.NullTenantResolver;
import io.nop.ai.agent.security.TenantSql;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Database-backed functional implementation of {@link ITeamTaskStore} — the
 * opt-in cross-process shared task store for multi-instance deployments
 * sharing a single DB (plan 227 / L4-8-team-task-update).
 *
 * <p><b>Backing store</b>: the {@code ai_agent_team_task} table (design 裁定 1
 * — see {@link AiAgentTeamTaskTable}). Each row is a single team task. The
 * table is auto-created at construction via {@link #initSchema} (mirrors the
 * {@code DbSessionTakeoverLock.initSchema} pattern — no hand-written DDL
 * required from integrators).
 *
 * <p><b>State-machine CAS</b> ({@link #claimTask} / {@link #completeTask} /
 * {@link #abandonTask} / {@link #reclaimTask}, design 裁定 3): each
 * transition is a single <b>conditional {@code UPDATE}</b> on
 * {@code STATUS}, with the affected-row-count determining success:
 * <ul>
 *   <li>{@code claimTask}: {@code UPDATE ... SET STATUS='CLAIMED',
 *       CLAIMED_BY=?, UPDATED_AT=? WHERE TASK_ID=? AND STATUS='CREATED'}.
 *       Only one concurrent claimer wins (the row's STATUS guard means a
 *       second claimer's UPDATE matches 0 rows).</li>
 *   <li>{@code completeTask}: {@code UPDATE ... SET STATUS='COMPLETED',
 *       UPDATED_AT=? WHERE TASK_ID=? AND STATUS='CLAIMED'}.</li>
 *   <li>{@code abandonTask}: {@code UPDATE ... SET STATUS='ABANDONED',
 *       UPDATED_AT=? WHERE TASK_ID=? AND STATUS IN ('CREATED','CLAIMED')}.</li>
 *   <li>{@code reclaimTask} (plan 240): {@code UPDATE ... SET STATUS='CREATED',
 *       CLAIMED_BY=NULL, UPDATED_AT=? WHERE TASK_ID=? AND STATUS='CLAIMED'}.
 *       Recovery transition that un-sticks a CLAIMED task whose claimer has
 *       disappeared, clearing claimedBy so the task is re-claimable.</li>
 * </ul>
 * When {@code executeUpdate() == 1} the transition succeeded and the updated
 * row is re-read via a single SELECT to return the fresh {@link TeamTask};
 * when {@code == 0} the transition is illegal (wrong source status) or the
 * task is missing — {@code Optional.empty()} is returned (non-exception
 * control flow, design 裁定 2). No separate {@code VERSION} column is needed
 * because STATUS itself is the optimistic-lock guard (design 裁定 4).
 *
 * <p><b>Thread safety</b>: guaranteed by atomic SQL CAS operations (PK
 * uniqueness + conditional UPDATE with affected-row counts). Multiple engines
 * may share the same {@link DbTeamTaskStore} instance or create independent
 * instances pointing at the same DB — both are safe (the DB row is the single
 * source of truth). H2 is the test backend; production can use any RDBMS that
 * supports the standard SQL above (Postgres / MySQL / Oracle).
 *
 * <p>See plan 227 (team-task-update) Phase 2 and design
 * {@code nop-ai-agent-actor-runtime-vision.md} §8.2 / §8.3.
 */
public class DbTeamTaskStore implements ITeamTaskStore {

    private static final Logger LOG = LoggerFactory.getLogger(DbTeamTaskStore.class);

    private final DataSource dataSource;
    private final ITenantResolver tenantResolver;

    // ========================================================================
    // Lifecycle
    // ========================================================================

    /**
     * Create a DB-backed team task store and initialize the DB schema (create
     * the {@code ai_agent_team_task} table if absent). Uses the
     * backward-compatible {@link NullTenantResolver}.
     *
     * @param dataSource the JDBC data source; never null
     */
    public DbTeamTaskStore(DataSource dataSource) {
        this(dataSource, NullTenantResolver.INSTANCE);
    }

    /**
     * Create a DB-backed team task store with a contextual tenant resolver
     * (plan 232 / vision §5.1). When the resolver reports a non-null tenant,
     * INSERT writes {@code TENANT_ID} and all SELECT/UPDATE inject the tenant
     * {@code WHERE}; when {@code null}, SQL is byte-identical to the original
     * (zero regression).
     *
     * @param dataSource     the JDBC data source; never null
     * @param tenantResolver the contextual tenant resolver; never null
     */
    public DbTeamTaskStore(DataSource dataSource, ITenantResolver tenantResolver) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
        this.tenantResolver = Objects.requireNonNull(tenantResolver, "tenantResolver must not be null");
        initSchema();
    }

    private String currentTenant() {
        return tenantResolver.resolveTenantId();
    }

    private void initSchema() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(AiAgentTeamTaskTable.DDL_CREATE_TABLE);
        } catch (SQLException e) {
            throw new NopAiAgentException(
                    "DbTeamTaskStore: failed to initialize schema: " + e.getMessage(), e);
        }
    }

    // ========================================================================
    // ITeamTaskStore — create + reads
    // ========================================================================

    @Override
    public TeamTask createTask(String teamId, String subject, String description,
                               List<String> blockedBy, String createdBy) {
        Objects.requireNonNull(teamId, "teamId");
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(blockedBy, "blockedBy");
        Objects.requireNonNull(createdBy, "createdBy");
        if (subject.isEmpty()) {
            throw new IllegalArgumentException(
                    "DbTeamTaskStore.createTask: subject must not be empty");
        }

        String taskId = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        String blockedByCsv = toBlockedByCsv(blockedBy);
        String tenant = currentTenant();

        String sql = "INSERT INTO " + AiAgentTeamTaskTable.TABLE_NAME
                + " (" + AiAgentTeamTaskTable.COL_TASK_ID
                + ", " + AiAgentTeamTaskTable.COL_TEAM_ID
                + ", " + AiAgentTeamTaskTable.COL_SUBJECT
                + ", " + AiAgentTeamTaskTable.COL_DESCRIPTION
                + ", " + AiAgentTeamTaskTable.COL_BLOCKED_BY
                + ", " + AiAgentTeamTaskTable.COL_STATUS
                + ", " + AiAgentTeamTaskTable.COL_CREATED_BY
                + ", " + AiAgentTeamTaskTable.COL_CLAIMED_BY
                + ", " + AiAgentTeamTaskTable.COL_CREATED_AT
                + ", " + AiAgentTeamTaskTable.COL_UPDATED_AT;
        if (tenant != null) {
            sql += ", " + AiAgentTeamTaskTable.COL_TENANT_ID;
        }
        sql += ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?";
        if (tenant != null) {
            sql += ", ?";
        }
        sql += ")";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, taskId);
            ps.setString(2, teamId);
            ps.setString(3, subject);
            ps.setString(4, description);
            ps.setString(5, blockedByCsv);
            ps.setString(6, TeamTaskStatus.CREATED.name());
            ps.setString(7, createdBy);
            // CLAIMED_BY is null at creation.
            ps.setNull(8, java.sql.Types.VARCHAR);
            ps.setLong(9, now);
            ps.setLong(10, now);
            if (tenant != null) {
                ps.setString(11, tenant);
            }
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new NopAiAgentException(
                    "DbTeamTaskStore.createTask: INSERT failed: " + e.getMessage(), e);
        }

        LOG.debug("DbTeamTaskStore.createTask: taskId={}, teamId={}, subject='{}'",
                taskId, teamId, subject);
        return new TeamTask(taskId, teamId, subject, description, blockedBy,
                TeamTaskStatus.CREATED, createdBy, null, now);
    }

    @Override
    public Optional<TeamTask> getTask(String taskId) {
        if (taskId == null) {
            return Optional.empty();
        }
        String sql = selectByColumn(AiAgentTeamTaskTable.COL_TASK_ID);
        String tenant = currentTenant();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, taskId);
            if (tenant != null) {
                ps.setString(2, tenant);
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new NopAiAgentException(
                    "DbTeamTaskStore.getTask: SELECT failed for taskId='" + taskId
                            + "': " + e.getMessage(), e);
        }
    }

    @Override
    public List<TeamTask> getTasksByTeam(String teamId) {
        if (teamId == null) {
            return List.of();
        }
        return selectListByColumn(AiAgentTeamTaskTable.COL_TEAM_ID, teamId);
    }

    @Override
    public List<TeamTask> getTasksByCreator(String createdBy) {
        if (createdBy == null) {
            return List.of();
        }
        return selectListByColumn(AiAgentTeamTaskTable.COL_CREATED_BY, createdBy);
    }

    // ========================================================================
    // ITeamTaskStore — state-machine transitions (conditional UPDATE CAS)
    // ========================================================================

    @Override
    public Optional<TeamTask> claimTask(String taskId, String claimedBy) {
        requireTaskId(taskId);
        Objects.requireNonNull(claimedBy, "claimedBy");

        long now = System.currentTimeMillis();
        String tenant = currentTenant();
        String sql = "UPDATE " + AiAgentTeamTaskTable.TABLE_NAME
                + " SET " + AiAgentTeamTaskTable.COL_STATUS + " = ?, "
                + AiAgentTeamTaskTable.COL_CLAIMED_BY + " = ?, "
                + AiAgentTeamTaskTable.COL_UPDATED_AT + " = ? "
                + "WHERE " + AiAgentTeamTaskTable.COL_TASK_ID + " = ? "
                + "AND " + AiAgentTeamTaskTable.COL_STATUS + " = ?";
        if (tenant != null) {
            sql += TenantSql.whereTenant(AiAgentTeamTaskTable.COL_TENANT_ID);
        }
        String claimTenant = tenant;
        boolean applied = conditionalUpdate(sql, stmt -> {
            stmt.setString(1, TeamTaskStatus.CLAIMED.name());
            stmt.setString(2, claimedBy);
            stmt.setLong(3, now);
            stmt.setString(4, taskId);
            stmt.setString(5, TeamTaskStatus.CREATED.name());
            if (claimTenant != null) {
                stmt.setString(6, claimTenant);
            }
        });
        if (!applied) {
            return Optional.empty();
        }
        return getTask(taskId);
    }

    @Override
    public Optional<TeamTask> completeTask(String taskId, String completedBy) {
        requireTaskId(taskId);
        Objects.requireNonNull(completedBy, "completedBy");

        long now = System.currentTimeMillis();
        String tenant = currentTenant();
        // complete preserves CLAIMED_BY (design 裁定 6) — do not overwrite it.
        String sql = "UPDATE " + AiAgentTeamTaskTable.TABLE_NAME
                + " SET " + AiAgentTeamTaskTable.COL_STATUS + " = ?, "
                + AiAgentTeamTaskTable.COL_UPDATED_AT + " = ? "
                + "WHERE " + AiAgentTeamTaskTable.COL_TASK_ID + " = ? "
                + "AND " + AiAgentTeamTaskTable.COL_STATUS + " = ?";
        if (tenant != null) {
            sql += TenantSql.whereTenant(AiAgentTeamTaskTable.COL_TENANT_ID);
        }
        String completeTenant = tenant;
        boolean applied = conditionalUpdate(sql, stmt -> {
            stmt.setString(1, TeamTaskStatus.COMPLETED.name());
            stmt.setLong(2, now);
            stmt.setString(3, taskId);
            stmt.setString(4, TeamTaskStatus.CLAIMED.name());
            if (completeTenant != null) {
                stmt.setString(5, completeTenant);
            }
        });
        if (!applied) {
            return Optional.empty();
        }
        return getTask(taskId);
    }

    @Override
    public Optional<TeamTask> abandonTask(String taskId, String abandonedBy) {
        requireTaskId(taskId);
        Objects.requireNonNull(abandonedBy, "abandonedBy");

        long now = System.currentTimeMillis();
        String tenant = currentTenant();
        // abandon is legal from CREATED or CLAIMED; CLAIMED_BY preserved.
        String sql = "UPDATE " + AiAgentTeamTaskTable.TABLE_NAME
                + " SET " + AiAgentTeamTaskTable.COL_STATUS + " = ?, "
                + AiAgentTeamTaskTable.COL_UPDATED_AT + " = ? "
                + "WHERE " + AiAgentTeamTaskTable.COL_TASK_ID + " = ? "
                + "AND " + AiAgentTeamTaskTable.COL_STATUS
                + " IN ('" + TeamTaskStatus.CREATED.name() + "','"
                + TeamTaskStatus.CLAIMED.name() + "')";
        if (tenant != null) {
            sql += TenantSql.whereTenant(AiAgentTeamTaskTable.COL_TENANT_ID);
        }
        String abandonTenant = tenant;
        boolean applied = conditionalUpdate(sql, stmt -> {
            stmt.setString(1, TeamTaskStatus.ABANDONED.name());
            stmt.setLong(2, now);
            stmt.setString(3, taskId);
            if (abandonTenant != null) {
                stmt.setString(4, abandonTenant);
            }
        });
        if (!applied) {
            return Optional.empty();
        }
        return getTask(taskId);
    }

    @Override
    public Optional<TeamTask> reclaimTask(String taskId, String reclaimedBy) {
        requireTaskId(taskId);
        Objects.requireNonNull(reclaimedBy, "reclaimedBy");

        long now = System.currentTimeMillis();
        String tenant = currentTenant();
        // reclaim is CLAIMED→CREATED: reset to re-claimable state by clearing
        // CLAIMED_BY (plan 240). Terminal statuses (COMPLETED / ABANDONED)
        // and CREATED are not affected (WHERE STATUS='CLAIMED' guard).
        String sql = "UPDATE " + AiAgentTeamTaskTable.TABLE_NAME
                + " SET " + AiAgentTeamTaskTable.COL_STATUS + " = ?, "
                + AiAgentTeamTaskTable.COL_CLAIMED_BY + " = NULL, "
                + AiAgentTeamTaskTable.COL_UPDATED_AT + " = ? "
                + "WHERE " + AiAgentTeamTaskTable.COL_TASK_ID + " = ? "
                + "AND " + AiAgentTeamTaskTable.COL_STATUS + " = ?";
        if (tenant != null) {
            sql += TenantSql.whereTenant(AiAgentTeamTaskTable.COL_TENANT_ID);
        }
        String reclaimTenant = tenant;
        boolean applied = conditionalUpdate(sql, stmt -> {
            stmt.setString(1, TeamTaskStatus.CREATED.name());
            stmt.setLong(2, now);
            stmt.setString(3, taskId);
            stmt.setString(4, TeamTaskStatus.CLAIMED.name());
            if (reclaimTenant != null) {
                stmt.setString(5, reclaimTenant);
            }
        });
        if (!applied) {
            return Optional.empty();
        }
        return getTask(taskId);
    }

    // ========================================================================
    // Internal helpers
    // ========================================================================

    @FunctionalInterface
    private interface StatementBinder {
        void bind(PreparedStatement ps) throws SQLException;
    }

    /**
     * Execute a conditional UPDATE and return whether exactly one row was
     * affected (the CAS success signal, design 裁定 3). Zero rows means the
     * task is missing or its STATUS is not the expected source state — a
     * CAS failure reported as {@code Optional.empty()} by the caller.
     */
    private boolean conditionalUpdate(String sql, StatementBinder binder) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            binder.bind(ps);
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new NopAiAgentException(
                    "DbTeamTaskStore: conditional UPDATE failed: " + e.getMessage(), e);
        }
    }

    private String selectByColumn(String column) {
        String sql = "SELECT * FROM " + AiAgentTeamTaskTable.TABLE_NAME
                + " WHERE " + column + " = ?";
        String tenant = currentTenant();
        if (tenant != null) {
            sql += TenantSql.whereTenant(AiAgentTeamTaskTable.COL_TENANT_ID);
        }
        return sql;
    }

    private List<TeamTask> selectListByColumn(String column, String value) {
        String sql = selectByColumn(column);
        String tenant = currentTenant();
        List<TeamTask> snapshot = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, value);
            if (tenant != null) {
                ps.setString(2, tenant);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    snapshot.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new NopAiAgentException(
                    "DbTeamTaskStore.selectListByColumn: SELECT failed for " + column
                            + "='" + value + "': " + e.getMessage(), e);
        }
        return Collections.unmodifiableList(snapshot);
    }

    private static TeamTask mapRow(ResultSet rs) throws SQLException {
        String taskId = rs.getString(AiAgentTeamTaskTable.COL_TASK_ID);
        String teamId = rs.getString(AiAgentTeamTaskTable.COL_TEAM_ID);
        String subject = rs.getString(AiAgentTeamTaskTable.COL_SUBJECT);
        String description = rs.getString(AiAgentTeamTaskTable.COL_DESCRIPTION);
        String blockedByCsv = rs.getString(AiAgentTeamTaskTable.COL_BLOCKED_BY);
        String statusName = rs.getString(AiAgentTeamTaskTable.COL_STATUS);
        String createdBy = rs.getString(AiAgentTeamTaskTable.COL_CREATED_BY);
        String claimedBy = rs.getString(AiAgentTeamTaskTable.COL_CLAIMED_BY);
        long createdAt = rs.getLong(AiAgentTeamTaskTable.COL_CREATED_AT);
        return new TeamTask(taskId, teamId, subject, description,
                fromBlockedByCsv(blockedByCsv),
                TeamTaskStatus.valueOf(statusName), createdBy, claimedBy, createdAt);
    }

    private static String toBlockedByCsv(List<String> blockedBy) {
        if (blockedBy == null || blockedBy.isEmpty()) {
            return null;
        }
        return String.join(",", blockedBy);
    }

    private static List<String> fromBlockedByCsv(String csv) {
        if (csv == null || csv.isEmpty()) {
            return Collections.emptyList();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(java.util.stream.Collectors.toUnmodifiableList());
    }

    private static void requireTaskId(String taskId) {
        if (taskId == null || taskId.isEmpty()) {
            throw new NopAiAgentException(
                    "DbTeamTaskStore: taskId must not be null or empty");
        }
    }
}
