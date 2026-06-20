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
 * {@link #abandonTask} / {@link #reclaimTask}, design 裁定 3 + plan 279
 * claim-epoch binding): each transition is a single <b>conditional
 * {@code UPDATE}</b> on {@code STATUS} (+ {@code CLAIM_EPOCH} for
 * complete/abandon), with the affected-row-count determining success:
 * <ul>
 *   <li>{@code claimTask}: {@code UPDATE ... SET STATUS='CLAIMED',
 *       CLAIMED_BY=?, CLAIM_EPOCH = COALESCE(CLAIM_EPOCH, 0) + 1, UPDATED_AT=?
 *       WHERE TASK_ID=? AND STATUS='CREATED'}. Only one concurrent claimer
 *       wins (the row's STATUS guard means a second claimer's UPDATE matches
 *       0 rows). The epoch increment lives inside the same atomic statement
 *       (no TOCTOU {@code SELECT MAX+1}). The assigned epoch is returned on
 *       the re-read {@link TeamTask}.</li>
 *   <li>{@code completeTask}: {@code UPDATE ... SET STATUS='COMPLETED',
 *       UPDATED_AT=? WHERE TASK_ID=? AND STATUS='CLAIMED' AND CLAIM_EPOCH=?}.
 *       The epoch binds the transition to the owner's claim generation — a
 *       stale in-flight dispatcher holding a pre-reclaim epoch is rejected
 *       (plan 279 / AR-01, closes the shared-daemon-id double-execution
 *       window).</li>
 *   <li>{@code abandonTask}: {@code UPDATE ... SET STATUS='ABANDONED',
 *       UPDATED_AT=? WHERE TASK_ID=? AND ((STATUS='CLAIMED' AND CLAIM_EPOCH=?)
 *       OR STATUS='CREATED')}. Two explicit source predicates: CLAIMED+epoch
 *       (owner binding) or CREATED (epoch-agnostic — a CREATED task has no
 *       owner to bind, so a lead may abandon any unclaimed / reclaimed task).</li>
 *   <li>{@code reclaimTask} (plan 240, augmented by plan 279):
 *       {@code UPDATE ... SET STATUS='CREATED', CLAIMED_BY=NULL,
 *       UPDATED_AT=? WHERE TASK_ID=? AND STATUS='CLAIMED'}. Recovery
 *       transition that un-sticks a CLAIMED task whose claimer has
 *       disappeared, clearing claimedBy but <b>preserving</b> claimEpoch so
 *       the task is re-claimable under a strictly-larger epoch (closing the
 *       shared-daemon-id double-execution window). The recovery daemon
 *       {@code DefaultTeamTaskRecoveryHandler} performs a semantically-
 *       equivalent raw JDBC UPDATE that likewise preserves the epoch.</li>
 * </ul>
 * When {@code executeUpdate() == 1} the transition succeeded and the updated
 * row is re-read via a single SELECT to return the fresh {@link TeamTask};
 * when {@code == 0} the transition is illegal (wrong source status / epoch
 * mismatch) or the task is missing — {@code Optional.empty()} is returned
 * (non-exception control flow, design 裁定 2). No separate {@code VERSION}
 * column is needed because STATUS + CLAIM_EPOCH together form the
 * optimistic-lock guard (design 裁定 4 + plan 279).
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
            migrateClaimEpochColumn(conn);
        } catch (SQLException e) {
            throw new NopAiAgentException(
                    "DbTeamTaskStore: failed to initialize schema: " + e.getMessage(), e);
        }
    }

    /**
     * Idempotently add the {@code CLAIM_EPOCH} column (plan 279 / AR-01) to
     * an already-deployed {@code ai_agent_team_task} table created before
     * this plan. {@code CREATE TABLE IF NOT EXISTS} does not add columns to
     * a pre-existing table, so a rolling deployment against a legacy table
     * would otherwise be missing the column. The column's presence is checked
     * via JDBC metadata so the ALTER is only issued when absent — portable
     * across H2 / MySQL / Postgres / Oracle (no dialect-specific
     * {@code IF NOT EXISTS} dependency).
     */
    private void migrateClaimEpochColumn(Connection conn) throws SQLException {
        if (columnExists(conn, AiAgentTeamTaskTable.TABLE_NAME, AiAgentTeamTaskTable.COL_CLAIM_EPOCH)) {
            return;
        }
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(AiAgentTeamTaskTable.DDL_ADD_CLAIM_EPOCH);
        }
        LOG.info("DbTeamTaskStore: migrated ai_agent_team_task — added CLAIM_EPOCH column (plan 279)");
    }

    /**
     * Check whether a column exists on a table using JDBC metadata, comparing
     * identifiers <b>case-insensitively</b>. This is required because each
     * RDBMS stores unquoted identifiers in its own canonical case (H2 / Oracle
     * store them UPPER-CASE; MySQL-on-Linux and Postgres store them lower-case),
     * while {@link AiAgentTeamTaskTable} declares the names in upper-case
     * constants and the {@code CREATE TABLE} uses unquoted identifiers. A
     * case-sensitive match would therefore mis-detect the freshly-created
     * column (e.g. H2 stores {@code AI_AGENT_TEAM_TASK.CLAIM_EPOCH} but the
     * raw table-name constant is {@code ai_agent_team_task}), causing the
     * migration ALTER to run on a table that already has the column and fail
     * with "Duplicate column name". Querying with {@code null} catalog /
     * schema / table-pattern and filtering case-insensitively here is
     * portable across all supported backends.
     */
    private static boolean columnExists(Connection conn, String tableName, String columnName) throws SQLException {
        String tn = tableName.toUpperCase();
        String cn = columnName.toUpperCase();
        try (ResultSet rs = conn.getMetaData().getColumns(null, null, null, null)) {
            while (rs.next()) {
                String t = rs.getString("TABLE_NAME");
                String c = rs.getString("COLUMN_NAME");
                if (t != null && c != null
                        && t.toUpperCase().equals(tn)
                        && c.toUpperCase().equals(cn)) {
                    return true;
                }
            }
        }
        return false;
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
                + ", " + AiAgentTeamTaskTable.COL_CLAIM_EPOCH
                + ", " + AiAgentTeamTaskTable.COL_CREATED_AT
                + ", " + AiAgentTeamTaskTable.COL_UPDATED_AT;
        if (tenant != null) {
            sql += ", " + AiAgentTeamTaskTable.COL_TENANT_ID;
        }
        sql += ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?";
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
            // CLAIMED_BY and CLAIM_EPOCH are null at creation (CREATED state).
            ps.setNull(8, java.sql.Types.VARCHAR);
            ps.setNull(9, java.sql.Types.INTEGER);
            ps.setLong(10, now);
            ps.setLong(11, now);
            if (tenant != null) {
                ps.setString(12, tenant);
            }
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new NopAiAgentException(
                    "DbTeamTaskStore.createTask: INSERT failed: " + e.getMessage(), e);
        }

        LOG.debug("DbTeamTaskStore.createTask: taskId={}, teamId={}, subject='{}'",
                taskId, teamId, subject);
        return new TeamTask(taskId, teamId, subject, description, blockedBy,
                TeamTaskStatus.CREATED, createdBy, null, null, now);
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
        // claim assigns a fresh monotonically-increasing CLAIM_EPOCH atomically
        // within the same conditional UPDATE (plan 279 / AR-01). The epoch
        // increment MUST live inside this CAS statement — never as a separate
        // SELECT MAX(CLAIM_EPOCH)+1 followed by UPDATE (that would be a TOCTOU
        // race letting two claimers pick the same epoch). COALESCE maps the
        // null CREATED-state epoch to 0 before incrementing, so the first
        // claim of a freshly-created / reclaimed task yields epoch 1.
        String sql = "UPDATE " + AiAgentTeamTaskTable.TABLE_NAME
                + " SET " + AiAgentTeamTaskTable.COL_STATUS + " = ?, "
                + AiAgentTeamTaskTable.COL_CLAIMED_BY + " = ?, "
                + AiAgentTeamTaskTable.COL_CLAIM_EPOCH + " = COALESCE("
                + AiAgentTeamTaskTable.COL_CLAIM_EPOCH + ", 0) + 1, "
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
        // Re-read to return the freshly assigned CLAIM_EPOCH (set by the
        // COALESCE+1 above) along with the rest of the row.
        return getTask(taskId);
    }

    @Override
    public Optional<TeamTask> completeTask(String taskId, String completedBy, Long claimEpoch) {
        requireTaskId(taskId);
        Objects.requireNonNull(completedBy, "completedBy");

        long now = System.currentTimeMillis();
        String tenant = currentTenant();
        // complete preserves CLAIMED_BY (design 裁定 6) — do not overwrite it.
        // The CAS also binds CLAIM_EPOCH (plan 279 / AR-01): only the owner
        // that holds the epoch recorded at claim time may complete. A stale
        // in-flight dispatcher holding an epoch from a pre-reclaim claim sees
        // a different row epoch → 0 rows → empty (CAS failure, not a silent
        // success). A null epoch param never matches a CLAIMED row (which
        // always has a non-null epoch), so an "I don't know the epoch"
        // complete is honestly rejected.
        String sql = "UPDATE " + AiAgentTeamTaskTable.TABLE_NAME
                + " SET " + AiAgentTeamTaskTable.COL_STATUS + " = ?, "
                + AiAgentTeamTaskTable.COL_UPDATED_AT + " = ? "
                + "WHERE " + AiAgentTeamTaskTable.COL_TASK_ID + " = ? "
                + "AND " + AiAgentTeamTaskTable.COL_STATUS + " = ? "
                + "AND " + AiAgentTeamTaskTable.COL_CLAIM_EPOCH + " = ?";
        if (tenant != null) {
            sql += TenantSql.whereTenant(AiAgentTeamTaskTable.COL_TENANT_ID);
        }
        String completeTenant = tenant;
        boolean applied = conditionalUpdate(sql, stmt -> {
            stmt.setString(1, TeamTaskStatus.COMPLETED.name());
            stmt.setLong(2, now);
            stmt.setString(3, taskId);
            stmt.setString(4, TeamTaskStatus.CLAIMED.name());
            if (claimEpoch != null) {
                stmt.setLong(5, claimEpoch);
            } else {
                stmt.setNull(5, java.sql.Types.INTEGER);
            }
            if (completeTenant != null) {
                stmt.setString(6, completeTenant);
            }
        });
        if (!applied) {
            return Optional.empty();
        }
        return getTask(taskId);
    }

    @Override
    public Optional<TeamTask> abandonTask(String taskId, String abandonedBy, Long claimEpoch) {
        requireTaskId(taskId);
        Objects.requireNonNull(abandonedBy, "abandonedBy");

        long now = System.currentTimeMillis();
        String tenant = currentTenant();
        // abandon is NOT a mirror of complete: it has two legal source states
        // expressed as explicit predicates (plan 279 / AR-01):
        //   (a) STATUS='CLAIMED' AND CLAIM_EPOCH=?  — the owner gives up;
        //       the epoch binds the transition to this owner's claim
        //       generation (a stale owner is rejected, same as complete).
        //   (b) STATUS='CREATED' — a lead abandons an unclaimed task, or one
        //       reclaimed back to CREATED. This branch is epoch-agnostic: a
        //       CREATED task has no current owner, so there is no epoch to
        //       bind (reclaim preserves the prior epoch to keep subsequent
        //       claims monotonic, so the CREATED branch must NOT predicate on
        //       CLAIM_EPOCH IS NULL — that would drop reclaimed tasks and break
        //       the lead-CREATED-abandon contract).
        // For the CREATED branch the bound epoch value is irrelevant (the
        // second predicate ignores it); passing null is the honest signal.
        // CLAIMED_BY is preserved (design 裁定 6).
        String sql = "UPDATE " + AiAgentTeamTaskTable.TABLE_NAME
                + " SET " + AiAgentTeamTaskTable.COL_STATUS + " = ?, "
                + AiAgentTeamTaskTable.COL_UPDATED_AT + " = ? "
                + "WHERE " + AiAgentTeamTaskTable.COL_TASK_ID + " = ? "
                + "AND ((" + AiAgentTeamTaskTable.COL_STATUS + " = ? "
                + "AND " + AiAgentTeamTaskTable.COL_CLAIM_EPOCH + " = ?) "
                + "OR " + AiAgentTeamTaskTable.COL_STATUS + " = ?)";
        if (tenant != null) {
            sql += TenantSql.whereTenant(AiAgentTeamTaskTable.COL_TENANT_ID);
        }
        String abandonTenant = tenant;
        boolean applied = conditionalUpdate(sql, stmt -> {
            stmt.setString(1, TeamTaskStatus.ABANDONED.name());
            stmt.setLong(2, now);
            stmt.setString(3, taskId);
            // branch (a): CLAIMED with the owner's epoch
            stmt.setString(4, TeamTaskStatus.CLAIMED.name());
            if (claimEpoch != null) {
                stmt.setLong(5, claimEpoch);
            } else {
                stmt.setNull(5, java.sql.Types.INTEGER);
            }
            // branch (b): CREATED with null epoch (claimEpoch value unused)
            stmt.setString(6, TeamTaskStatus.CREATED.name());
            if (abandonTenant != null) {
                stmt.setString(7, abandonTenant);
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
        // CLAIMED_BY (plan 240). CLAIM_EPOCH is intentionally PRESERVED (not
        // nulled): claimTask assigns the next epoch via
        // COALESCE(CLAIM_EPOCH, 0) + 1, so keeping the prior epoch across a
        // reclaim makes the next claim's epoch strictly larger than the
        // abandoned claim's epoch. Nulling it here would reset the next epoch
        // back to 1 — collapsing it with the stale in-flight owner's epoch and
        // REOPENING the shared-daemon-id double-execution window that plan 279
        // exists to close. The abandon CREATED branch is epoch-agnostic
        // (STATUS='CREATED', no owner to bind), so a preserved epoch does not
        // block a lead's CREATED abandon after reclaim. Terminal statuses
        // (COMPLETED / ABANDONED) and CREATED are not affected (WHERE
        // STATUS='CLAIMED' guard).
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
        Long claimEpoch = rs.getObject(AiAgentTeamTaskTable.COL_CLAIM_EPOCH, Long.class);
        long createdAt = rs.getLong(AiAgentTeamTaskTable.COL_CREATED_AT);
        return new TeamTask(taskId, teamId, subject, description,
                fromBlockedByCsv(blockedByCsv),
                TeamTaskStatus.valueOf(statusName), createdBy, claimedBy, claimEpoch, createdAt);
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
