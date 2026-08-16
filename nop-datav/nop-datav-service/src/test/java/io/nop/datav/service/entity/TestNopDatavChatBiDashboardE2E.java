package io.nop.datav.service.entity;

import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.messages.ChatToolCallMessage;
import io.nop.ai.api.chat.stream.ChatStreamChunk;
import io.nop.ai.toolkit.api.IToolCallInterceptor;
import io.nop.ai.toolkit.api.IToolManager;
import io.nop.ai.toolkit.executor.DefaultToolExecutorProvider;
import io.nop.ai.toolkit.manager.ToolManagerImpl;
import io.nop.ai.toolkit.model.AiToolCallResult;
import io.nop.api.core.context.TenantProxyContext;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.ICancelToken;
import io.nop.core.context.IServiceContext;
import io.nop.core.context.ServiceContextImpl;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.api.IDaoProvider;
import io.nop.datav.biz.INopDatavPanelBiz;
import io.nop.datav.biz.PanelDataResult;
import io.nop.datav.dao.entity.NopDatavDashboard;
import io.nop.datav.dao.entity.NopDatavDatasetRef;
import io.nop.datav.dao.entity.NopDatavPanel;
import io.nop.datav.service.chatbi.ChatBiResult;
import io.nop.datav.service.chatbi.ChatBiToolExecuteContext;
import io.nop.datav.service.chatbi.DatavDescribeDatasetExecutor;
import io.nop.datav.service.chatbi.DatavGenerateDashboardExecutor;
import io.nop.datav.service.chatbi.DatavListDatasetsExecutor;
import io.nop.datav.service.chatbi.DatavQueryDatasetExecutor;
import io.nop.orm.IOrmTemplate;
import io.nop.report.dao.entity.NopReportDataset;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ChatBI 看板生成端到端测试（D6-1b Phase 4）。
 *
 * <p>验证完整链路：{@code chatToDashboard} → mock IChatService（LLM tool-calling 序列）→ 真实
 * IToolManager（4 工具：list/describe/query + generate-dashboard）→ generate-dashboard executor
 * → 真实 Dashboard/Panel/DatasetRef 落库 → 经 {@code getPanelData} 消费 datasetRef 链路返回真实数据。</p>
 *
 * <p><b>Anti-Hollow</b>：断言（a）{@code IChatService.call} 被调用 ≥ 1 次、（b）{@code IToolManager.callTool}
 * 被调用 ≥ 1 次（含 generate-dashboard）、（c）返回 dashboardId 指向 DB 真实行、（d）该看板 panel 经
 * {@code getPanelData} 能返回真实数据（datasetRef 链路连通）、（e）panelConfig JSON 含 fieldMapping（非空壳）。</p>
 *
 * <p>覆盖：ai-present 多轮 tool-calling / ai-absent / max-iterations / spec-error-then-recover 四个分支。</p>
 */
public class TestNopDatavChatBiDashboardE2E extends AbstractNopDatavTest {

    private static final String CALLER = "dash-e2e-user";

    @Inject
    IDaoProvider daoProvider;

    @Inject
    IOrmTemplate ormTemplate;

    @Inject
    INopDatavPanelBiz panelBiz;

    // ==================== 主线 E2E：多轮 tool-calling → 草稿看板真实落库 → getPanelData 可消费 ====================

