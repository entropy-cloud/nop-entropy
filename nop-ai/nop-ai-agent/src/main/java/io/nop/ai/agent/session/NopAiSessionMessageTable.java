package io.nop.ai.agent.session;

/**
 * SQL DDL and column-name constants for the {@code nop_ai_session_message}
 * table — the persistence target for {@link DbModelSwitchedMessageWriter}.
 *
 * <p>The table schema is defined in the ORM model at
 * {@code _vfs/nop/ai/orm/app.orm.xml} (entity {@code NopAiSessionMessage}).
 * This class holds the concrete DDL used at runtime to auto-create the table,
 * mirroring the {@code AiAgentSessionTable} pattern used by
 * {@code DBSessionStore} and the {@code NopAiChatResponseTable} pattern used
 * by {@code DbUsageRecorder}. Column definitions match the generated ORM
 * entity {@code _NopAiSessionMessage} (nop-ai-dao).
 *
 * <p><b>Module boundary</b>: the agent module does not depend on
 * {@code nop-ai-dao}. This class holds local column-name constants rather
 * than referencing the generated {@code _NopAiSessionMessage} entity —
 * consistent with the {@code AiAgentSessionTable} / {@code NopAiChatResponseTable}
 * precedent.
 *
 * <p>This class deliberately holds only static constants (DDL + column-name
 * strings) — no behaviour.
 */
public final class NopAiSessionMessageTable {

    public static final String TABLE_NAME = "nop_ai_session_message";

    public static final String COL_ID = "id";
    public static final String COL_SESSION_ID = "session_id";
    public static final String COL_ROLE = "role";
    public static final String COL_SEQ = "seq";
    public static final String COL_CONTENT = "content";
    public static final String COL_TOOL_DETAILS = "tool_details";
    public static final String COL_REASONING = "reasoning";
    public static final String COL_METADATA = "metadata";
    public static final String COL_PARENT_ID = "parent_id";
    public static final String COL_FINISH_REASON = "finish_reason";
    public static final String COL_VERSION = "version";
    public static final String COL_CREATED_BY = "created_by";
    public static final String COL_CREATE_TIME = "create_time";
    public static final String COL_UPDATED_BY = "updated_by";
    public static final String COL_UPDATE_TIME = "update_time";
    /**
     * Multi-tenant isolation column (plan 232 / vision §5.1). Nullable:
     * {@code null} means "no tenant context" (legacy / single-tenant row).
     */
    public static final String COL_TENANT_ID = "tenant_id";

    public static final String INDEX_UK_SEQ = "uk_nop_ai_session_msg_seq";

    /**
     * Defensive auto-create DDL (idempotent). The canonical schema lives in the
     * ORM model; this mirrors its column definitions so the writer can run
     * standalone against a fresh DB (consistent with
     * {@code NopAiChatResponseTable.DDL_CREATE_TABLE}). Content / metadata /
     * reasoning / tool_details columns are created nullable because they are
     * optional per the ORM model. The unique index
     * {@code uk_nop_ai_session_msg_seq (session_id, seq)} enforces
     * per-session sequence uniqueness.
     */
    public static final String DDL_CREATE_TABLE = ""
            + "CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " ("
            + COL_ID + " VARCHAR(36) NOT NULL, "
            + COL_SESSION_ID + " VARCHAR(36) NOT NULL, "
            + COL_ROLE + " INTEGER NOT NULL, "
            + COL_SEQ + " BIGINT NOT NULL, "
            + COL_CONTENT + " CLOB, "
            + COL_TOOL_DETAILS + " CLOB, "
            + COL_REASONING + " CLOB, "
            + COL_METADATA + " CLOB, "
            + COL_PARENT_ID + " VARCHAR(36), "
            + COL_FINISH_REASON + " VARCHAR(20), "
            + COL_VERSION + " INTEGER NOT NULL, "
            + COL_CREATED_BY + " VARCHAR(50) NOT NULL, "
            + COL_CREATE_TIME + " TIMESTAMP NOT NULL, "
            + COL_UPDATED_BY + " VARCHAR(50) NOT NULL, "
            + COL_UPDATE_TIME + " TIMESTAMP NOT NULL, "
            + COL_TENANT_ID + " VARCHAR(100), "
            + "PRIMARY KEY (" + COL_ID + ")"
            + ")";

    public static final String DDL_CREATE_INDEX_UK_SEQ = ""
            + "CREATE UNIQUE INDEX IF NOT EXISTS " + INDEX_UK_SEQ
            + " ON " + TABLE_NAME + "(" + COL_SESSION_ID + ", " + COL_SEQ + ")";

    private NopAiSessionMessageTable() {
    }
}
