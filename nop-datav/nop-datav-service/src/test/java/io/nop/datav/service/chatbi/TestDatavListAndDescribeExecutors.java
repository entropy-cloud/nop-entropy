package io.nop.datav.service.entity;

import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;
import io.nop.core.lang.json.JsonTool;
import io.nop.dao.api.IDaoProvider;
import io.nop.datav.service.chatbi.ChatBiToolExecuteContext;
import io.nop.datav.service.chatbi.DatavDescribeDatasetExecutor;
import io.nop.datav.service.chatbi.DatavListDatasetsExecutor;
import io.nop.report.dao.entity.NopReportDataset;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link DatavListDatasetsExecutor} + {@link DatavDescribeDatasetExecutor} 单元测试。
 *
 * <p>验证 list executor 仅返回 status=活跃 的数据集；describe executor 正确解析 dsMeta/dsConfig；
 * 两者的错误路径（数据集不存在）均返回显式错误（非 null/空静默返回）。</p>
 */
public class TestDatavListAndDescribeExecutors extends AbstractNopDatavTest {

    @Inject
    IDaoProvider daoProvider;

    @Test
    public void testListReturnsOnlyActiveDatasets() {
        daoProvider.daoFor(NopReportDataset.class).saveEntityDirectly(
                newReportDataset("ds-active-1", "sql", "select 1", "First Active", 1));
        daoProvider.daoFor(NopReportDataset.class).saveEntityDirectly(
                newReportDataset("ds-active-2", "sql", "select 2", "Second Active", 1));
        // inactive dataset (status=0) — should NOT be listed
        daoProvider.daoFor(NopReportDataset.class).saveEntityDirectly(
                newReportDataset("ds-inactive", "sql", "select 3", "Inactive", 0));

        DatavListDatasetsExecutor executor = new DatavListDatasetsExecutor();
        executor.setDaoProvider(daoProvider);

        AiToolCall call = new AiToolCall();
        call.setToolName(DatavListDatasetsExecutor.TOOL_NAME);
        call.setInput("{}");

        AiToolCallResult result = executor.executeAsync(call, new ChatBiToolExecuteContext(null, "test", false)).toCompletableFuture().join();

        assertEquals("success", result.getStatus());
        @SuppressWarnings("unchecked")
        Map<String, Object> parsed = (Map<String, Object>) JsonTool.parseNonStrict(result.getOutput().getBody());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> datasets = (List<Map<String, Object>>) parsed.get("datasets");
        assertEquals(2, datasets.size(), "only active datasets (status=1) returned");
        boolean hasInactive = datasets.stream().anyMatch(d -> "ds-inactive".equals(d.get("sid")));
        assertTrue(!hasInactive, "inactive dataset must not appear");
    }

    @Test
    public void testListWithKeywordFilter() {
        daoProvider.daoFor(NopReportDataset.class).saveEntityDirectly(
                newReportDataset("ds-sales-kw", "sql", "select 1", "Regional Sales", 1));
        daoProvider.daoFor(NopReportDataset.class).saveEntityDirectly(
                newReportDataset("ds-orders-kw", "sql", "select 2", "Order Records", 1));

        DatavListDatasetsExecutor executor = new DatavListDatasetsExecutor();
        executor.setDaoProvider(daoProvider);

        AiToolCall call = new AiToolCall();
        call.setToolName(DatavListDatasetsExecutor.TOOL_NAME);
        call.setInput(JsonTool.stringify(Map.of("keyword", "sales")));

        AiToolCallResult result = executor.executeAsync(call, new ChatBiToolExecuteContext(null, "test", false)).toCompletableFuture().join();

        assertEquals("success", result.getStatus());
        @SuppressWarnings("unchecked")
        Map<String, Object> parsed = (Map<String, Object>) JsonTool.parseNonStrict(result.getOutput().getBody());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> datasets = (List<Map<String, Object>>) parsed.get("datasets");
        assertEquals(1, datasets.size(), "keyword filter 'sales' matches 1 dataset");
        assertEquals("ds-sales-kw", datasets.get(0).get("sid"));
    }

