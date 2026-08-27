package io.nop.metadata.service;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.autotest.NopTestProperty;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.graphql.GraphQLRequestBean;
import io.nop.api.core.beans.graphql.GraphQLResponseBean;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.engine.IGraphQLEngine;
import io.nop.metadata.dao.entity.NopMetaDataSource;
import io.nop.metadata.dao.entity.NopMetaReconciliationConfig;
import io.nop.metadata.dao.entity.NopMetaReconciliationResult;
import io.nop.metadata.dao.entity.NopMetaTable;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * check2 P2-07（2026-08-23 审计）回归：executeReconciliation 经 queryTableData 静默截断。
 *
 * <p>缺陷机制：executeReconciliation 调 {@code queryTableData(metaTableId, null, null, ...)}，
 * limit=null 触发 normalizeQueryLimit 缺省 1000——对账统计（statistics.totalRows/matchRate 持久化到
 * NopMetaReconciliationResult）在 >1000 行目标表上系统性失真且无任何截断标记。
 *
 * <p>修复：显式传入对账取数上限（{@code nop.metadata.reconciliation.fetch-limit}，默认对齐
 * queryTableData 上限 10000），并在 statistics 记录 {@code fetchedLimit} 与 {@code truncated}
 * （items 达到取数上限即保守置 true——无法区分是否还有更多行）。
 *
 * <p>本类经 {@code @NopTestProperty} 把 fetch-limit 收紧到 5 使截断可测：
 * 3 行表（未达上限）→ truncated=false；7 行表 → 只取 5 行，totalRows=5、truncated=true。
 * mutate-fail：回退为 limit=null 静默缺省时 statistics 无 fetchedLimit/truncated 键，
 * 且 7 行表 totalRows=7 ≠ 5 → 断言确定性失败。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
@NopTestProperty(name = "nop.metadata.reconciliation.fetch-limit", value = "5")
public class TestReconciliationFetchLimitMarker extends JunitBaseTestCase {

    public TestReconciliationFetchLimitMarker() {
        setTestConfig("nop.orm.init-database-schema", true);
    }

    @Inject
    IGraphQLEngine graphQLEngine;

    @Inject
    IDaoProvider daoProvider;

    /** 未达上限：全量行参与对账，statistics 带 fetchedLimit=5、truncated=false。 */
    @Test
    public void testFetchLimitRecordedWhenBelowLimit() throws Exception {
        String tableId = prepareTableWithRows("recon_fl_below", 3);
        String configId = saveConfig("rc-fl-below", tableId);

        GraphQLResponseBean resp = executeReconciliation(configId);
        assertFalse(resp.hasError(), "executeReconciliation should succeed: " + resp);

        Map<String, Object> stats = parseLatestStatistics(configId);
        assertFetchedLimit(stats, 5);
        assertEquals(Boolean.FALSE, stats.get("truncated"),
                "3-row table under limit 5 must not be marked truncated");
        assertEquals(3, ((Number) stats.get("totalRows")).intValue(),
                "all 3 rows must participate in reconciliation");
    }

    /** 超上限：只取前 5 行，statistics totalRows=5、truncated=true（失真可见可诊断）。 */
    @Test
    public void testTruncationMarkedWhenRowsExceedFetchLimit() throws Exception {
        String tableId = prepareTableWithRows("recon_fl_over", 7);
        String configId = saveConfig("rc-fl-over", tableId);

        GraphQLResponseBean resp = executeReconciliation(configId);
        assertFalse(resp.hasError(), "executeReconciliation should succeed: " + resp);

        Map<String, Object> stats = parseLatestStatistics(configId);
        assertFetchedLimit(stats, 5);
        assertEquals(Boolean.TRUE, stats.get("truncated"),
                "7-row table over limit 5 must be marked truncated");
        assertEquals(5, ((Number) stats.get("totalRows")).intValue(),
                "only fetched rows (5) must be counted");
    }

    /** fetchedLimit 必须存在且为数值（缺失即 P2-07 缺陷形态：无截断标记）。 */
    private static void assertFetchedLimit(Map<String, Object> stats, int expected) {
        Object fetchedLimit = stats.get("fetchedLimit");
        assertTrue(fetchedLimit instanceof Number,
                "statistics must record fetchedLimit as a number (was: " + fetchedLimit + ")");
        assertEquals(expected, ((Number) fetchedLimit).intValue());
    }

    // ===== helpers =====

