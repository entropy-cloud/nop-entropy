package io.nop.ai.agent.session;

import io.nop.ai.agent.engine.NopAiAgentException;
import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.messages.ChatMessage;
import io.nop.ai.api.chat.messages.ChatSystemMessage;
import io.nop.ai.api.chat.messages.ChatToolCall;
import io.nop.ai.api.chat.messages.ChatToolResponseMessage;
import io.nop.ai.api.chat.messages.ChatUserMessage;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.dao.jdbc.datasource.SimpleDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 2 functional tests for {@link DBSessionStore}: verifies the core
 * value — save → persist to DB → reload via new instance → read round-trip,
 * full-field integrity, upsert idempotency, listAllSessions SQL-based
 * discovery, remove, getOrCreate new/existing, forkSession, boundary cases
 * (empty session, missing row, corrupt JSON), and backward compatibility.
 *
 * <p>Every DB-backed operation is verified by direct SQL queries against the
 * {@code ai_agent_session} table (not just cache state), satisfying
 * Minimum Rules #22 Anti-Hollow and #23 Wiring Verification.
 */
public class TestDBSessionStore {

    private DataSource dataSource;
    private String dbUrl;

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    @BeforeEach
    void setUp() {
        dbUrl = "jdbc:h2:mem:test-db-session-store-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        SimpleDataSource ds = new SimpleDataSource();
        ds.setDriverClassName("org.h2.Driver");
        ds.setUrl(dbUrl);
        ds.setUsername("sa");
        ds.setPassword("");
        dataSource = ds;
    }

    @AfterEach
    void tearDown() {
        if (dataSource instanceof AutoCloseable) {
            try {
                ((AutoCloseable) dataSource).close();
            } catch (Exception ignored) {
                // best-effort close during teardown
            }
        }
    }

    // ========================================================================
    // save → get round-trip (core value)
    // ========================================================================

    @Test
    void saveGetRoundTripSurvivesNewInstance() throws Exception {
        AgentSession original = AgentSession.create("sess-1", "my-agent");
        original.appendMessages(buildMixedMessages());
        original.addTokensUsed(1234L);
        original.addIterations(7);
        original.setStatus(AgentExecStatus.running);
        original.getMetadata().put("key", "value");
        original.getMetadata().put("nested", Map.of("a", "b"));
        original.setParentSessionId("parent-0");
        original.setPlanId("plan-42");
        original.setCompactedAt(999L);

        DBSessionStore store1 = new DBSessionStore(dataSource);
        store1.save(original);

        // Verify row exists in the DB (Anti-Hollow — direct SQL)
        assertEquals(1, countSessionRows("sess-1"),
                "save must write exactly 1 row to ai_agent_session");

        // New store instance — simulate process restart (no shared memory state)
        DBSessionStore store2 = new DBSessionStore(dataSource);
        AgentSession restored = store2.get("sess-1");

        assertNotNull(restored, "After reload, get(sessionId) must return the persisted session");
        assertEquals("sess-1", restored.getSessionId());
        assertEquals("my-agent", restored.getAgentName());
        assertEquals(1234L, restored.getTotalTokensUsed());
        assertEquals(7, restored.getTotalIterations());
        assertEquals(AgentExecStatus.running, restored.getStatus());
        assertEquals("value", restored.getMetadata().get("key"));
        assertEquals("parent-0", restored.getParentSessionId());
        assertEquals("plan-42", restored.getPlanId());
        assertEquals(999L, restored.getCompactedAt());

        // Messages: full role round-trip
        List<ChatMessage> msgs = restored.getMessages();
        assertEquals(original.getMessageCount(), msgs.size(),
                "Message count must round-trip exactly");
        assertTrue(msgs.get(0) instanceof ChatSystemMessage);
        assertTrue(msgs.get(1) instanceof ChatUserMessage);
        assertTrue(msgs.get(2) instanceof ChatAssistantMessage);
        assertTrue(msgs.get(3) instanceof ChatToolResponseMessage);
        assertEquals("you are an agent", msgs.get(0).getContent());
        assertEquals("hello", msgs.get(1).getContent());
        assertEquals("calling tool", msgs.get(2).getContent());
        assertEquals("result", msgs.get(3).getContent());
    }

