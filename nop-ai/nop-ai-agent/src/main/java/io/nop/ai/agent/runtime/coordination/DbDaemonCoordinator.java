package io.nop.ai.agent.runtime.coordination;

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
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Statement;
import java.util.Objects;

/**
 * Database-backed functional implementation of {@link IDaemonCoordinator}
 * — the opt-in cross-process team-level scan-lease coordination for
 * multi-instance {@code TeamTaskSchedulerDaemon} deployments sharing a
 * single DB (plan 242 / {@code L4-cross-process-daemon-coordination}).
 *
 * <p><b>Backing store</b>: an independent {@code ai_agent_daemon_coord}
 * table (design 裁定 1 — see {@link AiAgentDaemonCoordTable}). Each row is
 * a single team's scan-lease: {@code TEAM_ID} (PK — at most one lease per
 * team, guaranteeing one scanner per team at a time) + {@code OWNER_ID}
 * (the acquiring daemon's {@code daemonOwnerId}) + {@code ACQUIRED_AT}
 * (epoch ms) + {@code EXPIRES_AT} (epoch ms, = acquired + leaseMs).
 * Auto-created at construction via {@link #initSchema} (mirrors the
 * {@code DbSessionTakeoverLock.initSchema} pattern from plan 221).
 *
 * <p><b>CAS acquire</b> ({@link #tryAcquireScanLease}): two-step atomic
 * sequence implemented via raw JDBC (no ORM dependency, consistent with
 * {@code DbSessionTakeoverLock}):
 * <ol>
 *   <li><b>INSERT</b> the new lease row. If the table has no row for this
 *       team, the INSERT succeeds and acquire returns {@code true}.</li>
 *   <li>If INSERT fails with a duplicate-key violation (PK conflict =
 *       {@link SQLIntegrityConstraintViolationException} or SQLState
 *       {@code 23505}), a prior lease exists. The lease is then acquired
 *       via a <b>conditional UPDATE</b>: {@code UPDATE ... SET OWNER_ID=?,
 *       ACQUIRED_AT=?, EXPIRES_AT=? WHERE TEAM_ID=? AND (OWNER_ID=? OR
 *       EXPIRES_AT <= ?)}.
 *       <ul>
 *         <li>Same-owner renewal: {@code OWNER_ID = ?} matches → row
 *             updated → returns {@code true} (idempotent re-acquire /
 *             renew).</li>
 *         <li>Stale-lease preemption: {@code EXPIRES_AT <= now} → row
 *             updated → returns {@code true} (passive lease/TTL expiry
 *             fail-safe).</li>
 *         <li>Active lease held by a different owner: neither predicate
 *             matches → zero rows updated → returns {@code false} (the
 *             caller skips the team — non-exception control flow).</li>
 *       </ul>
 *   </li>
 * </ol>
 *
 * <p><b>Conditional release</b> ({@link #releaseScanLease}): a single
 * {@code DELETE ... WHERE TEAM_ID=? AND OWNER_ID=?}. Only deletes a row
 * whose owner matches — never releases another owner's lease. Returns
 * {@code true} when a row was actually deleted (held by us); {@code false}
 * when no matching row exists (already released / expired and preempted /
 * never acquired).
 *
 * <p><b>isScanLeaseActive</b> ({@link #isScanLeaseActive}): a single
 * {@code SELECT COUNT(*) WHERE TEAM_ID=? AND EXPIRES_AT > ?}. Returns
 * {@code true} iff an <em>active</em> (non-expired) lease exists,
 * regardless of owner. Used for observability / tests to assert the
 * cross-process coordination state.
 *
 * <p><b>Thread safety</b>: guaranteed by atomic SQL CAS operations
 * (PK uniqueness + conditional UPDATE/DELETE with affected-row counts).
 * Multiple {@link DbDaemonCoordinator} instances may share the same
 * {@link DataSource} or create independent instances pointing at the same
 * DB — both are safe (the DB row is the single source of truth). H2 is
 * the test backend; production can use any RDBMS that supports the
 * standard SQL above (Postgres / MySQL / Oracle).
 *
 * <p><b>Honest failure contract (Minimum Rules #24)</b>: every
 * {@link SQLException} is wrapped in {@link NopAiAgentException} — never
 * swallowed. A {@code false} acquire is an explicit coordination signal
 * (another instance owns the team's scan lease), not a silent skip.
 *
 * <p><b>Correctness floor is unaffected</b>: even if this coordinator
 * fails completely (lease table dropped, all acquires return
 * {@code false}), {@code claimTask} CAS still guarantees no
 * double-dispatch (plan 227 / 240, design 裁定 6).
 *
 * <p>See plan 242 ({@code L4-cross-process-daemon-coordination}) Phase 1
 * and design {@code nop-ai-agent-cross-process-daemon-coordination.md}.
 */
