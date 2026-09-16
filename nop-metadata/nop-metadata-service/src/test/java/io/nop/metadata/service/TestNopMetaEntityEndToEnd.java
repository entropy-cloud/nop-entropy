package io.nop.metadata.service;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.autotest.NopTestProperty;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.core.context.IServiceContext;
import io.nop.core.context.ServiceContextImpl;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.graphql.core.ast.GraphQLOperationType;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.engine.IGraphQLEngine;
import io.nop.metadata.api.dto.QueryEntityDataResultDTO;
import io.nop.metadata.api.dto.QueryJoinDataResultDTO;
import io.nop.metadata.api.dto.ResolveEntityFieldsResultDTO;
import io.nop.metadata.biz.INopMetaEntityBiz;
import io.nop.metadata.dao.entity.NopMetaEntity;
import io.nop.metadata.dao.entity.NopMetaLineageEdge;
import io.nop.metadata.dao.entity.NopMetaTagLabel;
import io.nop.metadata.service.search.NopMetaSearchProcessor;
import io.nop.search.api.ISearchEngine;
import io.nop.search.api.SearchHit;
import io.nop.search.api.SearchRequest;
import io.nop.search.api.SearchResponse;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2261 Phase 4 端到端验证：单一用例按序走通外部数据源接入 → 实体同步 → 字段解析 →
 * 数据查询 → 聚合 → 关联查询 → 标注 → 血缘 → 搜索索引的完整链路（架构基线 §2/§4 主干）。
 *
 * <p>Anti-Hollow：每步断言真实产物（外部实体行落库、queryData 返回真实行、聚合桶计数与
 * 种子数据一致、join key 值相等、搜索命中实体自身 id），任何一步为空壳/伪造即失败。
 *
 * <p>夹具沿 {@link AggregationTestHelper}（H2 建连 + syncExternalTables + RPC 调用）与
 * {@code TestNopMetaJoinBizModel}/{@code TestMetadataPropagationIntegration} 的既有模式。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
@NopTestProperty(name = "nop.search.index-dir", value = "./target/nop-meta-e2e-search")
public class TestNopMetaEntityEndToEnd extends JunitBaseTestCase {

    @Inject
    IGraphQLEngine graphQLEngine;

    @Inject
    IDaoProvider daoProvider;

    @Inject
    INopMetaEntityBiz nopMetaEntityBizModel;

    @Inject
    NopMetaSearchProcessor metaSearchProcessor;

    @Inject
    ISearchEngine searchEngine;

    @Inject
    io.nop.orm.IOrmTemplate ormTemplate;

    IServiceContext svcCtx = new ServiceContextImpl();

    private static final String QUERY_SPACE = "qs_e2e_full_chain";
    private static final String DB_URL = "jdbc:h2:mem:" + QUERY_SPACE + ";DB_CLOSE_DELAY=-1";

