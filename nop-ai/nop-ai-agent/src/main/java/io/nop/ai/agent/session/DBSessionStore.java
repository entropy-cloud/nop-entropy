package io.nop.ai.agent.session;

import io.nop.ai.agent.engine.NopAiAgentException;
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

    /**
     * Create a DB-backed session store and initialize the DB schema (create
     * table + index if absent).
     *
     * @param dataSource the JDBC data source; never null
     */
    public DBSessionStore(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
        initSchema();
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
        AgentSession existing = sessions.get(sessionId);
        if (existing != null) {
            return existing;
        }
        AgentSession loaded = loadFromDb(sessionId);
        if (loaded != null) {
            AgentSession raced = putIfAbsent(sessionId, loaded);
            return raced != null ? raced : loaded;
        }
        AgentSession fresh = AgentSession.create(sessionId, agentName);
        AgentSession raced = putIfAbsent(sessionId, fresh);
        return raced != null ? raced : fresh;
    }

    @Override
    public AgentSession get(String sessionId) {
        AgentSession cached = sessions.get(sessionId);
        if (cached != null) {
            return cached;
        }
        AgentSession loaded = loadFromDb(sessionId);
        if (loaded != null) {
            AgentSession raced = putIfAbsent(sessionId, loaded);
            return raced != null ? raced : loaded;
        }
        return null;
    }

    @Override
    public void remove(String sessionId) {
        sessions.remove(sessionId);
        String deleteSql = "DELETE FROM " + AiAgentSessionTable.TABLE_NAME
                + " WHERE " + AiAgentSessionTable.COL_SESSION_ID + " = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(deleteSql)) {
            ps.setString(1, sessionId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new NopAiAgentException(
                    "DBSessionStore.remove: failed to delete session '" + sessionId
                            + "': " + e.getMessage(), e);
        }
    }

    @Override
    public Collection<AgentSession> getAll() {
        return sessions.values();
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
        String selectAll = "SELECT " + AiAgentSessionTable.COL_SESSION_DATA
                + " FROM " + AiAgentSessionTable.TABLE_NAME;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(selectAll);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String json = rs.getString(1);
                String sessionId = null;
                try {
                    AgentSession loaded = SessionFileReader.deserialize(json);
                    sessionId = loaded.getSessionId();
                    AgentSession raced = sessions.putIfAbsent(sessionId, loaded);
                    discovered.add(raced != null ? raced : loaded);
                } catch (NopAiAgentException e) {
                    LOG.warn("DBSessionStore.listAllSessions: skipping unreadable "
                                    + "session row (corrupt or truncated JSON)",
                            e);
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
        String mergeSql = "MERGE INTO " + AiAgentSessionTable.TABLE_NAME
                + " (" + AiAgentSessionTable.COL_SESSION_ID
                + ", " + AiAgentSessionTable.COL_AGENT_NAME
                + ", " + AiAgentSessionTable.COL_STATUS
                + ", " + AiAgentSessionTable.COL_SESSION_DATA
                + ", " + AiAgentSessionTable.COL_CREATED_AT
                + ", " + AiAgentSessionTable.COL_UPDATED_AT
                + ") KEY (" + AiAgentSessionTable.COL_SESSION_ID + ") VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(mergeSql)) {
            ps.setString(1, session.getSessionId());
            ps.setString(2, session.getAgentName());
            ps.setString(3, session.getStatus() != null ? session.getStatus().name() : null);
            ps.setString(4, json);
            ps.setLong(5, session.getCreatedAt());
            ps.setLong(6, session.getUpdatedAt());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new NopAiAgentException(
                    "DBSessionStore.save: failed to persist session '" + session.getSessionId()
                            + "': " + e.getMessage(), e);
        }
        sessions.put(session.getSessionId(), session);
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
        String selectSql = "SELECT " + AiAgentSessionTable.COL_SESSION_DATA
                + " FROM " + AiAgentSessionTable.TABLE_NAME
                + " WHERE " + AiAgentSessionTable.COL_SESSION_ID + " = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(selectSql)) {
            ps.setString(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String json = rs.getString(1);
                    return SessionFileReader.deserialize(json);
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
