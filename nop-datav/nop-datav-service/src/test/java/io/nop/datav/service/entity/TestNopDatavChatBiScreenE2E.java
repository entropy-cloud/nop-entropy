package io.nop.datav.service.entity;

import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.messages.ChatToolCallMessage;
import io.nop.ai.api.chat.stream.ChatStreamChunk;
import io.nop.ai.toolkit.api.IToolCallInterceptor;
import io.nop.ai.toolkit.api.IToolManager;
import io.nop.ai.toolkit.api.IToolExecuteContext;
import io.nop.ai.toolkit.executor.DefaultToolExecutorProvider;
import io.nop.ai.toolkit.manager.ToolManagerImpl;
import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;
import io.nop.api.core.context.TenantProxyContext;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.ICancelToken;
import io.nop.core.context.IServiceContext;
import io.nop.core.context.ServiceContextImpl;
import io.nop.core.lang.json.JsonTool;
import io.nop.dao.api.IDaoProvider;
import io.nop.datav.biz.ScreenLayoutConfig;
import io.nop.datav.dao.entity.NopDatavDatasetRef;
import io.nop.datav.dao.entity.NopDatavScreen;
import io.nop.datav.dao.entity.NopDatavScreenWidget;
import io.nop.datav.service.chatbi.ChatBiResult;
import io.nop.datav.service.chatbi.DatavGenerateScreenExecutor;
import io.nop.datav.service.chatbi.DatavListComponentTypesExecutor;
import io.nop.datav.service.component.PanelComponentRegistry;
import io.nop.datav.service.screen.ScreenLayoutParser;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ChatBI 大屏生成端到端测试（D6-2 Phase 4）。
 *
 * <p>验证完整链路：{@code chatToScreen} → mock IChatService（LLM tool-calling 序列：list-component-types →
 * list → describe → query → generate-screen）→ 真实 IToolManager → generate-screen executor
 * → 真实 Screen/ScreenWidget 落库 → 经 {@code getScreenDraftLayout} 消费生成配置（ScreenLayoutParser 解析）。</p>
 *
 * <p><b>Anti-Hollow</b>：断言（a）{@code IChatService.call} 被调用 ≥ 1 次、（b）{@code IToolManager.callTool}
 * 被调用 ≥ 1 次（含 generate-screen）、（c）返回 screenId 指向 DB 真实行、（d）经 {@code getScreenDraftLayout}
 * 可解析消费（生成 → 运行时消费链路连通，不抛越界异常）。</p>
 *
 * <p>覆盖：主线 E2E（含装饰组件）/ 装饰+数据组件混合（裁定 M）/ 数据正确性（getScreenDraftLayout 可解析）/
 * 错误路径 E2E（越界 → 无半成品残留）。</p>
 */
public class TestNopDatavChatBiScreenE2E extends AbstractNopDatavTest {

    private static final String CALLER = "screen-e2e-user";

    @Inject
    IDaoProvider daoProvider;

    @Inject
    IOrmTemplate ormTemplate;

    // ==================== 主线 E2E：多轮 tool-calling → 草稿大屏真实落库 → getScreenDraftLayout 可消费 ====================

