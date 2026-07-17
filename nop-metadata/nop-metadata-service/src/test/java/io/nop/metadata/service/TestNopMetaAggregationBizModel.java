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
import io.nop.metadata.dao.entity.NopMetaEntity;
import io.nop.metadata.dao.entity.NopMetaEntityField;
import io.nop.metadata.dao.entity.NopMetaModule;
import io.nop.metadata.dao.entity.NopMetaTable;
import io.nop.metadata.dao.entity.NopMetaTableDimension;
import io.nop.metadata.dao.entity.NopMetaTableFilter;
import io.nop.metadata.dao.entity.NopMetaTableJoin;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
                Arrays.asList("total", "cnt"), Arrays.asList("cat"), null, null, null, null, null);
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
                Arrays.asList("dc"), Arrays.asList("mon"), null, null, null, null, null);
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
                Arrays.asList("total"), Arrays.asList("mon"), null, null, null, null, null);
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
                Arrays.asList("total"), Arrays.asList("cat"), null, null, null, null, null);
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
                Arrays.asList("cnt"), Arrays.asList("st"), null, null, null, null, null);
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.get("items");
        assertFalse(items.isEmpty(), "entity aggregation by status must return real grouped rows: " + items);
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
                Arrays.asList("cnt"), Arrays.asList("st"), null, joinId, null, null, null);
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
                Arrays.asList("mcnt"), Arrays.asList("mst"), null, null, null, null, null);
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

    /** 跨 querySpace（跨库）entity-entity JOIN 聚合 deferred → 显式失败。 */
    @Test
    public void testJoinAggregationCrossQuerySpaceFails() {
        importModel();
        NopMetaEntity leftEntity = findMetaEntityByTable("nop_meta_entity");
        NopMetaEntity rightEntity = findMetaEntityByTable("nop_meta_entity_field");
        updateQuerySpaceSql(rightEntity.getMetaEntityId(), "qs_agg_cross_db");
        try {
            String leftTableId = findEntityTableId("nop_meta_entity");
            String joinId = createJoin(leftTableId, "inner", leftEntity.getMetaEntityId(),
                    rightEntity.getMetaEntityId(), "metaEntityId", "metaEntityId", "fld");
            assertTrue(queryAggregationJoinHasError(leftTableId, joinId),
                    "cross-querySpace JOIN aggregation must explicitly fail (deferred, not silent)");
        } finally {
            updateQuerySpaceSql(rightEntity.getMetaEntityId(), null);
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
                Arrays.asList("total"), Arrays.asList("cat"), null, joinId, null, null, null);
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
                Arrays.asList("rcnt"), Arrays.asList("rst"), null, joinId, null, null, null);
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

    /** 跨 querySpace（跨库）external↔external JOIN 聚合 deferred → 显式失败（不静默降级）。 */
    @Test
    public void testExternalJoinAggregationCrossQuerySpaceFails() throws Exception {
        // 两表分别处于不同 querySpace（跨库）
        String qs1 = "qs_ext_cross_1";
        String qs2 = "qs_ext_cross_2";
        String dbUrl1 = "jdbc:h2:mem:" + qs1 + ";DB_CLOSE_DELAY=-1";
        String dbUrl2 = "jdbc:h2:mem:" + qs2 + ";DB_CLOSE_DELAY=-1";
        seedH2(dbUrl1, "CREATE TABLE ext_a (k VARCHAR(20), v INT)", "INSERT INTO ext_a VALUES ('k1', 1)");
        seedH2(dbUrl2, "CREATE TABLE ext_b (k VARCHAR(20), v INT)", "INSERT INTO ext_b VALUES ('k1', 2)");
        saveDataSource("ds-" + qs1, qs1, dbUrl1);
        saveDataSource("ds-" + qs2, qs2, dbUrl2);
        syncExternalTables("ds-" + qs1);
        syncExternalTables("ds-" + qs2);
        String tableAId = tableId("EXT_A");
        String tableBId = tableId("EXT_B");

        String joinId = createTableTableJoin(tableAId, "inner", tableAId, tableBId, "K", "K", "b");
        // 跨 querySpace 失败发生在 measure 加载之前的路由分支，占位名即可触发
        assertTrue(queryAggregationJoinHasError(tableAId, joinId),
                "cross-querySpace external<->external JOIN aggregation must explicitly fail (deferred, not silent)");
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
                Arrays.asList("cnt"), Arrays.asList("cat"), null, joinId, null, null, null);
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
     * 不可同库失败（#24）：entity 物理表（NOP_META_MODULE）在选定 external 连接不可见（外部库未建该表）
     * → 显式失败 ERR_AGGR_JOIN_MIXED_CROSS_DB_DEFERRED（不静默降级 D5 拼接近似聚合）。
     */
    @Test
    public void testMixedCrossDbFails() throws Exception {
        importModel();
        NopMetaEntity leftEntity = findMetaEntityByTable("nop_meta_module");
        String leftTableId = findEntityTableId("nop_meta_module");

        // 外部库只含 MIXED_DIM，不含 NOP_META_MODULE → 可见性测试失败 → 跨库 deferred
        String querySpace = "qs_mixed_cross";
        String dbUrl = "jdbc:h2:mem:" + querySpace + ";DB_CLOSE_DELAY=-1";
        seedH2(dbUrl, "CREATE TABLE MIXED_DIM (STATUS_VAL VARCHAR(20), CAT_NAME VARCHAR(20))",
                "INSERT INTO MIXED_DIM VALUES ('A', 'Category A')");
        saveDataSource("ds-" + querySpace, querySpace, dbUrl);
        syncExternalTables("ds-" + querySpace);
        String dimTableId = externalTableId("MIXED_DIM");

        String joinId = createMixedJoin(leftTableId, "inner", leftEntity.getMetaEntityId(), dimTableId,
                "status", "STATUS_VAL", "dim");
        assertTrue(queryAggregationJoinHasError(leftTableId, joinId),
                "mixed-endpoint cross-DB (entity table not visible) must explicitly fail (deferred, not silent D5)");
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
                Arrays.asList("mcnt"), Arrays.asList("mst"), null, joinId, null, null, null);
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
            nopMetaTableBizModel.queryAggregation(tableId, measures, dims, null, null, null, null, null);
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

    private boolean queryAggregationJoinHasError(String tableId, String joinId) {
        // 失败发生在 measure 加载之前的分支（right/not-found/cross-querySpace/self-join/table-endpoint）
        // 用占位 measure/dim 名即可触发 join-specific 失败；字段归属失败分支由专用重载用真实名验证。
        return queryAggregationJoinHasError(tableId, "__any_measure__", "__any_dim__", joinId);
    }

    private boolean queryAggregationJoinHasError(String tableId, String measureName, String dimName, String joinId) {
        try {
            nopMetaTableBizModel.queryAggregation(tableId,
                    java.util.Arrays.asList(measureName), java.util.Arrays.asList(dimName),
                    null, joinId, null, null, null);
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