    @Test
    @SuppressWarnings("unchecked")
    public void testExternalEntityFullChain() throws Exception {
        AggregationTestHelper helper = new AggregationTestHelper(graphQLEngine, daoProvider, ormTemplate);

        // ===== 步骤 1：注册外部数据源（H2）+ 建两张外部表 =====
        // 默认 schema（H2 为 PUBLIC）：E2E_ORDERS / E2E_REGIONS；另建 APP schema 同名表 E2E_ORDERS
        // （多 schema 场景：验证第二张同表名异 schema 实体的 entityName 生成规则）。
        try (Connection c = DriverManager.getConnection(DB_URL, "sa", "");
             Statement st = c.createStatement()) {
            st.execute("CREATE TABLE E2E_ORDERS (ORDER_ID INT NOT NULL, AMT INT, REGION VARCHAR(20))");
            st.execute("INSERT INTO E2E_ORDERS VALUES (1, 100, 'CN')");
            st.execute("INSERT INTO E2E_ORDERS VALUES (2, 200, 'US')");
            st.execute("CREATE TABLE E2E_REGIONS (ORDER_ID INT NOT NULL, REGION VARCHAR(20))");
            st.execute("INSERT INTO E2E_REGIONS VALUES (1, 'CN')");
            st.execute("INSERT INTO E2E_REGIONS VALUES (2, 'US')");
            st.execute("CREATE SCHEMA APP");
            st.execute("CREATE TABLE APP.E2E_ORDERS (ORDER_ID INT NOT NULL, NOTE VARCHAR(50))");
            st.execute("INSERT INTO APP.E2E_ORDERS VALUES (1, 'app-row')");
        }
        String dataSourceId = "ds-" + QUERY_SPACE;
        helper.saveDataSource(dataSourceId, QUERY_SPACE, "jdbc", "ACTIVE", DB_URL);

        // ===== 步骤 2：syncExternalTables 产出 EXTERNAL 实体行 =====
        helper.syncExternalTables(dataSourceId);
        syncSchema(dataSourceId, "APP");

        NopMetaEntity orders = findExternalEntity("PUBLIC_E2E_ORDERS");
        assertEquals("E2E_ORDERS", orders.getTableName(), "tableName must keep the physical table name");
        assertEquals("EXTERNAL", orders.getEntityKind(), "synced entity must be EXTERNAL kind");
        assertNotNull(orders.getExternalColumns(), "externalColumns must be non-null after sync");
        assertFalse(orders.getExternalColumns().trim().isEmpty(),
                "externalColumns must carry the parsed column structure");
        assertEquals("PUBLIC", orders.getDbSchema(), "dbSchema must describe the source schema");
        assertNotNull(orders.getQuerySpace(), "querySpace must be wired to the datasource");

        NopMetaEntity regions = findExternalEntity("PUBLIC_E2E_REGIONS");
        assertEquals("EXTERNAL", regions.getEntityKind(), "second synced entity must be EXTERNAL kind");

        // 多 schema 同表名：第二张 APP.E2E_ORDERS 按 {schema}_{tableName} 生成 entityName，
        // 与 PUBLIC 侧同表名实体共存（dbSchema 作描述列，不进身份）
        NopMetaEntity appOrders = findExternalEntity("APP_E2E_ORDERS");
        assertEquals("E2E_ORDERS", appOrders.getTableName(), "same physical table name across schemas");
        assertEquals("APP", appOrders.getDbSchema(), "APP row must carry its own dbSchema");
        assertFalse(appOrders.getMetaEntityId().equals(orders.getMetaEntityId()),
                "same-name tables from different schemas must be distinct entities");

        // ===== 步骤 3：resolveEntityFields 返回字段非空 =====
        ResolveEntityFieldsResultDTO fieldsResult =
                nopMetaEntityBizModel.resolveEntityFields(orders.getMetaEntityId(), svcCtx);
        assertEquals("EXTERNAL", fieldsResult.getEntityKind(), "resolveEntityFields must report EXTERNAL kind");
        assertFalse(fieldsResult.getFields().isEmpty(), "externalColumns must resolve to fields");
        assertTrue(fieldsResult.getFields().stream().anyMatch(f -> "ORDER_ID".equalsIgnoreCase(f.getName())),
                "ORDER_ID column must be resolved from externalColumns: " + fieldsResult.getFields());
        assertTrue(fieldsResult.getFields().stream().allMatch(f -> "external".equals(f.getSourceType())),
                "external fields must carry sourceType=external: " + fieldsResult.getFields());

        // ===== 步骤 4：queryData 返回数据行 =====
        QueryEntityDataResultDTO dataResult =
                nopMetaEntityBizModel.queryData(orders.getMetaEntityId(), null, null, null, null, svcCtx);
        assertEquals("EXTERNAL", dataResult.getEntityKind());
        List<Map<String, Object>> items = dataResult.getItems();
        assertEquals(2, items.size(), "queryData must return the 2 seeded rows: " + items);
        assertTrue(items.stream().anyMatch(r -> intOf(getIgnoreCase(r, "AMT")) == 100),
                "row with AMT=100 must be returned (real data, not stub): " + items);

        // ===== 步骤 5：queryAggregation 单实体聚合 =====
        helper.createMeasure(orders.getMetaEntityId(), "cnt", "ORDER_ID", "count", null);
        helper.createDimension(orders.getMetaEntityId(), "rg", "REGION", "categorical", null);
        List<Map<String, Object>> aggItems = queryAggregationItems(orders.getMetaEntityId(),
                Arrays.asList("cnt"), Arrays.asList("rg"));
        assertEquals(2, aggItems.size(), "GROUP BY REGION must yield CN/US buckets: " + aggItems);
        for (Map<String, Object> bucket : aggItems) {
            assertEquals(1L, longOf(bucket.get("CNT")),
                    "each region bucket must count exactly 1 order: " + aggItems);
        }

        // ===== 步骤 6：NopMetaEntityJoin（两外部实体端点）+ queryJoinData =====
        String joinId = helper.createJoin(orders.getMetaEntityId(), "inner",
                orders.getMetaEntityId(), regions.getMetaEntityId(),
                "ORDER_ID", "ORDER_ID", "rg");
        QueryJoinDataResultDTO joinResult =
                nopMetaEntityBizModel.queryJoinData(orders.getMetaEntityId(), joinId, null, null, null, null, svcCtx);
        List<Map<String, Object>> joinItems = joinResult.getItems();
        assertEquals(2, joinItems.size(), "inner join on ORDER_ID must return 2 associated rows: " + joinItems);
        for (Map<String, Object> row : joinItems) {
            Object leftKey = getIgnoreCase(row, "ORDER_ID");
            Object rightKey = getIgnoreCase(row, "RG_ORDER_ID");
            assertNotNull(leftKey, "left join key ORDER_ID must be present: " + row.keySet());
            assertNotNull(rightKey, "right join key must be alias-prefixed (rg_order_id): " + row.keySet());
            assertEquals(String.valueOf(leftKey), String.valueOf(rightKey),
                    "JOIN ON t1.ORDER_ID = t2.ORDER_ID must produce equal key values");
        }

        // ===== 步骤 7：TagLabel 标注实体（entityType=NopMetaEntity）=====
        String labelId = "e2e-label-" + System.nanoTime();
        saveTagLabel(labelId, orders.getMetaEntityId());
        IEntityDao<NopMetaTagLabel> labelDao = daoProvider.daoFor(NopMetaTagLabel.class);
        QueryBean lq = new QueryBean();
        lq.addFilter(io.nop.api.core.beans.FilterBeans.eq(
                NopMetaTagLabel.PROP_NAME_entityId, orders.getMetaEntityId()));
        lq.addFilter(io.nop.api.core.beans.FilterBeans.eq(
                NopMetaTagLabel.PROP_NAME_entityType, "NopMetaEntity"));
        List<NopMetaTagLabel> labels = labelDao.findAllByQuery(lq);
        assertEquals(1, labels.size(), "exactly one TagLabel must be attached to the entity");
        assertEquals(labelId, labels.get(0).getTagLabelId(), "the saved TagLabel must be the one queried back");
        assertEquals("Manual", labels.get(0).getLabelType(), "labelType must round-trip");

        // ===== 步骤 8：NopMetaLineageEdge 记录血缘边 =====
        String edgeId = "e2e-edge-" + System.nanoTime();
        IEntityDao<NopMetaLineageEdge> edgeDao = daoProvider.daoFor(NopMetaLineageEdge.class);
        NopMetaLineageEdge edge = edgeDao.newEntity();
        edge.setLineageEdgeId(edgeId);
        edge.setSourceEntityId(orders.getMetaEntityId());
        edge.setTargetEntityId(regions.getMetaEntityId());
        edge.setTransformType("DIRECT");
        edgeDao.saveEntity(edge);

        IEntityDao<NopMetaLineageEdge> edgeDao2 = daoProvider.daoFor(NopMetaLineageEdge.class);
        QueryBean eq = new QueryBean();
        eq.addFilter(io.nop.api.core.beans.FilterBeans.eq(
                NopMetaLineageEdge.PROP_NAME_sourceEntityId, orders.getMetaEntityId()));
        eq.addFilter(io.nop.api.core.beans.FilterBeans.eq(
                NopMetaLineageEdge.PROP_NAME_targetEntityId, regions.getMetaEntityId()));
        List<NopMetaLineageEdge> edges = edgeDao2.findAllByQuery(eq);
        assertEquals(1, edges.size(), "the lineage edge must be persisted and queryable");
        assertEquals("DIRECT", edges.get(0).getTransformType(), "transformType must round-trip");

        // ===== 步骤 9：searchService addToIndex 后按新短名检索命中 =====
        // 用唯一 remark token 规避跨 run 索引残留；toSearchableDoc 将 remark 并入可检索内容
        String uniqueToken = "e2euniq" + System.nanoTime();
        // 直更 + evict（沿 updateQuerySpaceSql 先例：测试上下文无打开 OrmSession）
        io.nop.core.lang.sql.SQL upd = io.nop.core.lang.sql.SQL.begin().allowUnderscoreName(true)
                .sql("update NOP_META_ENTITY set REMARK=? where META_ENTITY_ID=?",
                        uniqueToken, orders.getMetaEntityId())
                .end();
        ormTemplate.executeUpdate(upd);
        ormTemplate.evictAll(NopMetaEntity.class.getName());
        NopMetaEntity forIndex = daoProvider.daoFor(NopMetaEntity.class).getEntityById(orders.getMetaEntityId());
        assertEquals(uniqueToken, forIndex.getRemark(), "remark update must be visible before indexing");

        metaSearchProcessor.addToIndex("MetaEntity", orders.getMetaEntityId(),
                NopMetadataHelper.toSearchableDoc(forIndex));
        searchEngine.refreshBlocking(NopMetaSearchProcessor.TOPIC);

        SearchRequest request = new SearchRequest();
        request.setTopic(NopMetaSearchProcessor.TOPIC);
        request.setQuery(uniqueToken);
        request.setLimit(10);
        SearchResponse response = searchEngine.search(request);
        List<SearchHit> hits = response.getItems();
        assertNotNull(hits, "search response must carry hits");
        assertTrue(hits.stream().anyMatch(h -> orders.getMetaEntityId().equals(h.getId())),
                "search by unique token must hit the indexed entity itself, hits="
                        + Arrays.toString(hits.toArray()));
    }