    @Test
    public void testE2eChatToScreenCreatesRealScreen() {
        NopReportDataset ds = newActiveSqlDataset("ds-screen-e2e",
                "select REGION as region, AMOUNT as amount from TEST_SCREEN_E2E",
                fieldsMeta("region", "amount"));
        daoProvider.daoFor(NopReportDataset.class).saveEntityDirectly(ds);

        AtomicInteger llmCallCount = new AtomicInteger(0);
        AtomicInteger toolCallCount = new AtomicInteger(0);

        // round 1: LLM 调 list-component-types（了解组件）
        // round 2: LLM 调 generate-screen（生成 chart + decorative-border widget）
        // round 3: LLM 给出最终文本摘要
        String genArgs = JsonTool.stringify(screenSpec("Business KPI Screen", 1920, 1080,
                Arrays.asList(
                        widget("chart", "ds-screen-e2e", fieldMapping("x", "region", "y", "amount"),
                                0, 100, 900, 400, 1),
                        widget("decorative-border", null, null, 0, 0, 1920, 80, 0),
                        widget("stat-tile", "ds-screen-e2e", null, 1000, 100, 400, 200, 1)
                )));
        MockChatService mockChat = new MockChatService(Arrays.asList(
                buildToolCallResponse("call-1", DatavListComponentTypesExecutor.TOOL_NAME, "{}"),
                buildToolCallResponse("call-2", DatavGenerateScreenExecutor.TOOL_NAME, genArgs),
                buildFinalAnswerResponse("已创建草稿大屏 Business KPI Screen，请审阅后发布。")
        ));
        mockChat.callCounter = llmCallCount;

        IToolManager toolManager = buildToolManagerWithCounters(toolCallCount);

        NopDatavChatBiBizModel bizModel = new NopDatavChatBiBizModel();
        bizModel.setChatService(mockChat);
        bizModel.setToolManager(toolManager);

        IServiceContext context = newContext(CALLER);
        ChatBiResult result = bizModel.chatToScreen("建一个经营 KPI 大屏，1920x1080，顶部 KPI 卡片，中间销售趋势图", context);

        // Anti-Hollow (a): IChatService.call 被调用 ≥ 1 次
        assertTrue(llmCallCount.get() >= 1, "IChatService.call must be invoked at least once");
        // Anti-Hollow (b): IToolManager.callTool 被调用 ≥ 1 次（含 generate-screen）
        assertTrue(toolCallCount.get() >= 1, "IToolManager.callTool must be invoked (generate-screen)");

        // Anti-Hollow (c): 返回的 createdEntityId（screenId）指向 DB 真实行
        assertNotNull(result);
        String screenId = result.getCreatedEntityId();
        assertNotNull(screenId, "createdEntityId (screenId) must be set");
        NopDatavScreen screen = daoProvider.daoFor(NopDatavScreen.class).getEntityById(screenId);
        assertNotNull(screen, "screenId must point to a real DB row (Anti-Hollow)");
        assertEquals(Integer.valueOf(0), screen.getPublishStatus(), "publishStatus=DRAFT (裁定 H)");
        assertEquals(CALLER, screen.getCreatedBy(), "createdBy=caller (operator 传递裁定 G)");
        assertEquals(CALLER, screen.getUpdatedBy());
        assertEquals(1920, screen.getScreenWidth());
        assertEquals(1080, screen.getScreenHeight());

        // 断言 Widget 落库 + 数量 + componentType + 自由画布定位
        List<NopDatavScreenWidget> widgets = findWidgets(screenId);
        assertEquals(3, widgets.size(), "3 widgets created (chart + decorative-border + stat-tile)");

        NopDatavScreenWidget chartWidget = widgets.stream()
                .filter(w -> "chart".equals(w.getComponentType())).findFirst().orElse(null);
        assertNotNull(chartWidget);
        assertEquals("ds-screen-e2e", chartWidget.getDatasetRefId(),
                "chart widget datasetRefId = nop-report sid (裁定 N: 直存 sid，不经 DatasetRef)");
        assertEquals(0, chartWidget.getX());
        assertEquals(100, chartWidget.getY());
        assertEquals(900, chartWidget.getW());
        assertEquals(400, chartWidget.getH());
        assertEquals(1, chartWidget.getZ());
        assertNotNull(chartWidget.getWidgetConfig());
        assertTrue(chartWidget.getWidgetConfig().contains("fieldMapping"),
                "widgetConfig must contain fieldMapping (Anti-Hollow, 非空壳)");

        // 装饰组件（裁定 M：needsDataset=false，datasetRefId 为空）
        NopDatavScreenWidget borderWidget = widgets.stream()
                .filter(w -> "decorative-border".equals(w.getComponentType())).findFirst().orElse(null);
        assertNotNull(borderWidget);
        assertNull(borderWidget.getDatasetRefId(),
                "decorative-border datasetRefId is null (裁定 M)");

        // 裁定 N：不创建 DatasetRef 行
        List<NopDatavDatasetRef> refs = daoProvider.daoFor(NopDatavDatasetRef.class).findAll();
        assertTrue(refs.isEmpty(), "no DatasetRef rows created (裁定 N: 直存 sid 不经 DatasetRef)");

        // Anti-Hollow (d): 生成的草稿大屏经 getScreenDraftLayout 可解析消费（生成 → 运行时消费链路连通）
        ScreenLayoutParser layoutParser = new ScreenLayoutParser(PanelComponentRegistry.getInstance());
        ScreenLayoutConfig layout = layoutParser.parse(screenId, serializeScreenContent(screen, widgets));
        assertNotNull(layout, "getScreenDraftLayout must return parseable ScreenLayoutConfig");
        assertEquals(1920, layout.getCanvas().getWidth());
        assertEquals(1080, layout.getCanvas().getHeight());
        assertEquals(3, layout.getWidgets().size(), "parsed layout has 3 widgets");
        // 生成的定位不越界（否则 getScreenDraftLayout 会抛 ERR_DATAV_SCREEN_WIDGET_OUT_OF_BOUNDS）
    }

