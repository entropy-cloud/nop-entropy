package io.nop.ai.agent.session;

import static io.nop.ai.agent.NopAiAgentErrors.ERR_AGENT_INTERNAL_DETAIL;
import static io.nop.ai.agent.NopAiAgentErrors.ARG_DETAIL;
import io.nop.ai.agent.engine.NopAiAgentException;
import io.nop.ai.agent.security.ITenantResolver;
import io.nop.ai.agent.security.NullTenantResolver;
import io.nop.ai.agent.security.TenantSql;
import io.nop.ai.api.chat.messages.ChatMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Database-backed {@link ISessionStore} — the third implementation (sibling of
 * {@link InMemorySessionStore} / {@link FileBackedSessionStore}). Persists
 * {@link AgentSession} state to the {@code ai_agent_session} table so any
 * service instance sharing the same DB can load and take over the session
 * (cross-process recovery — design §1.1).
 *
 * <p><b>Drop-in replacement</b>: the engine / executor dispatch-path wiring
 * is unchanged — selecting {@code DBSessionStore} only changes whether the
 * session state survives across processes sharing a DB. The
 * {@link InMemorySessionStore} default remains the shipped default; this store
 * is registered explicitly via
 * {@code new DefaultAgentEngine(chatService, toolManager, new DBSessionStore(dataSource), ...)}
 * when cross-instance durability is needed.
 *
 * <p><b>Persistence scheme</b>: raw JDBC ({@link DataSource} +
 * {@link PreparedStatement}), consistent with {@code DBDenialLedger} and
 * {@code DBMessageService}. The session state is stored using a hybrid column
 * layout: scalar queryable columns ({@code SESSION_ID} / {@code AGENT_NAME} /
 * {@code STATUS} / {@code CREATED_AT} / {@code UPDATED_AT}) + a full session
 * state JSON CLOB column ({@code SESSION_DATA}). The JSON CLOB is serialized
 * via {@link SessionFileWriter#serialize} and deserialized via
 * {@link SessionFileReader#deserialize} — zero new serialization code, 100%
 * consistent with {@link FileBackedSessionStore}.
 *
 * <p><b>Write-through cache</b>: an in-memory {@code ConcurrentHashMap} mirrors
 * the DB state for read performance. {@code save} writes through to DB and
 * updates the cache atomically. {@code remove} deletes both the DB row and the
 * cache entry. {@code get} cache-miss triggers a lazy DB load.
 *
 * <p><b>Thread safety</b>: guaranteed by DB operations + cache concurrency.
 * {@code save} / {@code get} / {@code remove} DB operations are atomic SQL
 * statements (MERGE / SELECT / DELETE); the cache is a
 * {@code ConcurrentHashMap}. Multiple sessions may access the same store
 * instance concurrently; per-session operations are isolated by
 * {@code WHERE SESSION_ID = ?}.
 */

/** * <p><b>store-layer 边界（审计 AI-2/AI-19 裁定）</b>：自建表/本地文件为引擎内部运行时状态，
 * 保留独立 store 层（不注册 ORM）；租户/软删不适用理由与表清单见
 * {@code ai-dev/design/nop-ai-agent/store-layer-contract.md}。
 */
public class DBSessionStore implements ISessionStore {

    private static final Logger LOG = LoggerFactory.getLogger(DBSessionStore.class);

    static final String PROPS_KEY_AGENT_NAME = "agentName";

    private final DataSource dataSource;
    private final ConcurrentHashMap<String, AgentSession> sessions = new ConcurrentHashMap<>();
    /**
     * Plan 2295 (WI14): persisted-state bookkeeping per session — independent
     * of {@link #sessions} because tenant mode bypasses the cache entirely;
     * a cache-anchored state would make the append path unreachable there.
     * Guarded by the per-session lock from {@link #sessionLocks}.
     */
    private final ConcurrentHashMap<String, SessionPersistenceState> persistedStates = new ConcurrentHashMap<>();
    /**
     * Plan 2295 (WI14): per-session locks serializing the whole
     * diff→write→state-update save sequence (engine has real concurrent
     * writers per session: injectInput/steering caller thread vs the ReAct
     * worker thread).
     */
    private final ConcurrentHashMap<String, Object> sessionLocks = new ConcurrentHashMap<>();
    private final ITenantResolver tenantResolver;

    /**
     * Create a DB-backed session store and initialize the DB schema (create
     * table + index if absent). Uses the backward-compatible
     * {@link NullTenantResolver} (no tenant filtering — all data visible).
     *
     * @param dataSource the JDBC data source; never null
     */
    public DBSessionStore(DataSource dataSource) {
        this(dataSource, NullTenantResolver.INSTANCE);
    }

    /**
     * Create a DB-backed session store with a contextual tenant resolver
     * (plan 232 / vision §5.1). When the resolver reports a non-null tenant,
     * all SQL injects the tenant {@code WHERE} condition, the MERGE key becomes
     * {@code (SESSION_ID, TENANT_ID)}, and the write-through cache is bypassed
     * (a sessionId cache key cannot distinguish tenants). When the resolver
     * reports {@code null}, behaviour is byte-identical to the single-tenant
     * store (zero regression).
     *
     * @param dataSource     the JDBC data source; never null
     * @param tenantResolver the contextual tenant resolver; never null
     */
    public DBSessionStore(DataSource dataSource, ITenantResolver tenantResolver) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
        this.tenantResolver = Objects.requireNonNull(tenantResolver, "tenantResolver must not be null");
        initSchema();
    }

    /**
     * @return the active tenantId for the current thread, or {@code null} when
     *         no tenant context is active (all data visible)
     */
    private String currentTenant() {
        return tenantResolver.resolveTenantId();
    }

    /**
     * Plan 232 (Design Decision 8): when a tenant context is active the
     * write-through cache is bypassed — a sessionId cache key cannot
     * distinguish tenants, so cross-tenant sessionId reuse would leak via the
     * cache. When there is no tenant context the cache is used as before.
     */
    private boolean cacheEnabled() {
        return currentTenant() == null;
    }

    private void initSchema() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(AiAgentSessionTable.DDL_CREATE_TABLE);
            stmt.execute(AiAgentSessionTable.DDL_CREATE_INDEX);
            // Plan 2295 (WI14): incremental write path — self-managed journal
            // table (store-layer boundary: not registered in ORM, see
            // store-layer-contract.md).
            stmt.execute(AiAgentSessionJournalTable.DDL_CREATE_TABLE);
        } catch (SQLException e) {
            throw new NopAiAgentException(ERR_AGENT_INTERNAL_DETAIL, e).param(ARG_DETAIL, "DBSessionStore: failed to initialize schema: " + e.getMessage());
        }
    }

    @Override
    public AgentSession getOrCreate(String sessionId, String agentName) {
        if (cacheEnabled()) {
            AgentSession existing = sessions.get(sessionId);
            if (existing != null) {
                return existing;
            }
        }
        AgentSession loaded = loadFromDb(sessionId);
        if (loaded != null) {
            if (cacheEnabled()) {
                AgentSession raced = putIfAbsent(sessionId, loaded);
                return raced != null ? raced : loaded;
            }
            return loaded;
        }
        AgentSession fresh = AgentSession.create(sessionId, agentName);
        if (cacheEnabled()) {
            AgentSession raced = putIfAbsent(sessionId, fresh);
            return raced != null ? raced : fresh;
        }
        return fresh;
    }

    @Override
    public AgentSession get(String sessionId) {
        if (cacheEnabled()) {
            AgentSession cached = sessions.get(sessionId);
            if (cached != null) {
                return cached;
            }
        }
        AgentSession loaded = loadFromDb(sessionId);
        if (loaded != null && cacheEnabled()) {
            AgentSession raced = putIfAbsent(sessionId, loaded);
            return raced != null ? raced : loaded;
        }
        return loaded;
    }

    @Override
    public void remove(String sessionId) {
        if (cacheEnabled()) {
            sessions.remove(sessionId);
        }
        persistedStates.remove(sessionId);
        sessionLocks.remove(sessionId);
        String tenant = currentTenant();
        String deleteSql = "DELETE FROM " + AiAgentSessionTable.TABLE_NAME
                + " WHERE " + AiAgentSessionTable.COL_SESSION_ID + " = ?";
        String deleteJournalSql = "DELETE FROM " + AiAgentSessionJournalTable.TABLE_NAME
                + " WHERE " + AiAgentSessionJournalTable.COL_SESSION_ID + " = ?";
        if (tenant != null) {
            deleteSql += TenantSql.whereTenant(AiAgentSessionTable.COL_TENANT_ID);
            deleteJournalSql += TenantSql.whereTenant(AiAgentSessionJournalTable.COL_TENANT_ID);
        }
        try (Connection conn = dataSource.getConnection()) {
            // Journal first (plan 2295/WI14): the delta rows must not outlive
            // the snapshot row (a replay against a missing snapshot finds no
            // base and would be meaningless anyway).
            try (PreparedStatement ps = conn.prepareStatement(deleteJournalSql)) {
                ps.setString(1, sessionId);
                if (tenant != null) {
                    ps.setString(2, tenant);
                }
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(deleteSql)) {
                ps.setString(1, sessionId);
                if (tenant != null) {
                    ps.setString(2, tenant);
                }
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw new NopAiAgentException(ERR_AGENT_INTERNAL_DETAIL, e).param(ARG_DETAIL, "DBSessionStore.remove: failed to delete session '" + sessionId
                            + "': " + e.getMessage());
        }
    }

    @Override
    public Collection<AgentSession> getAll() {
        // When a tenant context is active the cache cannot be trusted (it is
        // keyed by sessionId without a tenant dimension), so fall through to
        // a tenant-scoped DB scan. When no tenant context, return the cache
        // (existing single-tenant semantics).
        if (cacheEnabled()) {
            return sessions.values();
        }
        return listAllSessions();
    }

    /**
     * {@inheritDoc}
     * <p>
     * <b>Full DB discovery</b>: {@code SELECT SESSION_DATA FROM ai_agent_session}
     * → each row deserialized via {@link SessionFileReader#deserialize}.
     * Successfully loaded sessions are stored in the in-memory cache so
     * subsequent {@link #get} calls hit the cache (consistent with the
     * cache-on-load behaviour of {@link #get}).
     *
     * <p><b>Corruption isolation</b>: a corrupt or truncated {@code SESSION_DATA}
     * JSON (unparseable, missing required fields) is skipped and logged as a
     * warning, so one corrupt row does not block discovery of the remaining
     * sessions (Minimum Rules #24: the corruption is surfaced via the warning
     * log, not silently swallowed — a subsequent {@link #get} on the same
     * sessionId will still fail fast with {@link NopAiAgentException}).
     *
     * <p><b>Empty table</b>: returns an empty collection (a legitimate "no
     * persisted sessions" state, not an error).
     *
     * @return all sessions persisted in the DB; never null, possibly empty
     */
    @Override
    public Collection<AgentSession> listAllSessions() {
        Collection<AgentSession> discovered = new ArrayList<>();
        String tenant = currentTenant();
        String selectAll = "SELECT " + AiAgentSessionTable.COL_SESSION_DATA
                + " FROM " + AiAgentSessionTable.TABLE_NAME;
        if (tenant != null) {
            selectAll += " WHERE " + AiAgentSessionTable.COL_TENANT_ID + " = ?"
                    + " OR " + AiAgentSessionTable.COL_TENANT_ID + " IS NULL";
        }
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(selectAll)) {
            if (tenant != null) {
                ps.setString(1, tenant);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String json = rs.getString(1);
                    try {
                        // Unified load path (plan 2295/WI14): snapshot + journal
                        // replay — discovery must not return a journal-missing
                        // tail that a later get() would treat as authoritative.
                        SessionFileReader.PersistedSnapshot snapshot = SessionFileReader.deserializeSnapshot(json);
                        AgentSession loaded = snapshot.getSession();
                        registerPersistedState(loaded, loaded.getSessionId(), snapshot.getSnapshotVersion());
                        String sid = loaded.getSessionId();
                        if (cacheEnabled()) {
                            AgentSession raced = sessions.putIfAbsent(sid, loaded);
                            discovered.add(raced != null ? raced : loaded);
                        } else {
                            discovered.add(loaded);
                        }
                    } catch (NopAiAgentException e) {
                        LOG.warn("DBSessionStore.listAllSessions: skipping unreadable "
                                        + "session row (corrupt or truncated JSON)",
                                e);
                    }
                }
            }
        } catch (SQLException e) {
            throw new NopAiAgentException(ERR_AGENT_INTERNAL_DETAIL, e).param(ARG_DETAIL, "DBSessionStore.listAllSessions: failed to select sessions: "
                            + e.getMessage());
        }
        return discovered;
    }

    /**
     * {@inheritDoc}
     * <p>
     * <b>Incremental write path (plan 2295 / WI14)</b>: the save first tries
     * the append fast path — the incoming message list must fingerprint-match
     * the persisted prefix per position (catches guardrail in-place rewrites)
     * and the journal must be below the fold threshold. A matching save
     * INSERTs one journal row (delta messages + scalar state block) and
     * UPDATEs the queryable scalar columns (AGENT_NAME/STATUS/UPDATED_AT —
     * never the CLOB, and never inserting a main row: {@code SESSION_DATA} is
     * NOT NULL, so the UPDATE on a missing row fails loud instead of creating
     * a partial one). Every non-append case folds: full CLOB MERGE with a
     * bumped {@code snapshotVersion} (first) then journal row DELETE (second);
     * a crash between the two leaves stale rows that the replay version check
     * discards and self-heals. A cross-process writer winning the (SESSION_ID,
     * SEQ) insert race degrades the save to a fold (last-writer-wins, same as
     * the pre-WI14 MERGE semantics).
     */
    @Override
    public void save(AgentSession session) {
        if (session == null) {
            throw new NopAiAgentException(ERR_AGENT_INTERNAL_DETAIL).param(ARG_DETAIL, "DBSessionStore.save: session must not be null");
        }
        String sessionId = session.getSessionId();
        Object lock = sessionLock(sessionId);
        synchronized (lock) {
            SessionPersistenceState state = persistedStates.get(sessionId);
            List<ChatMessage> current = session.getMessages();

            if (state != null && state.canAppend() && tryAppend(state, session, current)) {
                if (cacheEnabled()) {
                    sessions.put(sessionId, session);
                }
                return;
            }

            fold(state, session, current);
            if (cacheEnabled()) {
                sessions.put(sessionId, session);
            }
        }
    }

    /**
     * Append fast path: verify the persisted prefix by content fingerprint,
     * then INSERT one journal row + UPDATE the scalar columns. Statement
     * order = journal row first, scalar columns second (self-sufficient data
     * before derived data — the mirror of the fold order). Returns false when
     * the caller must fold.
     */
    private boolean tryAppend(SessionPersistenceState state, AgentSession session, List<ChatMessage> current) {
        int persistedCount = state.prefixFingerprints.size();
        if (current.size() < persistedCount) {
            return false; // shrink → fold
        }
        // O(prefix) fingerprint recompute — the deliberate cost of the append
        // fast path (WI14 trades I/O, not CPU, for determinism).
        for (int i = 0; i < persistedCount; i++) {
            if (ChatMessageFingerprints.fingerprint(current.get(i)) != state.prefixFingerprints.get(i)) {
                return false; // in-place content mutation → fold
            }
        }
        List<ChatMessage> appended = current.subList(persistedCount, current.size());

        String entryJson = SessionDeltaJournal.encodeEntry(state.snapshotVersion, state.journalEntryCount,
                current.size(), appended, session);
        String tenant = currentTenant();
        String insertSql = "INSERT INTO " + AiAgentSessionJournalTable.TABLE_NAME
                + " (" + AiAgentSessionJournalTable.COL_SESSION_ID
                + ", " + AiAgentSessionJournalTable.COL_SEQ
                + ", " + AiAgentSessionJournalTable.COL_TENANT_ID
                + ", " + AiAgentSessionJournalTable.COL_BASE_VERSION
                + ", " + AiAgentSessionJournalTable.COL_ENTRY_JSON
                + ", " + AiAgentSessionJournalTable.COL_CREATED_AT
                + ") VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(insertSql)) {
            ps.setString(1, session.getSessionId());
            ps.setLong(2, state.journalEntryCount);
            ps.setString(3, tenant);
            ps.setLong(4, state.snapshotVersion);
            ps.setString(5, entryJson);
            ps.setLong(6, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            // Cross-process writer won the (SESSION_ID, SEQ) race — degrade to
            // fold (last-writer-wins, the pre-WI14 MERGE semantics) instead of
            // failing the execution.
            LOG.debug("DBSessionStore.tryAppend: journal insert raced with another writer for session '{}' — folding", session.getSessionId(), e);
            return false;
        }

        // Scalar columns second: keep the queryable STATUS/UPDATED_AT truthful
        // without touching the CLOB. UPDATE (not MERGE) — the append path must
        // never create a main row.
        String updateScalars = "UPDATE " + AiAgentSessionTable.TABLE_NAME + " SET "
                + AiAgentSessionTable.COL_AGENT_NAME + " = ?, "
                + AiAgentSessionTable.COL_STATUS + " = ?, "
                + AiAgentSessionTable.COL_UPDATED_AT + " = ? WHERE "
                + AiAgentSessionTable.COL_SESSION_ID + " = ?";
        if (tenant != null) {
            updateScalars += TenantSql.whereTenant(AiAgentSessionTable.COL_TENANT_ID);
        }
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(updateScalars)) {
            ps.setString(1, session.getAgentName());
            ps.setString(2, session.getStatus() != null ? session.getStatus().name() : null);
            ps.setLong(3, session.getUpdatedAt());
            ps.setString(4, session.getSessionId());
            if (tenant != null) {
                ps.setString(5, tenant);
            }
            if (ps.executeUpdate() == 0) {
                // No main row — the append path must never create one.
                throw new NopAiAgentException(ERR_AGENT_INTERNAL_DETAIL).param(ARG_DETAIL, "DBSessionStore.tryAppend: main row missing for session '"
                        + session.getSessionId() + "' (append never creates the base)");
            }
        } catch (SQLException e) {
            throw new NopAiAgentException(ERR_AGENT_INTERNAL_DETAIL, e).param(ARG_DETAIL, "DBSessionStore.tryAppend: failed to update scalar columns for session '"
                    + session.getSessionId() + "': " + e.getMessage());
        }

        state.journalEntryCount++;
        for (ChatMessage message : appended) {
            state.prefixFingerprints.add(ChatMessageFingerprints.fingerprint(message));
        }
        return true;
    }

    /**
     * Fold: full CLOB MERGE (with bumped snapshotVersion) FIRST, journal row
     * DELETE second. Crash between the two leaves stale rows that the replay
     * version check discards and self-heals by folding the replayed state.
     */
    private void fold(SessionPersistenceState state, AgentSession session, List<ChatMessage> current) {
        long newVersion = (state != null ? state.snapshotVersion : 0L) + 1L;
        mergeFullRow(session, newVersion);
        deleteJournalRows(session.getSessionId());
        SessionPersistenceState fresh = state != null ? state : new SessionPersistenceState();
        fresh.resetAfterFold(newVersion, current);
        persistedStates.put(session.getSessionId(), fresh);
    }

    /**
     * The pre-WI14 full-row MERGE, parameterized by the snapshot version
     * stamped into the CLOB.
     */
    private void mergeFullRow(AgentSession session, long snapshotVersion) {
        String json = SessionFileWriter.serialize(session, snapshotVersion);
        String tenant = currentTenant();
        // becomes (SESSION_ID, TENANT_ID) so each tenant has its own session
        // namespace; TENANT_ID is appended as the last VALUES column. When no
        // tenant context, the SQL is byte-identical to the original upsert
        // (KEY (SESSION_ID), no TENANT_ID column) — zero regression.
        String mergeSql;
        if (tenant != null) {
            mergeSql = "MERGE INTO " + AiAgentSessionTable.TABLE_NAME
                    + " (" + AiAgentSessionTable.COL_SESSION_ID
                    + ", " + AiAgentSessionTable.COL_AGENT_NAME
                    + ", " + AiAgentSessionTable.COL_STATUS
                    + ", " + AiAgentSessionTable.COL_SESSION_DATA
                    + ", " + AiAgentSessionTable.COL_CREATED_AT
                    + ", " + AiAgentSessionTable.COL_UPDATED_AT
                    + ", " + AiAgentSessionTable.COL_TENANT_ID
                    + ") KEY (" + AiAgentSessionTable.COL_SESSION_ID
                    + ", " + AiAgentSessionTable.COL_TENANT_ID
                    + ") VALUES (?, ?, ?, ?, ?, ?, ?)";
        } else {
            mergeSql = "MERGE INTO " + AiAgentSessionTable.TABLE_NAME
                    + " (" + AiAgentSessionTable.COL_SESSION_ID
                    + ", " + AiAgentSessionTable.COL_AGENT_NAME
                    + ", " + AiAgentSessionTable.COL_STATUS
                    + ", " + AiAgentSessionTable.COL_SESSION_DATA
                    + ", " + AiAgentSessionTable.COL_CREATED_AT
                    + ", " + AiAgentSessionTable.COL_UPDATED_AT
                    + ") KEY (" + AiAgentSessionTable.COL_SESSION_ID + ") VALUES (?, ?, ?, ?, ?, ?)";
        }
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(mergeSql)) {
            ps.setString(1, session.getSessionId());
            ps.setString(2, session.getAgentName());
            ps.setString(3, session.getStatus() != null ? session.getStatus().name() : null);
            ps.setString(4, json);
            ps.setLong(5, session.getCreatedAt());
            ps.setLong(6, session.getUpdatedAt());
            if (tenant != null) {
                ps.setString(7, tenant);
            }
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new NopAiAgentException(ERR_AGENT_INTERNAL_DETAIL, e).param(ARG_DETAIL, "DBSessionStore.fold: failed to persist session '" + session.getSessionId()
                            + "': " + e.getMessage());
        }
    }

    private void deleteJournalRows(String sessionId) {
        String tenant = currentTenant();
        String deleteSql = "DELETE FROM " + AiAgentSessionJournalTable.TABLE_NAME
                + " WHERE " + AiAgentSessionJournalTable.COL_SESSION_ID + " = ?";
        if (tenant != null) {
            deleteSql += TenantSql.whereTenant(AiAgentSessionJournalTable.COL_TENANT_ID);
        }
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(deleteSql)) {
            ps.setString(1, sessionId);
            if (tenant != null) {
                ps.setString(2, tenant);
            }
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new NopAiAgentException(ERR_AGENT_INTERNAL_DETAIL, e).param(ARG_DETAIL, "DBSessionStore.fold: failed to clear journal for session '"
                    + sessionId + "': " + e.getMessage());
        }
    }

    @Override
    public String forkSession(String parentSessionId, boolean inheritContext, Map<String, Object> props) {
        return forkSession(parentSessionId, inheritContext, props, null);
    }

    @Override
    public String forkSession(String parentSessionId, boolean inheritContext, Map<String, Object> props,
                              Predicate<ChatMessage> messageFilter) {
        AgentSession parent = get(parentSessionId);
        if (parent == null) {
            throw new NopAiAgentException(ERR_AGENT_INTERNAL_DETAIL).param(ARG_DETAIL, "forkSession failed: parent session not found: parentSessionId=" + parentSessionId);
        }

        String childAgentName = resolveChildAgentName(parent, props);
        String childSessionId = UUID.randomUUID().toString();
        AgentSession child = AgentSession.create(childSessionId, childAgentName);

        if (inheritContext) {
            child.appendMessages(filterMessages(parent.getMessages(), messageFilter));
            child.setPlanId(parent.getPlanId());
            child.setMetadata(parent.getMetadata());
        }

        mergeProps(child, props);

        child.setParentSessionId(parentSessionId);
        save(child);

        return childSessionId;
    }

    private static List<ChatMessage> filterMessages(List<ChatMessage> messages, Predicate<ChatMessage> filter) {
        if (filter == null) {
            return messages;
        }
        return messages.stream().filter(filter).collect(Collectors.toList());
    }

    // ========================================================================
    // Internal helpers
    // ========================================================================

    private AgentSession putIfAbsent(String sessionId, AgentSession session) {
        return sessions.putIfAbsent(sessionId, session);
    }

    private Object sessionLock(String sessionId) {
        return sessionLocks.computeIfAbsent(sessionId, k -> new Object());
    }

    private AgentSession loadFromDb(String sessionId) {
        String tenant = currentTenant();
        // carries its own tenant for recovery paths (resumeSession/
        // restoreSession) that have no request/Principal source.
        String selectSql = "SELECT " + AiAgentSessionTable.COL_SESSION_DATA
                + ", " + AiAgentSessionTable.COL_TENANT_ID
                + " FROM " + AiAgentSessionTable.TABLE_NAME
                + " WHERE " + AiAgentSessionTable.COL_SESSION_ID + " = ?";
        if (tenant != null) {
            selectSql += TenantSql.whereTenant(AiAgentSessionTable.COL_TENANT_ID);
        }
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(selectSql)) {
            ps.setString(1, sessionId);
            if (tenant != null) {
                ps.setString(2, tenant);
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String json = rs.getString(1);
                    SessionFileReader.PersistedSnapshot snapshot = SessionFileReader.deserializeSnapshot(json);
                    AgentSession loaded = snapshot.getSession();
                    // The persisted JSON may predate the tenantId field; the
                    // TENANT_ID column is authoritative, so set it from the
                    // column when the JSON did not carry one.
                    if (loaded.getTenantId() == null) {
                        loaded.setTenantId(rs.getString(2));
                    }
                    // Plan 2295 (WI14): replay the journal tail and register
                    // the persisted state so subsequent saves can append (the
                    // tenant mode bypasses the session cache — the state map
                    // is what keeps the append path alive there).
                    registerPersistedState(loaded, sessionId, snapshot.getSnapshotVersion());
                    return loaded;
                }
            }
        } catch (SQLException e) {
            throw new NopAiAgentException(ERR_AGENT_INTERNAL_DETAIL, e).param(ARG_DETAIL, "DBSessionStore.loadFromDb: failed to load session '" + sessionId
                            + "': " + e.getMessage());
        }
        return null;
    }

    /**
     * Plan 2295 (WI14): replay the journal tail onto the loaded snapshot and
     * register the per-session persisted state. On a stale/illegal journal
     * (fold-crash orphaned rows sit at the head), self-heals by folding the
     * replayed state — CLOB MERGE with a bumped version + journal row delete —
     * restoring the "CLOB == replayed state, journal empty" invariant.
     */
    private void registerPersistedState(AgentSession session, String sessionId, long snapshotVersion) {
        SessionPersistenceState state = new SessionPersistenceState();
        List<JournalRow> rows = selectJournalRows(sessionId);
        int applied = 0;
        boolean heal = false;
        for (JournalRow row : rows) {
            SessionDeltaJournal.Entry entry;
            try {
                entry = SessionDeltaJournal.decodeEntry(row.entryJson);
            } catch (NopAiAgentException e) {
                LOG.warn("DBSessionStore.registerPersistedState: discarding journal from invalid row "
                        + "(session={}, applied={})", sessionId, applied, e);
                heal = true;
                break;
            }
            if (entry.getBaseVersion() != snapshotVersion || entry.getSeq() != applied) {
                LOG.warn("DBSessionStore.registerPersistedState: stale journal rows for session={} "
                                + "(entryBaseVersion={}, snapshotVersion={}, expectedSeq={}, gotSeq={}) — healing",
                        sessionId, entry.getBaseVersion(), snapshotVersion, applied, entry.getSeq());
                heal = true;
                break;
            }
            SessionDeltaJournal.applyTo(session, entry);
            applied++;
        }

        if (heal) {
            synchronized (sessionLock(sessionId)) {
                long healedVersion = snapshotVersion + 1L;
                mergeFullRow(session, healedVersion);
                deleteJournalRows(sessionId);
                state.snapshotVersion = healedVersion;
                state.journalEntryCount = 0;
                for (ChatMessage message : session.getMessages()) {
                    state.prefixFingerprints.add(ChatMessageFingerprints.fingerprint(message));
                }
                persistedStates.put(sessionId, state);
            }
        } else {
            state.snapshotVersion = snapshotVersion;
            state.journalEntryCount = applied;
            for (ChatMessage message : session.getMessages()) {
                state.prefixFingerprints.add(ChatMessageFingerprints.fingerprint(message));
            }
            persistedStates.put(sessionId, state);
        }
    }

    private List<JournalRow> selectJournalRows(String sessionId) {
        String tenant = currentTenant();
        String selectSql = "SELECT " + AiAgentSessionJournalTable.COL_SEQ
                + ", " + AiAgentSessionJournalTable.COL_BASE_VERSION
                + ", " + AiAgentSessionJournalTable.COL_ENTRY_JSON
                + " FROM " + AiAgentSessionJournalTable.TABLE_NAME
                + " WHERE " + AiAgentSessionJournalTable.COL_SESSION_ID + " = ?"
                + " ORDER BY " + AiAgentSessionJournalTable.COL_SEQ;
        if (tenant != null) {
            selectSql += TenantSql.whereTenant(AiAgentSessionJournalTable.COL_TENANT_ID);
        }
        List<JournalRow> rows = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(selectSql)) {
            ps.setString(1, sessionId);
            if (tenant != null) {
                ps.setString(2, tenant);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new JournalRow(rs.getLong(1), rs.getLong(2), rs.getString(3)));
                }
            }
        } catch (SQLException e) {
            throw new NopAiAgentException(ERR_AGENT_INTERNAL_DETAIL, e).param(ARG_DETAIL, "DBSessionStore.selectJournalRows: failed to read journal for session '"
                    + sessionId + "': " + e.getMessage());
        }
        return rows;
    }

    private static final class JournalRow {
        final long seq;
        final long baseVersion;
        final String entryJson;

        JournalRow(long seq, long baseVersion, String entryJson) {
            this.seq = seq;
            this.baseVersion = baseVersion;
            this.entryJson = entryJson;
        }
    }

    private static String resolveChildAgentName(AgentSession parent, Map<String, Object> props) {
        if (props != null) {
            Object agentNameValue = props.get(PROPS_KEY_AGENT_NAME);
            if (agentNameValue instanceof String && !((String) agentNameValue).isEmpty()) {
                return (String) agentNameValue;
            }
        }
        return parent.getAgentName();
    }

    private static void mergeProps(AgentSession child, Map<String, Object> props) {
        if (props == null || props.isEmpty()) {
            return;
        }
        Map<String, Object> merged = new HashMap<>(child.getMetadata());
        for (Map.Entry<String, Object> entry : props.entrySet()) {
            if (!PROPS_KEY_AGENT_NAME.equals(entry.getKey())) {
                merged.put(entry.getKey(), entry.getValue());
            }
        }
        child.setMetadata(merged);
    }
}