    @Test
    public void testE2eChatToDashboardCreatesRealDashboard() {
        createSalesTable();
        insertSalesRow("north", "widget", 100);
        insertSalesRow("north", "gadget", 200);

        NopReportDataset ds = newActiveSqlDataset("ds-dash-e2e",
                "select REGION as region, AMOUNT as amount from TEST_DATAV_SALES order by AMOUNT desc",
                fieldsMeta("region", "amount"));
        daoProvider.daoFor(NopReportDataset.class).saveEntityDirectly(ds);

        AtomicInteger llmCallCount = new AtomicInteger(0);
        AtomicInteger toolCallCount = new AtomicInteger(0);

        // round 1: LLM 调 generate-dashboard（生成 chart + text panel）
        // round 2: LLM 给出最终文本摘要
        String genArgs = JsonTool.stringify(buildSpec("Regional Sales Dashboard",
                panel("Sales Chart", "chart", "ds-dash-e2e", fieldMapping("x", "region", "y", "amount"), 0),
                panel("Notes", "text", null, null, 1)));
        MockChatService mockChat = new MockChatService(Arrays.asList(
                buildToolCallResponse("call-1", DatavGenerateDashboardExecutor.TOOL_NAME, genArgs),
                buildFinalAnswerResponse("已创建草稿看板 Regional Sales Dashboard，请审阅后发布。")
        ));
        mockChat.callCounter = llmCallCount;

        IToolManager toolManager = buildToolManagerWithCounters(toolCallCount);

        NopDatavChatBiBizModel bizModel = new NopDatavChatBiBizModel();
        bizModel.setChatService(mockChat);
        bizModel.setToolManager(toolManager);

        IServiceContext context = newContext(CALLER);
        ChatBiResult result = bizModel.chatToDashboard("建一个各地区销售额分析的看板", context);

        // Anti-Hollow (a): IChatService.call 被调用 ≥ 1 次
        assertTrue(llmCallCount.get() >= 1, "IChatService.call must be invoked at least once");
        // Anti-Hollow (b): IToolManager.callTool 被调用 ≥ 1 次（generate-dashboard）
        assertTrue(toolCallCount.get() >= 1, "IToolManager.callTool must be invoked (generate-dashboard)");

        // Anti-Hollow (c): 返回的 createdEntityId（dashboardId）指向 DB 真实行
        assertNotNull(result);
        String dashboardId = result.getCreatedEntityId();
        assertNotNull(dashboardId, "createdEntityId (dashboardId) must be set");
        NopDatavDashboard dash = daoProvider.daoFor(NopDatavDashboard.class).getEntityById(dashboardId);
        assertNotNull(dash, "dashboardId must point to a real DB row (Anti-Hollow)");
        assertEquals(Integer.valueOf(0), dash.getPublishStatus(), "publishStatus=DRAFT (裁定 H)");
        assertEquals(CALLER, dash.getCreatedBy(), "createdBy=caller (operator 传递裁定 G)");
        assertEquals(CALLER, dash.getUpdatedBy());

        // 断言 Panel + DatasetRef 落库
        List<NopDatavPanel> panels = findPanels(dashboardId);
        assertEquals(2, panels.size(), "2 panels created (chart + text)");
        List<NopDatavDatasetRef> refs = findDatasetRefs(dashboardId);
        assertEquals(1, refs.size(), "1 DatasetRef (dedup, 2 panels share 1 dataset)");

        // 断言 chart panel 配置：panelName=title, panelConfig 含 fieldMapping
        NopDatavPanel chart = panels.stream().filter(p -> p.getPanelType() == 0).findFirst().orElse(null);
        assertNotNull(chart);
        assertEquals("Sales Chart", chart.getPanelName(), "panelName=规格.title (mandatory)");
        assertNotNull(chart.getPanelConfig(), "panelConfig must be populated");
        assertTrue(chart.getPanelConfig().contains("fieldMapping"),
                "panelConfig must contain fieldMapping subkey (Anti-Hollow e, 非空壳)");

        // Anti-Hollow (d): 生成的草稿看板 needsDataset=true panel 经 getPanelData 能返回真实数据
        // （datasetRef 链路连通：panel.datasetRefId → DatasetRef.refDatasetId → NopReportDataset → SQL）
        // 注：生成 DatasetRef 的 paramMapping 初值为 {}（裁定 I），故用无 ${param} 占位符的数据集
        // 验证「链路连通」（fieldMapping 落地另由上方 panelConfig JSON 断言覆盖）。
        PanelDataResult data = panelBiz.getPanelData(chart.getPanelId(),
                null, newContext(CALLER));
        assertNotNull(data);
        assertTrue(data.isHasDataset());
        assertEquals("chart", data.getComponentType());
        assertEquals(2, data.getRows().size(), "getPanelData returns real data rows (2 sales rows)");
        Object firstAmount = findCaseInsensitive(data.getRows().get(0), "amount");
        assertEquals(200, ((Number) firstAmount).intValue(),
                "ordered desc → first row amount=200 (real DB data, not stub)");
    }

    // ==================== ai-absent 分支：显式失败 ====================

