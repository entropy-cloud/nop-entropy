package io.nop.ai.agent.session;

import io.nop.ai.agent.engine.NopAiAgentException;
import io.nop.ai.agent.security.ITenantResolver;
import io.nop.ai.agent.security.NullTenantResolver;
import io.nop.ai.agent.security.TenantSql;
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
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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
public class DBSessionStore implements ISessionStore {

    private static final Logger LOG = LoggerFactory.getLogger(DBSessionStore.class);

    static final String PROPS_KEY_AGENT_NAME = "agentName";

    private final DataSource dataSource;
    private final ConcurrentHashMap<String, AgentSession> sessions = new ConcurrentHashMap<>();
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
        } catch (SQLException e) {
            throw new NopAiAgentException(
                    "DBSessionStore: failed to initialize schema: " + e.getMessage(), e);
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
        String tenant = currentTenant();
        String deleteSql = "DELETE FROM " + AiAgentSessionTable.TABLE_NAME
                + " WHERE " + AiAgentSessionTable.COL_SESSION_ID + " = ?";
        if (tenant != null) {
            deleteSql += TenantSql.whereTenant(AiAgentSessionTable.COL_TENANT_ID);
        }
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(deleteSql)) {
            ps.setString(1, sessionId);
            if (tenant != null) {
                ps.setString(2, tenant);
            }
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new NopAiAgentException(
                    "DBSessionStore.remove: failed to delete session '" + sessionId
                            + "': " + e.getMessage(), e);
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
                        AgentSession loaded = SessionFileReader.deserialize(json);
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
            throw new NopAiAgentException(
                    "DBSessionStore.listAllSessions: failed to select sessions: "
                            + e.getMessage(), e);
        }
        return discovered;
    }

    /**
     * {@inheritDoc}
     * <p>
     * <b>Full persistence</b>: serialize the session to JSON via
     * {@link SessionFileWriter#serialize} and upsert it into the
     * {@code ai_agent_session} table using {@code MERGE INTO} (idempotent
     * overwrite — same semantics as {@link FileBackedSessionStore#save}). Also
     * updates the in-memory cache so subsequent {@link #get} calls return the
     * latest state without a DB read.
     */
    @Override
    public void save(AgentSession session) {
        if (session == null) {
            throw new NopAiAgentException("DBSessionStore.save: session must not be null");
        }
        String json = SessionFileWriter.serialize(session);
        String tenant = currentTenant();
        // Plan 232 (Design Decision 9): when a tenant is active, the MERGE key
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
            throw new NopAiAgentException(
                    "DBSessionStore.save: failed to persist session '" + session.getSessionId()
                            + "': " + e.getMessage(), e);
        }
        if (cacheEnabled()) {
            sessions.put(session.getSessionId(), session);
        }
    }

    @Override
    public String forkSession(String parentSessionId, boolean inheritContext, Map<String, Object> props) {
        AgentSession parent = get(parentSessionId);
        if (parent == null) {
            throw new NopAiAgentException(
                    "forkSession failed: parent session not found: parentSessionId=" + parentSessionId);
        }

        String childAgentName = resolveChildAgentName(parent, props);
        String childSessionId = UUID.randomUUID().toString();
        AgentSession child = AgentSession.create(childSessionId, childAgentName);

        if (inheritContext) {
            child.appendMessages(parent.getMessages());
            child.setPlanId(parent.getPlanId());
            child.setMetadata(parent.getMetadata());
        }

        mergeProps(child, props);

        child.setParentSessionId(parentSessionId);
        save(child);

        return childSessionId;
    }

    // ========================================================================
    // Internal helpers
    // ========================================================================

    private AgentSession putIfAbsent(String sessionId, AgentSession session) {
        return sessions.putIfAbsent(sessionId, session);
    }

    private AgentSession loadFromDb(String sessionId) {
        String tenant = currentTenant();
        // Plan 270 finding 13-12: also read TENANT_ID so the loaded session
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
                    AgentSession loaded = SessionFileReader.deserialize(json);
                    // The persisted JSON may predate the tenantId field; the
                    // TENANT_ID column is authoritative, so set it from the
                    // column when the JSON did not carry one.
                    if (loaded.getTenantId() == null) {
                        loaded.setTenantId(rs.getString(2));
                    }
                    return loaded;
                }
            }
        } catch (SQLException e) {
            throw new NopAiAgentException(
                    "DBSessionStore.loadFromDb: failed to load session '" + sessionId
                            + "': " + e.getMessage(), e);
        }
        return null;
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