    /**
     * #17 AR-7：keyword 过滤与 status 过滤组合——验证 status=1 下推到 SQL 后，
     * 内存 keyword 过滤仍正确工作，且非活跃数据集（status=0）不被加载（即使其名称匹配 keyword）。
     */
    @Test
    public void testListKeywordFilterCombinedWithActiveStatus() {
        daoProvider.daoFor(NopReportDataset.class).saveEntityDirectly(
                newReportDataset("ds-sales-active", "sql", "select 1", "Regional Sales", 1));
        daoProvider.daoFor(NopReportDataset.class).saveEntityDirectly(
                newReportDataset("ds-orders-active", "sql", "select 2", "Order Records", 1));
        // 非活跃数据集（status=0）名称匹配 keyword，但不应被加载
        daoProvider.daoFor(NopReportDataset.class).saveEntityDirectly(
                newReportDataset("ds-sales-inactive", "sql", "select 3", "Historical Sales", 0));

        DatavListDatasetsExecutor executor = new DatavListDatasetsExecutor();
        executor.setDaoProvider(daoProvider);

        AiToolCall call = new AiToolCall();
        call.setToolName(DatavListDatasetsExecutor.TOOL_NAME);
        call.setInput(JsonTool.stringify(Map.of("keyword", "sales")));

        AiToolCallResult result = executor.executeAsync(call, new ChatBiToolExecuteContext(null, "test", false)).toCompletableFuture().join();

        assertEquals("success", result.getStatus());
        @SuppressWarnings("unchecked")
        Map<String, Object> parsed = (Map<String, Object>) JsonTool.parseNonStrict(result.getOutput().getBody());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> datasets = (List<Map<String, Object>>) parsed.get("datasets");
        assertEquals(1, datasets.size(), "keyword 'sales' matches only 1 ACTIVE dataset (inactive excluded)");
        assertEquals("ds-sales-active", datasets.get(0).get("sid"));
        boolean hasInactive = datasets.stream().anyMatch(d -> "ds-sales-inactive".equals(d.get("sid")));
        assertTrue(!hasInactive, "inactive dataset must not be loaded even if name matches keyword");
    }