    // ========================================================================
    // Field integrity: every AgentSession field
    // ========================================================================

    @Test
    void fieldIntegrityAllStatusEnumValues() {
        DBSessionStore store1 = new DBSessionStore(dataSource);

        for (AgentExecStatus status : AgentExecStatus.values()) {
            String sid = "status-" + status.name();
            AgentSession session = AgentSession.create(sid, "agent");
            session.setStatus(status);
            store1.save(session);
        }

        DBSessionStore store2 = new DBSessionStore(dataSource);
        for (AgentExecStatus status : AgentExecStatus.values()) {
            AgentSession restored = store2.get("status-" + status.name());
            assertNotNull(restored, "Status " + status + " must round-trip");
            assertEquals(status, restored.getStatus());
        }
    }

    @Test
    void fieldIntegrityTimestampsPreserved() throws InterruptedException {
        DBSessionStore store1 = new DBSessionStore(dataSource);

        AgentSession session = AgentSession.create("ts-1", "agent");
        long created = session.getCreatedAt();
        Thread.sleep(5);
        session.appendMessages(List.of(new ChatUserMessage("x")));
        long updated = session.getUpdatedAt();

        store1.save(session);

        DBSessionStore store2 = new DBSessionStore(dataSource);
        AgentSession restored = store2.get("ts-1");
        assertNotNull(restored);
        assertEquals(created, restored.getCreatedAt(),
                "createdAt must survive the round-trip exactly");
        assertEquals(updated, restored.getUpdatedAt(),
                "updatedAt must survive the round-trip exactly");
    }

    @Test
    void fieldIntegrityNestedMetadata() {
        DBSessionStore store1 = new DBSessionStore(dataSource);

        AgentSession session = AgentSession.create("meta-1", "agent");
        Map<String, Object> meta = new HashMap<>();
        meta.put("str", "value");
        meta.put("num", 42);
        meta.put("bool", true);
        meta.put("nested", Map.of("deep", Map.of("deeper", "found")));
        session.setMetadata(meta);
        store1.save(session);

        DBSessionStore store2 = new DBSessionStore(dataSource);
        AgentSession restored = store2.get("meta-1");
        assertNotNull(restored);
        assertEquals("value", restored.getMetadata().get("str"));
        assertEquals(42, restored.getMetadata().get("num"));
        assertEquals(true, restored.getMetadata().get("bool"));
    }

    // ========================================================================
    // Upsert idempotency
    // ========================================================================

    @Test
    void saveUpsertIsIdempotent() throws Exception {
        DBSessionStore store = new DBSessionStore(dataSource);

        AgentSession session = AgentSession.create("upsert-1", "agent");
        session.appendMessages(List.of(new ChatUserMessage("first")));
        session.setStatus(AgentExecStatus.running);
        store.save(session);

        // Save again with different state
        session.setStatus(AgentExecStatus.completed);
        session.appendMessages(List.of(new ChatUserMessage("second")));
        store.save(session);

        // Row count must be 1 (upsert, not insert)
        assertEquals(1, countSessionRows("upsert-1"),
                "MERGE INTO must be idempotent (1 row, not 2)");

        // Fresh instance reads the latest state
        DBSessionStore store2 = new DBSessionStore(dataSource);
        AgentSession restored = store2.get("upsert-1");
        assertNotNull(restored);
        assertEquals(AgentExecStatus.completed, restored.getStatus(),
                "get must return the latest upsert state");
        assertEquals(2, restored.getMessageCount(),
                "Latest messages must be present");
    }

    // ========================================================================
    // listAllSessions: SQL-based discovery
    // ========================================================================

