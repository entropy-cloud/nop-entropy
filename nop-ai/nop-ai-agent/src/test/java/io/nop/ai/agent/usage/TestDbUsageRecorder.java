package io.nop.ai.agent.usage;

import io.nop.ai.agent.engine.NopAiAgentException;
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
import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 202 (L2-18) focused tests for {@link DbUsageRecorder}: verifies DB row
 * write + field integrity, modelId resolution (found / not-found paths), and
 * SQL-exception wrapping. Every assertion queries the
 * {@code nop_ai_chat_response} table directly (Anti-Hollow, Minimum Rules #22).
 */
public class TestDbUsageRecorder {

    private static final String DDL_NOP_AI_MODEL = ""
            + "CREATE TABLE IF NOT EXISTS nop_ai_model ("
            + "id VARCHAR(100) NOT NULL, "
            + "provider VARCHAR(100), "
            + "model_name VARCHAR(200), "
            + "base_url VARCHAR(500), "
            + "api_key VARCHAR(500), "
            + "version INTEGER, "
            + "created_by VARCHAR(100), "
            + "create_time TIMESTAMP, "
            + "updated_by VARCHAR(100), "
            + "update_time TIMESTAMP, "
            + "PRIMARY KEY (id)"
            + ")";

    private DataSource dataSource;

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
        SimpleDataSource ds = new SimpleDataSource();
        ds.setDriverClassName("org.h2.Driver");
        ds.setUrl("jdbc:h2:mem:test-db-usage-recorder-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        ds.setUsername("sa");
        ds.setPassword("");
        dataSource = ds;

        // nop_ai_model is owned by nop-ai-dao; create inline for modelId
        // resolution tests (column names per _NopAiModel.java).
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(DDL_NOP_AI_MODEL);
        } catch (Exception e) {
            throw new IllegalStateException("setUp: failed to create nop_ai_model", e);
        }
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
    // Row write + field integrity (with modelId resolved)
    // ========================================================================

    @Test
    void recordWritesRowWithMatchingFieldsAndResolvedModelId() throws Exception {
        // Pre-insert a nop_ai_model row matching the record's provider+model
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("INSERT INTO nop_ai_model (id, provider, model_name) "
                    + "VALUES ('model-pk-1', 'openai', 'gpt-4')");
        }

        DbUsageRecorder recorder = new DbUsageRecorder(dataSource);

        UsageRecord record = new UsageRecord();
        record.setSessionId("sess-field-1");
        record.setAgentName("agent-x");
        record.setRequestId("req-field-1");
        record.setAiProvider("openai");
        record.setAiModel("gpt-4");
        record.setPromptTokens(120);
        record.setCompletionTokens(45);
        record.setResponseDurationMs(321L);
        record.setResponseTimestamp(System.currentTimeMillis());

        recorder.record(record);

        assertEquals(1, countRows(), "record() must write exactly 1 row");

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT request_id, session_id, model_id, ai_provider, ai_model, "
                             + "prompt_tokens, completion_tokens, response_duration_ms, "
                             + "response_timestamp FROM nop_ai_chat_response")) {
            assertTrue(rs.next());
            assertEquals("req-field-1", rs.getString("request_id"));
            assertEquals("sess-field-1", rs.getString("session_id"));
            assertEquals("model-pk-1", rs.getString("model_id"),
                    "model_id must be resolved from nop_ai_model by provider+model_name");
            assertEquals("openai", rs.getString("ai_provider"));
            assertEquals("gpt-4", rs.getString("ai_model"));
            assertEquals(120, rs.getInt("prompt_tokens"));
            assertEquals(45, rs.getInt("completion_tokens"));
            assertEquals(321L, rs.getLong("response_duration_ms"));
            assertEquals(new Timestamp(record.getResponseTimestamp()), rs.getTimestamp("response_timestamp"));
        }
    }

    // ========================================================================
    // modelId resolution: not-found path
    // ========================================================================

    @Test
    void recordWritesNullModelIdWhenNoMatchingModelRow() throws Exception {
        DbUsageRecorder recorder = new DbUsageRecorder(dataSource);

        UsageRecord record = new UsageRecord();
        record.setSessionId("sess-nomodel-1");
        record.setRequestId("req-nomodel-1");
        record.setAiProvider("unknown-provider");
        record.setAiModel("unknown-model");
        record.setPromptTokens(10);
        record.setCompletionTokens(5);
        record.setResponseDurationMs(50L);
        record.setResponseTimestamp(System.currentTimeMillis());

        recorder.record(record);

        assertEquals(1, countRows(), "record() must still write a row when model is not found");

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT model_id, ai_provider, ai_model FROM nop_ai_chat_response")) {
            assertTrue(rs.next());
            assertNull(rs.getString("model_id"),
                    "model_id must be null when no nop_ai_model row matches");
            assertEquals("unknown-provider", rs.getString("ai_provider"),
                    "ai_provider must still be recorded (graceful degradation)");
            assertEquals("unknown-model", rs.getString("ai_model"),
                    "ai_model must still be recorded (graceful degradation)");
        }
    }

    // ========================================================================
    // modelId resolution: null provider/model short-circuits to null
    // ========================================================================

    @Test
    void recordHandlesNullProviderOrModelGracefully() throws Exception {
        DbUsageRecorder recorder = new DbUsageRecorder(dataSource);

        UsageRecord record = new UsageRecord();
        record.setSessionId("sess-null-prov");
        record.setRequestId("req-null-prov");
        record.setAiProvider(null);
        record.setAiModel(null);
        record.setPromptTokens(0);
        record.setCompletionTokens(0);
        record.setResponseTimestamp(System.currentTimeMillis());

        recorder.record(record);

        assertEquals(1, countRows());
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT model_id FROM nop_ai_chat_response")) {
            assertTrue(rs.next());
            assertNull(rs.getString("model_id"),
                    "null provider/model must produce null model_id without error");
        }
    }

    // ========================================================================
    // Null responseDurationMs path (defensive)
    // ========================================================================

    @Test
    void recordWritesNullDurationWhenAbsent() throws Exception {
        DbUsageRecorder recorder = new DbUsageRecorder(dataSource);

        UsageRecord record = new UsageRecord();
        record.setSessionId("sess-no-dur");
        record.setRequestId("req-no-dur");
        record.setAiProvider("p");
        record.setAiModel("m");
        record.setResponseTimestamp(System.currentTimeMillis());
        // responseDurationMs left null

        recorder.record(record);

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT response_duration_ms FROM nop_ai_chat_response")) {
            assertTrue(rs.next());
            assertEquals(0, rs.getInt("response_duration_ms"));
            assertTrue(rs.wasNull(), "response_duration_ms must be SQL NULL when record's duration is null");
        }
    }

    // ========================================================================
    // SQL exception wrapped as NopAiAgentException (no silent skip)
    // ========================================================================

    @Test
    void sqlFailureOnInsertIsWrappedAsNopAiAgentException() throws Exception {
        DbUsageRecorder recorder = new DbUsageRecorder(dataSource);

        // Drop the target table so the INSERT fails (initSchema already ran).
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE nop_ai_chat_response");
        }

        UsageRecord record = new UsageRecord();
        record.setSessionId("sess-fail");
        record.setRequestId("req-fail");
        record.setAiProvider("p");
        record.setAiModel("m");
        record.setResponseTimestamp(System.currentTimeMillis());

        NopAiAgentException ex = assertThrows(NopAiAgentException.class, () -> recorder.record(record),
                "INSERT failure must be wrapped as NopAiAgentException (no silent skip, Minimum Rules #24)");
        assertNotNull(ex.getMessage());
        assertNotNull(ex.getCause(),
                "Wrapped exception must retain the original SQLException as cause");
    }

    @Test
    void recordNullThrowsNopAiAgentException() {
        DbUsageRecorder recorder = new DbUsageRecorder(dataSource);
        assertThrows(NopAiAgentException.class, () -> recorder.record(null),
                "record(null) must fail fast (Minimum Rules #24)");
    }

    @Test
    void constructorRejectsNullDataSource() {
        assertThrows(NullPointerException.class, () -> new DbUsageRecorder(null),
                "constructor must reject null DataSource");
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    private int countRows() throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM nop_ai_chat_response")) {
            rs.next();
            return rs.getInt(1);
        }
    }
}
