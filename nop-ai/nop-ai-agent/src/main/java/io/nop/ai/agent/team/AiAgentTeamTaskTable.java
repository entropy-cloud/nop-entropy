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
 *   <li>{@code CLAIM_EPOCH} — monotonically increasing claim generation
 *       counter (plan 279 / AR-01). {@code null} until the first
 *       {@code claimTask}; set to {@code COALESCE(CLAIM_EPOCH, 0) + 1} on
 *       every successful {@code claimTask} and <b>preserved across
 *       {@code reclaimTask}</b> (reclaim clears {@code CLAIMED_BY} only, not
 *       the epoch — keeping it monotonic so the next claim's epoch is
 *       strictly larger than any abandoned claim's, which is what closes the
 *       shared-daemon-id double-execution window). {@code completeTask} /
 *       {@code abandonTask} (CLAIMED branch) bind this column in their CAS
 *       WHERE so a stale in-flight dispatcher (holding an old epoch from a
 *       pre-reclaim claim) cannot complete/abandon a task that was reclaimed
 *       and re-claimed by another owner.</li>
 *   <li>{@code CREATED_AT} — epoch ms at creation.</li>
 *   <li>{@code UPDATED_AT} — epoch ms of the most recent status transition
 *       (design 裁定 7; equals CREATED_AT at creation).</li>
 * </ul>
 *
 * <p><b>Schema migration (plan 279)</b>: {@code CLAIM_EPOCH} is added to
 * {@link #DDL_CREATE_TABLE} for fresh tables, and applied idempotently to
 * already-deployed tables via {@link #DDL_ADD_CLAIM_EPOCH} (guarded by a
 * metadata existence check in {@code DbTeamTaskStore.initSchema}, so the
 * ALTER is only issued when the column is absent — portable across H2 /
 * MySQL / Postgres / Oracle, no {@code IF NOT EXISTS} dialect dependency).
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
    /**
     * Claim-generation epoch column (plan 279 / AR-01). Nullable INTEGER:
     * {@code null} until the first {@code claimTask}; set to
     * {@code COALESCE(CLAIM_EPOCH, 0) + 1} on each successful claim and
     * PRESERVED across {@code reclaimTask} (which clears {@code CLAIMED_BY}
     * only — keeping the epoch monotonic so the next claim's epoch is
     * strictly larger than any abandoned claim's). Bound in the
     * {@code completeTask} / {@code abandonTask} CAS WHERE so a stale
     * in-flight dispatcher holding a pre-reclaim epoch cannot complete or
     * abandon a task reclaimed and re-claimed by another owner — closing
     * the shared-daemon-id double-execution window.
     */
    public static final String COL_CLAIM_EPOCH = "CLAIM_EPOCH";
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
            + COL_CLAIM_EPOCH + " INTEGER, "
            + COL_CREATED_AT + " BIGINT NOT NULL, "
            + COL_UPDATED_AT + " BIGINT NOT NULL, "
            + COL_TENANT_ID + " VARCHAR(100), "
            + "PRIMARY KEY (" + COL_TASK_ID + ")"
            + ")";

    /**
     * Idempotent migration that adds {@link #COL_CLAIM_EPOCH} to an
     * already-deployed {@code ai_agent_team_task} table created before
     * plan 279. {@code CREATE TABLE IF NOT EXISTS} does not add columns to a
     * pre-existing table, so this ALTER is required for rolling deployments.
     * It is issued only when a metadata check confirms the column is absent
     * (see {@code DbTeamTaskStore.initSchema}), making it portable across
     * RDBMSes without dialect-specific {@code IF NOT EXISTS} support.
     */
    public static final String DDL_ADD_CLAIM_EPOCH = ""
            + "ALTER TABLE " + TABLE_NAME
            + " ADD COLUMN " + COL_CLAIM_EPOCH + " INTEGER";

    private AiAgentTeamTaskTable() {
    }
}
