package io.nop.ai.agent.session;

import io.nop.ai.agent.engine.NopAiAgentException;
import io.nop.dao.jdbc.datasource.SimpleDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 205 (L2-21) Phase 2 unit test for {@link DbModelSwitchedMessageWriter}.
 * Verifies that a model-switched audit message is correctly persisted to the
 * {@code nop_ai_session_message} table via raw JDBC, with all NOT NULL columns
 * populated and the metadata JSON containing the expected fields.
 */
public class TestDbModelSwitchedMessageWriter {

    private static DataSource dataSource;

    @BeforeAll
    static void init() {
        SimpleDataSource ds = new SimpleDataSource();
        ds.setDriverClassName("org.h2.Driver");
        ds.setUrl("jdbc:h2:mem:test-model-switched-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        ds.setUsername("sa");
        ds.setPassword("");
        dataSource = ds;
    }

    @AfterAll
    static void destroy() {
        if (dataSource instanceof AutoCloseable) {
            try {
                ((AutoCloseable) dataSource).close();
            } catch (Exception ignored) {
                // best-effort close
            }
        }
    }

    @BeforeEach
    void clearRows() throws Exception {
        // DbModelSwitchedMessageWriter.initSchema() creates the table on first
        // construction; clear rows between tests for isolation.
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM nop_ai_session_message");
        } catch (Exception ignored) {
            // table may not exist yet
        }
    }

    @Test
    void writeModelSwitchedPersistsRowWithAllNotNullColumns() throws Exception {
        DbModelSwitchedMessageWriter writer = new DbModelSwitchedMessageWriter(dataSource);

        writer.writeModelSwitched("sess-unit-1", "openai:gpt-4o", "anthropic:claude-3",
                "budget-downgrade", "complex", 1);

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT id, session_id, role, seq, content, metadata, version, "
                             + "created_by, create_time, updated_by, update_time "
                             + "FROM nop_ai_session_message WHERE session_id = 'sess-unit-1'")) {

            assertTrue(rs.next(), "A row must be persisted after writeModelSwitched");

            assertNotNull(rs.getString("id"), "id (UUID) must be populated");
            assertEquals("sess-unit-1", rs.getString("session_id"));
            assertEquals(80, rs.getInt("role"),
                    "role must be MESSAGE_TYPE_MODEL_SWITCHED (80)");
            assertEquals(1, rs.getLong("seq"), "seq must match the supplied value");
            assertNotNull(rs.getString("content"), "content must be populated");
            assertEquals(0, rs.getInt("version"), "version must default to 0");
            assertEquals("ai-agent", rs.getString("created_by"), "created_by must be populated");
            assertNotNull(rs.getTimestamp("create_time"), "create_time must be populated");
            assertEquals("ai-agent", rs.getString("updated_by"), "updated_by must be populated");
            assertNotNull(rs.getTimestamp("update_time"), "update_time must be populated");

            String metadata = rs.getString("metadata");
            assertNotNull(metadata, "metadata JSON must be populated");
            assertTrue(metadata.contains("\"fromModel\""), "metadata must contain fromModel");
            assertTrue(metadata.contains("\"openai:gpt-4o\""), "metadata fromModel value");
            assertTrue(metadata.contains("\"toModel\""), "metadata must contain toModel");
            assertTrue(metadata.contains("\"anthropic:claude-3\""), "metadata toModel value");
            assertTrue(metadata.contains("\"routingReason\""), "metadata must contain routingReason");
            assertTrue(metadata.contains("\"budget-downgrade\""), "metadata routingReason value");
            assertTrue(metadata.contains("\"complexity\""), "metadata must contain complexity");
            assertTrue(metadata.contains("\"complex\""), "metadata complexity value");
        }
    }

    @Test
    void writeModelSwitchedHandlesNullRoutingReasonAndComplexity() throws Exception {
        DbModelSwitchedMessageWriter writer = new DbModelSwitchedMessageWriter(dataSource);

        writer.writeModelSwitched("sess-unit-2", "openai:gpt-4", "openai:gpt-4o",
                null, null, 1);

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT metadata FROM nop_ai_session_message WHERE session_id = 'sess-unit-2'")) {

            assertTrue(rs.next());
            String metadata = rs.getString("metadata");
            assertNotNull(metadata);
            // null routingReason/complexity are graceful degradation, written as JSON null
            assertTrue(metadata.contains("\"routingReason\":null"),
                    "null routingReason must be serialized as JSON null");
            assertTrue(metadata.contains("\"complexity\":null"),
                    "null complexity must be serialized as JSON null");
        }
    }

    @Test
    void writeModelSwitchedThrowsOnNullSessionId() {
        DbModelSwitchedMessageWriter writer = new DbModelSwitchedMessageWriter(dataSource);

        assertThrows(NopAiAgentException.class, () ->
                        writer.writeModelSwitched(null, "a:b", "c:d", "reason", "complex", 1),
                "writeModelSwitched must throw NopAiAgentException on null sessionId");
    }

    @Test
    void seqIncrementsCorrectlyAcrossMultipleWrites() throws Exception {
        DbModelSwitchedMessageWriter writer = new DbModelSwitchedMessageWriter(dataSource);

        writer.writeModelSwitched("sess-seq", "a:b", "c:d", null, null, 1);
        writer.writeModelSwitched("sess-seq", "c:d", "e:f", null, null, 2);
        writer.writeModelSwitched("sess-seq", "e:f", "g:h", null, null, 3);

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT COUNT(*) AS cnt FROM nop_ai_session_message WHERE session_id = 'sess-seq'")) {
            assertTrue(rs.next());
            assertEquals(3, rs.getInt("cnt"),
                    "Three writes must produce three rows");
        }

        // Verify unique constraint uk_nop_ai_session_msg_seq (session_id, seq)
        // allows multiple seq values for the same session
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT seq FROM nop_ai_session_message "
                             + "WHERE session_id = 'sess-seq' ORDER BY seq")) {
            assertTrue(rs.next());
            assertEquals(1, rs.getLong("seq"));
            assertTrue(rs.next());
            assertEquals(2, rs.getLong("seq"));
            assertTrue(rs.next());
            assertEquals(3, rs.getLong("seq"));
        }
    }

    @Test
    void writerDefaultsToNoOpWhenNotProvided() {
        // Verify NoOpModelSwitchedMessageWriter is a valid singleton default
        IModelSwitchedMessageWriter noOp = NoOpModelSwitchedMessageWriter.noOp();
        assertNotNull(noOp);
        // NoOp should not throw
        noOp.writeModelSwitched("s", "a", "b", "r", "c", 1);
        assertTrue(noOp == NoOpModelSwitchedMessageWriter.noOp(),
                "NoOpModelSwitchedMessageWriter must be a singleton");
    }
}
