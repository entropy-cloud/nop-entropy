package io.nop.metadata.service;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.TreeBean;
import io.nop.api.core.beans.query.OrderFieldBean;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.graphql.core.ast.GraphQLOperationType;
import io.nop.graphql.core.engine.IGraphQLEngine;
import io.nop.metadata.core.dto.AggregationResultDTO;
import io.nop.metadata.dao.entity.NopMetaEntityField;
import io.nop.metadata.dao.entity.NopMetaTableMeasure;
import io.nop.orm.IOrmTemplate;
import io.nop.metadata.service.NopMetadataException;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static io.nop.metadata.service.TestAggregationHelper.*;
import static org.junit.jupiter.api.Assertions.*;

@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestAggregationCategoricalAndTemporal extends JunitBaseTestCase {

    public TestAggregationCategoricalAndTemporal() {
        setTestConfig("nop.orm.init-database-schema", true);
    }

    @Inject
    IGraphQLEngine graphQLEngine;
    @Inject
    IDaoProvider daoProvider;
    @Inject
    IOrmTemplate ormTemplate;

    TestAggregationHelper _helper;

    @BeforeEach
    void initHelper() {
        _helper = new TestAggregationHelper(graphQLEngine, daoProvider, ormTemplate);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> queryAggregationItems(String tableId, List<String> measures, List<String> dims,
                                                   TreeBean filter, String joinId, Long limit, Long offset,
                                                   TreeBean having, List<OrderFieldBean> orderBy) {
        ApiResponse<?> resp = _helper.executeRpc(GraphQLOperationType.query, "NopMetaTable__queryAggregation",
                _helper.queryAggregationRequest(tableId, measures, dims, filter, joinId, limit, offset, having, orderBy));
        if (!resp.isOk()) {
            throw new NopMetadataException(NopMetadataErrors.ERR_AGGR_EXEC_FAILED).param("response", String.valueOf(resp));
        }
        Map<String, Object> data = (Map<String, Object>) resp.getData();
        return (List<Map<String, Object>>) data.get("items");
    }

    @SuppressWarnings("unchecked")
    private ApiResponse<?> queryAggregationRaw(String tableId, List<String> measures, List<String> dims,
                                                TreeBean filter, String joinId, Long limit, Long offset,
                                                TreeBean having, List<OrderFieldBean> orderBy) {
        return _helper.executeRpc(GraphQLOperationType.query, "NopMetaTable__queryAggregation",
                _helper.queryAggregationRequest(tableId, measures, dims, filter, joinId, limit, offset, having, orderBy));
    }

    /** categorical 维度 + sum/count 聚合：GROUP BY CATEGORY → A=sum 30/count 2，B=sum 30/count 1。 */
    @Test
    @SuppressWarnings("unchecked")
    public void testCategoricalAggregation() throws Exception {
        String dbUrl = "jdbc:h2:mem:meta_agg_cat;DB_CLOSE_DELAY=-1";
        _helper.seedAggTable(dbUrl);
        String tableId = _helper.prepareExternalTable(dbUrl, "qs_agg_cat", "EXT_AGG");
        _helper.createMeasure(tableId, "total", "AMOUNT", "sum", null);
        _helper.createMeasure(tableId, "cnt", "AMOUNT", "count", null);
        _helper.createDimension(tableId, "cat", "CATEGORY", "categorical", null);

        List<Map<String, Object>> items = queryAggregationItems(tableId,
                Arrays.asList("total", "cnt"), Arrays.asList("cat"), null, null, null, null, null, null);
        
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
        _helper.seedAggTable(dbUrl);
        String tableId = _helper.prepareExternalTable(dbUrl, "qs_agg_cd", "EXT_AGG");
        _helper.createMeasure(tableId, "dc", "CATEGORY", "countDistinct", null);
        _helper.createDimension(tableId, "mon", "CREATED_AT", "temporal", "month");

        List<Map<String, Object>> items = queryAggregationItems(tableId,
                Arrays.asList("dc"), Arrays.asList("mon"), null, null, null, null, null, null);
        
        assertFalse(items.isEmpty(), "temporal month grouping must yield rows");
        for (Map<String, Object> row : items) {
            assertEquals(1, toInt(getIgnoreCase(row, "DC")), "countDistinct(CATEGORY) per month = 1: " + row);
        }
    }

    /** 时间维度 granularity=month 分桶（D7，H2 DATE_TRUNC）：按月分组 sum。 */
    @Test
    @SuppressWarnings("unchecked")
    public void testTemporalGranularityBucketing() throws Exception {
        String dbUrl = "jdbc:h2:mem:meta_agg_tm;DB_CLOSE_DELAY=-1";
        _helper.seedAggTable(dbUrl);
        String tableId = _helper.prepareExternalTable(dbUrl, "qs_agg_tm", "EXT_AGG");
        _helper.createMeasure(tableId, "total", "AMOUNT", "sum", null);
        _helper.createDimension(tableId, "mon", "CREATED_AT", "temporal", "month");

        List<Map<String, Object>> items = queryAggregationItems(tableId,
                Arrays.asList("total"), Arrays.asList("mon"), null, null, null, null, null, null);
        
        assertEquals(2, items.size(), "group by month must yield 2 months (2024-01, 2024-02)");
        for (Map<String, Object> row : items) {
            assertEquals(30, toInt(getIgnoreCase(row, "TOTAL")), "monthly SUM(AMOUNT) = 30: " + row);
        }
    }

    /** 默认过滤器自动应用：isDefault filter AMOUNT > 15 → 只聚合 amount>15 的行。 */
    @Test
    @SuppressWarnings("unchecked")
    public void testDefaultFilterApplied() throws Exception {
        String dbUrl = "jdbc:h2:mem:meta_agg_df;DB_CLOSE_DELAY=-1";
        _helper.seedAggTable(dbUrl);
        String tableId = _helper.prepareExternalTable(dbUrl, "qs_agg_df", "EXT_AGG");
        _helper.createMeasure(tableId, "total", "AMOUNT", "sum", null);
        _helper.createDimension(tableId, "cat", "CATEGORY", "categorical", null);
        TreeBean defaultFilter = FilterBeans.gt("AMOUNT", 15);
        _helper.createDefaultFilter(tableId, "df", defaultFilter);

        List<Map<String, Object>> items = queryAggregationItems(tableId,
                Arrays.asList("total"), Arrays.asList("cat"), null, null, null, null, null, null);
        
        Map<String, Object> rowA = findRow(items, "CAT", "A");
        assertEquals(20, toInt(rowA.get("TOTAL")), "default filter AMOUNT>15: SUM(A) = 20 (10 excluded)");
    }

    /** expression 型 Measure 真实执行：AMOUNT * 2，aggFunc = sum。 */
    @Test
    @SuppressWarnings("unchecked")
    public void testExpressionMeasureExplicitlyFails() throws Exception {
        String dbUrl = "jdbc:h2:mem:meta_agg_exp;DB_CLOSE_DELAY=-1";
        _helper.seedAggTable(dbUrl);
        String tableId = _helper.prepareExternalTable(dbUrl, "qs_agg_exp", "EXT_AGG");
        _helper.createMeasure(tableId, "exprM", "AMOUNT", "sum", "AMOUNT * 2");
        _helper.createDimension(tableId, "cat", "CATEGORY", "categorical", null);

        List<Map<String, Object>> items = queryAggregationItems(tableId,
                Arrays.asList("exprM"), Arrays.asList("cat"), null, null, null, null, null, null);
        
        assertNotNull(items);
        assertEquals(2, items.size(), "expression measure must execute and yield 2 groups (A, B): " + items);
        Map<String, Object> rowA = findRow(items, "CAT", "A");
        Map<String, Object> rowB = findRow(items, "CAT", "B");
        assertNotNull(rowA, "group A must exist: " + items);
        assertNotNull(rowB, "group B must exist: " + items);
        assertEquals(60, toInt(rowA.get("EXPRM")), "SUM(AMOUNT*2) for A = 60 (10*2 + 20*2): " + rowA);
        assertEquals(60, toInt(rowB.get("EXPRM")), "SUM(AMOUNT*2) for B = 60 (30*2): " + rowB);
    }

    /** 不约定的 granularity 显式失败。 */
    @Test
    public void testUnsupportedGranularityFails() throws Exception {
        String dbUrl = "jdbc:h2:mem:meta_agg_g;DB_CLOSE_DELAY=-1";
        _helper.seedAggTable(dbUrl);
        String tableId = _helper.prepareExternalTable(dbUrl, "qs_agg_g", "EXT_AGG");
        _helper.createMeasure(tableId, "total", "AMOUNT", "sum", null);
        _helper.createDimension(tableId, "weird", "CREATED_AT", "temporal", "fortnight");

        assertTrue(_helper.queryAggregationHasError(tableId, Arrays.asList("total"), Arrays.asList("weird")),
                "unsupported granularity must explicitly fail");
    }

    /** external-sql 路径 expression 端到端测试。 */
    @Test
    @SuppressWarnings("unchecked")
    public void testExternalPathExpressionMeasureExecution() throws Exception {
        String dbUrl = "jdbc:h2:mem:meta_agg_ext_expr;DB_CLOSE_DELAY=-1";
        _helper.seedAggTable(dbUrl);
        String tableId = _helper.prepareExternalTable(dbUrl, "qs_agg_ext_expr", "EXT_AGG");
        _helper.createMeasure(tableId, "triple", "AMOUNT", "sum", "AMOUNT * 2 + AMOUNT");
        _helper.createDimension(tableId, "cat", "CATEGORY", "categorical", null);

        List<Map<String, Object>> items = queryAggregationItems(tableId,
                Arrays.asList("triple"), Arrays.asList("cat"), null, null, null, null, null, null);
        
        assertNotNull(items);
        Map<String, Object> rowA = findRow(items, "CAT", "A");
        Map<String, Object> rowB = findRow(items, "CAT", "B");
        assertEquals(90, toInt(rowA.get("TRIPLE")), "SUM(AMOUNT*3) for A = 90: " + rowA);
        assertEquals(90, toInt(rowB.get("TRIPLE")), "SUM(AMOUNT*3) for B = 90: " + rowB);
    }

    /** entity 路径 expression 端到端测试。 */
    @Test
    @SuppressWarnings("unchecked")
    public void testEntityPathExpressionMeasureExecution() {
        _helper.importModel();
        String tableId = _helper.findEntityTableId("nop_meta_module");
        String statusFieldId = _helper.findEntityFieldId("nop_meta_module", "status");
        _helper.createMeasure(tableId, "exprStatus", statusFieldId, "sum", "VERSION + VERSION");
        _helper.createDimension(tableId, "st", statusFieldId, "categorical", null);

        List<Map<String, Object>> items = queryAggregationItems(tableId,
                Arrays.asList("exprStatus"), Arrays.asList("st"), null, null, null, null, null, null);
        
        assertNotNull(items, "items must not be null");
        assertFalse(items.isEmpty(), "entity path expression must yield real grouped rows: " + items);
        for (Map<String, Object> row : items) {
            Object v = getIgnoreCase(row, "EXPRSTATUS");
            assertNotNull(v, "expression result column must be present: " + row.keySet());
            assertTrue(toLong(v) >= 0, "expression SUM(col+col) must be non-negative: " + row);
        }
    }

    /** entity 路径 expression + temporal granularity 共存场景。 */
    @Test
    @SuppressWarnings("unchecked")
    public void testEntityPathExpressionWithTemporalGranularity() {
        _helper.importModel();
        _helper.spreadEntityCreateTimeAcrossTwoMonths();
        try {
            String tableId = _helper.findEntityTableId("nop_meta_entity");
            String createTimeFieldId = _helper.findEntityFieldId("nop_meta_entity", "createTime");
            _helper.createMeasure(tableId, "exprCnt", createTimeFieldId, "sum", "VERSION + VERSION");
            _helper.createDimension(tableId, "mon", createTimeFieldId, "temporal", "month");

            List<Map<String, Object>> items = queryAggregationItems(tableId,
                    Arrays.asList("exprCnt"), Arrays.asList("mon"), null, null, null, null, null, null);
            
            assertNotNull(items);
            assertEquals(2, items.size(),
                    "entity path expression + temporal month granularity must yield 2 buckets: " + items);
            for (Map<String, Object> row : items) {
                Object v = getIgnoreCase(row, "EXPRCNT");
                assertNotNull(v, "expression result column must be present: " + row.keySet());
                assertTrue(toLong(v) >= 0, "expression SUM(col+col) must be non-negative: " + row);
            }
        } finally {
            _helper.resetEntityCreateTime();
        }
    }

    // ===== expression failure paths =====

    @Test
    public void testExpressionUnparseableFails() throws Exception {
        String dbUrl = "jdbc:h2:mem:meta_agg_unp;DB_CLOSE_DELAY=-1";
        _helper.seedAggTable(dbUrl);
        String tableId = _helper.prepareExternalTable(dbUrl, "qs_agg_unp", "EXT_AGG");
        _helper.createMeasure(tableId, "badExpr", "AMOUNT", "sum", "(AMOUNT * 2");
        _helper.createDimension(tableId, "cat", "CATEGORY", "categorical", null);

        ApiResponse<?> resp = queryAggregationRaw(tableId, Arrays.asList("badExpr"), Arrays.asList("cat"),
                null, null, null, null, null, null);
        assertTrue(!resp.isOk(),
                "unparseable expression must fail");
        String errMsg = resp.getMsg() != null ? resp.getMsg().toLowerCase() : "";
        assertTrue(errMsg.contains("unparseable"),
                "unparseable expression must fail with ERR_AGGR_EXPRESSION_UNPARSEABLE: " + resp);
    }

    @Test
    public void testExpressionUnsafeFails() throws Exception {
        String dbUrl = "jdbc:h2:mem:meta_agg_uns;DB_CLOSE_DELAY=-1";
        _helper.seedAggTable(dbUrl);
        String tableId = _helper.prepareExternalTable(dbUrl, "qs_agg_uns", "EXT_AGG");
        _helper.createMeasure(tableId, "dropExpr", "AMOUNT", "sum", "DROP TABLE foo");
        _helper.createDimension(tableId, "cat", "CATEGORY", "categorical", null);

        ApiResponse<?> resp = queryAggregationRaw(tableId, Arrays.asList("dropExpr"), Arrays.asList("cat"),
                null, null, null, null, null, null);
        assertTrue(!resp.isOk(),
                "unsafe expression must fail");
        String errMsg = resp.getMsg() != null ? resp.getMsg().toLowerCase() : "";
        assertTrue(errMsg.contains("unsafe"),
                "unsafe expression must fail with ERR_AGGR_EXPRESSION_UNSAFE: " + resp);
    }

    @Test
    public void testExpressionHavingOrderByUnsupportedFails() throws Exception {
        String dbUrl = "jdbc:h2:mem:meta_agg_having_expr;DB_CLOSE_DELAY=-1";
        _helper.seedAggTable(dbUrl);
        String tableId = _helper.prepareExternalTable(dbUrl, "qs_agg_having_expr", "EXT_AGG");
        _helper.createMeasure(tableId, "exprM", "AMOUNT", "sum", "AMOUNT * 2");
        _helper.createDimension(tableId, "cat", "CATEGORY", "categorical", null);

        TreeBean having = FilterBeans.gt("exprM", 15);
        ApiResponse<?> resp = queryAggregationRaw(tableId, Arrays.asList("exprM"), Arrays.asList("cat"),
                null, null, null, null, having, null);
        assertTrue(!resp.isOk(),
                "expression measure referenced by HAVING must explicitly fail: " + resp);
        String errMsg = resp.getMsg() != null ? resp.getMsg().toLowerCase() : "";
        assertTrue(errMsg.contains("having-order-by-unsupported")
                        || errMsg.contains("having or order by"),
                "expression measure referenced by HAVING must explicitly fail: " + resp);

        List<OrderFieldBean> orderBy = Arrays.asList(OrderFieldBean.desc("exprM"));
        ApiResponse<?> resp2 = queryAggregationRaw(tableId, Arrays.asList("exprM"), Arrays.asList("cat"),
                null, null, null, null, null, orderBy);
        assertTrue(!resp2.isOk(),
                "expression measure referenced by ORDER BY must explicitly fail: " + resp2);
        String errMsg2 = resp2.getMsg() != null ? resp2.getMsg().toLowerCase() : "";
        assertTrue(errMsg2.contains("having-order-by-unsupported")
                        || errMsg2.contains("having or order by"),
                "expression measure referenced by ORDER BY must explicitly fail: " + resp2);
    }

    // ===== save-time validation =====

    @Test
    public void testSaveTimeValidationFails() throws Exception {
        String dbUrl = "jdbc:h2:mem:meta_agg_save;DB_CLOSE_DELAY=-1";
        _helper.seedAggTable(dbUrl);
        String tableId = _helper.prepareExternalTable(dbUrl, "qs_agg_save", "EXT_AGG");

        io.nop.api.core.beans.graphql.GraphQLResponseBean dropResp = _helper.execSaveMutation(
                tableId, "badDrop", "DROP TABLE foo");
        assertNotNull(dropResp);
        assertTrue(dropResp.hasError(),
                "save with DROP keyword expression must fail (not silently stored): " + dropResp);

        io.nop.api.core.beans.graphql.GraphQLResponseBean unpResp = _helper.execSaveMutation(
                tableId, "badUnp", "(AMOUNT");
        assertTrue(unpResp.hasError(),
                "save with unparseable expression must fail (not silently stored): " + unpResp);

        StringBuilder tooLong = new StringBuilder();
        while (tooLong.length() <= 1000) {
            tooLong.append("AMOUNT + ");
        }
        tooLong.append("1");
        io.nop.api.core.beans.graphql.GraphQLResponseBean tlResp = _helper.execSaveMutation(
                tableId, "badTooLong", tooLong.toString());
        assertTrue(tlResp.hasError(),
                "save with too-long expression must fail (not silently stored): " + tlResp);

        IEntityDao<NopMetaTableMeasure> measureDao = daoProvider.daoFor(NopMetaTableMeasure.class);
        io.nop.api.core.beans.query.QueryBean q = new io.nop.api.core.beans.query.QueryBean();
        q.addFilter(io.nop.api.core.beans.FilterBeans.eq(NopMetaTableMeasure.PROP_NAME_metaTableId, tableId));
        List<NopMetaTableMeasure> saved = measureDao.findAllByQuery(q);
        for (NopMetaTableMeasure m : saved) {
            assertFalse("badDrop".equals(m.getMeasureName())
                            || "badUnp".equals(m.getMeasureName())
                            || "badTooLong".equals(m.getMeasureName()),
                    "invalid expression measure must NOT be persisted: " + m.getMeasureName());
        }
    }

    @Test
    public void testSaveTimeValidationAllowsValidExpression() throws Exception {
        String dbUrl = "jdbc:h2:mem:meta_agg_save_ok;DB_CLOSE_DELAY=-1";
        _helper.seedAggTable(dbUrl);
        String tableId = _helper.prepareExternalTable(dbUrl, "qs_agg_save_ok", "EXT_AGG");

        io.nop.api.core.beans.graphql.GraphQLResponseBean resp = _helper.execSaveMutation(
                tableId, "validExpr", "AMOUNT * 2");
        assertFalse(resp.hasError(),
                "save with valid expression must succeed via GraphQL mutation BizModel.save: " + resp);
    }

    // ===== external single-table having/orderBy =====

    @Test
    @SuppressWarnings("unchecked")
    public void testExternalAggregationHavingOrderBy() throws Exception {
        String dbUrl = "jdbc:h2:mem:meta_agg_having;DB_CLOSE_DELAY=-1";
        _helper.seedAggTable(dbUrl);
        try (Connection c = DriverManager.getConnection(dbUrl, "sa", "");
             Statement st = c.createStatement()) {
            st.execute("INSERT INTO ext_agg VALUES ('C', 5, '2024-01-15 10:00:00')");
        }
        String tableId = _helper.prepareExternalTable(dbUrl, "qs_agg_having", "EXT_AGG");
        _helper.createMeasure(tableId, "total", "AMOUNT", "sum", null);
        _helper.createDimension(tableId, "cat", "CATEGORY", "categorical", null);

        TreeBean having = FilterBeans.gt("total", 15);
        List<OrderFieldBean> orderBy = Arrays.asList(OrderFieldBean.desc("total"));

        List<Map<String, Object>> items = queryAggregationItems(tableId,
                Arrays.asList("total"), Arrays.asList("cat"), null, null, null, null, having, orderBy);
        
        assertEquals(2, items.size(), "having SUM>15 must exclude group C (sum=5)");
        for (Map<String, Object> row : items) {
            int total = toInt(getIgnoreCase(row, "TOTAL"));
            assertTrue(total > 15, "all retained groups must satisfy having SUM>15: " + row);
        }
    }

    @Test
    public void testExternalAggregationHavingUnknownNameFails() throws Exception {
        String dbUrl = "jdbc:h2:mem:meta_agg_having_unk;DB_CLOSE_DELAY=-1";
        _helper.seedAggTable(dbUrl);
        String tableId = _helper.prepareExternalTable(dbUrl, "qs_agg_having_unk", "EXT_AGG");
        _helper.createMeasure(tableId, "total", "AMOUNT", "sum", null);
        _helper.createDimension(tableId, "cat", "CATEGORY", "categorical", null);

        TreeBean having = FilterBeans.gt("unknown_measure", 15);
        ApiResponse<?> resp = queryAggregationRaw(tableId,
                Arrays.asList("total"), Arrays.asList("cat"), null, null, null, null, having, null);
        assertTrue(!resp.isOk(),
                "must fail with unknown measure name");
        String errMsg = resp.getMsg() != null ? resp.getMsg().toLowerCase() : "";
        assertTrue(errMsg.contains("unknown") || errMsg.contains("not in the user-selected"),
                "error message must indicate unknown name: " + resp);
    }

    @Test
    public void testExternalAggregationOrderByUnknownNameFails() throws Exception {
        String dbUrl = "jdbc:h2:mem:meta_agg_orderby_unk;DB_CLOSE_DELAY=-1";
        _helper.seedAggTable(dbUrl);
        String tableId = _helper.prepareExternalTable(dbUrl, "qs_agg_orderby_unk", "EXT_AGG");
        _helper.createMeasure(tableId, "total", "AMOUNT", "sum", null);
        _helper.createDimension(tableId, "cat", "CATEGORY", "categorical", null);

        List<OrderFieldBean> orderBy = Arrays.asList(OrderFieldBean.desc("unknown_measure"));
        ApiResponse<?> resp = queryAggregationRaw(tableId,
                Arrays.asList("total"), Arrays.asList("cat"), null, null, null, null, null, orderBy);
        assertTrue(!resp.isOk(),
                "must fail with unknown measure name");
        String errMsg = resp.getMsg() != null ? resp.getMsg().toLowerCase() : "";
        assertTrue(errMsg.contains("unknown") || errMsg.contains("not in the user-selected"),
                "error message must indicate unknown name: " + resp);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testBackwardCompatNoHavingNoOrderBy() throws Exception {
        String dbUrl = "jdbc:h2:mem:meta_agg_compat;DB_CLOSE_DELAY=-1";
        _helper.seedAggTable(dbUrl);
        String tableId = _helper.prepareExternalTable(dbUrl, "qs_agg_compat", "EXT_AGG");
        _helper.createMeasure(tableId, "total", "AMOUNT", "sum", null);
        _helper.createDimension(tableId, "cat", "CATEGORY", "categorical", null);

        List<Map<String, Object>> items = queryAggregationItems(tableId,
                Arrays.asList("total"), Arrays.asList("cat"), null, null, null, null, null, null);
        
        assertEquals(2, items.size(), "no having/orderBy → all groups retained (A, B)");
    }

    // ===== entity aggregation having/orderBy =====

    @Test
    @SuppressWarnings("unchecked")
    public void testEntityAggregationHavingOrderBy() {
        _helper.importModel();
        String tableId = _helper.findEntityTableId("nop_meta_module");
        IEntityDao<NopMetaEntityField> fieldDao = daoProvider.daoFor(NopMetaEntityField.class);
        String statusFieldId = _helper.findEntityFieldId("nop_meta_module", "status", fieldDao);
        _helper.createMeasure(tableId, "cnt", statusFieldId, "count", null);
        _helper.createDimension(tableId, "st", statusFieldId, "categorical", null);

        TreeBean having = FilterBeans.ge("cnt", 1);
        List<OrderFieldBean> orderBy = Arrays.asList(OrderFieldBean.desc("cnt"));
        List<Map<String, Object>> items = queryAggregationItems(tableId,
                Arrays.asList("cnt"), Arrays.asList("st"), null, null, null, null, having, orderBy);
        
        assertFalse(items.isEmpty(), "entity aggregation with having/orderBy must return real grouped rows: " + items);
        for (Map<String, Object> row : items) {
            assertTrue(toLong(getIgnoreCase(row, "CNT")) >= 1, "having cnt>=1 must hold: " + row);
        }
    }

    // ===== external single-table arithmetic having =====

    @Test
    @SuppressWarnings("unchecked")
    public void testExternalSqlArithmeticHaving() throws Exception {
        String dbUrl = "jdbc:h2:mem:meta_agg_arith_ext;DB_CLOSE_DELAY=-1";
        _helper.seedArithTable(dbUrl);
        String tableId = _helper.prepareExternalTable(dbUrl, "qs_agg_arith_ext", "EXT_ARITH");
        _helper.createMeasure(tableId, "sumA", "VAL_A", "sum", null);
        _helper.createMeasure(tableId, "sumB", "VAL_B", "sum", null);
        _helper.createDimension(tableId, "cat", "CATEGORY", "categorical", null);

        TreeBean having = havingArithmeticLeaf("gt", "sumA - sumB", 10);
        List<Map<String, Object>> items = queryAggregationItems(tableId,
                Arrays.asList("sumA", "sumB"), Arrays.asList("cat"),
                null, null, null, null, having, null);
        
        assertNotNull(items, "items must not be null");
        assertEquals(2, items.size(), "arithmetic having SUM(A)-SUM(B)>10 must keep A(24), B(29); drop C(4)");
        for (Map<String, Object> row : items) {
            int sumA = toInt(getIgnoreCase(row, "SUMA"));
            int sumB = toInt(getIgnoreCase(row, "SUMB"));
            assertTrue(sumA - sumB > 10,
                    "arithmetic having must hold for retained group: sumA=" + sumA + " sumB=" + sumB);
        }
    }

    @Test
    public void testExternalSqlArithmeticHavingUnknownNameFails() throws Exception {
        String dbUrl = "jdbc:h2:mem:meta_agg_arith_unk;DB_CLOSE_DELAY=-1";
        _helper.seedArithTable(dbUrl);
        String tableId = _helper.prepareExternalTable(dbUrl, "qs_agg_arith_unk", "EXT_ARITH");
        _helper.createMeasure(tableId, "sumA", "VAL_A", "sum", null);
        _helper.createDimension(tableId, "cat", "CATEGORY", "categorical", null);

        TreeBean having = havingArithmeticLeaf("gt", "sumA - sumB", 10);
        ApiResponse<?> resp = queryAggregationRaw(tableId,
                Arrays.asList("sumA"), Arrays.asList("cat"),
                null, null, null, null, having, null);
        assertTrue(!resp.isOk(),
                "arithmetic having with unknown measure name must explicitly fail: " + resp);
        String errMsg = resp.getMsg() != null ? resp.getMsg().toLowerCase() : "";
        assertTrue(errMsg.contains("unknown") || errMsg.contains("not in the user-selected"),
                "error message must indicate unknown name: " + resp);
    }

    @Test
    public void testExternalSqlArithmeticHavingExpressionMeasureFails() throws Exception {
        String dbUrl = "jdbc:h2:mem:meta_agg_arith_expr;DB_CLOSE_DELAY=-1";
        _helper.seedArithTable(dbUrl);
        String tableId = _helper.prepareExternalTable(dbUrl, "qs_agg_arith_expr", "EXT_ARITH");
        _helper.createMeasure(tableId, "exprM", "VAL_A", "sum", "VAL_A * 2");
        _helper.createMeasure(tableId, "sumB", "VAL_B", "sum", null);
        _helper.createDimension(tableId, "cat", "CATEGORY", "categorical", null);

        TreeBean having = havingArithmeticLeaf("gt", "exprM - sumB", 10);
        ApiResponse<?> resp = queryAggregationRaw(tableId,
                Arrays.asList("exprM", "sumB"), Arrays.asList("cat"),
                null, null, null, null, having, null);
        assertTrue(!resp.isOk(),
                "arithmetic having referencing expression-type measure must fail: " + resp);
        String errMsg = resp.getMsg() != null ? resp.getMsg().toLowerCase() : "";
        assertTrue(errMsg.contains("having-order-by-unsupported")
                        || errMsg.contains("having or order by"),
                "arithmetic having referencing expression-type measure must fail: " + resp);
    }

    @Test
    public void testExternalSqlArithmeticHavingLiteralFails() throws Exception {
        String dbUrl = "jdbc:h2:mem:meta_agg_arith_lit;DB_CLOSE_DELAY=-1";
        _helper.seedArithTable(dbUrl);
        String tableId = _helper.prepareExternalTable(dbUrl, "qs_agg_arith_lit", "EXT_ARITH");
        _helper.createMeasure(tableId, "sumA", "VAL_A", "sum", null);
        _helper.createMeasure(tableId, "sumB", "VAL_B", "sum", null);
        _helper.createDimension(tableId, "cat", "CATEGORY", "categorical", null);

        TreeBean having = havingArithmeticLeaf("gt", "sumA / sumB * 100", 10);
        ApiResponse<?> resp = queryAggregationRaw(tableId,
                Arrays.asList("sumA", "sumB"), Arrays.asList("cat"),
                null, null, null, null, having, null);
        assertTrue(!resp.isOk(),
                "arithmetic having with literal must explicitly fail: " + resp);
        String errMsg = resp.getMsg() != null ? resp.getMsg().toLowerCase() : "";
        assertTrue(errMsg.contains("unsafe") || errMsg.contains("literals")
                        || errMsg.contains("not allowed"),
                "arithmetic having with literal must explicitly fail: " + resp);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testBackwardCompatRegularHavingUnchanged() throws Exception {
        String dbUrl = "jdbc:h2:mem:meta_agg_arith_compat;DB_CLOSE_DELAY=-1";
        _helper.seedArithTable(dbUrl);
        String tableId = _helper.prepareExternalTable(dbUrl, "qs_agg_arith_compat", "EXT_ARITH");
        _helper.createMeasure(tableId, "sumA", "VAL_A", "sum", null);
        _helper.createDimension(tableId, "cat", "CATEGORY", "categorical", null);

        TreeBean having = FilterBeans.gt("sumA", 10);
        List<Map<String, Object>> items = queryAggregationItems(tableId,
                Arrays.asList("sumA"), Arrays.asList("cat"),
                null, null, null, null, having, null);
        
        assertEquals(2, items.size(), "regular having SUM(A)>10 must keep A(30), B(30); drop C(5)");
    }

    // ===== entity arithmetic having =====

    @Test
    @SuppressWarnings("unchecked")
    public void testEntityArithmeticHaving() {
        _helper.importModel();
        String tableId = _helper.findEntityTableId("nop_meta_module");
        IEntityDao<NopMetaEntityField> fieldDao = daoProvider.daoFor(NopMetaEntityField.class);
        String statusFieldId = _helper.findEntityFieldId("nop_meta_module", "status", fieldDao);
        _helper.createMeasure(tableId, "cntA", statusFieldId, "count", null);
        _helper.createMeasure(tableId, "cntB", statusFieldId, "count", null);
        _helper.createDimension(tableId, "st", statusFieldId, "categorical", null);

        TreeBean having = havingArithmeticLeaf("ge", "cntA - cntB", 0);
        List<Map<String, Object>> items = queryAggregationItems(tableId,
                Arrays.asList("cntA", "cntB"), Arrays.asList("st"),
                null, null, null, null, having, null);
        
        assertNotNull(items, "items must not be null");
        assertFalse(items.isEmpty(),
                "entity path arithmetic having must yield real grouped rows: " + items);
        for (Map<String, Object> row : items) {
            long cntA = toLong(getIgnoreCase(row, "CNTA"));
            long cntB = toLong(getIgnoreCase(row, "CNTB"));
            assertTrue(cntA - cntB >= 0,
                    "arithmetic having cntA-cntB>=0 must hold: cntA=" + cntA + " cntB=" + cntB);
        }
    }
}
