package io.nop.ai.agent.team;

/**
 * SQL DDL and constants for the {@code ai_agent_team_task} table — the
 * cross-process shared task store backing for {@link DbTeamTaskStore}.
 * Each row records a single team task so that any service instance sharing
 * the same DB can read the team's shared task list and drive the team-task
 * state machine (plan 227 / L4-8-team-task-update).
 *
 * <p><b>Raw JDBC + Table 常量类（design 裁定 1）</b>: this table is managed
 * via raw JDBC (not as an ORM entity) — consistent with the module's
 * established DB-persistence pattern ({@code AiAgentSessionLockTable} /
 * {@code DbSessionTakeoverLock}, {@code DBSessionStore}, {@code DBMessageService}).
 * The table is auto-created at {@link DbTeamTaskStore} construction time
 * (see {@code initSchema}), mirroring the {@code DbSessionTakeoverLock}
 * pattern. No ORM / DAO / codegen pipeline is introduced.
 *
 * <p><b>Columns</b>:
 * <ul>
 *   <li>{@code TASK_ID} — PK, UUID generated at creation by the store.</li>
 *   <li>{@code TEAM_ID} — the owning team's UUID identity.</li>
 *   <li>{@code SUBJECT} — short task title.</li>
 *   <li>{@code DESCRIPTION} — optional longer description (nullable).</li>
 *   <li>{@code BLOCKED_BY} — comma-separated dependency task IDs (nullable;
 *       stored verbatim, not resolved in this slice).</li>
 *   <li>{@code STATUS} — the {@link TeamTaskStatus} name (CREATED / CLAIMED
 *       / COMPLETED / ABANDONED). Also the optimistic-lock guard: each
 *       transition is a conditional {@code UPDATE ... WHERE STATUS=?}, so
 *       STATUS doubles as the CAS version (design 裁定 3 / 4 — no separate
 *       VERSION column).</li>
 *   <li>{@code CREATED_BY} — the sessionId of the task creator.</li>
 *   <li>{@code CLAIMED_BY} — the sessionId of the member that claimed the
 *       task (null until CLAIMED; preserved thereafter — design 裁定 6).</li>
 *   <li>{@code CREATED_AT} — epoch ms at creation.</li>
 *   <li>{@code UPDATED_AT} — epoch ms of the most recent status transition
 *       (design 裁定 7; equals CREATED_AT at creation).</li>
 * </ul>
 */
public final class AiAgentTeamTaskTable {

    public static final String TABLE_NAME = "ai_agent_team_task";

    public static final String COL_TASK_ID = "TASK_ID";
    public static final String COL_TEAM_ID = "TEAM_ID";
    public static final String COL_SUBJECT = "SUBJECT";
    public static final String COL_DESCRIPTION = "DESCRIPTION";
    public static final String COL_BLOCKED_BY = "BLOCKED_BY";
    public static final String COL_STATUS = "STATUS";
    public static final String COL_CREATED_BY = "CREATED_BY";
    public static final String COL_CLAIMED_BY = "CLAIMED_BY";
    public static final String COL_CREATED_AT = "CREATED_AT";
    public static final String COL_UPDATED_AT = "UPDATED_AT";
    /**
     * Multi-tenant isolation column (plan 232 / vision §5.1). Nullable:
     * {@code null} means "no tenant context" (legacy / single-tenant row).
     */
    public static final String COL_TENANT_ID = "TENANT_ID";

    public static final String DDL_CREATE_TABLE = ""
            + "CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " ("
            + COL_TASK_ID + " VARCHAR(100) NOT NULL, "
            + COL_TEAM_ID + " VARCHAR(100) NOT NULL, "
            + COL_SUBJECT + " VARCHAR(500) NOT NULL, "
            + COL_DESCRIPTION + " VARCHAR(4000), "
            + COL_BLOCKED_BY + " VARCHAR(4000), "
            + COL_STATUS + " VARCHAR(20) NOT NULL, "
            + COL_CREATED_BY + " VARCHAR(200) NOT NULL, "
            + COL_CLAIMED_BY + " VARCHAR(200), "
            + COL_CREATED_AT + " BIGINT NOT NULL, "
            + COL_UPDATED_AT + " BIGINT NOT NULL, "
            + COL_TENANT_ID + " VARCHAR(100), "
            + "PRIMARY KEY (" + COL_TASK_ID + ")"
            + ")";

    private AiAgentTeamTaskTable() {
    }
}
