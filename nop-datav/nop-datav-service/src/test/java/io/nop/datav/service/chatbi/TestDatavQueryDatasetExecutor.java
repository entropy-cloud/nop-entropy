package io.nop.datav.service.entity;

import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.api.IDaoProvider;
import io.nop.datav.service.chatbi.ChatBiToolExecuteContext;
import io.nop.datav.service.chatbi.DatavQueryDatasetExecutor;
import io.nop.report.dao.entity.NopReportDataset;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link DatavQueryDatasetExecutor} 单元测试（D6-1 Phase 4）。
 *
 * <p>验证 query executor 的核心查询语义（design doc §4 数据流）：LLM params Map 直接作为
 * {@code PanelSqlBuilder.build} 的 params 参数（不复用 PanelParamEvaluator），经参数化绑定执行查询。</p>
 *
 * <p>Anti-Hollow：断言 executor 返回的 columns + rows 为真实 DB 查询结果（非 stub/placeholder）。</p>
 */
public class TestDatavQueryDatasetExecutor extends AbstractNopDatavTest {

    @Inject
    IDaoProvider daoProvider;

    @Test
    public void testQueryDatasetReturnsRealData() {
        createSalesTable();
        insertSalesRow("north", "widget", 100);
        insertSalesRow("north", "gadget", 200);
        insertSalesRow("south", "widget", 50);

        NopReportDataset ds = newReportDataset("ds-test-query", "sql",
                "select REGION as region, PRODUCT as product, AMOUNT as amount "
                        + "from TEST_DATAV_SALES where REGION = ${region} order by AMOUNT desc");
        daoProvider.daoFor(NopReportDataset.class).saveEntityDirectly(ds);

        DatavQueryDatasetExecutor executor = new DatavQueryDatasetExecutor();
        executor.setDaoProvider(daoProvider);
        executor.setJdbcTemplate(jdbcTemplate);

        AiToolCall call = new AiToolCall();
        call.setToolName(DatavQueryDatasetExecutor.TOOL_NAME);
        call.setInput(JsonTool.stringify(Map.of("datasetSid", "ds-test-query", "params", Map.of("region", "north"))));

        AiToolCallResult result = executor.executeAsync(call, new ChatBiToolExecuteContext(null, "test", false)).toCompletableFuture().join();

        assertEquals("success", result.getStatus());
        assertNotNull(result.getOutput());
        assertNotNull(result.getOutput().getBody());

        @SuppressWarnings("unchecked")
        Map<String, Object> parsed = (Map<String, Object>) JsonTool.parseNonStrict(result.getOutput().getBody());
        assertNotNull(parsed.get("columns"));
        assertNotNull(parsed.get("rows"));

        @SuppressWarnings("unchecked")
        java.util.List<String> columns = (java.util.List<String>) parsed.get("columns");
        assertEquals(3, columns.size());
        assertTrue(containsIgnoreCase(columns, "region"));
        assertTrue(containsIgnoreCase(columns, "amount"));

        @SuppressWarnings("unchecked")
        java.util.List<Map<String, Object>> rows = (java.util.List<Map<String, Object>>) parsed.get("rows");
        assertEquals(2, rows.size(), "north has 2 rows");
        // ordered desc: first row amount=200
        Object firstAmount = findCaseInsensitive(rows.get(0), "amount");
        assertEquals(200, ((Number) firstAmount).intValue());
    }

    @Test
    public void testQueryDatasetNotFoundReturnsExplicitError() {
        DatavQueryDatasetExecutor executor = new DatavQueryDatasetExecutor();
        executor.setDaoProvider(daoProvider);
        executor.setJdbcTemplate(jdbcTemplate);

        AiToolCall call = new AiToolCall();
        call.setToolName(DatavQueryDatasetExecutor.TOOL_NAME);
        call.setInput(JsonTool.stringify(Map.of("datasetSid", "nonexistent-ds")));

        AiToolCallResult result = executor.executeAsync(call, new ChatBiToolExecuteContext(null, "test", false)).toCompletableFuture().join();

        // 无静默跳过：数据集不存在时返回显式错误（status=failure）
        assertEquals("failure", result.getStatus(), "dataset-not-found must return explicit error");
        assertNotNull(result.getError());
        assertNotNull(result.getError().getBody());
        assertTrue(result.getError().getBody().contains("not found"));
    }

