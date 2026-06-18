package io.nop.ai.agent.runtime.coordination;

/**
 * SQL DDL and constants for the {@code ai_agent_daemon_coord} table — the
 * cross-process team-level scan-lease backing for {@link DbDaemonCoordinator}.
 * Each row records the lease state of a single team's scan lease so any
 * scheduler-daemon instance sharing the same DB can coordinate scan
 * ownership (plan 242 / {@code L4-cross-process-daemon-coordination}).
 *
 * <p><b>Independent table (design 裁定 1)</b>: the scan lease lives in its
 * own table (not as added columns on {@code ai_agent_team} or a reuse of
 * {@code ai_agent_session_lock}) because:
 * <ol>
 *   <li>Scan leases are short-lived runtime coordination state; team rows
 *       are persistent business state and session-takeover locks are a
 *       different domain — separate concerns.</li>
 *   <li>Releasing a lease is a row {@code DELETE}; a column-based design
 *       would only {@code UPDATE} back to null and leave dead columns.</li>
 *   <li>{@code TEAM_ID} primary key guarantees at most one active lease
 *       per team — clear CAS semantics (one scanner per team at a time).</li>
 *   <li>Consistent with the raw-JDBC pattern used by
 *       {@code AiAgentSessionLockTable} / {@code DbSessionTakeoverLock}
 *       (plan 221), which this mirrors.</li>
 * </ol>
 *
 * <p><b>Different key space from {@code ai_agent_session_lock}</b>: this
 * table is keyed by {@code TEAM_ID} (the scan-coordination domain), whereas
 * {@code ai_agent_session_lock} is keyed by {@code SESSION_ID} (the
 * session-takeover domain). The two leases may coexist without
 * interference (design 裁定 3).
 *
 * <p>The table is auto-created at {@link DbDaemonCoordinator} construction
 * time (see {@code initSchema}), mirroring the
 * {@code DbSessionTakeoverLock.initSchema} pattern.
 */
public final class AiAgentDaemonCoordTable {

    public static final String TABLE_NAME = "ai_agent_daemon_coord";

    public static final String COL_TEAM_ID = "TEAM_ID";
    public static final String COL_OWNER_ID = "OWNER_ID";
    public static final String COL_ACQUIRED_AT = "ACQUIRED_AT";
    public static final String COL_EXPIRES_AT = "EXPIRES_AT";
    /**
     * Multi-tenant isolation column. Nullable: {@code null} means "no
     * tenant context" (legacy / single-tenant row), mirroring
     * {@code ai_agent_session_lock.TENANT_ID}.
     */
    public static final String COL_TENANT_ID = "TENANT_ID";

    public static final String DDL_CREATE_TABLE = ""
            + "CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " ("
            + COL_TEAM_ID + " VARCHAR(100) NOT NULL, "
            + COL_OWNER_ID + " VARCHAR(200) NOT NULL, "
            + COL_ACQUIRED_AT + " BIGINT NOT NULL, "
            + COL_EXPIRES_AT + " BIGINT NOT NULL, "
            + COL_TENANT_ID + " VARCHAR(100), "
            + "PRIMARY KEY (" + COL_TEAM_ID + ")"
            + ")";

    private AiAgentDaemonCoordTable() {
    }
}