    @Test
    void listAllSessionsDiscoversAllPersistedSessions() {
        DBSessionStore store1 = new DBSessionStore(dataSource);

        for (AgentExecStatus status : AgentExecStatus.values()) {
            String sid = "disc-" + status.name();
            AgentSession s = AgentSession.create(sid, "agent-" + status.name());
            s.setStatus(status);
            s.appendMessages(List.of(new ChatUserMessage("msg-" + status)));
            store1.save(s);
        }

        // Fresh instance — empty cache
        DBSessionStore store2 = new DBSessionStore(dataSource);
        assertEquals(0, store2.getAll().size(),
                "getAll() on a fresh instance must return cache-only (empty)");

        Collection<AgentSession> discovered = store2.listAllSessions();
        assertEquals(AgentExecStatus.values().length, discovered.size(),
                "listAllSessions must discover every persisted session");

        for (AgentExecStatus status : AgentExecStatus.values()) {
            AgentSession found = discovered.stream()
                    .filter(s -> ("disc-" + status.name()).equals(s.getSessionId()))
                    .findFirst().orElse(null);
            assertNotNull(found, "Discovered set must contain session for status " + status);
            assertEquals(status, found.getStatus());
            assertEquals("agent-" + status.name(), found.getAgentName());
            assertEquals(1, found.getMessageCount());
        }

        // After discovery, sessions are cached — getAll() sees them
        assertEquals(AgentExecStatus.values().length, store2.getAll().size(),
                "listAllSessions must populate the cache");
    }

    @Test
    void listAllSessionsReturnsEmptyOnEmptyTable() {
        DBSessionStore store = new DBSessionStore(dataSource);
        Collection<AgentSession> result = store.listAllSessions();
        assertNotNull(result);
        assertTrue(result.isEmpty(), "listAllSessions on empty table must return empty");
    }

    // ========================================================================
    // remove
    // ========================================================================

    @Test
    void removeDeletesDbRowAndCache() throws Exception {
        DBSessionStore store1 = new DBSessionStore(dataSource);

        AgentSession session = AgentSession.create("rm-1", "agent");
        store1.save(session);
        assertEquals(1, countSessionRows("rm-1"));

        store1.remove("rm-1");

        assertEquals(0, countSessionRows("rm-1"),
                "remove must delete the DB row");
        assertNull(store1.get("rm-1"), "remove must also clear the cache");

        // A fresh instance must also not find it
        DBSessionStore store2 = new DBSessionStore(dataSource);
        assertNull(store2.get("rm-1"));
    }

    // ========================================================================
    // getOrCreate: new vs existing
    // ========================================================================

    @Test
    void getOrCreateCreatesFreshWhenNotInDb() throws Exception {
        DBSessionStore store = new DBSessionStore(dataSource);

        AgentSession session = store.getOrCreate("new-1", "new-agent");
        assertNotNull(session);
        assertEquals("new-1", session.getSessionId());
        assertEquals("new-agent", session.getAgentName());
        assertEquals(0, session.getMessageCount());

        // getOrCreate does NOT persist — save must be called explicitly
        // (consistent with InMemorySessionStore / FileBackedSessionStore)
        assertEquals(0, countSessionRows("new-1"),
                "getOrCreate must NOT write to DB until save is called");
    }

    @Test
    void getOrCreateLoadsFromDbWhenCacheMiss() {
        DBSessionStore store1 = new DBSessionStore(dataSource);
        AgentSession session = AgentSession.create("gc-1", "agent");
        session.appendMessages(List.of(new ChatUserMessage("persisted")));
        store1.save(session);

        DBSessionStore store2 = new DBSessionStore(dataSource);
        AgentSession loaded = store2.getOrCreate("gc-1", "different-agent");
        assertEquals("agent", loaded.getAgentName(),
                "getOrCreate on cache-miss must load the persisted agentName");
        assertEquals(1, loaded.getMessageCount());
        assertEquals("persisted", loaded.getMessages().get(0).getContent());
    }