    @Test
    public void testQueryDatasetMaxRowsCap() {
        createSalesTable();
        insertSalesRow("east", "a", 1);
        insertSalesRow("east", "b", 2);
        insertSalesRow("east", "c", 3);

        NopReportDataset ds = newReportDataset("ds-test-maxrows", "sql",
                "select REGION as region, AMOUNT as amount from TEST_DATAV_SALES where REGION = ${region}");
        daoProvider.daoFor(NopReportDataset.class).saveEntityDirectly(ds);

        DatavQueryDatasetExecutor executor = new DatavQueryDatasetExecutor();
        executor.setDaoProvider(daoProvider);
        executor.setJdbcTemplate(jdbcTemplate);

        AiToolCall call = new AiToolCall();
        call.setToolName(DatavQueryDatasetExecutor.TOOL_NAME);
        call.setInput(JsonTool.stringify(Map.of(
                "datasetSid", "ds-test-maxrows",
                "params", Map.of("region", "east"),
                "maxRows", 2)));

        AiToolCallResult result = executor.executeAsync(call, new ChatBiToolExecuteContext(null, "test", false)).toCompletableFuture().join();

        assertEquals("success", result.getStatus());
        @SuppressWarnings("unchecked")
        Map<String, Object> parsed = (Map<String, Object>) JsonTool.parseNonStrict(result.getOutput().getBody());
        @SuppressWarnings("unchecked")
        java.util.List<?> rows = (java.util.List<?>) parsed.get("rows");
        assertEquals(2, rows.size(), "maxRows=2 caps to 2 rows (3 exist)");
    }

    @Test
    public void testQueryNonSqlDatasetReturnsError() {
        NopReportDataset ds = newReportDataset("ds-test-non-sql", "file",
                "not-a-sql-text");
        daoProvider.daoFor(NopReportDataset.class).saveEntityDirectly(ds);

        DatavQueryDatasetExecutor executor = new DatavQueryDatasetExecutor();
        executor.setDaoProvider(daoProvider);
        executor.setJdbcTemplate(jdbcTemplate);

        AiToolCall call = new AiToolCall();
        call.setToolName(DatavQueryDatasetExecutor.TOOL_NAME);
        call.setInput(JsonTool.stringify(Map.of("datasetSid", "ds-test-non-sql")));

        AiToolCallResult result = executor.executeAsync(call, new ChatBiToolExecuteContext(null, "test", false)).toCompletableFuture().join();

        assertEquals("failure", result.getStatus());
        assertTrue(result.getError().getBody().contains("not a SQL dataset"));
    }

    // ==================== 数据集可见性（P1-03 / 裁定 D4 选项 B，plan 2026-08-15-2146-1） ====================

    /**
     * P1-03: user 对无权限数据集 datav-query-dataset → 显式拒绝（携带
     * ERR_DATAV_CHATBI_DATASET_NO_ACCESS 结构化错误码，非静默空结果）。
     */
    @Test
    public void testQueryInvisibleDatasetReturnsExplicitError() {
        NopReportDataset ds = newReportDataset("ds-vis-bob-query", "sql",
                "select REGION as region from TEST_DATAV_SALES where REGION = ${region}");
        ds.setCreatedBy("bob");
        ds.setUpdatedBy("bob");
        daoProvider.daoFor(NopReportDataset.class).saveEntityDirectly(ds);

        DatavQueryDatasetExecutor executor = new DatavQueryDatasetExecutor();
        executor.setDaoProvider(daoProvider);
        executor.setJdbcTemplate(jdbcTemplate);

        AiToolCall call = new AiToolCall();
        call.setToolName(DatavQueryDatasetExecutor.TOOL_NAME);
        call.setInput(JsonTool.stringify(Map.of("datasetSid", "ds-vis-bob-query",
                "params", Map.of("region", "north"))));

        AiToolCallResult result = executor.executeAsync(call,
                new ChatBiToolExecuteContext(null, "alice", false)).toCompletableFuture().join();

        assertEquals("failure", result.getStatus(), "non-owner query must be rejected");
        assertNotNull(result.getError());
        assertTrue(result.getError().getBody().contains("nop.err.datav.chatbi-dataset-no-access"),
                "rejection must carry the structured no-access errorCode, got: " + result.getError().getBody());
    }

