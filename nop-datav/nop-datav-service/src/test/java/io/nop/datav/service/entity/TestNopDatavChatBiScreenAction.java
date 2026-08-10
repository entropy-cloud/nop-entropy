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
import io.nop.datav.dao.entity.NopDatavScreen;
import io.nop.datav.dao.entity.NopDatavScreenWidget;
import io.nop.datav.service.chatbi.ChatBiResult;
import io.nop.datav.service.chatbi.ChatBiSystemPrompt;
import io.nop.datav.service.chatbi.ChatBiToolCallingLoop;
import io.nop.datav.service.chatbi.DatavGenerateScreenExecutor;
import io.nop.datav.service.chatbi.DatavListComponentTypesExecutor;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code chatToScreen} action 单元测试（D6-2 Phase 3 Exit Criteria）。
 *
 * <p>覆盖：ai-present 多轮 tool-calling（list-component-types → generate-screen）→ 真实 screenId 落库 +
 * operator 传递（createdBy）/ ai-absent 显式失败 / max-iterations 超限 / 校验反馈循环（越界 → recover）/
 * system prompt 含约束（禁止 SQL + 草稿 + 不越界）。</p>
 *
 * <p>接线验证（rule #23）：mock LLM 返回 generate-screen tool call 时，DatavGenerateScreenExecutor 被调用。</p>
 */
public class TestNopDatavChatBiScreenAction extends AbstractNopDatavTest {

    private static final String OPERATOR = "screen-action-user";

    @Inject
    IDaoProvider daoProvider;

    @Inject
    io.nop.orm.IOrmTemplate ormTemplate;

    // ==================== 主线：ai-present 多轮 → 真实 screenId 落库 + operator 传递 ====================

    @Test
    public void testChatToScreenMultiRoundCreatesDraftScreen() {
        NopReportDataset ds = newActiveSqlDataset("ds-screen-action",
                "select REGION as region, AMOUNT as amount from TEST_SCREEN_SALES2",
                fieldsMeta("region", "amount"));
        daoProvider.daoFor(NopReportDataset.class).saveEntityDirectly(ds);

        AtomicInteger llmCallCount = new AtomicInteger(0);
        AtomicInteger toolCallCount = new AtomicInteger(0);

        // round 1: LLM 返回 list-component-types tool call
        // round 2: LLM 返回 generate-screen tool call（合法规格）
        // round 3: LLM 返回最终文本答案
        MockChatService mockChat = new MockChatService(Arrays.asList(
                buildToolCallResponse("call-1", "datav-list-component-types", Collections.emptyMap()),
                buildToolCallResponse("call-2", "datav-generate-screen", screenSpec("Action Screen", 1920, 1080,
                        Arrays.asList(
                                widget("chart", "ds-screen-action", fieldMapping("x", "region", "y", "amount"),
                                        0, 100, 900, 400, 1),
                                widget("decorative-border", null, null, 0, 0, 1920, 80, 0)
                        ))),
                buildFinalAnswerResponse("已为您创建经营 KPI 大屏草稿，请审阅后发布。")
        ));
        mockChat.callCounter = llmCallCount;

        IToolManager toolManager = buildToolManagerWithCounters(toolCallCount);

        NopDatavChatBiBizModel bizModel = new NopDatavChatBiBizModel();
        bizModel.setChatService(mockChat);
        bizModel.setToolManager(toolManager);

        ChatBiResult result = bizModel.chatToScreen("建一个经营 KPI 大屏，1920x1080",
                newContext(OPERATOR));

        // Anti-Hollow: IChatService.call + IToolManager.callTool 被调用
        assertTrue(llmCallCount.get() >= 1, "IChatService.call must be invoked at least once");
        assertTrue(toolCallCount.get() >= 1, "IToolManager.callTool must be invoked at least once");

        // 返回 screenId（非空）
        assertNotNull(result);
        assertNotNull(result.getAnswer());
        String screenId = result.getCreatedEntityId();
        assertNotNull(screenId, "createdEntityId must be the screenId");

        // screenId 指向真实 DB 行（Anti-Hollow）
        NopDatavScreen screen = daoProvider.daoFor(NopDatavScreen.class).getEntityById(screenId);
        assertNotNull(screen, "Screen row must exist in DB (Anti-Hollow)");
        assertEquals(0, screen.getPublishStatus(), "publishStatus=DRAFT (0)");
        assertEquals(OPERATOR, screen.getCreatedBy(), "createdBy=operator (裁定 G operator 传递)");

        // 断言 widget 落库 + 定位正确
        List<NopDatavScreenWidget> widgets = daoProvider.daoFor(NopDatavScreenWidget.class).findAll().stream()
                .filter(w -> screenId.equals(w.getScreenId()))
                .collect(java.util.stream.Collectors.toList());
        assertEquals(2, widgets.size(), "2 widgets created");
        assertFalse(widgets.stream().noneMatch(w -> "chart".equals(w.getComponentType())),
                "chart widget exists");
        assertFalse(widgets.stream().noneMatch(w -> "decorative-border".equals(w.getComponentType())),
                "decorative-border widget exists (大屏可用全部 14 类)");
    }

