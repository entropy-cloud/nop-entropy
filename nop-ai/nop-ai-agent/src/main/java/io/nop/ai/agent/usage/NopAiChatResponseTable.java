package io.nop.ai.agent.usage;

/**
 * SQL DDL and constants for the {@code nop_ai_chat_response} table — the
 * persistence target for {@link DbUsageRecorder}. Each row records the usage
 * data of a single LLM call (tokens, duration, model) so per-model token usage
 * is queryable downstream (L2-20 aggregation, L2-22 budget control).
 *
 * <p>The table schema is defined in the ORM model at
 * {@code _vfs/nop/ai/orm/app.orm.xml} (entity {@code NopAiChatResponse}). This
 * class holds the concrete DDL used at runtime to auto-create the table,
 * mirroring the {@code AiAgentSessionTable} pattern used by
 * {@code DBSessionStore}. Column definitions match the generated ORM entity
 * {@code _NopAiChatResponse} (nop-ai-dao). The score columns
 * ({@code correctness_score} etc.) are created nullable and left unwritten by
 * the recorder (they are populated by the evaluation subsystem, not the usage
 * recorder).
 *
 * <p>This class deliberately holds only static constants (DDL + column-name
 * strings) — no behaviour — exactly like {@code AiAgentSessionTable}.
 */
public final class NopAiChatResponseTable {

    public static final String TABLE_NAME = "nop_ai_chat_response";

    public static final String COL_ID = "id";
    public static final String COL_REQUEST_ID = "request_id";
    public static final String COL_SESSION_ID = "session_id";
    public static final String COL_MODEL_ID = "model_id";
    public static final String COL_AI_PROVIDER = "ai_provider";
    public static final String COL_AI_MODEL = "ai_model";
    public static final String COL_RESPONSE_CONTENT = "response_content";
    public static final String COL_RESPONSE_TIMESTAMP = "response_timestamp";
    public static final String COL_PROMPT_TOKENS = "prompt_tokens";
    public static final String COL_COMPLETION_TOKENS = "completion_tokens";
    public static final String COL_RESPONSE_DURATION_MS = "response_duration_ms";
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

    /**
     * Defensive auto-create DDL (idempotent). The canonical schema lives in the
     * ORM model; this mirrors its column definitions so the recorder can run
     * standalone against a fresh DB (consistent with
     * {@code AiAgentSessionTable.DDL_CREATE_TABLE}). Score columns are created
     * nullable because the usage recorder never writes them.
     */
    public static final String DDL_CREATE_TABLE = ""
            + "CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " ("
            + COL_ID + " VARCHAR(100) NOT NULL, "
            + COL_REQUEST_ID + " VARCHAR(100), "
            + COL_SESSION_ID + " VARCHAR(100), "
            + COL_MODEL_ID + " VARCHAR(100), "
            + COL_AI_PROVIDER + " VARCHAR(100), "
            + COL_AI_MODEL + " VARCHAR(200), "
            + COL_RESPONSE_CONTENT + " VARCHAR(4000), "
            + COL_RESPONSE_TIMESTAMP + " TIMESTAMP, "
            + COL_PROMPT_TOKENS + " INTEGER, "
            + COL_COMPLETION_TOKENS + " INTEGER, "
            + COL_RESPONSE_DURATION_MS + " INTEGER, "
            + "correctness_score DECIMAL(20,4), "
            + "performance_score DECIMAL(20,4), "
            + "readability_score DECIMAL(20,4), "
            + "compliance_score DECIMAL(20,4), "
            + COL_VERSION + " INTEGER, "
            + COL_CREATED_BY + " VARCHAR(100), "
            + COL_CREATE_TIME + " TIMESTAMP, "
            + COL_UPDATED_BY + " VARCHAR(100), "
            + COL_UPDATE_TIME + " TIMESTAMP, "
            + COL_TENANT_ID + " VARCHAR(100), "
            + "PRIMARY KEY (" + COL_ID + ")"
            + ")";

    private NopAiChatResponseTable() {
    }
}
