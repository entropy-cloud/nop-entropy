package io.nop.ai.agent.message;

/**
 * SQL DDL and constants for the {@code ai_agent_message} table — the
 * persistence backing for {@code DBMessageService}.
 *
 * <p>The table schema is defined in the ORM model at
 * {@code _vfs/nop/ai/agent/orm/app.orm.xml}. This class holds the concrete DDL
 * used at runtime to auto-create the table.
 *
 * <p>Status lifecycle: {@code PENDING(0)} → {@code CLAIMED(10)} →
 * {@code CONSUMED(20)}. A message in PENDING can be claimed by any
 * {@code DBMessageService} instance sharing the same DB (competing consumers).
 */
public final class AiAgentMessageTable {

    public static final String TABLE_NAME = "ai_agent_message";

    public static final String COL_SID = "SID";
    public static final String COL_TOPIC = "TOPIC";
    public static final String COL_MESSAGE_BODY = "MESSAGE_BODY";
    public static final String COL_STATUS = "STATUS";
    public static final String COL_CONSUMER_ID = "CONSUMER_ID";
    public static final String COL_CREATED_AT = "CREATED_AT";
    public static final String COL_CLAIMED_AT = "CLAIMED_AT";
    public static final String COL_CONSUMED_AT = "CONSUMED_AT";
    /**
     * Multi-tenant isolation column (plan 232 / vision §5.1). Nullable:
     * {@code null} means "no tenant context" (legacy / single-tenant row).
     */
    public static final String COL_TENANT_ID = "TENANT_ID";
    /**
     * Plan 2519 (裁定⑨ / 3.1-C3-021): per-message delivery-attempt counter.
     * Incremented on every claim; a message whose attempts reach the
     * configured redelivery bound is moved to the dead-letter table instead of
     * being re-queued forever (poison-message upper bound, C7-025).
     */
    public static final String COL_DELIVERY_ATTEMPTS = "DELIVERY_ATTEMPTS";
    /**
     * Plan 2519 (裁定⑨ / CL-069): claim generation fencing token. Incremented
     * on every claim; consume/release writes are conditioned on the generation
     * so a stale (old-generation) consumer write is rejected instead of
     * clobbering the newer claim.
     */
    public static final String COL_CLAIM_GEN = "CLAIM_GEN";

    public static final int STATUS_PENDING = 0;
    public static final int STATUS_CLAIMED = 10;
    public static final int STATUS_CONSUMED = 20;
    /** Plan 2519 (裁定⑨): terminal status for poison messages moved to DLQ. */
    public static final int STATUS_DEAD_LETTER = 30;

    public static final String DLQ_TABLE_NAME = "ai_agent_message_dlq";

    public static final String COL_DLQ_SID = "SID";
    public static final String COL_DLQ_TOPIC = "TOPIC";
    public static final String COL_DLQ_MESSAGE_BODY = "MESSAGE_BODY";
    public static final String COL_DLQ_FAIL_REASON = "FAIL_REASON";
    public static final String COL_DLQ_ATTEMPTS = "ATTEMPTS";
    public static final String COL_DLQ_CREATED_AT = "CREATED_AT";
    public static final String COL_DLQ_TENANT_ID = "TENANT_ID";

    public static final String DDL_CREATE_TABLE = ""
            + "CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " ("
            + COL_SID + " VARCHAR(32) NOT NULL, "
            + COL_TOPIC + " VARCHAR(200) NOT NULL, "
            + COL_MESSAGE_BODY + " CLOB NOT NULL, "
            + COL_STATUS + " INTEGER NOT NULL DEFAULT " + STATUS_PENDING + ", "
            + COL_CONSUMER_ID + " VARCHAR(100), "
            + COL_CREATED_AT + " TIMESTAMP NOT NULL, "
            + COL_CLAIMED_AT + " TIMESTAMP, "
            + COL_CONSUMED_AT + " TIMESTAMP, "
            + COL_TENANT_ID + " VARCHAR(100), "
            + COL_DELIVERY_ATTEMPTS + " INTEGER NOT NULL DEFAULT 0, "
            + COL_CLAIM_GEN + " INTEGER NOT NULL DEFAULT 0, "
            + "PRIMARY KEY (" + COL_SID + ")"
            + ")";

    public static final String DDL_CREATE_INDEX = ""
            + "CREATE INDEX IF NOT EXISTS IDX_" + TABLE_NAME + "_TOPIC_STATUS "
            + "ON " + TABLE_NAME + "(" + COL_TOPIC + ", " + COL_STATUS + ")";

    /**
     * Plan 2519 (裁定⑨): dead-letter table for poison messages (self-built
     * JDBC table, same store-layer family as {@link #TABLE_NAME} — zero ORM
     * source-model touch). Each row carries the original payload, the failure
     * reason and the delivery-attempt count at dead-lettering time.
     */
    public static final String DDL_CREATE_DLQ_TABLE = ""
            + "CREATE TABLE IF NOT EXISTS " + DLQ_TABLE_NAME + " ("
            + COL_DLQ_SID + " VARCHAR(32) NOT NULL, "
            + COL_DLQ_TOPIC + " VARCHAR(200) NOT NULL, "
            + COL_DLQ_MESSAGE_BODY + " CLOB NOT NULL, "
            + COL_DLQ_FAIL_REASON + " VARCHAR(500), "
            + COL_DLQ_ATTEMPTS + " INTEGER NOT NULL DEFAULT 0, "
            + COL_DLQ_CREATED_AT + " TIMESTAMP NOT NULL, "
            + COL_DLQ_TENANT_ID + " VARCHAR(100), "
            + "PRIMARY KEY (" + COL_DLQ_SID + ")"
            + ")";

    private AiAgentMessageTable() {
    }
}
