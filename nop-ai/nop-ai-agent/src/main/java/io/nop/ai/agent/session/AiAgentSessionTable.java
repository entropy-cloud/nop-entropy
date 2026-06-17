package io.nop.ai.agent.session;

/**
 * SQL DDL and constants for the {@code ai_agent_session} table — the
 * persistence backing for {@code DBSessionStore}. Each row records the full
 * state of a single {@link AgentSession} so any service instance sharing the
 * same DB can load and take over the session (cross-process recovery).
 *
 * <p>The table schema is defined in the ORM model at
 * {@code _vfs/nop/ai/agent/orm/app.orm.xml}. This class holds the concrete DDL
 * used at runtime to auto-create the table.
 *
 * <p><b>Hybrid column layout</b>: scalar queryable columns
 * ({@code SESSION_ID} / {@code AGENT_NAME} / {@code STATUS} / {@code CREATED_AT}
 * / {@code UPDATED_AT}) support future status-based SQL filtering, monitoring,
 * and cleanup. The full session state (messages, metadata, counters, etc.) is
 * stored as a JSON CLOB in {@code SESSION_DATA}, serialized via
 * {@link SessionFileWriter#serialize} and deserialized via
 * {@link SessionFileReader#deserialize} — zero new serialization code, 100%
 * consistent with {@code FileBackedSessionStore}.
 */
public final class AiAgentSessionTable {

    public static final String TABLE_NAME = "ai_agent_session";

    public static final String COL_SESSION_ID = "SESSION_ID";
    public static final String COL_AGENT_NAME = "AGENT_NAME";
    public static final String COL_STATUS = "STATUS";
    public static final String COL_SESSION_DATA = "SESSION_DATA";
    public static final String COL_CREATED_AT = "CREATED_AT";
    public static final String COL_UPDATED_AT = "UPDATED_AT";
    /**
     * Multi-tenant isolation column (plan 232 / vision §5.1). Nullable: a
     * {@code null} value means "no tenant context" (legacy / single-tenant
     * row, visible to all).
     */
    public static final String COL_TENANT_ID = "TENANT_ID";

    public static final String INDEX_STATUS = "IDX_AI_AGENT_SESSION_STATUS";

    public static final String DDL_CREATE_TABLE = ""
            + "CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " ("
            + COL_SESSION_ID + " VARCHAR(100) NOT NULL, "
            + COL_AGENT_NAME + " VARCHAR(200), "
            + COL_STATUS + " VARCHAR(30), "
            + COL_SESSION_DATA + " CLOB NOT NULL, "
            + COL_CREATED_AT + " BIGINT NOT NULL, "
            + COL_UPDATED_AT + " BIGINT NOT NULL, "
            + COL_TENANT_ID + " VARCHAR(100), "
            + "PRIMARY KEY (" + COL_SESSION_ID + ")"
            + ")";

    public static final String DDL_CREATE_INDEX = ""
            + "CREATE INDEX IF NOT EXISTS " + INDEX_STATUS + " "
            + "ON " + TABLE_NAME + "(" + COL_STATUS + ")";

    private AiAgentSessionTable() {
    }
}