    // ==================== ai-absent：显式失败 ====================

    @Test
    public void testAiAbsentThrowsExplicitError() {
        NopDatavChatBiBizModel bizModel = new NopDatavChatBiBizModel();
        bizModel.setChatService(null);
        bizModel.setToolManager(null);

        NopException ex = assertThrows(NopException.class, () ->
                bizModel.chatToScreen("any description", newContext(OPERATOR)));

        assertEquals("nop.err.datav.chatbi-ai-not-available", ex.getErrorCode());
    }

    // ==================== max-iterations：超限抛异常 ====================

    @Test
    public void testMaxIterationsExceeded() {
        MockChatService alwaysToolCall = new MockChatService(Collections.singletonList(
                buildToolCallResponse("call-loop", "datav-list-component-types", Collections.emptyMap())
        ));
        alwaysToolCall.repeatLast = true;

        AtomicInteger toolCallCount = new AtomicInteger(0);
        IToolManager toolManager = buildToolManagerWithCounters(toolCallCount);

        NopDatavChatBiBizModel bizModel = new NopDatavChatBiBizModel();
        bizModel.setChatService(alwaysToolCall);
        bizModel.setToolManager(toolManager);

        NopException ex = assertThrows(NopException.class, () ->
                bizModel.chatToScreen("loop question", newContext(OPERATOR)));

        assertEquals("nop.err.datav.chatbi-max-iterations-exceeded", ex.getErrorCode());
    }

    // ==================== 校验反馈循环：越界 → recover ====================

    @Test
    public void testOutOfBoundsThenRecover() {
        NopReportDataset ds = newActiveSqlDataset("ds-screen-recover",
                "select REGION as region from TEST_SCREEN_SALES2", fieldsMeta("region"));
        daoProvider.daoFor(NopReportDataset.class).saveEntityDirectly(ds);

        AtomicInteger toolCallCount = new AtomicInteger(0);

        // round 1: LLM 产出越界规格（x+w > screenWidth）→ generate-screen 返回错误
        // round 2: LLM 修正后重新调用 generate-screen → 成功
        // round 3: LLM 给出最终答案
        MockChatService mockChat = new MockChatService(Arrays.asList(
                buildToolCallResponse("call-1", "datav-generate-screen", screenSpec("Recover Screen", 1920, 1080,
                        Collections.singletonList(
                                widget("text", null, null, 1000, 0, 1000, 50, 0) // 越界
                        ))),
                buildToolCallResponse("call-2", "datav-generate-screen", screenSpec("Recover Screen", 1920, 1080,
                        Collections.singletonList(
                                widget("text", null, null, 0, 0, 1000, 50, 0) // 修正后合法
                        ))),
                buildFinalAnswerResponse("已修正定位并创建大屏。")
        ));

        IToolManager toolManager = buildToolManagerWithCounters(toolCallCount);

        NopDatavChatBiBizModel bizModel = new NopDatavChatBiBizModel();
        bizModel.setChatService(mockChat);
        bizModel.setToolManager(toolManager);

        ChatBiResult result = bizModel.chatToScreen("建一个大屏", newContext(OPERATOR));

        assertNotNull(result);
        assertNotNull(result.getCreatedEntityId(), "screenId returned after recover");
        assertTrue(toolCallCount.get() >= 2, "at least 2 tool calls (error + success)");
    }

    // ==================== system prompt 含约束（可观测） ====================

    @Test
    public void testSystemPromptContainsSafetyConstraints() {
        String prompt = ChatBiSystemPrompt.buildScreenSystemPrompt();
        assertNotNull(prompt);
        // 禁止生成 SQL 约束
        assertTrue(prompt.contains("MUST NOT generate, write, or execute raw SQL"),
                "system prompt must contain 'no SQL' constraint");
        // 产出草稿不自动发布约束
        assertTrue(prompt.contains("DRAFT") && prompt.contains("MUST NOT claim it is published"),
                "system prompt must contain 'draft / no auto-publish' constraint");
        // widget 不可越界约束（裁定 L）
        assertTrue(prompt.contains("MUST NOT exceed canvas bounds"),
                "system prompt must contain 'no out-of-bounds' constraint");
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

    private static ChatResponse buildToolCallResponse(String callId, String toolName, Map<String, Object> arguments) {
        ChatResponse response = new ChatResponse();
        ChatToolCallMessage tcm = new ChatToolCallMessage(callId, toolName, new LinkedHashMap<>(arguments));
        response.addMessage(tcm);
        return response;
    }

    private static ChatResponse buildFinalAnswerResponse(String answer) {
        ChatResponse response = new ChatResponse();
        response.addMessage(new ChatAssistantMessage(answer));
        return response;
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
        java.util.List<Map<String, Object>> fields = new java.util.ArrayList<>();
        for (String n : names) {
            Map<String, Object> f = new LinkedHashMap<>();
            f.put("name", n);
            f.put("type", "string");
            fields.add(f);
        }
        return JsonTool.stringify(java.util.Collections.singletonMap("fields", fields));
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
        ds.setCreatedBy("test");
        ds.setCreateTime(new Timestamp(now));
        ds.setUpdatedBy("test");
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
