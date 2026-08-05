package io.nop.metadata.service;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.graphql.GraphQLRequestBean;
import io.nop.api.core.beans.graphql.GraphQLResponseBean;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.graphql.core.engine.IGraphQLEngine;
import io.nop.metadata.dao.entity.NopMetaDataSource;
import io.nop.metadata.dao.entity.NopMetaModule;
import io.nop.metadata.dao.entity.NopMetaTable;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R4.2（plan-2026-08-05-1625-1）多 schema 支持行为回归测试：
 *
 * <p>修复前：UK_NOP_META_TABLE_MODULE_NAME = (metaModuleId, tableName, isDelta) 不含 metaSchema，
 * 同模块下两个不同 schema 的同名外部表在 upsertExternalTable 中必然撞 UK（duplicate key）。
 * 修复后：UK 扩展为 (metaModuleId, tableName, isDelta, metaSchema)（路径 A 保持可空），
 * 多 schema 同名表可共存——两 schema 各落一行、互不覆盖，重复同步为 update 而非追加（单 schema 回归）。
 *
 * <p>createSqlTable 重复守卫（D5）：4 列 UK 含可空 META_SCHEMA 后 SQL 表（恒 null-schema）
 * 第二次创建不再被 DB 层拦截，补 find-or-fail 守卫保持 fail-fast 语义——重复创建必须显式失败
 * （错误码 nop.err.metadata.sql-view-table-exists），不得静默成功。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestNopMetaTableMultiSchemaUpsert extends JunitBaseTestCase {

    public TestNopMetaTableMultiSchemaUpsert() {
        setTestConfig("nop.orm.init-database-schema", true);
    }

    @Inject
    IGraphQLEngine graphQLEngine;

    @Inject
    IDaoProvider daoProvider;

    /**
     * 端到端（Anti-Hollow）：同模块下两个不同 schema 的同名外部表从 syncExternalTables 入口
     * 到 DB 落盘——两 schema 两行互不覆盖；重复同步（单 schema 回归）为 update 而非追加。
     */
    @Test
    public void testMultiSchemaSameNameTablesCoexist() throws Exception {
        String querySpace = "qs_multi_schema";
        String dbUrl = "jdbc:h2:mem:" + querySpace + ";DB_CLOSE_DELAY=-1";
        try (Connection c = DriverManager.getConnection(dbUrl, "sa", "");
             Statement st = c.createStatement()) {
            st.execute("CREATE SCHEMA S1");
            st.execute("CREATE SCHEMA S2");
            st.execute("CREATE TABLE S1.EXT_SAME (id INT)");
            st.execute("CREATE TABLE S2.EXT_SAME (id INT)");
        }
        saveDataSource("ds-" + querySpace, querySpace, dbUrl);
        syncSchema("ds-" + querySpace, "S1");
        syncSchema("ds-" + querySpace, "S2");

        List<NopMetaTable> rows = findExternalTables("EXT_SAME");
        assertEquals(2, rows.size(), "two schemas must yield two rows (no UK collision): " + rows);
        assertTrue(rows.stream().anyMatch(t -> "S1".equals(t.getMetaSchema())),
                "S1 row must be persisted with metaSchema=S1: " + rows);
        assertTrue(rows.stream().anyMatch(t -> "S2".equals(t.getMetaSchema())),
                "S2 row must be persisted with metaSchema=S2: " + rows);
        assertTrue(rows.stream().map(NopMetaTable::getMetaTableId).distinct().count() == 2,
                "two schema rows must be distinct records");

        // 单 schema 回归：重复同步同一 schema 不追加（update 路径）
        syncSchema("ds-" + querySpace, "S1");
        assertEquals(2, findExternalTables("EXT_SAME").size(),
                "re-sync of S1 must update the existing row, not duplicate it");
    }

    /**
     * createSqlTable 重复守卫（D5）：同模块同表名第二次创建必须显式失败
     * （nop.err.metadata.sql-view-table-exists），保持 fail-fast 语义。
     */
    @Test
    public void testCreateSqlTableDuplicateFailsFast() {
        String moduleId = ensureExternalSystemModuleId();
        String sql = "SELECT id FROM t";
        GraphQLResponseBean first = graphQLEngine.executeGraphQL(graphQLEngine.newGraphQLContext(req(
                "mutation { NopMetaTable__createSqlTable(sql: \"" + sql + "\", "
                        + "tableName: \"sql_dup\", metaModuleId: \"" + moduleId + "\") { metaTableId } }")));
        assertFalse(first.hasError(), "first create must succeed: " + first);

        GraphQLResponseBean second = graphQLEngine.executeGraphQL(graphQLEngine.newGraphQLContext(req(
                "mutation { NopMetaTable__createSqlTable(sql: \"" + sql + "\", "
                        + "tableName: \"sql_dup\", metaModuleId: \"" + moduleId + "\") { metaTableId } }")));
        assertTrue(second.hasError(), "duplicate create must fail fast, not silently succeed: " + second);
        String msg = second.getErrors().get(0).getMessage();
        assertNotNull(msg, "error must carry a message");
        assertTrue(msg.contains("sql_dup"), "error must reference the duplicate tableName: " + msg);
        assertTrue(msg.contains("already exists"), "error must be the sql-view-table-exists code, got: " + msg);
    }

    // ============================ helpers ============================

    private void syncSchema(String dataSourceId, String schemaPattern) {
        GraphQLResponseBean resp = graphQLEngine.executeGraphQL(graphQLEngine.newGraphQLContext(req(
                "mutation { NopMetaDataSource__syncExternalTables(dataSourceId: \"" + dataSourceId
                        + "\", schemaPattern: \"" + schemaPattern + "\") { syncedTableCount errors { code message detail } } }")));
        assertFalse(resp.hasError(), "sync should not error: " + resp);
    }

    private List<NopMetaTable> findExternalTables(String tableName) {
        IEntityDao<NopMetaTable> tableDao = daoProvider.daoFor(NopMetaTable.class);
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaTable.PROP_NAME_tableName, tableName));
        q.addFilter(FilterBeans.eq("tableType", "external"));
        return tableDao.findAllByQuery(q);
    }

    private void saveDataSource(String id, String querySpace, String dbUrl) {
        IEntityDao<NopMetaDataSource> dao = daoProvider.daoFor(NopMetaDataSource.class);
        NopMetaDataSource ds = dao.newEntity();
        ds.setDataSourceId(id);
        ds.setQuerySpace(querySpace);
        ds.setName(id);
        ds.setDatasourceType("jdbc");
        ds.setConnectionConfig("{\"jdbcUrl\":\"" + dbUrl + "\",\"username\":\"sa\",\"password\":\"\","
                + "\"driverClassName\":\"org.h2.Driver\"}");
        ds.setStatus("ACTIVE");
        ds.setVersion(1L);
        Timestamp now = new Timestamp(System.currentTimeMillis());
        ds.setCreateTime(now);
        ds.setUpdateTime(now);
        dao.saveEntity(ds);
    }

    private String ensureExternalSystemModuleId() {
        IEntityDao<NopMetaModule> moduleDao = daoProvider.daoFor(NopMetaModule.class);
        NopMetaModule m = moduleDao.newEntity();
        m.setModuleId("nop/test-multischema-" + System.nanoTime());
        m.setModuleName("test-multischema");
        m.setDisplayName("test-multischema");
        m.setModuleVersion(1L);
        m.setStatus("RELEASED");
        m.setImportedAt(new Timestamp(System.currentTimeMillis()));
        moduleDao.saveEntity(m);
        return m.getMetaModuleId();
    }

    private GraphQLRequestBean req(String query) {
        GraphQLRequestBean request = new GraphQLRequestBean();
        request.setQuery(query);
        return request;
    }
}