    @Test
    public void testAiAbsentThrowsExplicitError() {
        NopDatavChatBiBizModel bizModel = new NopDatavChatBiBizModel();
        bizModel.setChatService(null);
        bizModel.setToolManager(null);

        NopException ex = assertThrows(NopException.class, () ->
                bizModel.chatToDashboard("any description", newContext(CALLER)));
        assertEquals("nop.err.datav.chatbi-ai-not-available", ex.getErrorCode());
    }

    // ==================== max-iterations 分支：超限抛异常 ====================

    @Test
    public void testMaxIterationsExceeded() {
        NopReportDataset ds = newActiveSqlDataset("ds-maxiter-dash",
                "select 1 as one", fieldsMeta("one"));
        daoProvider.daoFor(NopReportDataset.class).saveEntityDirectly(ds);

        // mock LLM 永远返回 tool call → 永不给出最终答案 → 超限
        AtomicInteger toolCallCount = new AtomicInteger(0);
        String genArgs = JsonTool.stringify(buildSpec("Loop Dash",
                panel("C", "chart", "ds-maxiter-dash", null, 0)));
        MockChatService alwaysToolCall = new MockChatService(Collections.singletonList(
                buildToolCallResponse("call-loop", DatavGenerateDashboardExecutor.TOOL_NAME, genArgs)
        ));
        alwaysToolCall.repeatLast = true;

        IToolManager toolManager = buildToolManagerWithCounters(toolCallCount);

        NopDatavChatBiBizModel bizModel = new NopDatavChatBiBizModel();
        bizModel.setChatService(alwaysToolCall);
        bizModel.setToolManager(toolManager);

        NopException ex = assertThrows(NopException.class, () ->
                bizModel.chatToDashboard("loop description", newContext(CALLER)));
        assertEquals("nop.err.datav.chatbi-max-iterations-exceeded", ex.getErrorCode());
        assertTrue(toolCallCount.get() >= 1, "at least one tool call before exceeding max iterations");
    }

    // ==================== spec-error-then-recover 分支：错误回喂 → 第二轮修正成功 ====================

    @Test
    public void testSpecErrorRecoveredOnSecondRound() {
        createSalesTable();
        insertSalesRow("north", "widget", 100);
        NopReportDataset ds = newActiveSqlDataset("ds-recover",
                "select REGION as region, AMOUNT as amount from TEST_DATAV_SALES where REGION = ${region}",
                fieldsMeta("region", "amount"));
        daoProvider.daoFor(NopReportDataset.class).saveEntityDirectly(ds);

        AtomicInteger toolCallCount = new AtomicInteger(0);

        // round 1: LLM 产出含未知 componentType 的规格 → generate-dashboard 返回错误
        String badArgs = JsonTool.stringify(buildSpec("Bad Dash",
                panel("Weird", "nonexistent-type", null, null, 0)));
        // round 2: LLM 收到错误 tool response 后修正 → 产出合法规格
        String goodArgs = JsonTool.stringify(buildSpec("Good Dash",
                panel("Chart", "chart", "ds-recover", fieldMapping("x", "region", "y", "amount"), 0)));
        // round 3: LLM 给出最终文本摘要
        MockChatService mockChat = new MockChatService(Arrays.asList(
                buildToolCallResponse("bad-1", DatavGenerateDashboardExecutor.TOOL_NAME, badArgs),
                buildToolCallResponse("good-2", DatavGenerateDashboardExecutor.TOOL_NAME, goodArgs),
                buildFinalAnswerResponse("已修正并创建草稿看板。")
        ));

        IToolManager toolManager = buildToolManagerWithCounters(toolCallCount);
        NopDatavChatBiBizModel bizModel = new NopDatavChatBiBizModel();
        bizModel.setChatService(mockChat);
        bizModel.setToolManager(toolManager);

        ChatBiResult result = bizModel.chatToDashboard("建一个看板（先犯错后修正）", newContext(CALLER));

        // 错误被回喂，第二轮修正后成功创建
        assertNotNull(result);
        assertNotNull(result.getCreatedEntityId(), "second round corrects spec and creates dashboard");
        assertTrue(toolCallCount.get() >= 2, "both bad + good tool calls invoked");
        NopDatavDashboard dash = daoProvider.daoFor(NopDatavDashboard.class)
                .getEntityById(result.getCreatedEntityId());
        assertNotNull(dash, "recovered dashboard real in DB");
        assertEquals("Good Dash", dash.getDashboardName(), "the GOOD spec was created (not the bad one)");
    }