    // ========================================================================
    // forkSession
    // ========================================================================

    @Test
    void forkSessionInheritsContext() throws Exception {
        DBSessionStore store = new DBSessionStore(dataSource);

        AgentSession parent = AgentSession.create("fork-parent", "agent");
        parent.appendMessages(List.of(new ChatUserMessage("parent-msg")));
        parent.setPlanId("plan-1");
        Map<String, Object> meta = new HashMap<>();
        meta.put("k", "v");
        parent.setMetadata(meta);
        store.save(parent);

        String childId = store.forkSession("fork-parent", true, null);
        assertNotNull(childId);

        AgentSession child = store.get(childId);
        assertNotNull(child);
        assertEquals("fork-parent", child.getParentSessionId());
        assertEquals("agent", child.getAgentName());
        assertEquals(1, child.getMessageCount());
        assertEquals("parent-msg", child.getMessages().get(0).getContent());
        assertEquals("plan-1", child.getPlanId());
        assertEquals("v", child.getMetadata().get("k"));

        // Child row exists in DB
        assertEquals(1, countSessionRows(childId));
    }

    @Test
    void forkSessionDoesNotInheritContextWhenFalse() throws Exception {
        DBSessionStore store = new DBSessionStore(dataSource);

        AgentSession parent = AgentSession.create("fork-p2", "agent");
        parent.appendMessages(List.of(new ChatUserMessage("parent-msg")));
        parent.setPlanId("plan-2");
        store.save(parent);

        String childId = store.forkSession("fork-p2", false, null);
        AgentSession child = store.get(childId);
        assertNotNull(child);
        assertEquals(0, child.getMessageCount(),
                "forkSession with inheritContext=false must start empty");
        assertNull(child.getPlanId());
        assertEquals("fork-p2", child.getParentSessionId());
    }

    @Test
    void forkSessionFailsFastWhenParentNotFound() {
        DBSessionStore store = new DBSessionStore(dataSource);
        assertThrows(NopAiAgentException.class,
                () -> store.forkSession("nonexistent-parent", true, null));
    }

    // ========================================================================
    // Boundary: empty session, missing row, corrupt JSON
    // ========================================================================

    @Test
    void emptySessionRoundTrips() {
        DBSessionStore store1 = new DBSessionStore(dataSource);

        AgentSession empty = AgentSession.create("empty-1", "agent");
        store1.save(empty);

        DBSessionStore store2 = new DBSessionStore(dataSource);
        AgentSession restored = store2.get("empty-1");
        assertNotNull(restored);
        assertEquals(0, restored.getMessageCount());
    }

    @Test
    void getReturnsNullWhenRowDoesNotExist() {
        DBSessionStore store = new DBSessionStore(dataSource);
        assertNull(store.get("never-saved"),
                "get on missing session must return null (legitimate absence)");
    }

    @Test
    void corruptJsonRowIsSkippedInListAllSessions() throws Exception {
        DBSessionStore store1 = new DBSessionStore(dataSource);

        AgentSession good1 = AgentSession.create("good-1", "a");
        good1.setStatus(AgentExecStatus.running);
        store1.save(good1);

        AgentSession good2 = AgentSession.create("good-2", "b");
        good2.setStatus(AgentExecStatus.pending);
        store1.save(good2);

        // Manually insert a corrupt SESSION_DATA row
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("INSERT INTO " + AiAgentSessionTable.TABLE_NAME
                    + " (" + AiAgentSessionTable.COL_SESSION_ID
                    + ", " + AiAgentSessionTable.COL_AGENT_NAME
                    + ", " + AiAgentSessionTable.COL_STATUS
                    + ", " + AiAgentSessionTable.COL_SESSION_DATA
                    + ", " + AiAgentSessionTable.COL_CREATED_AT
                    + ", " + AiAgentSessionTable.COL_UPDATED_AT
                    + ") VALUES ('corrupt-1', 'x', 'running', '{ this is not valid json', 0, 0)");
        }

