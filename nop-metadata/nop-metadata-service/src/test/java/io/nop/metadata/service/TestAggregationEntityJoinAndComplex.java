package io.nop.metadata.service;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.TreeBean;
import io.nop.api.core.beans.query.OrderFieldBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.graphql.core.ast.GraphQLOperationType;
import io.nop.graphql.core.engine.IGraphQLEngine;
import io.nop.metadata.core.dto.AggregationResultDTO;
import io.nop.metadata.dao.entity.NopMetaEntity;
import io.nop.metadata.dao.entity.NopMetaEntityField;
import io.nop.orm.IOrmTemplate;
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
public class TestAggregationEntityJoinAndComplex extends JunitBaseTestCase {

    public TestAggregationEntityJoinAndComplex() {
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
            throw new NopException(NopMetadataErrors.ERR_AGGR_EXEC_FAILED).param("response", String.valueOf(resp));
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
    // entity 表聚合（D6 entity 路径）
    // ============================================================

    @Test
    @SuppressWarnings("unchecked")
    public void testEntityAggregation() {
        _helper.importModel();
        String tableId = _helper.findEntityTableId("nop_meta_module");
        IEntityDao<NopMetaEntityField> fieldDao = daoProvider.daoFor(NopMetaEntityField.class);
        String statusFieldId = _helper.findEntityFieldId("nop_meta_module", "status", fieldDao);
        _helper.createMeasure(tableId, "cnt", statusFieldId, "count", null);
        _helper.createDimension(tableId, "st", statusFieldId, "categorical", null);

        List<Map<String, Object>> items = queryAggregationItems(tableId,
                Arrays.asList("cnt"), Arrays.asList("st"), null, null, null, null, null, null);
        
        assertFalse(items.isEmpty(), "entity aggregation by status must return real grouped rows: " + items);
    }

    // ============================================================
    // entity 路径时间维度 granularity 分桶（D7.1，bypass EQL）
    // ============================================================

    @Test
    @SuppressWarnings("unchecked")
    public void testEntityTemporalGranularityBucketingMonth() {
        _helper.importModel();
        long totalEntities = _helper.countRows("select count(*) as c from nop_meta_entity");
        assertTrue(totalEntities >= 2, "nop_meta_entity must have at least 2 rows for month bucketing: " + totalEntities);
        _helper.spreadEntityCreateTimeAcrossTwoMonths();
        try {
            String tableId = _helper.findEntityTableId("nop_meta_entity");
            String createTimeFieldId = _helper.findEntityFieldId("nop_meta_entity", "createTime");
            _helper.createMeasure(tableId, "cnt", createTimeFieldId, "count", null);
            _helper.createDimension(tableId, "mon", createTimeFieldId, "temporal", "month");

            List<Map<String, Object>> items = queryAggregationItems(tableId,
                    Arrays.asList("cnt"), Arrays.asList("mon"), null, null, null, null, null, null);
            
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
            _helper.resetEntityCreateTime();
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testEntityTemporalGranularityAllValues() {
        _helper.importModel();
        long totalEntities = _helper.countRows("select count(*) as c from nop_meta_entity");
        assertTrue(totalEntities >= 2, "nop_meta_entity must have at least 2 rows: " + totalEntities);
        _helper.spreadEntityCreateTimeAcrossTwoMonths();
        try {
            String[] granularities = {"year", "quarter", "month", "week", "day", "hour"};
            for (String granularity : granularities) {
                String tableId = _helper.findEntityTableId("nop_meta_entity");
                String createTimeFieldId = _helper.findEntityFieldId("nop_meta_entity", "createTime");
                _helper.createMeasure(tableId, "cnt_" + granularity, createTimeFieldId, "count", null);
                _helper.createDimension(tableId, "d_" + granularity, createTimeFieldId, "temporal", granularity);

                List<Map<String, Object>> items = queryAggregationItems(tableId,
                        Arrays.asList("cnt_" + granularity), Arrays.asList("d_" + granularity),
                        null, null, null, null, null, null);
                
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
            _helper.resetEntityCreateTime();
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testEntityAggregationGranularityMatchesExternal() throws Exception {
        _helper.importModel();
        _helper.spreadEntityCreateTimeAcrossTwoMonths();
        try {
            List<Object[]> rowsWithCreateTime = _helper.queryEntityCreateTimeRows();
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
            _helper.saveDataSource("ds-" + querySpace, querySpace, dbUrl);
            _helper.syncExternalTables("ds-" + querySpace);
            String externalTableId = _helper.externalTableId("EXT_ENTITY_GRAN");

            _helper.createMeasure(externalTableId, "xcnt", "K", "count", null);
            _helper.createDimension(externalTableId, "xmon", "CREATED_AT", "temporal", "month");
            List<Map<String, Object>> extItems = queryAggregationItems(externalTableId,
                    Arrays.asList("xcnt"), Arrays.asList("xmon"), null, null, null, null, null, null);

            String entityTableId = _helper.findEntityTableId("nop_meta_entity");
            String createTimeFieldId = _helper.findEntityFieldId("nop_meta_entity", "createTime");
            _helper.createMeasure(entityTableId, "ecnt", createTimeFieldId, "count", null);
            _helper.createDimension(entityTableId, "emon", createTimeFieldId, "temporal", "month");
            List<Map<String, Object>> entityItems = queryAggregationItems(entityTableId,
                    Arrays.asList("ecnt"), Arrays.asList("emon"), null, null, null, null, null, null);

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
                        "bucket " + e.getKey() + " count must match between entity and external paths");
            }
        } finally {
            _helper.resetEntityCreateTime();
        }
    }

    @Test
    public void testEntityUnsupportedGranularityFails() {
        _helper.importModel();
        String tableId = _helper.findEntityTableId("nop_meta_entity");
        String createTimeFieldId = _helper.findEntityFieldId("nop_meta_entity", "createTime");
        _helper.createMeasure(tableId, "cnt", createTimeFieldId, "count", null);
        _helper.createDimension(tableId, "weird", createTimeFieldId, "temporal", "fortnight");

        assertTrue(_helper.queryAggregationHasError(tableId, Arrays.asList("cnt"), Arrays.asList("weird")),
                "entity path with unsupported granularity must explicitly fail");
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testEntityTemporalGranularityBucketingViaGraphQL() {
        _helper.importModel();
        long totalEntities = _helper.countRows("select count(*) as c from nop_meta_entity");
        _helper.spreadEntityCreateTimeAcrossTwoMonths();
        try {
            String tableId = _helper.findEntityTableId("nop_meta_entity");
            String createTimeFieldId = _helper.findEntityFieldId("nop_meta_entity", "createTime");
            _helper.createMeasure(tableId, "gcnt", createTimeFieldId, "count", null);
            _helper.createDimension(tableId, "gmon", createTimeFieldId, "temporal", "month");

            io.nop.api.core.beans.graphql.GraphQLRequestBean request = new io.nop.api.core.beans.graphql.GraphQLRequestBean();
            request.setQuery("query { NopMetaTable__queryAggregation(metaTableId: \"" + tableId + "\", "
                    + "measures: [\"gcnt\"], dimensions: [\"gmon\"]) { items } }");
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
            _helper.resetEntityCreateTime();
        }
    }

    // ============================================================
    // entity↔entity JOIN 聚合（plan 0852-1）
    // ============================================================

    @Test
    @SuppressWarnings("unchecked")
    public void testEntityJoinAggregationCorrectness() {
        _helper.importModel();
        NopMetaEntity leftEntity = _helper.findMetaEntityByTable("nop_meta_entity");
        NopMetaEntity rightEntity = _helper.findMetaEntityByTable("nop_meta_entity_field");
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
        assertFalse(items.isEmpty(), "entity JOIN aggregation must return real grouped rows: " + items);

        long totalFields = _helper.countRows("select count(*) as c from nop_meta_entity_field");
        long sumCnt = 0;
        for (Map<String, Object> row : items) {
            assertNotNull(getIgnoreCase(row, "ST"), "dimension column ST must be present: " + row.keySet());
            Object cnt = getIgnoreCase(row, "CNT");
            assertNotNull(cnt, "measure column CNT must be present: " + row.keySet());
            sumCnt += toLong(cnt);
        }
        assertEquals(totalFields, sumCnt,
                "SUM(count(fields)) grouped by entity.displayName must equal total field rows: " + items);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testEntityJoinAggregationViaGraphQL() {
        _helper.importModel();
        NopMetaEntity leftEntity = _helper.findMetaEntityByTable("nop_meta_entity");
        NopMetaEntity rightEntity = _helper.findMetaEntityByTable("nop_meta_entity_field");
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
        assertFalse(resp.hasError(), "GraphQL queryAggregation(joinId) must succeed: " + resp);
        Map<String, Object> data = (Map<String, Object>) resp.getData();
        Object qaObj = data.get("NopMetaTable__queryAggregation");
        assertNotNull(qaObj, "GraphQL queryAggregation(joinId) must return non-null Map result");
        Map<String, Object> qa = (Map<String, Object>) qaObj;
        List<Map<String, Object>> items = (List<Map<String, Object>>) qa.get("items");
        assertNotNull(items, "GraphQL items must not be null");
        assertFalse(items.isEmpty(),
                "GraphQL queryAggregation(joinId) end-to-end must return real grouped rows: " + items);
    }

    /** 无 joinId（null）→ 单表聚合既有行为零回归。 */
    @Test
    @SuppressWarnings("unchecked")
    public void testNoJoinIdSingleTableBehaviorUnchanged() {
        _helper.importModel();
        String tableId = _helper.findEntityTableId("nop_meta_module");
        String statusFieldId = _helper.findEntityFieldId("nop_meta_module", "status");
        _helper.createMeasure(tableId, "mcnt", statusFieldId, "count", null);
        _helper.createDimension(tableId, "mst", statusFieldId, "categorical", null);

        List<Map<String, Object>> items = queryAggregationItems(tableId,
                Arrays.asList("mcnt"), Arrays.asList("mst"), null, null, null, null, null, null);
        
        assertFalse(items.isEmpty(), "single-table aggregation (no joinId) must still work: " + items);
    }

    /** joinType=right → 显式失败。 */
    @Test
    public void testJoinAggregationJoinTypeRightFails() {
        _helper.importModel();
        NopMetaEntity leftEntity = _helper.findMetaEntityByTable("nop_meta_entity");
        NopMetaEntity rightEntity = _helper.findMetaEntityByTable("nop_meta_entity_field");
        String leftTableId = _helper.findEntityTableId("nop_meta_entity");
        String joinId = _helper.createJoin(leftTableId, "right", leftEntity.getMetaEntityId(),
                rightEntity.getMetaEntityId(), "metaEntityId", "metaEntityId", "fld");
        assertTrue(_helper.queryAggregationJoinHasError(leftTableId, joinId),
                "joinType=right JOIN aggregation must explicitly fail (not silently degrade)");
    }

    /** join 不存在/不归属 → 显式失败。 */
    @Test
    public void testJoinAggregationNotFoundFails() {
        _helper.importModel();
        String leftTableId = _helper.findEntityTableId("nop_meta_entity");
        assertTrue(_helper.queryAggregationJoinHasError(leftTableId, "__no_such_join__"),
                "non-existent join must explicitly fail in JOIN aggregation");
    }

    /** Measure 的 entityFieldId 归属既非左也非右 entity → 字段不可归属，显式失败。 */
    @Test
    public void testJoinAggregationFieldSideUnresolvedFails() {
        _helper.importModel();
        NopMetaEntity leftEntity = _helper.findMetaEntityByTable("nop_meta_entity");
        NopMetaEntity rightEntity = _helper.findMetaEntityByTable("nop_meta_entity_field");
        String leftTableId = _helper.findEntityTableId("nop_meta_entity");
        String joinId = _helper.createJoin(leftTableId, "inner", leftEntity.getMetaEntityId(),
                rightEntity.getMetaEntityId(), "metaEntityId", "metaEntityId", "fld");
        String leftDimFieldId = _helper.findEntityFieldId("nop_meta_entity", "displayName");
        String thirdEntityFieldId = _helper.findEntityFieldId("nop_meta_module", "status");
        _helper.createDimension(leftTableId, "st", leftDimFieldId, "categorical", null);
        _helper.createMeasure(leftTableId, "cnt", thirdEntityFieldId, "count", null);

        assertTrue(_helper.queryAggregationJoinHasError(leftTableId, "cnt", "st", joinId),
                "measure whose entityFieldId belongs to neither left nor right entity must explicitly fail");
    }

    /** self-join（leftEntityId == rightEntityId）→ 字段归属两侧均命中、无法表达右别名，显式失败。 */
    @Test
    public void testJoinAggregationSelfJoinFails() {
        _helper.importModel();
        NopMetaEntity entity = _helper.findMetaEntityByTable("nop_meta_entity");
        String leftTableId = _helper.findEntityTableId("nop_meta_entity");
        String joinId = _helper.createJoin(leftTableId, "inner", entity.getMetaEntityId(),
                entity.getMetaEntityId(), "metaEntityId", "metaEntityId", "self");
        assertTrue(_helper.queryAggregationJoinHasError(leftTableId, joinId),
                "self-join JOIN aggregation must explicitly fail (alias attribution ambiguous)");
    }

    // ============================================================
    // 接线验证：重构/路由增加后原有路径仍工作
    // ============================================================

    @Test
    @SuppressWarnings("unchecked")
    public void testEntityJoinAggregationStillWorksAfterRefactor() {
        _helper.importModel();
        NopMetaEntity leftEntity = _helper.findMetaEntityByTable("nop_meta_entity");
        NopMetaEntity rightEntity = _helper.findMetaEntityByTable("nop_meta_entity_field");
        String leftTableId = _helper.findEntityTableId("nop_meta_entity");
        String joinId = _helper.createJoin(leftTableId, "inner", leftEntity.getMetaEntityId(),
                rightEntity.getMetaEntityId(), "metaEntityId", "metaEntityId", "fld");
        String leftDimFieldId = _helper.findEntityFieldId("nop_meta_entity", "displayName");
        String rightMeasureFieldId = _helper.findEntityFieldId("nop_meta_entity_field", "fieldName");
        _helper.createDimension(leftTableId, "rst", leftDimFieldId, "categorical", null);
        _helper.createMeasure(leftTableId, "rcnt", rightMeasureFieldId, "count", null);

        List<Map<String, Object>> items = queryAggregationItems(leftTableId,
                Arrays.asList("rcnt"), Arrays.asList("rst"), null, joinId, null, null, null, null);
        
        assertNotNull(items, "items must not be null");
        assertFalse(items.isEmpty(), "entity-entity JOIN aggregation must still work after refactor: " + items);
        long totalFields = _helper.countRows("select count(*) as c from nop_meta_entity_field");
        long sumCnt = 0;
        for (Map<String, Object> row : items) {
            sumCnt += toLong(getIgnoreCase(row, "RCNT"));
        }
        assertEquals(totalFields, sumCnt,
                "SUM(count(fields)) grouped by entity.displayName must equal total field rows");
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testEntityEntityJoinStillWorksAfterMixedRoute() {
        _helper.importModel();
        NopMetaEntity leftEntity = _helper.findMetaEntityByTable("nop_meta_entity");
        NopMetaEntity rightEntity = _helper.findMetaEntityByTable("nop_meta_entity_field");
        String leftTableId = _helper.findEntityTableId("nop_meta_entity");
        String joinId = _helper.createJoin(leftTableId, "inner", leftEntity.getMetaEntityId(),
                rightEntity.getMetaEntityId(), "metaEntityId", "metaEntityId", "fld");
        String leftDimFieldId = _helper.findEntityFieldId("nop_meta_entity", "displayName");
        String rightMeasureFieldId = _helper.findEntityFieldId("nop_meta_entity_field", "fieldName");
        _helper.createDimension(leftTableId, "mst", leftDimFieldId, "categorical", null);
        _helper.createMeasure(leftTableId, "mcnt", rightMeasureFieldId, "count", null);

        List<Map<String, Object>> items = queryAggregationItems(leftTableId,
                Arrays.asList("mcnt"), Arrays.asList("mst"), null, joinId, null, null, null, null);
        
        assertNotNull(items, "items must not be null");
        assertFalse(items.isEmpty(), "entity-entity JOIN aggregation must still work after mixed-endpoint route added: " + items);
    }

    // ============================================================
    // entity↔entity JOIN + having/orderBy
    // ============================================================

    @Test
    @SuppressWarnings("unchecked")
    public void testEntityEntityJoinHavingOrderBy() {
        _helper.importModel();
        NopMetaEntity leftEntity = _helper.findMetaEntityByTable("nop_meta_entity");
        NopMetaEntity rightEntity = _helper.findMetaEntityByTable("nop_meta_entity_field");
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
        assertFalse(items.isEmpty(), "entity-entity JOIN with having/orderBy must return real grouped rows: " + items);
        if (items.size() > 1) {
            long first = toLong(getIgnoreCase(items.get(0), "CNT"));
            long last = toLong(getIgnoreCase(items.get(items.size() - 1), "CNT"));
            assertTrue(first >= last, "orderBy cnt DESC must hold: first=" + first + " last=" + last);
        }
    }
}