    // ===== helpers =====

    /** 同步指定 schema（沿 AggregationTestHelper.syncExternalTables 的 mutation 形态）。 */
    private void syncSchema(String dataSourceId, String schemaPattern) {
        io.nop.api.core.beans.graphql.GraphQLRequestBean request = new io.nop.api.core.beans.graphql.GraphQLRequestBean();
        request.setQuery("mutation { NopMetaDataSource__syncExternalTables(dataSourceId: \"" + dataSourceId
                + "\", schemaPattern: \"" + schemaPattern + "\") { syncedTableCount errors { code message detail } } }");
        io.nop.api.core.beans.graphql.GraphQLResponseBean resp =
                graphQLEngine.executeGraphQL(graphQLEngine.newGraphQLContext(request));
        Assertions.assertFalse(resp.hasError(), "schema sync should not error: " + resp);
    }

    /** 按 entityName 查找 EXTERNAL 实体行（多 schema 场景下 tableName 有歧义，按实体名精确定位）。 */
    private NopMetaEntity findExternalEntity(String entityName) {
        IEntityDao<NopMetaEntity> dao = daoProvider.daoFor(NopMetaEntity.class);
        QueryBean q = new QueryBean();
        q.addFilter(io.nop.api.core.beans.FilterBeans.eq(NopMetaEntity.PROP_NAME_entityName, entityName));
        q.addFilter(io.nop.api.core.beans.FilterBeans.eq("entityKind", "EXTERNAL"));
        NopMetaEntity t = dao.findFirstByQuery(q);
        assertNotNull(t, "EXTERNAL entity " + entityName + " must exist after sync");
        return t;
    }