    /**
     * P1-03: admin 全量可见——跨 createdBy 查询照常返回真实数据（回归：无过度限制）。
     */
    @Test
    public void testQueryVisibleForAdminAcrossCreators() {
        createSalesTable();
        insertSalesRow("north", "widget", 100);

        NopReportDataset ds = newReportDataset("ds-vis-admin-query", "sql",
                "select REGION as region from TEST_DATAV_SALES where REGION = ${region}");
        ds.setCreatedBy("bob");
        ds.setUpdatedBy("bob");
        daoProvider.daoFor(NopReportDataset.class).saveEntityDirectly(ds);

        DatavQueryDatasetExecutor executor = new DatavQueryDatasetExecutor();
        executor.setDaoProvider(daoProvider);
        executor.setJdbcTemplate(jdbcTemplate);

        AiToolCall call = new AiToolCall();
        call.setToolName(DatavQueryDatasetExecutor.TOOL_NAME);
        call.setInput(JsonTool.stringify(Map.of("datasetSid", "ds-vis-admin-query",
                "params", Map.of("region", "north"))));

        AiToolCallResult result = executor.executeAsync(call,
                new ChatBiToolExecuteContext(null, "admin-user", true)).toCompletableFuture().join();

        assertEquals("success", result.getStatus(), "admin must query datasets across creators");
        @SuppressWarnings("unchecked")
        Map<String, Object> parsed = (Map<String, Object>) JsonTool.parseNonStrict(result.getOutput().getBody());
        @SuppressWarnings("unchecked")
        java.util.List<?> rows = (java.util.List<?>) parsed.get("rows");
        assertEquals(1, rows.size(), "real DB row returned for admin");
    }

    // ==================== Helpers ====================

    private NopReportDataset newReportDataset(String sid, String dsType, String dsText) {
        long now = System.currentTimeMillis();
        NopReportDataset ds = new NopReportDataset();
        ds.setSid(sid);
        ds.setDsName(sid);
        ds.setIsSingleRow(false);
        ds.setDsType(dsType);
        ds.setDsText(dsText);
        ds.setDsMeta("{}");
        ds.setStatus(1);
        ds.setVersion(0);
        ds.setCreatedBy("test");
        ds.setCreateTime(new Timestamp(now));
        ds.setUpdatedBy("test");
        ds.setUpdateTime(new Timestamp(now));
        return ds;
    }

    private void createSalesTable() {
        jdbcTemplate.executeUpdate(SQL.begin()
                .name("create:TEST_DATAV_SALES")
                .sql("create table TEST_DATAV_SALES(REGION varchar(50), PRODUCT varchar(50), AMOUNT int)")
                .end());
    }

    private void insertSalesRow(String region, String product, int amount) {
        jdbcTemplate.executeUpdate(SQL.begin()
                .name("insert:TEST_DATAV_SALES")
                .sql("insert into TEST_DATAV_SALES(REGION, PRODUCT, AMOUNT) values(")
                .param(region).sql(",").param(product).sql(",").param(amount).sql(")")
                .end());
    }

    private static Object findCaseInsensitive(Map<String, Object> map, String key) {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (key.equalsIgnoreCase(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static boolean containsIgnoreCase(java.util.List<String> list, String key) {
        for (String s : list) {
            if (key.equalsIgnoreCase(s)) {
                return true;
            }
        }
        return false;
    }
}
