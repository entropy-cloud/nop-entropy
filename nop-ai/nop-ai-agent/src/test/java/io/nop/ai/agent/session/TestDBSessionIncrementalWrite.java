package io.nop.ai.agent.session;

import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.messages.ChatMessage;
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
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 2295 (WI14): incremental write-path tests for {@link DBSessionStore} —
 * journal row appends (with truthful scalar columns), folds (rows cleared +
 * CLOB rewritten), stale-row self-heal, restart recovery, discovery, and
 * tenant symmetry. Every DB effect is asserted by direct SQL against the
 * {@code ai_agent_session_journal} / {@code ai_agent_session} tables
 * (Anti-Hollow / Wiring Verification rules).
 */
public class TestDBSessionIncrementalWrite {

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
        dbUrl = "jdbc:h2:mem:test-db-session-journal-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
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
            } catch (Exception e) {
                // ignore
            }
        }
    }

    // ========================================================================
    // Append path: journal row + scalar columns
    // ========================================================================

    @Test
    void appendSaveInsertsJournalRowAndUpdatesScalarColumns() throws Exception {
        DBSessionStore store = new DBSessionStore(dataSource);

        AgentSession session = AgentSession.create("sess-db-app", "agent");
        session.appendMessages(List.of(new ChatUserMessage("m1")));
        session.setStatus(AgentExecStatus.running);
        store.save(session); // fold → main row exists

        assertEquals(0, countJournalRows("sess-db-app"), "fold clears the journal");

        session.appendMessages(List.of(new ChatUserMessage("m2")));
        store.save(session); // append

        assertEquals(1, countJournalRows("sess-db-app"), "append INSERTs one journal row");
        // Scalar columns are updated by the append (STATUS/UPDATED_AT truthful).
        session.setStatus(AgentExecStatus.completed);
        session.addIterations(2);
        store.save(session); // scalar-only append (empty delta)

        assertEquals(2, countJournalRows("sess-db-app"));
        assertEquals("completed", scalarColumn("sess-db-app", "STATUS"),
                "append path must keep the indexed STATUS column truthful");

        // Restart: a fresh instance replays rows → full state.
        DBSessionStore store2 = new DBSessionStore(dataSource);
        AgentSession restored = store2.get("sess-db-app");
        assertNotNull(restored);
        assertEquals(2, restored.getMessageCount());
        assertEquals(AgentExecStatus.completed, restored.getStatus());
        assertEquals(2, restored.getTotalIterations(), "journal state block carries iterations");
        assertEquals("m2", restored.getMessages().get(1).getContent());
    }

    @Test
    void foldClearsJournalRowsAndRewritesClob() throws Exception {
        DBSessionStore store = new DBSessionStore(dataSource);

        AgentSession session = AgentSession.create("sess-db-fold", "agent");
        session.appendMessages(List.of(new ChatUserMessage("m0")));
        store.save(session); // fold

        for (int i = 1; i <= SessionPersistenceState.FOLD_THRESHOLD; i++) {
            session.appendMessages(List.of(new ChatUserMessage("m" + i)));
            store.save(session);
        }
        assertEquals(SessionPersistenceState.FOLD_THRESHOLD, countJournalRows("sess-db-fold"));

        session.appendMessages(List.of(new ChatUserMessage("overflow")));
        store.save(session); // threshold → fold

        assertEquals(0, countJournalRows("sess-db-fold"), "fold deletes journal rows");
        String clob = readClob("sess-db-fold");
        assertTrue(clob.contains("\"snapshotVersion\":2"), "fold rewrites the CLOB with the bumped version");
        assertTrue(clob.contains("overflow"), "CLOB carries the folded state");

        DBSessionStore store2 = new DBSessionStore(dataSource);
        assertEquals(SessionPersistenceState.FOLD_THRESHOLD + 2, store2.get("sess-db-fold").getMessageCount());
    }

    // ========================================================================
    // Stale rows: self-heal + post-heal append survival
    // ========================================================================

    @Test
    void staleJournalRowsAreHealedByFoldOfReplayedState() throws Exception {
        DBSessionStore store = new DBSessionStore(dataSource);

        AgentSession session = AgentSession.create("sess-db-stale", "agent");
        session.appendMessages(List.of(new ChatUserMessage("m1")));
        store.save(session); // fold v1
        session.appendMessages(List.of(new ChatUserMessage("m2")));
        store.save(session); // journal row baseVersion=1

        // Simulate the fold-crash window: CLOB rewritten (v2), rows NOT cleared.
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE ai_agent_session SET SESSION_DATA = ? WHERE SESSION_ID = ?")) {
            ps.setString(1, SessionFileWriter.serialize(session, 2L));
            ps.setString(2, "sess-db-stale");
            ps.executeUpdate();
        }

        DBSessionStore store2 = new DBSessionStore(dataSource);
        AgentSession restored = store2.get("sess-db-stale");
        assertEquals(2, restored.getMessageCount(), "v2 CLOB is self-sufficient");
        assertEquals(0, countJournalRows("sess-db-stale"), "stale rows healed away (heal = fold)");

        // Post-heal append survives restart (the round-2 Major-1 scenario).
        restored.appendMessages(List.of(new ChatUserMessage("m3")));
        store2.save(restored);
        DBSessionStore store3 = new DBSessionStore(dataSource);
        assertEquals(3, store3.get("sess-db-stale").getMessageCount(),
                "post-heal append must be replay-visible across restart");
    }

    // ========================================================================
    // Discovery + remove
    // ========================================================================

    @Test
    void listAllSessionsReplaysJournalRows() {
        DBSessionStore store = new DBSessionStore(dataSource);

        AgentSession session = AgentSession.create("sess-db-disc", "agent");
        session.appendMessages(List.of(new ChatUserMessage("m1")));
        store.save(session);
        session.appendMessages(List.of(new ChatUserMessage("m2")));
        store.save(session); // tail only in journal rows

        DBSessionStore store2 = new DBSessionStore(dataSource);
        List<AgentSession> discovered = new ArrayList<>(store2.listAllSessions());
        assertEquals(1, discovered.size());
        assertEquals(2, discovered.get(0).getMessageCount(),
                "discovery must replay journal rows");
    }

    @Test
    void removeDeletesJournalRowsAndMainRow() throws Exception {
        DBSessionStore store = new DBSessionStore(dataSource);

        AgentSession session = AgentSession.create("sess-db-rm", "agent");
        session.appendMessages(List.of(new ChatUserMessage("m1")));
        store.save(session);
        session.appendMessages(List.of(new ChatUserMessage("m2")));
        store.save(session);

        store.remove("sess-db-rm");
        assertEquals(0, countJournalRows("sess-db-rm"));
        assertNullRow("sess-db-rm");
    }

    // ========================================================================
    // Tenant symmetry
    // ========================================================================

    @Test
    void tenantModeAppendAndReplayStayTenantScoped() throws Exception {
        // Constructing a store initializes the schema (tables must exist
        // before the raw INSERT below).
        ITenantResolverFixture tenantA = new ITenantResolverFixture("tenant-a");
        DBSessionStore storeA = new DBSessionStore(dataSource, tenantA);

        // Ruling (plan 2295): same-sessionId cross-tenant is unsupported —
        // the pre-WI14 MERGE KEY (SESSION_ID, TENANT_ID) against the
        // SESSION_ID-only PK already deadlocks on it, so tenant scoping is
        // asserted as: journal rows carry the writing tenant, a foreign
        // tenant cannot load the session at all, and a foreign tenant's own
        // session replays only its own rows.
        AgentSession session = AgentSession.create("sess-tenant-a", "agent-a");
        session.appendMessages(List.of(new ChatUserMessage("a1")));
        storeA.save(session); // fold (tenant-a row)
        session.appendMessages(List.of(new ChatUserMessage("a2")));
        storeA.save(session); // append (tenant-a journal row)

        assertEquals(1, countJournalRows("sess-tenant-a"));
        assertEquals("tenant-a", journalRowTenant("sess-tenant-a"),
                "journal row must carry the writing tenant");

        // Foreign tenant cannot even load the session (main-row filter).
        DBSessionStore storeB = new DBSessionStore(dataSource, new ITenantResolverFixture("tenant-other"));
        org.junit.jupiter.api.Assertions.assertNull(storeB.get("sess-tenant-a"),
                "tenant filter must exclude tenant-a's session from tenant-other");

        // Tenant-other's own session appends and replays independently.
        AgentSession other = AgentSession.create("sess-tenant-b", "agent-b");
        other.appendMessages(List.of(new ChatUserMessage("b1")));
        storeB.save(other);
        other.appendMessages(List.of(new ChatUserMessage("b2")));
        storeB.save(other);
        assertEquals(1, countJournalRows("sess-tenant-b"), "B's journal holds only B's rows");
        DBSessionStore storeB2 = new DBSessionStore(dataSource, new ITenantResolverFixture("tenant-other"));
        assertEquals(2, storeB2.get("sess-tenant-b").getMessageCount(),
                "B's journal replay is tenant-scoped and complete");
    }

    /** Minimal ITenantResolver stub (avoids a test-only production dependency). */
    static final class ITenantResolverFixture implements io.nop.ai.agent.security.ITenantResolver {
        private final String tenantId;

        ITenantResolverFixture(String tenantId) {
            this.tenantId = tenantId;
        }

        @Override
        public String resolveTenantId() {
            return tenantId;
        }
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    private int countJournalRows(String sessionId) throws Exception {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT COUNT(*) FROM ai_agent_session_journal WHERE SESSION_ID = ?")) {
            ps.setString(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private String scalarColumn(String sessionId, String column) throws Exception {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT " + column + " FROM ai_agent_session WHERE SESSION_ID = ?")) {
            ps.setString(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getString(1);
            }
        }
    }

    private String readClob(String sessionId) throws Exception {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT SESSION_DATA FROM ai_agent_session WHERE SESSION_ID = ?")) {
            ps.setString(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getString(1);
            }
        }
    }

    private String journalRowTenant(String sessionId) throws Exception {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT TENANT_ID FROM ai_agent_session_journal WHERE SESSION_ID = ?")) {
            ps.setString(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getString(1);
            }
        }
    }

    private void assertNullRow(String sessionId) throws Exception {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT COUNT(*) FROM ai_agent_session WHERE SESSION_ID = ?")) {
            ps.setString(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                assertEquals(0, rs.getInt(1), "main row must be gone");
            }
        }
    }
}
