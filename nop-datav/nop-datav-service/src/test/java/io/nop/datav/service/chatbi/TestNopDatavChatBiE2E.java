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
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.ICancelToken;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.api.IDaoProvider;
import io.nop.datav.service.chatbi.ChatBiResult;
import io.nop.datav.service.chatbi.ChatBiToolExecuteContext;
import io.nop.datav.service.chatbi.DatavDescribeDatasetExecutor;
import io.nop.datav.service.chatbi.DatavListDatasetsExecutor;
import io.nop.datav.service.chatbi.DatavQueryDatasetExecutor;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ChatBI 端到端测试（D6-1 Phase 4）。
 *
 * <p>验证完整链路：ChatBI action → mock IChatService（模拟 LLM tool-calling）→ 真实 IToolManager
 * → 真实 executor → 真实 DB 查询 → 结果回传。</p>
 *
 * <p><b>Anti-Hollow</b>：断言（a）{@code IChatService.call} 被调用 ≥ 1 次、（b）{@code IToolManager.callTool}
 * 被调用 ≥ 1 次、（c）最终结果含真实数据行（非 stub/placeholder）。</p>
 *
 * <p>覆盖：ai-present 多轮 tool-calling / ai-absent / max-iterations / tool-error 四个分支（无静默跳过）。</p>
 */
public class TestNopDatavChatBiE2E extends AbstractNopDatavTest {

    @Inject
    IDaoProvider daoProvider;

    // ==================== 主线 E2E：多轮 tool-calling → 真实查询结果 ====================

    @Test
    public void testE2eChatToQueryMultiRoundToolCalling() {
        createSalesTable();
        insertSalesRow("north", "widget", 100);
        insertSalesRow("north", "gadget", 200);

        NopReportDataset ds = newReportDataset("ds-e2e-chatbi", "sql",
                "select REGION as region, AMOUNT as amount "
                        + "from TEST_DATAV_SALES where REGION = ${region} order by AMOUNT desc");
        daoProvider.daoFor(NopReportDataset.class).saveEntityDirectly(ds);

        // mock LLM 调用计数器
        AtomicInteger llmCallCount = new AtomicInteger(0);
        // toolManager callTool 计数器
        AtomicInteger toolCallCount = new AtomicInteger(0);

        // round 1: LLM 返回 datav-query-dataset tool call
        // round 2: LLM 返回最终文本答案（无 tool calls）
        MockChatService mockChat = new MockChatService(Arrays.asList(
                buildToolCallResponse("call-1", "datav-query-dataset", Map.of(
                        "datasetSid", "ds-e2e-chatbi",
                        "params", Map.of("region", "north"))),
                buildFinalAnswerResponse("查询结果：north 地区有 2 条销售记录。")
        ));
        mockChat.callCounter = llmCallCount;

        IToolManager toolManager = buildToolManagerWithCounters(toolCallCount);

        NopDatavChatBiBizModel bizModel = new NopDatavChatBiBizModel();
        bizModel.setChatService(mockChat);
        bizModel.setToolManager(toolManager);

        ChatBiResult result = bizModel.chatToQuery("查询 north 地区的销售额");

        // Anti-Hollow (a): IChatService.call 被调用 ≥ 1 次
        assertTrue(llmCallCount.get() >= 1, "IChatService.call must be invoked at least once");
        // Anti-Hollow (b): IToolManager.callTool 被调用 ≥ 1 次
        assertTrue(toolCallCount.get() >= 1, "IToolManager.callTool must be invoked at least once");

        // Anti-Hollow (c): 最终结果含真实数据行
        assertNotNull(result);
        assertNotNull(result.getAnswer());
        assertTrue(result.getAnswer().contains("north"), "final answer should mention north");
        assertEquals(2, result.getRows().size(), "result contains real query rows (2 north rows)");
        assertTrue(containsIgnoreCase(result.getColumns(), "region"));
        assertTrue(containsIgnoreCase(result.getColumns(), "amount"));
        // ordered desc: first row amount=200 (real DB data, not stub)
        Object firstAmount = findCaseInsensitive(result.getRows().get(0), "amount");
        assertEquals(200, ((Number) firstAmount).intValue(), "first row is real DB data (amount=200, ordered desc)");
    }

    // ==================== ai-absent 分支：显式失败 ====================

    @Test
    public void testAiAbsentThrowsExplicitError() {
        NopDatavChatBiBizModel bizModel = new NopDatavChatBiBizModel();
        // 不注入 IChatService/IToolManager → 保持 null
        bizModel.setChatService(null);
        bizModel.setToolManager(null);

        NopException ex = assertThrows(NopException.class, () ->
                bizModel.chatToQuery("any question"));

        assertEquals("nop.err.datav.chatbi-ai-not-available", ex.getErrorCode());
    }