    private String prepareTableWithRows(String querySpace, int rows) throws Exception {
        String dbUrl = "jdbc:h2:mem:" + querySpace + ";DB_CLOSE_DELAY=-1";
        try (Connection c = DriverManager.getConnection(dbUrl, "sa", "");
             Statement st = c.createStatement()) {
            st.execute("CREATE TABLE EXT_" + querySpace.toUpperCase() + " (id INT NOT NULL, name VARCHAR(50))");
            for (int i = 1; i <= rows; i++) {
                st.execute("INSERT INTO EXT_" + querySpace.toUpperCase() + " VALUES (" + i + ", 'n" + i + "')");
            }
        }
        IEntityDao<NopMetaDataSource> dsDao = daoProvider.daoFor(NopMetaDataSource.class);
        NopMetaDataSource ds = dsDao.newEntity();
        ds.setDataSourceId("ds-" + querySpace);
        ds.setQuerySpace(querySpace);
        ds.setName("ds-" + querySpace);
        ds.setDatasourceType("jdbc");
        ds.setConnectionConfig("{\"jdbcUrl\":\"" + dbUrl + "\",\"username\":\"sa\",\"password\":\"\","
                + "\"driverClassName\":\"org.h2.Driver\"}");
        ds.setStatus("ACTIVE");
        ds.setVersion(1L);
        ds.setCreatedBy("autotest");
        ds.setUpdatedBy("autotest");
        Timestamp now = new Timestamp(System.currentTimeMillis());
        ds.setCreateTime(now);
        ds.setUpdateTime(now);
        dsDao.saveEntity(ds);
        GraphQLResponseBean syncResp = execute(
                "mutation { NopMetaDataSource__syncExternalTables(dataSourceId: \"ds-" + querySpace
                        + "\", schemaPattern: \"PUBLIC\") { syncedTableCount } }");
        assertFalse(syncResp.hasError(), "sync should not error: " + syncResp);

        IEntityDao<NopMetaTable> tableDao = daoProvider.daoFor(NopMetaTable.class);
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaTable.PROP_NAME_tableName, "EXT_" + querySpace.toUpperCase()));
        q.addFilter(FilterBeans.eq("tableType", "external"));
        NopMetaTable t = tableDao.findFirstByQuery(q);
        assertNotNull(t, "external table must be synced");
        return t.getMetaTableId();
    }

    private String saveConfig(String configId, String metaTableId) {
        IEntityDao<NopMetaReconciliationConfig> dao = daoProvider.daoFor(NopMetaReconciliationConfig.class);
        NopMetaReconciliationConfig c = dao.newEntity();
        c.setConfigId(configId);
        c.setConfigName(configId + "-name");
        c.setDisplayName(configId + "-name");
        c.setMetaTableId(metaTableId);
        c.setColumnName("NAME");
        c.setIdentifierSpace("wikidata");
        c.setTargetEntityType("company");
        c.setMatchStrategy("exact");
        c.setAutoMatch((byte) 1);
        c.setAutoMatchThreshold(0.8);
        c.setVersion(1L);
        c.setCreatedBy("autotest");
        c.setUpdatedBy("autotest");
        Timestamp now = new Timestamp(System.currentTimeMillis());
        c.setCreateTime(now);
        c.setUpdateTime(now);
        dao.saveEntity(c);
        return configId;
    }

    private GraphQLResponseBean executeReconciliation(String configId) {
        return execute("mutation { NopMetaReconciliationConfig__executeReconciliation(configId: \""
                + configId + "\") { resultId } }");
    }

    private GraphQLResponseBean execute(String query) {
        GraphQLRequestBean request = new GraphQLRequestBean();
        request.setQuery(query);
        IGraphQLExecutionContext context = graphQLEngine.newGraphQLContext(request);
        return graphQLEngine.executeGraphQL(context);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseLatestStatistics(String configId) {
        IEntityDao<NopMetaReconciliationResult> dao = daoProvider.daoFor(NopMetaReconciliationResult.class);
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaReconciliationResult.PROP_NAME_configId, configId));
        q.addOrderField(NopMetaReconciliationResult.PROP_NAME_executeTime, true);
        q.setLimit(1);
        assertTrue(!dao.findAllByQuery(q).isEmpty(), "result must be written for config " + configId);
        NopMetaReconciliationResult r = dao.findAllByQuery(q).get(0);
        return (Map<String, Object>) io.nop.core.lang.json.JsonTool.parse(r.getStatistics());
    }
}
