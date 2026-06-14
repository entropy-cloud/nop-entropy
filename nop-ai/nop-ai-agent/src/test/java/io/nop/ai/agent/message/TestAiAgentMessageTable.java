package io.nop.ai.agent.message;

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
 * Phase 1 tests for the {@code ai_agent_message} ORM model and DDL.
 *
 * <p>Verifies:
 * <ul>
 *   <li>The ORM model file exists in the classpath and is valid XML following the orm structure</li>
 *   <li>The DDL derived from the model creates the table successfully in H2</li>
 *   <li>The created table has the expected columns</li>
 * </ul>
 */
public class TestAiAgentMessageTable {

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
    void ormModelFileExistsAndIsValid() {
        XNode ormNode = loadOrmModel();
        assertNotNull(ormNode);
        assertEquals("orm", ormNode.getTagName());

        XNode entitiesNode = ormNode.childByTag("entities");
        assertNotNull(entitiesNode, "entities section should exist in the ORM model");
        XNode entityNode = entitiesNode.childByAttr("name", "io.nop.ai.agent.message.AiAgentMessage");
        assertNotNull(entityNode, "entity AiAgentMessage should be defined in the ORM model");
        assertEquals("ai_agent_message", entityNode.attrText("tableName"));
    }

    @Test
    void ormModelDefinesAllRequiredColumns() {
        XNode ormNode = loadOrmModel();
        XNode entitiesNode = ormNode.childByTag("entities");
        XNode entityNode = entitiesNode.childByAttr("name", "io.nop.ai.agent.message.AiAgentMessage");
        XNode columnsNode = entityNode.childByTag("columns");
        assertNotNull(columnsNode);

        List<String> columnNames = new ArrayList<>();
        for (XNode col : columnsNode.childrenByTag("column")) {
            columnNames.add(col.attrText("code"));
        }

        assertTrue(columnNames.contains("SID"), "SID column must be defined");
        assertTrue(columnNames.contains("TOPIC"), "TOPIC column must be defined");
        assertTrue(columnNames.contains("MESSAGE_BODY"), "MESSAGE_BODY column must be defined");
        assertTrue(columnNames.contains("STATUS"), "STATUS column must be defined");
        assertTrue(columnNames.contains("CONSUMER_ID"), "CONSUMER_ID column must be defined");
        assertTrue(columnNames.contains("CREATED_AT"), "CREATED_AT column must be defined");
        assertTrue(columnNames.contains("CLAIMED_AT"), "CLAIMED_AT column must be defined");
        assertTrue(columnNames.contains("CONSUMED_AT"), "CONSUMED_AT column must be defined");
    }

    @Test
    void ddlCreatesTableInH2() throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:test-orm-ddl;DB_CLOSE_DELAY=-1", "sa", "");
             Statement stmt = conn.createStatement()) {

            stmt.execute(AiAgentMessageTable.DDL_CREATE_TABLE);
            stmt.execute(AiAgentMessageTable.DDL_CREATE_INDEX);

            assertTrue(tableExists(conn, AiAgentMessageTable.TABLE_NAME),
                    "Table " + AiAgentMessageTable.TABLE_NAME + " should exist after DDL execution");
            assertTrue(indexExists(conn, "IDX_" + AiAgentMessageTable.TABLE_NAME + "_TOPIC_STATUS"),
                    "Index should exist after DDL execution");
        }
    }

    @Test
    void ddlCreatesExpectedColumns() throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:test-orm-cols;DB_CLOSE_DELAY=-1", "sa", "");
             Statement stmt = conn.createStatement()) {

            stmt.execute(AiAgentMessageTable.DDL_CREATE_TABLE);

            List<String> columnNames = new ArrayList<>();
            try (ResultSet rs = conn.getMetaData().getColumns(null, null,
                    AiAgentMessageTable.TABLE_NAME.toUpperCase(), null)) {
                while (rs.next()) {
                    columnNames.add(rs.getString("COLUMN_NAME"));
                }
            }

            assertFalse(columnNames.isEmpty(), "Table should have columns");
            assertTrue(columnNames.contains("SID"));
            assertTrue(columnNames.contains("TOPIC"));
            assertTrue(columnNames.contains("MESSAGE_BODY"));
            assertTrue(columnNames.contains("STATUS"));
            assertTrue(columnNames.contains("CONSUMER_ID"));
            assertTrue(columnNames.contains("CREATED_AT"));
            assertTrue(columnNames.contains("CLAIMED_AT"));
            assertTrue(columnNames.contains("CONSUMED_AT"));
        }
    }

    @Test
    void ddlIsIdempotent() throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:test-orm-idem;DB_CLOSE_DELAY=-1", "sa", "");
             Statement stmt = conn.createStatement()) {

            stmt.execute(AiAgentMessageTable.DDL_CREATE_TABLE);
            stmt.execute(AiAgentMessageTable.DDL_CREATE_TABLE);
            stmt.execute(AiAgentMessageTable.DDL_CREATE_INDEX);
            stmt.execute(AiAgentMessageTable.DDL_CREATE_INDEX);

            assertTrue(tableExists(conn, AiAgentMessageTable.TABLE_NAME));
        }
    }

    @Test
    void insertAndQueryMessage() throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:test-orm-insert;DB_CLOSE_DELAY=-1", "sa", "");
             Statement stmt = conn.createStatement()) {

            stmt.execute(AiAgentMessageTable.DDL_CREATE_TABLE);

            stmt.execute("INSERT INTO " + AiAgentMessageTable.TABLE_NAME
                    + " (SID, TOPIC, MESSAGE_BODY, STATUS, CONSUMER_ID, CREATED_AT) VALUES "
                    + "('msg-1', 'agent.B.inbox', '{\"hello\":true}', 0, NULL, CURRENT_TIMESTAMP)");

            try (ResultSet rs = stmt.executeQuery(
                    "SELECT SID, TOPIC, STATUS FROM " + AiAgentMessageTable.TABLE_NAME)) {
                assertTrue(rs.next());
                assertEquals("msg-1", rs.getString("SID"));
                assertEquals("agent.B.inbox", rs.getString("TOPIC"));
                assertEquals(0, rs.getInt("STATUS"));
                assertFalse(rs.next());
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
                AiAgentMessageTable.TABLE_NAME.toUpperCase(), false, false)) {
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
