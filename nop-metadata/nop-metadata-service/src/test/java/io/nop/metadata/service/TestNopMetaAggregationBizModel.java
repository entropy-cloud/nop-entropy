package io.nop.metadata.service;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.TreeBean;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.core.lang.json.JsonTool;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.graphql.core.engine.IGraphQLEngine;
import io.nop.metadata.dao.entity.NopMetaDataSource;
import io.nop.metadata.dao.entity.NopMetaModule;
import io.nop.metadata.dao.entity.NopMetaTable;
import io.nop.metadata.dao.entity.NopMetaTableDimension;
import io.nop.metadata.dao.entity.NopMetaTableFilter;
import io.nop.metadata.dao.entity.NopMetaTableMeasure;
import io.nop.metadata.service.entity.NopMetaTableBizModel;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证指标/维度聚合查询 queryAggregation（架构基线 §4.4.2 D6/D7）：categorical 聚合（sum/count/countDistinct）、
 * 时间维度 granularity 分桶（H2 DATE_TRUNC）、默认过滤器自动应用、expression 型 Measure 显式失败。
 *
 * <p>Anti-Hollow：真实 H2 造数 → 聚合 SQL → 断言真实聚合值（非空壳）。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestNopMetaAggregationBizModel extends JunitBaseTestCase {

    public TestNopMetaAggregationBizModel() {
        setTestConfig("nop.orm.init-database-schema", true);
    }

    @Inject
    IGraphQLEngine graphQLEngine;
    @Inject
    IDaoProvider daoProvider;
    @Inject
    NopMetaTableBizModel nopMetaTableBizModel;

    /** categorical 维度 + sum/count 聚合：GROUP BY CATEGORY → A=sum 30/count 2，B=sum 30/count 1。 */
    @Test
    @SuppressWarnings("unchecked")
    public void testCategoricalAggregation() throws Exception {
        String dbUrl = "jdbc:h2:mem:meta_agg_cat;DB_CLOSE_DELAY=-1";
        seedAggTable(dbUrl);
        String tableId = prepareExternalTable(dbUrl, "qs_agg_cat", "EXT_AGG");
        createMeasure(tableId, "total", "AMOUNT", "sum", null);
        createMeasure(tableId, "cnt", "AMOUNT", "count", null);
        createDimension(tableId, "cat", "CATEGORY", "categorical", null);

        Map<String, Object> result = nopMetaTableBizModel.queryAggregation(tableId,
                Arrays.asList("total", "cnt"), Arrays.asList("cat"), null, null, null, null);
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.get("items");
        assertEquals(2, items.size(), "group by CATEGORY must yield 2 groups (A,B)");
        Map<String, Object> rowA = findRow(items, "CAT", "A");
        Map<String, Object> rowB = findRow(items, "CAT", "B");
        assertEquals(30, toInt(rowA.get("TOTAL")), "SUM(AMOUNT) for A = 10+20 = 30");
        assertEquals(2, toInt(rowA.get("CNT")), "COUNT for A = 2");
        assertEquals(30, toInt(rowB.get("TOTAL")), "SUM(AMOUNT) for B = 30");
        assertEquals(1, toInt(rowB.get("CNT")), "COUNT for B = 1");
    }

    /** countDistinct：按月分组，每月 distinct category 计数。 */
    @Test
    @SuppressWarnings("unchecked")
    public void testCountDistinctAggregation() throws Exception {
        String dbUrl = "jdbc:h2:mem:meta_agg_cd;DB_CLOSE_DELAY=-1";
        seedAggTable(dbUrl);
        String tableId = prepareExternalTable(dbUrl, "qs_agg_cd", "EXT_AGG");
        createMeasure(tableId, "dc", "CATEGORY", "countDistinct", null);
        createDimension(tableId, "mon", "CREATED_AT", "temporal", "month");

        Map<String, Object> result = nopMetaTableBizModel.queryAggregation(tableId,
                Arrays.asList("dc"), Arrays.asList("mon"), null, null, null, null);
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.get("items");
        assertFalse(items.isEmpty(), "temporal month grouping must yield rows");
        // 每月只有 1 个 distinct category（A in Jan, B in Feb）
        for (Map<String, Object> row : items) {
            assertEquals(1, toInt(getIgnoreCase(row, "DC")), "countDistinct(CATEGORY) per month = 1: " + row);
        }
    }

    /** 时间维度 granularity=month 分桶（D7，H2 DATE_TRUNC）：按月分组 sum。 */
    @Test
    @SuppressWarnings("unchecked")
    public void testTemporalGranularityBucketing() throws Exception {
        String dbUrl = "jdbc:h2:mem:meta_agg_tm;DB_CLOSE_DELAY=-1";
        seedAggTable(dbUrl);
        String tableId = prepareExternalTable(dbUrl, "qs_agg_tm", "EXT_AGG");
        createMeasure(tableId, "total", "AMOUNT", "sum", null);
        createDimension(tableId, "mon", "CREATED_AT", "temporal", "month");

        Map<String, Object> result = nopMetaTableBizModel.queryAggregation(tableId,
                Arrays.asList("total"), Arrays.asList("mon"), null, null, null, null);
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.get("items");
        assertEquals(2, items.size(), "group by month must yield 2 months (2024-01, 2024-02)");
        // 每月 sum = 30（Jan: 10+20, Feb: 30）
        for (Map<String, Object> row : items) {
            assertEquals(30, toInt(getIgnoreCase(row, "TOTAL")), "monthly SUM(AMOUNT) = 30: " + row);
        }
    }

    /** 默认过滤器自动应用：isDefault filter AMOUNT > 15 → 只聚合 amount>15 的行。 */
    @Test
    @SuppressWarnings("unchecked")
    public void testDefaultFilterApplied() throws Exception {
        String dbUrl = "jdbc:h2:mem:meta_agg_df;DB_CLOSE_DELAY=-1";
        seedAggTable(dbUrl);
        String tableId = prepareExternalTable(dbUrl, "qs_agg_df", "EXT_AGG");
        createMeasure(tableId, "total", "AMOUNT", "sum", null);
        createDimension(tableId, "cat", "CATEGORY", "categorical", null);
        // 默认过滤器：AMOUNT > 15（排除 A 的 10）
        TreeBean defaultFilter = FilterBeans.gt("AMOUNT", 15);
        createDefaultFilter(tableId, "df", defaultFilter);

        Map<String, Object> result = nopMetaTableBizModel.queryAggregation(tableId,
                Arrays.asList("total"), Arrays.asList("cat"), null, null, null, null);
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.get("items");
        // AMOUNT>15：A 的 20 保留（A=20），B 的 30 保留（B=30）。A 的 10 被过滤。
        Map<String, Object> rowA = findRow(items, "CAT", "A");
        assertEquals(20, toInt(rowA.get("TOTAL")), "default filter AMOUNT>15: SUM(A) = 20 (10 excluded)");
    }

    /** expression 型 Measure 显式失败（不静默跳过、不当 0 返回）。 */
    @Test
    public void testExpressionMeasureExplicitlyFails() throws Exception {
        String dbUrl = "jdbc:h2:mem:meta_agg_exp;DB_CLOSE_DELAY=-1";
        seedAggTable(dbUrl);
        String tableId = prepareExternalTable(dbUrl, "qs_agg_exp", "EXT_AGG");
        createMeasure(tableId, "exprM", "AMOUNT", "sum", "AMOUNT * 2");
        createDimension(tableId, "cat", "CATEGORY", "categorical", null);

        assertTrue(queryAggregationHasError(tableId, Arrays.asList("exprM"), Arrays.asList("cat")),
                "expression-type measure must explicitly fail");
    }

    /** 不约定的 granularity 显式失败。 */
    @Test
    public void testUnsupportedGranularityFails() throws Exception {
        String dbUrl = "jdbc:h2:mem:meta_agg_g;DB_CLOSE_DELAY=-1";
        seedAggTable(dbUrl);
        String tableId = prepareExternalTable(dbUrl, "qs_agg_g", "EXT_AGG");
        createMeasure(tableId, "total", "AMOUNT", "sum", null);
        createDimension(tableId, "weird", "CREATED_AT", "temporal", "fortnight");

        assertTrue(queryAggregationHasError(tableId, Arrays.asList("total"), Arrays.asList("weird")),
                "unsupported granularity must explicitly fail");
    }

    /** entity 表聚合（D6 entity 路径）：importOrmModel 后按实体状态分组 count。 */
    @Test
    @SuppressWarnings("unchecked")
    public void testEntityAggregation() {
        importModel();
        String tableId = findEntityTableId("nop_meta_module");
        // NopMetaModule 有 status 字段；按 status 分组 count
        IEntityDao<io.nop.metadata.dao.entity.NopMetaEntityField> fieldDao =
                daoProvider.daoFor(io.nop.metadata.dao.entity.NopMetaEntityField.class);
        String statusFieldId = findEntityFieldId("nop_meta_module", "status", fieldDao);
        createMeasure(tableId, "cnt", statusFieldId, "count", null);
        createDimension(tableId, "st", statusFieldId, "categorical", null);

        Map<String, Object> result = nopMetaTableBizModel.queryAggregation(tableId,
                Arrays.asList("cnt"), Arrays.asList("st"), null, null, null, null);
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.get("items");
        assertFalse(items.isEmpty(), "entity aggregation by status must return real grouped rows: " + items);
    }

    // ===== helpers =====

    private void seedAggTable(String dbUrl) throws Exception {
        try (Connection c = DriverManager.getConnection(dbUrl, "sa", "");
             Statement st = c.createStatement()) {
            st.execute("CREATE TABLE ext_agg (category VARCHAR(20), amount INT, created_at TIMESTAMP)");
            st.execute("INSERT INTO ext_agg VALUES ('A', 10, '2024-01-15 10:00:00')");
            st.execute("INSERT INTO ext_agg VALUES ('A', 20, '2024-01-20 10:00:00')");
            st.execute("INSERT INTO ext_agg VALUES ('B', 30, '2024-02-10 10:00:00')");
        }
    }

    private void importModel() {
        io.nop.api.core.beans.graphql.GraphQLRequestBean request = new io.nop.api.core.beans.graphql.GraphQLRequestBean();
        request.setQuery("mutation { NopMetaModule__importOrmModel(path: \"/nop/metadata/orm/app.orm.xml\") { metaModuleId } }");
        graphQLEngine.executeGraphQL(graphQLEngine.newGraphQLContext(request));
    }

    private String findEntityTableId(String tableName) {
        IEntityDao<NopMetaTable> dao = daoProvider.daoFor(NopMetaTable.class);
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaTable.PROP_NAME_tableName, tableName));
        q.addFilter(FilterBeans.eq("tableType", "entity"));
        NopMetaTable t = dao.findFirstByQuery(q);
        org.junit.jupiter.api.Assertions.assertNotNull(t, "entity table " + tableName + " must exist after import");
        return t.getMetaTableId();
    }

    private String findEntityFieldId(String tableName, String fieldName,
                                     IEntityDao<io.nop.metadata.dao.entity.NopMetaEntityField> fieldDao) {
        IEntityDao<io.nop.metadata.dao.entity.NopMetaEntity> entityDao =
                daoProvider.daoFor(io.nop.metadata.dao.entity.NopMetaEntity.class);
        QueryBean eq = new QueryBean();
        eq.addFilter(FilterBeans.eq(io.nop.metadata.dao.entity.NopMetaEntity.PROP_NAME_tableName, tableName));
        io.nop.metadata.dao.entity.NopMetaEntity ent = entityDao.findFirstByQuery(eq);
        org.junit.jupiter.api.Assertions.assertNotNull(ent, "entity " + tableName + " must exist");
        QueryBean fq = new QueryBean();
        fq.addFilter(FilterBeans.eq(io.nop.metadata.dao.entity.NopMetaEntityField.PROP_NAME_metaEntityId, ent.getMetaEntityId()));
        fq.addFilter(FilterBeans.eq(io.nop.metadata.dao.entity.NopMetaEntityField.PROP_NAME_fieldName, fieldName));
        io.nop.metadata.dao.entity.NopMetaEntityField f = fieldDao.findFirstByQuery(fq);
        org.junit.jupiter.api.Assertions.assertNotNull(f, "field " + fieldName + " must exist on " + tableName);
        return f.getEntityFieldId();
    }

    private String prepareExternalTable(String dbUrl, String querySpace, String expectedTable) {
        saveDataSource("ds-" + querySpace, querySpace, "jdbc", "ACTIVE", dbUrl);
        io.nop.api.core.beans.graphql.GraphQLRequestBean request = new io.nop.api.core.beans.graphql.GraphQLRequestBean();
        request.setQuery("mutation { NopMetaDataSource__syncExternalTables(dataSourceId: \"ds-" + querySpace
                + "\", schemaPattern: \"PUBLIC\") }");
        io.nop.api.core.beans.graphql.GraphQLResponseBean resp =
                graphQLEngine.executeGraphQL(graphQLEngine.newGraphQLContext(request));
        org.junit.jupiter.api.Assertions.assertFalse(resp.hasError(), "sync should not error: " + resp);
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
        org.junit.jupiter.api.Assertions.assertNotNull(t, "external table " + tableName + " must be synced");
        return t.getMetaTableId();
    }

    private void createMeasure(String tableId, String name, String entityFieldId, String aggFunc, String expression) {
        IEntityDao<NopMetaTableMeasure> dao = daoProvider.daoFor(NopMetaTableMeasure.class);
        NopMetaTableMeasure m = dao.newEntity();
        m.setMetaTableId(tableId);
        m.setMeasureName(name);
        m.setEntityFieldId(entityFieldId);
        m.setAggFunc(aggFunc);
        if (expression != null) {
            m.setExpression(expression);
        }
        m.setVersion(1L);
        dao.saveEntity(m);
    }

    private void createDimension(String tableId, String name, String entityFieldId, String dimensionType,
                                 String granularity) {
        IEntityDao<NopMetaTableDimension> dao = daoProvider.daoFor(NopMetaTableDimension.class);
        NopMetaTableDimension d = dao.newEntity();
        d.setMetaTableId(tableId);
        d.setDimensionName(name);
        d.setEntityFieldId(entityFieldId);
        d.setDimensionType(dimensionType);
        if (granularity != null) {
            d.setGranularity(granularity);
        }
        d.setVersion(1L);
        dao.saveEntity(d);
    }

    private void createDefaultFilter(String tableId, String name, TreeBean definition) {
        IEntityDao<NopMetaTableFilter> dao = daoProvider.daoFor(NopMetaTableFilter.class);
        NopMetaTableFilter f = dao.newEntity();
        f.setMetaTableId(tableId);
        f.setFilterName(name);
        f.setDefinition(JsonTool.stringify(definition));
        f.setIsDefault((byte) 1);
        f.setVersion(1L);
        dao.saveEntity(f);
    }

    private boolean queryAggregationHasError(String tableId, List<String> measures, List<String> dims) {
        try {
            nopMetaTableBizModel.queryAggregation(tableId, measures, dims, null, null, null, null);
            return false;
        } catch (Exception e) {
            return true;
        }
    }

    private static Map<String, Object> findRow(List<Map<String, Object>> items, String key, String val) {
        for (Map<String, Object> row : items) {
            Object v = getIgnoreCase(row, key);
            if (v != null && val.equals(String.valueOf(v))) {
                return row;
            }
        }
        return null;
    }

    private static Object getIgnoreCase(Map<String, Object> map, String key) {
        for (Map.Entry<String, Object> e : map.entrySet()) {
            if (e.getKey().equalsIgnoreCase(key)) {
                return e.getValue();
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