public class DbDaemonCoordinator implements IDaemonCoordinator {

    private static final Logger LOG = LoggerFactory.getLogger(DbDaemonCoordinator.class);

    private final DataSource dataSource;
    private final ITenantResolver tenantResolver;

    // ========================================================================
    // Lifecycle
    // ========================================================================

    /**
     * Create a DB-backed scan-lease coordinator and initialize the DB
     * schema (create the {@code ai_agent_daemon_coord} table if absent).
     * Uses the backward-compatible {@link NullTenantResolver}.
     *
     * @param dataSource the JDBC data source; never null
     */
    public DbDaemonCoordinator(DataSource dataSource) {
        this(dataSource, NullTenantResolver.INSTANCE);
    }

    /**
     * Create a DB-backed scan-lease coordinator with a contextual tenant
     * resolver. When the resolver reports a non-null tenant, INSERT writes
     * {@code TENANT_ID} and all SELECT/UPDATE/DELETE inject the tenant
     * {@code WHERE}; when {@code null}, SQL is byte-identical to the
     * original (zero regression).
     *
     * @param dataSource     the JDBC data source; never null
     * @param tenantResolver the contextual tenant resolver; never null
     */
    public DbDaemonCoordinator(DataSource dataSource, ITenantResolver tenantResolver) {
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
            stmt.execute(AiAgentDaemonCoordTable.DDL_CREATE_TABLE);
        } catch (SQLException e) {
            throw new NopAiAgentException(
                    "DbDaemonCoordinator: failed to initialize schema: " + e.getMessage(), e);
        }
    }

    // ========================================================================
    // IDaemonCoordinator
    // ========================================================================

    @Override
    public boolean tryAcquireScanLease(String teamId, String ownerId, long leaseMs) {
        requireArgument(teamId, "teamId");
        requireArgument(ownerId, "ownerId");
        requirePositiveLease(leaseMs);

        long now = System.currentTimeMillis();
        long expiresAt = now + leaseMs;
        String tenant = currentTenant();

        // Step 1: optimistic INSERT. Succeeds when no prior lease row exists.
        String insertSql = "INSERT INTO " + AiAgentDaemonCoordTable.TABLE_NAME
                + " (" + AiAgentDaemonCoordTable.COL_TEAM_ID
                + ", " + AiAgentDaemonCoordTable.COL_OWNER_ID
                + ", " + AiAgentDaemonCoordTable.COL_ACQUIRED_AT
                + ", " + AiAgentDaemonCoordTable.COL_EXPIRES_AT;
        if (tenant != null) {
            insertSql += ", " + AiAgentDaemonCoordTable.COL_TENANT_ID;
        }
        insertSql += ") VALUES (?, ?, ?, ?";
        if (tenant != null) {
            insertSql += ", ?";
        }
        insertSql += ")";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(insertSql)) {
            ps.setString(1, teamId);
            ps.setString(2, ownerId);
            ps.setLong(3, now);
            ps.setLong(4, expiresAt);
            if (tenant != null) {
                ps.setString(5, tenant);
            }
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            if (!isDuplicateKey(e)) {
                throw new NopAiAgentException(
                        "DbDaemonCoordinator.tryAcquireScanLease: INSERT failed for team '" + teamId
                                + "': " + e.getMessage(), e);
            }
            // Duplicate-key → a prior lease row exists. Fall through to the
            // conditional UPDATE (renew / preempt / fail).
        }

        // Step 2: conditional UPDATE. Succeeds when the existing lease is
        // either held by the same owner (renew) OR has expired (preempt).
        // Fails (zero rows) when an active lease is held by a different
        // owner.
        String updateSql = "UPDATE " + AiAgentDaemonCoordTable.TABLE_NAME
                + " SET " + AiAgentDaemonCoordTable.COL_OWNER_ID + " = ?, "
                + AiAgentDaemonCoordTable.COL_ACQUIRED_AT + " = ?, "
                + AiAgentDaemonCoordTable.COL_EXPIRES_AT + " = ? "
                + "WHERE " + AiAgentDaemonCoordTable.COL_TEAM_ID + " = ? "
                + "AND (" + AiAgentDaemonCoordTable.COL_OWNER_ID + " = ? "
                + "OR " + AiAgentDaemonCoordTable.COL_EXPIRES_AT + " <= ?)";
        if (tenant != null) {
            updateSql += TenantSql.whereTenant(AiAgentDaemonCoordTable.COL_TENANT_ID);
        }
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(updateSql)) {
            ps.setString(1, ownerId);
            ps.setLong(2, now);
            ps.setLong(3, expiresAt);
            ps.setString(4, teamId);
            ps.setString(5, ownerId);
            ps.setLong(6, now);
            if (tenant != null) {
                ps.setString(7, tenant);
            }
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new NopAiAgentException(
                    "DbDaemonCoordinator.tryAcquireScanLease: conditional UPDATE failed for team '"
                            + teamId + "': " + e.getMessage(), e);
        }
    }

    @Override
    public boolean releaseScanLease(String teamId, String ownerId) {
        requireArgument(teamId, "teamId");
        requireArgument(ownerId, "ownerId");

        String tenant = currentTenant();
        String deleteSql = "DELETE FROM " + AiAgentDaemonCoordTable.TABLE_NAME
                + " WHERE " + AiAgentDaemonCoordTable.COL_TEAM_ID + " = ? "
                + "AND " + AiAgentDaemonCoordTable.COL_OWNER_ID + " = ?";
        if (tenant != null) {
            deleteSql += TenantSql.whereTenant(AiAgentDaemonCoordTable.COL_TENANT_ID);
        }
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(deleteSql)) {
            ps.setString(1, teamId);
            ps.setString(2, ownerId);
            if (tenant != null) {
                ps.setString(3, tenant);
            }
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new NopAiAgentException(
                    "DbDaemonCoordinator.releaseScanLease: DELETE failed for team '" + teamId
                            + "', owner '" + ownerId + "': " + e.getMessage(), e);
        }
    }

    @Override
    public boolean isScanLeaseActive(String teamId) {
        requireArgument(teamId, "teamId");

        long now = System.currentTimeMillis();
        String tenant = currentTenant();
        String selectSql = "SELECT COUNT(*) FROM " + AiAgentDaemonCoordTable.TABLE_NAME
                + " WHERE " + AiAgentDaemonCoordTable.COL_TEAM_ID + " = ? "
                + "AND " + AiAgentDaemonCoordTable.COL_EXPIRES_AT + " > ?";
        if (tenant != null) {
            selectSql += TenantSql.whereTenant(AiAgentDaemonCoordTable.COL_TENANT_ID);
        }
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(selectSql)) {
            ps.setString(1, teamId);
            ps.setLong(2, now);
            if (tenant != null) {
                ps.setString(3, tenant);
            }
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            throw new NopAiAgentException(
                    "DbDaemonCoordinator.isScanLeaseActive: SELECT failed for team '" + teamId
                            + "': " + e.getMessage(), e);
        }
    }

    // ========================================================================
    // Internal helpers
    // ========================================================================

    private static void requireArgument(String value, String name) {
        if (value == null || value.isEmpty()) {
            throw new NopAiAgentException(
                    "DbDaemonCoordinator: " + name + " must not be null or empty");
        }
    }

    private static void requirePositiveLease(long leaseMs) {
        if (leaseMs <= 0) {
            throw new NopAiAgentException(
                    "DbDaemonCoordinator: leaseMs must be > 0 (got " + leaseMs + ")");
        }
    }

    /**
     * Detect a primary-key / unique-constraint violation in a portable way.
     * H2 reports SQLState {@code 23505}; the standard SQLSTATE for
     * "integrity constraint violation: unique" is {@code 23505}, which
     * Postgres / H2 / Derby share. MySQL historically uses vendor error
     * code 1062 — we also check the exception type as a fallback. Mirrors
     * {@code DbSessionTakeoverLock.isDuplicateKey} (plan 221).
     */
    private static boolean isDuplicateKey(SQLException e) {
        if (e instanceof SQLIntegrityConstraintViolationException) {
            return true;
        }
        String sqlState = e.getSQLState();
        if ("23505".equals(sqlState)) {
            return true;
        }
        // MySQL vendor code 1062 = ER_DUP_ENTRY
        if (e.getErrorCode() == 1062) {
            return true;
        }
        // Walk the chained causes — some drivers wrap the constraint
        // violation one level deep.
        SQLException next = e.getNextException();
        return next != null && next != e && isDuplicateKey(next);
    }
}
