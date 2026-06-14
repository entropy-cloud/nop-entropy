package io.nop.ai.agent.security;

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
 * Phase 1 tests for the {@code ai_agent_denial} ORM model and DDL.
 *
 * <p>Verifies:
 * <ul>
 *   <li>The ORM model file defines the {@code ai_agent_denial} entity with the
 *       expected columns</li>
 *   <li>The DDL derived from the model creates the table + index successfully
 *       in H2</li>
 *   <li>The created table has the expected columns</li>
 *   <li>The DDL is idempotent (safe to re-run)</li>
 *   <li>Insert + per-session COUNT/DELETE work against the table</li>
 * </ul>
 */
public class TestAiAgentDenialTable {

    private static final String ORM_MODEL_PATH = "_vfs/nop/ai/agent/orm/app.orm.xml";

    private XNode loadOrmModel() {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(ORM_MODEL_PATH)) {
            assertNotNull(is, "ORM model file should exist in classpath: " + ORM_MODEL_PATH);
            return XNodeParser.instance().parseFromText(null, new String(is.readAllBytes()));
        } catch (Exception e) {
            throw new io.nop.ai.api.exceptions.NopAiException("Failed to load ORM model: " + ORM_MODEL_PATH, e);
        }
    }

    @Test
    void ormModelDefinesDenialEntity() {
        XNode ormNode = loadOrmModel();
        assertNotNull(ormNode);
        assertEquals("orm", ormNode.getTagName());

        XNode entitiesNode = ormNode.childByTag("entities");
        assertNotNull(entitiesNode, "entities section should exist in the ORM model");
        XNode entityNode = entitiesNode.childByAttr("name", "io.nop.ai.agent.security.AiAgentDenial");
        assertNotNull(entityNode, "entity AiAgentDenial should be defined in the ORM model");
        assertEquals("ai_agent_denial", entityNode.attrText("tableName"));
    }

    @Test
    void ormModelDefinesAllRequiredColumns() {
        XNode ormNode = loadOrmModel();
        XNode entitiesNode = ormNode.childByTag("entities");
        XNode entityNode = entitiesNode.childByAttr("name", "io.nop.ai.agent.security.AiAgentDenial");
        XNode columnsNode = entityNode.childByTag("columns");
        assertNotNull(columnsNode);

        List<String> columnNames = new ArrayList<>();
        for (XNode col : columnsNode.childrenByTag("column")) {
            columnNames.add(col.attrText("code"));
        }

        assertTrue(columnNames.contains("SID"), "SID column must be defined");
        assertTrue(columnNames.contains("SESSION_ID"), "SESSION_ID column must be defined");
        assertTrue(columnNames.contains("TOOL_NAME"), "TOOL_NAME column must be defined");
        assertTrue(columnNames.contains("LAYER_SOURCE"), "LAYER_SOURCE column must be defined");
        assertTrue(columnNames.contains("REASON"), "REASON column must be defined");
        assertTrue(columnNames.contains("MATCHED_RULE"), "MATCHED_RULE column must be defined");
        assertTrue(columnNames.contains("DENIAL_TIMESTAMP"), "DENIAL_TIMESTAMP column must be defined");
        assertTrue(columnNames.contains("CREATED_AT"), "CREATED_AT column must be defined");
    }

    @Test
    void ddlCreatesTableAndIndexInH2() throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:test-denial-ddl;DB_CLOSE_DELAY=-1", "sa", "");
             Statement stmt = conn.createStatement()) {

            stmt.execute(AiAgentDenialTable.DDL_CREATE_TABLE);
            stmt.execute(AiAgentDenialTable.DDL_CREATE_INDEX);

            assertTrue(tableExists(conn, AiAgentDenialTable.TABLE_NAME),
                    "Table " + AiAgentDenialTable.TABLE_NAME + " should exist after DDL execution");
            assertTrue(indexExists(conn, AiAgentDenialTable.INDEX_SESSION_ID),
                    "Index " + AiAgentDenialTable.INDEX_SESSION_ID + " should exist after DDL execution");
        }
    }

    @Test
    void ddlCreatesExpectedColumns() throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:test-denial-cols;DB_CLOSE_DELAY=-1", "sa", "");
             Statement stmt = conn.createStatement()) {

            stmt.execute(AiAgentDenialTable.DDL_CREATE_TABLE);

            List<String> columnNames = new ArrayList<>();
            try (ResultSet rs = conn.getMetaData().getColumns(null, null,
                    AiAgentDenialTable.TABLE_NAME.toUpperCase(), null)) {
                while (rs.next()) {
                    columnNames.add(rs.getString("COLUMN_NAME"));
                }
            }

            assertFalse(columnNames.isEmpty(), "Table should have columns");
            assertTrue(columnNames.contains("SID"));
            assertTrue(columnNames.contains("SESSION_ID"));
            assertTrue(columnNames.contains("TOOL_NAME"));
            assertTrue(columnNames.contains("LAYER_SOURCE"));
            assertTrue(columnNames.contains("REASON"));
            assertTrue(columnNames.contains("MATCHED_RULE"));
            assertTrue(columnNames.contains("DENIAL_TIMESTAMP"));
            assertTrue(columnNames.contains("CREATED_AT"));
        }
    }

    @Test
    void ddlIsIdempotent() throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:test-denial-idem;DB_CLOSE_DELAY=-1", "sa", "");
             Statement stmt = conn.createStatement()) {

            stmt.execute(AiAgentDenialTable.DDL_CREATE_TABLE);
            stmt.execute(AiAgentDenialTable.DDL_CREATE_TABLE);
            stmt.execute(AiAgentDenialTable.DDL_CREATE_INDEX);
            stmt.execute(AiAgentDenialTable.DDL_CREATE_INDEX);

            assertTrue(tableExists(conn, AiAgentDenialTable.TABLE_NAME));
        }
    }

    @Test
    void insertAndCountPerSession() throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:test-denial-insert;DB_CLOSE_DELAY=-1", "sa", "");
             Statement stmt = conn.createStatement()) {

            stmt.execute(AiAgentDenialTable.DDL_CREATE_TABLE);

            stmt.execute("INSERT INTO " + AiAgentDenialTable.TABLE_NAME
                    + " (SID, SESSION_ID, TOOL_NAME, LAYER_SOURCE, REASON, MATCHED_RULE,"
                    + " DENIAL_TIMESTAMP, CREATED_AT) VALUES "
                    + "('d-1', 'sessA', 'shell.exec', 'LAYER1_TOOL_ACCESS', 'deny', 'rule', 1000, CURRENT_TIMESTAMP)");
            stmt.execute("INSERT INTO " + AiAgentDenialTable.TABLE_NAME
                    + " (SID, SESSION_ID, TOOL_NAME, LAYER_SOURCE, REASON, MATCHED_RULE,"
                    + " DENIAL_TIMESTAMP, CREATED_AT) VALUES "
                    + "('d-2', 'sessA', 'shell.exec', 'LAYER3_APPROVAL_GATE', 'deny', 'rule', 1001, CURRENT_TIMESTAMP)");
            stmt.execute("INSERT INTO " + AiAgentDenialTable.TABLE_NAME
                    + " (SID, SESSION_ID, TOOL_NAME, LAYER_SOURCE, REASON, MATCHED_RULE,"
                    + " DENIAL_TIMESTAMP, CREATED_AT) VALUES "
                    + "('d-3', 'sessB', 'shell.exec', 'LAYER1_TOOL_ACCESS', 'deny', 'rule', 1002, CURRENT_TIMESTAMP)");

            // Per-session COUNT: sessA has 2, sessB has 1.
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT COUNT(*) FROM " + AiAgentDenialTable.TABLE_NAME
                            + " WHERE " + AiAgentDenialTable.COL_SESSION_ID + " = 'sessA'")) {
                assertTrue(rs.next());
                assertEquals(2, rs.getInt(1));
            }
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT COUNT(*) FROM " + AiAgentDenialTable.TABLE_NAME
                            + " WHERE " + AiAgentDenialTable.COL_SESSION_ID + " = 'sessB'")) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1));
            }

            // Per-session DELETE clears only the targeted session.
            stmt.execute("DELETE FROM " + AiAgentDenialTable.TABLE_NAME
                    + " WHERE " + AiAgentDenialTable.COL_SESSION_ID + " = 'sessA'");
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT COUNT(*) FROM " + AiAgentDenialTable.TABLE_NAME
                            + " WHERE " + AiAgentDenialTable.COL_SESSION_ID + " = 'sessB'")) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1), "sessB must remain after deleting sessA");
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
                AiAgentDenialTable.TABLE_NAME.toUpperCase(), false, false)) {
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
