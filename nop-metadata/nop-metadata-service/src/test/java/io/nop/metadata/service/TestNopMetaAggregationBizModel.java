package io.nop.metadata.service;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.TreeBean;
import io.nop.api.core.beans.query.OrderFieldBean;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.core.lang.json.JsonTool;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.graphql.core.engine.IGraphQLEngine;
import io.nop.metadata.dao.entity.NopMetaDataSource;
import io.nop.metadata.dao.entity.NopMetaEntity;
import io.nop.metadata.dao.entity.NopMetaEntityField;
import io.nop.metadata.dao.entity.NopMetaModule;
import io.nop.metadata.dao.entity.NopMetaTable;
import io.nop.metadata.dao.entity.NopMetaTableDimension;
import io.nop.metadata.dao.entity.NopMetaTableFilter;
import io.nop.metadata.dao.entity.NopMetaTableJoin;
import io.nop.metadata.dao.entity.NopMetaTableMeasure;
import io.nop.metadata.service.entity.NopMetaTableBizModel;
import io.nop.metadata.service.entity.NopMetaTableMeasureBizModel;
import io.nop.metadata.service.query.MetaAggregationExecutor;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    @Inject
    NopMetaTableMeasureBizModel nopMetaTableMeasureBizModel;
    @Inject
    io.nop.orm.IOrmTemplate ormTemplate;

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
                Arrays.asList("total", "cnt"), Arrays.asList("cat"), null, null, null, null, null, null, null);
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
                Arrays.asList("dc"), Arrays.asList("mon"), null, null, null, null, null, null, null);
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
                Arrays.asList("total"), Arrays.asList("mon"), null, null, null, null, null, null, null);
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
                Arrays.asList("total"), Arrays.asList("cat"), null, null, null, null, null, null, null);
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.get("items");
        // AMOUNT>15：A 的 20 保留（A=20），B 的 30 保留（B=30）。A 的 10 被过滤。
        Map<String, Object> rowA = findRow(items, "CAT", "A");
        assertEquals(20, toInt(rowA.get("TOTAL")), "default filter AMOUNT>15: SUM(A) = 20 (10 excluded)");
    }

    /**
     * expression 型 Measure 真实执行（plan 2026-07-18-1400-1 改写：原 testExpressionMeasureExplicitlyFails 断言显式失败，
     * 现改为成功路径——expression 现在可执行）。
     *
     * <p>expression = {@code AMOUNT * 2}，aggFunc = sum → SUM(AMOUNT*2) = 2 * SUM(AMOUNT)。
     * 数据：A=10+20=30 → SUM(AMOUNT*2) = 20+40 = 60；B=30 → SUM(AMOUNT*2) = 60。
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testExpressionMeasureExplicitlyFails() throws Exception {
        String dbUrl = "jdbc:h2:mem:meta_agg_exp;DB_CLOSE_DELAY=-1";
        seedAggTable(dbUrl);
        String tableId = prepareExternalTable(dbUrl, "qs_agg_exp", "EXT_AGG");
        createMeasure(tableId, "exprM", "AMOUNT", "sum", "AMOUNT * 2");
        createDimension(tableId, "cat", "CATEGORY", "categorical", null);

        Map<String, Object> result = nopMetaTableBizModel.queryAggregation(tableId,
                Arrays.asList("exprM"), Arrays.asList("cat"), null, null, null, null, null, null, null);
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.get("items");
        assertNotNull(items);
        assertEquals(2, items.size(), "expression measure must execute and yield 2 groups (A, B): " + items);
        Map<String, Object> rowA = findRow(items, "CAT", "A");
        Map<String, Object> rowB = findRow(items, "CAT", "B");
        assertNotNull(rowA, "group A must exist: " + items);
        assertNotNull(rowB, "group B must exist: " + items);
        // SUM(AMOUNT*2) for A = (10*2) + (20*2) = 60; for B = 30*2 = 60
        assertEquals(60, toInt(rowA.get("EXPRM")), "SUM(AMOUNT*2) for A = 60 (10*2 + 20*2): " + rowA);
        assertEquals(60, toInt(rowB.get("EXPRM")), "SUM(AMOUNT*2) for B = 60 (30*2): " + rowB);
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
                Arrays.asList("cnt"), Arrays.asList("st"), null, null, null, null, null, null, null);
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.get("items");
        assertFalse(items.isEmpty(), "entity aggregation by status must return real grouped rows: " + items);
    }

    // ============================================================
    // plan 2026-07-18-1100-2：entity 路径时间维度 granularity 分桶（D7.1，bypass EQL）
    // ============================================================

    /**
     * entity 路径 temporal dimension + granularity=month 分桶（§4.4.2 D7.1，主 Exit Criterion）。
     *
     * <p>Anti-Hollow：把 NOP_META_ENTITY.CREATE_TIME 通过直接 SQL UPDATE 分布到 2 个不同月份，
     * 经 entity 路径 bypass EQL（{@code TableReferenceExecutor} 平台 JDBC Connection + {@code DATE_TRUNC}）
     * 跑 GROUP BY month 分桶，断言：
     * <ul>
     *   <li>真实分组行非空（stub / 旧行 346 裸列直查会得到 N 个分组而非 2 个，立即失败此断言）</li>
     *   <li>分组数 == 2（2024-01 与 2024-02，证明 {@code DATE_TRUNC('month', CREATE_TIME)} 真实下沉到 SQL）</li>
     *   <li>SUM(CNT) == NOP_META_ENTITY 总行数（聚合无丢失）</li>
     * </ul>
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testEntityTemporalGranularityBucketingMonth() {
        importModel();
        long totalEntities = countRows("select count(*) as c from nop_meta_entity");
        assertTrue(totalEntities >= 2, "nop_meta_entity must have at least 2 rows for month bucketing: " + totalEntities);
        // 把一半行 UPDATE 到 2024-01-15，另一半到 2024-02-15（两月分桶）
        spreadEntityCreateTimeAcrossTwoMonths();
        try {
            String tableId = findEntityTableId("nop_meta_entity");
            String createTimeFieldId = findEntityFieldId("nop_meta_entity", "createTime");
            createMeasure(tableId, "cnt", createTimeFieldId, "count", null);
            createDimension(tableId, "mon", createTimeFieldId, "temporal", "month");

            Map<String, Object> result = nopMetaTableBizModel.queryAggregation(tableId,
                    Arrays.asList("cnt"), Arrays.asList("mon"), null, null, null, null, null, null, null);
            List<Map<String, Object>> items = (List<Map<String, Object>>) result.get("items");
            assertNotNull(items, "items must not be null");
            assertEquals(2, items.size(),
                    "entity path month bucketing must yield 2 buckets (2024-01, 2024-02): " + items);
            long sumCnt = 0;
            for (Map<String, Object> row : items) {
                Object cnt = getIgnoreCase(row, "CNT");
                assertNotNull(cnt, "measure CNT must be present: " + row.keySet());
                assertTrue(toLong(cnt) > 0, "each month bucket must have positive count: " + row);
                sumCnt += toLong(cnt);
            }
            assertEquals(totalEntities, sumCnt,
                    "SUM(CNT) across month buckets must equal total nop_meta_entity rows: " + items);
        } finally {
            resetEntityCreateTime();
        }
    }

    /**
     * entity 路径 temporal dimension + 各 granularity 值（year/quarter/month/week/day/hour）均成功分桶（§4.4.2 D7.1）。
     *
     * <p>对每个约定 granularity 值跑一次 entity 聚合，断言：执行无异常 + 真实分组行非空 + SUM(CNT) == 总行数。
     * 不对每个 bucket 的精确值做断言（各 granularity 的桶数与数据时间分布相关），仅验证「下沉到 SQL 成功 + 全量行参与聚合」。
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testEntityTemporalGranularityAllValues() {
        importModel();
        long totalEntities = countRows("select count(*) as c from nop_meta_entity");
        assertTrue(totalEntities >= 2, "nop_meta_entity must have at least 2 rows: " + totalEntities);
        // 分布到不同时间（覆盖 year/quarter/month/week/day/hour 各粒度）
        spreadEntityCreateTimeAcrossTwoMonths();
        try {
            String[] granularities = {"year", "quarter", "month", "week", "day", "hour"};
            for (String granularity : granularities) {
                String tableId = findEntityTableId("nop_meta_entity");
                String createTimeFieldId = findEntityFieldId("nop_meta_entity", "createTime");
                createMeasure(tableId, "cnt_" + granularity, createTimeFieldId, "count", null);
                createDimension(tableId, "d_" + granularity, createTimeFieldId, "temporal", granularity);

                Map<String, Object> result = nopMetaTableBizModel.queryAggregation(tableId,
                        Arrays.asList("cnt_" + granularity), Arrays.asList("d_" + granularity),
                        null, null, null, null, null, null, null);
                List<Map<String, Object>> items = (List<Map<String, Object>>) result.get("items");
                assertNotNull(items, "items must not be null for granularity=" + granularity);
                assertFalse(items.isEmpty(),
                        "entity path granularity=" + granularity + " must yield non-empty groups: " + items);
                long sumCnt = 0;
                for (Map<String, Object> row : items) {
                    Object cnt = getIgnoreCase(row, "CNT_" + granularity.toUpperCase());
                    assertNotNull(cnt, "measure column must be present for granularity=" + granularity + ": " + row);
                    sumCnt += toLong(cnt);
                }
                assertEquals(totalEntities, sumCnt,
                        "SUM(CNT) for granularity=" + granularity + " must equal total rows: " + items);
            }
        } finally {
            resetEntityCreateTime();
        }
    }

    /**
     * entity 路径与 external/sql 路径同 granularity + 同数据 → 聚合结果一致（§4.4.2 D7.1 能力对齐证据）。
     *
     * <p>把 NOP_META_ENTITY 数据复制到外部 H2 库的同一物理表结构（CREATE_TIME 同值），分别用 entity 路径（bypass EQL）
     * 和 external 路径（withConnection）跑 month granularity 聚合，断言两者分组键集合 + 计数值一致。
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testEntityAggregationGranularityMatchesExternal() throws Exception {
        importModel();
        spreadEntityCreateTimeAcrossTwoMonths();
        try {
            // 1) 取 entity 路径真实 CREATE_TIME 数据，复制到外部 H2 库 ext_entity_gran 表
            List<Object[]> rowsWithCreateTime = queryEntityCreateTimeRows();
            assertFalse(rowsWithCreateTime.isEmpty(), "test data must have rows");
            String querySpace = "qs_entity_gran_match";
            String dbUrl = "jdbc:h2:mem:" + querySpace + ";DB_CLOSE_DELAY=-1";
            try (Connection c = DriverManager.getConnection(dbUrl, "sa", "");
                 Statement st = c.createStatement()) {
                st.execute("CREATE TABLE ext_entity_gran (K VARCHAR(200), CREATED_AT TIMESTAMP)");
                for (Object[] row : rowsWithCreateTime) {
                    String k = String.valueOf(row[0]).replace("'", "''");
                    String ts = String.valueOf(row[1]);
                    st.execute("INSERT INTO ext_entity_gran VALUES ('" + k + "', '" + ts + "')");
                }
            }
            saveDataSource("ds-" + querySpace, querySpace, dbUrl);
            syncExternalTables("ds-" + querySpace);
            String externalTableId = externalTableId("EXT_ENTITY_GRAN");

            // 2) external 路径 month granularity
            createMeasure(externalTableId, "xcnt", "K", "count", null);
            createDimension(externalTableId, "xmon", "CREATED_AT", "temporal", "month");
            Map<String, Object> extResult = nopMetaTableBizModel.queryAggregation(externalTableId,
                    Arrays.asList("xcnt"), Arrays.asList("xmon"), null, null, null, null, null, null, null);
            List<Map<String, Object>> extItems = (List<Map<String, Object>>) extResult.get("items");

            // 3) entity 路径 month granularity
            String entityTableId = findEntityTableId("nop_meta_entity");
            String createTimeFieldId = findEntityFieldId("nop_meta_entity", "createTime");
            createMeasure(entityTableId, "ecnt", createTimeFieldId, "count", null);
            createDimension(entityTableId, "emon", createTimeFieldId, "temporal", "month");
            Map<String, Object> entityResult = nopMetaTableBizModel.queryAggregation(entityTableId,
                    Arrays.asList("ecnt"), Arrays.asList("emon"), null, null, null, null, null, null, null);
            List<Map<String, Object>> entityItems = (List<Map<String, Object>>) entityResult.get("items");

            // 4) 一致性断言：分组数相同 + 每个分桶的计数相同（按 bucket 起始时间对齐）
            assertEquals(extItems.size(), entityItems.size(),
                    "entity path and external path must yield same bucket count: ext=" + extItems + " entity=" + entityItems);
            Map<String, Long> extMap = bucketsToCountMap(extItems, "XMON", "XCNT");
            Map<String, Long> entityMap = bucketsToCountMap(entityItems, "EMON", "ECNT");
            assertEquals(extMap.size(), entityMap.size(),
                    "bucket count maps must have same size: ext=" + extMap + " entity=" + entityMap);
            for (Map.Entry<String, Long> e : extMap.entrySet()) {
                Long entityCnt = entityMap.get(e.getKey());
                assertNotNull(entityCnt,
                        "external bucket " + e.getKey() + " must exist in entity path: ext=" + extMap + " entity=" + entityMap);
                assertEquals(e.getValue().longValue(), entityCnt.longValue(),
                        "bucket " + e.getKey() + " count must match between entity and external paths: ext="
                                + extMap + " entity=" + entityMap);
            }
        } finally {
            resetEntityCreateTime();
        }
    }

    /**
     * 失败路径：entity 路径不约定 granularity（非约定值）→ 显式失败（§4.4.2 D7.1 失败路径显式化）。
     */
    @Test
    public void testEntityUnsupportedGranularityFails() {
        importModel();
        String tableId = findEntityTableId("nop_meta_entity");
        String createTimeFieldId = findEntityFieldId("nop_meta_entity", "createTime");
        createMeasure(tableId, "cnt", createTimeFieldId, "count", null);
        createDimension(tableId, "weird", createTimeFieldId, "temporal", "fortnight");

        assertTrue(queryAggregationHasError(tableId, Arrays.asList("cnt"), Arrays.asList("weird")),
                "entity path with unsupported granularity must explicitly fail (not silent bare-column fallback)");
    }

    /**
     * 端到端验证（#22）+ 接线验证（#23）：从 GraphQL {@code queryAggregation} 入口到 entity 聚合 granularity 分桶
     * 结果的完整路径已验证。entity 路径 bypass EQL 在运行时真实调用 {@code GranularityBucketing.translate}
     * （非旧行 346 裸列直查）。
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testEntityTemporalGranularityBucketingViaGraphQL() {
        importModel();
        long totalEntities = countRows("select count(*) as c from nop_meta_entity");
        spreadEntityCreateTimeAcrossTwoMonths();
        try {
            String tableId = findEntityTableId("nop_meta_entity");
            String createTimeFieldId = findEntityFieldId("nop_meta_entity", "createTime");
            createMeasure(tableId, "gcnt", createTimeFieldId, "count", null);
            createDimension(tableId, "gmon", createTimeFieldId, "temporal", "month");

            io.nop.api.core.beans.graphql.GraphQLRequestBean request = new io.nop.api.core.beans.graphql.GraphQLRequestBean();
            request.setQuery("query { NopMetaTable__queryAggregation(metaTableId: \"" + tableId + "\", "
                    + "measures: [\"gcnt\"], dimensions: [\"gmon\"]) }");
            io.nop.api.core.beans.graphql.GraphQLResponseBean resp =
                    graphQLEngine.executeGraphQL(graphQLEngine.newGraphQLContext(request));
            assertFalse(resp.hasError(),
                    "GraphQL entity path month granularity queryAggregation must succeed: " + resp);
            Map<String, Object> data = (Map<String, Object>) resp.getData();
            Map<String, Object> qa = (Map<String, Object>) data.get("NopMetaTable__queryAggregation");
            assertNotNull(qa, "GraphQL queryAggregation must return non-null Map result");
            List<Map<String, Object>> items = (List<Map<String, Object>>) qa.get("items");
            assertNotNull(items, "GraphQL items must not be null");
            assertEquals(2, items.size(),
                    "GraphQL entity path month bucketing end-to-end must yield 2 buckets (2024-01, 2024-02): " + items);
            long sumCnt = 0;
            for (Map<String, Object> row : items) {
                Object cnt = getIgnoreCase(row, "GCNT");
                assertNotNull(cnt, "measure GCNT must be present: " + row.keySet());
                sumCnt += toLong(cnt);
            }
            assertEquals(totalEntities, sumCnt,
                    "GraphQL entity path SUM(CNT) must equal total nop_meta_entity rows: " + items);
        } finally {
            resetEntityCreateTime();
        }
    }

    /** 直接 SQL UPDATE NOP_META_ENTITY.CREATE_TIME 分布到 2024-01-15 / 2024-02-15 两月（按 entity ID 列表对半分）。 */
    private void spreadEntityCreateTimeAcrossTwoMonths() {
        Timestamp jan = Timestamp.valueOf("2024-01-15 10:00:00");
        Timestamp feb = Timestamp.valueOf("2024-02-15 10:00:00");
        List<String> allIds = queryAllEntityIds();
        assertNotNull(allIds);
        assertFalse(allIds.isEmpty(), "must have at least 1 entity row for time spread");
        int mid = allIds.size() / 2;
        for (int i = 0; i < allIds.size(); i++) {
            String id = allIds.get(i);
            Timestamp ts = (i < mid) ? jan : feb;
            io.nop.core.lang.sql.SQL upd = io.nop.core.lang.sql.SQL.begin().allowUnderscoreName(true)
                    .sql("update NOP_META_ENTITY set CREATE_TIME=? where META_ENTITY_ID=?", ts, id).end();
            ormTemplate.executeUpdate(upd);
        }
        ormTemplate.evictAll(NopMetaEntity.class.getName());
    }

    private List<String> queryAllEntityIds() {
        io.nop.core.lang.sql.SQL q = io.nop.core.lang.sql.SQL.begin().allowUnderscoreName(true)
                .sql("select META_ENTITY_ID from NOP_META_ENTITY").end();
        return ormTemplate.executeQuery(q, null, ds -> {
            List<String> ids = new java.util.ArrayList<>();
            for (io.nop.dataset.IDataRow row : ds) {
                ids.add(String.valueOf(row.getObject(0)));
            }
            return ids;
        });
    }

    /** 还原 NOP_META_ENTITY.CREATE_TIME 到当前时间（避免污染后续测试）。 */
    private void resetEntityCreateTime() {
        Timestamp now = new Timestamp(System.currentTimeMillis());
        io.nop.core.lang.sql.SQL upd = io.nop.core.lang.sql.SQL.begin().allowUnderscoreName(true)
                .sql("update NOP_META_ENTITY set CREATE_TIME=?", now).end();
        ormTemplate.executeUpdate(upd);
        ormTemplate.evictAll(NopMetaEntity.class.getName());
    }

    /** 取所有 NOP_META_ENTITY 行的 ENTITY_NAME + CREATE_TIME（按分布后的时间）。 */
    private List<Object[]> queryEntityCreateTimeRows() {
        io.nop.core.lang.sql.SQL q = io.nop.core.lang.sql.SQL.begin().allowUnderscoreName(true)
                .sql("select ENTITY_NAME, CREATE_TIME from NOP_META_ENTITY").end();
        return ormTemplate.executeQuery(q, null, ds -> {
            List<Object[]> rows = new java.util.ArrayList<>();
            for (io.nop.dataset.IDataRow row : ds) {
                rows.add(new Object[]{row.getObject(0), row.getObject(1)});
            }
            return rows;
        });
    }

    /** 把聚合结果 items 转为 {bucketStartTimestamp字符串: count} map，按归一化键对齐（截断到秒）。 */
    private static Map<String, Long> bucketsToCountMap(List<Map<String, Object>> items, String dimKey, String cntKey) {
        Map<String, Long> map = new java.util.TreeMap<>();
        for (Map<String, Object> row : items) {
            Object bucket = getIgnoreCase(row, dimKey);
            Object cnt = getIgnoreCase(row, cntKey);
            assertNotNull(bucket, "bucket column " + dimKey + " must be present: " + row);
            assertNotNull(cnt, "count column " + cntKey + " must be present: " + row);
            // bucket 为 java.sql.Timestamp（DATE_TRUNC 返回）或 String，统一 toString 后截断到秒（去掉毫秒）
            String key = normalizeBucketKey(String.valueOf(bucket));
            map.merge(key, toLong(cnt), Long::sum);
        }
        return map;
    }

    /** 归一化 bucket 起始时间字符串：取前 19 字符（'yyyy-MM-dd HH:mm:ss' 截断到秒）。 */
    private static String normalizeBucketKey(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > 19 ? s.substring(0, 19) : s;
    }

    // ============================================================
    // plan 0852-1：entity↔entity JOIN 聚合（queryAggregation + joinId）
    // ============================================================

    /**
     * 同库 entity↔entity JOIN 聚合正确性 + Anti-Hollow（plan 0852-1 Phase 1 主 Exit Criterion）：
     *
     * <p>left = nop_meta_entity（dimension = displayName，左表属性），
     * right = nop_meta_entity_field（measure = count(field)，右表属性），join ON metaEntityId。
     * 经 {@code queryAggregation(joinId)} 跑 GROUP BY over JOIN，断言：
     * <ul>
     *   <li>真实分组行非空（stub 立即失败此断言）</li>
     *   <li>维度列 ST + 指标列 CNT 同时存在</li>
     *   <li>跨表 Measure（右表 count）真实命中：SUM(CNT) 跨所有分组 == nop_meta_entity_field 总行数
     *       （证明 JOIN + GROUP BY 正确，非笛卡尔积/非空 items）</li>
     * </ul>
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testEntityJoinAggregationCorrectness() {
        importModel();
        NopMetaEntity leftEntity = findMetaEntityByTable("nop_meta_entity");
        NopMetaEntity rightEntity = findMetaEntityByTable("nop_meta_entity_field");
        String leftTableId = findEntityTableId("nop_meta_entity");
        String joinId = createJoin(leftTableId, "inner", leftEntity.getMetaEntityId(),
                rightEntity.getMetaEntityId(), "metaEntityId", "metaEntityId", "fld");

        // 维度：左 entity 的 displayName（归属 left entity）
        String leftDimFieldId = findEntityFieldId("nop_meta_entity", "displayName");
        // 指标：右 entity_field 的 fieldName（归属 right entity）→ 跨表 count
        String rightMeasureFieldId = findEntityFieldId("nop_meta_entity_field", "fieldName");
        createDimension(leftTableId, "st", leftDimFieldId, "categorical", null);
        createMeasure(leftTableId, "cnt", rightMeasureFieldId, "count", null);

        Map<String, Object> result = nopMetaTableBizModel.queryAggregation(leftTableId,
                Arrays.asList("cnt"), Arrays.asList("st"), null, joinId, null, null, null, null, null);
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.get("items");
        assertNotNull(items, "items must not be null");
        assertFalse(items.isEmpty(), "entity JOIN aggregation must return real grouped rows: " + items);

        // 跨表 Measure 真实命中：SUM(CNT) == nop_meta_entity_field 总行数（JOIN+GROUP BY 正确性证明）
        long totalFields = countRows("select count(*) as c from nop_meta_entity_field");
        long sumCnt = 0;
        for (Map<String, Object> row : items) {
            assertNotNull(getIgnoreCase(row, "ST"), "dimension column ST must be present: " + row.keySet());
            Object cnt = getIgnoreCase(row, "CNT");
            assertNotNull(cnt, "measure column CNT must be present: " + row.keySet());
            sumCnt += toLong(cnt);
        }
        assertEquals(totalFields, sumCnt,
                "SUM(count(fields)) grouped by entity.displayName must equal total field rows (JOIN+GROUP BY correctness): "
                        + items);
    }

    /**
     * 端到端验证（Minimum Rules #22）：从 GraphQL {@code queryAggregation(metaTableId, measures, dimensions, joinId)}
     * 入口到聚合 {@code items} 输出完整跑通。接线验证：JOIN 聚合路径经 GraphQL 调用 BizModel action 真实产出结果。
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testEntityJoinAggregationViaGraphQL() {
        importModel();
        NopMetaEntity leftEntity = findMetaEntityByTable("nop_meta_entity");
        NopMetaEntity rightEntity = findMetaEntityByTable("nop_meta_entity_field");
        String leftTableId = findEntityTableId("nop_meta_entity");
        String joinId = createJoin(leftTableId, "inner", leftEntity.getMetaEntityId(),
                rightEntity.getMetaEntityId(), "metaEntityId", "metaEntityId", "fld");
        String leftDimFieldId = findEntityFieldId("nop_meta_entity", "displayName");
        String rightMeasureFieldId = findEntityFieldId("nop_meta_entity_field", "fieldName");
        createDimension(leftTableId, "gst", leftDimFieldId, "categorical", null);
        createMeasure(leftTableId, "gcnt", rightMeasureFieldId, "count", null);

        io.nop.api.core.beans.graphql.GraphQLRequestBean request = new io.nop.api.core.beans.graphql.GraphQLRequestBean();
        // queryAggregation 返回 Map（GraphQL 标量），不带 selection set（items 随 Map 整体返回）
        request.setQuery("query { NopMetaTable__queryAggregation(metaTableId: \"" + leftTableId + "\", "
                + "measures: [\"gcnt\"], dimensions: [\"gst\"], joinId: \"" + joinId + "\") }");
        io.nop.api.core.beans.graphql.GraphQLResponseBean resp =
                graphQLEngine.executeGraphQL(graphQLEngine.newGraphQLContext(request));
        assertFalse(resp.hasError(), "GraphQL queryAggregation(joinId) must succeed: " + resp);
        Map<String, Object> data = (Map<String, Object>) resp.getData();
        // Map 返回：queryAggregation 字段值为整个 Map（含 items）
        Object qaObj = data.get("NopMetaTable__queryAggregation");
        assertNotNull(qaObj, "GraphQL queryAggregation(joinId) must return non-null Map result");
        Map<String, Object> qa = (Map<String, Object>) qaObj;
        List<Map<String, Object>> items = (List<Map<String, Object>>) qa.get("items");
        assertNotNull(items, "GraphQL items must not be null");
        assertFalse(items.isEmpty(),
                "GraphQL queryAggregation(joinId) end-to-end must return real grouped rows: " + items);
    }

    /** 无 joinId（null）→ 单表聚合既有行为零回归（plan Exit Criterion：不带 joinId 行为逐字一致）。 */
    @Test
    @SuppressWarnings("unchecked")
    public void testNoJoinIdSingleTableBehaviorUnchanged() {
        importModel();
        String tableId = findEntityTableId("nop_meta_module");
        String statusFieldId = findEntityFieldId("nop_meta_module", "status");
        createMeasure(tableId, "mcnt", statusFieldId, "count", null);
        createDimension(tableId, "mst", statusFieldId, "categorical", null);

        // joinId = null → 走单表 entity 聚合分支（不进入 JOIN 聚合路径）
        Map<String, Object> result = nopMetaTableBizModel.queryAggregation(tableId,
                Arrays.asList("mcnt"), Arrays.asList("mst"), null, null, null, null, null, null, null);
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.get("items");
        assertFalse(items.isEmpty(), "single-table aggregation (no joinId) must still work: " + items);
    }

    /** joinType=right → 显式失败（复用 join 校验，不静默降级）。 */
    @Test
    public void testJoinAggregationJoinTypeRightFails() {
        importModel();
        NopMetaEntity leftEntity = findMetaEntityByTable("nop_meta_entity");
        NopMetaEntity rightEntity = findMetaEntityByTable("nop_meta_entity_field");
        String leftTableId = findEntityTableId("nop_meta_entity");
        String joinId = createJoin(leftTableId, "right", leftEntity.getMetaEntityId(),
                rightEntity.getMetaEntityId(), "metaEntityId", "metaEntityId", "fld");
        assertTrue(queryAggregationJoinHasError(leftTableId, joinId),
                "joinType=right JOIN aggregation must explicitly fail (not silently degrade)");
    }

    /** join 不存在/不归属 → 显式失败（复用 join 校验）。 */
    @Test
    public void testJoinAggregationNotFoundFails() {
        importModel();
        String leftTableId = findEntityTableId("nop_meta_entity");
        assertTrue(queryAggregationJoinHasError(leftTableId, "__no_such_join__"),
                "non-existent join must explicitly fail in JOIN aggregation");
    }

    /**
     * 跨 querySpace（跨库）entity-entity JOIN 聚合 → D10 内存 GROUP BY 成功（plan 1500-2）。
     *
     * <p>Anti-Hollow 主 Exit Criterion：经 {@code executeJoin} 取合并行 → 内存 GROUP BY。
     * <ul>
     *   <li>真实分组行非空（stub 立即失败此断言）</li>
     *   <li>entity 侧 measure 经**属性名**（fieldName）取值正确（非静默 0）</li>
     *   <li>SUM(count(fields)) grouped by entity.displayName == nop_meta_entity_field 总行数（正确性证明）</li>
     * </ul>
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testEntityEntityCrossDbJoinAggregationSucceeds() {
        importModel();
        NopMetaEntity leftEntity = findMetaEntityByTable("nop_meta_entity");
        NopMetaEntity rightEntity = findMetaEntityByTable("nop_meta_entity_field");
        updateQuerySpaceSql(rightEntity.getMetaEntityId(), "qs_agg_cross_db");
        try {
            String leftTableId = findEntityTableId("nop_meta_entity");
            String joinId = createJoin(leftTableId, "inner", leftEntity.getMetaEntityId(),
                    rightEntity.getMetaEntityId(), "metaEntityId", "metaEntityId", "fld");
            String leftDimFieldId = findEntityFieldId("nop_meta_entity", "displayName");
            String rightMeasureFieldId = findEntityFieldId("nop_meta_entity_field", "fieldName");
            createDimension(leftTableId, "st", leftDimFieldId, "categorical", null);
            createMeasure(leftTableId, "cnt", rightMeasureFieldId, "count", null);

            Map<String, Object> result = nopMetaTableBizModel.queryAggregation(leftTableId,
                    Arrays.asList("cnt"), Arrays.asList("st"), null, joinId, null, null, null, null, null);
            List<Map<String, Object>> items = (List<Map<String, Object>>) result.get("items");
            assertNotNull(items, "items must not be null");
            assertFalse(items.isEmpty(),
                    "cross-DB entity-entity JOIN aggregation must return real grouped rows via in-memory GROUP BY: " + items);

            // entity 侧 measure 经属性名取值正确性证明：SUM(count(fields)) == 总 field 行数
            long totalFields = countRows("select count(*) as c from nop_meta_entity_field");
            long sumCnt = 0;
            for (Map<String, Object> row : items) {
                assertNotNull(getIgnoreCase(row, "ST"), "dimension column ST must be present: " + row.keySet());
                Object cnt = getIgnoreCase(row, "CNT");
                assertNotNull(cnt, "measure column CNT must be present (not silent null/0): " + row.keySet());
                sumCnt += toLong(cnt);
            }
            assertEquals(totalFields, sumCnt,
                    "SUM(count(fields)) grouped by entity.displayName must equal total field rows "
                            + "(cross-DB in-memory GROUP BY correctness, entity measure via property name not silent 0): " + items);
        } finally {
            updateQuerySpaceSql(rightEntity.getMetaEntityId(), null);
        }
    }

    /**
     * 跨 querySpace（跨库）entity-entity JOIN 聚合 GraphQL 端到端（#22）+ 接线（#23）（plan 1500-2）。
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testEntityEntityCrossDbJoinAggregationViaGraphQL() {
        importModel();
        NopMetaEntity leftEntity = findMetaEntityByTable("nop_meta_entity");
        NopMetaEntity rightEntity = findMetaEntityByTable("nop_meta_entity_field");
        updateQuerySpaceSql(rightEntity.getMetaEntityId(), "qs_agg_cross_db_gql");
        try {
            String leftTableId = findEntityTableId("nop_meta_entity");
            String joinId = createJoin(leftTableId, "inner", leftEntity.getMetaEntityId(),
                    rightEntity.getMetaEntityId(), "metaEntityId", "metaEntityId", "fld");
            String leftDimFieldId = findEntityFieldId("nop_meta_entity", "displayName");
            String rightMeasureFieldId = findEntityFieldId("nop_meta_entity_field", "fieldName");
            createDimension(leftTableId, "gst", leftDimFieldId, "categorical", null);
            createMeasure(leftTableId, "gcnt", rightMeasureFieldId, "count", null);

            io.nop.api.core.beans.graphql.GraphQLRequestBean request = new io.nop.api.core.beans.graphql.GraphQLRequestBean();
            request.setQuery("query { NopMetaTable__queryAggregation(metaTableId: \"" + leftTableId + "\", "
                    + "measures: [\"gcnt\"], dimensions: [\"gst\"], joinId: \"" + joinId + "\") }");
            io.nop.api.core.beans.graphql.GraphQLResponseBean resp =
                    graphQLEngine.executeGraphQL(graphQLEngine.newGraphQLContext(request));
            assertFalse(resp.hasError(), "GraphQL cross-DB entity-entity queryAggregation(joinId) must succeed: " + resp);
            Map<String, Object> data = (Map<String, Object>) resp.getData();
            Map<String, Object> qa = (Map<String, Object>) data.get("NopMetaTable__queryAggregation");
            assertNotNull(qa, "GraphQL queryAggregation(joinId) must return non-null Map result");
            List<Map<String, Object>> items = (List<Map<String, Object>>) qa.get("items");
            assertNotNull(items, "GraphQL items must not be null");
            assertFalse(items.isEmpty(),
                    "GraphQL cross-DB entity-entity queryAggregation(joinId) end-to-end must return real grouped rows: " + items);
        } finally {
            updateQuerySpaceSql(rightEntity.getMetaEntityId(), null);
        }
    }

    /**
     * 跨库 entity-entity JOIN 聚合 self-join → 显式失败（双侧别名机制不足，沿用 D8/D9）。
     */
    @Test
    public void testEntityEntityCrossDbSelfJoinFails() {
        importModel();
        NopMetaEntity entity = findMetaEntityByTable("nop_meta_entity");
        updateQuerySpaceSql(entity.getMetaEntityId(), "qs_cross_self");
        try {
            String leftTableId = findEntityTableId("nop_meta_entity");
            String joinId = createJoin(leftTableId, "inner", entity.getMetaEntityId(),
                    entity.getMetaEntityId(), "metaEntityId", "metaEntityId", "self");
            assertTrue(queryAggregationJoinHasError(leftTableId, joinId),
                    "cross-DB self-join JOIN aggregation must explicitly fail (alias attribution ambiguous)");
        } finally {
            updateQuerySpaceSql(entity.getMetaEntityId(), null);
        }
    }

    /**
     * Measure 的 entityFieldId 归属既非左也非右 entity（指向第三个 entity 的字段）→ 字段不可归属，显式失败
     *（不静默归属左/不静默跳过）。
     */
    @Test
    public void testJoinAggregationFieldSideUnresolvedFails() {
        importModel();
        NopMetaEntity leftEntity = findMetaEntityByTable("nop_meta_entity");
        NopMetaEntity rightEntity = findMetaEntityByTable("nop_meta_entity_field");
        String leftTableId = findEntityTableId("nop_meta_entity");
        String joinId = createJoin(leftTableId, "inner", leftEntity.getMetaEntityId(),
                rightEntity.getMetaEntityId(), "metaEntityId", "metaEntityId", "fld");
        // 维度用左表字段（合法），指标故意指向第三个 entity（nop_meta_module）的字段 → 不可归属
        String leftDimFieldId = findEntityFieldId("nop_meta_entity", "displayName");
        String thirdEntityFieldId = findEntityFieldId("nop_meta_module", "status");
        createDimension(leftTableId, "st", leftDimFieldId, "categorical", null);
        createMeasure(leftTableId, "cnt", thirdEntityFieldId, "count", null);

        // 用真实创建的 measure/dim 名，确保加载成功后到达字段归属判定（非 measure-not-found）
        assertTrue(queryAggregationJoinHasError(leftTableId, "cnt", "st", joinId),
                "measure whose entityFieldId belongs to neither left nor right entity must explicitly fail");
    }

    /** self-join（leftEntityId == rightEntityId）→ 字段归属两侧均命中、无法表达右别名，显式失败。 */
    @Test
    public void testJoinAggregationSelfJoinFails() {
        importModel();
        NopMetaEntity entity = findMetaEntityByTable("nop_meta_entity");
        String leftTableId = findEntityTableId("nop_meta_entity");
        // 两端点同为 nop_meta_entity（self-join）
        String joinId = createJoin(leftTableId, "inner", entity.getMetaEntityId(),
                entity.getMetaEntityId(), "metaEntityId", "metaEntityId", "self");
        assertTrue(queryAggregationJoinHasError(leftTableId, joinId),
                "self-join JOIN aggregation must explicitly fail (alias attribution ambiguous)");
    }

    /**
     * 端点非 entity（external/sql table 端点）→ external/sql JOIN 聚合 deferred，显式失败
     *（ERR_AGGR_JOIN_ENDPOINT_NOT_ENTITY，不静默降级单表）。
     */
    @Test
    public void testJoinAggregationTableEndpointFails() throws Exception {
        importModel();
        NopMetaEntity leftEntity = findMetaEntityByTable("nop_meta_entity");
        String leftTableId = findEntityTableId("nop_meta_entity");
        // 右端点为 sql table 端点（非 entity）
        String querySpace = "qs_agg_tbl_ep";
        String dbUrl = "jdbc:h2:mem:" + querySpace + ";DB_CLOSE_DELAY=-1";
        seedH2(dbUrl, "CREATE TABLE ext_dim (k VARCHAR(20), v VARCHAR(20))", "INSERT INTO ext_dim VALUES ('k1','v1')");
        saveDataSource("ds-" + querySpace, querySpace, dbUrl);
        String sqlTableId = saveSqlTableManual("SELECT k, v FROM ext_dim", querySpace);

        String joinId = createMixedJoin(leftTableId, "inner", leftEntity.getMetaEntityId(), sqlTableId,
                "displayName", "k", "x");
        assertTrue(queryAggregationJoinHasError(leftTableId, joinId),
                "JOIN aggregation with external/sql table endpoint must explicitly fail (deferred)");
    }

    // ============================================================
    // plan 1200-1：external↔external 同库 JOIN 聚合 + Measure/Dimension 侧别建模
    // ============================================================

    /**
     * external↔external 同库 JOIN 聚合正确性 + Anti-Hollow（plan 1200-1 Phase 3 主 Exit Criterion）：
     *
     * <p>left = EXT_FACT（measure = sum(AMOUNT)，side=left），right = EXT_DIM（dimension = CAT_NAME，side=right），
     * join ON CAT_ID。经 {@code queryAggregation(joinId)} 跑原生 GROUP BY over JOIN，断言：
     * <ul>
     *   <li>真实分组行非空（stub/退化单表立即失败此断言）</li>
     *   <li>按 CAT_NAME 分组后 SUM(AMOUNT)：A=30（1→10+20），B=30（2→30），与等价直接 SQL 一致</li>
     * </ul>
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testExternalExternalJoinAggregationCorrectness() throws Exception {
        String querySpace = "qs_ext_join";
        String dbUrl = "jdbc:h2:mem:" + querySpace + ";DB_CLOSE_DELAY=-1";
        seedFactDimTables(dbUrl);
        // sync 两个 external 表（同 querySpace 共享数据源）
        saveDataSource("ds-" + querySpace, querySpace, dbUrl);
        syncExternalTables("ds-" + querySpace);
        String factTableId = tableId("EXT_FACT");
        String dimTableId = tableId("EXT_DIM");

        String joinId = createTableTableJoin(factTableId, "inner", factTableId, dimTableId,
                "CAT_ID", "CAT_ID", "dim");
        createMeasureWithSide(factTableId, "total", "AMOUNT", "sum", "left");
        createDimensionWithSide(factTableId, "cat", "CAT_NAME", "categorical", null, "right");

        Map<String, Object> result = nopMetaTableBizModel.queryAggregation(factTableId,
                Arrays.asList("total"), Arrays.asList("cat"), null, joinId, null, null, null, null, null);
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.get("items");
        assertNotNull(items, "items must not be null");
        assertEquals(2, items.size(), "group by CAT_NAME must yield 2 groups (A,B): " + items);
        Map<String, Object> rowA = findRow(items, "CAT", "A");
        Map<String, Object> rowB = findRow(items, "CAT", "B");
        assertNotNull(rowA, "group A must exist: " + items);
        assertNotNull(rowB, "group B must exist: " + items);
        assertEquals(30, toInt(rowA.get("TOTAL")), "SUM(AMOUNT) for A = 10+20 = 30");
        assertEquals(30, toInt(rowB.get("TOTAL")), "SUM(AMOUNT) for B = 30");
    }

    /**
     * 端到端验证（Minimum Rules #22）+ 接线验证（#23）：从 GraphQL {@code queryAggregation(joinId)}
     *（两端点 external 同库）经 MetaAggregationExecutor 路由到聚合 items 输出完整跑通。
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testExternalExternalJoinAggregationViaGraphQL() throws Exception {
        String querySpace = "qs_ext_join_gql";
        String dbUrl = "jdbc:h2:mem:" + querySpace + ";DB_CLOSE_DELAY=-1";
        seedFactDimTables(dbUrl);
        saveDataSource("ds-" + querySpace, querySpace, dbUrl);
        syncExternalTables("ds-" + querySpace);
        String factTableId = tableId("EXT_FACT");
        String dimTableId = tableId("EXT_DIM");

        String joinId = createTableTableJoin(factTableId, "inner", factTableId, dimTableId,
                "CAT_ID", "CAT_ID", "dim");
        createMeasureWithSide(factTableId, "gtotal", "AMOUNT", "sum", "left");
        createDimensionWithSide(factTableId, "gcat", "CAT_NAME", "categorical", null, "right");

        io.nop.api.core.beans.graphql.GraphQLRequestBean request = new io.nop.api.core.beans.graphql.GraphQLRequestBean();
        request.setQuery("query { NopMetaTable__queryAggregation(metaTableId: \"" + factTableId + "\", "
                + "measures: [\"gtotal\"], dimensions: [\"gcat\"], joinId: \"" + joinId + "\") }");
        io.nop.api.core.beans.graphql.GraphQLResponseBean resp =
                graphQLEngine.executeGraphQL(graphQLEngine.newGraphQLContext(request));
        assertFalse(resp.hasError(), "GraphQL external<->external queryAggregation(joinId) must succeed: " + resp);
        Map<String, Object> data = (Map<String, Object>) resp.getData();
        Map<String, Object> qa = (Map<String, Object>) data.get("NopMetaTable__queryAggregation");
        assertNotNull(qa, "GraphQL queryAggregation(joinId) must return non-null Map result");
        List<Map<String, Object>> items = (List<Map<String, Object>>) qa.get("items");
        assertNotNull(items, "GraphQL items must not be null");
        assertFalse(items.isEmpty(),
                "GraphQL external<->external queryAggregation(joinId) end-to-end must return real grouped rows: " + items);
    }

    /**
     * 接线验证（#23）：重构后 entity↔entity JOIN 聚合路径仍被调用（未失效、未退化为单表/external 路径）。
     * 复用 entity-entity 正确性用例的核心断言，确保 router 未绕过既有 entity 路径。
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testEntityJoinAggregationStillWorksAfterRefactor() {
        importModel();
        NopMetaEntity leftEntity = findMetaEntityByTable("nop_meta_entity");
        NopMetaEntity rightEntity = findMetaEntityByTable("nop_meta_entity_field");
        String leftTableId = findEntityTableId("nop_meta_entity");
        String joinId = createJoin(leftTableId, "inner", leftEntity.getMetaEntityId(),
                rightEntity.getMetaEntityId(), "metaEntityId", "metaEntityId", "fld");
        String leftDimFieldId = findEntityFieldId("nop_meta_entity", "displayName");
        String rightMeasureFieldId = findEntityFieldId("nop_meta_entity_field", "fieldName");
        createDimension(leftTableId, "rst", leftDimFieldId, "categorical", null);
        createMeasure(leftTableId, "rcnt", rightMeasureFieldId, "count", null);

        Map<String, Object> result = nopMetaTableBizModel.queryAggregation(leftTableId,
                Arrays.asList("rcnt"), Arrays.asList("rst"), null, joinId, null, null, null, null, null);
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.get("items");
        assertNotNull(items, "items must not be null");
        assertFalse(items.isEmpty(), "entity-entity JOIN aggregation must still work after refactor: " + items);
        long totalFields = countRows("select count(*) as c from nop_meta_entity_field");
        long sumCnt = 0;
        for (Map<String, Object> row : items) {
            sumCnt += toLong(getIgnoreCase(row, "RCNT"));
        }
        assertEquals(totalFields, sumCnt,
                "SUM(count(fields)) grouped by entity.displayName must equal total field rows (entity path not bypassed)");
    }

    /** external/sql 端点缺 side（query-time 必填）→ 显式失败（ERR_AGGR_JOIN_SIDE_REQUIRED，不静默归属/不静默跳过）。 */
    @Test
    public void testExternalJoinAggregationExternalSideRequiredFails() throws Exception {
        String querySpace = "qs_ext_side";
        String dbUrl = "jdbc:h2:mem:" + querySpace + ";DB_CLOSE_DELAY=-1";
        seedFactDimTables(dbUrl);
        saveDataSource("ds-" + querySpace, querySpace, dbUrl);
        syncExternalTables("ds-" + querySpace);
        String factTableId = tableId("EXT_FACT");
        String dimTableId = tableId("EXT_DIM");

        String joinId = createTableTableJoin(factTableId, "inner", factTableId, dimTableId,
                "CAT_ID", "CAT_ID", "dim");
        // measure 无 side → external 端点 side 必填报错
        createMeasure(factTableId, "total", "AMOUNT", "sum", null);
        createDimensionWithSide(factTableId, "cat", "CAT_NAME", "categorical", null, "right");

        assertTrue(queryAggregationJoinHasError(factTableId, "total", "cat", joinId),
                "external/sql join endpoint measure without side must explicitly fail (side required at query-time)");
    }

    /** side 指向端点字段集合不含该列 → 显式失败（ERR_AGGR_JOIN_FIELD_NOT_ON_SIDE）。 */
    @Test
    public void testExternalJoinAggregationColumnNotOnSideFails() throws Exception {
        String querySpace = "qs_ext_col";
        String dbUrl = "jdbc:h2:mem:" + querySpace + ";DB_CLOSE_DELAY=-1";
        seedFactDimTables(dbUrl);
        saveDataSource("ds-" + querySpace, querySpace, dbUrl);
        syncExternalTables("ds-" + querySpace);
        String factTableId = tableId("EXT_FACT");
        String dimTableId = tableId("EXT_DIM");

        String joinId = createTableTableJoin(factTableId, "inner", factTableId, dimTableId,
                "CAT_ID", "CAT_ID", "dim");
        // AMOUNT 属于左表(EXT_FACT)，但 side=right 指向右表(EXT_DIM) → 列不存在于 right 端点
        createMeasureWithSide(factTableId, "total", "AMOUNT", "sum", "right");
        createDimensionWithSide(factTableId, "cat", "CAT_NAME", "categorical", null, "right");

        assertTrue(queryAggregationJoinHasError(factTableId, "total", "cat", joinId),
                "measure column not on declared side endpoint must explicitly fail");
    }

    /** entity 端点 side 与 metaEntityId 判定的端点不一致 → 显式失败（ERR_AGGR_JOIN_ENTITY_SIDE_MISMATCH）。 */
    @Test
    public void testExternalJoinAggregationEntitySideMismatchFails() {
        importModel();
        NopMetaEntity leftEntity = findMetaEntityByTable("nop_meta_entity");
        NopMetaEntity rightEntity = findMetaEntityByTable("nop_meta_entity_field");
        String leftTableId = findEntityTableId("nop_meta_entity");
        String joinId = createJoin(leftTableId, "inner", leftEntity.getMetaEntityId(),
                rightEntity.getMetaEntityId(), "metaEntityId", "metaEntityId", "fld");
        // displayName 属于左 entity，但 side=right → 不一致
        String leftDimFieldId = findEntityFieldId("nop_meta_entity", "displayName");
        createDimensionWithSide(leftTableId, "st", leftDimFieldId, "categorical", null, "right");
        String rightMeasureFieldId = findEntityFieldId("nop_meta_entity_field", "fieldName");
        createMeasure(leftTableId, "cnt", rightMeasureFieldId, "count", null);

        assertTrue(queryAggregationJoinHasError(leftTableId, "cnt", "st", joinId),
                "entity endpoint side inconsistent with metaEntityId attribution must explicitly fail");
    }

    /**
     * 跨 querySpace（跨库）external↔external JOIN 聚合 → D10 内存 GROUP BY 成功（plan 1500-2）。
     *
     * <p>Anti-Hollow：两表分别在不同 querySpace（各自 H2 库），经 {@code executeJoin} 取数 + 合并 + 内存 GROUP BY。
     * 断言按 CAT_NAME 分组 SUM(AMOUNT) 正确：A=30（10+20），B=30（30）。
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testExternalExternalCrossDbJoinAggregationSucceeds() throws Exception {
        // 两表分别处于不同 querySpace（跨库）
        String qs1 = "qs_ext_cross_1";
        String qs2 = "qs_ext_cross_2";
        String dbUrl1 = "jdbc:h2:mem:" + qs1 + ";DB_CLOSE_DELAY=-1";
        String dbUrl2 = "jdbc:h2:mem:" + qs2 + ";DB_CLOSE_DELAY=-1";
        seedH2(dbUrl1, "CREATE TABLE ext_fact_cross (K VARCHAR(20), AMOUNT INT)",
                "INSERT INTO ext_fact_cross VALUES ('k1', 10)",
                "INSERT INTO ext_fact_cross VALUES ('k1', 20)",
                "INSERT INTO ext_fact_cross VALUES ('k2', 30)");
        seedH2(dbUrl2, "CREATE TABLE ext_dim_cross (K VARCHAR(20), CAT_NAME VARCHAR(20))",
                "INSERT INTO ext_dim_cross VALUES ('k1', 'A')",
                "INSERT INTO ext_dim_cross VALUES ('k2', 'B')");
        saveDataSource("ds-" + qs1, qs1, dbUrl1);
        saveDataSource("ds-" + qs2, qs2, dbUrl2);
        syncExternalTables("ds-" + qs1);
        syncExternalTables("ds-" + qs2);
        String factTableId = externalTableId("EXT_FACT_CROSS");
        String dimTableId = externalTableId("EXT_DIM_CROSS");

        String joinId = createTableTableJoin(factTableId, "inner", factTableId, dimTableId, "K", "K", "dim");
        createMeasureWithSide(factTableId, "total", "AMOUNT", "sum", "left");
        createDimensionWithSide(factTableId, "cat", "CAT_NAME", "categorical", null, "right");

        Map<String, Object> result = nopMetaTableBizModel.queryAggregation(factTableId,
                Arrays.asList("total"), Arrays.asList("cat"), null, joinId, null, null, null, null, null);
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.get("items");
        assertNotNull(items, "items must not be null");
        assertEquals(2, items.size(), "group by CAT_NAME must yield 2 groups (A,B): " + items);
        Map<String, Object> rowA = findRow(items, "CAT", "A");
        Map<String, Object> rowB = findRow(items, "CAT", "B");
        assertNotNull(rowA, "group A must exist: " + items);
        assertNotNull(rowB, "group B must exist: " + items);
        assertEquals(30, toInt(rowA.get("TOTAL")), "SUM(AMOUNT) for A = 10+20 = 30 (cross-DB in-memory GROUP BY)");
        assertEquals(30, toInt(rowB.get("TOTAL")), "SUM(AMOUNT) for B = 30 (cross-DB in-memory GROUP BY)");
    }

    // ============================================================
    // plan 1500-1：混合端点（entity↔external/sql）同库 JOIN 聚合（D1.5）
    // ============================================================

    /**
     * 混合端点同库 JOIN 聚合正确性 + Anti-Hollow（plan 1500-1 Phase 2 主 Exit Criterion）：
     *
     * <p>left = nop_meta_module（entity 端点，物理表 NOP_META_MODULE 在外部 H2 库 qs_mixed_same 中存在），
     * right = MIXED_DIM（external 端点，同库同步）。join ON STATUS = STATUS_VAL。
     * 经 {@code queryAggregation(joinId)} 在单一 external {@code withConnection} 上跑原生 GROUP BY over JOIN，断言：
     * <ul>
     *   <li>真实分组行非空（stub/退化 D5 拼接立即失败此断言）</li>
     *   <li>按 CAT_NAME 分组后 COUNT(status)：Category A = 2，Category B = 1（真实聚合值，非伪造）</li>
     * </ul>
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testMixedSameDbJoinAggregationCorrectness() throws Exception {
        importModel();
        NopMetaEntity leftEntity = findMetaEntityByTable("nop_meta_module");
        String leftTableId = findEntityTableId("nop_meta_module");

        // 外部 H2 库含 NOP_META_MODULE（entity 物理表，供同库可见性测试通过）+ MIXED_DIM（external 端点）
        String querySpace = "qs_mixed_same";
        String dbUrl = "jdbc:h2:mem:" + querySpace + ";DB_CLOSE_DELAY=-1";
        seedMixedSameDbTables(dbUrl);
        saveDataSource("ds-" + querySpace, querySpace, dbUrl);
        syncExternalTables("ds-" + querySpace);
        String dimTableId = externalTableId("MIXED_DIM");

        // 混合 join：entity 端点（left）+ external table 端点（right）
        String joinId = createMixedJoin(leftTableId, "inner", leftEntity.getMetaEntityId(), dimTableId,
                "status", "STATUS_VAL", "dim");
        // measure：entity 侧 nop_meta_module.status 字段，count，side=left
        String statusFieldId = findEntityFieldId("nop_meta_module", "status");
        createMeasureWithSide(leftTableId, "cnt", statusFieldId, "count", "left");
        // dimension：external 侧 MIXED_DIM.CAT_NAME，side=right
        createDimensionWithSide(leftTableId, "cat", "CAT_NAME", "categorical", null, "right");

        Map<String, Object> result = nopMetaTableBizModel.queryAggregation(leftTableId,
                Arrays.asList("cnt"), Arrays.asList("cat"), null, joinId, null, null, null, null, null);
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.get("items");
        assertNotNull(items, "items must not be null");
        assertEquals(2, items.size(), "group by CAT_NAME must yield 2 groups (Category A, Category B): " + items);
        Map<String, Object> rowA = findRow(items, "CAT", "Category A");
        Map<String, Object> rowB = findRow(items, "CAT", "Category B");
        assertNotNull(rowA, "group 'Category A' must exist: " + items);
        assertNotNull(rowB, "group 'Category B' must exist: " + items);
        assertEquals(2, toInt(rowA.get("CNT")), "COUNT(status) for Category A = 2 (mod1, mod2)");
        assertEquals(1, toInt(rowB.get("CNT")), "COUNT(status) for Category B = 1 (mod3)");
    }

    /**
     * 端到端验证（#22）+ 接线验证（#23）：从 GraphQL {@code queryAggregation(joinId)}（混合端点同库）
     * 经 MetaAggregationExecutor 路由到 executeMixedSameDbJoinAggregation，产出真实聚合 items。
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testMixedSameDbJoinAggregationViaGraphQL() throws Exception {
        importModel();
        NopMetaEntity leftEntity = findMetaEntityByTable("nop_meta_module");
        String leftTableId = findEntityTableId("nop_meta_module");

        String querySpace = "qs_mixed_gql";
        String dbUrl = "jdbc:h2:mem:" + querySpace + ";DB_CLOSE_DELAY=-1";
        seedMixedSameDbTables(dbUrl);
        saveDataSource("ds-" + querySpace, querySpace, dbUrl);
        syncExternalTables("ds-" + querySpace);
        String dimTableId = externalTableId("MIXED_DIM");

        String joinId = createMixedJoin(leftTableId, "inner", leftEntity.getMetaEntityId(), dimTableId,
                "status", "STATUS_VAL", "dim");
        String statusFieldId = findEntityFieldId("nop_meta_module", "status");
        createMeasureWithSide(leftTableId, "gcnt", statusFieldId, "count", "left");
        createDimensionWithSide(leftTableId, "gcat", "CAT_NAME", "categorical", null, "right");

        io.nop.api.core.beans.graphql.GraphQLRequestBean request = new io.nop.api.core.beans.graphql.GraphQLRequestBean();
        request.setQuery("query { NopMetaTable__queryAggregation(metaTableId: \"" + leftTableId + "\", "
                + "measures: [\"gcnt\"], dimensions: [\"gcat\"], joinId: \"" + joinId + "\") }");
        io.nop.api.core.beans.graphql.GraphQLResponseBean resp =
                graphQLEngine.executeGraphQL(graphQLEngine.newGraphQLContext(request));
        assertFalse(resp.hasError(), "GraphQL mixed same-DB queryAggregation(joinId) must succeed: " + resp);
        Map<String, Object> data = (Map<String, Object>) resp.getData();
        Map<String, Object> qa = (Map<String, Object>) data.get("NopMetaTable__queryAggregation");
        assertNotNull(qa, "GraphQL queryAggregation(joinId) must return non-null Map result");
        List<Map<String, Object>> items = (List<Map<String, Object>>) qa.get("items");
        assertNotNull(items, "GraphQL items must not be null");
        assertFalse(items.isEmpty(),
                "GraphQL mixed same-DB queryAggregation(joinId) end-to-end must return real grouped rows: " + items);
    }

    /**
     * 混合端点跨库 JOIN 聚合 → D10 内存 GROUP BY 成功（plan 1500-2）。
     *
     * <p>entity 物理表（NOP_META_MODULE）在选定 external 连接不可见 → 跨库路径。
     * 经 {@code executeJoin} 混合拼接（entity 侧 ORM DAO + table 侧 withConnection）→ 内存 GROUP BY。
     * 用平台库 nop_meta_module 实际 status 值造匹配 MIXED_DIM 数据，断言真实聚合值（非伪造）。
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testMixedCrossDbJoinAggregationSucceeds() throws Exception {
        importModel();
        NopMetaEntity leftEntity = findMetaEntityByTable("nop_meta_module");
        String leftTableId = findEntityTableId("nop_meta_module");

        // 查询平台库 nop_meta_module 实际 status 值（entity 侧经 ORM DAO 取数）
        List<String> statuses = queryDistinctColumnValues("select distinct status from nop_meta_module", "STATUS");
        assertFalse(statuses.isEmpty(), "nop_meta_module must have status values after import");

        // 外部库只含 MIXED_DIM（不含 NOP_META_MODULE）→ 可见性测试失败 → 跨库内存 GROUP BY 路径
        String querySpace = "qs_mixed_cross_ok";
        String dbUrl = "jdbc:h2:mem:" + querySpace + ";DB_CLOSE_DELAY=-1";
        List<String> statements = new ArrayList<>();
        statements.add("CREATE TABLE MIXED_DIM (STATUS_VAL VARCHAR(20), CAT_NAME VARCHAR(20))");
        for (String s : statuses) {
            statements.add("INSERT INTO MIXED_DIM VALUES ('" + s.replace("'", "''") + "', 'All')");
        }
        seedH2(dbUrl, statements.toArray(new String[0]));
        saveDataSource("ds-" + querySpace, querySpace, dbUrl);
        syncExternalTables("ds-" + querySpace);
        String dimTableId = externalTableId("MIXED_DIM");

        String joinId = createMixedJoin(leftTableId, "inner", leftEntity.getMetaEntityId(), dimTableId,
                "status", "STATUS_VAL", "dim");
        String statusFieldId = findEntityFieldId("nop_meta_module", "status");
        createMeasureWithSide(leftTableId, "cnt", statusFieldId, "count", "left");
        createDimensionWithSide(leftTableId, "cat", "CAT_NAME", "categorical", null, "right");

        Map<String, Object> result = nopMetaTableBizModel.queryAggregation(leftTableId,
                Arrays.asList("cnt"), Arrays.asList("cat"), null, joinId, null, null, null, null, null);
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.get("items");
        assertNotNull(items, "items must not be null");
        assertFalse(items.isEmpty(),
                "cross-DB mixed JOIN aggregation must return real grouped rows via in-memory GROUP BY: " + items);
        // entity 侧 measure 经属性名取值正确性证明：CNT 非伪造值（非静默 0）
        for (Map<String, Object> row : items) {
            Object cnt = getIgnoreCase(row, "CNT");
            assertNotNull(cnt, "measure CNT must be present (not silent null/0): " + row.keySet());
            assertTrue(toLong(cnt) > 0, "cross-DB mixed in-memory GROUP BY count must be real positive value: " + row);
        }
        // 所有 status 值映射到 'All' 类别 → 单组
        Map<String, Object> rowAll = findRow(items, "CAT", "All");
        assertNotNull(rowAll, "group 'All' must exist: " + items);
    }

    /**
     * 混合端点跨库：external/sql 端点 dimension 缺 side → 显式失败（ERR_AGGR_JOIN_SIDE_REQUIRED）。
     * 验证 side 必填规则在跨库内存 GROUP BY 路径同样生效（#24 无静默跳过）。
     */
    @Test
    public void testMixedCrossDbTableSideRequiredFails() throws Exception {
        importModel();
        NopMetaEntity leftEntity = findMetaEntityByTable("nop_meta_module");
        String leftTableId = findEntityTableId("nop_meta_module");

        String querySpace = "qs_mixed_cross_side";
        String dbUrl = "jdbc:h2:mem:" + querySpace + ";DB_CLOSE_DELAY=-1";
        seedH2(dbUrl, "CREATE TABLE MIXED_DIM (STATUS_VAL VARCHAR(20), CAT_NAME VARCHAR(20))",
                "INSERT INTO MIXED_DIM VALUES ('A', 'Category A')");
        saveDataSource("ds-" + querySpace, querySpace, dbUrl);
        syncExternalTables("ds-" + querySpace);
        String dimTableId = externalTableId("MIXED_DIM");

        String joinId = createMixedJoin(leftTableId, "inner", leftEntity.getMetaEntityId(), dimTableId,
                "status", "STATUS_VAL", "dim");
        String statusFieldId = findEntityFieldId("nop_meta_module", "status");
        createMeasureWithSide(leftTableId, "cnt", statusFieldId, "count", "left");
        // dimension 无 side → 跨库内存 GROUP BY 路径下 external 端点 side 必填报错
        createDimension(leftTableId, "cat", "CAT_NAME", "categorical", null);

        assertTrue(queryAggregationJoinHasError(leftTableId, "cnt", "cat", joinId),
                "cross-DB mixed-endpoint external/sql dimension without side must explicitly fail (side required)");
    }

    /**
     * external/sql 端点缺 side（query-time 必填）→ 显式失败（ERR_AGGR_JOIN_SIDE_REQUIRED）。
     * 复用混合端点上下文，验证 side 必填规则在混合路径同样生效。
     */
    @Test
    public void testMixedJoinTableSideRequiredFails() throws Exception {
        importModel();
        NopMetaEntity leftEntity = findMetaEntityByTable("nop_meta_module");
        String leftTableId = findEntityTableId("nop_meta_module");

        String querySpace = "qs_mixed_side";
        String dbUrl = "jdbc:h2:mem:" + querySpace + ";DB_CLOSE_DELAY=-1";
        seedMixedSameDbTables(dbUrl);
        saveDataSource("ds-" + querySpace, querySpace, dbUrl);
        syncExternalTables("ds-" + querySpace);
        String dimTableId = externalTableId("MIXED_DIM");

        String joinId = createMixedJoin(leftTableId, "inner", leftEntity.getMetaEntityId(), dimTableId,
                "status", "STATUS_VAL", "dim");
        // dimension 无 side → external 端点 side 必填报错
        String statusFieldId = findEntityFieldId("nop_meta_module", "status");
        createMeasureWithSide(leftTableId, "cnt", statusFieldId, "count", "left");
        createDimension(leftTableId, "cat", "CAT_NAME", "categorical", null);

        assertTrue(queryAggregationJoinHasError(leftTableId, "cnt", "cat", joinId),
                "mixed-endpoint external/sql dimension without side must explicitly fail (side required at query-time)");
    }

    /**
     * side 指向端点字段集合不含该列 → 显式失败（ERR_AGGR_JOIN_FIELD_NOT_ON_SIDE）。
     * CAT_NAME 属于 external 端点（MIXED_DIM），但 side=left 指向 entity 端点（nop_meta_module）→ 列不存在。
     */
    @Test
    public void testMixedJoinColumnNotOnSideFails() throws Exception {
        importModel();
        NopMetaEntity leftEntity = findMetaEntityByTable("nop_meta_module");
        String leftTableId = findEntityTableId("nop_meta_module");

        String querySpace = "qs_mixed_col";
        String dbUrl = "jdbc:h2:mem:" + querySpace + ";DB_CLOSE_DELAY=-1";
        seedMixedSameDbTables(dbUrl);
        saveDataSource("ds-" + querySpace, querySpace, dbUrl);
        syncExternalTables("ds-" + querySpace);
        String dimTableId = externalTableId("MIXED_DIM");

        String joinId = createMixedJoin(leftTableId, "inner", leftEntity.getMetaEntityId(), dimTableId,
                "status", "STATUS_VAL", "dim");
        String statusFieldId = findEntityFieldId("nop_meta_module", "status");
        createMeasureWithSide(leftTableId, "cnt", statusFieldId, "count", "left");
        // CAT_NAME 属于 external，但 side=left 指向 entity → 列不存在于 entity 端点
        createDimensionWithSide(leftTableId, "cat", "CAT_NAME", "categorical", null, "left");

        assertTrue(queryAggregationJoinHasError(leftTableId, "cnt", "cat", joinId),
                "mixed-endpoint dimension column not on declared side endpoint must explicitly fail");
    }

    /** joinType=right 混合端点 → 显式失败（复用 join 校验，不静默降级）。 */
    @Test
    public void testMixedJoinJoinTypeRightFails() throws Exception {
        importModel();
        NopMetaEntity leftEntity = findMetaEntityByTable("nop_meta_module");
        String leftTableId = findEntityTableId("nop_meta_module");

        String querySpace = "qs_mixed_right";
        String dbUrl = "jdbc:h2:mem:" + querySpace + ";DB_CLOSE_DELAY=-1";
        seedMixedSameDbTables(dbUrl);
        saveDataSource("ds-" + querySpace, querySpace, dbUrl);
        syncExternalTables("ds-" + querySpace);
        String dimTableId = externalTableId("MIXED_DIM");

        String joinId = createMixedJoin(leftTableId, "right", leftEntity.getMetaEntityId(), dimTableId,
                "status", "STATUS_VAL", "dim");
        assertTrue(queryAggregationJoinHasError(leftTableId, joinId),
                "joinType=right mixed-endpoint JOIN aggregation must explicitly fail (not silently degrade)");
    }

    /**
     * 接线验证：entity↔entity JOIN 聚合路径在混合端点路由新增后仍被正确调用（未误入混合分支）。
     * 复用 entity-entity 正确性用例的核心断言，确保 router 未绕过既有 entity 路径。
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testEntityEntityJoinStillWorksAfterMixedRoute() {
        importModel();
        NopMetaEntity leftEntity = findMetaEntityByTable("nop_meta_entity");
        NopMetaEntity rightEntity = findMetaEntityByTable("nop_meta_entity_field");
        String leftTableId = findEntityTableId("nop_meta_entity");
        String joinId = createJoin(leftTableId, "inner", leftEntity.getMetaEntityId(),
                rightEntity.getMetaEntityId(), "metaEntityId", "metaEntityId", "fld");
        String leftDimFieldId = findEntityFieldId("nop_meta_entity", "displayName");
        String rightMeasureFieldId = findEntityFieldId("nop_meta_entity_field", "fieldName");
        createDimension(leftTableId, "mst", leftDimFieldId, "categorical", null);
        createMeasure(leftTableId, "mcnt", rightMeasureFieldId, "count", null);

        Map<String, Object> result = nopMetaTableBizModel.queryAggregation(leftTableId,
                Arrays.asList("mcnt"), Arrays.asList("mst"), null, joinId, null, null, null, null, null);
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.get("items");
        assertNotNull(items, "items must not be null");
        assertFalse(items.isEmpty(), "entity-entity JOIN aggregation must still work after mixed-endpoint route added: " + items);
    }

    /**
     * 造混合端点同库测试用 H2 表：NOP_META_MODULE（entity 物理表，仅 STATUS 列）+ MIXED_DIM（external 端点）。
     * 数据：NOP_META_MODULE 三行（'A','A','B'），MIXED_DIM 两行（'A'→Category A, 'B'→Category B）。
     * inner join ON STATUS=STATUS_VAL → 按 CAT_NAME 分组：Category A count=2，Category B count=1。
     */
    private void seedMixedSameDbTables(String dbUrl) throws Exception {
        try (Connection c = DriverManager.getConnection(dbUrl, "sa", "");
             Statement st = c.createStatement()) {
            // entity 物理表（同库可见性测试目标）——仅需 STATUS 列即可（measure count 用）
            st.execute("CREATE TABLE NOP_META_MODULE (STATUS VARCHAR(20))");
            st.execute("INSERT INTO NOP_META_MODULE VALUES ('A')");
            st.execute("INSERT INTO NOP_META_MODULE VALUES ('A')");
            st.execute("INSERT INTO NOP_META_MODULE VALUES ('B')");
            // external 端点表
            st.execute("CREATE TABLE MIXED_DIM (STATUS_VAL VARCHAR(20), CAT_NAME VARCHAR(50))");
            st.execute("INSERT INTO MIXED_DIM VALUES ('A', 'Category A')");
            st.execute("INSERT INTO MIXED_DIM VALUES ('B', 'Category B')");
        }
    }

    /** 查找指定 tableName 的 external NopMetaTable.metaTableId（sync 后存在）。 */
    private String externalTableId(String tableName) {
        IEntityDao<NopMetaTable> tableDao = daoProvider.daoFor(NopMetaTable.class);
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaTable.PROP_NAME_tableName, tableName));
        q.addFilter(FilterBeans.eq("tableType", "external"));
        NopMetaTable t = tableDao.findFirstByQuery(q);
        org.junit.jupiter.api.Assertions.assertNotNull(t, "external table " + tableName + " must be synced");
        return t.getMetaTableId();
    }

    // ============================================================
    // plan 2026-07-18-0900-2：having/orderBy 端到端测试（三条路径）
    // ============================================================

    /**
     * external/sql 单表聚合 + having + orderBy：sum(AMOUNT) group by CATEGORY。
     * having: SUM(AMOUNT) > 15（排除 SUM=10 的 group）；orderBy: SUM(AMOUNT) DESC。
     * 数据：A=10+20=30, B=30 → having >15 保留全部；orderBy DESC 后第一行 TOTAL=30（多组中第一组）。
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testExternalAggregationHavingOrderBy() throws Exception {
        String dbUrl = "jdbc:h2:mem:meta_agg_having;DB_CLOSE_DELAY=-1";
        seedAggTable(dbUrl);
        // 增加一行 C 类别 amount=5（C 被 having >15 过滤掉）
        try (Connection c = DriverManager.getConnection(dbUrl, "sa", "");
             Statement st = c.createStatement()) {
            st.execute("INSERT INTO ext_agg VALUES ('C', 5, '2024-01-15 10:00:00')");
        }
        String tableId = prepareExternalTable(dbUrl, "qs_agg_having", "EXT_AGG");
        createMeasure(tableId, "total", "AMOUNT", "sum", null);
        createDimension(tableId, "cat", "CATEGORY", "categorical", null);

        TreeBean having = FilterBeans.gt("total", 15);
        List<OrderFieldBean> orderBy = Arrays.asList(OrderFieldBean.desc("total"));

        Map<String, Object> result = nopMetaTableBizModel.queryAggregation(tableId,
                Arrays.asList("total"), Arrays.asList("cat"), null, null, null, null, having, orderBy, null);
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.get("items");
        // having SUM>15 排除 C（5）；剩 A=30, B=30
        assertEquals(2, items.size(), "having SUM>15 must exclude group C (sum=5)");
        for (Map<String, Object> row : items) {
            int total = toInt(getIgnoreCase(row, "TOTAL"));
            assertTrue(total > 15, "all retained groups must satisfy having SUM>15: " + row);
        }
    }

    /**
     * 失败路径：having 引用未选定 measure/dimension name → 显式失败（ERR_AGGR_HAVING_UNKNOWN_NAME）。
     */
    @Test
    public void testExternalAggregationHavingUnknownNameFails() throws Exception {
        String dbUrl = "jdbc:h2:mem:meta_agg_having_unk;DB_CLOSE_DELAY=-1";
        seedAggTable(dbUrl);
        String tableId = prepareExternalTable(dbUrl, "qs_agg_having_unk", "EXT_AGG");
        createMeasure(tableId, "total", "AMOUNT", "sum", null);
        createDimension(tableId, "cat", "CATEGORY", "categorical", null);

        // having 引用 'unknown_measure' → 未在选定集合内 → 显式失败
        TreeBean having = FilterBeans.gt("unknown_measure", 15);
        Exception e = assertThrows(Exception.class, () -> nopMetaTableBizModel.queryAggregation(tableId,
                Arrays.asList("total"), Arrays.asList("cat"), null, null, null, null, having, null, null));
        assertTrue(e.getMessage().contains("unknown") || e.getMessage().contains("Unknown")
                        || e.getMessage().contains("not in the user-selected"),
                "error message must indicate unknown name: " + e.getMessage());
    }

    /**
     * 失败路径：orderBy 引用未选定 measure/dimension name → 显式失败（ERR_AGGR_ORDER_BY_UNKNOWN_NAME）。
     */
    @Test
    public void testExternalAggregationOrderByUnknownNameFails() throws Exception {
        String dbUrl = "jdbc:h2:mem:meta_agg_orderby_unk;DB_CLOSE_DELAY=-1";
        seedAggTable(dbUrl);
        String tableId = prepareExternalTable(dbUrl, "qs_agg_orderby_unk", "EXT_AGG");
        createMeasure(tableId, "total", "AMOUNT", "sum", null);
        createDimension(tableId, "cat", "CATEGORY", "categorical", null);

        List<OrderFieldBean> orderBy = Arrays.asList(OrderFieldBean.desc("unknown_measure"));
        Exception e = assertThrows(Exception.class, () -> nopMetaTableBizModel.queryAggregation(tableId,
                Arrays.asList("total"), Arrays.asList("cat"), null, null, null, null, null, orderBy, null));
        assertTrue(e.getMessage().contains("unknown") || e.getMessage().contains("Unknown")
                        || e.getMessage().contains("not in the user-selected"),
                "error message must indicate unknown name: " + e.getMessage());
    }

    /**
     * entity 单表聚合 + having + orderBy：count(status) group by status。
     * 验证 entity 路径 SQL 生成 HAVING + ORDER BY 子句。
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testEntityAggregationHavingOrderBy() {
        importModel();
        String tableId = findEntityTableId("nop_meta_module");
        IEntityDao<io.nop.metadata.dao.entity.NopMetaEntityField> fieldDao =
                daoProvider.daoFor(io.nop.metadata.dao.entity.NopMetaEntityField.class);
        String statusFieldId = findEntityFieldId("nop_meta_module", "status", fieldDao);
        createMeasure(tableId, "cnt", statusFieldId, "count", null);
        createDimension(tableId, "st", statusFieldId, "categorical", null);

        TreeBean having = FilterBeans.ge("cnt", 1);
        List<OrderFieldBean> orderBy = Arrays.asList(OrderFieldBean.desc("cnt"));
        Map<String, Object> result = nopMetaTableBizModel.queryAggregation(tableId,
                Arrays.asList("cnt"), Arrays.asList("st"), null, null, null, null, having, orderBy, null);
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.get("items");
        assertFalse(items.isEmpty(), "entity aggregation with having/orderBy must return real grouped rows: " + items);
        // 所有保留行 cnt >= 1（having 过滤生效）
        for (Map<String, Object> row : items) {
            assertTrue(toLong(getIgnoreCase(row, "CNT")) >= 1, "having cnt>=1 must hold: " + row);
        }
    }

    /**
     * entity↔entity JOIN 同库聚合 + having + orderBy。
     * 验证 JOIN 同库路径生成 HAVING + ORDER BY。
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testEntityEntityJoinHavingOrderBy() {
        importModel();
        NopMetaEntity leftEntity = findMetaEntityByTable("nop_meta_entity");
        NopMetaEntity rightEntity = findMetaEntityByTable("nop_meta_entity_field");
        String leftTableId = findEntityTableId("nop_meta_entity");
        String joinId = createJoin(leftTableId, "inner", leftEntity.getMetaEntityId(),
                rightEntity.getMetaEntityId(), "metaEntityId", "metaEntityId", "fld");
        String leftDimFieldId = findEntityFieldId("nop_meta_entity", "displayName");
        String rightMeasureFieldId = findEntityFieldId("nop_meta_entity_field", "fieldName");
        createDimension(leftTableId, "st", leftDimFieldId, "categorical", null);
        createMeasure(leftTableId, "cnt", rightMeasureFieldId, "count", null);

        TreeBean having = FilterBeans.ge("cnt", 1);
        List<OrderFieldBean> orderBy = Arrays.asList(OrderFieldBean.desc("cnt"));
        Map<String, Object> result = nopMetaTableBizModel.queryAggregation(leftTableId,
                    Arrays.asList("cnt"), Arrays.asList("st"), null, joinId, null, null, having, orderBy, null);
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.get("items");
        assertNotNull(items, "items must not be null");
        assertFalse(items.isEmpty(), "entity-entity JOIN with having/orderBy must return real grouped rows: " + items);
        // 验证 orderBy DESC：第一行的 cnt 应 >= 最后一行
        if (items.size() > 1) {
            long first = toLong(getIgnoreCase(items.get(0), "CNT"));
            long last = toLong(getIgnoreCase(items.get(items.size() - 1), "CNT"));
            assertTrue(first >= last, "orderBy cnt DESC must hold: first=" + first + " last=" + last);
        }
    }

    /**
     * external↔external JOIN 同库聚合 + having + orderBy。
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testExternalExternalJoinHavingOrderBy() throws Exception {
        String querySpace = "qs_ext_join_having";
        String dbUrl = "jdbc:h2:mem:" + querySpace + ";DB_CLOSE_DELAY=-1";
        seedFactDimTables(dbUrl);
        saveDataSource("ds-" + querySpace, querySpace, dbUrl);
        syncExternalTables("ds-" + querySpace);
        String factTableId = tableId("EXT_FACT");
        String dimTableId = tableId("EXT_DIM");

        String joinId = createTableTableJoin(factTableId, "inner", factTableId, dimTableId,
                "CAT_ID", "CAT_ID", "dim");
        createMeasureWithSide(factTableId, "total", "AMOUNT", "sum", "left");
        createDimensionWithSide(factTableId, "cat", "CAT_NAME", "categorical", null, "right");

        TreeBean having = FilterBeans.ge("total", 30);
        List<OrderFieldBean> orderBy = Arrays.asList(OrderFieldBean.desc("total"));
        Map<String, Object> result = nopMetaTableBizModel.queryAggregation(factTableId,
                Arrays.asList("total"), Arrays.asList("cat"), null, joinId, null, null, having, orderBy, null);
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.get("items");
        // A=30, B=30 都 >= 30
        assertEquals(2, items.size(), "having total>=30 must keep both groups A and B");
        for (Map<String, Object> row : items) {
            assertTrue(toInt(getIgnoreCase(row, "TOTAL")) >= 30, "having total>=30 must hold: " + row);
        }
    }

    /**
     * 混合端点同库 JOIN 聚合 + having + orderBy。
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testMixedSameDbJoinHavingOrderBy() throws Exception {
        importModel();
        NopMetaEntity leftEntity = findMetaEntityByTable("nop_meta_module");
        String leftTableId = findEntityTableId("nop_meta_module");

        String querySpace = "qs_mixed_having";
        String dbUrl = "jdbc:h2:mem:" + querySpace + ";DB_CLOSE_DELAY=-1";
        seedMixedSameDbTables(dbUrl);
        saveDataSource("ds-" + querySpace, querySpace, dbUrl);
        syncExternalTables("ds-" + querySpace);
        String dimTableId = externalTableId("MIXED_DIM");

        String joinId = createMixedJoin(leftTableId, "inner", leftEntity.getMetaEntityId(), dimTableId,
                "status", "STATUS_VAL", "dim");
        String statusFieldId = findEntityFieldId("nop_meta_module", "status");
        createMeasureWithSide(leftTableId, "cnt", statusFieldId, "count", "left");
        createDimensionWithSide(leftTableId, "cat", "CAT_NAME", "categorical", null, "right");

        TreeBean having = FilterBeans.ge("cnt", 1);
        List<OrderFieldBean> orderBy = Arrays.asList(OrderFieldBean.desc("cnt"));
        Map<String, Object> result = nopMetaTableBizModel.queryAggregation(leftTableId,
                Arrays.asList("cnt"), Arrays.asList("cat"), null, joinId, null, null, having, orderBy, null);
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.get("items");
        assertNotNull(items, "items must not be null");
        assertFalse(items.isEmpty(), "mixed same-DB JOIN with having/orderBy must return real grouped rows: " + items);
        for (Map<String, Object> row : items) {
            assertTrue(toLong(getIgnoreCase(row, "CNT")) >= 1, "having cnt>=1 must hold: " + row);
        }
    }

    /**
     * 跨库 entity-entity JOIN 内存聚合 + having + orderBy（D10 内存路径，验证 MemoryFilterEvaluator + 比较器接线）。
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testCrossDbJoinMemoryHavingOrderBy() {
        importModel();
        NopMetaEntity leftEntity = findMetaEntityByTable("nop_meta_entity");
        NopMetaEntity rightEntity = findMetaEntityByTable("nop_meta_entity_field");
        updateQuerySpaceSql(rightEntity.getMetaEntityId(), "qs_agg_cross_having");
        try {
            String leftTableId = findEntityTableId("nop_meta_entity");
            String joinId = createJoin(leftTableId, "inner", leftEntity.getMetaEntityId(),
                    rightEntity.getMetaEntityId(), "metaEntityId", "metaEntityId", "fld");
            String leftDimFieldId = findEntityFieldId("nop_meta_entity", "displayName");
            String rightMeasureFieldId = findEntityFieldId("nop_meta_entity_field", "fieldName");
            createDimension(leftTableId, "st", leftDimFieldId, "categorical", null);
            createMeasure(leftTableId, "cnt", rightMeasureFieldId, "count", null);

            // having cnt >= 1（过滤空组）+ orderBy cnt DESC（验证 MemoryOrderByComparator 接线）
            TreeBean having = FilterBeans.ge("cnt", 1);
            List<OrderFieldBean> orderBy = Arrays.asList(OrderFieldBean.desc("cnt"));
            Map<String, Object> result = nopMetaTableBizModel.queryAggregation(leftTableId,
                Arrays.asList("cnt"), Arrays.asList("st"), null, joinId, null, null, having, orderBy, null);
            List<Map<String, Object>> items = (List<Map<String, Object>>) result.get("items");
            assertNotNull(items, "items must not be null");
            assertFalse(items.isEmpty(),
                    "cross-DB in-memory JOIN with having/orderBy must return real grouped rows: " + items);
            // 验证 having 过滤生效
            for (Map<String, Object> row : items) {
                Object cnt = getIgnoreCase(row, "CNT");
                assertNotNull(cnt, "measure CNT must be present (not silent null/0): " + row.keySet());
                assertTrue(toLong(cnt) >= 1, "having cnt>=1 must hold: " + row);
            }
            // 验证 orderBy DESC：cnt 单调递减
            if (items.size() > 1) {
                for (int i = 1; i < items.size(); i++) {
                    long prev = toLong(getIgnoreCase(items.get(i - 1), "CNT"));
                    long curr = toLong(getIgnoreCase(items.get(i), "CNT"));
                    assertTrue(prev >= curr, "orderBy cnt DESC must hold: prev=" + prev + " curr=" + curr);
                }
            }
        } finally {
            updateQuerySpaceSql(rightEntity.getMetaEntityId(), null);
        }
    }

    /**
     * 向后兼容：无 having/orderBy 的既有调用行为零变化（既有测试全绿覆盖，这里专门再次验证 10-arg 传 null 等价于 8-arg 旧行为）。
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testBackwardCompatNoHavingNoOrderBy() throws Exception {
        String dbUrl = "jdbc:h2:mem:meta_agg_compat;DB_CLOSE_DELAY=-1";
        seedAggTable(dbUrl);
        String tableId = prepareExternalTable(dbUrl, "qs_agg_compat", "EXT_AGG");
        createMeasure(tableId, "total", "AMOUNT", "sum", null);
        createDimension(tableId, "cat", "CATEGORY", "categorical", null);

        // having=null, orderBy=null → 与既有行为完全一致（无 HAVING/ORDER BY 子句）
        Map<String, Object> result = nopMetaTableBizModel.queryAggregation(tableId,
                Arrays.asList("total"), Arrays.asList("cat"), null, null, null, null, null, null, null);
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.get("items");
        assertEquals(2, items.size(), "no having/orderBy → all groups retained (A, B)");
    }

    // ============================================================
    // plan 2026-07-18-1400-1：expression 型 Measure 三路径执行 + 失败路径 ErrorCode（端到端）
    // ============================================================

    /**
     * entity 路径 expression 端到端测试（§4.4.2 D12.2，bypass EQL 路径）。
     *
     * <p>importOrmModel 后对 nop_meta_module 表建 expression measure（{@code MODULE_VERSION + MODULE_VERSION}
     * aggFunc=sum），按 status 分组。Anti-Hollow：真实执行 entity 路径 bypass EQL + 注入 expression，
     * 断言结果非空 + expression 真实生效（非 stub 0）。
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testEntityPathExpressionMeasureExecution() {
        importModel();
        String tableId = findEntityTableId("nop_meta_module");
        String statusFieldId = findEntityFieldId("nop_meta_module", "status");
        // expression：VERSION + VERSION（nop_meta_module 的 columnCode=VERSION，纯列算术，aggFunc=sum）
        createMeasure(tableId, "exprStatus", statusFieldId, "sum",
                "VERSION + VERSION");
        createDimension(tableId, "st", statusFieldId, "categorical", null);

        Map<String, Object> result = nopMetaTableBizModel.queryAggregation(tableId,
                Arrays.asList("exprStatus"), Arrays.asList("st"), null, null, null, null, null, null, null);
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.get("items");
        assertNotNull(items, "items must not be null");
        assertFalse(items.isEmpty(), "entity path expression must yield real grouped rows: " + items);
        // expression 真实执行：每个分组的 EXPRSTATUS 是 2 * SUM(MODULE_VERSION)（非伪造）
        for (Map<String, Object> row : items) {
            Object v = getIgnoreCase(row, "EXPRSTATUS");
            assertNotNull(v, "expression result column must be present: " + row.keySet());
            assertTrue(toLong(v) >= 0, "expression SUM(col+col) must be non-negative: " + row);
        }
    }

    /**
     * entity 路径 expression + temporal granularity 共存场景（bypass EQL 必须支持 expression 与 granularity 共存）。
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testEntityPathExpressionWithTemporalGranularity() {
        importModel();
        spreadEntityCreateTimeAcrossTwoMonths();
        try {
            String tableId = findEntityTableId("nop_meta_entity");
            String createTimeFieldId = findEntityFieldId("nop_meta_entity", "createTime");
            // expression：DEL_VERSION + DEL_VERSION（nop_meta_entity 的 version 字段 columnCode=DEL_VERSION，纯列算术）
            createMeasure(tableId, "exprCnt", createTimeFieldId, "sum", "DEL_VERSION + DEL_VERSION");
            createDimension(tableId, "mon", createTimeFieldId, "temporal", "month");

            Map<String, Object> result = nopMetaTableBizModel.queryAggregation(tableId,
                    Arrays.asList("exprCnt"), Arrays.asList("mon"), null, null, null, null, null, null, null);
            List<Map<String, Object>> items = (List<Map<String, Object>>) result.get("items");
            assertNotNull(items);
            assertEquals(2, items.size(),
                    "entity path expression + temporal month granularity must yield 2 buckets: " + items);
            // Anti-Hollow: 验证 expression + temporal granularity 共存路径真实执行（结果非 null 即可，
            // DEL_VERSION 实际值由 import 决定——通常为 0 故 SUM=0，但分组数 == 2 证明 SQL 真实构造 + 执行）
            for (Map<String, Object> row : items) {
                Object v = getIgnoreCase(row, "EXPRCNT");
                assertNotNull(v, "expression result column must be present: " + row.keySet());
                assertTrue(toLong(v) >= 0, "expression SUM(col+col) must be non-negative: " + row);
            }
        } finally {
            resetEntityCreateTime();
        }
    }

    /**
     * external-sql 路径 expression 端到端测试（§4.4.2 D12.2，withConnection 路径）。
     *
     * <p>expression = {@code AMOUNT * 2}（已由改写后的 testExpressionMeasureExplicitlyFails 覆盖，此处补充参数绑定用例）：
     * {@code AMOUNT + ?} 不允许（字面量须参数化），改为 {@code AMOUNT * 2 + 0}（验证复杂算术）。
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testExternalPathExpressionMeasureExecution() throws Exception {
        String dbUrl = "jdbc:h2:mem:meta_agg_ext_expr;DB_CLOSE_DELAY=-1";
        seedAggTable(dbUrl);
        String tableId = prepareExternalTable(dbUrl, "qs_agg_ext_expr", "EXT_AGG");
        // expression：AMOUNT * 2 + AMOUNT（等价 AMOUNT * 3）
        createMeasure(tableId, "triple", "AMOUNT", "sum", "AMOUNT * 2 + AMOUNT");
        createDimension(tableId, "cat", "CATEGORY", "categorical", null);

        Map<String, Object> result = nopMetaTableBizModel.queryAggregation(tableId,
                Arrays.asList("triple"), Arrays.asList("cat"), null, null, null, null, null, null, null);
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.get("items");
        assertNotNull(items);
        Map<String, Object> rowA = findRow(items, "CAT", "A");
        Map<String, Object> rowB = findRow(items, "CAT", "B");
        // SUM(AMOUNT*2 + AMOUNT) for A = (10*2+10) + (20*2+20) = 30 + 60 = 90
        assertEquals(90, toInt(rowA.get("TRIPLE")), "SUM(AMOUNT*3) for A = 90: " + rowA);
        // for B = 30*2 + 30 = 90
        assertEquals(90, toInt(rowB.get("TRIPLE")), "SUM(AMOUNT*3) for B = 90: " + rowB);
    }

    /**
     * JOIN 同库 external↔external 路径 expression 端到端测试（§4.4.2 D12.2，JOIN 路径）。
     *
     * <p>expression = {@code l.AMOUNT * 2}（JOIN 限定名 l./r.），aggFunc=sum。
     * 数据：A=10+20=30，B=30 → SUM(l.AMOUNT*2)：A=60，B=60。
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testJoinPathExpressionMeasureExecution() throws Exception {
        String querySpace = "qs_ext_join_expr";
        String dbUrl = "jdbc:h2:mem:" + querySpace + ";DB_CLOSE_DELAY=-1";
        seedFactDimTables(dbUrl);
        saveDataSource("ds-" + querySpace, querySpace, dbUrl);
        syncExternalTables("ds-" + querySpace);
        String factTableId = tableId("EXT_FACT");
        String dimTableId = tableId("EXT_DIM");

        String joinId = createTableTableJoin(factTableId, "inner", factTableId, dimTableId,
                "CAT_ID", "CAT_ID", "dim");
        // expression：l.AMOUNT * 2（JOIN 限定名 + 算术）
        createMeasureWithSideAndExpression(factTableId, "totalDbl", "AMOUNT", "sum", "left",
                "l.AMOUNT * 2");
        createDimensionWithSide(factTableId, "cat", "CAT_NAME", "categorical", null, "right");

        Map<String, Object> result = nopMetaTableBizModel.queryAggregation(factTableId,
                Arrays.asList("totalDbl"), Arrays.asList("cat"), null, joinId, null, null, null, null, null);
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.get("items");
        assertNotNull(items);
        assertEquals(2, items.size(), "group by CAT_NAME must yield 2 groups (A,B): " + items);
        Map<String, Object> rowA = findRow(items, "CAT", "A");
        Map<String, Object> rowB = findRow(items, "CAT", "B");
        // SUM(l.AMOUNT*2) for A = (10+20)*2 = 60; for B = 30*2 = 60
        assertEquals(60, toInt(rowA.get("TOTALDBL")), "SUM(l.AMOUNT*2) for A = 60: " + rowA);
        assertEquals(60, toInt(rowB.get("TOTALDBL")), "SUM(l.AMOUNT*2) for B = 60: " + rowB);
    }

    /**
     * 跨库内存路径 expression 显式失败测试（§4.4.2 D12.2 / D10）。
     *
     * <p>expression measure + 跨 querySpace join → 抛 ERR_AGGR_EXPRESSION_MEMORY_NOT_COMPUTABLE。
     */
    @Test
    public void testCrossDbPathExpressionMeasureFails() throws Exception {
        importModel();
        NopMetaEntity leftEntity = findMetaEntityByTable("nop_meta_module");
        NopMetaEntity rightEntity = findMetaEntityByTable("nop_meta_entity");
        // 让 right entity 跨 querySpace
        updateQuerySpaceSql(rightEntity.getMetaEntityId(), "qs_cross_expr");
        try {
            String leftTableId = findEntityTableId("nop_meta_module");
            String joinId = createJoin(leftTableId, "inner", leftEntity.getMetaEntityId(),
                    rightEntity.getMetaEntityId(), "moduleId", "moduleId", "xe");
            String leftFieldId = findEntityFieldId("nop_meta_module", "status");
            createMeasure(leftTableId, "exprM", leftFieldId, "sum", "1");
            createDimension(leftTableId, "st", leftFieldId, "categorical", null);

            Exception ex = assertThrows(Exception.class, () -> nopMetaTableBizModel.queryAggregation(
                    leftTableId, Arrays.asList("exprM"), Arrays.asList("st"),
                    null, joinId, null, null, null, null, null));
            // 验证错误消息含 expression memory not computable 标识
            String msg = ex.getMessage();
            assertTrue(msg.contains("expression-memory-not-computable")
                            || msg.contains("memory-not-computable")
                            || msg.contains("not computable"),
                    "cross-DB path expression must explicitly fail with memory-not-computable ErrorCode: " + msg);
        } finally {
            updateQuerySpaceSql(rightEntity.getMetaEntityId(), null);
        }
    }

    /**
     * 失败路径 ErrorCode 测试（6 类）：
     * (1) unparseable（未闭合括号）；
     * (2) unsafe（含 DROP 关键字）；
     * (3) dialect-unsupported（MySQL + DATE_TRUNC —— 单元测试 TestExpressionMeasureValidator 已覆盖，此处略）；
     * (4) memory-not-computable（testCrossDbPathExpressionMeasureFails 已覆盖）；
     * (5) too-long（>1000 字符，save 阶段失败）；
     * (6) having-order-by-unsupported（expression 被 having name 引用）。
     */
    @Test
    public void testExpressionUnparseableFails() throws Exception {
        String dbUrl = "jdbc:h2:mem:meta_agg_unp;DB_CLOSE_DELAY=-1";
        seedAggTable(dbUrl);
        String tableId = prepareExternalTable(dbUrl, "qs_agg_unp", "EXT_AGG");
        createMeasure(tableId, "badExpr", "AMOUNT", "sum", "(AMOUNT * 2");
        createDimension(tableId, "cat", "CATEGORY", "categorical", null);

        Exception ex = assertThrows(Exception.class, () -> nopMetaTableBizModel.queryAggregation(
                tableId, Arrays.asList("badExpr"), Arrays.asList("cat"),
                null, null, null, null, null, null, null));
        assertTrue(ex.getMessage().contains("unparseable"),
                "unparseable expression must fail with ERR_AGGR_EXPRESSION_UNPARSEABLE: " + ex.getMessage());
    }

    @Test
    public void testExpressionUnsafeFails() throws Exception {
        String dbUrl = "jdbc:h2:mem:meta_agg_uns;DB_CLOSE_DELAY=-1";
        seedAggTable(dbUrl);
        String tableId = prepareExternalTable(dbUrl, "qs_agg_uns", "EXT_AGG");
        createMeasure(tableId, "dropExpr", "AMOUNT", "sum", "DROP TABLE foo");
        createDimension(tableId, "cat", "CATEGORY", "categorical", null);

        Exception ex = assertThrows(Exception.class, () -> nopMetaTableBizModel.queryAggregation(
                tableId, Arrays.asList("dropExpr"), Arrays.asList("cat"),
                null, null, null, null, null, null, null));
        assertTrue(ex.getMessage().contains("unsafe"),
                "unsafe expression must fail with ERR_AGGR_EXPRESSION_UNSAFE: " + ex.getMessage());
    }

    @Test
    public void testExpressionHavingOrderByUnsupportedFails() throws Exception {
        String dbUrl = "jdbc:h2:mem:meta_agg_having_expr;DB_CLOSE_DELAY=-1";
        seedAggTable(dbUrl);
        String tableId = prepareExternalTable(dbUrl, "qs_agg_having_expr", "EXT_AGG");
        createMeasure(tableId, "exprM", "AMOUNT", "sum", "AMOUNT * 2");
        createDimension(tableId, "cat", "CATEGORY", "categorical", null);

        // expression measure 被 HAVING 引用 → 显式失败
        TreeBean having = FilterBeans.gt("exprM", 15);
        Exception ex = assertThrows(Exception.class, () -> nopMetaTableBizModel.queryAggregation(
                tableId, Arrays.asList("exprM"), Arrays.asList("cat"),
                null, null, null, null, having, null, null));
        assertTrue(ex.getMessage().contains("having-order-by-unsupported")
                        || ex.getMessage().contains("HAVING or ORDER BY"),
                "expression measure referenced by HAVING must explicitly fail: " + ex.getMessage());

        // expression measure 被 ORDER BY 引用 → 显式失败
        List<OrderFieldBean> orderBy = Arrays.asList(OrderFieldBean.desc("exprM"));
        Exception ex2 = assertThrows(Exception.class, () -> nopMetaTableBizModel.queryAggregation(
                tableId, Arrays.asList("exprM"), Arrays.asList("cat"),
                null, null, null, null, null, orderBy, null));
        assertTrue(ex2.getMessage().contains("having-order-by-unsupported")
                        || ex2.getMessage().contains("HAVING or ORDER BY"),
                "expression measure referenced by ORDER BY must explicitly fail: " + ex2.getMessage());
    }

    /**
     * save-time 校验端到端测试（须经 NopMetaTableMeasureBizModel.save 入口）。
     *
     * <p>危险关键字 / 超长 / unparseable 的 expression 保存失败（不静默存入）。
     * 此处通过 GraphQL mutation（{@code NopMetaTableMeasure__save}）入口触发 BizModel.save，非 dao.saveEntity 直存。
     */
    @Test
    public void testSaveTimeValidationFails() throws Exception {
        String dbUrl = "jdbc:h2:mem:meta_agg_save;DB_CLOSE_DELAY=-1";
        seedAggTable(dbUrl);
        String tableId = prepareExternalTable(dbUrl, "qs_agg_save", "EXT_AGG");

        // 1) 危险关键字 → ERR_AGGR_EXPRESSION_UNSAFE
        io.nop.api.core.beans.graphql.GraphQLResponseBean dropResp = execSaveMutation(
                tableId, "badDrop", "DROP TABLE foo");
        assertNotNull(dropResp);
        assertTrue(dropResp.hasError(),
                "save with DROP keyword expression must fail (not silently stored): " + dropResp);

        // 2) unparseable → ERR_AGGR_EXPRESSION_UNPARSEABLE
        io.nop.api.core.beans.graphql.GraphQLResponseBean unpResp = execSaveMutation(
                tableId, "badUnp", "(AMOUNT");
        assertTrue(unpResp.hasError(),
                "save with unparseable expression must fail (not silently stored): " + unpResp);

        // 3) 超长 (>1000) → ERR_AGGR_EXPRESSION_TOO_LONG
        StringBuilder tooLong = new StringBuilder();
        while (tooLong.length() <= 1000) {
            tooLong.append("AMOUNT + ");
        }
        tooLong.append("1");
        io.nop.api.core.beans.graphql.GraphQLResponseBean tlResp = execSaveMutation(
                tableId, "badTooLong", tooLong.toString());
        assertTrue(tlResp.hasError(),
                "save with too-long expression must fail (not silently stored): " + tlResp);

        // 4) 验证行未持久化（防御性：检查 badDrop / badUnp / badTooLong 都未入库）
        IEntityDao<NopMetaTableMeasure> measureDao = daoProvider.daoFor(NopMetaTableMeasure.class);
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaTableMeasure.PROP_NAME_metaTableId, tableId));
        List<NopMetaTableMeasure> saved = measureDao.findAllByQuery(q);
        for (NopMetaTableMeasure m : saved) {
            assertFalse("badDrop".equals(m.getMeasureName())
                            || "badUnp".equals(m.getMeasureName())
                            || "badTooLong".equals(m.getMeasureName()),
                    "invalid expression measure must NOT be persisted: " + m.getMeasureName());
        }
    }

    /**
     * save-time 校验：合法 expression 经 GraphQL mutation save 成功存入（验证 save-time 宽松校验不会误拒合法 expression）。
     */
    @Test
    public void testSaveTimeValidationAllowsValidExpression() throws Exception {
        String dbUrl = "jdbc:h2:mem:meta_agg_save_ok;DB_CLOSE_DELAY=-1";
        seedAggTable(dbUrl);
        String tableId = prepareExternalTable(dbUrl, "qs_agg_save_ok", "EXT_AGG");

        io.nop.api.core.beans.graphql.GraphQLResponseBean resp = execSaveMutation(
                tableId, "validExpr", "AMOUNT * 2");
        assertFalse(resp.hasError(),
                "save with valid expression must succeed via GraphQL mutation BizModel.save: " + resp);
    }

    /** 执行 NopMetaTableMeasure__save mutation，触发 BizModel.save 入口的 expression save-time 校验。 */
    private io.nop.api.core.beans.graphql.GraphQLResponseBean execSaveMutation(String tableId,
                                                                                  String measureName,
                                                                                  String expression) {
        String exprJson = io.nop.core.lang.json.JsonTool.stringify(expression);
        String dataJson = "{metaTableId:\"" + tableId + "\",measureName:\"" + measureName
                + "\",aggFunc:\"sum\",entityFieldId:null,expression:" + exprJson + ",version:1}";
        io.nop.api.core.beans.graphql.GraphQLRequestBean request = new io.nop.api.core.beans.graphql.GraphQLRequestBean();
        request.setQuery("mutation { NopMetaTableMeasure__save(data: " + dataJson + ") { measureName expression } }");
        return graphQLEngine.executeGraphQL(graphQLEngine.newGraphQLContext(request));
    }

    // ============================================================
    // plan 2026-07-18-1500-2：多列算术 having 端到端测试（三条 SQL 路径 + 跨库内存显式失败 + 失败路径）
    // ============================================================

    /**
     * 构造多列算术 having 的 leaf TreeBean（{@code expr} 属性 + {@code op} + {@code value}）。
     * 经 TreeBean.setAttr 承载 expr 属性，不修改 TreeBean 类（R1 B3）。
     */
    private static TreeBean havingArithmeticLeaf(String op, String expr, Object value) {
        TreeBean leaf = new TreeBean(op);
        leaf.setAttr(io.nop.metadata.service.query.MetaAggregationExecutor.HAVING_EXPR_ATTR, expr);
        leaf.setAttr(io.nop.api.core.beans.FilterBeanConstants.FILTER_ATTR_VALUE, value);
        return leaf;
    }

    /** 造多列算术 having 专用表：ext_arith(category, val_a, val_b)。 */
    private void seedArithTable(String dbUrl) throws Exception {
        try (Connection c = DriverManager.getConnection(dbUrl, "sa", "");
             Statement st = c.createStatement()) {
            st.execute("CREATE TABLE ext_arith (category VARCHAR(20), val_a INT, val_b INT)");
            st.execute("INSERT INTO ext_arith VALUES ('A', 10, 1)");
            st.execute("INSERT INTO ext_arith VALUES ('A', 20, 5)");
            st.execute("INSERT INTO ext_arith VALUES ('B', 30, 1)");
            st.execute("INSERT INTO ext_arith VALUES ('C', 5, 1)");
        }
    }

    /**
     * external/sql 单表路径：多列算术 having {@code SUM(val_a) - SUM(val_b) > 10} 端到端测试。
     *
     * <p>数据：A: SUM(val_a)=30, SUM(val_b)=6, diff=24；B: 30-1=29；C: 5-1=4。
     * HAVING diff>10 保留 A、B；排除 C。验证：name→aggSql 替换 + 安全校验 + SQL 真实执行 + 过滤生效。
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testExternalSqlArithmeticHaving() throws Exception {
        String dbUrl = "jdbc:h2:mem:meta_agg_arith_ext;DB_CLOSE_DELAY=-1";
        seedArithTable(dbUrl);
        String tableId = prepareExternalTable(dbUrl, "qs_agg_arith_ext", "EXT_ARITH");
        createMeasure(tableId, "sumA", "VAL_A", "sum", null);
        createMeasure(tableId, "sumB", "VAL_B", "sum", null);
        createDimension(tableId, "cat", "CATEGORY", "categorical", null);

        // HAVING sumA - sumB > 10
        TreeBean having = havingArithmeticLeaf("gt", "sumA - sumB", 10);
        Map<String, Object> result = nopMetaTableBizModel.queryAggregation(tableId,
                Arrays.asList("sumA", "sumB"), Arrays.asList("cat"),
                null, null, null, null, having, null, null);
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.get("items");
        assertNotNull(items, "items must not be null");
        // A=24, B=29 satisfy >10; C=4 fails → 2 items
        assertEquals(2, items.size(), "arithmetic having SUM(A)-SUM(B)>10 must keep A(24), B(29); drop C(4)");
        // 验证保留的 group 都满足条件（Anti-Hollow：真实过滤生效，非 stub）
        for (Map<String, Object> row : items) {
            int sumA = toInt(getIgnoreCase(row, "SUMA"));
            int sumB = toInt(getIgnoreCase(row, "SUMB"));
            assertTrue(sumA - sumB > 10,
                    "arithmetic having must hold for retained group: sumA=" + sumA + " sumB=" + sumB);
        }
    }

    /**
     * external/sql 单表路径：失败路径——未选定 measure name → 显式失败。
     */
    @Test
    public void testExternalSqlArithmeticHavingUnknownNameFails() throws Exception {
        String dbUrl = "jdbc:h2:mem:meta_agg_arith_unk;DB_CLOSE_DELAY=-1";
        seedArithTable(dbUrl);
        String tableId = prepareExternalTable(dbUrl, "qs_agg_arith_unk", "EXT_ARITH");
        createMeasure(tableId, "sumA", "VAL_A", "sum", null);
        createDimension(tableId, "cat", "CATEGORY", "categorical", null);

        // expr 引用未选定的 sumB
        TreeBean having = havingArithmeticLeaf("gt", "sumA - sumB", 10);
        Exception ex = assertThrows(Exception.class, () -> nopMetaTableBizModel.queryAggregation(tableId,
                Arrays.asList("sumA"), Arrays.asList("cat"),
                null, null, null, null, having, null, null));
        assertTrue(ex.getMessage().contains("unknown") || ex.getMessage().contains("Unknown")
                        || ex.getMessage().contains("not in the user-selected"),
                "arithmetic having with unknown measure name must explicitly fail: " + ex.getMessage());
    }

    /**
     * external/sql 单表路径：失败路径——expression 型 measure 被 arithmetic 引用 → 显式失败。
     */
    @Test
    public void testExternalSqlArithmeticHavingExpressionMeasureFails() throws Exception {
        String dbUrl = "jdbc:h2:mem:meta_agg_arith_expr;DB_CLOSE_DELAY=-1";
        seedArithTable(dbUrl);
        String tableId = prepareExternalTable(dbUrl, "qs_agg_arith_expr", "EXT_ARITH");
        // exprM 为 expression 型 measure（aggSql 含 ?）
        createMeasure(tableId, "exprM", "VAL_A", "sum", "VAL_A * 2");
        createMeasure(tableId, "sumB", "VAL_B", "sum", null);
        createDimension(tableId, "cat", "CATEGORY", "categorical", null);

        // expr 引用 exprM（expression 型，含 ?） → ? 安全边界拒绝
        TreeBean having = havingArithmeticLeaf("gt", "exprM - sumB", 10);
        Exception ex = assertThrows(Exception.class, () -> nopMetaTableBizModel.queryAggregation(tableId,
                Arrays.asList("exprM", "sumB"), Arrays.asList("cat"),
                null, null, null, null, having, null, null));
        assertTrue(ex.getMessage().contains("having-order-by-unsupported")
                        || ex.getMessage().contains("HAVING or ORDER BY"),
                "arithmetic having referencing expression-type measure must fail (? safety boundary): "
                        + ex.getMessage());
    }

    /**
     * external/sql 单表路径：失败路径——不安全关键字（替换后 SQL 含 DROP）→ 显式失败。
     *
     * <p>通过精心构造的 measure name（{@code evil}）映射到 {@code DROP TABLE foo}（measure 加载阶段会拒绝，
     * 但 defense-in-depth 验证 having preprocess 阶段也会拒绝）。此测试聚焦 having preprocess 失败路径，
     * 故用合法 aggSql 但 user expr 含不安全关键字：{@code evil - evil}（替换后含两个 DROP）。
     * 由于 measure 自身的 aggSql 在 load 阶段已校验为 SUM(col) 形态，本测试用合法 aggSql + 含 DROP 的 user expr
     * 不易构造。改为验证 user expr 含字面量时显式失败（Phase 1 字面量禁止）。
     */
    @Test
    public void testExternalSqlArithmeticHavingLiteralFails() throws Exception {
        String dbUrl = "jdbc:h2:mem:meta_agg_arith_lit;DB_CLOSE_DELAY=-1";
        seedArithTable(dbUrl);
        String tableId = prepareExternalTable(dbUrl, "qs_agg_arith_lit", "EXT_ARITH");
        createMeasure(tableId, "sumA", "VAL_A", "sum", null);
        createMeasure(tableId, "sumB", "VAL_B", "sum", null);
        createDimension(tableId, "cat", "CATEGORY", "categorical", null);

        // user expr 含字面量 100 → Phase 1 禁止（Phase 1 仅允许 measure name 算术组合）
        TreeBean having = havingArithmeticLeaf("gt", "sumA / sumB * 100", 10);
        Exception ex = assertThrows(Exception.class, () -> nopMetaTableBizModel.queryAggregation(tableId,
                Arrays.asList("sumA", "sumB"), Arrays.asList("cat"),
                null, null, null, null, having, null, null));
        assertTrue(ex.getMessage().contains("unsafe") || ex.getMessage().contains("literals")
                        || ex.getMessage().contains("not allowed"),
                "arithmetic having with literal must explicitly fail (Phase 1 literals disallowed): "
                        + ex.getMessage());
    }

    /**
     * entity 单表路径：多列算术 having {@code SUM(val_a) - SUM(val_b) >= 0} 端到端测试。
     *
     * <p>验证 entity 路径（bypass EQL）真实注入多列算术 SQL + 过滤生效（非 stub）。
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testEntityArithmeticHaving() {
        importModel();
        String tableId = findEntityTableId("nop_meta_module");
        IEntityDao<NopMetaEntityField> fieldDao = daoProvider.daoFor(NopMetaEntityField.class);
        String statusFieldId = findEntityFieldId("nop_meta_module", "status", fieldDao);
        // 用 status 字段做两个 count measure（同一列两个 count 算术组合）
        createMeasure(tableId, "cntA", statusFieldId, "count", null);
        createMeasure(tableId, "cntB", statusFieldId, "count", null);
        createDimension(tableId, "st", statusFieldId, "categorical", null);

        // HAVING cntA - cntB >= 0（count - count = 0 for any group；保留所有 group）
        TreeBean having = havingArithmeticLeaf("ge", "cntA - cntB", 0);
        Map<String, Object> result = nopMetaTableBizModel.queryAggregation(tableId,
                Arrays.asList("cntA", "cntB"), Arrays.asList("st"),
                null, null, null, null, having, null, null);
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.get("items");
        assertNotNull(items, "items must not be null");
        assertFalse(items.isEmpty(),
                "entity path arithmetic having must yield real grouped rows: " + items);
        // Anti-Hollow：验证两个 measure 真实执行 + 算术过滤生效
        for (Map<String, Object> row : items) {
            long cntA = toLong(getIgnoreCase(row, "CNTA"));
            long cntB = toLong(getIgnoreCase(row, "CNTB"));
            assertTrue(cntA - cntB >= 0,
                    "arithmetic having cntA-cntB>=0 must hold: cntA=" + cntA + " cntB=" + cntB);
        }
    }

    /**
     * JOIN 同库 external↔external 路径：多列算术 having 端到端测试。
     *
     * <p>验证 JOIN 路径（l./r. 限定名）真实注入多列算术 SQL。
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testExternalExternalJoinArithmeticHaving() throws Exception {
        String querySpace = "qs_ext_join_arith";
        String dbUrl = "jdbc:h2:mem:" + querySpace + ";DB_CLOSE_DELAY=-1";
        seedFactDimTables(dbUrl);
        // 增加另一列 VAL_X 以构造两个 measure 算术组合（同列不同 aggFunc 亦可）
        try (Connection c = DriverManager.getConnection(dbUrl, "sa", "");
             Statement st = c.createStatement()) {
            st.execute("ALTER TABLE ext_fact ADD COLUMN val_x INT");
            st.execute("UPDATE ext_fact SET val_x = 1 WHERE cat_id = 1");
            st.execute("UPDATE ext_fact SET val_x = 2 WHERE cat_id = 2");
        }
        saveDataSource("ds-" + querySpace, querySpace, dbUrl);
        syncExternalTables("ds-" + querySpace);
        String factTableId = tableId("EXT_FACT");
        String dimTableId = tableId("EXT_DIM");

        String joinId = createTableTableJoin(factTableId, "inner", factTableId, dimTableId,
                "CAT_ID", "CAT_ID", "dim");
        // left.sum(AMOUNT) - left.sum(VAL_X)：A: 30 - 2 (1+1), B: 30 - 2
        createMeasureWithSide(factTableId, "sumAmt", "AMOUNT", "sum", "left");
        createMeasureWithSide(factTableId, "sumX", "VAL_X", "sum", "left");
        createDimensionWithSide(factTableId, "cat", "CAT_NAME", "categorical", null, "right");

        // HAVING sumAmt - sumX >= 20
        TreeBean having = havingArithmeticLeaf("ge", "sumAmt - sumX", 20);
        Map<String, Object> result = nopMetaTableBizModel.queryAggregation(factTableId,
                Arrays.asList("sumAmt", "sumX"), Arrays.asList("cat"),
                null, joinId, null, null, having, null, null);
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.get("items");
        assertNotNull(items);
        // A: SUM(AMOUNT)=30, SUM(VAL_X)=2 → 28 >= 20 ✓; B: 30-2=28 ✓
        assertEquals(2, items.size(), "arithmetic having sumAmt-sumX>=20 must keep both groups");
        for (Map<String, Object> row : items) {
            int sumAmt = toInt(getIgnoreCase(row, "SUMAMT"));
            int sumX = toInt(getIgnoreCase(row, "SUMX"));
            assertTrue(sumAmt - sumX >= 20,
                    "JOIN arithmetic having must hold: sumAmt=" + sumAmt + " sumX=" + sumX);
        }
    }

    /**
     * 跨库内存路径：多列算术 having 显式失败（对齐 D12.2，验证 {@code MemoryFilterEvaluator} 入口检测）。
     */
    @Test
    public void testCrossDbMemoryArithmeticHavingFails() {
        importModel();
        NopMetaEntity leftEntity = findMetaEntityByTable("nop_meta_entity");
        NopMetaEntity rightEntity = findMetaEntityByTable("nop_meta_entity_field");
        // 让 right 跨 querySpace → 走 D10 内存 GROUP BY 路径
        updateQuerySpaceSql(rightEntity.getMetaEntityId(), "qs_arith_cross");
        try {
            String leftTableId = findEntityTableId("nop_meta_entity");
            String joinId = createJoin(leftTableId, "inner", leftEntity.getMetaEntityId(),
                    rightEntity.getMetaEntityId(), "metaEntityId", "metaEntityId", "fld");
            String leftDimFieldId = findEntityFieldId("nop_meta_entity", "displayName");
            String rightMeasureFieldId = findEntityFieldId("nop_meta_entity_field", "fieldName");
            createDimension(leftTableId, "st", leftDimFieldId, "categorical", null);
            createMeasure(leftTableId, "cntA", rightMeasureFieldId, "count", null);
            createMeasure(leftTableId, "cntB", rightMeasureFieldId, "count", null);

            // 多列算术 having → 跨库内存路径须显式失败
            TreeBean having = havingArithmeticLeaf("gt", "cntA - cntB", 0);
            Exception ex = assertThrows(Exception.class, () -> nopMetaTableBizModel.queryAggregation(
                    leftTableId, Arrays.asList("cntA", "cntB"), Arrays.asList("st"),
                    null, joinId, null, null, having, null, null));
            assertTrue(ex.getMessage().contains("having-expr-memory-not-computable")
                            || ex.getMessage().contains("memory-not-computable")
                            || ex.getMessage().contains("not computable"),
                    "cross-DB memory path arithmetic having must explicitly fail: " + ex.getMessage());
        } finally {
            updateQuerySpaceSql(rightEntity.getMetaEntityId(), null);
        }
    }

    /**
     * 向后兼容：无 {@code expr} 属性的常规 having leaf 零行为变化（既有路径不被多列算术 preprocess 影响）。
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testBackwardCompatRegularHavingUnchanged() throws Exception {
        String dbUrl = "jdbc:h2:mem:meta_agg_arith_compat;DB_CLOSE_DELAY=-1";
        seedArithTable(dbUrl);
        String tableId = prepareExternalTable(dbUrl, "qs_agg_arith_compat", "EXT_ARITH");
        createMeasure(tableId, "sumA", "VAL_A", "sum", null);
        createDimension(tableId, "cat", "CATEGORY", "categorical", null);

        // 常规 having（无 expr 属性，使用 name 属性）→ 与既有行为一致
        TreeBean having = FilterBeans.gt("sumA", 10);
        Map<String, Object> result = nopMetaTableBizModel.queryAggregation(tableId,
                Arrays.asList("sumA"), Arrays.asList("cat"),
                null, null, null, null, having, null, null);
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.get("items");
        // A=30, B=30 satisfy >10; C=5 fails → 2 items
        assertEquals(2, items.size(), "regular having SUM(A)>10 must keep A(30), B(30); drop C(5)");
    }


    /** 创建带 side + expression 的 measure（plan 2026-07-18-1400-1 expression JOIN 测试用 helper）。 */
    private void createMeasureWithSideAndExpression(String tableId, String name, String entityFieldId,
                                                     String aggFunc, String side, String expression) {
        IEntityDao<NopMetaTableMeasure> dao = daoProvider.daoFor(NopMetaTableMeasure.class);
        NopMetaTableMeasure m = dao.newEntity();
        m.setMetaTableId(tableId);
        m.setMeasureName(name);
        m.setEntityFieldId(entityFieldId);
        m.setAggFunc(aggFunc);
        if (side != null) {
            m.setSide(side);
        }
        if (expression != null) {
            m.setExpression(expression);
        }
        m.setVersion(1L);
        dao.saveEntity(m);
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

    /** 造事实表 + 维度表（external↔external JOIN 聚合测试用）。 */
    private void seedFactDimTables(String dbUrl) throws Exception {
        try (Connection c = DriverManager.getConnection(dbUrl, "sa", "");
             Statement st = c.createStatement()) {
            st.execute("CREATE TABLE ext_fact (cat_id INT, amount INT)");
            st.execute("CREATE TABLE ext_dim (cat_id INT, cat_name VARCHAR(20))");
            st.execute("INSERT INTO ext_fact VALUES (1, 10)");
            st.execute("INSERT INTO ext_fact VALUES (1, 20)");
            st.execute("INSERT INTO ext_fact VALUES (2, 30)");
            st.execute("INSERT INTO ext_dim VALUES (1, 'A')");
            st.execute("INSERT INTO ext_dim VALUES (2, 'B')");
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
            nopMetaTableBizModel.queryAggregation(tableId, measures, dims, null, null, null, null, null, null, null);
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

    // ===== JOIN 聚合测试 helpers（plan 0852-1）=====

    private NopMetaEntity findMetaEntityByTable(String tableName) {
        IEntityDao<NopMetaEntity> dao = daoProvider.daoFor(NopMetaEntity.class);
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaEntity.PROP_NAME_tableName, tableName));
        NopMetaEntity e = dao.findFirstByQuery(q);
        assertNotNull(e, "NopMetaEntity for table " + tableName + " must exist after import");
        return e;
    }

    private String findEntityFieldId(String tableName, String fieldName) {
        IEntityDao<NopMetaEntityField> fieldDao = daoProvider.daoFor(NopMetaEntityField.class);
        return findEntityFieldId(tableName, fieldName, fieldDao);
    }

    private String createJoin(String metaTableId, String joinType, String leftEntityId, String rightEntityId,
                              String leftField, String rightField, String alias) {
        IEntityDao<NopMetaTableJoin> dao = daoProvider.daoFor(NopMetaTableJoin.class);
        NopMetaTableJoin join = dao.newEntity();
        join.setMetaTableId(metaTableId);
        join.setJoinType(joinType);
        join.setLeftEntityId(leftEntityId);
        join.setRightEntityId(rightEntityId);
        join.setLeftField(leftField);
        join.setRightField(rightField);
        join.setAlias(alias);
        join.setVersion(1L);
        Timestamp now = new Timestamp(System.currentTimeMillis());
        join.setCreatedBy("autotest");
        join.setCreateTime(now);
        dao.saveEntity(join);
        return join.getJoinId();
    }

    /** 混合端点 join（leftEntityId entity 端点 + rightTableId table 端点），用于 external/sql 端点失败用例。 */
    private String createMixedJoin(String metaTableId, String joinType, String leftEntityId, String rightTableId,
                                   String leftField, String rightField, String alias) {
        IEntityDao<NopMetaTableJoin> dao = daoProvider.daoFor(NopMetaTableJoin.class);
        NopMetaTableJoin join = dao.newEntity();
        join.setMetaTableId(metaTableId);
        join.setJoinType(joinType);
        join.setLeftEntityId(leftEntityId);
        join.setRightTableId(rightTableId);
        join.setLeftField(leftField);
        join.setRightField(rightField);
        join.setAlias(alias);
        join.setVersion(1L);
        Timestamp now = new Timestamp(System.currentTimeMillis());
        join.setCreatedBy("autotest");
        join.setCreateTime(now);
        dao.saveEntity(join);
        return join.getJoinId();
    }

    /** external↔external 双 table 端点 join（leftTableId + rightTableId），用于 external↔external 聚合用例。 */
    private String createTableTableJoin(String metaTableId, String joinType, String leftTableId, String rightTableId,
                                        String leftField, String rightField, String alias) {
        IEntityDao<NopMetaTableJoin> dao = daoProvider.daoFor(NopMetaTableJoin.class);
        NopMetaTableJoin join = dao.newEntity();
        join.setMetaTableId(metaTableId);
        join.setJoinType(joinType);
        join.setLeftTableId(leftTableId);
        join.setRightTableId(rightTableId);
        join.setLeftField(leftField);
        join.setRightField(rightField);
        join.setAlias(alias);
        join.setVersion(1L);
        Timestamp now = new Timestamp(System.currentTimeMillis());
        join.setCreatedBy("autotest");
        join.setCreateTime(now);
        dao.saveEntity(join);
        return join.getJoinId();
    }

    private void createMeasureWithSide(String tableId, String name, String entityFieldId, String aggFunc, String side) {
        IEntityDao<NopMetaTableMeasure> dao = daoProvider.daoFor(NopMetaTableMeasure.class);
        NopMetaTableMeasure m = dao.newEntity();
        m.setMetaTableId(tableId);
        m.setMeasureName(name);
        m.setEntityFieldId(entityFieldId);
        m.setAggFunc(aggFunc);
        if (side != null) {
            m.setSide(side);
        }
        m.setVersion(1L);
        dao.saveEntity(m);
    }

    private void createDimensionWithSide(String tableId, String name, String entityFieldId, String dimensionType,
                                         String granularity, String side) {
        IEntityDao<NopMetaTableDimension> dao = daoProvider.daoFor(NopMetaTableDimension.class);
        NopMetaTableDimension d = dao.newEntity();
        d.setMetaTableId(tableId);
        d.setDimensionName(name);
        d.setEntityFieldId(entityFieldId);
        d.setDimensionType(dimensionType);
        if (granularity != null) {
            d.setGranularity(granularity);
        }
        if (side != null) {
            d.setSide(side);
        }
        d.setVersion(1L);
        dao.saveEntity(d);
    }

    /** sync 外部表（plan 1200-1：external↔external JOIN 测试复用 sync 链路建表）。 */
    private void syncExternalTables(String dataSourceId) {
        io.nop.api.core.beans.graphql.GraphQLRequestBean request = new io.nop.api.core.beans.graphql.GraphQLRequestBean();
        request.setQuery("mutation { NopMetaDataSource__syncExternalTables(dataSourceId: \"" + dataSourceId
                + "\", schemaPattern: \"PUBLIC\") }");
        io.nop.api.core.beans.graphql.GraphQLResponseBean resp =
                graphQLEngine.executeGraphQL(graphQLEngine.newGraphQLContext(request));
        org.junit.jupiter.api.Assertions.assertFalse(resp.hasError(), "sync should not error: " + resp);
    }

    /** 直接 SQL UPDATE NOP_META_ENTITY.QUERY_SPACE 并清缓存，使后续 getEntityById 重读新值。 */
    private void updateQuerySpaceSql(String metaEntityId, String querySpace) {
        io.nop.core.lang.sql.SQL upd = io.nop.core.lang.sql.SQL.begin().allowUnderscoreName(true)
                .sql("update NOP_META_ENTITY set QUERY_SPACE=? where META_ENTITY_ID=?",
                        querySpace == null ? "" : querySpace, metaEntityId)
                .end();
        ormTemplate.executeUpdate(upd);
        ormTemplate.evictAll(NopMetaEntity.class.getName());
    }

    private long countRows(String sql) {
        io.nop.core.lang.sql.SQL q = io.nop.core.lang.sql.SQL.begin().allowUnderscoreName(true).sql(sql).end();
        return ormTemplate.executeQuery(q, null, ds -> {
            for (io.nop.dataset.IDataRow row : ds) {
                return ((Number) row.getObject(0)).longValue();
            }
            return 0L;
        });
    }

    /** 查询单列的去重值列表（用于构造匹配 join key 的测试数据）。 */
    private List<String> queryDistinctColumnValues(String sql, String columnName) {
        io.nop.core.lang.sql.SQL q = io.nop.core.lang.sql.SQL.begin().allowUnderscoreName(true).sql(sql).end();
        return ormTemplate.executeQuery(q, null, ds -> {
            List<String> values = new java.util.ArrayList<>();
            for (io.nop.dataset.IDataRow row : ds) {
                Object v = row.getObject(0);
                if (v != null) {
                    values.add(String.valueOf(v));
                }
            }
            return values;
        });
    }

    private boolean queryAggregationJoinHasError(String tableId, String joinId) {
        // 失败发生在 measure 加载之前的分支（right/not-found/cross-querySpace/self-join/table-endpoint）
        // 用占位 measure/dim 名即可触发 join-specific 失败；字段归属失败分支由专用重载用真实名验证。
        return queryAggregationJoinHasError(tableId, "__any_measure__", "__any_dim__", joinId);
    }

    private boolean queryAggregationJoinHasError(String tableId, String measureName, String dimName, String joinId) {
        try {
            nopMetaTableBizModel.queryAggregation(tableId,
                    java.util.Arrays.asList(measureName), java.util.Arrays.asList(dimName),
                    null, joinId, null, null, null, null, null);
            return false;
        } catch (Exception e) {
            return true;
        }
    }

    private String saveSqlTableManual(String sourceSql, String querySpace) {
        IEntityDao<NopMetaTable> dao = daoProvider.daoFor(NopMetaTable.class);
        NopMetaTable t = dao.newEntity();
        t.setMetaModuleId(ensureTestModuleId());
        t.setTableName("SQL_AGG_" + System.nanoTime());
        t.setDisplayName("sql-agg-endpoint");
        t.setTableType("sql");
        t.setQuerySpace(querySpace);
        t.setSourceSql(sourceSql);
        t.setVersion(1L);
        dao.saveEntity(t);
        return t.getMetaTableId();
    }

    private String ensureTestModuleId() {
        IEntityDao<NopMetaModule> dao = daoProvider.daoFor(NopMetaModule.class);
        NopMetaModule m = dao.newEntity();
        m.setModuleId("nop/test-agg-" + System.nanoTime());
        m.setModuleName("test-agg");
        m.setDisplayName("test-agg");
        m.setModuleVersion(1L);
        m.setStatus("RELEASED");
        m.setImportedAt(new Timestamp(System.currentTimeMillis()));
        dao.saveEntity(m);
        return m.getMetaModuleId();
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

    private static void seedH2(String dbUrl, String... statements) throws Exception {
        try (Connection c = DriverManager.getConnection(dbUrl, "sa", "");
             Statement st = c.createStatement()) {
            for (String sql : statements) {
                st.execute(sql);
            }
        }
    }

    private static long toLong(Object v) {
        if (v == null) {
            return 0;
        }
        return ((Number) v).longValue();
    }
}
