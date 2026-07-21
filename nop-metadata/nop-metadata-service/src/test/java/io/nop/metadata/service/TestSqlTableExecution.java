package io.nop.metadata.service;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.graphql.GraphQLRequestBean;
import io.nop.api.core.beans.graphql.GraphQLResponseBean;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.core.lang.json.JsonTool;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.graphql.core.engine.IGraphQLEngine;
import io.nop.metadata.dao.entity.NopMetaCatalog;
import io.nop.metadata.dao.entity.NopMetaDataSource;
import io.nop.metadata.dao.entity.NopMetaModule;
import io.nop.metadata.dao.entity.NopMetaProfilingResult;
import io.nop.metadata.dao.entity.NopMetaQualityRule;
import io.nop.metadata.dao.entity.NopMetaTable;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 2 端到端验证：sql 类型表 × Catalog/Quality/Profiling 三大执行器（架构基线 §4.4.3 D2-D5）。
 *
 * <p>Anti-Hollow：所有成功路径用真实 H2 建连 + 真实数据，经 BizModel action 入口（collectCatalogForTable /
 * executeQualityRule / profileTable）→ table-reference 解析 → withConnection 子查询执行 → executor 产出结果，
 * 断言 rowCount/检测结果/统计值真实非空。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestSqlTableExecution extends JunitBaseTestCase {

    public TestSqlTableExecution() {
        setTestConfig("nop.orm.init-database-schema", true);
    }

    @Inject
    IGraphQLEngine graphQLEngine;

    @Inject
    IDaoProvider daoProvider;

    // ===== Catalog × sql =====

    @Test
    public void testSqlCatalogCollectsRowCount() throws Exception {
        PreparedEnv env = prepare("qs_sql_cat", "SELECT amount, name FROM test_data");

        GraphQLResponseBean resp = graphQLEngine.executeGraphQL(graphQLEngine.newGraphQLContext(req(
                "mutation { NopMetaDataSource__collectCatalogForTable(metaTableId: \"" + env.tableId + "\") { tableCount tables { tableName metaSchema tableType rowCount sizeBytes } errors { code message detail } } }")));
        assertFalse(resp.hasError(), "sql catalog should not error: " + resp);

        NopMetaCatalog row = findCatalogRow(env.tableId);
        assertNotNull(row, "catalog row must be written for sql table");
        assertEquals(4L, row.getRowCount().longValue(), "rowCount must be real COUNT(*) on subquery");
        assertTrue(row.getIndexCount() == null, "sql subquery has no physical indexes (null, not fabricated 0)");
        assertTrue(row.getDetails().contains("indexCount"), "indexCount must be marked unavailable in details");
    }

    // ===== Quality × sql =====

    @Test
    public void testSqlQualityVolume() throws Exception {
        PreparedEnv env = prepare("qs_sql_qvol", "SELECT amount, name FROM test_data");
        saveQualityRule("qr-sql-vol", "volume", "table", env.tableId, 2.0, "{\"minRows\":2}");

        GraphQLResponseBean resp = execRule("qr-sql-vol");
        assertFalse(resp.hasError(), "sql quality volume should not error: " + resp);
        assertStatus(resp, "PASS");
        assertActualValue(resp, 4.0);
    }

    @Test
    public void testSqlQualityNotNull() throws Exception {
        PreparedEnv env = prepare("qs_sql_qnn", "SELECT amount, name FROM test_data");
        saveQualityRule("qr-sql-nn", "not_null", "field", env.tableId, 0.0, "{\"column\":\"amount\"}");

        GraphQLResponseBean resp = execRule("qr-sql-nn");
        assertFalse(resp.hasError(), "sql quality not_null should not error: " + resp);
        assertStatus(resp, "PASS");
    }

    @Test
    public void testSqlQualityDerivedColumnSkipped() throws Exception {
        // D5：sql 视图合成列 <expr_1> 在 field 级检查 → 显式 SKIP + reason=derived-column-skipped（不整表失败）
        PreparedEnv env = prepare("qs_sql_d5", "SELECT amount, amount * 2 FROM test_data");
        saveQualityRule("qr-sql-d5", "not_null", "field", env.tableId, 0.0, "{\"column\":\"<expr_1>\"}");

        GraphQLResponseBean resp = execRule("qr-sql-d5");
        assertFalse(resp.hasError(), "derived column rule should SKIP not error: " + resp);
        String data = String.valueOf(resp.getData());
        assertTrue(data.contains("SKIP"), "derived column must be SKIP: " + data);
        assertTrue(data.contains("derived-column-skipped"), "must have reason=derived-column-skipped: " + data);
    }

    // ===== Profiling × sql =====

    @Test
    public void testSqlProfilingNumericStats() throws Exception {
        PreparedEnv env = prepare("qs_sql_prof", "SELECT amount, name FROM test_data");

        GraphQLResponseBean resp = graphQLEngine.executeGraphQL(graphQLEngine.newGraphQLContext(req(
                "mutation { NopMetaTable__profileTable(metaTableId: \"" + env.tableId + "\") { profilingResultId columnCount columns { columnName rowCount nullCount nullRatio minValue maxValue } unavailable errors { code message detail } } }")));
        assertFalse(resp.hasError(), "sql profiling should not error: " + resp);

        NopMetaProfilingResult row = findProfilingResult(env.tableId);
        assertNotNull(row, "profiling result must be written for sql table");
        Map<String, Object> tableStats = parseMap(row.getTableStats());
        assertEquals(4L, toLong(tableStats.get("rowCount")), "rowCount must be real COUNT(*) on subquery");

        List<Map<String, Object>> cols = parseList(row.getColumnStats());
        Map<String, Object> amount = findColumn(cols, "AMOUNT");
        assertNotNull(amount, "AMOUNT column must be profiled from sql subquery");
        Map<String, Object> numeric = (Map<String, Object>) amount.get("numericStats");
        assertNotNull(numeric, "numeric column must have numericStats");
        assertEquals(25.0, toDouble(numeric.get("meanValue")), 1e-6, "mean must match AVG() on subquery");
        assertEquals(12.909944, toDouble(numeric.get("stddevValue")), 1e-4, "stddev must match STDDEV_SAMP()");
        assertEquals(25.0, toDouble(numeric.get("medianValue")), 1e-6, "median must match in-app computation");
    }

    @Test
    public void testSqlProfilingNoDataSourceFails() {
        NopMetaTable sqlTable = saveManualSqlTable("SELECT 1", "qs_sql_no_ds");
        GraphQLResponseBean resp = graphQLEngine.executeGraphQL(graphQLEngine.newGraphQLContext(req(
                "mutation { NopMetaTable__profileTable(metaTableId: \"" + sqlTable.getMetaTableId() + "\") { profilingResultId columnCount columns { columnName rowCount nullCount nullRatio minValue maxValue } unavailable errors { code message detail } } }")));
        assertTrue(resp.hasError(), "sql table with no datasource must explicitly fail: " + resp);
    }

    // ===== helpers =====

    static final class PreparedEnv {
        final String tableId;
        PreparedEnv(String tableId) { this.tableId = tableId; }
    }

    /**
     * 准备 sql 测试环境：建 H2 库 + test_data 表（4 行 amount=10,20,30,40）+ 注册数据源 +
     * 创建 sql 类型 NopMetaTable（sourceSql 引用 test_data）。
     */
    private PreparedEnv prepare(String querySpace, String sourceSql) throws Exception {
        String dbUrl = "jdbc:h2:mem:" + querySpace + ";DB_CLOSE_DELAY=-1";
        try (Connection c = DriverManager.getConnection(dbUrl, "sa", "");
             Statement st = c.createStatement()) {
            st.execute("CREATE TABLE test_data (amount DOUBLE, name VARCHAR(20))");
            st.execute("INSERT INTO test_data VALUES (10.0, 'aaa')");
            st.execute("INSERT INTO test_data VALUES (20.0, 'bb')");
            st.execute("INSERT INTO test_data VALUES (30.0, 'ccc')");
            st.execute("INSERT INTO test_data VALUES (40.0, 'dddd')");
        }
        saveDataSource("ds-" + querySpace, querySpace, "jdbc", "ACTIVE",
                "{\"jdbcUrl\":\"" + dbUrl + "\",\"username\":\"sa\",\"password\":\"\","
                        + "\"driverClassName\":\"org.h2.Driver\"}");
        String tableId = saveSqlTableAndGetId(sourceSql, querySpace);
        return new PreparedEnv(tableId);
    }

    private String saveSqlTableAndGetId(String sourceSql, String querySpace) {
        IEntityDao<NopMetaTable> dao = daoProvider.daoFor(NopMetaTable.class);
        NopMetaTable t = dao.newEntity();
        t.setMetaModuleId(ensureModuleId());
        t.setTableName("SQL_T_" + System.nanoTime());
        t.setDisplayName("sql-table");
        t.setTableType("sql");
        t.setQuerySpace(querySpace);
        t.setSourceSql(sourceSql);
        t.setVersion(1L);
        dao.saveEntity(t);
        return t.getMetaTableId();
    }

    private NopMetaTable saveManualSqlTable(String sourceSql, String querySpace) {
        IEntityDao<NopMetaTable> dao = daoProvider.daoFor(NopMetaTable.class);
        NopMetaTable t = dao.newEntity();
        t.setMetaModuleId(ensureModuleId());
        t.setTableName("SQL_M_" + System.nanoTime());
        t.setDisplayName("sql-manual");
        t.setTableType("sql");
        t.setQuerySpace(querySpace);
        t.setSourceSql(sourceSql);
        t.setVersion(1L);
        dao.saveEntity(t);
        return t;
    }

    private void saveDataSource(String id, String querySpace, String type, String status, String config) {
        IEntityDao<NopMetaDataSource> dao = daoProvider.daoFor(NopMetaDataSource.class);
        NopMetaDataSource ds = dao.newEntity();
        ds.setDataSourceId(id);
        ds.setQuerySpace(querySpace);
        ds.setName(id);
        ds.setDatasourceType(type);
        ds.setConnectionConfig(config);
        ds.setStatus(status);
        ds.setVersion(1L);
        Timestamp now = new Timestamp(System.currentTimeMillis());
        ds.setCreateTime(now);
        ds.setUpdateTime(now);
        dao.saveEntity(ds);
    }

    private String ensureModuleId() {
        IEntityDao<NopMetaModule> dao = daoProvider.daoFor(NopMetaModule.class);
        NopMetaModule m = dao.newEntity();
        m.setModuleId("nop/test-sql-" + System.nanoTime());
        m.setModuleName("test-sql");
        m.setDisplayName("test-sql");
        m.setModuleVersion(1L);
        m.setStatus("RELEASED");
        m.setImportedAt(new Timestamp(System.currentTimeMillis()));
        dao.saveEntity(m);
        return m.getMetaModuleId();
    }

    private void saveQualityRule(String ruleId, String ruleType, String entityType,
                                 String tableId, Double threshold, String params) {
        IEntityDao<NopMetaQualityRule> dao = daoProvider.daoFor(NopMetaQualityRule.class);
        NopMetaQualityRule r = dao.newEntity();
        r.setQualityRuleId(ruleId);
        r.setRuleName(ruleId);
        r.setDisplayName(ruleId);
        r.setRuleType(ruleType);
        r.setEntityType(entityType);
        r.setEntityId(tableId);
        r.setSeverity("WARNING");
        r.setThreshold(threshold);
        r.setParams(params);
        r.setVersion(1L);
        dao.saveEntity(r);
    }

    private GraphQLResponseBean execRule(String ruleId) {
        return graphQLEngine.executeGraphQL(graphQLEngine.newGraphQLContext(req(
                "mutation { NopMetaQualityRule__executeQualityRule(qualityRuleId: \"" + ruleId + "\") }")));
    }

    private GraphQLRequestBean req(String query) {
        GraphQLRequestBean r = new GraphQLRequestBean();
        r.setQuery(query);
        return r;
    }

    private void assertStatus(GraphQLResponseBean resp, String expected) {
        Map<String, Object> data = (Map<String, Object>) resp.getData();
        Map<String, Object> result = (Map<String, Object>) data.get("NopMetaQualityRule__executeQualityRule");
        assertEquals(expected, result.get("status"), "status mismatch: " + resp);
    }

    private void assertActualValue(GraphQLResponseBean resp, double expected) {
        Map<String, Object> data = (Map<String, Object>) resp.getData();
        Map<String, Object> result = (Map<String, Object>) data.get("NopMetaQualityRule__executeQualityRule");
        assertEquals(expected, ((Number) result.get("actualValue")).doubleValue(), 1e-6);
    }

    private NopMetaCatalog findCatalogRow(String metaTableId) {
        IEntityDao<NopMetaCatalog> dao = daoProvider.daoFor(NopMetaCatalog.class);
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaCatalog.PROP_NAME_metaTableId, metaTableId));
        return dao.findFirstByQuery(q);
    }

    private NopMetaProfilingResult findProfilingResult(String metaTableId) {
        IEntityDao<NopMetaProfilingResult> dao = daoProvider.daoFor(NopMetaProfilingResult.class);
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaProfilingResult.PROP_NAME_metaTableId, metaTableId));
        return dao.findFirstByQuery(q);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseMap(String json) {
        return json == null ? null : (Map<String, Object>) JsonTool.parse(json);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> parseList(String json) {
        return json == null ? null : (List<Map<String, Object>>) JsonTool.parse(json);
    }

    private static Map<String, Object> findColumn(List<Map<String, Object>> cols, String name) {
        for (Map<String, Object> c : cols) {
            if (name.equalsIgnoreCase(String.valueOf(c.get("columnName")))) {
                return c;
            }
        }
        return null;
    }

    private static long toLong(Object v) {
        return ((Number) v).longValue();
    }

    private static double toDouble(Object v) {
        return ((Number) v).doubleValue();
    }
}
