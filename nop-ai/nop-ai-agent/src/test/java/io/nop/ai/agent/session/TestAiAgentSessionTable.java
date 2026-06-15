package io.nop.ai.agent.session;

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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 1 tests for the {@code ai_agent_session} ORM model and DDL.
 *
 * <p>Verifies:
 * <ul>
 *   <li>The ORM model file defines the {@code ai_agent_session} entity with the
 *       expected columns</li>
 *   <li>The DDL derived from the model creates the table + index successfully
 *       in H2</li>
 *   <li>The created table has the expected columns</li>
 *   <li>The DDL is idempotent (safe to re-run)</li>
 *   <li>MERGE INTO (upsert) + SELECT + DELETE work against the table</li>
 * </ul>
 */
public class TestAiAgentSessionTable {

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
    void ormModelDefinesSessionEntity() {
        XNode ormNode = loadOrmModel();
        assertNotNull(ormNode);
        assertEquals("orm", ormNode.getTagName());

        XNode entitiesNode = ormNode.childByTag("entities");
        assertNotNull(entitiesNode, "entities section should exist in the ORM model");
        XNode entityNode = entitiesNode.childByAttr("name", "io.nop.ai.agent.session.AiAgentSession");
        assertNotNull(entityNode, "entity AiAgentSession should be defined in the ORM model");
        assertEquals("ai_agent_session", entityNode.attrText("tableName"));
    }

    @Test
    void ormModelDefinesAllRequiredColumns() {
        XNode ormNode = loadOrmModel();
        XNode entitiesNode = ormNode.childByTag("entities");
        XNode entityNode = entitiesNode.childByAttr("name", "io.nop.ai.agent.session.AiAgentSession");
        XNode columnsNode = entityNode.childByTag("columns");
        assertNotNull(columnsNode);

        List<String> columnNames = new ArrayList<>();
        for (XNode col : columnsNode.childrenByTag("column")) {
            columnNames.add(col.attrText("code"));
        }

        assertTrue(columnNames.contains("SESSION_ID"), "SESSION_ID column must be defined");
        assertTrue(columnNames.contains("AGENT_NAME"), "AGENT_NAME column must be defined");
        assertTrue(columnNames.contains("STATUS"), "STATUS column must be defined");
        assertTrue(columnNames.contains("SESSION_DATA"), "SESSION_DATA column must be defined");
        assertTrue(columnNames.contains("CREATED_AT"), "CREATED_AT column must be defined");
        assertTrue(columnNames.contains("UPDATED_AT"), "UPDATED_AT column must be defined");
    }

    @Test
    void ormModelSessionIdIsPrimaryKey() {
        XNode ormNode = loadOrmModel();
        XNode entitiesNode = ormNode.childByTag("entities");
        XNode entityNode = entitiesNode.childByAttr("name", "io.nop.ai.agent.session.AiAgentSession");
        XNode columnsNode = entityNode.childByTag("columns");

        XNode sessionIdCol = null;
        for (XNode col : columnsNode.childrenByTag("column")) {
            if ("SESSION_ID".equals(col.attrText("code"))) {
                sessionIdCol = col;
                break;
            }
        }
        assertNotNull(sessionIdCol);
        assertEquals("true", sessionIdCol.attrText("primary"),
                "SESSION_ID must be the primary key");
        assertEquals("true", sessionIdCol.attrText("mandatory"),
                "SESSION_ID must be mandatory");
    }

