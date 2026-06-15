package io.nop.ai.agent.reliability;

import io.nop.ai.api.exceptions.NopAiException;
import io.nop.core.lang.xml.XNode;
import io.nop.core.lang.xml.parse.XNodeParser;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 1 tests for the {@code ai_agent_checkpoint} ORM model and DDL.
 *
 * <p>Verifies:
 * <ul>
 *   <li>The ORM model file defines the {@code ai_agent_checkpoint} entity with
 *       the expected columns</li>
 *   <li>{@code SESSION_ID} is nullable (not mandatory) — anonymous checkpoints
 *       can be persisted</li>
 *   <li>The DDL creates the table + index successfully in H2</li>
 *   <li>The created table has the expected columns</li>
 *   <li>The DDL is idempotent (safe to re-run)</li>
 *   <li>INSERT + SELECT work against the table</li>
 * </ul>
 */
public class TestAiAgentCheckpointTable {

    private static final String ORM_MODEL_PATH = "_vfs/nop/ai/agent/orm/app.orm.xml";

    private XNode loadOrmModel() {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(ORM_MODEL_PATH)) {
            assertNotNull(is, "ORM model file should exist in classpath: " + ORM_MODEL_PATH);
            return XNodeParser.instance().parseFromText(null, new String(is.readAllBytes()));
        } catch (Exception e) {
            throw new NopAiException("Failed to load ORM model: " + ORM_MODEL_PATH, e);
        }
    }

    @Test
    void ormModelDefinesCheckpointEntity() {
        XNode ormNode = loadOrmModel();
        assertNotNull(ormNode);
        assertEquals("orm", ormNode.getTagName());

        XNode entitiesNode = ormNode.childByTag("entities");
        assertNotNull(entitiesNode, "entities section should exist in the ORM model");
        XNode entityNode = entitiesNode.childByAttr("name", "io.nop.ai.agent.reliability.AiAgentCheckpoint");
        assertNotNull(entityNode, "entity AiAgentCheckpoint should be defined in the ORM model");
        assertEquals("ai_agent_checkpoint", entityNode.attrText("tableName"));
    }

    @Test
    void ormModelDefinesAllRequiredColumns() {
        XNode ormNode = loadOrmModel();
        XNode entitiesNode = ormNode.childByTag("entities");
        XNode entityNode = entitiesNode.childByAttr("name", "io.nop.ai.agent.reliability.AiAgentCheckpoint");
        XNode columnsNode = entityNode.childByTag("columns");
        assertNotNull(columnsNode);

        List<String> columnNames = new ArrayList<>();
        for (XNode col : columnsNode.childrenByTag("column")) {
            columnNames.add(col.attrText("code"));
        }

        assertTrue(columnNames.contains("WATERMARK"), "WATERMARK column must be defined");
        assertTrue(columnNames.contains("SESSION_ID"), "SESSION_ID column must be defined");
        assertTrue(columnNames.contains("SEQ"), "SEQ column must be defined");
        assertTrue(columnNames.contains("CHECKPOINT_TIMESTAMP"), "CHECKPOINT_TIMESTAMP column must be defined");
        assertTrue(columnNames.contains("CHECKPOINT_TYPE"), "CHECKPOINT_TYPE column must be defined");
        assertTrue(columnNames.contains("TOOL_NAME"), "TOOL_NAME column must be defined");
        assertTrue(columnNames.contains("CALL_ID"), "CALL_ID column must be defined");
        assertTrue(columnNames.contains("INPUT_SUMMARY"), "INPUT_SUMMARY column must be defined");
        assertTrue(columnNames.contains("OUTPUT_SUMMARY"), "OUTPUT_SUMMARY column must be defined");
        assertTrue(columnNames.contains("MESSAGE_COUNT"), "MESSAGE_COUNT column must be defined");
        assertTrue(columnNames.contains("TOKEN_ESTIMATE"), "TOKEN_ESTIMATE column must be defined");
    }

    @Test
    void ormModelWatermarkIsPrimaryKey() {
        XNode ormNode = loadOrmModel();
        XNode entitiesNode = ormNode.childByTag("entities");
        XNode entityNode = entitiesNode.childByAttr("name", "io.nop.ai.agent.reliability.AiAgentCheckpoint");
        XNode columnsNode = entityNode.childByTag("columns");

        XNode watermarkCol = null;
        for (XNode col : columnsNode.childrenByTag("column")) {
            if ("WATERMARK".equals(col.attrText("code"))) {
                watermarkCol = col;
                break;
            }
        }
        assertNotNull(watermarkCol);
        assertEquals("true", watermarkCol.attrText("primary"),
                "WATERMARK must be the primary key");
        assertEquals("true", watermarkCol.attrText("mandatory"),
                "WATERMARK must be mandatory");
    }

    @Test
    void ormModelSessionIdIsNullable() {
        XNode ormNode = loadOrmModel();
        XNode entitiesNode = ormNode.childByTag("entities");
        XNode entityNode = entitiesNode.childByAttr("name", "io.nop.ai.agent.reliability.AiAgentCheckpoint");
        XNode columnsNode = entityNode.childByTag("columns");

        XNode sessionIdCol = null;
        for (XNode col : columnsNode.childrenByTag("column")) {
            if ("SESSION_ID".equals(col.attrText("code"))) {
                sessionIdCol = col;
                break;
            }
        }
        assertNotNull(sessionIdCol);
        assertNull(sessionIdCol.attrText("primary"),
                "SESSION_ID must NOT be primary (WATERMARK is the PK)");
        assertNull(sessionIdCol.attrText("mandatory"),
                "SESSION_ID must NOT be mandatory (anonymous checkpoints allowed)");
    }

    @Test
    void ddlCreatesTableAndIndexInH2() throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:test-checkpoint-ddl;DB_CLOSE_DELAY=-1", "sa", "");
             Statement stmt = conn.createStatement()) {

            stmt.execute(AiAgentCheckpointTable.DDL_CREATE_TABLE);
            stmt.execute(AiAgentCheckpointTable.DDL_CREATE_INDEX);

            assertTrue(tableExists(conn, AiAgentCheckpointTable.TABLE_NAME),
                    "Table " + AiAgentCheckpointTable.TABLE_NAME + " should exist after DDL execution");
            assertTrue(indexExists(conn, AiAgentCheckpointTable.INDEX_SESSION_SEQ),
                    "Index " + AiAgentCheckpointTable.INDEX_SESSION_SEQ + " should exist after DDL execution");
        }
    }

    @Test
    void ddlCreatesExpectedColumns() throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:test-checkpoint-cols;DB_CLOSE_DELAY=-1", "sa", "");
             Statement stmt = conn.createStatement()) {

            stmt.execute(AiAgentCheckpointTable.DDL_CREATE_TABLE);

            List<String> columnNames = new ArrayList<>();
            try (ResultSet rs = conn.getMetaData().getColumns(null, null,
                    AiAgentCheckpointTable.TABLE_NAME.toUpperCase(), null)) {
                while (rs.next()) {
                    columnNames.add(rs.getString("COLUMN_NAME"));
                }
            }

            assertFalse(columnNames.isEmpty(), "Table should have columns");
            assertTrue(columnNames.contains("WATERMARK"));
            assertTrue(columnNames.contains("SESSION_ID"));
            assertTrue(columnNames.contains("SEQ"));
            assertTrue(columnNames.contains("CHECKPOINT_TIMESTAMP"));
            assertTrue(columnNames.contains("CHECKPOINT_TYPE"));
            assertTrue(columnNames.contains("TOOL_NAME"));
            assertTrue(columnNames.contains("CALL_ID"));
            assertTrue(columnNames.contains("INPUT_SUMMARY"));
            assertTrue(columnNames.contains("OUTPUT_SUMMARY"));
            assertTrue(columnNames.contains("MESSAGE_COUNT"));
            assertTrue(columnNames.contains("TOKEN_ESTIMATE"));
        }
    }

    @Test
    void ddlIsIdempotent() throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:test-checkpoint-idem;DB_CLOSE_DELAY=-1", "sa", "");
             Statement stmt = conn.createStatement()) {

            stmt.execute(AiAgentCheckpointTable.DDL_CREATE_TABLE);
            stmt.execute(AiAgentCheckpointTable.DDL_CREATE_TABLE);
            stmt.execute(AiAgentCheckpointTable.DDL_CREATE_INDEX);
            stmt.execute(AiAgentCheckpointTable.DDL_CREATE_INDEX);

            assertTrue(tableExists(conn, AiAgentCheckpointTable.TABLE_NAME));
        }
    }

    @Test
    void insertAndSelectWork() throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:test-checkpoint-ins;DB_CLOSE_DELAY=-1", "sa", "");
             Statement stmt = conn.createStatement()) {

            stmt.execute(AiAgentCheckpointTable.DDL_CREATE_TABLE);

            stmt.execute("INSERT INTO " + AiAgentCheckpointTable.TABLE_NAME
                    + " (" + AiAgentCheckpointTable.COL_WATERMARK
                    + ", " + AiAgentCheckpointTable.COL_SESSION_ID
                    + ", " + AiAgentCheckpointTable.COL_SEQ
                    + ", " + AiAgentCheckpointTable.COL_CHECKPOINT_TIMESTAMP
                    + ", " + AiAgentCheckpointTable.COL_CHECKPOINT_TYPE
                    + ", " + AiAgentCheckpointTable.COL_TOOL_NAME
                    + ", " + AiAgentCheckpointTable.COL_CALL_ID
                    + ", " + AiAgentCheckpointTable.COL_INPUT_SUMMARY
                    + ", " + AiAgentCheckpointTable.COL_OUTPUT_SUMMARY
                    + ", " + AiAgentCheckpointTable.COL_MESSAGE_COUNT
                    + ", " + AiAgentCheckpointTable.COL_TOKEN_ESTIMATE
                    + ") VALUES ('wm-1', 'sess-1', 0, 1000, 'TOOL_EXECUTION', 'echo', 'c1', 'in', 'out', 5, 200)");

            try (ResultSet rs = stmt.executeQuery(
                    "SELECT " + AiAgentCheckpointTable.COL_SESSION_ID
                            + ", " + AiAgentCheckpointTable.COL_SEQ
                            + ", " + AiAgentCheckpointTable.COL_CHECKPOINT_TYPE
                            + " FROM " + AiAgentCheckpointTable.TABLE_NAME
                            + " WHERE " + AiAgentCheckpointTable.COL_WATERMARK + " = 'wm-1'")) {
                assertTrue(rs.next());
                assertEquals("sess-1", rs.getString(1));
                assertEquals(0, rs.getInt(2));
                assertEquals("TOOL_EXECUTION", rs.getString(3));
            }
        }
    }

    @Test
    void insertWithNullSessionIdWorks() throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:test-checkpoint-null-sid;DB_CLOSE_DELAY=-1", "sa", "");
             Statement stmt = conn.createStatement()) {

            stmt.execute(AiAgentCheckpointTable.DDL_CREATE_TABLE);

            stmt.execute("INSERT INTO " + AiAgentCheckpointTable.TABLE_NAME
                    + " (" + AiAgentCheckpointTable.COL_WATERMARK
                    + ", " + AiAgentCheckpointTable.COL_SESSION_ID
                    + ", " + AiAgentCheckpointTable.COL_SEQ
                    + ", " + AiAgentCheckpointTable.COL_CHECKPOINT_TIMESTAMP
                    + ", " + AiAgentCheckpointTable.COL_CHECKPOINT_TYPE
                    + ", " + AiAgentCheckpointTable.COL_MESSAGE_COUNT
                    + ", " + AiAgentCheckpointTable.COL_TOKEN_ESTIMATE
                    + ") VALUES ('wm-anon', NULL, 0, 1000, 'TOOL_EXECUTION', 0, 0)");

            try (ResultSet rs = stmt.executeQuery(
                    "SELECT " + AiAgentCheckpointTable.COL_SESSION_ID
                            + " FROM " + AiAgentCheckpointTable.TABLE_NAME
                            + " WHERE " + AiAgentCheckpointTable.COL_WATERMARK + " = 'wm-anon'")) {
                assertTrue(rs.next());
                assertNull(rs.getString(1),
                        "Anonymous checkpoint SESSION_ID must be null (nullable column)");
            }
        }
    }

    private boolean tableExists(Connection conn, String tableName) throws SQLException {
        try (ResultSet rs = conn.getMetaData().getTables(null, null, tableName.toUpperCase(), null)) {
            return rs.next();
        }
    }

    private boolean indexExists(Connection conn, String indexName) throws SQLException {
        try (ResultSet rs = conn.getMetaData().getIndexInfo(null, null,
                AiAgentCheckpointTable.TABLE_NAME.toUpperCase(), false, false)) {
            while (rs.next()) {
                String name = rs.getString("INDEX_NAME");
                if (indexName.equalsIgnoreCase(name)) {
                    return true;
                }
            }
        }
        return false;
    }
}
