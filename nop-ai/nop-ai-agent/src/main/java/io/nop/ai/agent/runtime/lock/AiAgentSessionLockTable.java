package io.nop.ai.agent.runtime.lock;

/**
 * SQL DDL and constants for the {@code ai_agent_session_lock} table — the
 * cross-process takeover lock backing for {@link DbSessionTakeoverLock}.
 * Each row records the lease state of a single session's takeover lock so
 * any service instance sharing the same DB can coordinate session
 * restoration / execution ownership (plan 221 / L4-8-P4).
 *
 * <p><b>Independent table (design 裁定 1)</b>: the lock lives in its own
 * table (not as added columns on {@code ai_agent_session}) because:
 * <ol>
 *   <li>Locks are temporary runtime state; session rows are persistent
 *       business state — separate concerns.</li>
 *   <li>Releasing a lock is a row {@code DELETE}; a column-based design
 *       would only {@code UPDATE} back to null and leave dead columns.</li>
 *   <li>{@code SESSION_ID} primary key guarantees at most one lock per
 *       session — clear semantics.</li>
 *   <li>Consistent with the raw-JDBC pattern used by
 *       {@code AiAgentSessionTable} / {@code DBSessionStore}.</li>
 * </ol>
 *
 * <p>The table is auto-created at {@link DbSessionTakeoverLock}
 * construction time (see {@code initSchema}), mirroring the
 * {@code DBSessionStore.initSchema} pattern.
 */
public final class AiAgentSessionLockTable {

    public static final String TABLE_NAME = "ai_agent_session_lock";

    public static final String COL_SESSION_ID = "SESSION_ID";
    public static final String COL_LOCK_OWNER = "LOCK_OWNER";
    public static final String COL_LOCK_ACQUIRED_AT = "LOCK_ACQUIRED_AT";
    public static final String COL_LOCK_EXPIRES_AT = "LOCK_EXPIRES_AT";
    /**
     * Multi-tenant isolation column (plan 232 / vision §5.1). Nullable:
     * {@code null} means "no tenant context" (legacy / single-tenant row).
     */
    public static final String COL_TENANT_ID = "TENANT_ID";

    public static final String DDL_CREATE_TABLE = ""
            + "CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " ("
            + COL_SESSION_ID + " VARCHAR(100) NOT NULL, "
            + COL_LOCK_OWNER + " VARCHAR(200) NOT NULL, "
            + COL_LOCK_ACQUIRED_AT + " BIGINT NOT NULL, "
            + COL_LOCK_EXPIRES_AT + " BIGINT NOT NULL, "
            + COL_TENANT_ID + " VARCHAR(100), "
            + "PRIMARY KEY (" + COL_SESSION_ID + ")"
            + ")";

    private AiAgentSessionLockTable() {
    }
}