    private void saveTagLabel(String labelId, String entityId) {
        IEntityDao<NopMetaTagLabel> dao = daoProvider.daoFor(NopMetaTagLabel.class);
        NopMetaTagLabel label = dao.newEntity();
        label.setTagLabelId(labelId);
        label.setSource("Manual");
        label.setTagId("e2e-tag");
        label.setLabelType("Manual");
        label.setState("Confirmed");
        label.setEntityType("NopMetaEntity");
        label.setEntityId(entityId);
        label.setVersion(1L);
        label.setCreatedBy("autotest");
        label.setUpdatedBy("autotest");
        dao.saveEntity(label);
    }

    /** 调 NopMetaEntity__queryAggregation RPC 并返回 items（沿 TestAggregationCategoricalAndTemporal 模式）。 */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> queryAggregationItems(String tableId, List<String> measures,
                                                            List<String> dimensions) {
        java.util.LinkedHashMap<String, Object> params = new java.util.LinkedHashMap<>();
        params.put("metaEntityId", tableId);
        params.put("measures", measures);
        params.put("dimensions", dimensions);
        ApiResponse<?> resp = executeRpc(GraphQLOperationType.query,
                "NopMetaEntity__queryAggregation", ApiRequest.build(params));
        if (!resp.isOk()) {
            throw new IllegalStateException("queryAggregation failed: " + resp);
        }
        // RPC 返回 data 为 Map（非强类型 DTO），与 TestAggregationCategoricalAndTemporal 一致
        Map<String, Object> data = (Map<String, Object>) resp.getData();
        return (List<Map<String, Object>>) data.get("items");
    }

    private <T> ApiResponse<T> executeRpc(GraphQLOperationType opType, String action, ApiRequest<?> request) {
        IGraphQLExecutionContext ctx = graphQLEngine.newRpcContext(opType, action, request);
        return (ApiResponse<T>) graphQLEngine.executeRpc(ctx);
    }

    private static Object getIgnoreCase(Map<String, Object> row, String key) {
        for (Map.Entry<String, Object> e : row.entrySet()) {
            if (e.getKey().equalsIgnoreCase(key)) {
                return e.getValue();
            }
        }
        return null;
    }

    private static int intOf(Object v) {
        return ((Number) v).intValue();
    }

    private static long longOf(Object v) {
        return ((Number) v).longValue();
    }
}