    // ==================== 错误路径：未创建半成品看板 ====================

    @Test
    public void testErrorPathLeavesNoHalfBakedDashboard() {
        NopReportDataset ds = newActiveSqlDataset("ds-nobake",
                "select 1 as one", fieldsMeta("one"));
        daoProvider.daoFor(NopReportDataset.class).saveEntityDirectly(ds);

        AtomicInteger toolCallCount = new AtomicInteger(0);

        // round 1: LLM 产出非法 datasetSid → executor 返回错误
        // round 2: LLM 给出最终文本（向用户报告无法创建）
        String badArgs = JsonTool.stringify(buildSpec("No Bake Dash",
                panel("Chart", "chart", "ds-does-not-exist", null, 0)));
        MockChatService mockChat = new MockChatService(Arrays.asList(
                buildToolCallResponse("bad-nobake", DatavGenerateDashboardExecutor.TOOL_NAME, badArgs),
                buildFinalAnswerResponse("抱歉，无法创建看板，引用的数据集不存在。")
        ));

        IToolManager toolManager = buildToolManagerWithCounters(toolCallCount);
        NopDatavChatBiBizModel bizModel = new NopDatavChatBiBizModel();
        bizModel.setChatService(mockChat);
        bizModel.setToolManager(toolManager);

        long dashBefore = daoProvider.daoFor(NopDatavDashboard.class).findAll().size();

        ChatBiResult result = bizModel.chatToDashboard("建一个引用不存在数据集的看板", newContext(CALLER));

        // executor 返回错误 → LLM 给出最终文本答案 → action 透传错误结果（createdEntityId=null）
        assertNotNull(result);
        assertEquals(null, result.getCreatedEntityId(),
                "no dashboard created when spec is invalid (createdEntityId stays null)");
        assertEquals(dashBefore, daoProvider.daoFor(NopDatavDashboard.class).findAll().size(),
                "no half-baked Dashboard row left (无静默跳过 + 无半成品)");
        assertTrue(toolCallCount.get() >= 1, "generate-dashboard executor was invoked (and returned error)");
    }

    // ==================== Helpers ====================

    private IToolManager buildToolManagerWithCounters(AtomicInteger toolCallCount) {
        DatavListDatasetsExecutor listExec = new DatavListDatasetsExecutor();
        listExec.setDaoProvider(daoProvider);
        DatavDescribeDatasetExecutor descExec = new DatavDescribeDatasetExecutor();
        descExec.setDaoProvider(daoProvider);
        DatavQueryDatasetExecutor queryExec = new DatavQueryDatasetExecutor();
        queryExec.setDaoProvider(daoProvider);
        queryExec.setJdbcTemplate(jdbcTemplate);
        DatavGenerateDashboardExecutor genExec = new DatavGenerateDashboardExecutor();
        genExec.setDaoProvider(daoProvider);
        genExec.setOrmTemplate(ormTemplate);

        DefaultToolExecutorProvider provider = new DefaultToolExecutorProvider();
        provider.setExecutors(Arrays.asList(listExec, descExec, queryExec, genExec));

        return new CountingToolManager(provider, Collections.emptyList(), toolCallCount);
    }

    private static ChatResponse buildToolCallResponse(String callId, String toolName, String argumentsJson) {
        ChatResponse response = new ChatResponse();
        // ChatToolCallMessage 接受 arguments Map；但我们需保留 JSON 顺序，故解析后传入
        @SuppressWarnings("unchecked")
        Map<String, Object> args = JsonTool.parseNonStrict(argumentsJson) instanceof Map
                ? (Map<String, Object>) JsonTool.parseNonStrict(argumentsJson)
                : new LinkedHashMap<>();
        ChatToolCallMessage tcm = new ChatToolCallMessage(callId, toolName, args);
        response.addMessage(tcm);
        return response;
    }

    private static ChatResponse buildFinalAnswerResponse(String answer) {
        ChatResponse response = new ChatResponse();
        response.addMessage(new ChatAssistantMessage(answer));
        return response;
    }

    private IServiceContext newContext(String userName) {
        ServiceContextImpl context = new ServiceContextImpl();
        context.setContext(new TenantProxyContext(context.getContext()));
        context.getContext().setUserName(userName);
        return context;
    }