    @Test
    void ddlCreatesTableAndIndexInH2() throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:test-session-ddl;DB_CLOSE_DELAY=-1", "sa", "");
             Statement stmt = conn.createStatement()) {

            stmt.execute(AiAgentSessionTable.DDL_CREATE_TABLE);
            stmt.execute(AiAgentSessionTable.DDL_CREATE_INDEX);

            assertTrue(tableExists(conn, AiAgentSessionTable.TABLE_NAME),
                    "Table " + AiAgentSessionTable.TABLE_NAME + " should exist after DDL execution");
            assertTrue(indexExists(conn, AiAgentSessionTable.INDEX_STATUS),
                    "Index " + AiAgentSessionTable.INDEX_STATUS + " should exist after DDL execution");
        }
    }

    @Test
    void ddlCreatesExpectedColumns() throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:test-session-cols;DB_CLOSE_DELAY=-1", "sa", "");
             Statement stmt = conn.createStatement()) {

            stmt.execute(AiAgentSessionTable.DDL_CREATE_TABLE);

            List<String> columnNames = new ArrayList<>();
            try (ResultSet rs = conn.getMetaData().getColumns(null, null,
                    AiAgentSessionTable.TABLE_NAME.toUpperCase(), null)) {
                while (rs.next()) {
                    columnNames.add(rs.getString("COLUMN_NAME"));
                }
            }

            assertFalse(columnNames.isEmpty(), "Table should have columns");
            assertTrue(columnNames.contains("SESSION_ID"));
            assertTrue(columnNames.contains("AGENT_NAME"));
            assertTrue(columnNames.contains("STATUS"));
            assertTrue(columnNames.contains("SESSION_DATA"));
            assertTrue(columnNames.contains("CREATED_AT"));
            assertTrue(columnNames.contains("UPDATED_AT"));
        }
    }

    @Test
    void ddlIsIdempotent() throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:test-session-idem;DB_CLOSE_DELAY=-1", "sa", "");
             Statement stmt = conn.createStatement()) {

            stmt.execute(AiAgentSessionTable.DDL_CREATE_TABLE);
            stmt.execute(AiAgentSessionTable.DDL_CREATE_TABLE);
            stmt.execute(AiAgentSessionTable.DDL_CREATE_INDEX);
            stmt.execute(AiAgentSessionTable.DDL_CREATE_INDEX);

            assertTrue(tableExists(conn, AiAgentSessionTable.TABLE_NAME));
        }
    }

    @Test
    void mergeUpsertAndSelectAndDeleteWork() throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:test-session-merge;DB_CLOSE_DELAY=-1", "sa", "");
             Statement stmt = conn.createStatement()) {

            stmt.execute(AiAgentSessionTable.DDL_CREATE_TABLE);

            // MERGE INTO (upsert) — first insert
            stmt.execute("MERGE INTO " + AiAgentSessionTable.TABLE_NAME
                    + " (" + AiAgentSessionTable.COL_SESSION_ID
                    + ", " + AiAgentSessionTable.COL_AGENT_NAME
                    + ", " + AiAgentSessionTable.COL_STATUS
                    + ", " + AiAgentSessionTable.COL_SESSION_DATA
                    + ", " + AiAgentSessionTable.COL_CREATED_AT
                    + ", " + AiAgentSessionTable.COL_UPDATED_AT
                    + ") KEY (" + AiAgentSessionTable.COL_SESSION_ID + ") VALUES "
                    + "('sessA', 'agent-1', 'running', '{\"v\":1}', 1000, 1001)");

            // Upsert same key with different data — must update, not duplicate
            stmt.execute("MERGE INTO " + AiAgentSessionTable.TABLE_NAME
                    + " (" + AiAgentSessionTable.COL_SESSION_ID
                    + ", " + AiAgentSessionTable.COL_AGENT_NAME
                    + ", " + AiAgentSessionTable.COL_STATUS
                    + ", " + AiAgentSessionTable.COL_SESSION_DATA
                    + ", " + AiAgentSessionTable.COL_CREATED_AT
                    + ", " + AiAgentSessionTable.COL_UPDATED_AT
                    + ") KEY (" + AiAgentSessionTable.COL_SESSION_ID + ") VALUES "
                    + "('sessA', 'agent-1', 'completed', '{\"v\":2}', 1000, 1002)");

            // Row count must be 1 (upsert, not insert)
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT COUNT(*) FROM " + AiAgentSessionTable.TABLE_NAME
                            + " WHERE " + AiAgentSessionTable.COL_SESSION_ID + " = 'sessA'")) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1), "MERGE INTO must be idempotent upsert (1 row, not 2)");
            }

            // The data reflects the latest upsert
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT " + AiAgentSessionTable.COL_STATUS
                            + " FROM " + AiAgentSessionTable.TABLE_NAME
                            + " WHERE " + AiAgentSessionTable.COL_SESSION_ID + " = 'sessA'")) {
                assertTrue(rs.next());
                assertEquals("completed", rs.getString(1));
            }

            // DELETE
            stmt.execute("DELETE FROM " + AiAgentSessionTable.TABLE_NAME
                    + " WHERE " + AiAgentSessionTable.COL_SESSION_ID + " = 'sessA'");
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT COUNT(*) FROM " + AiAgentSessionTable.TABLE_NAME
                            + " WHERE " + AiAgentSessionTable.COL_SESSION_ID + " = 'sessA'")) {
                assertTrue(rs.next());
                assertEquals(0, rs.getInt(1));
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
                AiAgentSessionTable.TABLE_NAME.toUpperCase(), false, false)) {
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