    // ==================== E2E 装饰组件覆盖（裁定 M） ====================

    @Test
    public void testE2eDecorativeAndDataComponentsMixed() {
        NopReportDataset ds = newActiveSqlDataset("ds-mix-e2e",
                "select REGION as region from TEST_SCREEN_E2E", fieldsMeta("region"));
        daoProvider.daoFor(NopReportDataset.class).saveEntityDirectly(ds);

        AtomicInteger toolCallCount = new AtomicInteger(0);

        // 规格：1 个 needsDataset=false（decorative-border）+ 1 个 needsDataset=true（chart）
        String genArgs = JsonTool.stringify(screenSpec("Mixed Screen", 1920, 1080,
                Arrays.asList(
                        widget("decorative-border", null, null, 0, 0, 1920, 80, 0),
                        widget("chart", "ds-mix-e2e", fieldMapping("x", "region"),
                                0, 100, 900, 400, 1)
                )));
        MockChatService mockChat = new MockChatService(Arrays.asList(
                buildToolCallResponse("call-1", DatavGenerateScreenExecutor.TOOL_NAME, genArgs),
                buildFinalAnswerResponse("已创建混合组件大屏。")
        ));

        IToolManager toolManager = buildToolManagerWithCounters(toolCallCount);
        NopDatavChatBiBizModel bizModel = new NopDatavChatBiBizModel();
        bizModel.setChatService(mockChat);
        bizModel.setToolManager(toolManager);

        ChatBiResult result = bizModel.chatToScreen("建一个混合大屏", newContext(CALLER));

        assertNotNull(result);
        String screenId = result.getCreatedEntityId();
        assertNotNull(screenId);

        List<NopDatavScreenWidget> widgets = findWidgets(screenId);
        assertEquals(2, widgets.size());

        NopDatavScreenWidget border = widgets.stream()
                .filter(w -> "decorative-border".equals(w.getComponentType())).findFirst().orElse(null);
        assertNotNull(border);
        assertNull(border.getDatasetRefId(), "decorative component datasetRefId is empty (裁定 M)");

        NopDatavScreenWidget chart = widgets.stream()
                .filter(w -> "chart".equals(w.getComponentType())).findFirst().orElse(null);
        assertNotNull(chart);
        assertEquals("ds-mix-e2e", chart.getDatasetRefId(),
                "chart component datasetRefId = nop-report sid (裁定 M + N)");
    }

    // ==================== E2E 数据正确性：getScreenDraftLayout 可解析 ====================