    private List<NopDatavPanel> findPanels(String dashboardId) {
        return daoProvider.daoFor(NopDatavPanel.class).findAll().stream()
                .filter(p -> dashboardId.equals(p.getDashboardId()))
                .collect(Collectors.toList());
    }

    private List<NopDatavDatasetRef> findDatasetRefs(String dashboardId) {
        return daoProvider.daoFor(NopDatavDatasetRef.class).findAll().stream()
                .filter(r -> dashboardId.equals(r.getDashboardId()))
                .collect(Collectors.toList());
    }

    private static Map<String, Object> buildSpec(String dashName, Map<String, Object>... panels) {
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("dashboardName", dashName);
        spec.put("panels", Arrays.asList(panels));
        return spec;
    }

    private static Map<String, Object> panel(String title, String componentType, String datasetSid,
                                              Map<String, Object> fieldMapping, int sortOrder) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("title", title);
        p.put("componentType", componentType);
        if (datasetSid != null) {
            p.put("datasetSid", datasetSid);
        }
        if (fieldMapping != null) {
            p.put("fieldMapping", fieldMapping);
        }
        p.put("sortOrder", sortOrder);
        return p;
    }

    private static Map<String, Object> fieldMapping(String... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return m;
    }

    private static String fieldsMeta(String... names) {
        List<Map<String, Object>> fields = new ArrayList<>();
        for (String n : names) {
            Map<String, Object> f = new LinkedHashMap<>();
            f.put("name", n);
            f.put("type", "string");
            fields.add(f);
        }
        return JsonTool.stringify(Collections.singletonMap("fields", fields));
    }

    private NopReportDataset newActiveSqlDataset(String sid, String dsText, String dsMeta) {
        long now = System.currentTimeMillis();
        NopReportDataset ds = new NopReportDataset();
        ds.setSid(sid);
        ds.setDsName(sid);
        ds.setIsSingleRow(false);
        ds.setDsType("sql");
        ds.setDsText(dsText);
        ds.setDsMeta(dsMeta != null ? dsMeta : "{}");
        ds.setStatus(1);
        ds.setVersion(0);
        // AR-1 迁移（plan 2026-08-16-2137-1）：E2E 经 BizModel 真实身份链路以 CALLER（非 admin）调用，
        // fixture createdBy 必须与 CALLER 对齐（预期内迁移，非回归）
        ds.setCreatedBy(CALLER);
        ds.setCreateTime(new Timestamp(now));
        ds.setUpdatedBy(CALLER);
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

    // ==================== Mock IChatService ====================

    private static class MockChatService implements IChatService {
        private final List<ChatResponse> responses;
        private final AtomicInteger index = new AtomicInteger(0);
        private volatile boolean repeatLast = false;
        private AtomicInteger callCounter = new AtomicInteger(0);

        MockChatService(List<ChatResponse> responses) {
            this.responses = new ArrayList<>(responses);
        }

        @Override
        public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
            return java.util.concurrent.CompletableFuture.completedFuture(call(request, cancelToken));
        }

        @Override
        public ChatResponse call(ChatRequest request, ICancelToken cancelToken) {
            if (callCounter != null) {
                callCounter.incrementAndGet();
            }
            int idx = index.getAndIncrement();
            if (idx < responses.size()) {
                return responses.get(idx);
            }
            if (repeatLast && !responses.isEmpty()) {
                return responses.get(responses.size() - 1);
            }
            return buildFinalAnswerResponse("");
        }

        @Override
        public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
            throw new UnsupportedOperationException("callStream not supported in mock");
        }
    }

    private static class CountingToolManager extends ToolManagerImpl {
        private final AtomicInteger counter;

        CountingToolManager(DefaultToolExecutorProvider provider,
                            List<IToolCallInterceptor> interceptors,
                            AtomicInteger counter) {
            super(provider, interceptors);
            this.counter = counter;
        }

        @Override
        public java.util.concurrent.CompletableFuture<AiToolCallResult> callTool(
                String toolName, io.nop.ai.toolkit.model.AiToolCall call,
                io.nop.ai.toolkit.api.IToolExecuteContext context) {
            counter.incrementAndGet();
            return super.callTool(toolName, call, context);
        }
    }
}
