package io.nop.ai.agent.team;

import io.nop.ai.agent.engine.NopAiAgentException;
import io.nop.ai.agent.quota.IResourceGuard;
import io.nop.ai.agent.quota.NoOpResourceGuard;
import io.nop.ai.agent.quota.QuotaDecision;
import io.nop.ai.agent.quota.QuotaDimension;
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
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Database-backed functional implementation of {@link ITeamManager} — the
 * opt-in cross-process shared team registry for multi-instance deployments
 * sharing a single DB (plan 230 / L4-team-db-persistence).
 *
 * <p><b>Backing store</b>: two raw-JDBC tables (design 裁定 1 / 2 — see
 * {@link AiAgentTeamTable} and {@link AiAgentTeamMemberTable}).
 * {@code ai_agent_team} holds one row per team; {@code ai_agent_team_member}
 * holds one row per member. Both tables are auto-created at construction via
 * {@link #initSchema} (mirrors the {@code DbTeamTaskStore.initSchema} pattern
 * — no hand-written DDL required from integrators).
 *
 * <p><b>Drop-in opt-in</b>: integrators construct
 * {@code new DbTeamManager(dataSource)} and inject it via
 * {@code DefaultAgentEngine.setTeamManager(...)}. No engine/context code
 * change is required — {@code DbTeamManager implements ITeamManager} is
 * transparent to {@link DefaultTeamAclChecker} and the team tools
 * (design 裁定 7). The shipped default remains {@link NoOpTeamManager}
 * (zero regression).
 *
 * <h2>Snapshot-rebuild read semantics (design 裁定 3)</h2>
 * Unlike {@link InMemoryTeamManager} (which returns live, mutable
 * {@link Team} objects that the manager mutates in place), this DB-backed
 * manager <b>rebuilds a fresh {@link Team} snapshot on every read</b>: each
 * {@link #getTeam} / {@link #getTeamBySession} / {@link #getActiveTeams} /
 * {@link #getMember} call SELECTs the team row + its member rows and
 * reconstructs a new {@link Team} (with a fresh member map). Mutations to a
 * returned {@link Team}/{@link TeamMember} are <strong>not</strong>
 * persisted — all persistence goes through the write methods
 * ({@link #addMember} / {@link #removeMember} / {@link #bindMemberSession} /
 * {@link #disbandTeam}). This satisfies the {@link ITeamManager} contract
 * ("callers must treat returned members as read-only") and mirrors the
 * {@code DbTeamTaskStore} snapshot-rebuild pattern.
 *
 * <h2>State-machine transitions (conditional UPDATE CAS)</h2>
 * <ul>
 *   <li>{@link #bindMemberSession} — after a successful member-binding UPDATE,
 *       a conditional {@code UPDATE ai_agent_team SET STATUS='ACTIVE'
 *       WHERE TEAM_ID=? AND STATUS='CREATED'} drives the exactly-once
 *       CREATED→ACTIVE activation (design 裁定 5, equivalent to
 *       {@link InMemoryTeamManager}'s {@code teams.compute} exactly-once).</li>
 *   <li>{@link #disbandTeam} — conditional
 *       {@code UPDATE ai_agent_team SET STATUS='DISBANDED', DISBANDED_AT=?
 *       WHERE TEAM_ID=? AND STATUS &lt;&gt; 'DISBANDED'} is idempotent (design
 *       裁定 6).</li>
 *   <li>{@link #addMember} — unique-constraint {@code (TEAM_ID, MEMBER_NAME)}
 *       violation is translated into a {@link NopAiAgentException} (duplicate
 *       detection equivalent to {@link InMemoryTeamManager}'s
 *       {@code putIfAbsent}).</li>
 * </ul>
 *
 * <h2>Cross-process sharing &amp; no tenant isolation</h2>
 * Multiple JVM instances pointing at the same DB see each other's teams /
 * members / bindings / disbands. There is <strong>no</strong> tenantId /
 * userId column — teams are visible to all instances (multi-tenant isolation
 * is an explicit successor, design 裁定 / Non-Goals).
 *
 * <p>See plan 230 (L4-team-db-persistence), plan 221/227 (raw JDBC pattern),
 * and plan 223 ({@link ITeamManager} contract).
 */
public class DbTeamManager implements ITeamManager {

    private static final Logger LOG = LoggerFactory.getLogger(DbTeamManager.class);

    private final DataSource dataSource;
    private final ITenantResolver tenantResolver;
    /**
     * Central quota-decision gateway (plan 234 / vision §5.2). Constructor-
     * injected; defaults to {@link NoOpResourceGuard} (always allow = zero
     * regression). Enforced at {@code createTeam} / {@code addMember}
     * ({@link QuotaDimension#TEAM_MEMBERS}) and {@code bindMemberSession}
     * ({@link QuotaDimension#TEAM_PARALLEL_BOUND_MEMBERS}, consuming the
     * persisted {@code TeamRow.maxParallelMembers}).
     */
    private final IResourceGuard resourceGuard;

    // ========================================================================
    // Lifecycle
    // ========================================================================

    /**
     * Create a DB-backed team manager and initialize the DB schema (create
     * the {@code ai_agent_team} + {@code ai_agent_team_member} tables if
     * absent). Uses the backward-compatible {@link NullTenantResolver} (no
     * tenant filtering — teams visible to all instances) and the shipped
     * {@link NoOpResourceGuard} (no quota enforcement).
     *
     * @param dataSource the JDBC data source; never null
     */
    public DbTeamManager(DataSource dataSource) {
        this(dataSource, NullTenantResolver.INSTANCE, NoOpResourceGuard.noOp());
    }

    /**
     * Create a DB-backed team manager with a contextual tenant resolver
     * (plan 232 / vision §5.1) and the shipped {@link NoOpResourceGuard}.
     *
     * @param dataSource     the JDBC data source; never null
     * @param tenantResolver the contextual tenant resolver; never null
     */
    public DbTeamManager(DataSource dataSource, ITenantResolver tenantResolver) {
        this(dataSource, tenantResolver, NoOpResourceGuard.noOp());
    }

    /**
     * Create a DB-backed team manager with a contextual tenant resolver and
     * an explicit quota guard (plan 234 / vision §5.2). When a
     * {@link io.nop.ai.agent.quota.DefaultResourceGuard} is supplied, the
     * manager enforces {@link QuotaDimension#TEAM_MEMBERS} at
     * {@code createTeam}/{@code addMember} and
     * {@link QuotaDimension#TEAM_PARALLEL_BOUND_MEMBERS} at
     * {@code bindMemberSession} (the {@code maxParallelMembers} hint→enforced
     * upgrade, consuming the persisted {@code TeamRow.maxParallelMembers}).
     *
     * @param dataSource     the JDBC data source; never null
     * @param tenantResolver the contextual tenant resolver; never null
     * @param resourceGuard  the quota-decision gateway; {@code null} falls back
     *                       to {@link NoOpResourceGuard}
     */
    public DbTeamManager(DataSource dataSource, ITenantResolver tenantResolver,
                         IResourceGuard resourceGuard) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
        this.tenantResolver = Objects.requireNonNull(tenantResolver, "tenantResolver must not be null");
        this.resourceGuard = resourceGuard != null ? resourceGuard : NoOpResourceGuard.noOp();
        initSchema();
    }

    /**
     * Adjudicate a quota dimension and fail-fast on denial (plan 234, Design
     * Decision §6 — No Silent No-Op).
     */
    private void enforceQuota(QuotaDimension dimension, String scopeKey,
                              int projectedCount, int overrideLimit, String operation) {
        QuotaDecision decision = resourceGuard.checkConcurrent(dimension, scopeKey,
                projectedCount, overrideLimit);
        if (!decision.isAllowed()) {
            throw new NopAiAgentException(
                    "DbTeamManager." + operation + " denied by quota: " + decision.getReason());
        }
    }

    private String currentTenant() {
        return tenantResolver.resolveTenantId();
    }

    private void initSchema() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(AiAgentTeamTable.DDL_CREATE_TABLE);
            stmt.execute(AiAgentTeamMemberTable.DDL_CREATE_TABLE);
        } catch (SQLException e) {
            throw new NopAiAgentException(
                    "DbTeamManager: failed to initialize schema: " + e.getMessage(), e);
        }
    }

    // ========================================================================
    // ITeamManager — createTeam
    // ========================================================================

    @Override
    public Team createTeam(TeamSpec spec) {
        Objects.requireNonNull(spec, "spec");
        String teamId = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();

        // Plan 234: enforce TEAM_MEMBERS quota before INSERT (config-driven;
        // projectedCount = the spec's initial member count).
        enforceQuota(QuotaDimension.TEAM_MEMBERS, teamId,
                spec.getMemberSpecs().size(), 0, "createTeam");

        // INSERT team row (status CREATED, disbandedAt null).
        String tenant = currentTenant();
        String teamSql = "INSERT INTO " + AiAgentTeamTable.TABLE_NAME
                + " (" + AiAgentTeamTable.COL_TEAM_ID
                + ", " + AiAgentTeamTable.COL_TEAM_NAME
                + ", " + AiAgentTeamTable.COL_DESCRIPTION
                + ", " + AiAgentTeamTable.COL_LEAD_AGENT_NAME
                + ", " + AiAgentTeamTable.COL_MAX_PARALLEL_MEMBERS
                + ", " + AiAgentTeamTable.COL_STATUS
                + ", " + AiAgentTeamTable.COL_CREATED_AT
                + ", " + AiAgentTeamTable.COL_DISBANDED_AT;
        if (tenant != null) {
            teamSql += ", " + AiAgentTeamTable.COL_TENANT_ID;
        }
        teamSql += ") VALUES (?, ?, ?, ?, ?, ?, ?, ?";
        if (tenant != null) {
            teamSql += ", ?";
        }
        teamSql += ")";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(teamSql)) {
            ps.setString(1, teamId);
            ps.setString(2, spec.getTeamName());
            ps.setString(3, spec.getDescription());
            ps.setString(4, spec.getLeadAgentName());
            ps.setInt(5, spec.getMaxParallelMembers());
            ps.setString(6, TeamStatus.CREATED.name());
            ps.setLong(7, now);
            ps.setNull(8, java.sql.Types.BIGINT);
            if (tenant != null) {
                ps.setString(9, tenant);
            }
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new NopAiAgentException(
                    "DbTeamManager.createTeam: INSERT team failed: " + e.getMessage(), e);
        }

        // Batch INSERT member rows (SESSION_ID / ACTOR_ID null at creation).
        insertMembers(teamId, spec.getMemberSpecs(), now);

        LOG.debug("DbTeamManager.createTeam: teamId={}, teamName='{}', memberCount={}",
                teamId, spec.getTeamName(), spec.getMemberSpecs().size());

        // Return a fresh snapshot of the just-created team (design 裁定 3).
        return getTeam(teamId).orElseThrow(() -> new NopAiAgentException(
                "DbTeamManager.createTeam: created team not readable: " + teamId));
    }

    private void insertMembers(String teamId, List<TeamMemberSpec> memberSpecs, long joinedAt) {
        if (memberSpecs == null || memberSpecs.isEmpty()) {
            return;
        }
        String tenant = currentTenant();
        String memberSql = "INSERT INTO " + AiAgentTeamMemberTable.TABLE_NAME
                + " (" + AiAgentTeamMemberTable.COL_TEAM_ID
                + ", " + AiAgentTeamMemberTable.COL_MEMBER_NAME
                + ", " + AiAgentTeamMemberTable.COL_AGENT_MODEL
                + ", " + AiAgentTeamMemberTable.COL_ROLE
                + ", " + AiAgentTeamMemberTable.COL_SESSION_ID
                + ", " + AiAgentTeamMemberTable.COL_ACTOR_ID
                + ", " + AiAgentTeamMemberTable.COL_JOINED_AT;
        if (tenant != null) {
            memberSql += ", " + AiAgentTeamMemberTable.COL_TENANT_ID;
        }
        memberSql += ") VALUES (?, ?, ?, ?, ?, ?, ?";
        if (tenant != null) {
            memberSql += ", ?";
        }
        memberSql += ")";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(memberSql)) {
            for (TeamMemberSpec ms : memberSpecs) {
                ps.setString(1, teamId);
                ps.setString(2, ms.getMemberName());
                ps.setString(3, ms.getAgentModel());
                ps.setString(4, ms.getRole().name());
                ps.setNull(5, java.sql.Types.VARCHAR);
                ps.setNull(6, java.sql.Types.VARCHAR);
                ps.setLong(7, joinedAt);
                if (tenant != null) {
                    ps.setString(8, tenant);
                }
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (SQLException e) {
            throw new NopAiAgentException(
                    "DbTeamManager.insertMembers: INSERT members failed for teamId="
                            + teamId + ": " + e.getMessage(), e);
        }
    }

    // ========================================================================
    // ITeamManager — reads (snapshot rebuild)
    // ========================================================================

    @Override
    public Optional<Team> getTeam(String teamId) {
        if (teamId == null) {
            return Optional.empty();
        }
        TeamRow row = selectTeamRow(teamId);
        if (row == null) {
            return Optional.empty();
        }
        return Optional.of(rebuildTeam(row));
    }

    @Override
    public Optional<Team> getTeamBySession(String sessionId) {
        if (sessionId == null) {
            return Optional.empty();
        }
        // design 裁定 4: reverse-lookup via SELECT member table on SESSION_ID
        // (no in-memory reverse index; full-table scan is acceptable for the
        // foundational slice — index/cache optimization is a successor).
        String tenant = currentTenant();
        String sql = "SELECT " + AiAgentTeamMemberTable.COL_TEAM_ID
                + " FROM " + AiAgentTeamMemberTable.TABLE_NAME
                + " WHERE " + AiAgentTeamMemberTable.COL_SESSION_ID + " = ?";
        if (tenant != null) {
            sql += TenantSql.whereTenant(AiAgentTeamMemberTable.COL_TENANT_ID);
        }
        String teamId;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, sessionId);
            if (tenant != null) {
                ps.setString(2, tenant);
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                teamId = rs.getString(AiAgentTeamMemberTable.COL_TEAM_ID);
            }
        } catch (SQLException e) {
            throw new NopAiAgentException(
                    "DbTeamManager.getTeamBySession: SELECT failed for sessionId='"
                            + sessionId + "': " + e.getMessage(), e);
        }
        return getTeam(teamId);
    }

    @Override
    public Collection<Team> getActiveTeams() {
        String tenant = currentTenant();
        String sql = "SELECT * FROM " + AiAgentTeamTable.TABLE_NAME
                + " WHERE " + AiAgentTeamTable.COL_STATUS + " <> ?";
        if (tenant != null) {
            sql += TenantSql.whereTenant(AiAgentTeamTable.COL_TENANT_ID);
        }
        List<Team> active = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, TeamStatus.DISBANDED.name());
            if (tenant != null) {
                ps.setString(2, tenant);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    active.add(rebuildTeam(mapTeamRow(rs)));
                }
            }
        } catch (SQLException e) {
            throw new NopAiAgentException(
                    "DbTeamManager.getActiveTeams: SELECT failed: " + e.getMessage(), e);
        }
        return Collections.unmodifiableList(active);
    }

    @Override
    public Optional<TeamMember> getMember(String teamId, String memberName) {
        if (teamId == null || memberName == null) {
            return Optional.empty();
        }
        MemberRow row = selectMemberRow(teamId, memberName);
        if (row == null) {
            return Optional.empty();
        }
        return Optional.of(rebuildMember(row));
    }

    // ========================================================================
    // ITeamManager — disbandTeam (conditional UPDATE, idempotent)
    // ========================================================================

    @Override
    public Team disbandTeam(String teamId) {
        if (teamId == null) {
            throw new NopAiAgentException(
                    "DbTeamManager.disbandTeam: teamId must not be null");
        }
        // First check existence so a missing team fails fast (matching
        // InMemoryTeamManager.disbandTeam's "team not found" exception). An
        // already-DISBANDED team is idempotent (design 裁定 6).
        TeamRow row = selectTeamRow(teamId);
        if (row == null) {
            throw new NopAiAgentException(
                    "DbTeamManager.disbandTeam: team not found: " + teamId);
        }
        if (row.status != TeamStatus.DISBANDED) {
            long now = System.currentTimeMillis();
            String tenant = currentTenant();
            String sql = "UPDATE " + AiAgentTeamTable.TABLE_NAME
                    + " SET " + AiAgentTeamTable.COL_STATUS + " = ?, "
                    + AiAgentTeamTable.COL_DISBANDED_AT + " = ? "
                    + "WHERE " + AiAgentTeamTable.COL_TEAM_ID + " = ? "
                    + "AND " + AiAgentTeamTable.COL_STATUS + " <> ?";
            if (tenant != null) {
                sql += TenantSql.whereTenant(AiAgentTeamTable.COL_TENANT_ID);
            }
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, TeamStatus.DISBANDED.name());
                ps.setLong(2, now);
                ps.setString(3, teamId);
                ps.setString(4, TeamStatus.DISBANDED.name());
                if (tenant != null) {
                    ps.setString(5, tenant);
                }
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new NopAiAgentException(
                        "DbTeamManager.disbandTeam: UPDATE failed for teamId="
                                + teamId + ": " + e.getMessage(), e);
            }
            LOG.debug("DbTeamManager.disbandTeam: teamId={} transitioned to DISBANDED", teamId);
        }
        // Return a fresh snapshot reflecting the (now) terminal state.
        return getTeam(teamId).orElseThrow(() -> new NopAiAgentException(
                "DbTeamManager.disbandTeam: team not readable after disband: " + teamId));
    }

    // ========================================================================
    // ITeamManager — addMember / removeMember
    // ========================================================================

    @Override
    public TeamMember addMember(String teamId, TeamMemberSpec memberSpec) {
        Objects.requireNonNull(memberSpec, "memberSpec");
        if (teamId == null) {
            throw new NopAiAgentException(
                    "DbTeamManager.addMember: teamId must not be null");
        }
        // design 裁定 6: validate team exists + non-DISBANDED before the DML
        // (matching InMemoryTeamManager.addMember's fail-fast).
        TeamRow row = selectTeamRow(teamId);
        if (row == null) {
            throw new NopAiAgentException(
                    "DbTeamManager.addMember: team not found: " + teamId);
        }
        if (row.status == TeamStatus.DISBANDED) {
            throw new NopAiAgentException(
                    "DbTeamManager.addMember: cannot add member to a disbanded team: "
                            + teamId);
        }
        // Plan 234: enforce TEAM_MEMBERS quota before INSERT (projectedCount =
        // current member count + 1). selectMemberRows is the same snapshot
        // rebuild path used by reads.
        int currentMemberCount = selectMemberRows(teamId).size();
        enforceQuota(QuotaDimension.TEAM_MEMBERS, teamId,
                currentMemberCount + 1, 0, "addMember");
        long now = System.currentTimeMillis();
        String tenant = currentTenant();
        String sql = "INSERT INTO " + AiAgentTeamMemberTable.TABLE_NAME
                + " (" + AiAgentTeamMemberTable.COL_TEAM_ID
                + ", " + AiAgentTeamMemberTable.COL_MEMBER_NAME
                + ", " + AiAgentTeamMemberTable.COL_AGENT_MODEL
                + ", " + AiAgentTeamMemberTable.COL_ROLE
                + ", " + AiAgentTeamMemberTable.COL_SESSION_ID
                + ", " + AiAgentTeamMemberTable.COL_ACTOR_ID
                + ", " + AiAgentTeamMemberTable.COL_JOINED_AT;
        if (tenant != null) {
            sql += ", " + AiAgentTeamMemberTable.COL_TENANT_ID;
        }
        sql += ") VALUES (?, ?, ?, ?, ?, ?, ?";
        if (tenant != null) {
            sql += ", ?";
        }
        sql += ")";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, teamId);
            ps.setString(2, memberSpec.getMemberName());
            ps.setString(3, memberSpec.getAgentModel());
            ps.setString(4, memberSpec.getRole().name());
            ps.setNull(5, java.sql.Types.VARCHAR);
            ps.setNull(6, java.sql.Types.VARCHAR);
            ps.setLong(7, now);
            if (tenant != null) {
                ps.setString(8, tenant);
            }
            ps.executeUpdate();
        } catch (SQLException e) {
            // Unique-constraint (TEAM_ID, MEMBER_NAME) violation → duplicate
            // member, mirroring InMemoryTeamManager's putIfAbsent detection.
            throw new NopAiAgentException(
                    "DbTeamManager.addMember: member already exists or INSERT failed "
                            + "(teamId=" + teamId + ", memberName="
                            + memberSpec.getMemberName() + "): " + e.getMessage(), e);
        }
        LOG.debug("DbTeamManager.addMember: teamId={}, memberName='{}'",
                teamId, memberSpec.getMemberName());
        return new TeamMember(memberSpec, now);
    }

    @Override
    public boolean removeMember(String teamId, String memberName) {
        if (teamId == null || memberName == null) {
            return false;
        }
        String tenant = currentTenant();
        String sql = "DELETE FROM " + AiAgentTeamMemberTable.TABLE_NAME
                + " WHERE " + AiAgentTeamMemberTable.COL_TEAM_ID + " = ? "
                + "AND " + AiAgentTeamMemberTable.COL_MEMBER_NAME + " = ?";
        if (tenant != null) {
            sql += TenantSql.whereTenant(AiAgentTeamMemberTable.COL_TENANT_ID);
        }
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, teamId);
            ps.setString(2, memberName);
            if (tenant != null) {
                ps.setString(3, tenant);
            }
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new NopAiAgentException(
                    "DbTeamManager.removeMember: DELETE failed (teamId=" + teamId
                            + ", memberName=" + memberName + "): " + e.getMessage(), e);
        }
    }

    // ========================================================================
    // ITeamManager — bindMemberSession (UPDATE + conditional activation)
    // ========================================================================

    @Override
    public boolean bindMemberSession(String teamId, String memberName,
                                     String sessionId, String actorId) {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(actorId, "actorId");
        if (teamId == null || memberName == null) {
            return false;
        }
        // design 裁定 6: do NOT validate DISBANDED here — matching
        // InMemoryTeamManager.bindMemberSession's de-facto runtime behaviour
        // (it only checks team == null). A bind on a DISBANDED team succeeds
        // but skips the CREATED→ACTIVE activation (the conditional UPDATE
        // matches 0 rows). The TeamStatus doc-vs-code divergence fix is an
        // independent successor.
        //
        // Plan 234: enforce TEAM_PARALLEL_BOUND_MEMBERS quota before the
        // UPDATE (the maxParallelMembers hint→enforced upgrade). Derive the
        // bound count + the target member's current bound state from the
        // member rows (a member is bound when SESSION_ID is non-null). The
        // limit is the team's persisted maxParallelMembers. Idempotent rebind
        // of an already-bound member does not increase the projected count.
        TeamRow teamRow = selectTeamRow(teamId);
        if (teamRow != null) {
            List<MemberRow> rows = selectMemberRows(teamId);
            int boundCount = 0;
            boolean targetAlreadyBound = false;
            for (MemberRow mr : rows) {
                if (mr.sessionId != null) {
                    boundCount++;
                    if (memberName.equals(mr.memberName)) {
                        targetAlreadyBound = true;
                    }
                }
            }
            int projectedCount = targetAlreadyBound ? boundCount : boundCount + 1;
            enforceQuota(QuotaDimension.TEAM_PARALLEL_BOUND_MEMBERS, teamId,
                    projectedCount, teamRow.maxParallelMembers, "bindMemberSession");
        }
        String tenant = currentTenant();
        String bindSql = "UPDATE " + AiAgentTeamMemberTable.TABLE_NAME
                + " SET " + AiAgentTeamMemberTable.COL_SESSION_ID + " = ?, "
                + AiAgentTeamMemberTable.COL_ACTOR_ID + " = ? "
                + "WHERE " + AiAgentTeamMemberTable.COL_TEAM_ID + " = ? "
                + "AND " + AiAgentTeamMemberTable.COL_MEMBER_NAME + " = ?";
        if (tenant != null) {
            bindSql += TenantSql.whereTenant(AiAgentTeamMemberTable.COL_TENANT_ID);
        }
        boolean bound;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(bindSql)) {
            ps.setString(1, sessionId);
            ps.setString(2, actorId);
            ps.setString(3, teamId);
            ps.setString(4, memberName);
            if (tenant != null) {
                ps.setString(5, tenant);
            }
            bound = ps.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new NopAiAgentException(
                    "DbTeamManager.bindMemberSession: UPDATE member failed (teamId="
                            + teamId + ", memberName=" + memberName + "): "
                            + e.getMessage(), e);
        }
        if (!bound) {
            return false;
        }
        // design 裁定 5: first-binding CREATED→ACTIVE activation via
        // conditional UPDATE. At most one concurrent binding transitions the
        // team (second UPDATE matches 0 rows, idempotent). The activation's
        // affected-row-count does NOT influence the return value — binding
        // success is the contract (activation is a side effect), matching
        // InMemoryTeamManager semantics.
        String activateSql = "UPDATE " + AiAgentTeamTable.TABLE_NAME
                + " SET " + AiAgentTeamTable.COL_STATUS + " = ? "
                + "WHERE " + AiAgentTeamTable.COL_TEAM_ID + " = ? "
                + "AND " + AiAgentTeamTable.COL_STATUS + " = ?";
        if (tenant != null) {
            activateSql += TenantSql.whereTenant(AiAgentTeamTable.COL_TENANT_ID);
        }
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(activateSql)) {
            ps.setString(1, TeamStatus.ACTIVE.name());
            ps.setString(2, teamId);
            ps.setString(3, TeamStatus.CREATED.name());
            if (tenant != null) {
                ps.setString(4, tenant);
            }
            int activated = ps.executeUpdate();
            if (activated == 1) {
                LOG.debug("DbTeamManager.bindMemberSession: teamId={} CREATED→ACTIVE", teamId);
            }
        } catch (SQLException e) {
            throw new NopAiAgentException(
                    "DbTeamManager.bindMemberSession: activation UPDATE failed (teamId="
                            + teamId + "): " + e.getMessage(), e);
        }
        return true;
    }

    // ========================================================================
    // Internal helpers — row types, SELECT, snapshot rebuild
    // ========================================================================

    private static final class TeamRow {
        final String teamId;
        final String teamName;
        final String description;
        final String leadAgentName;
        final int maxParallelMembers;
        final TeamStatus status;
        final long createdAt;
        final Long disbandedAt;

        TeamRow(String teamId, String teamName, String description, String leadAgentName,
                int maxParallelMembers, TeamStatus status, long createdAt, Long disbandedAt) {
            this.teamId = teamId;
            this.teamName = teamName;
            this.description = description;
            this.leadAgentName = leadAgentName;
            this.maxParallelMembers = maxParallelMembers;
            this.status = status;
            this.createdAt = createdAt;
            this.disbandedAt = disbandedAt;
        }
    }

    private static final class MemberRow {
        final String teamId;
        final String memberName;
        final String agentModel;
        final MemberRole role;
        final String sessionId;
        final String actorId;
        final long joinedAt;

        MemberRow(String teamId, String memberName, String agentModel, MemberRole role,
                  String sessionId, String actorId, long joinedAt) {
            this.teamId = teamId;
            this.memberName = memberName;
            this.agentModel = agentModel;
            this.role = role;
            this.sessionId = sessionId;
            this.actorId = actorId;
            this.joinedAt = joinedAt;
        }
    }

    private TeamRow selectTeamRow(String teamId) {
        String tenant = currentTenant();
        String sql = "SELECT * FROM " + AiAgentTeamTable.TABLE_NAME
                + " WHERE " + AiAgentTeamTable.COL_TEAM_ID + " = ?";
        if (tenant != null) {
            sql += TenantSql.whereTenant(AiAgentTeamTable.COL_TENANT_ID);
        }
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, teamId);
            if (tenant != null) {
                ps.setString(2, tenant);
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return mapTeamRow(rs);
            }
        } catch (SQLException e) {
            throw new NopAiAgentException(
                    "DbTeamManager.selectTeamRow: SELECT failed for teamId='"
                            + teamId + "': " + e.getMessage(), e);
        }
    }

    private static TeamRow mapTeamRow(ResultSet rs) throws SQLException {
        return new TeamRow(
                rs.getString(AiAgentTeamTable.COL_TEAM_ID),
                rs.getString(AiAgentTeamTable.COL_TEAM_NAME),
                rs.getString(AiAgentTeamTable.COL_DESCRIPTION),
                rs.getString(AiAgentTeamTable.COL_LEAD_AGENT_NAME),
                rs.getInt(AiAgentTeamTable.COL_MAX_PARALLEL_MEMBERS),
                TeamStatus.valueOf(rs.getString(AiAgentTeamTable.COL_STATUS)),
                rs.getLong(AiAgentTeamTable.COL_CREATED_AT),
                (Long) rs.getObject(AiAgentTeamTable.COL_DISBANDED_AT));
    }

    private List<MemberRow> selectMemberRows(String teamId) {
        String tenant = currentTenant();
        String sql = "SELECT * FROM " + AiAgentTeamMemberTable.TABLE_NAME
                + " WHERE " + AiAgentTeamMemberTable.COL_TEAM_ID + " = ?";
        if (tenant != null) {
            sql += TenantSql.whereTenant(AiAgentTeamMemberTable.COL_TENANT_ID);
        }
        List<MemberRow> rows = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, teamId);
            if (tenant != null) {
                ps.setString(2, tenant);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(mapMemberRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new NopAiAgentException(
                    "DbTeamManager.selectMemberRows: SELECT failed for teamId='"
                            + teamId + "': " + e.getMessage(), e);
        }
        return rows;
    }

    private MemberRow selectMemberRow(String teamId, String memberName) {
        String tenant = currentTenant();
        String sql = "SELECT * FROM " + AiAgentTeamMemberTable.TABLE_NAME
                + " WHERE " + AiAgentTeamMemberTable.COL_TEAM_ID + " = ? "
                + "AND " + AiAgentTeamMemberTable.COL_MEMBER_NAME + " = ?";
        if (tenant != null) {
            sql += TenantSql.whereTenant(AiAgentTeamMemberTable.COL_TENANT_ID);
        }
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, teamId);
            ps.setString(2, memberName);
            if (tenant != null) {
                ps.setString(3, tenant);
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return mapMemberRow(rs);
            }
        } catch (SQLException e) {
            throw new NopAiAgentException(
                    "DbTeamManager.selectMemberRow: SELECT failed (teamId="
                            + teamId + ", memberName=" + memberName + "): "
                            + e.getMessage(), e);
        }
    }

    private static MemberRow mapMemberRow(ResultSet rs) throws SQLException {
        return new MemberRow(
                rs.getString(AiAgentTeamMemberTable.COL_TEAM_ID),
                rs.getString(AiAgentTeamMemberTable.COL_MEMBER_NAME),
                rs.getString(AiAgentTeamMemberTable.COL_AGENT_MODEL),
                MemberRole.valueOf(rs.getString(AiAgentTeamMemberTable.COL_ROLE)),
                rs.getString(AiAgentTeamMemberTable.COL_SESSION_ID),
                rs.getString(AiAgentTeamMemberTable.COL_ACTOR_ID),
                rs.getLong(AiAgentTeamMemberTable.COL_JOINED_AT));
    }

    /**
     * Rebuild a fresh {@link Team} snapshot (design 裁定 3 / 8): reconstruct
     * {@link TeamSpec} from the team + member rows, rebuild the member map,
     * and return a new {@link Team}. The member map is a fresh
     * {@link ConcurrentHashMap} so the caller can safely iterate; mutations to
     * it are not persisted.
     */
    private Team rebuildTeam(TeamRow row) {
        List<MemberRow> memberRows = selectMemberRows(row.teamId);

        ConcurrentMap<String, TeamMember> members = new ConcurrentHashMap<>(memberRows.size());
        // Use an order-preserving spec list keyed by insertion order of rows.
        Map<String, TeamMemberSpec> specMap = new LinkedHashMap<>();
        for (MemberRow mr : memberRows) {
            members.put(mr.memberName, rebuildMember(mr));
            specMap.put(mr.memberName, new TeamMemberSpec(mr.memberName, mr.agentModel, mr.role));
        }
        TeamSpec spec = new TeamSpec(row.teamName, row.description, row.leadAgentName,
                Collections.unmodifiableList(new ArrayList<>(specMap.values())),
                row.maxParallelMembers);

        Team team = new Team(row.teamId, spec, members, row.status, row.createdAt);
        if (row.disbandedAt != null) {
            team.setDisbandedAt(row.disbandedAt);
        }
        return team;
    }

    private static TeamMember rebuildMember(MemberRow row) {
        TeamMember member = new TeamMember(row.memberName, row.role, row.joinedAt);
        if (row.sessionId != null && row.actorId != null) {
            member.bind(row.sessionId, row.actorId);
        }
        return member;
    }
}