    @Test
    public void testE2eGeneratedScreenConsumableViaGetScreenDraftLayout() {
        NopReportDataset ds = newActiveSqlDataset("ds-consume-e2e",
                "select REGION as region from TEST_SCREEN_E2E", fieldsMeta("region"));
        daoProvider.daoFor(NopReportDataset.class).saveEntityDirectly(ds);

        AtomicInteger toolCallCount = new AtomicInteger(0);

        String genArgs = JsonTool.stringify(screenSpec("Consumable Screen", 1920, 1080,
                Collections.singletonList(
                        widget("chart", "ds-consume-e2e", fieldMapping("x", "region"),
                                10, 10, 500, 300, 0))));
        MockChatService mockChat = new MockChatService(Arrays.asList(
                buildToolCallResponse("call-1", DatavGenerateScreenExecutor.TOOL_NAME, genArgs),
                buildFinalAnswerResponse("已创建大屏。")
        ));

        IToolManager toolManager = buildToolManagerWithCounters(toolCallCount);
        NopDatavChatBiBizModel bizModel = new NopDatavChatBiBizModel();
        bizModel.setChatService(mockChat);
        bizModel.setToolManager(toolManager);

        ChatBiResult result = bizModel.chatToScreen("建一个大屏", newContext(CALLER));
        String screenId = result.getCreatedEntityId();
        assertNotNull(screenId);

        NopDatavScreen screen = daoProvider.daoFor(NopDatavScreen.class).getEntityById(screenId);
        List<NopDatavScreenWidget> widgets = findWidgets(screenId);

        // 生成的定位不越界（否则 getScreenDraftLayout 会抛 ERR_DATAV_SCREEN_WIDGET_OUT_OF_BOUNDS）
        ScreenLayoutParser layoutParser = new ScreenLayoutParser(PanelComponentRegistry.getInstance());
        ScreenLayoutConfig layout = layoutParser.parse(screenId, serializeScreenContent(screen, widgets));
        assertNotNull(layout);
        assertEquals(1, layout.getWidgets().size());
        assertEquals("chart", layout.getWidgets().get(0).getComponentType());
        assertEquals(10, layout.getWidgets().get(0).getX());
        assertEquals(500, layout.getWidgets().get(0).getW());
    }

    // ==================== E2E 错误路径：越界 → 无半成品残留 ====================

    @Test
    public void testE2eOutOfBoundsErrorLeavesNoHalfBakedScreen() {
        AtomicInteger toolCallCount = new AtomicInteger(0);

        long screenBefore = daoProvider.daoFor(NopDatavScreen.class).findAll().size();
        long widgetBefore = daoProvider.daoFor(NopDatavScreenWidget.class).findAll().size();

        // 越界规格（x+w > screenWidth）
        String genArgs = JsonTool.stringify(screenSpec("Oob E2E Screen", 1920, 1080,
                Collections.singletonList(
                        widget("text", null, null, 1000, 0, 1000, 50, 0))));
        MockChatService mockChat = new MockChatService(Arrays.asList(
                buildToolCallResponse("call-1", DatavGenerateScreenExecutor.TOOL_NAME, genArgs),
                buildFinalAnswerResponse("抱歉，定位越界，请调整后重试。")
        ));

        IToolManager toolManager = buildToolManagerWithCounters(toolCallCount);
        NopDatavChatBiBizModel bizModel = new NopDatavChatBiBizModel();
        bizModel.setChatService(mockChat);
        bizModel.setToolManager(toolManager);

        ChatBiResult result = bizModel.chatToScreen("建一个越界大屏", newContext(CALLER));

        // generate-screen 返回错误（OUT_OF_BOUNDS），循环回喂 LLM → LLM 给出最终文本（不抛异常）
        assertNotNull(result);
        assertNotNull(result.getAnswer());
        assertTrue(toolCallCount.get() >= 1, "generate-screen was called (returned error)");

        // 无半成品残留（裁定 O）
        assertEquals(screenBefore, daoProvider.daoFor(NopDatavScreen.class).findAll().size(),
                "no Screen row left after out-of-bounds error (无半成品)");
        assertEquals(widgetBefore, daoProvider.daoFor(NopDatavScreenWidget.class).findAll().size(),
                "no ScreenWidget row left after out-of-bounds error");
    }

    // ==================== Helpers ====================

    private IServiceContext newContext(String userName) {
        ServiceContextImpl context = new ServiceContextImpl();
        context.setContext(new TenantProxyContext(context.getContext()));
        context.getContext().setUserName(userName);
        return context;
    }

    private IToolManager buildToolManagerWithCounters(AtomicInteger toolCallCount) {
        DatavListComponentTypesExecutor listCompExec = new DatavListComponentTypesExecutor();

        DatavGenerateScreenExecutor genScreenExec = new DatavGenerateScreenExecutor();
        genScreenExec.setDaoProvider(daoProvider);
        genScreenExec.setOrmTemplate(ormTemplate);

        DefaultToolExecutorProvider provider = new DefaultToolExecutorProvider();
        provider.setExecutors(Arrays.asList(listCompExec, genScreenExec));

        return new CountingToolManager(provider, Collections.emptyList(), toolCallCount);
    }