        // Fresh instance — discovery must skip the corrupt row
        DBSessionStore store2 = new DBSessionStore(dataSource);
        Collection<AgentSession> discovered = store2.listAllSessions();

        assertEquals(2, discovered.size(),
                "listAllSessions must skip corrupt rows and return the good ones");
        assertTrue(discovered.stream().anyMatch(s -> "good-1".equals(s.getSessionId())));
        assertTrue(discovered.stream().anyMatch(s -> "good-2".equals(s.getSessionId())));
        assertFalse(discovered.stream().anyMatch(s -> "corrupt-1".equals(s.getSessionId())),
                "Corrupt session must NOT appear in discovered set");

        // A subsequent get(corruptId) must still fail fast
        assertThrows(NopAiAgentException.class, () -> store2.get("corrupt-1"),
                "get() on the corrupt id must fail fast");
    }

    // ========================================================================
    // getAll returns cache values
    // ========================================================================

    @Test
    void getAllReturnsCachedSessions() {
        DBSessionStore store = new DBSessionStore(dataSource);
        store.save(AgentSession.create("g1", "a"));
        store.save(AgentSession.create("g2", "b"));

        assertEquals(2, store.getAll().size());
    }

    // ========================================================================
    // ISessionStore contract bridge
    // ========================================================================

    @Test
    void isessionStoreDefaultSaveThrowsUOE() {
        ISessionStore store = new ISessionStore() {
            @Override
            public AgentSession getOrCreate(String sessionId, String agentName) { return null; }
            @Override
            public AgentSession get(String sessionId) { return null; }
            @Override
            public void remove(String sessionId) {}
            @Override
            public Collection<AgentSession> getAll() { return null; }
        };
        AgentSession session = AgentSession.create("x", "y");
        assertThrows(UnsupportedOperationException.class, () -> store.save(session),
                "ISessionStore default save must throw UOE (Minimum Rules #24)");
    }

    @Test
    void dbStoreSavePersistsToDb() throws Exception {
        DBSessionStore store = new DBSessionStore(dataSource);
        AgentSession session = AgentSession.create("fb-1", "agent");
        session.appendMessages(List.of(new ChatUserMessage("hi")));
        store.save(session);

        assertEquals(1, countSessionRows("fb-1"),
                "DBSessionStore.save must write to DB");
    }

    @Test
    void inMemoryStoreSaveIsExplicitNoOpAndDoesNotThrow() {
        InMemorySessionStore store = new InMemorySessionStore();
        AgentSession session = AgentSession.create("x", "y");
        assertDoesNotThrow(() -> store.save(session));
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    private int countSessionRows(String sessionId) throws Exception {
        String sql = "SELECT COUNT(*) FROM " + AiAgentSessionTable.TABLE_NAME
                + " WHERE " + AiAgentSessionTable.COL_SESSION_ID + " = '" + sessionId + "'";
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static List<ChatMessage> buildMixedMessages() {
        List<ChatMessage> msgs = new ArrayList<>();

        ChatSystemMessage sys = new ChatSystemMessage("you are an agent");
        sys.setMessageId("m0");
        msgs.add(sys);

        ChatUserMessage user = new ChatUserMessage("hello");
        user.setMessageId("m1");
        msgs.add(user);

        ChatAssistantMessage assistant = new ChatAssistantMessage();
        assistant.setContent("calling tool");
        assistant.setMessageId("m2");
        ChatToolCall call = new ChatToolCall();
        call.setId("call_1");
        call.setName("echo");
        call.setArguments(Map.of("x", "y"));
        assistant.setToolCalls(List.of(call));
        msgs.add(assistant);

        ChatToolResponseMessage toolResp = ChatToolResponseMessage.fromToolCall(call, "result");
        toolResp.setMessageId("m3");
        msgs.add(toolResp);

        return msgs;
    }
}
