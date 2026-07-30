package io.nop.metadata.service;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.dao.api.IDaoProvider;
import io.nop.graphql.core.engine.IGraphQLEngine;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestBiSemanticResolveTableFields extends JunitBaseTestCase {

    public TestBiSemanticResolveTableFields() {
        setTestConfig("nop.orm.init-database-schema", true);
    }

    @Inject
    IGraphQLEngine graphQLEngine;

    @Inject
    IDaoProvider daoProvider;

    private BiSemanticTestHelper helper;

    @BeforeEach
    public void setUp() {
        helper = new BiSemanticTestHelper(graphQLEngine, daoProvider);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testResolveTableFieldsEntity() {
        String moduleId = helper.ensureModule("resolveEntityFields");
        String entityId = helper.saveEntity(moduleId, "EntityTypeA", "col1", "col2", "col3");
        String tableId = helper.saveEntityTable(moduleId, "entity_table", entityId);

        Map<String, Object> result = helper.resolveTableFields(tableId);
        assertEquals("entity", result.get("tableType"));
        List<Map<String, Object>> fields = (List<Map<String, Object>>) result.get("fields");
        assertTrue(fields.size() >= 3, "should contain at least col1/col2/col3");
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testResolveTableFieldsExternal() {
        String buildSql = "[{\"columnName\":\"amount\",\"dataType\":\"DOUBLE\",\"nullable\":true},"
                + "{\"columnName\":\"name\",\"dataType\":\"VARCHAR\",\"nullable\":false}]";
        String tableId = helper.saveExternalTable("T_RESOLVE_EXT", "qs_resolve_ext", buildSql);

        Map<String, Object> result = helper.resolveTableFields(tableId);
        assertEquals("external", result.get("tableType"));
        List<Map<String, Object>> fields = (List<Map<String, Object>>) result.get("fields");
        assertEquals(2, fields.size());
        assertEquals("amount", fields.get(0).get("name"));
        assertEquals("name", fields.get(1).get("name"));
        assertEquals("external", fields.get(0).get("sourceType"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testResolveTableFieldsSql() {
        String moduleId = helper.ensureModule("resolveSqlFields");
        String sql = "SELECT id, name, status FROM my_table";
        String tableId = helper.saveSqlTable(moduleId, "sql_table", sql);

        Map<String, Object> result = helper.resolveTableFields(tableId);
        assertEquals("sql", result.get("tableType"));
        List<Map<String, Object>> fields = (List<Map<String, Object>>) result.get("fields");
        assertTrue(fields.size() >= 3, "should contain id/name/status from SELECT");
    }

    @Test
    public void testResolveTableFieldsEntityBaseEntityIdNullFails() {
        String moduleId = helper.ensureModule("resolveFailEntity");
        String tableId = helper.saveEntityTable(moduleId, "entity_no_base", null);

        var resp = helper.runGraphQL(
                "query { NopMetaTable__resolveTableFields(metaTableId: \"" + helper.escapeGraphQL(tableId) + "\") { tableType fields { name } } }");
        assertTrue(resp.hasError(), "resolveTableFields should fail when baseEntityId is null");
    }

    @Test
    public void testResolveTableFieldsExternalBadBuildSqlFails() {
        String buildSql = "[invalid json]";
        String tableId = helper.saveExternalTable("external_bad", "test-query-space", buildSql);

        var resp = helper.runGraphQL(
                "query { NopMetaTable__resolveTableFields(metaTableId: \"" + helper.escapeGraphQL(tableId) + "\") { tableType fields { name } } }");
        assertTrue(resp.hasError(), "resolveTableFields should fail on bad buildSql JSON");
    }
}