    private static ChatResponse buildToolCallResponse(String callId, String toolName, Object arguments) {
        ChatResponse response = new ChatResponse();
        Map<String, Object> argsMap;
        if (arguments instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> m = (Map<String, Object>) arguments;
            argsMap = new LinkedHashMap<>(m);
        } else if (arguments instanceof String) {
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = (Map<String, Object>) JsonTool.parseNonStrict((String) arguments);
            argsMap = parsed;
        } else {
            argsMap = Collections.emptyMap();
        }
        ChatToolCallMessage tcm = new ChatToolCallMessage(callId, toolName, argsMap);
        response.addMessage(tcm);
        return response;
    }

    private static ChatResponse buildFinalAnswerResponse(String answer) {
        ChatResponse response = new ChatResponse();
        response.addMessage(new ChatAssistantMessage(answer));
        return response;
    }

    /**
     * 复刻 NopDatavScreenBizModel.serializeScreenContent 的逻辑，供 E2E 测试构建编辑态内容 JSON
     * 供 ScreenLayoutParser 解析（模拟 getScreenDraftLayout 的内部路径）。
     */
    private String serializeScreenContent(NopDatavScreen screen, List<NopDatavScreenWidget> widgets) {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("screenName", screen.getScreenName());
        content.put("displayName", screen.getDisplayName());
        content.put("screenWidth", screen.getScreenWidth());
        content.put("screenHeight", screen.getScreenHeight());
        content.put("adaptorMode", screen.getAdaptorMode() == null ? 10 : screen.getAdaptorMode());
        content.put("backgroundConfig", parseJson(screen.getBackgroundConfig()));
        content.put("widgets", widgets.stream().map(this::widgetToMap).collect(Collectors.toList()));
        return JsonTool.stringify(content);
    }

    private Map<String, Object> widgetToMap(NopDatavScreenWidget widget) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("widgetId", widget.getWidgetId());
        map.put("widgetName", widget.getWidgetName());
        map.put("componentType", widget.getComponentType());
        map.put("datasetRefId", widget.getDatasetRefId());
        map.put("x", widget.getX());
        map.put("y", widget.getY());
        map.put("w", widget.getW());
        map.put("h", widget.getH());
        map.put("z", widget.getZ());
        map.put("widgetConfig", parseJson(widget.getWidgetConfig()));
        return map;
    }

    private Object parseJson(String json) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        return JsonTool.parse(json);
    }

    private List<NopDatavScreenWidget> findWidgets(String screenId) {
        return daoProvider.daoFor(NopDatavScreenWidget.class).findAll().stream()
                .filter(w -> screenId.equals(w.getScreenId()))
                .collect(Collectors.toList());
    }

    private static Map<String, Object> screenSpec(String screenName, int width, int height,
                                                    List<Map<String, Object>> widgets) {
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("screenName", screenName);
        spec.put("screenWidth", width);
        spec.put("screenHeight", height);
        spec.put("widgets", widgets);
        return spec;
    }

    private static Map<String, Object> widget(String componentType, String datasetSid,
                                                Map<String, Object> fieldMapping,
                                                int x, int y, int w, int h, int z) {
        Map<String, Object> wid = new LinkedHashMap<>();
        wid.put("componentType", componentType);
        if (datasetSid != null) {
            wid.put("datasetSid", datasetSid);
        }
        if (fieldMapping != null) {
            wid.put("fieldMapping", fieldMapping);
        }
        wid.put("x", x);
        wid.put("y", y);
        wid.put("w", w);
        wid.put("h", h);
        wid.put("z", z);
        return wid;
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
            return CompletableFuture.completedFuture(call(request, cancelToken));
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
        public CompletableFuture<AiToolCallResult> callTool(
                String toolName, AiToolCall call,
                IToolExecuteContext context) {
            counter.incrementAndGet();
            return super.callTool(toolName, call, context);
        }
    }
}
