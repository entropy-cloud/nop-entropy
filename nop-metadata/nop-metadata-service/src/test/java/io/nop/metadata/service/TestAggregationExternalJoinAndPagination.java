package io.nop.metadata.service;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.TreeBean;
import io.nop.api.core.beans.query.OrderFieldBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.dao.api.IDaoProvider;
import io.nop.graphql.core.engine.IGraphQLEngine;
import io.nop.metadata.api.dto.AggregationResultDTO;
import io.nop.metadata.dao.entity.NopMetaEntity;
import io.nop.metadata.dao.entity.NopMetaEntityField;
import io.nop.orm.IOrmTemplate;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.graphql.core.ast.GraphQLOperationType;
import io.nop.metadata.service.NopMetadataException;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static io.nop.metadata.service.TestAggregationHelper.*;
import static org.junit.jupiter.api.Assertions.*;

@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestAggregationExternalJoinAndPagination extends JunitBaseTestCase {

    public TestAggregationExternalJoinAndPagination() {
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

    // ============================================================
    // external↔external 同库 JOIN 聚合
    // ============================================================

    @Test
    @SuppressWarnings("unchecked")
    public void testExternalExternalJoinAggregationCorrectness() throws Exception {
        String querySpace = "qs_ext_join";
        String dbUrl = "jdbc:h2:mem:" + querySpace + ";DB_CLOSE_DELAY=-1";
        _helper.seedFactDimTables(dbUrl);
        _helper.saveDataSource("ds-" + querySpace, querySpace, dbUrl);
        _helper.syncExternalTables("ds-" + querySpace);
        String factTableId = _helper.tableId("EXT_FACT");
        String dimTableId = _helper.tableId("EXT_DIM");

        String joinId = _helper.createTableTableJoin(factTableId, "inner", factTableId, dimTableId,
                "CAT_ID", "CAT_ID", "dim");
        _helper.createMeasureWithSide(factTableId, "total", "AMOUNT", "sum", "left");
        _helper.createDimensionWithSide(factTableId, "cat", "CAT_NAME", "categorical", null, "right");

        List<Map<String, Object>> items = queryAggregationItems(factTableId,
                Arrays.asList("total"), Arrays.asList("cat"), null, joinId, null, null, null, null);
        
        assertNotNull(items, "items must not be null");
        assertEquals(2, items.size(), "group by CAT_NAME must yield 2 groups (A,B): " + items);
        Map<String, Object> rowA = findRow(items, "CAT", "A");
        Map<String, Object> rowB = findRow(items, "CAT", "B");
        assertNotNull(rowA, "group A must exist: " + items);
        assertNotNull(rowB, "group B must exist: " + items);
        assertEquals(30, toInt(rowA.get("TOTAL")), "SUM(AMOUNT) for A = 10+20 = 30");
        assertEquals(30, toInt(rowB.get("TOTAL")), "SUM(AMOUNT) for B = 30");
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testExternalExternalJoinAggregationViaGraphQL() throws Exception {
        String querySpace = "qs_ext_join_gql";
        String dbUrl = "jdbc:h2:mem:" + querySpace + ";DB_CLOSE_DELAY=-1";
        _helper.seedFactDimTables(dbUrl);
        _helper.saveDataSource("ds-" + querySpace, querySpace, dbUrl);
        _helper.syncExternalTables("ds-" + querySpace);
        String factTableId = _helper.tableId("EXT_FACT");
        String dimTableId = _helper.tableId("EXT_DIM");

        String joinId = _helper.createTableTableJoin(factTableId, "inner", factTableId, dimTableId,
                "CAT_ID", "CAT_ID", "dim");
        _helper.createMeasureWithSide(factTableId, "gtotal", "AMOUNT", "sum", "left");
        _helper.createDimensionWithSide(factTableId, "gcat", "CAT_NAME", "categorical", null, "right");

        io.nop.api.core.beans.graphql.GraphQLRequestBean request = new io.nop.api.core.beans.graphql.GraphQLRequestBean();
        request.setQuery("query { NopMetaTable__queryAggregation(metaTableId: \"" + factTableId + "\", "
                + "measures: [\"gtotal\"], dimensions: [\"gcat\"], joinId: \"" + joinId + "\") { items } }");
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

    // ============================================================
    // 分页参数（AR-01 双绑 + AR-09 limit 归一化，plan 2026-08-06-0553-3 Phase 1）
    // ============================================================

    @Test
    @SuppressWarnings("unchecked")
    public void testExternalExternalJoinAggregationPagination() throws Exception {
        String querySpace = "qs_ext_join_page";
        String dbUrl = "jdbc:h2:mem:" + querySpace + ";DB_CLOSE_DELAY=-1";
        _helper.seedFactDimTables(dbUrl);
        _helper.saveDataSource("ds-" + querySpace, querySpace, dbUrl);
        _helper.syncExternalTables("ds-" + querySpace);
        String factTableId = _helper.tableId("EXT_FACT");
        String dimTableId = _helper.tableId("EXT_DIM");

        String joinId = _helper.createTableTableJoin(factTableId, "inner", factTableId, dimTableId,
                "CAT_ID", "CAT_ID", "dim");
        _helper.createMeasureWithSide(factTableId, "total", "AMOUNT", "sum", "left");
        _helper.createDimensionWithSide(factTableId, "cat", "CAT_NAME", "categorical", null, "right");

        // AR-01：双绑 bug 修复前 limit!=null 必抛 SQLException；修复后按行键断言分页结果
        List<Map<String, Object>> items = queryAggregationItems(factTableId,
                Arrays.asList("total"), Arrays.asList("cat"), null, joinId, 2L, 1L, null, null);

        assertNotNull(items, "items must not be null");
        // 2 个分组（A,B）offset=1 后应剩 1 个（SQL LIMIT 2 OFFSET 1）
        assertEquals(1, items.size(), "limit=2 offset=1 on 2 groups must yield 1 group: " + items);
        Map<String, Object> row = items.get(0);
        String cat = String.valueOf(getIgnoreCase(row, "CAT"));
        assertTrue("A".equals(cat) || "B".equals(cat),
                "remaining group must be A or B (row-key based, no implicit order): " + items);
        assertEquals(30, toInt(getIgnoreCase(row, "TOTAL")), "remaining group SUM(AMOUNT) must be 30: " + items);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testMixedSameDbJoinAggregationPagination() throws Exception {
        _helper.importModel();
        NopMetaEntity leftEntity = _helper.findMetaEntityByTable("nop_meta_module");
        String leftTableId = _helper.findEntityTableId("nop_meta_module");

        String querySpace = "qs_mixed_page";
        String dbUrl = "jdbc:h2:mem:" + querySpace + ";DB_CLOSE_DELAY=-1";
        _helper.seedMixedSameDbTables(dbUrl);
        _helper.saveDataSource("ds-" + querySpace, querySpace, dbUrl);
        _helper.syncExternalTables("ds-" + querySpace);
        String dimTableId = _helper.externalTableId("MIXED_DIM");

        String joinId = _helper.createMixedJoin(leftTableId, "inner", leftEntity.getMetaEntityId(), dimTableId,
                "status", "STATUS_VAL", "dim");
        String statusFieldId = _helper.findEntityFieldId("nop_meta_module", "status");
        _helper.createMeasureWithSide(leftTableId, "cnt", statusFieldId, "count", "left");
        _helper.createDimensionWithSide(leftTableId, "cat", "CAT_NAME", "categorical", null, "right");

        // AR-01：mixed 同库路径同样双绑，limit!=null 必炸；修复后分页正确
        List<Map<String, Object>> items = queryAggregationItems(leftTableId,
                Arrays.asList("cnt"), Arrays.asList("cat"), null, joinId, 2L, 1L, null, null);

        assertNotNull(items, "items must not be null");
        // 2 个分组（Category A, Category B）offset=1 后应剩 1 个
        assertEquals(1, items.size(), "limit=2 offset=1 on 2 groups must yield 1 group: " + items);
        Map<String, Object> row = items.get(0);
        String cat = String.valueOf(getIgnoreCase(row, "CAT"));
        assertTrue("Category A".equals(cat) || "Category B".equals(cat),
                "remaining group must be Category A or B (row-key based, no implicit order): " + items);
    }

    // ============================================================
    // external↔external 跨库 JOIN 聚合（D10 内存 GROUP BY）
    // ============================================================

    @Test
    @SuppressWarnings("unchecked")
    public void testExternalExternalCrossDbJoinAggregationSucceeds() throws Exception {
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
        _helper.saveDataSource("ds-" + qs1, qs1, dbUrl1);
        _helper.saveDataSource("ds-" + qs2, qs2, dbUrl2);
        _helper.syncExternalTables("ds-" + qs1);
        _helper.syncExternalTables("ds-" + qs2);
        String factTableId = _helper.externalTableId("EXT_FACT_CROSS");
        String dimTableId = _helper.externalTableId("EXT_DIM_CROSS");

        String joinId = _helper.createTableTableJoin(factTableId, "inner", factTableId, dimTableId, "K", "K", "dim");
        _helper.createMeasureWithSide(factTableId, "total", "AMOUNT", "sum", "left");
        _helper.createDimensionWithSide(factTableId, "cat", "CAT_NAME", "categorical", null, "right");

        List<Map<String, Object>> items = queryAggregationItems(factTableId,
                Arrays.asList("total"), Arrays.asList("cat"), null, joinId, null, null, null, null);
        
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
    // external JOIN side validation failure paths
    // ============================================================

    @Test
    public void testExternalJoinAggregationExternalSideRequiredFails() throws Exception {
        String querySpace = "qs_ext_side";
        String dbUrl = "jdbc:h2:mem:" + querySpace + ";DB_CLOSE_DELAY=-1";
        _helper.seedFactDimTables(dbUrl);
        _helper.saveDataSource("ds-" + querySpace, querySpace, dbUrl);
        _helper.syncExternalTables("ds-" + querySpace);
        String factTableId = _helper.tableId("EXT_FACT");
        String dimTableId = _helper.tableId("EXT_DIM");

        String joinId = _helper.createTableTableJoin(factTableId, "inner", factTableId, dimTableId,
                "CAT_ID", "CAT_ID", "dim");
        _helper.createMeasure(factTableId, "total", "AMOUNT", "sum", null);
        _helper.createDimensionWithSide(factTableId, "cat", "CAT_NAME", "categorical", null, "right");

        assertTrue(_helper.queryAggregationJoinHasError(factTableId, "total", "cat", joinId),
                "external/sql join endpoint measure without side must explicitly fail (side required at query-time)");
    }

    @Test
    public void testExternalJoinAggregationColumnNotOnSideFails() throws Exception {
        String querySpace = "qs_ext_col";
        String dbUrl = "jdbc:h2:mem:" + querySpace + ";DB_CLOSE_DELAY=-1";
        _helper.seedFactDimTables(dbUrl);
        _helper.saveDataSource("ds-" + querySpace, querySpace, dbUrl);
        _helper.syncExternalTables("ds-" + querySpace);
        String factTableId = _helper.tableId("EXT_FACT");
        String dimTableId = _helper.tableId("EXT_DIM");

        String joinId = _helper.createTableTableJoin(factTableId, "inner", factTableId, dimTableId,
                "CAT_ID", "CAT_ID", "dim");
        _helper.createMeasureWithSide(factTableId, "total", "AMOUNT", "sum", "right");
        _helper.createDimensionWithSide(factTableId, "cat", "CAT_NAME", "categorical", null, "right");

        assertTrue(_helper.queryAggregationJoinHasError(factTableId, "total", "cat", joinId),
                "measure column not on declared side endpoint must explicitly fail");
    }

    @Test
    public void testExternalJoinAggregationEntitySideMismatchFails() {
        _helper.importModel();
        NopMetaEntity leftEntity = _helper.findMetaEntityByTable("nop_meta_entity");
        NopMetaEntity rightEntity = _helper.findMetaEntityByTable("nop_meta_entity_field");
        String leftTableId = _helper.findEntityTableId("nop_meta_entity");
        String joinId = _helper.createJoin(leftTableId, "inner", leftEntity.getMetaEntityId(),
                rightEntity.getMetaEntityId(), "metaEntityId", "metaEntityId", "fld");
        String leftDimFieldId = _helper.findEntityFieldId("nop_meta_entity", "displayName");
        _helper.createDimensionWithSide(leftTableId, "st", leftDimFieldId, "categorical", null, "right");
        String rightMeasureFieldId = _helper.findEntityFieldId("nop_meta_entity_field", "fieldName");
        _helper.createMeasure(leftTableId, "cnt", rightMeasureFieldId, "count", null);

        assertTrue(_helper.queryAggregationJoinHasError(leftTableId, "cnt", "st", joinId),
                "entity endpoint side inconsistent with metaEntityId attribution must explicitly fail");
    }

    @Test
    public void testJoinAggregationTableEndpointFails() throws Exception {
        _helper.importModel();
        NopMetaEntity leftEntity = _helper.findMetaEntityByTable("nop_meta_entity");
        String leftTableId = _helper.findEntityTableId("nop_meta_entity");
        String querySpace = "qs_agg_tbl_ep";
        String dbUrl = "jdbc:h2:mem:" + querySpace + ";DB_CLOSE_DELAY=-1";
        seedH2(dbUrl, "CREATE TABLE ext_dim (k VARCHAR(20), v VARCHAR(20))", "INSERT INTO ext_dim VALUES ('k1','v1')");
        _helper.saveDataSource("ds-" + querySpace, querySpace, dbUrl);
        String sqlTableId = _helper.saveSqlTableManual("SELECT k, v FROM ext_dim", querySpace);

        String joinId = _helper.createMixedJoin(leftTableId, "inner", leftEntity.getMetaEntityId(), sqlTableId,
                "displayName", "k", "x");
        assertTrue(_helper.queryAggregationJoinHasError(leftTableId, joinId),
                "JOIN aggregation with external/sql table endpoint must explicitly fail (deferred)");
    }

    // ============================================================
    // entity↔entity 跨库 JOIN 聚合（D10 内存 GROUP BY）
    // ============================================================

    @Test
    @SuppressWarnings("unchecked")
    public void testEntityEntityCrossDbJoinAggregationSucceeds() {
        _helper.importModel();
        NopMetaEntity leftEntity = _helper.findMetaEntityByTable("nop_meta_entity");
        NopMetaEntity rightEntity = _helper.findMetaEntityByTable("nop_meta_entity_field");
        _helper.updateQuerySpaceSql(rightEntity.getMetaEntityId(), "qs_agg_cross_db");
        try {
            String leftTableId = _helper.findEntityTableId("nop_meta_entity");
            String joinId = _helper.createJoin(leftTableId, "inner", leftEntity.getMetaEntityId(),
                    rightEntity.getMetaEntityId(), "metaEntityId", "metaEntityId", "fld");
            String leftDimFieldId = _helper.findEntityFieldId("nop_meta_entity", "displayName");
            String rightMeasureFieldId = _helper.findEntityFieldId("nop_meta_entity_field", "fieldName");
            _helper.createDimension(leftTableId, "st", leftDimFieldId, "categorical", null);
            _helper.createMeasure(leftTableId, "cnt", rightMeasureFieldId, "count", null);

            List<Map<String, Object>> items = queryAggregationItems(leftTableId,
                    Arrays.asList("cnt"), Arrays.asList("st"), null, joinId, null, null, null, null);
            
            assertNotNull(items, "items must not be null");
            assertFalse(items.isEmpty(),
                    "cross-DB entity-entity JOIN aggregation must return real grouped rows via in-memory GROUP BY: " + items);
            long totalFields = _helper.countRows("select count(*) as c from nop_meta_entity_field");
            long sumCnt = 0;
            for (Map<String, Object> row : items) {
                assertNotNull(getIgnoreCase(row, "ST"), "dimension column ST must be present: " + row.keySet());
                Object cnt = getIgnoreCase(row, "CNT");
                assertNotNull(cnt, "measure column CNT must be present (not silent null/0): " + row.keySet());
                sumCnt += toLong(cnt);
            }
            assertEquals(totalFields, sumCnt,
                    "SUM(count(fields)) grouped by entity.displayName must equal total field rows "
                            + "(cross-DB in-memory GROUP BY correctness): " + items);
        } finally {
            _helper.updateQuerySpaceSql(rightEntity.getMetaEntityId(), null);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testEntityEntityCrossDbJoinAggregationViaGraphQL() {
        _helper.importModel();
        NopMetaEntity leftEntity = _helper.findMetaEntityByTable("nop_meta_entity");
        NopMetaEntity rightEntity = _helper.findMetaEntityByTable("nop_meta_entity_field");
        _helper.updateQuerySpaceSql(rightEntity.getMetaEntityId(), "qs_agg_cross_db_gql");
        try {
            String leftTableId = _helper.findEntityTableId("nop_meta_entity");
            String joinId = _helper.createJoin(leftTableId, "inner", leftEntity.getMetaEntityId(),
                    rightEntity.getMetaEntityId(), "metaEntityId", "metaEntityId", "fld");
            String leftDimFieldId = _helper.findEntityFieldId("nop_meta_entity", "displayName");
            String rightMeasureFieldId = _helper.findEntityFieldId("nop_meta_entity_field", "fieldName");
            _helper.createDimension(leftTableId, "gst", leftDimFieldId, "categorical", null);
            _helper.createMeasure(leftTableId, "gcnt", rightMeasureFieldId, "count", null);

            io.nop.api.core.beans.graphql.GraphQLRequestBean request = new io.nop.api.core.beans.graphql.GraphQLRequestBean();
            request.setQuery("query { NopMetaTable__queryAggregation(metaTableId: \"" + leftTableId + "\", "
                    + "measures: [\"gcnt\"], dimensions: [\"gst\"], joinId: \"" + joinId + "\") { items } }");
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
            _helper.updateQuerySpaceSql(rightEntity.getMetaEntityId(), null);
        }
    }

    @Test
    public void testEntityEntityCrossDbSelfJoinFails() {
        _helper.importModel();
        NopMetaEntity entity = _helper.findMetaEntityByTable("nop_meta_entity");
        _helper.updateQuerySpaceSql(entity.getMetaEntityId(), "qs_cross_self");
        try {
            String leftTableId = _helper.findEntityTableId("nop_meta_entity");
            String joinId = _helper.createJoin(leftTableId, "inner", entity.getMetaEntityId(),
                    entity.getMetaEntityId(), "metaEntityId", "metaEntityId", "self");
            assertTrue(_helper.queryAggregationJoinHasError(leftTableId, joinId),
                    "cross-DB self-join JOIN aggregation must explicitly fail (alias attribution ambiguous)");
        } finally {
            _helper.updateQuerySpaceSql(entity.getMetaEntityId(), null);
        }
    }

    // ============================================================
    // 混合端点（entity↔external）同库 JOIN 聚合
    // ============================================================

    @Test
    @SuppressWarnings("unchecked")
    public void testMixedSameDbJoinAggregationCorrectness() throws Exception {
        _helper.importModel();
        NopMetaEntity leftEntity = _helper.findMetaEntityByTable("nop_meta_module");
        String leftTableId = _helper.findEntityTableId("nop_meta_module");

        String querySpace = "qs_mixed_same";
        String dbUrl = "jdbc:h2:mem:" + querySpace + ";DB_CLOSE_DELAY=-1";
        _helper.seedMixedSameDbTables(dbUrl);
        _helper.saveDataSource("ds-" + querySpace, querySpace, dbUrl);
        _helper.syncExternalTables("ds-" + querySpace);
        String dimTableId = _helper.externalTableId("MIXED_DIM");

        String joinId = _helper.createMixedJoin(leftTableId, "inner", leftEntity.getMetaEntityId(), dimTableId,
                "status", "STATUS_VAL", "dim");
        String statusFieldId = _helper.findEntityFieldId("nop_meta_module", "status");
        _helper.createMeasureWithSide(leftTableId, "cnt", statusFieldId, "count", "left");
        _helper.createDimensionWithSide(leftTableId, "cat", "CAT_NAME", "categorical", null, "right");

        List<Map<String, Object>> items = queryAggregationItems(leftTableId,
                Arrays.asList("cnt"), Arrays.asList("cat"), null, joinId, null, null, null, null);
        
        assertNotNull(items, "items must not be null");
        assertEquals(2, items.size(), "group by CAT_NAME must yield 2 groups (Category A, Category B): " + items);
        Map<String, Object> rowA = findRow(items, "CAT", "Category A");
        Map<String, Object> rowB = findRow(items, "CAT", "Category B");
        assertNotNull(rowA, "group 'Category A' must exist: " + items);
        assertNotNull(rowB, "group 'Category B' must exist: " + items);
        assertEquals(2, toInt(rowA.get("CNT")), "COUNT(status) for Category A = 2 (mod1, mod2)");
        assertEquals(1, toInt(rowB.get("CNT")), "COUNT(status) for Category B = 1 (mod3)");
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testMixedSameDbJoinAggregationViaGraphQL() throws Exception {
        _helper.importModel();
        NopMetaEntity leftEntity = _helper.findMetaEntityByTable("nop_meta_module");
        String leftTableId = _helper.findEntityTableId("nop_meta_module");

        String querySpace = "qs_mixed_gql";
        String dbUrl = "jdbc:h2:mem:" + querySpace + ";DB_CLOSE_DELAY=-1";
        _helper.seedMixedSameDbTables(dbUrl);
        _helper.saveDataSource("ds-" + querySpace, querySpace, dbUrl);
        _helper.syncExternalTables("ds-" + querySpace);
        String dimTableId = _helper.externalTableId("MIXED_DIM");

        String joinId = _helper.createMixedJoin(leftTableId, "inner", leftEntity.getMetaEntityId(), dimTableId,
                "status", "STATUS_VAL", "dim");
        String statusFieldId = _helper.findEntityFieldId("nop_meta_module", "status");
        _helper.createMeasureWithSide(leftTableId, "gcnt", statusFieldId, "count", "left");
        _helper.createDimensionWithSide(leftTableId, "gcat", "CAT_NAME", "categorical", null, "right");

        io.nop.api.core.beans.graphql.GraphQLRequestBean request = new io.nop.api.core.beans.graphql.GraphQLRequestBean();
        request.setQuery("query { NopMetaTable__queryAggregation(metaTableId: \"" + leftTableId + "\", "
                + "measures: [\"gcnt\"], dimensions: [\"gcat\"], joinId: \"" + joinId + "\") { items } }");
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

    // ============================================================
    // 混合端点跨库 JOIN 聚合（D10 内存 GROUP BY）
    // ============================================================

    @Test
    @SuppressWarnings("unchecked")
    public void testMixedCrossDbJoinAggregationSucceeds() throws Exception {
        _helper.importModel();
        NopMetaEntity leftEntity = _helper.findMetaEntityByTable("nop_meta_module");
        String leftTableId = _helper.findEntityTableId("nop_meta_module");

        List<String> statuses = _helper.queryDistinctColumnValues("select distinct status from nop_meta_module", "STATUS");
        assertFalse(statuses.isEmpty(), "nop_meta_module must have status values after import");

        String querySpace = "qs_mixed_cross_ok";
        String dbUrl = "jdbc:h2:mem:" + querySpace + ";DB_CLOSE_DELAY=-1";
        List<String> statements = new ArrayList<>();
        statements.add("CREATE TABLE MIXED_DIM (STATUS_VAL VARCHAR(20), CAT_NAME VARCHAR(20))");
        for (String s : statuses) {
            statements.add("INSERT INTO MIXED_DIM VALUES ('" + s.replace("'", "''") + "', 'All')");
        }
        seedH2(dbUrl, statements.toArray(new String[0]));
        _helper.saveDataSource("ds-" + querySpace, querySpace, dbUrl);
        _helper.syncExternalTables("ds-" + querySpace);
        String dimTableId = _helper.externalTableId("MIXED_DIM");

        String joinId = _helper.createMixedJoin(leftTableId, "inner", leftEntity.getMetaEntityId(), dimTableId,
                "status", "STATUS_VAL", "dim");
        String statusFieldId = _helper.findEntityFieldId("nop_meta_module", "status");
        _helper.createMeasureWithSide(leftTableId, "cnt", statusFieldId, "count", "left");
        _helper.createDimensionWithSide(leftTableId, "cat", "CAT_NAME", "categorical", null, "right");

        List<Map<String, Object>> items = queryAggregationItems(leftTableId,
                Arrays.asList("cnt"), Arrays.asList("cat"), null, joinId, null, null, null, null);
        
        assertNotNull(items, "items must not be null");
        assertFalse(items.isEmpty(),
                "cross-DB mixed JOIN aggregation must return real grouped rows via in-memory GROUP BY: " + items);
        for (Map<String, Object> row : items) {
            Object cnt = getIgnoreCase(row, "CNT");
            assertNotNull(cnt, "measure CNT must be present (not silent null/0): " + row.keySet());
            assertTrue(toLong(cnt) > 0, "cross-DB mixed in-memory GROUP BY count must be real positive value: " + row);
        }
        Map<String, Object> rowAll = findRow(items, "CAT", "All");
        assertNotNull(rowAll, "group 'All' must exist: " + items);
    }

    // ============================================================
    // 混合端点 side/failure paths
    // ============================================================

    @Test
    public void testMixedCrossDbTableSideRequiredFails() throws Exception {
        _helper.importModel();
        NopMetaEntity leftEntity = _helper.findMetaEntityByTable("nop_meta_module");
        String leftTableId = _helper.findEntityTableId("nop_meta_module");

        String querySpace = "qs_mixed_cross_side";
        String dbUrl = "jdbc:h2:mem:" + querySpace + ";DB_CLOSE_DELAY=-1";
        seedH2(dbUrl, "CREATE TABLE MIXED_DIM (STATUS_VAL VARCHAR(20), CAT_NAME VARCHAR(20))",
                "INSERT INTO MIXED_DIM VALUES ('A', 'Category A')");
        _helper.saveDataSource("ds-" + querySpace, querySpace, dbUrl);
        _helper.syncExternalTables("ds-" + querySpace);
        String dimTableId = _helper.externalTableId("MIXED_DIM");

        String joinId = _helper.createMixedJoin(leftTableId, "inner", leftEntity.getMetaEntityId(), dimTableId,
                "status", "STATUS_VAL", "dim");
        String statusFieldId = _helper.findEntityFieldId("nop_meta_module", "status");
        _helper.createMeasureWithSide(leftTableId, "cnt", statusFieldId, "count", "left");
        _helper.createDimension(leftTableId, "cat", "CAT_NAME", "categorical", null);

        assertTrue(_helper.queryAggregationJoinHasError(leftTableId, "cnt", "cat", joinId),
                "cross-DB mixed-endpoint external/sql dimension without side must explicitly fail (side required)");
    }

    @Test
    public void testMixedJoinTableSideRequiredFails() throws Exception {
        _helper.importModel();
        NopMetaEntity leftEntity = _helper.findMetaEntityByTable("nop_meta_module");
        String leftTableId = _helper.findEntityTableId("nop_meta_module");

        String querySpace = "qs_mixed_side";
        String dbUrl = "jdbc:h2:mem:" + querySpace + ";DB_CLOSE_DELAY=-1";
        _helper.seedMixedSameDbTables(dbUrl);
        _helper.saveDataSource("ds-" + querySpace, querySpace, dbUrl);
        _helper.syncExternalTables("ds-" + querySpace);
        String dimTableId = _helper.externalTableId("MIXED_DIM");

        String joinId = _helper.createMixedJoin(leftTableId, "inner", leftEntity.getMetaEntityId(), dimTableId,
                "status", "STATUS_VAL", "dim");
        String statusFieldId = _helper.findEntityFieldId("nop_meta_module", "status");
        _helper.createMeasureWithSide(leftTableId, "cnt", statusFieldId, "count", "left");
        _helper.createDimension(leftTableId, "cat", "CAT_NAME", "categorical", null);

        assertTrue(_helper.queryAggregationJoinHasError(leftTableId, "cnt", "cat", joinId),
                "mixed-endpoint external/sql dimension without side must explicitly fail (side required at query-time)");
    }

    @Test
    public void testMixedJoinColumnNotOnSideFails() throws Exception {
        _helper.importModel();
        NopMetaEntity leftEntity = _helper.findMetaEntityByTable("nop_meta_module");
        String leftTableId = _helper.findEntityTableId("nop_meta_module");

        String querySpace = "qs_mixed_col";
        String dbUrl = "jdbc:h2:mem:" + querySpace + ";DB_CLOSE_DELAY=-1";
        _helper.seedMixedSameDbTables(dbUrl);
        _helper.saveDataSource("ds-" + querySpace, querySpace, dbUrl);
        _helper.syncExternalTables("ds-" + querySpace);
        String dimTableId = _helper.externalTableId("MIXED_DIM");

        String joinId = _helper.createMixedJoin(leftTableId, "inner", leftEntity.getMetaEntityId(), dimTableId,
                "status", "STATUS_VAL", "dim");
        String statusFieldId = _helper.findEntityFieldId("nop_meta_module", "status");
        _helper.createMeasureWithSide(leftTableId, "cnt", statusFieldId, "count", "left");
        _helper.createDimensionWithSide(leftTableId, "cat", "CAT_NAME", "categorical", null, "left");

        assertTrue(_helper.queryAggregationJoinHasError(leftTableId, "cnt", "cat", joinId),
                "mixed-endpoint dimension column not on declared side endpoint must explicitly fail");
    }

    @Test
    public void testMixedJoinJoinTypeRightFails() throws Exception {
        _helper.importModel();
        NopMetaEntity leftEntity = _helper.findMetaEntityByTable("nop_meta_module");
        String leftTableId = _helper.findEntityTableId("nop_meta_module");

        String querySpace = "qs_mixed_right";
        String dbUrl = "jdbc:h2:mem:" + querySpace + ";DB_CLOSE_DELAY=-1";
        _helper.seedMixedSameDbTables(dbUrl);
        _helper.saveDataSource("ds-" + querySpace, querySpace, dbUrl);
        _helper.syncExternalTables("ds-" + querySpace);
        String dimTableId = _helper.externalTableId("MIXED_DIM");

        String joinId = _helper.createMixedJoin(leftTableId, "right", leftEntity.getMetaEntityId(), dimTableId,
                "status", "STATUS_VAL", "dim");
        assertTrue(_helper.queryAggregationJoinHasError(leftTableId, joinId),
                "joinType=right mixed-endpoint JOIN aggregation must explicitly fail (not silently degrade)");
    }

    // ============================================================
    // JOIN 聚合 + having/orderBy
    // ============================================================

    @Test
    @SuppressWarnings("unchecked")
    public void testExternalExternalJoinHavingOrderBy() throws Exception {
        String querySpace = "qs_ext_join_having";
        String dbUrl = "jdbc:h2:mem:" + querySpace + ";DB_CLOSE_DELAY=-1";
        _helper.seedFactDimTables(dbUrl);
        _helper.saveDataSource("ds-" + querySpace, querySpace, dbUrl);
        _helper.syncExternalTables("ds-" + querySpace);
        String factTableId = _helper.tableId("EXT_FACT");
        String dimTableId = _helper.tableId("EXT_DIM");

        String joinId = _helper.createTableTableJoin(factTableId, "inner", factTableId, dimTableId,
                "CAT_ID", "CAT_ID", "dim");
        _helper.createMeasureWithSide(factTableId, "total", "AMOUNT", "sum", "left");
        _helper.createDimensionWithSide(factTableId, "cat", "CAT_NAME", "categorical", null, "right");

        TreeBean having = FilterBeans.ge("total", 30);
        List<OrderFieldBean> orderBy = Arrays.asList(OrderFieldBean.desc("total"));
        List<Map<String, Object>> items = queryAggregationItems(factTableId,
                Arrays.asList("total"), Arrays.asList("cat"), null, joinId, null, null, having, orderBy);
        
        assertEquals(2, items.size(), "having total>=30 must keep both groups A and B");
        for (Map<String, Object> row : items) {
            assertTrue(toInt(getIgnoreCase(row, "TOTAL")) >= 30, "having total>=30 must hold: " + row);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testMixedSameDbJoinHavingOrderBy() throws Exception {
        _helper.importModel();
        NopMetaEntity leftEntity = _helper.findMetaEntityByTable("nop_meta_module");
        String leftTableId = _helper.findEntityTableId("nop_meta_module");

        String querySpace = "qs_mixed_having";
        String dbUrl = "jdbc:h2:mem:" + querySpace + ";DB_CLOSE_DELAY=-1";
        _helper.seedMixedSameDbTables(dbUrl);
        _helper.saveDataSource("ds-" + querySpace, querySpace, dbUrl);
        _helper.syncExternalTables("ds-" + querySpace);
        String dimTableId = _helper.externalTableId("MIXED_DIM");

        String joinId = _helper.createMixedJoin(leftTableId, "inner", leftEntity.getMetaEntityId(), dimTableId,
                "status", "STATUS_VAL", "dim");
        String statusFieldId = _helper.findEntityFieldId("nop_meta_module", "status");
        _helper.createMeasureWithSide(leftTableId, "cnt", statusFieldId, "count", "left");
        _helper.createDimensionWithSide(leftTableId, "cat", "CAT_NAME", "categorical", null, "right");

        TreeBean having = FilterBeans.ge("cnt", 1);
        List<OrderFieldBean> orderBy = Arrays.asList(OrderFieldBean.desc("cnt"));
        List<Map<String, Object>> items = queryAggregationItems(leftTableId,
                Arrays.asList("cnt"), Arrays.asList("cat"), null, joinId, null, null, having, orderBy);
        
        assertNotNull(items, "items must not be null");
        assertFalse(items.isEmpty(), "mixed same-DB JOIN with having/orderBy must return real grouped rows: " + items);
        for (Map<String, Object> row : items) {
            assertTrue(toLong(getIgnoreCase(row, "CNT")) >= 1, "having cnt>=1 must hold: " + row);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testCrossDbJoinMemoryHavingOrderBy() {
        _helper.importModel();
        NopMetaEntity leftEntity = _helper.findMetaEntityByTable("nop_meta_entity");
        NopMetaEntity rightEntity = _helper.findMetaEntityByTable("nop_meta_entity_field");
        _helper.updateQuerySpaceSql(rightEntity.getMetaEntityId(), "qs_agg_cross_having");
        try {
            String leftTableId = _helper.findEntityTableId("nop_meta_entity");
            String joinId = _helper.createJoin(leftTableId, "inner", leftEntity.getMetaEntityId(),
                    rightEntity.getMetaEntityId(), "metaEntityId", "metaEntityId", "fld");
            String leftDimFieldId = _helper.findEntityFieldId("nop_meta_entity", "displayName");
            String rightMeasureFieldId = _helper.findEntityFieldId("nop_meta_entity_field", "fieldName");
            _helper.createDimension(leftTableId, "st", leftDimFieldId, "categorical", null);
            _helper.createMeasure(leftTableId, "cnt", rightMeasureFieldId, "count", null);

            TreeBean having = FilterBeans.ge("cnt", 1);
            List<OrderFieldBean> orderBy = Arrays.asList(OrderFieldBean.desc("cnt"));
            List<Map<String, Object>> items = queryAggregationItems(leftTableId,
                    Arrays.asList("cnt"), Arrays.asList("st"), null, joinId, null, null, having, orderBy);
            
            assertNotNull(items, "items must not be null");
            assertFalse(items.isEmpty(),
                    "cross-DB in-memory JOIN with having/orderBy must return real grouped rows: " + items);
            for (Map<String, Object> row : items) {
                Object cnt = getIgnoreCase(row, "CNT");
                assertNotNull(cnt, "measure CNT must be present (not silent null/0): " + row.keySet());
                assertTrue(toLong(cnt) >= 1, "having cnt>=1 must hold: " + row);
            }
            if (items.size() > 1) {
                for (int i = 1; i < items.size(); i++) {
                    long prev = toLong(getIgnoreCase(items.get(i - 1), "CNT"));
                    long curr = toLong(getIgnoreCase(items.get(i), "CNT"));
                    assertTrue(prev >= curr, "orderBy cnt DESC must hold: prev=" + prev + " curr=" + curr);
                }
            }
        } finally {
            _helper.updateQuerySpaceSql(rightEntity.getMetaEntityId(), null);
        }
    }

    /**
     * AR-19 端到端（plan 2026-08-06-0914-3 Phase 2，Minimum Rules #22）：同一份数据在
     * 跨库内存聚合路径（fact/dim 分属两个数据源 → in-memory GROUP BY + MemoryFilterEvaluator having）
     * 与同库 SQL 路径（fact/dim 同一数据源 → SQL JOIN + SQL HAVING）下，同一 having
     * （measure 比较 + dimension LIKE）产出一致结果集。
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testCrossDbMemoryHavingMatchesSameDbSqlPath() throws Exception {
        // ===== 配置 A：跨库内存路径（fact 在 qs1、dim 在 qs2）=====
        String qs1 = "qs_e2e_mem_1";
        String qs2 = "qs_e2e_mem_2";
        String dbUrl1 = "jdbc:h2:mem:" + qs1 + ";DB_CLOSE_DELAY=-1";
        String dbUrl2 = "jdbc:h2:mem:" + qs2 + ";DB_CLOSE_DELAY=-1";
        seedH2(dbUrl1, "CREATE TABLE EXT_FACT_E2E (CAT_ID INT, AMOUNT INT)",
                "INSERT INTO EXT_FACT_E2E VALUES (1, 10)",
                "INSERT INTO EXT_FACT_E2E VALUES (1, 20)",
                "INSERT INTO EXT_FACT_E2E VALUES (2, 30)");
        seedH2(dbUrl2, "CREATE TABLE EXT_DIM_E2E (CAT_ID INT, CAT_NAME VARCHAR(20))",
                "INSERT INTO EXT_DIM_E2E VALUES (1, 'A')",
                "INSERT INTO EXT_DIM_E2E VALUES (2, 'B')");
        _helper.saveDataSource("ds-" + qs1, qs1, dbUrl1);
        _helper.saveDataSource("ds-" + qs2, qs2, dbUrl2);
        _helper.syncExternalTables("ds-" + qs1);
        _helper.syncExternalTables("ds-" + qs2);
        String memFact = _helper.externalTableId("EXT_FACT_E2E");
        String memDim = _helper.externalTableId("EXT_DIM_E2E");
        String memJoin = _helper.createTableTableJoin(memFact, "inner", memFact, memDim, "CAT_ID", "CAT_ID", "dim");
        _helper.createMeasureWithSide(memFact, "total", "AMOUNT", "sum", "left");
        _helper.createDimensionWithSide(memFact, "cat", "CAT_NAME", "categorical", null, "right");

        // ===== 配置 B：同库 SQL 路径（fact + dim 同一数据源；NopMetaTable UK 无 datasource 维度，
        // 同表名跨库会 upsert 串行，故 SQL 配置用独立表名、数据内容一致）=====
        String qs3 = "qs_e2e_sql";
        String dbUrl3 = "jdbc:h2:mem:" + qs3 + ";DB_CLOSE_DELAY=-1";
        seedH2(dbUrl3, "CREATE TABLE EXT_FACT_E2E_SQL (CAT_ID INT, AMOUNT INT)",
                "INSERT INTO EXT_FACT_E2E_SQL VALUES (1, 10)",
                "INSERT INTO EXT_FACT_E2E_SQL VALUES (1, 20)",
                "INSERT INTO EXT_FACT_E2E_SQL VALUES (2, 30)",
                "CREATE TABLE EXT_DIM_E2E_SQL (CAT_ID INT, CAT_NAME VARCHAR(20))",
                "INSERT INTO EXT_DIM_E2E_SQL VALUES (1, 'A')",
                "INSERT INTO EXT_DIM_E2E_SQL VALUES (2, 'B')");
        _helper.saveDataSource("ds-" + qs3, qs3, dbUrl3);
        _helper.syncExternalTables("ds-" + qs3);
        String sqlFact = _helper.externalTableId("EXT_FACT_E2E_SQL");
        String sqlDim = _helper.externalTableId("EXT_DIM_E2E_SQL");
        String sqlJoin = _helper.createTableTableJoin(sqlFact, "inner", sqlFact, sqlDim, "CAT_ID", "CAT_ID", "dim");
        _helper.createMeasureWithSide(sqlFact, "total", "AMOUNT", "sum", "left");
        _helper.createDimensionWithSide(sqlFact, "cat", "CAT_NAME", "categorical", null, "right");

        // ===== 同一 having：measure 比较 + dimension LIKE =====
        TreeBean having = FilterBeans.and(
                FilterBeans.ge("total", 30),
                FilterBeans.like("cat", "A%"));

        List<Map<String, Object>> memoryItems = queryAggregationItems(memFact,
                Arrays.asList("total"), Arrays.asList("cat"), null, memJoin, null, null, having, null);
        List<Map<String, Object>> sqlItems = queryAggregationItems(sqlFact,
                Arrays.asList("total"), Arrays.asList("cat"), null, sqlJoin, null, null, having, null);

        // 两路径一致：恰好组 A（total=30, CAT=A）——组 B 被 LIKE 排除
        assertEquals(1, memoryItems.size(), "cross-DB memory having must keep exactly group A: " + memoryItems);
        assertEquals(memoryItems.size(), sqlItems.size(),
                "memory path and SQL path must agree on group count: memory=" + memoryItems + " sql=" + sqlItems);
        assertEquals("A", getIgnoreCase(memoryItems.get(0), "CAT"), "memory path group: " + memoryItems);
        assertEquals("A", getIgnoreCase(sqlItems.get(0), "CAT"), "SQL path group: " + sqlItems);
        assertEquals(toInt(getIgnoreCase(memoryItems.get(0), "TOTAL")),
                toInt(getIgnoreCase(sqlItems.get(0), "TOTAL")),
                "memory and SQL paths must agree on measure value");
        assertEquals(30, toInt(getIgnoreCase(memoryItems.get(0), "TOTAL")), "SUM(AMOUNT) for A = 30");
    }

    /**
     * AR-20b 端到端（plan 2026-08-06-1228-1 Phase 2，Minimum Rules #22）：跨库 join 一侧 join 键为
     * INT（H2 → Integer）、另一侧为 BIGINT（H2 → Long）——修复前经
     * {@code CrossDbJoinMerger.verifyCrossDbKeyTypeConsistency} 精确类比较抛
     * {@code ERR_JOIN_CROSS_DB_KEY_TYPE_MISMATCH}（INT vs BIGINT 数值相等误拒）；修复后整型族数值等值
     * 键匹配（stringKey 语义），聚合产出正确 join 结果。
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testCrossDbJoinIntVsBigintKeyMatches() throws Exception {
        String qs1 = "qs_e2e_int";
        String qs2 = "qs_e2e_bigint";
        String dbUrl1 = "jdbc:h2:mem:" + qs1 + ";DB_CLOSE_DELAY=-1";
        String dbUrl2 = "jdbc:h2:mem:" + qs2 + ";DB_CLOSE_DELAY=-1";
        seedH2(dbUrl1, "CREATE TABLE EXT_FACT_INT (CAT_ID INT, AMOUNT INT)",
                "INSERT INTO EXT_FACT_INT VALUES (1, 10)",
                "INSERT INTO EXT_FACT_INT VALUES (1, 20)",
                "INSERT INTO EXT_FACT_INT VALUES (2, 30)");
        seedH2(dbUrl2, "CREATE TABLE EXT_DIM_BIG (CAT_ID BIGINT, CAT_NAME VARCHAR(20))",
                "INSERT INTO EXT_DIM_BIG VALUES (1, 'A')",
                "INSERT INTO EXT_DIM_BIG VALUES (2, 'B')");
        _helper.saveDataSource("ds-" + qs1, qs1, dbUrl1);
        _helper.saveDataSource("ds-" + qs2, qs2, dbUrl2);
        _helper.syncExternalTables("ds-" + qs1);
        _helper.syncExternalTables("ds-" + qs2);
        String factTableId = _helper.externalTableId("EXT_FACT_INT");
        String dimTableId = _helper.externalTableId("EXT_DIM_BIG");
        String joinId = _helper.createTableTableJoin(factTableId, "inner", factTableId, dimTableId,
                "CAT_ID", "CAT_ID", "dim");
        _helper.createMeasureWithSide(factTableId, "total", "AMOUNT", "sum", "left");
        _helper.createDimensionWithSide(factTableId, "cat", "CAT_NAME", "categorical", null, "right");

        List<Map<String, Object>> items = queryAggregationItems(factTableId,
                Arrays.asList("total"), Arrays.asList("cat"), null, joinId, null, null, null, null);

        assertNotNull(items, "items must not be null");
        assertEquals(2, items.size(),
                "INT vs BIGINT join keys are numerically equal and must both match (groups A,B): " + items);
        assertEquals("A", getIgnoreCase(items.get(0), "CAT"), "group A first: " + items);
        assertEquals("B", getIgnoreCase(items.get(1), "CAT"), "group B second: " + items);
        assertEquals(30, toInt(getIgnoreCase(items.get(0), "TOTAL")), "SUM(AMOUNT) for A = 10+20 = 30");
        assertEquals(30, toInt(getIgnoreCase(items.get(1), "TOTAL")), "SUM(AMOUNT) for B = 30");
    }

    // ============================================================
    // JOIN 路径 expression
    // ============================================================

    @Test
    @SuppressWarnings("unchecked")
    public void testJoinPathExpressionMeasureExecution() throws Exception {
        String querySpace = "qs_ext_join_expr";
        String dbUrl = "jdbc:h2:mem:" + querySpace + ";DB_CLOSE_DELAY=-1";
        _helper.seedFactDimTables(dbUrl);
        _helper.saveDataSource("ds-" + querySpace, querySpace, dbUrl);
        _helper.syncExternalTables("ds-" + querySpace);
        String factTableId = _helper.tableId("EXT_FACT");
        String dimTableId = _helper.tableId("EXT_DIM");

        String joinId = _helper.createTableTableJoin(factTableId, "inner", factTableId, dimTableId,
                "CAT_ID", "CAT_ID", "dim");
        _helper.createMeasureWithSideAndExpression(factTableId, "totalDbl", "AMOUNT", "sum", "left",
                "l.AMOUNT * 2");
        _helper.createDimensionWithSide(factTableId, "cat", "CAT_NAME", "categorical", null, "right");

        List<Map<String, Object>> items = queryAggregationItems(factTableId,
                Arrays.asList("totalDbl"), Arrays.asList("cat"), null, joinId, null, null, null, null);
        
        assertNotNull(items);
        assertEquals(2, items.size(), "group by CAT_NAME must yield 2 groups (A,B): " + items);
        Map<String, Object> rowA = findRow(items, "CAT", "A");
        Map<String, Object> rowB = findRow(items, "CAT", "B");
        assertEquals(60, toInt(rowA.get("TOTALDBL")), "SUM(l.AMOUNT*2) for A = 60: " + rowA);
        assertEquals(60, toInt(rowB.get("TOTALDBL")), "SUM(l.AMOUNT*2) for B = 60: " + rowB);
    }

    @Test
    public void testCrossDbPathExpressionMeasureFails() throws Exception {
        _helper.importModel();
        NopMetaEntity leftEntity = _helper.findMetaEntityByTable("nop_meta_module");
        NopMetaEntity rightEntity = _helper.findMetaEntityByTable("nop_meta_entity");
        _helper.updateQuerySpaceSql(rightEntity.getMetaEntityId(), "qs_cross_expr");
        try {
            String leftTableId = _helper.findEntityTableId("nop_meta_module");
            String joinId = _helper.createJoin(leftTableId, "inner", leftEntity.getMetaEntityId(),
                    rightEntity.getMetaEntityId(), "moduleId", "moduleId", "xe");
            String leftFieldId = _helper.findEntityFieldId("nop_meta_module", "status");
            _helper.createMeasure(leftTableId, "exprM", leftFieldId, "sum", "1");
            _helper.createDimension(leftTableId, "st", leftFieldId, "categorical", null);

            ApiResponse<?> resp = queryAggregationRaw(
                    leftTableId, Arrays.asList("exprM"), Arrays.asList("st"),
                    null, joinId, null, null, null, null);
            assertTrue(!resp.isOk(), "cross-DB path expression must explicitly fail with memory-not-computable ErrorCode");
            String errMsg = resp.getMsg() != null ? resp.getMsg().toLowerCase() : "";
            assertTrue(errMsg.contains("expression-memory-not-computable")
                            || errMsg.contains("memory-not-computable")
                            || errMsg.contains("not computable"),
                    "cross-DB path expression must explicitly fail with memory-not-computable ErrorCode: " + resp.getMsg());
        } finally {
            _helper.updateQuerySpaceSql(rightEntity.getMetaEntityId(), null);
        }
    }

    @Test
    public void testCrossDbMemoryArithmeticHavingFails() {
        _helper.importModel();
        NopMetaEntity leftEntity = _helper.findMetaEntityByTable("nop_meta_entity");
        NopMetaEntity rightEntity = _helper.findMetaEntityByTable("nop_meta_entity_field");
        _helper.updateQuerySpaceSql(rightEntity.getMetaEntityId(), "qs_arith_cross");
        try {
            String leftTableId = _helper.findEntityTableId("nop_meta_entity");
            String joinId = _helper.createJoin(leftTableId, "inner", leftEntity.getMetaEntityId(),
                    rightEntity.getMetaEntityId(), "metaEntityId", "metaEntityId", "fld");
            String leftDimFieldId = _helper.findEntityFieldId("nop_meta_entity", "displayName");
            String rightMeasureFieldId = _helper.findEntityFieldId("nop_meta_entity_field", "fieldName");
            _helper.createDimension(leftTableId, "st", leftDimFieldId, "categorical", null);
            _helper.createMeasure(leftTableId, "cntA", rightMeasureFieldId, "count", null);
            _helper.createMeasure(leftTableId, "cntB", rightMeasureFieldId, "count", null);

            TreeBean having = havingArithmeticLeaf("gt", "cntA - cntB", 0);
            ApiResponse<?> resp = queryAggregationRaw(
                    leftTableId, Arrays.asList("cntA", "cntB"), Arrays.asList("st"),
                    null, joinId, null, null, having, null);
            assertTrue(!resp.isOk(), "cross-DB memory path arithmetic having must explicitly fail");
            String errMsg = resp.getMsg() != null ? resp.getMsg().toLowerCase() : "";
            assertTrue(errMsg.contains("having-expr-memory-not-computable")
                            || errMsg.contains("memory-not-computable")
                            || errMsg.contains("not computable"),
                    "cross-DB memory path arithmetic having must explicitly fail: " + resp.getMsg());
        } finally {
            _helper.updateQuerySpaceSql(rightEntity.getMetaEntityId(), null);
        }
    }

    // ============================================================
    // external↔external JOIN 算术 having
    // ============================================================

    @Test
    @SuppressWarnings("unchecked")
    public void testExternalExternalJoinArithmeticHaving() throws Exception {
        String querySpace = "qs_ext_join_arith";
        String dbUrl = "jdbc:h2:mem:" + querySpace + ";DB_CLOSE_DELAY=-1";
        _helper.seedFactDimTables(dbUrl);
        try (Connection c = DriverManager.getConnection(dbUrl, "sa", "");
             Statement st = c.createStatement()) {
            st.execute("ALTER TABLE ext_fact ADD COLUMN val_x INT");
            st.execute("UPDATE ext_fact SET val_x = 1 WHERE cat_id = 1");
            st.execute("UPDATE ext_fact SET val_x = 2 WHERE cat_id = 2");
        }
        _helper.saveDataSource("ds-" + querySpace, querySpace, dbUrl);
        _helper.syncExternalTables("ds-" + querySpace);
        String factTableId = _helper.tableId("EXT_FACT");
        String dimTableId = _helper.tableId("EXT_DIM");

        String joinId = _helper.createTableTableJoin(factTableId, "inner", factTableId, dimTableId,
                "CAT_ID", "CAT_ID", "dim");
        _helper.createMeasureWithSide(factTableId, "sumAmt", "AMOUNT", "sum", "left");
        _helper.createMeasureWithSide(factTableId, "sumX", "VAL_X", "sum", "left");
        _helper.createDimensionWithSide(factTableId, "cat", "CAT_NAME", "categorical", null, "right");

        TreeBean having = havingArithmeticLeaf("ge", "sumAmt - sumX", 20);
        List<Map<String, Object>> items = queryAggregationItems(factTableId,
                Arrays.asList("sumAmt", "sumX"), Arrays.asList("cat"),
                null, joinId, null, null, having, null);
        
        assertNotNull(items);
        assertEquals(2, items.size(), "arithmetic having sumAmt-sumX>=20 must keep both groups");
        for (Map<String, Object> row : items) {
            int sumAmt = toInt(getIgnoreCase(row, "SUMAMT"));
            int sumX = toInt(getIgnoreCase(row, "SUMX"));
            assertTrue(sumAmt - sumX >= 20,
                    "JOIN arithmetic having must hold: sumAmt=" + sumAmt + " sumX=" + sumX);
        }
    }
}
