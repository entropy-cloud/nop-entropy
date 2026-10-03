package io.nop.biz.crud;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.ast.GraphQLOperationType;
import io.nop.graphql.core.engine.IGraphQLEngine;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 通过 {@link IGraphQLEngine}（newRpcContext + executeRpc）验证 nop-biz
 * CrudBizModel 的 CRUD / findPage / 批量保存语义。
 * <p>
 * 全部用例走 GraphQL 引擎完整管道（schema 暴露校验、ORM session、事务、上下文注入），
 * 不直接调用 bizObj.method（testing.md 禁令）。
 * <p>
 * 测试实体 test.TestIndex 定义于测试资源 _vfs/nop/test/orm/app.orm.xml，
 * 业务对象模型 TestIndex.xbiz(graphql:base="crud") + TestIndex.xmeta 位于
 * _vfs/nop/test/model/TestIndex/，由 BizObjectManager 的模块模型扫描自动注册。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestGraphQLCrudSemantics extends JunitBaseTestCase {

    @Inject
    IGraphQLEngine graphQLEngine;

    // ==================== wiring sanity ====================

    /**
     * 引擎可注入，且 GraphQL schema 中已暴露 TestIndex 的 CRUD 操作
     * （newRpcContext 能为该操作构建执行上下文）。
     */
    @Test
    public void testWiringSanity_engineInjectedAndOperationExposed() {
        assertNotNull(graphQLEngine, "IGraphQLEngine must be injected in nop-biz test container");

        IGraphQLExecutionContext context = graphQLEngine.newRpcContext(
                GraphQLOperationType.query, "TestIndex__get", apiRequest(Map.of("id", "no-such")));
        assertNotNull(context, "newRpcContext must build an execution context for exposed operation");
    }

    // ==================== save / get / update ====================

    /**
     * save 返回保存后的实体：主键与字段值回填正确（显式主键写入）。
     */
    @Test
    public void testSaveReturnsPersistedFields() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sid", "gs-save-1");
        data.put("name", "idx-save");
        data.put("unit", "kg");
        data.put("value", 3);

        ApiResponse<?> response = executeRpc(GraphQLOperationType.mutation, "TestIndex__save",
                Map.of("data", data));

        assertEquals(0, response.getStatus(), "save should succeed, got: " + response);
        Map<String, Object> saved = dataMap(response);
        assertEquals("gs-save-1", saved.get("sid"), "save result must carry the primary key");
        assertEquals("idx-save", saved.get("name"));
        assertEquals(3, ((Number) saved.get("value")).intValue());
    }

    /**
     * get 读取已保存实体，字段与保存值一致（读回链路：GraphQL selection → ORM fetcher）。
     */
    @Test
    public void testGetReturnsSavedEntityFields() {
        saveIndex("gs-get-1", "idx-get", "m", 7);

        ApiResponse<?> response = executeRpc(GraphQLOperationType.query, "TestIndex__get",
                Map.of("id", "gs-get-1"));

        assertEquals(0, response.getStatus(), "get should succeed, got: " + response);
        Map<String, Object> loaded = dataMap(response);
        assertEquals("gs-get-1", loaded.get("sid"));
        assertEquals("idx-get", loaded.get("name"));
        assertEquals("m", loaded.get("unit"));
        assertEquals(7, ((Number) loaded.get("value")).intValue());
    }

    /**
     * update 只修改提供字段：value 更新为新值，未提供的 name 保持不变。
     */
    @Test
    public void testUpdateChangesOnlyProvidedFields() {
        saveIndex("gs-upd-1", "idx-upd", null, 1);

        // TestIndex 主键属性名为 sid：data 必须携带 sid 而非固定 "id" 键（plan 2306 项 22）
        ApiResponse<?> update = executeRpc(GraphQLOperationType.mutation, "TestIndex__update",
                Map.of("data", Map.of("sid", "gs-upd-1", "value", 42)));
        assertEquals(0, update.getStatus(), "update should succeed, got: " + update);

        ApiResponse<?> get = executeRpc(GraphQLOperationType.query, "TestIndex__get",
                Map.of("id", "gs-upd-1"));
        Map<String, Object> loaded = dataMap(get);
        assertEquals("idx-upd", loaded.get("name"), "unmodified field must keep its value");
        assertEquals(42, ((Number) loaded.get("value")).intValue(), "modified field must be updated");
    }

    /**
     * update 不存在的主键必须报错（status != 0），不允许静默成功。
     */
    @Test
    public void testUpdateUnknownIdFails() {
        ApiResponse<?> response = executeRpc(GraphQLOperationType.mutation, "TestIndex__update",
                Map.of("data", Map.of("sid", "gs-missing", "value", 1)));

        assertNotEquals(0, response.getStatus(),
                "update on unknown id must fail, got: " + response);
    }

    /**
     * saveOrUpdate：无记录时插入，再次调用同一主键时更新（而非新增第二条）。
     * 回归覆盖 wi8#2（plan 2306 项 22）：插入/更新的判定依据是实体真实主键属性名 sid。
     * 修复前两次调用必须分别携带 sid/id 两种键——带 sid 的第二次调用会被误判为插入，
     * 最终以 duplicate-key 报错；修复后统一携带 sid 即可完成 upsert。
     */
    @Test
    public void testSaveOrUpdateInsertsThenUpdates() {
        ApiResponse<?> insert = executeRpc(GraphQLOperationType.mutation, "TestIndex__saveOrUpdate",
                Map.of("data", Map.of("sid", "gs-upsert-1", "name", "idx-upsert", "value", 1)));
        assertEquals(0, insert.getStatus(), "saveOrUpdate insert should succeed, got: " + insert);

        ApiResponse<?> upsert = executeRpc(GraphQLOperationType.mutation, "TestIndex__saveOrUpdate",
                Map.of("data", Map.of("sid", "gs-upsert-1", "name", "idx-upsert", "value", 5)));
        assertEquals(0, upsert.getStatus(), "saveOrUpdate update should succeed, got: " + upsert);

        assertEquals(1, countBy("name", "idx-upsert"),
                "saveOrUpdate with same id must not create a second row");
        ApiResponse<?> get = executeRpc(GraphQLOperationType.query, "TestIndex__get",
                Map.of("id", "gs-upsert-1"));
        assertEquals(5, ((Number) dataMap(get).get("value")).intValue(),
                "saveOrUpdate must persist the new value");
    }

    // ==================== findPage / findCount / findFirst / findList ====================

    /**
     * findPage 带 eq 过滤：只返回匹配行，total 反映过滤后的行数。
     */
    @Test
    public void testFindPageFilterReturnsMatchingRowsOnly() {
        saveIndex("gs-fp-a", "idx-fp-a", "u1", 1);
        saveIndex("gs-fp-b", "idx-fp-b", "u2", 2);
        saveIndex("gs-fp-c", "idx-fp-c", "u1", 3);

        QueryBean query = new QueryBean().addFilterCondition("unit", "eq", "u1");
        ApiResponse<?> response = executeRpc(GraphQLOperationType.query, "TestIndex__findPage",
                Map.of("query", query));

        assertEquals(0, response.getStatus(), "findPage should succeed, got: " + response);
        List<Map<String, Object>> items = pageItems(response);
        assertEquals(2, items.size(), "filter unit=u1 must return exactly 2 rows");
        assertEquals(2L, pageTotal(response), "total must reflect the filtered row count");
        for (Map<String, Object> item : items) {
            assertEquals("u1", item.get("unit"), "every returned row must satisfy the filter");
            assertNotEquals("gs-fp-b", item.get("sid"), "non-matching row must not be returned");
        }
    }

    /**
     * findPage 分页：limit/offset 截取正确的窗口。
     */
    @Test
    public void testFindPageLimitAndOffsetWindow() {
        saveIndex("gs-pg-1", "idx-pg", null, 1);
        saveIndex("gs-pg-2", "idx-pg", null, 2);
        saveIndex("gs-pg-3", "idx-pg", null, 3);

        QueryBean query = new QueryBean()
                .addFilterCondition("name", "eq", "idx-pg")
                .addOrderField("sid", false)
                .limit(2)
                .offset(1);
        ApiResponse<?> response = executeRpc(GraphQLOperationType.query, "TestIndex__findPage",
                Map.of("query", query));

        assertEquals(0, response.getStatus(), "findPage should succeed, got: " + response);
        List<Map<String, Object>> items = pageItems(response);
        assertEquals(2, items.size(), "limit=2 must return 2 rows");
        assertEquals(3L, pageTotal(response), "total must ignore limit and reflect all matches");
        assertEquals("gs-pg-2", items.get(0).get("sid"), "offset=1 must skip the first row");
        assertEquals("gs-pg-3", items.get(1).get("sid"));
    }

    /**
     * findCount 返回过滤后的行数（long 语义）。
     */
    @Test
    public void testFindCountReflectsFilter() {
        saveIndex("gs-ct-1", "idx-ct", "cpu", 1);
        saveIndex("gs-ct-2", "idx-ct", "mem", 2);

        QueryBean query = new QueryBean().addFilterCondition("name", "eq", "idx-ct");
        ApiResponse<?> all = executeRpc(GraphQLOperationType.query, "TestIndex__findCount",
                Map.of("query", query));
        assertEquals(0, all.getStatus(), "findCount should succeed, got: " + all);
        assertEquals(2L, ((Number) all.getData()).longValue(), "count without unit filter must be 2");

        QueryBean unitFilter = new QueryBean()
                .addFilterCondition("name", "eq", "idx-ct")
                .addFilterCondition("unit", "eq", "cpu");
        ApiResponse<?> filtered = executeRpc(GraphQLOperationType.query, "TestIndex__findCount",
                Map.of("query", unitFilter));
        assertEquals(1L, ((Number) filtered.getData()).longValue(),
                "count with unit=cpu filter must be 1");
    }

    /**
     * findFirst 返回满足过滤条件的单条记录。
     */
    @Test
    public void testFindFirstReturnsSingleMatch() {
        saveIndex("gs-ff-1", "idx-ff", "s1", 1);
        saveIndex("gs-ff-2", "idx-ff", "s2", 2);

        QueryBean query = new QueryBean()
                .addFilterCondition("unit", "eq", "s2")
                .addFilterCondition("name", "eq", "idx-ff");
        ApiResponse<?> response = executeRpc(GraphQLOperationType.query, "TestIndex__findFirst",
                Map.of("query", query));

        assertEquals(0, response.getStatus(), "findFirst should succeed, got: " + response);
        Map<String, Object> row = dataMap(response);
        assertEquals("gs-ff-2", row.get("sid"), "findFirst must return the row matching unit=s2");
    }

    /**
     * findList 返回匹配集合（非分页），行数与过滤条件一致。
     */
    @Test
    public void testFindListReturnsAllMatches() {
        saveIndex("gs-fl-1", "idx-fl", "list-unit", 1);
        saveIndex("gs-fl-2", "idx-fl", "other-unit", 2);
        saveIndex("gs-fl-3", "idx-fl", "list-unit", 3);

        QueryBean query = new QueryBean().addFilterCondition("unit", "eq", "list-unit");
        ApiResponse<?> response = executeRpc(GraphQLOperationType.query, "TestIndex__findList",
                Map.of("query", query));

        assertEquals(0, response.getStatus(), "findList should succeed, got: " + response);
        List<Map<String, Object>> rows = listRows(response);
        assertEquals(2, rows.size(), "findList must return exactly the 2 matching rows");
        Set<Object> ids = rows.stream().map(r -> r.get("sid")).collect(Collectors.toSet());
        assertTrue(ids.contains("gs-fl-1") && ids.contains("gs-fl-3"),
                "findList rows must be the matching ones, got: " + ids);
    }

    // ==================== 批量语义 ====================

    /**
     * batchGet 按主键集合返回已存在的实体。
     */
    @Test
    public void testBatchGetReturnsExistingEntities() {
        saveIndex("gs-bg-1", "idx-bg", null, 1);
        saveIndex("gs-bg-2", "idx-bg", null, 2);

        ApiResponse<?> response = executeRpc(GraphQLOperationType.query, "TestIndex__batchGet",
                Map.of("ids", Arrays.asList("gs-bg-1", "gs-bg-2")));

        assertEquals(0, response.getStatus(), "batchGet should succeed, got: " + response);
        List<Map<String, Object>> rows = listRows(response);
        assertEquals(2, rows.size(), "batchGet must return both existing rows");
        Set<Object> ids = rows.stream().map(r -> r.get("sid")).collect(Collectors.toSet());
        assertEquals(Set.of("gs-bg-1", "gs-bg-2"), ids);
    }

    /**
     * batchUpdate 将同一字段值应用到多个主键。
     */
    @Test
    public void testBatchUpdateAppliesValueToAllIds() {
        saveIndex("gs-bu-1", "idx-bu", "old", 1);
        saveIndex("gs-bu-2", "idx-bu", "old", 2);

        ApiResponse<?> response = executeRpc(GraphQLOperationType.mutation, "TestIndex__batchUpdate",
                Map.of("ids", Set.of("gs-bu-1", "gs-bu-2"), "data", Map.of("unit", "new")));
        assertEquals(0, response.getStatus(), "batchUpdate should succeed, got: " + response);

        assertEquals(2L, countBy("unit", "new"), "both rows must carry the new unit value");
        assertEquals(0L, countBy("unit", "old"), "no row may keep the old unit value");
    }

    /**
     * batchModify（批量保存）：一次调用内完成插入 + 更新 + 删除三类变更。
     */
    @Test
    public void testBatchModifyMixesInsertUpdateDelete() {
        saveIndex("gs-bm-1", "idx-bm", null, 1);
        saveIndex("gs-bm-2", "idx-bm", null, 2);

        List<Map<String, Object>> data = Arrays.asList(
                new LinkedHashMap<>(Map.of("sid", "gs-bm-new", "name", "idx-bm", "value", 30)),
                new LinkedHashMap<>(Map.of("sid", "gs-bm-1", "value", 99)));

        ApiResponse<?> response = executeRpc(GraphQLOperationType.mutation, "TestIndex__batchModify",
                Map.of("data", data, "delIds", Set.of("gs-bm-2")));
        assertEquals(0, response.getStatus(), "batchModify should succeed, got: " + response);

        assertEquals(99, ((Number) dataMap(executeRpc(GraphQLOperationType.query, "TestIndex__get",
                Map.of("id", "gs-bm-1"))).get("value")).intValue(),
                "batchModify must apply the update branch to the row with id");
        assertEquals(0L, countByFilter("sid", "eq", "gs-bm-2"),
                "batchModify must delete the row listed in delIds");
        assertEquals(30, ((Number) dataMap(executeRpc(GraphQLOperationType.query, "TestIndex__get",
                Map.of("id", "gs-bm-new"))).get("value")).intValue(),
                "batchModify must insert the row without id");
    }

    /**
     * batchDelete 删除存在的行；缺失的主键在返回集合中报告（而非静默丢弃）。
     */
    @Test
    public void testBatchDeleteRemovesAndReportsMissing() {
        saveIndex("gs-bd-1", "idx-bd", null, 1);
        saveIndex("gs-bd-2", "idx-bd", null, 2);

        ApiResponse<?> response = executeRpc(GraphQLOperationType.mutation, "TestIndex__batchDelete",
                Map.of("ids", Set.of("gs-bd-1", "gs-bd-nope")));
        assertEquals(0, response.getStatus(), "batchDelete should succeed, got: " + response);

        @SuppressWarnings("unchecked")
        Set<Object> notDeleted = (Set<Object>) response.getData();
        assertNotNull(notDeleted);
        assertTrue(notDeleted.contains("gs-bd-nope"),
                "missing id must be reported in the return set, got: " + notDeleted);
        assertFalse(notDeleted.contains("gs-bd-1"), "deleted id must not be reported as missing");

        assertEquals(1L, countBy("name", "idx-bd"), "only the existing row must be deleted");
    }

    // ==================== delete ====================

    /**
     * delete 删除单条记录并返回 true；行数随之减少。
     */
    @Test
    public void testDeleteRemovesSingleRow() {
        saveIndex("gs-del-1", "idx-del", null, 1);
        assertEquals(1L, countBy("name", "idx-del"));

        ApiResponse<?> response = executeRpc(GraphQLOperationType.mutation, "TestIndex__delete",
                Map.of("id", "gs-del-1"));
        assertEquals(0, response.getStatus(), "delete should succeed, got: " + response);
        assertEquals(Boolean.TRUE, response.getData(), "delete must report success");

        assertEquals(0L, countBy("name", "idx-del"), "deleted row must disappear from counts");
    }

    // ==================== helpers ====================

    private ApiResponse<?> executeRpc(GraphQLOperationType opType, String action, Map<String, Object> data) {
        return graphQLEngine.executeRpc(
                graphQLEngine.newRpcContext(opType, action, apiRequest(data)));
    }

    private static ApiRequest<Map<String, Object>> apiRequest(Map<String, Object> data) {
        ApiRequest<Map<String, Object>> request = new ApiRequest<>();
        request.setData(new HashMap<>(data));
        return request;
    }

    private Map<String, Object> dataMap(ApiResponse<?> response) {
        assertEquals(0, response.getStatus(), "expected successful response, got: " + response);
        assertNotNull(response.getData(), "response data must not be null");
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.getData();
        return data;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> pageItems(ApiResponse<?> response) {
        Map<String, Object> data = dataMap(response);
        return (List<Map<String, Object>>) data.get("items");
    }

    private long pageTotal(ApiResponse<?> response) {
        Object total = dataMap(response).get("total");
        return total == null ? -1L : ((Number) total).longValue();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> listRows(ApiResponse<?> response) {
        Object data = response.getData();
        if (data instanceof List)
            return (List<Map<String, Object>>) data;
        return pageItems(response);
    }

    private long countBy(String prop, Object value) {
        return countByFilter(prop, "eq", value);
    }

    private long countByFilter(String prop, String op, Object value) {
        QueryBean query = new QueryBean().addFilterCondition(prop, op, value);
        ApiResponse<?> response = executeRpc(GraphQLOperationType.query, "TestIndex__findCount",
                Map.of("query", query));
        assertEquals(0, response.getStatus(), "findCount should succeed, got: " + response);
        return ((Number) response.getData()).longValue();
    }

    private void saveIndex(String sid, String name, String unit, Integer value) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sid", sid);
        data.put("name", name);
        if (unit != null)
            data.put("unit", unit);
        data.put("value", value);

        ApiResponse<?> response = executeRpc(GraphQLOperationType.mutation, "TestIndex__save",
                Map.of("data", data));
        assertEquals(0, response.getStatus(), "seed save should succeed, got: " + response);
    }
}