    @Test
    public void testDescribeReturnsFieldsAndParams() {
        String dsMeta = JsonTool.stringify(Map.of("fields", List.of(
                Map.of("name", "region", "type", "string"),
                Map.of("name", "amount", "type", "number"))));
        String dsConfig = JsonTool.stringify(Map.of("params", List.of(
                Map.of("name", "region", "type", "string", "required", false))));

        NopReportDataset ds = newReportDataset("ds-describe", "sql", "select * from T where R=${region}", "Describe Test", 1);
        ds.setDsMeta(dsMeta);
        ds.setDsConfig(dsConfig);
        daoProvider.daoFor(NopReportDataset.class).saveEntityDirectly(ds);

        DatavDescribeDatasetExecutor executor = new DatavDescribeDatasetExecutor();
        executor.setDaoProvider(daoProvider);

        AiToolCall call = new AiToolCall();
        call.setToolName(DatavDescribeDatasetExecutor.TOOL_NAME);
        call.setInput(JsonTool.stringify(Map.of("datasetSid", "ds-describe")));

        AiToolCallResult result = executor.executeAsync(call, new ChatBiToolExecuteContext(null, "test", false)).toCompletableFuture().join();

        assertEquals("success", result.getStatus());
        @SuppressWarnings("unchecked")
        Map<String, Object> parsed = (Map<String, Object>) JsonTool.parseNonStrict(result.getOutput().getBody());
        assertEquals("ds-describe", parsed.get("sid"));
        assertEquals("sql", parsed.get("dsType"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> fields = (List<Map<String, Object>>) parsed.get("fields");
        assertEquals(2, fields.size(), "dsMeta parsed to 2 fields");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> params = (List<Map<String, Object>>) parsed.get("params");
        assertEquals(1, params.size(), "dsConfig parsed to 1 param");
        assertEquals("region", params.get(0).get("name"));
    }

    @Test
    public void testDescribeNotFoundReturnsExplicitError() {
        DatavDescribeDatasetExecutor executor = new DatavDescribeDatasetExecutor();
        executor.setDaoProvider(daoProvider);

        AiToolCall call = new AiToolCall();
        call.setToolName(DatavDescribeDatasetExecutor.TOOL_NAME);
        call.setInput(JsonTool.stringify(Map.of("datasetSid", "no-such-ds")));

        AiToolCallResult result = executor.executeAsync(call, new ChatBiToolExecuteContext(null, "test", false)).toCompletableFuture().join();

        // 无静默跳过：数据集不存在时返回显式错误
        assertEquals("failure", result.getStatus());
        assertNotNull(result.getError());
        assertTrue(result.getError().getBody().contains("not found"));
    }

    @Test
    public void testDescribeMissingDatasetSidReturnsError() {
        DatavDescribeDatasetExecutor executor = new DatavDescribeDatasetExecutor();
        executor.setDaoProvider(daoProvider);

        AiToolCall call = new AiToolCall();
        call.setToolName(DatavDescribeDatasetExecutor.TOOL_NAME);
        call.setInput("{}");

        AiToolCallResult result = executor.executeAsync(call, new ChatBiToolExecuteContext(null, "test", false)).toCompletableFuture().join();

        assertEquals("failure", result.getStatus());
        assertTrue(result.getError().getBody().contains("datasetSid is required"));
    }

    // ==================== 数据集可见性（P1-03 / 裁定 D4 选项 B，plan 2026-08-15-2146-1） ====================

    /**
     * P1-03: list 枚举按 createdBy/admin 可见性过滤——alice 仅见自己的活跃数据集（bob 的不可见，
     * 静默过滤语义：不泄露存在性）；admin 全量；无身份 fail-closed 返回空。
     */
    @Test
    public void testListFiltersByDatasetVisibility() {
        daoProvider.daoFor(NopReportDataset.class).saveEntityDirectly(
                newReportDataset("ds-vis-alice", "sql", "select 1", "Alice DS", 1, "alice"));
        daoProvider.daoFor(NopReportDataset.class).saveEntityDirectly(
                newReportDataset("ds-vis-bob", "sql", "select 2", "Bob DS", 1, "bob"));

        DatavListDatasetsExecutor executor = new DatavListDatasetsExecutor();
        executor.setDaoProvider(daoProvider);

        AiToolCall call = new AiToolCall();
        call.setToolName(DatavListDatasetsExecutor.TOOL_NAME);
        call.setInput("{}");

        // alice（非 admin）：仅自己的数据集
        AiToolCallResult aliceResult = executor.executeAsync(call,
                new ChatBiToolExecuteContext(null, "alice", false)).toCompletableFuture().join();
        assertEquals("success", aliceResult.getStatus());
        @SuppressWarnings("unchecked")
        Map<String, Object> parsed = (Map<String, Object>) JsonTool.parseNonStrict(aliceResult.getOutput().getBody());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> aliceDatasets = (List<Map<String, Object>>) parsed.get("datasets");
        assertTrue(aliceDatasets.stream().anyMatch(d -> "ds-vis-alice".equals(d.get("sid"))),
                "alice sees her own dataset");
        assertFalse(aliceDatasets.stream().anyMatch(d -> "ds-vis-bob".equals(d.get("sid"))),
                "alice must NOT see bob's dataset (visibility filter)");

        // admin：全量
        AiToolCallResult adminResult = executor.executeAsync(call,
                new ChatBiToolExecuteContext(null, "admin-user", true)).toCompletableFuture().join();
        assertEquals("success", adminResult.getStatus());
        @SuppressWarnings("unchecked")
        Map<String, Object> adminParsed = (Map<String, Object>) JsonTool.parseNonStrict(adminResult.getOutput().getBody());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> adminDatasets = (List<Map<String, Object>>) adminParsed.get("datasets");
        assertTrue(adminDatasets.stream().anyMatch(d -> "ds-vis-alice".equals(d.get("sid")))
                        && adminDatasets.stream().anyMatch(d -> "ds-vis-bob".equals(d.get("sid"))),
                "admin sees all datasets");

        // 无身份（operator=null 且非 admin）：fail-closed 空列表
        AiToolCallResult noIdentityResult = executor.executeAsync(call,
                new ChatBiToolExecuteContext()).toCompletableFuture().join();
        assertEquals("success", noIdentityResult.getStatus());
        @SuppressWarnings("unchecked")
        Map<String, Object> noIdentityParsed = (Map<String, Object>) JsonTool.parseNonStrict(noIdentityResult.getOutput().getBody());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> noIdentityDatasets = (List<Map<String, Object>>) noIdentityParsed.get("datasets");
        assertTrue(noIdentityDatasets.isEmpty(), "no-identity invocation must see no datasets (fail-closed)");
    }

    /**
     * P1-03: describe 对不可见数据集显式拒绝（ERR_DATAV_CHATBI_DATASET_NO_ACCESS 错误码），
     * owner 照常可描述（无过度限制）。
     */
    @Test
    public void testDescribeInvisibleDatasetReturnsExplicitError() {
        NopReportDataset ds = newReportDataset("ds-desc-vis", "sql", "select * from T", "Bob Secret", 1, "bob");
        daoProvider.daoFor(NopReportDataset.class).saveEntityDirectly(ds);

        DatavDescribeDatasetExecutor executor = new DatavDescribeDatasetExecutor();
        executor.setDaoProvider(daoProvider);

        AiToolCall call = new AiToolCall();
        call.setToolName(DatavDescribeDatasetExecutor.TOOL_NAME);
        call.setInput(JsonTool.stringify(Map.of("datasetSid", "ds-desc-vis")));

        AiToolCallResult denied = executor.executeAsync(call,
                new ChatBiToolExecuteContext(null, "alice", false)).toCompletableFuture().join();
        assertEquals("failure", denied.getStatus(), "non-owner describe must be rejected");
        assertNotNull(denied.getError());
        assertTrue(denied.getError().getBody().contains("nop.err.datav.chatbi-dataset-no-access"),
                "rejection must carry the structured no-access errorCode, got: " + denied.getError().getBody());

        AiToolCallResult owner = executor.executeAsync(call,
                new ChatBiToolExecuteContext(null, "bob", false)).toCompletableFuture().join();
        assertEquals("success", owner.getStatus(), "owner describe still works");

        AiToolCallResult admin = executor.executeAsync(call,
                new ChatBiToolExecuteContext(null, "admin-user", true)).toCompletableFuture().join();
        assertEquals("success", admin.getStatus(), "admin describe works across creators");
    }

    // ==================== Helpers ====================

    private NopReportDataset newReportDataset(String sid, String dsType, String dsText, String dsName, int status) {
        return newReportDataset(sid, dsType, dsText, dsName, status, "test");
    }

    private NopReportDataset newReportDataset(String sid, String dsType, String dsText, String dsName,
                                              int status, String createdBy) {
        long now = System.currentTimeMillis();
        NopReportDataset ds = new NopReportDataset();
        ds.setSid(sid);
        ds.setDsName(dsName);
        ds.setIsSingleRow(false);
        ds.setDsType(dsType);
        ds.setDsText(dsText);
        ds.setDsMeta("{}");
        ds.setStatus(status);
        ds.setVersion(0);
        ds.setCreatedBy(createdBy);
        ds.setCreateTime(new Timestamp(now));
        ds.setUpdatedBy(createdBy);
        ds.setUpdateTime(new Timestamp(now));
        return ds;
    }
}
