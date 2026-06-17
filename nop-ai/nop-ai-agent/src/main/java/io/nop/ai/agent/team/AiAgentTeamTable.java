package io.nop.ai.agent.team;

/**
 * SQL DDL and constants for the {@code ai_agent_team} table — the
 * cross-process shared team registry backing for {@link DbTeamManager}.
 * Each row records a single agent team so that any service instance sharing
 * the same DB can read/create/disband teams and drive the team state machine
 * (plan 230 / L4-team-db-persistence).
 *
 * <p><b>Raw JDBC + Table 常量类（design 裁定 1）</b>: this table is managed
 * via raw JDBC (not as an ORM entity) — consistent with the module's
 * established DB-persistence pattern ({@code AiAgentTeamTaskTable} /
 * {@code DbTeamTaskStore}, {@code AiAgentSessionLockTable} /
 * {@code DbSessionTakeoverLock}). The table is auto-created at
 * {@link DbTeamManager} construction time (see {@code initSchema}), mirroring
 * the {@code DbTeamTaskStore.initSchema} pattern. No ORM / DAO / codegen
 * pipeline is introduced.
 *
 * <p><b>Columns</b>:
 * <ul>
 *   <li>{@code TEAM_ID} — PK, UUID generated at creation by the manager.</li>
 *   <li>{@code TEAM_NAME} — human-readable team name (from
 *       {@link TeamSpec#getTeamName()}).</li>
 *   <li>{@code DESCRIPTION} — optional human-readable description (nullable;
 *       from {@link TeamSpec#getDescription()}).</li>
 *   <li>{@code LEAD_AGENT_NAME} — the member name designated as the team lead
 *       (from {@link TeamSpec#getLeadAgentName()}).</li>
 *   <li>{@code MAX_PARALLEL_MEMBERS} — capacity hint (from
 *       {@link TeamSpec#getMaxParallelMembers()}); recorded but NOT enforced
 *       in the foundational slice (ResourceGuard is a successor).</li>
 *   <li>{@code STATUS} — the {@link TeamStatus} name (CREATED / ACTIVE /
 *       DISBANDED). Doubles as the optimistic-lock guard for the
 *       CREATED→ACTIVE and *→DISBANDED transitions (conditional
 *       {@code UPDATE ... WHERE STATUS=...}, no separate VERSION column).</li>
 *   <li>{@code CREATED_AT} — epoch ms at creation.</li>
 *   <li>{@code DISBANDED_AT} — epoch ms recorded on disband (nullable; null
 *       until the team is disbanded).</li>
 * </ul>
 */
public final class AiAgentTeamTable {

    public static final String TABLE_NAME = "ai_agent_team";

    public static final String COL_TEAM_ID = "TEAM_ID";
    public static final String COL_TEAM_NAME = "TEAM_NAME";
    public static final String COL_DESCRIPTION = "DESCRIPTION";
    public static final String COL_LEAD_AGENT_NAME = "LEAD_AGENT_NAME";
    public static final String COL_MAX_PARALLEL_MEMBERS = "MAX_PARALLEL_MEMBERS";
    public static final String COL_STATUS = "STATUS";
    public static final String COL_CREATED_AT = "CREATED_AT";
    public static final String COL_DISBANDED_AT = "DISBANDED_AT";
    /**
     * Multi-tenant isolation column (plan 232 / vision §5.1). Nullable:
     * {@code null} means "no tenant context" (legacy / single-tenant row).
     */
    public static final String COL_TENANT_ID = "TENANT_ID";

    public static final String DDL_CREATE_TABLE = ""
            + "CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " ("
            + COL_TEAM_ID + " VARCHAR(100) NOT NULL, "
            + COL_TEAM_NAME + " VARCHAR(500) NOT NULL, "
            + COL_DESCRIPTION + " VARCHAR(4000), "
            + COL_LEAD_AGENT_NAME + " VARCHAR(200) NOT NULL, "
            + COL_MAX_PARALLEL_MEMBERS + " INT NOT NULL, "
            + COL_STATUS + " VARCHAR(20) NOT NULL, "
            + COL_CREATED_AT + " BIGINT NOT NULL, "
            + COL_DISBANDED_AT + " BIGINT, "
            + COL_TENANT_ID + " VARCHAR(100), "
            + "PRIMARY KEY (" + COL_TEAM_ID + ")"
            + ")";

    private AiAgentTeamTable() {
    }
}
