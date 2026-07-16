/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.metadata.service;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.TreeBean;
import io.nop.api.core.beans.graphql.GraphQLRequestBean;
import io.nop.api.core.beans.graphql.GraphQLResponseBean;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.graphql.core.engine.IGraphQLEngine;
import io.nop.metadata.dao.entity.NopMetaDataSource;
import io.nop.metadata.dao.entity.NopMetaEntity;
import io.nop.metadata.dao.entity.NopMetaModule;
import io.nop.metadata.dao.entity.NopMetaTable;
import io.nop.metadata.service.entity.NopMetaTableBizModel;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证统一单表查询 queryTableData（架构基线 §4.4 D1/D2）：entity/external/sql 三类 tableType 端到端
 * 真实返回行数据（非空壳）+ 失败路径显式失败 + filter→WHERE 注入防护 + 方言范围。
 *
 * <p>Map 返回的 @BizQuery action 不支持 GraphQL 字段选择，统一按整 Map 返回后从 result 取字段
 * （与 previewSqlFields/profileTable 一致）。
 *
 * <p>Anti-Hollow：entity 路径真实 importOrmModel 后查 NopMetaModule 实体表；external/sql 路径真实 H2 建连 +
 * 物理表造数，断言返回行数据与实测数据一致，证明运行时确实通过 ORM（entity）/ withConnection（external/sql）建连并执行了 SQL。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestNopMetaTableQueryBizModel extends JunitBaseTestCase {

    public TestNopMetaTableQueryBizModel() {
        setTestConfig("nop.orm.init-database-schema", true);
    }

    @Inject
    IGraphQLEngine graphQLEngine;

    @Inject
    IDaoProvider daoProvider;

    @Inject
    NopMetaTableBizModel nopMetaTableBizModel;

    // ===== entity 分派：经 IOrmTemplate（架构基线 §4.4 D1）=====

    /** entity 表：importOrmModel 后查 NopMetaModule 实体表 → 返回真实导入的 module 行。 */
    @Test
    @SuppressWarnings("unchecked")
    public void testQueryEntityTableReturnsRows() {
        execute("mutation { NopMetaModule__importOrmModel(path: \"/nop/metadata/orm/app.orm.xml\")" +
                " { metaModuleId } }");
        String tableId = findEntityTableId("nop_meta_module");

        // 直接调 BizModel（GraphQL Map 返回不支持字段选择；此处验证 entity→ORM 真实分派）
        Map<String, Object> result = nopMetaTableBizModel.queryTableData(tableId, null, null, null, null);
        assertEquals("entity", result.get("tableType"));
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.get("items");
        assertNotNull(items, "items must not be null");
        // 接线验证：entity 路径确实通过 ORM 返回了真实导入的 module 行（非空壳）
        assertFalse(items.isEmpty(), "entity query must return real imported rows (not empty stub): " + items);
    }

    /** entity 表 + filter（经 ORM QueryBean.setFilter）：过滤不匹配 → 0 行（验证 filter 接线到 ORM）。 */
    @Test
    @SuppressWarnings("unchecked")
    public void testQueryEntityTableWithFilter() {
        execute("mutation { NopMetaModule__importOrmModel(path: \"/nop/metadata/orm/app.orm.xml\")" +
                " { metaModuleId } }");
        String tableId = findEntityTableId("nop_meta_module");

        // 直接调 BizModel 传 filter（TreeBean 非 GraphQL 命名类型）
        TreeBean filter = FilterBeans.eq("moduleId", "__never_matches_anything__");
        Map<String, Object> result = nopMetaTableBizModel.queryTableData(tableId, filter, null, null, null);
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.get("items");
        assertTrue(items.isEmpty(), "entity filter moduleId=__nope__ must return 0 rows (filter wired to ORM)");
    }

    // ===== external 分派：withConnection 跑限定表名原生 SQL（架构基线 §4.4 D1）=====

    /** external 表：H2 造数 → syncExternalTables → queryTableData 返回真实行数据。 */
    @Test
    @SuppressWarnings("unchecked")
    public void testQueryExternalTableReturnsRows() throws Exception {
        String dbUrl = "jdbc:h2:mem:meta_q_ext;DB_CLOSE_DELAY=-1";
        seedTable(dbUrl, "CREATE TABLE ext_q (id INT NOT NULL, amount INT, name VARCHAR(20))",
                "INSERT INTO ext_q VALUES (1, 10, 'aaa')",
                "INSERT INTO ext_q VALUES (2, 20, 'bb')",
                "INSERT INTO ext_q VALUES (3, 30, 'ccc')");
        String tableId = prepareExternalTable(dbUrl, "qs_q_ext", "EXT_Q");

        Map<String, Object> result = queryTableData(tableId, null, null, null);
        assertEquals("external", result.get("tableType"));
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.get("items");
        assertEquals(3, items.size(), "external query must return all 3 real rows");

        // 真实数据断言（stub 立即失败这些断言）
        Map<String, Object> row0 = findRowById(items, 1);
        assertNotNull(row0, "row with id=1 must exist");
        assertEquals(10, toInt(row0.get("AMOUNT")), "amount must match seeded data");
        assertEquals("aaa", String.valueOf(row0.get("NAME")), "name must match seeded data");
    }

    /** external 表 + filter(amount > 15)：直接调 BizModel 传 TreeBean filter，验证 WHERE 翻译正确。 */
    @Test
    @SuppressWarnings("unchecked")
    public void testQueryExternalTableWithFilter() throws Exception {
        String dbUrl = "jdbc:h2:mem:meta_q_ext_f;DB_CLOSE_DELAY=-1";
        seedTable(dbUrl, "CREATE TABLE ext_qf (id INT NOT NULL, amount INT)",
                "INSERT INTO ext_qf VALUES (1, 10)",
                "INSERT INTO ext_qf VALUES (2, 20)",
                "INSERT INTO ext_qf VALUES (3, 30)",
                "INSERT INTO ext_qf VALUES (4, 40)");
        String tableId = prepareExternalTable(dbUrl, "qs_q_ext_f", "EXT_QF");

        // 直接调 BizModel action（TreeBean 非 GraphQL 命名类型，经 GraphQL 变量传不进来；
        // filter→WHERE 翻译由 TestFilterToSqlTranslator 单测覆盖，此处验证整条 external+filter+withConnection 链路）
        TreeBean filter = FilterBeans.gt("AMOUNT", 15);
        Map<String, Object> result = nopMetaTableBizModel.queryTableData(tableId, filter, null, null, null);
        assertEquals("external", result.get("tableType"));
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.get("items");
        assertEquals(3, items.size(), "filter amount>15 must return 3 rows (20,30,40)");
    }

    /** external 表 + limit/offset 分页（无 filter）。 */
    @Test
    @SuppressWarnings("unchecked")
    public void testQueryExternalTablePagination() throws Exception {
        String dbUrl = "jdbc:h2:mem:meta_q_ext_p;DB_CLOSE_DELAY=-1";
        seedTable(dbUrl, "CREATE TABLE ext_qp (id INT NOT NULL)",
                "INSERT INTO ext_qp VALUES (1)", "INSERT INTO ext_qp VALUES (2)",
                "INSERT INTO ext_qp VALUES (3)", "INSERT INTO ext_qp VALUES (4)");
        String tableId = prepareExternalTable(dbUrl, "qs_q_ext_p", "EXT_QP");

        Map<String, Object> result = queryTableData(tableId, null, 2L, 1L);
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.get("items");
        assertEquals(2, items.size(), "limit=2 offset=1 must return 2 rows");
    }

    // ===== sql 分派：withConnection 执行 sourceSql 子查询（架构基线 §4.4 D1 + D2）=====

    /** sql 表：H2 造数 → createSqlTable(sourceSql, querySpace) → queryTableData 返回真实行数据。 */
    @Test
    @SuppressWarnings("unchecked")
    public void testQuerySqlTableReturnsRows() throws Exception {
        String dbUrl = "jdbc:h2:mem:meta_q_sql;DB_CLOSE_DELAY=-1";
        seedTable(dbUrl, "CREATE TABLE sql_src (id INT NOT NULL, val INT)",
                "INSERT INTO sql_src VALUES (1, 100)",
                "INSERT INTO sql_src VALUES (2, 200)");
        // 注册数据源（D2：sql 表 querySpace 必须匹配 NopMetaDataSource）
        saveDataSource("ds-q-sql", "qs_q_sql", "jdbc", "ACTIVE", dbUrl);

        String tableId = createSqlTable("SELECT id, val FROM sql_src", "sql_q_tab", "qs_q_sql");

        Map<String, Object> result = queryTableData(tableId, null, null, null);
        assertEquals("sql", result.get("tableType"));
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.get("items");
        assertEquals(2, items.size(), "sql query must return 2 real rows");
        // 真实数据断言（sourceSql 子查询返回真实列）
        Map<String, Object> row0 = findRowById(items, 1);
        assertNotNull(row0, "row with id=1 must exist");
        assertEquals(100, toInt(row0.get("VAL")), "val must match seeded data");
    }

    // ===== 失败路径显式失败（不静默空集，Minimum Rules #24）=====

    /** 表不存在 → 显式失败（不 NPE）。 */
    @Test
    public void testQueryTableNotFound() {
        GraphQLResponseBean resp = execute(
                "query { NopMetaTable__queryTableData(metaTableId: \"__nope__\") }");
        assertTrue(resp.hasError(), "non-existent table must explicitly fail (no NPE): " + resp);
    }

    /** external 表无注册数据源 → 显式失败。 */
    @Test
    public void testQueryExternalTableNoDataSource() {
        NopMetaTable ext = saveManualTable("EXT_Q_NDS", "external", "qs_no_ds_q");
        assertTrue(queryTableDataHasError(ext.getMetaTableId()),
                "external table with no datasource must explicitly fail");
    }

    /** DISABLED 数据源 → 显式失败。 */
    @Test
    public void testQueryTableDataSourceDisabled() {
        saveDataSource("ds-q-disabled", "qs_q_disabled", "jdbc", "DISABLED",
                "jdbc:h2:mem:meta_q_d;DB_CLOSE_DELAY=-1");
        NopMetaTable ext = saveManualTable("EXT_QD", "external", "qs_q_disabled");
        assertTrue(queryTableDataHasError(ext.getMetaTableId()),
                "DISABLED datasource must explicitly fail");
    }

    /** sql 表 querySpace 为 null → 显式失败（D2：null/无匹配显式失败，不静默空集、不伪造路由）。 */
    @Test
    public void testQuerySqlTableNullQuerySpace() {
        String tableId = createSqlTable("SELECT 1 AS one", "sql_null_qs", null);
        assertTrue(queryTableDataHasError(tableId),
                "sql table with null querySpace must explicitly fail (D2)");
    }

    /** sql 表 querySpace 无匹配数据源 → 显式失败。 */
    @Test
    public void testQuerySqlTableNoMatchingDataSource() {
        String tableId = createSqlTable("SELECT 1 AS one", "sql_no_ds", "qs_missing_q");
        assertTrue(queryTableDataHasError(tableId),
                "sql table with no matching datasource must explicitly fail");
    }

    /** entity 表 baseEntityId 指向未注册的实体 → 显式失败（不静默空集）。 */
    @Test
    public void testQueryEntityTableNotRegistered() {
        IEntityDao<NopMetaEntity> entityDao = daoProvider.daoFor(NopMetaEntity.class);
        NopMetaEntity fakeEntity = entityDao.newEntity();
        fakeEntity.setOrmModelId("fake-orm-model");
        fakeEntity.setEntityName("__not_a_registered_entity__");
        fakeEntity.setTableName("__fake_table__");
        fakeEntity.setClassName("x");
        fakeEntity.setVersion(1L);
        entityDao.saveEntity(fakeEntity);

        // 创建 entity 表时即设置 baseEntityId（避免脱离 session 后再 update）
        IEntityDao<NopMetaTable> tableDao = daoProvider.daoFor(NopMetaTable.class);
        NopMetaTable table = tableDao.newEntity();
        table.setMetaModuleId(ensureExternalSystemModuleId());
        table.setTableName("__FAKE_ENTITY_TABLE__");
        table.setDisplayName("__FAKE_ENTITY_TABLE__");
        table.setTableType("entity");
        table.setBaseEntityId(fakeEntity.getMetaEntityId());
        table.setVersion(1L);
        tableDao.saveEntity(table);

        assertTrue(queryTableDataHasError(table.getMetaTableId()),
                "entity not registered in runtime ORM must explicitly fail");
    }

    // ===== helpers =====

    /** 调 queryTableData（无 filter 变量），返回完整 result Map。 */
    @SuppressWarnings("unchecked")
    private Map<String, Object> queryTableData(String tableId, Object filter, Long limit, Long offset) {
        StringBuilder q = new StringBuilder("query { NopMetaTable__queryTableData(metaTableId: \"").append(tableId).append("\"");
        if (limit != null) {
            q.append(", limit: ").append(limit);
        }
        if (offset != null) {
            q.append(", offset: ").append(offset);
        }
        q.append(") }");
        GraphQLResponseBean resp = execute(q.toString());
        assertFalse(resp.hasError(), "queryTableData should succeed: " + resp.getErrorCode()
                + " :: " + (resp.getErrors() == null ? "" : resp.getErrors()));
        return (Map<String, Object>) ((Map<String, Object>) resp.getData()).get("NopMetaTable__queryTableData");
    }

    private boolean queryTableDataHasError(String tableId) {
        GraphQLResponseBean resp = execute(
                "query { NopMetaTable__queryTableData(metaTableId: \"" + tableId + "\") }");
        return resp.hasError();
    }

    private GraphQLResponseBean execute(String query) {
        GraphQLRequestBean request = new GraphQLRequestBean();
        request.setQuery(query);
        return graphQLEngine.executeGraphQL(graphQLEngine.newGraphQLContext(request));
    }

    /** 创建 sql 表（createSqlTable，Map 整体返回），返回 metaTableId。 */
    @SuppressWarnings("unchecked")
    private String createSqlTable(String sql, String tableName, String querySpace) {
        StringBuilder q = new StringBuilder("mutation { NopMetaTable__createSqlTable(sql: \"")
                .append(escapeGraphQL(sql)).append("\", tableName: \"").append(tableName)
                .append("\", metaModuleId: \"").append(ensureExternalSystemModuleId()).append("\"");
        if (querySpace != null) {
            q.append(", querySpace: \"").append(querySpace).append("\"");
        }
        q.append(") }");
        GraphQLResponseBean resp = execute(q.toString());
        assertFalse(resp.hasError(), "createSqlTable should succeed: " + resp);
        return String.valueOf(((Map<String, Object>) ((Map<String, Object>) resp.getData())
                .get("NopMetaTable__createSqlTable")).get("metaTableId"));
    }

    /** 端到端：建数据源 + 同步 external 表结构，返回 metaTableId。 */
    private String prepareExternalTable(String dbUrl, String querySpace, String expectedTable) {
        saveDataSource("ds-" + querySpace, querySpace, "jdbc", "ACTIVE", dbUrl);
        GraphQLResponseBean syncResp = execute(
                "mutation { NopMetaDataSource__syncExternalTables(dataSourceId: \"ds-" + querySpace
                        + "\", schemaPattern: \"PUBLIC\") }");
        assertFalse(syncResp.hasError(), "sync should not error: " + syncResp);
        return tableId(expectedTable);
    }

    private void saveDataSource(String id, String querySpace, String datasourceType, String status, String dbUrl) {
        IEntityDao<NopMetaDataSource> dao = daoProvider.daoFor(NopMetaDataSource.class);
        NopMetaDataSource ds = dao.newEntity();
        ds.setDataSourceId(id);
        ds.setQuerySpace(querySpace);
        ds.setName(id);
        ds.setDatasourceType(datasourceType);
        ds.setConnectionConfig("{\"jdbcUrl\":\"" + dbUrl + "\",\"username\":\"sa\",\"password\":\"\","
                + "\"driverClassName\":\"org.h2.Driver\"}");
        ds.setStatus(status);
        ds.setVersion(1L);
        ds.setCreatedBy("autotest");
        ds.setUpdatedBy("autotest");
        Timestamp now = new Timestamp(System.currentTimeMillis());
        ds.setCreateTime(now);
        ds.setUpdateTime(now);
        dao.saveEntity(ds);
    }

    private String tableId(String tableName) {
        IEntityDao<NopMetaTable> tableDao = daoProvider.daoFor(NopMetaTable.class);
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaTable.PROP_NAME_tableName, tableName));
        q.addFilter(FilterBeans.eq("tableType", "external"));
        NopMetaTable t = tableDao.findFirstByQuery(q);
        assertNotNull(t, "external table " + tableName + " must be synced before query");
        return t.getMetaTableId();
    }

    /** 查找 importOrmModel 创建的 entity 表（按 tableName + tableType=entity）。 */
    private String findEntityTableId(String tableName) {
        IEntityDao<NopMetaTable> tableDao = daoProvider.daoFor(NopMetaTable.class);
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaTable.PROP_NAME_tableName, tableName));
        q.addFilter(FilterBeans.eq("tableType", "entity"));
        NopMetaTable t = tableDao.findFirstByQuery(q);
        assertNotNull(t, "entity table " + tableName + " must be created by importOrmModel");
        return t.getMetaTableId();
    }

    private NopMetaTable saveManualTable(String tableName, String tableType, String querySpace) {
        IEntityDao<NopMetaTable> tableDao = daoProvider.daoFor(NopMetaTable.class);
        NopMetaTable t = tableDao.newEntity();
        t.setMetaModuleId(ensureExternalSystemModuleId());
        t.setTableName(tableName);
        t.setDisplayName(tableName);
        t.setTableType(tableType);
        if (querySpace != null) {
            t.setQuerySpace(querySpace);
        }
        t.setVersion(1L);
        tableDao.saveEntity(t);
        return t;
    }

    private String ensureExternalSystemModuleId() {
        IEntityDao<NopMetaModule> moduleDao = daoProvider.daoFor(NopMetaModule.class);
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaModule.PROP_NAME_moduleId, "nop/meta-external"));
        NopMetaModule module = moduleDao.findFirstByQuery(q);
        if (module != null) {
            return module.getMetaModuleId();
        }
        module = moduleDao.newEntity();
        module.setModuleId("nop/meta-external");
        module.setModuleName("meta-external");
        module.setDisplayName("外部表系统模块");
        module.setModuleVersion(1L);
        module.setStatus("RELEASED");
        module.setImportedAt(new Timestamp(System.currentTimeMillis()));
        moduleDao.saveEntity(module);
        return module.getMetaModuleId();
    }

    private void seedTable(String dbUrl, String createDdl, String... inserts) throws Exception {
        try (Connection c = DriverManager.getConnection(dbUrl, "sa", "");
             Statement st = c.createStatement()) {
            st.execute(createDdl);
            for (String ins : inserts) {
                st.execute(ins);
            }
        }
    }

    private static String escapeGraphQL(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> findRowById(List<Map<String, Object>> items, int id) {
        for (Map<String, Object> row : items) {
            for (Object v : row.values()) {
                if (v != null && (v instanceof Number) && ((Number) v).intValue() == id) {
                    return row;
                }
            }
        }
        return null;
    }

    private static int toInt(Object v) {
        if (v == null) {
            return 0;
        }
        return ((Number) v).intValue();
    }
}