    // ==================== max-iterations 分支：超限抛异常 ====================

    @Test
    public void testMaxIterationsExceeded() {
        createSalesTable();
        NopReportDataset ds = newReportDataset("ds-maxiter", "sql",
                "select 1 as one from TEST_DATAV_SALES where REGION = ${region}");
        daoProvider.daoFor(NopReportDataset.class).saveEntityDirectly(ds);

        // mock LLM 永远返回 tool call → 永不给出最终答案 → 超限
        AtomicInteger toolCallCount = new AtomicInteger(0);
        MockChatService alwaysToolCall = new MockChatService(Collections.singletonList(
                buildToolCallResponse("call-loop", "datav-query-dataset", Map.of(
                        "datasetSid", "ds-maxiter",
                        "params", Map.of("region", "north")))
        ));
        alwaysToolCall.repeatLast = true;

        IToolManager toolManager = buildToolManagerWithCounters(toolCallCount);

        NopDatavChatBiBizModel bizModel = new NopDatavChatBiBizModel();
        bizModel.setChatService(alwaysToolCall);
        bizModel.setToolManager(toolManager);

        // maxIterations 默认 5（CFG_DATAV_CHATBI_MAX_ITERATIONS），mock 永远返回 tool call → 超限
        NopException ex = assertThrows(NopException.class, () ->
                bizModel.chatToQuery("loop question"));

        assertEquals("nop.err.datav.chatbi-max-iterations-exceeded", ex.getErrorCode());
        assertTrue(toolCallCount.get() >= 1, "at least one tool call before exceeding max iterations");
    }

    // ==================== tool-error 分支：错误透传到 LLM ====================

    @Test
    public void testToolErrorIsPropagatedAsToolResponse() {
        AtomicInteger llmCallCount = new AtomicInteger(0);
        AtomicInteger toolCallCount = new AtomicInteger(0);

        // round 1: LLM 请求查不存在的数据集 → executor 返回错误
        // round 2: LLM 收到错误 tool response 后给出最终文本（向用户报告错误）
        MockChatService mockChat = new MockChatService(Arrays.asList(
                buildToolCallResponse("call-err", "datav-query-dataset", Map.of(
                        "datasetSid", "nonexistent-dataset",
                        "params", Map.of())),
                buildFinalAnswerResponse("抱歉，找不到该数据集。")
        ));
        mockChat.callCounter = llmCallCount;

        IToolManager toolManager = buildToolManagerWithCounters(toolCallCount);

        NopDatavChatBiBizModel bizModel = new NopDatavChatBiBizModel();
        bizModel.setChatService(mockChat);
        bizModel.setToolManager(toolManager);

        ChatBiResult result = bizModel.chatToQuery("查一个不存在的数据集");

        // executor 返回错误（status=failure），但循环不抛——错误作为 tool response 回喂 LLM，
        // LLM 给出最终文本答案（无静默跳过）
        assertNotNull(result);
        assertNotNull(result.getAnswer());
        assertTrue(llmCallCount.get() >= 2, "two LLM rounds: tool-call + final-answer");
        assertTrue(toolCallCount.get() >= 1, "tool was called (returned error)");
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

        DefaultToolExecutorProvider provider = new DefaultToolExecutorProvider();
        provider.setExecutors(Arrays.asList(listExec, descExec, queryExec));

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

    // ==================== Mock IChatService ====================

    /**
     * 模拟 LLM 的 IChatService。按预设队列返回 ChatResponse；{@code repeatLast=true} 时
     * 永远重复最后一个 response（用于 max-iterations 测试）。
     */
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
            // 默认 fallback：返回最终空答案
            return buildFinalAnswerResponse("");
        }

        @Override
        public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
            throw new UnsupportedOperationException("callStream not supported in mock");
        }
    }

    /**
     * IToolManager 包装器，计数 {@code callTool} 调用次数（Anti-Hollow 断言用）。
     */
    private static class CountingToolManager extends ToolManagerImpl {
        private final AtomicInteger counter;

        CountingToolManager(DefaultToolExecutorProvider provider,
                            List<IToolCallInterceptor> interceptors,
                            AtomicInteger counter) {
            super(provider, interceptors);
            this.counter = counter;
        }

        @Override
        public java.util.concurrent.CompletableFuture<io.nop.ai.toolkit.model.AiToolCallResult> callTool(
                String toolName, io.nop.ai.toolkit.model.AiToolCall call,
                io.nop.ai.toolkit.api.IToolExecuteContext context) {
            counter.incrementAndGet();
            return super.callTool(toolName, call, context);
        }
    }
}
