package io.nop.ai.agent.session;

/**
 * SQL DDL and constants for the {@code ai_agent_session_journal} table — the
 * incremental write-path backing for {@link DBSessionStore} (plan 2295/WI14).
 * Between folds each save appends one row here (the delta since the previous
 * save) instead of rewriting the full-session {@code SESSION_DATA} CLOB; a
 * fold rewrites the CLOB (with a bumped {@code snapshotVersion}) and clears
 * this table's rows for the session.
 *
 * <p><b>Entry shape</b>: {@code ENTRY_JSON} carries one
 * {@link SessionDeltaJournal} entry ({@code baseVersion}/{@code seq}/
 * {@code totalMessageCount}/{@code appendedMessages}/{@code state}). Replay
 * validates {@code baseVersion} against the snapshot's
 * {@code snapshotVersion} and contiguous {@code seq} from 0; the first stale
 * or illegal row ends the replay and the store self-heals by folding the
 * replayed state (CLOB rewrite + row delete).
 *
 * <p><b>Tenant ruling</b>: the primary key is {@code (SESSION_ID, SEQ)} —
 * aligned with the main table's {@code SESSION_ID}-only PK. "Same sessionId
 * across tenants" is an unsupported scenario (a session belongs to exactly
 * one tenant; {@code TenantSql} filtering already scopes every read/write to
 * the active tenant's rows). {@code TENANT_ID} is carried for symmetric
 * filtering with the main table, not as part of the key.
 */
public final class AiAgentSessionJournalTable {

    public static final String TABLE_NAME = "ai_agent_session_journal";

    public static final String COL_SESSION_ID = "SESSION_ID";
    public static final String COL_SEQ = "SEQ";
    public static final String COL_TENANT_ID = "TENANT_ID";
    public static final String COL_BASE_VERSION = "BASE_VERSION";
    public static final String COL_ENTRY_JSON = "ENTRY_JSON";
    public static final String COL_CREATED_AT = "CREATED_AT";

    public static final String DDL_CREATE_TABLE = ""
            + "CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " ("
            + COL_SESSION_ID + " VARCHAR(100) NOT NULL, "
            + COL_SEQ + " BIGINT NOT NULL, "
            + COL_TENANT_ID + " VARCHAR(100), "
            + COL_BASE_VERSION + " BIGINT NOT NULL, "
            + COL_ENTRY_JSON + " CLOB NOT NULL, "
            + COL_CREATED_AT + " BIGINT NOT NULL, "
            + "PRIMARY KEY (" + COL_SESSION_ID + ", " + COL_SEQ + ")"
            + ")";

    private AiAgentSessionJournalTable() {
    }
}
