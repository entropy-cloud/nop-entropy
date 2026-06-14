package io.nop.ai.agent.security;

/**
 * SQL DDL and constants for the {@code ai_agent_denial} table — the
 * persistence backing for {@code DBDenialLedger}. Each row records a single
 * per-session denial produced at a dispatch-path deny checkpoint
 * (Layer 1 / 2 / 3).
 *
 * <p>The table schema is defined in the ORM model at
 * {@code _vfs/nop/ai/agent/orm/app.orm.xml}. This class holds the concrete DDL
 * used at runtime to auto-create the table.
 *
 * <p>The table uses column storage (one column per {@link DenialRecord} field)
 * rather than a JSON blob, because {@code DenialRecord} contains only simple
 * types (String + enum + long) and the core operations are per-session
 * {@code COUNT} and per-session {@code DELETE}, which are efficient native SQL
 * with column storage.
 */
public final class AiAgentDenialTable {

    public static final String TABLE_NAME = "ai_agent_denial";

    public static final String COL_SID = "SID";
    public static final String COL_SESSION_ID = "SESSION_ID";
    public static final String COL_TOOL_NAME = "TOOL_NAME";
    public static final String COL_LAYER_SOURCE = "LAYER_SOURCE";
    public static final String COL_REASON = "REASON";
    public static final String COL_MATCHED_RULE = "MATCHED_RULE";
    public static final String COL_DENIAL_TIMESTAMP = "DENIAL_TIMESTAMP";
    public static final String COL_CREATED_AT = "CREATED_AT";

    public static final String INDEX_SESSION_ID = "IDX_AI_AGENT_DENIAL_SESSION_ID";

    public static final String DDL_CREATE_TABLE = ""
            + "CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " ("
            + COL_SID + " VARCHAR(32) NOT NULL, "
            + COL_SESSION_ID + " VARCHAR(100) NOT NULL, "
            + COL_TOOL_NAME + " VARCHAR(200), "
            + COL_LAYER_SOURCE + " VARCHAR(50) NOT NULL, "
            + COL_REASON + " VARCHAR(500), "
            + COL_MATCHED_RULE + " VARCHAR(200), "
            + COL_DENIAL_TIMESTAMP + " BIGINT NOT NULL, "
            + COL_CREATED_AT + " TIMESTAMP NOT NULL, "
            + "PRIMARY KEY (" + COL_SID + ")"
            + ")";

    public static final String DDL_CREATE_INDEX = ""
            + "CREATE INDEX IF NOT EXISTS " + INDEX_SESSION_ID + " "
            + "ON " + TABLE_NAME + "(" + COL_SESSION_ID + ")";

    private AiAgentDenialTable() {
    }
}
