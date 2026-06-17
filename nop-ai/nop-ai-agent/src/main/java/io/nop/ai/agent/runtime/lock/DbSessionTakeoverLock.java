package io.nop.ai.agent.runtime.lock;

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
 * Database-backed functional implementation of {@link ISessionTakeoverLock}
 * — the opt-in cross-process takeover lock for multi-instance deployments
 * sharing a single DB (plan 221 / L4-8-P4).
 *
 * <p><b>Backing store</b>: an independent {@code ai_agent_session_lock}
 * table (design 裁定 1 — see {@link AiAgentSessionLockTable}). Each row is
 * a single session's lock lease: {@code SESSION_ID} (PK — at most one lock
 * per session) + {@code LOCK_OWNER} (the acquiring engine's instanceId) +
 * {@code LOCK_ACQUIRED_AT} (epoch ms) + {@code LOCK_EXPIRES_AT} (epoch ms,
 * = acquired + leaseMs). Auto-created at construction via {@link #initSchema}
 * (mirrors the {@code DBSessionStore.initSchema} pattern).
 *
 * <p><b>CAS acquire</b> ({@link #tryAcquire}): two-step atomic sequence
 * implemented via raw JDBC (no ORM dependency, consistent with
 * {@code DBSessionStore}):
 * <ol>
 *   <li><b>INSERT</b> the new lease row. If the table has no row for this
 *       session, the INSERT succeeds and acquire returns {@code true}.</li>
 *   <li>If INSERT fails with a duplicate-key violation (PK conflict =
 *       {@link SQLIntegrityConstraintViolationException} or SQLState
 *       {@code 23505}), a prior lease exists. The lock is then acquired
 *       via a <b>conditional UPDATE</b>: {@code UPDATE ... SET
 *       LOCK_OWNER=?, LOCK_ACQUIRED_AT=?, LOCK_EXPIRES_AT=? WHERE
 *       SESSION_ID=? AND (LOCK_OWNER=? OR LOCK_EXPIRES_AT <= ?)}.
 *       <ul>
 *         <li>Same-owner renewal: {@code LOCK_OWNER = ?} matches → row
 *             updated → returns {@code true} (idempotent re-acquire /
 *             renew).</li>
 *         <li>Stale-lock preemption: {@code LOCK_EXPIRES_AT <= now} → row
 *             updated → returns {@code true} (passive lease/TTL expiry
 *             fail-safe, design 裁定 2).</li>
 *         <li>Active lock held by a different owner: neither predicate
 *             matches → zero rows updated → returns {@code false} (the
 *             caller decides fail-fast vs skip — non-exception control
 *             flow, design 裁定 3).</li>
 *       </ul>
 *   </li>
 * </ol>
 *
 * <p><b>Conditional release</b> ({@link #release}): a single
 * {@code DELETE ... WHERE SESSION_ID=? AND LOCK_OWNER=?}. Only deletes a
 * row whose owner matches — never releases another owner's lock. Returns
 * {@code true} when a row was actually deleted (held by us); {@code false}
 * when no matching row exists (already released / expired and preempted /
 * never acquired).
 *
 * <p><b>isHeld</b> ({@link #isHeld}): a single
 * {@code SELECT COUNT(*) WHERE SESSION_ID=? AND LOCK_EXPIRES_AT > ?}.
 * Returns {@code true} iff an <em>active</em> (non-expired) lease exists,
 * regardless of owner. Used by {@code restorePendingSessions} to skip
 * sessions already being processed by another instance.
 *
 * <p><b>tryRenew</b> ({@link #tryRenew}): a conditional
 * {@code UPDATE ... SET LOCK_EXPIRES_AT=? WHERE SESSION_ID=? AND
 * LOCK_OWNER=?}. Only succeeds when {@code ownerId} currently holds the
 * lease (active or expired). Reserved for manual / future use — the engine
 * does not auto-call it during ReAct iterations (auto heart-beat renew is
 * an explicit successor — see plan 221 Non-Goals).
 *
 * <p><b>Thread safety</b>: guaranteed by atomic SQL CAS operations
 * (PK uniqueness + conditional UPDATE/DELETE with affected-row counts).
 * Multiple owners may share the same {@link DbSessionTakeoverLock}
 * instance or create independent instances pointing at the same DB — both
 * are safe (the DB row is the single source of truth). H2 is the test
 * backend; production can use any RDBMS that supports the standard SQL
 * above (Postgres / MySQL / Oracle).
 *
 * <p>See plan 221 (L4-8-P4) Phase 2 and design
 * {@code nop-ai-agent-actor-runtime-vision.md} §6.3 / §10 Phase 4.
 */
public class DbSessionTakeoverLock implements ISessionTakeoverLock {

    private static final Logger LOG = LoggerFactory.getLogger(DbSessionTakeoverLock.class);

    private final DataSource dataSource;
    private final ITenantResolver tenantResolver;

    // ========================================================================
    // Lifecycle
    // ========================================================================

    /**
     * Create a DB-backed takeover lock and initialize the DB schema (create
     * the {@code ai_agent_session_lock} table if absent). Uses the
     * backward-compatible {@link NullTenantResolver}.
     *
     * @param dataSource the JDBC data source; never null
     */
    public DbSessionTakeoverLock(DataSource dataSource) {
        this(dataSource, NullTenantResolver.INSTANCE);
    }

    /**
     * Create a DB-backed takeover lock with a contextual tenant resolver
     * (plan 232 / vision §5.1). When the resolver reports a non-null tenant,
     * INSERT writes {@code TENANT_ID} and all SELECT/UPDATE/DELETE inject the
     * tenant {@code WHERE}; when {@code null}, SQL is byte-identical to the
     * original (zero regression).
     *
     * @param dataSource     the JDBC data source; never null
     * @param tenantResolver the contextual tenant resolver; never null
     */
    public DbSessionTakeoverLock(DataSource dataSource, ITenantResolver tenantResolver) {
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
            stmt.execute(AiAgentSessionLockTable.DDL_CREATE_TABLE);
        } catch (SQLException e) {
            throw new NopAiAgentException(
                    "DbSessionTakeoverLock: failed to initialize schema: " + e.getMessage(), e);
        }
    }

    // ========================================================================
    // ISessionTakeoverLock
    // ========================================================================

    @Override
    public boolean tryAcquire(String sessionId, String ownerId, long leaseMs) {
        requireArgument(sessionId, "sessionId");
        requireArgument(ownerId, "ownerId");
        requirePositiveLease(leaseMs);

        long now = System.currentTimeMillis();
        long expiresAt = now + leaseMs;
        String tenant = currentTenant();

        // Step 1: optimistic INSERT. Succeeds when no prior lease row exists.
        String insertSql = "INSERT INTO " + AiAgentSessionLockTable.TABLE_NAME
                + " (" + AiAgentSessionLockTable.COL_SESSION_ID
                + ", " + AiAgentSessionLockTable.COL_LOCK_OWNER
                + ", " + AiAgentSessionLockTable.COL_LOCK_ACQUIRED_AT
                + ", " + AiAgentSessionLockTable.COL_LOCK_EXPIRES_AT;
        if (tenant != null) {
            insertSql += ", " + AiAgentSessionLockTable.COL_TENANT_ID;
        }
        insertSql += ") VALUES (?, ?, ?, ?";
        if (tenant != null) {
            insertSql += ", ?";
        }
        insertSql += ")";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(insertSql)) {
            ps.setString(1, sessionId);
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
                        "DbSessionTakeoverLock.tryAcquire: INSERT failed for session '" + sessionId
                                + "': " + e.getMessage(), e);
            }
            // Duplicate-key → a prior lease row exists. Fall through to the
            // conditional UPDATE (renew / preempt / fail).
        }

        // Step 2: conditional UPDATE. Succeeds when the existing lease is
        // either held by the same owner (renew) OR has expired (preempt).
        // Fails (zero rows) when an active lease is held by a different
        // owner.
        String updateSql = "UPDATE " + AiAgentSessionLockTable.TABLE_NAME
                + " SET " + AiAgentSessionLockTable.COL_LOCK_OWNER + " = ?, "
                + AiAgentSessionLockTable.COL_LOCK_ACQUIRED_AT + " = ?, "
                + AiAgentSessionLockTable.COL_LOCK_EXPIRES_AT + " = ? "
                + "WHERE " + AiAgentSessionLockTable.COL_SESSION_ID + " = ? "
                + "AND (" + AiAgentSessionLockTable.COL_LOCK_OWNER + " = ? "
                + "OR " + AiAgentSessionLockTable.COL_LOCK_EXPIRES_AT + " <= ?)";
        if (tenant != null) {
            updateSql += TenantSql.whereTenant(AiAgentSessionLockTable.COL_TENANT_ID);
        }
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(updateSql)) {
            ps.setString(1, ownerId);
            ps.setLong(2, now);
            ps.setLong(3, expiresAt);
            ps.setString(4, sessionId);
            ps.setString(5, ownerId);
            ps.setLong(6, now);
            if (tenant != null) {
                ps.setString(7, tenant);
            }
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new NopAiAgentException(
                    "DbSessionTakeoverLock.tryAcquire: conditional UPDATE failed for session '"
                            + sessionId + "': " + e.getMessage(), e);
        }
    }

    @Override
    public boolean release(String sessionId, String ownerId) {
        requireArgument(sessionId, "sessionId");
        requireArgument(ownerId, "ownerId");

        String tenant = currentTenant();
        String deleteSql = "DELETE FROM " + AiAgentSessionLockTable.TABLE_NAME
                + " WHERE " + AiAgentSessionLockTable.COL_SESSION_ID + " = ? "
                + "AND " + AiAgentSessionLockTable.COL_LOCK_OWNER + " = ?";
        if (tenant != null) {
            deleteSql += TenantSql.whereTenant(AiAgentSessionLockTable.COL_TENANT_ID);
        }
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(deleteSql)) {
            ps.setString(1, sessionId);
            ps.setString(2, ownerId);
            if (tenant != null) {
                ps.setString(3, tenant);
            }
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new NopAiAgentException(
                    "DbSessionTakeoverLock.release: DELETE failed for session '" + sessionId
                            + "', owner '" + ownerId + "': " + e.getMessage(), e);
        }
    }

    @Override
    public boolean isHeld(String sessionId) {
        requireArgument(sessionId, "sessionId");

        long now = System.currentTimeMillis();
        String tenant = currentTenant();
        String selectSql = "SELECT COUNT(*) FROM " + AiAgentSessionLockTable.TABLE_NAME
                + " WHERE " + AiAgentSessionLockTable.COL_SESSION_ID + " = ? "
                + "AND " + AiAgentSessionLockTable.COL_LOCK_EXPIRES_AT + " > ?";
        if (tenant != null) {
            selectSql += TenantSql.whereTenant(AiAgentSessionLockTable.COL_TENANT_ID);
        }
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(selectSql)) {
            ps.setString(1, sessionId);
            ps.setLong(2, now);
            if (tenant != null) {
                ps.setString(3, tenant);
            }
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            throw new NopAiAgentException(
                    "DbSessionTakeoverLock.isHeld: SELECT failed for session '" + sessionId
                            + "': " + e.getMessage(), e);
        }
    }

    @Override
    public boolean tryRenew(String sessionId, String ownerId, long leaseMs) {
        requireArgument(sessionId, "sessionId");
        requireArgument(ownerId, "ownerId");
        requirePositiveLease(leaseMs);

        long now = System.currentTimeMillis();
        long expiresAt = now + leaseMs;
        String tenant = currentTenant();
        String updateSql = "UPDATE " + AiAgentSessionLockTable.TABLE_NAME
                + " SET " + AiAgentSessionLockTable.COL_LOCK_EXPIRES_AT + " = ?, "
                + AiAgentSessionLockTable.COL_LOCK_ACQUIRED_AT + " = ? "
                + "WHERE " + AiAgentSessionLockTable.COL_SESSION_ID + " = ? "
                + "AND " + AiAgentSessionLockTable.COL_LOCK_OWNER + " = ?";
        if (tenant != null) {
            updateSql += TenantSql.whereTenant(AiAgentSessionLockTable.COL_TENANT_ID);
        }
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(updateSql)) {
            ps.setLong(1, expiresAt);
            ps.setLong(2, now);
            ps.setString(3, sessionId);
            ps.setString(4, ownerId);
            if (tenant != null) {
                ps.setString(5, tenant);
            }
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new NopAiAgentException(
                    "DbSessionTakeoverLock.tryRenew: UPDATE failed for session '" + sessionId
                            + "', owner '" + ownerId + "': " + e.getMessage(), e);
        }
    }

    // ========================================================================
    // Internal helpers
    // ========================================================================

    private static void requireArgument(String value, String name) {
        if (value == null || value.isEmpty()) {
            throw new NopAiAgentException(
                    "DbSessionTakeoverLock: " + name + " must not be null or empty");
        }
    }

    private static void requirePositiveLease(long leaseMs) {
        if (leaseMs <= 0) {
            throw new NopAiAgentException(
                    "DbSessionTakeoverLock: leaseMs must be > 0 (got " + leaseMs + ")");
        }
    }

    /**
     * Detect a primary-key / unique-constraint violation in a portable way.
     * H2 reports SQLState {@code 23505}; the standard SQLSTATE for
     * "integrity constraint violation: unique" is {@code 23505}, which
     * Postgres / H2 / Derby share. MySQL historically uses vendor error
     * code 1062 — we also check the exception type as a fallback.
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
