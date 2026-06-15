package io.nop.ai.agent.reliability;

/**
 * SQL DDL and constants for the {@code ai_agent_checkpoint} table — the
 * persistence backing for {@code DBCheckpointManager}. Each row records a
 * single recovery-safe checkpoint so any service instance sharing the same DB
 * can retrieve the resume-point watermark (cross-process recovery).
 *
 * <p>The table schema is defined in the ORM model at
 * {@code _vfs/nop/ai/agent/orm/app.orm.xml}. This class holds the concrete DDL
 * used at runtime to auto-create the table.
 *
 * <p><b>Hybrid column layout</b>: 9 scalar columns
 * ({@code WATERMARK} / {@code SESSION_ID} / {@code SEQ} /
 * {@code CHECKPOINT_TIMESTAMP} / {@code CHECKPOINT_TYPE} / {@code TOOL_NAME} /
 * {@code CALL_ID} / {@code MESSAGE_COUNT} / {@code TOKEN_ESTIMATE}) are
 * directly queryable via native SQL (e.g.
 * {@code WHERE SESSION_ID = ? ORDER BY SEQ DESC}). The two long-text fields
 * ({@code INPUT_SUMMARY} / {@code OUTPUT_SUMMARY}) are stored as CLOB because
 * they carry the full tool-call I/O payload (not just a short summary), which
 * can exceed VARCHAR limits for tools like {@code file_write} /
 * {@code file_read}. This mirrors the {@code ai_agent_session} hybrid layout
 * (scalar columns + {@code SESSION_DATA} CLOB) rather than the
 * {@code ai_agent_denial} all-scalar layout.
 *
 * <p><b>{@code SESSION_ID} is nullable</b>: a {@link Checkpoint} may have a
 * null {@code sessionId} (anonymous session). Unlike
 * {@code ai_agent_session} / {@code ai_agent_denial} where
 * {@code SESSION_ID ... NOT NULL}, this column omits {@code NOT NULL} so
 * anonymous checkpoints can still be persisted and retrieved via
 * {@code getCheckpoint(watermark)} PK lookup.
 */
public final class AiAgentCheckpointTable {

    public static final String TABLE_NAME = "ai_agent_checkpoint";

    public static final String COL_WATERMARK = "WATERMARK";
    public static final String COL_SESSION_ID = "SESSION_ID";
    public static final String COL_SEQ = "SEQ";
    public static final String COL_CHECKPOINT_TIMESTAMP = "CHECKPOINT_TIMESTAMP";
    public static final String COL_CHECKPOINT_TYPE = "CHECKPOINT_TYPE";
    public static final String COL_TOOL_NAME = "TOOL_NAME";
    public static final String COL_CALL_ID = "CALL_ID";
    public static final String COL_INPUT_SUMMARY = "INPUT_SUMMARY";
    public static final String COL_OUTPUT_SUMMARY = "OUTPUT_SUMMARY";
    public static final String COL_MESSAGE_COUNT = "MESSAGE_COUNT";
    public static final String COL_TOKEN_ESTIMATE = "TOKEN_ESTIMATE";

    public static final String INDEX_SESSION_SEQ = "IDX_AI_AGENT_CHECKPOINT_SESSION_SEQ";

    public static final String DDL_CREATE_TABLE = ""
            + "CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " ("
            + COL_WATERMARK + " VARCHAR(100) NOT NULL, "
            + COL_SESSION_ID + " VARCHAR(100), "
            + COL_SEQ + " INTEGER NOT NULL, "
            + COL_CHECKPOINT_TIMESTAMP + " BIGINT NOT NULL, "
            + COL_CHECKPOINT_TYPE + " VARCHAR(30) NOT NULL, "
            + COL_TOOL_NAME + " VARCHAR(200), "
            + COL_CALL_ID + " VARCHAR(100), "
            + COL_INPUT_SUMMARY + " CLOB, "
            + COL_OUTPUT_SUMMARY + " CLOB, "
            + COL_MESSAGE_COUNT + " INTEGER NOT NULL, "
            + COL_TOKEN_ESTIMATE + " BIGINT NOT NULL, "
            + "PRIMARY KEY (" + COL_WATERMARK + ")"
            + ")";

    public static final String DDL_CREATE_INDEX = ""
            + "CREATE INDEX IF NOT EXISTS " + INDEX_SESSION_SEQ + " "
            + "ON " + TABLE_NAME + "(" + COL_SESSION_ID + ", " + COL_SEQ + ")";

    private AiAgentCheckpointTable() {
    }
}
