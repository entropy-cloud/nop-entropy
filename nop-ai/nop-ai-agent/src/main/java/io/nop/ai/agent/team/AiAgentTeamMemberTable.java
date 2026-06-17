package io.nop.ai.agent.team;

/**
 * SQL DDL and constants for the {@code ai_agent_team_member} table — the
 * cross-process shared team-member registry backing for {@link DbTeamManager}.
 * Each row records a single member of a team (one-to-many with
 * {@code ai_agent_team} via {@code TEAM_ID}), so that any service instance
 * sharing the same DB can read/add/remove/bind members (plan 230 /
 * L4-team-db-persistence).
 *
 * <p><b>Raw JDBC + Table 常量类（design 裁定 1 / 2）</b>: this table is managed
 * via raw JDBC (not as an ORM entity) — consistent with the module's
 * established DB-persistence pattern. The table is auto-created at
 * {@link DbTeamManager} construction time (see {@code initSchema}). No
 * physical foreign-key constraint is added to {@code TEAM_ID} (logical
 * association only — consistent with {@code ai_agent_team_task.TEAM_ID}
 * which also has no FK); the unique constraint
 * {@code (TEAM_ID, MEMBER_NAME)} enforces {@code addMember} duplicate
 * detection (design 裁定 2).
 *
 * <p><b>Columns</b>:
 * <ul>
 *   <li>{@code TEAM_ID} — the owning team's UUID identity (references
 *       {@code ai_agent_team.TEAM_ID} logically).</li>
 *   <li>{@code MEMBER_NAME} — the unique member identifier within the team
 *       (from {@link TeamMemberSpec#getMemberName()}).</li>
 *   <li>{@code AGENT_MODEL} — the agent configuration/model name (from
 *       {@link TeamMemberSpec#getAgentModel()}). Stored so that
 *       {@link TeamSpec} can be fully rebuilt on read.</li>
 *   <li>{@code ROLE} — the {@link MemberRole} name (LEAD / MEMBER).</li>
 *   <li>{@code SESSION_ID} — the persistent session identity this member is
 *       bound to (nullable; null until {@code bindMemberSession}).</li>
 *   <li>{@code ACTOR_ID} — the runtime Actor identity this member is bound
 *       to (nullable; null until {@code bindMemberSession}).</li>
 *   <li>{@code JOINED_AT} — epoch ms when the member joined the team.</li>
 * </ul>
 *
 * <p><b>Unique constraint</b>: {@code (TEAM_ID, MEMBER_NAME)} — corresponds
 * to {@code addMember}'s duplicate detection. A second INSERT with the same
 * tuple violates the constraint and is translated into a
 * {@code NopAiAgentException} (mirroring {@link InMemoryTeamManager}'s
 * {@code putIfAbsent} duplicate detection).
 */
public final class AiAgentTeamMemberTable {

    public static final String TABLE_NAME = "ai_agent_team_member";

    public static final String COL_TEAM_ID = "TEAM_ID";
    public static final String COL_MEMBER_NAME = "MEMBER_NAME";
    public static final String COL_AGENT_MODEL = "AGENT_MODEL";
    public static final String COL_ROLE = "ROLE";
    public static final String COL_SESSION_ID = "SESSION_ID";
    public static final String COL_ACTOR_ID = "ACTOR_ID";
    public static final String COL_JOINED_AT = "JOINED_AT";
    /**
     * Multi-tenant isolation column (plan 232 / vision §5.1). Nullable:
     * {@code null} means "no tenant context" (legacy / single-tenant row).
     */
    public static final String COL_TENANT_ID = "TENANT_ID";

    public static final String DDL_CREATE_TABLE = ""
            + "CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " ("
            + COL_TEAM_ID + " VARCHAR(100) NOT NULL, "
            + COL_MEMBER_NAME + " VARCHAR(200) NOT NULL, "
            + COL_AGENT_MODEL + " VARCHAR(200) NOT NULL, "
            + COL_ROLE + " VARCHAR(20) NOT NULL, "
            + COL_SESSION_ID + " VARCHAR(200), "
            + COL_ACTOR_ID + " VARCHAR(200), "
            + COL_JOINED_AT + " BIGINT NOT NULL, "
            + COL_TENANT_ID + " VARCHAR(100), "
            + "CONSTRAINT UK_TEAM_MEMBER UNIQUE (" + COL_TEAM_ID + ", " + COL_MEMBER_NAME + ")"
            + ")";

    private AiAgentTeamMemberTable() {
    }
}
