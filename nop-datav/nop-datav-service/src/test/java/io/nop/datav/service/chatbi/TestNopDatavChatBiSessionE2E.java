package io.nop.datav.service.entity;

import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.messages.ChatMessage;
import io.nop.ai.api.chat.messages.ChatSystemMessage;
import io.nop.ai.api.chat.messages.ChatToolCallMessage;
import io.nop.ai.api.chat.messages.ChatUserMessage;
import io.nop.ai.api.chat.stream.ChatStreamChunk;
import io.nop.ai.toolkit.api.IToolManager;
import io.nop.ai.toolkit.executor.DefaultToolExecutorProvider;
import io.nop.ai.toolkit.manager.ToolManagerImpl;
import io.nop.api.core.context.TenantProxyContext;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.ICancelToken;
import io.nop.core.context.IServiceContext;
import io.nop.core.context.ServiceContextImpl;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.datav.dao.entity.NopDatavChatMessage;
import io.nop.datav.dao.entity.NopDatavChatSession;
import io.nop.datav.service.chatbi.ChatBiResult;
import io.nop.datav.service.chatbi.ChatBiSessionManager;
import io.nop.datav.service.chatbi.DatavDescribeDatasetExecutor;
import io.nop.datav.service.chatbi.DatavListDatasetsExecutor;
import io.nop.datav.service.chatbi.DatavQueryDatasetExecutor;
import io.nop.report.dao.entity.NopReportDataset;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;

import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_CHATBI_SESSION_NOT_FOUND;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ChatBI 多轮会话端到端测试（plan 2026-08-15-0004-1 Phase 4）。
 *
 * <p>完整链路：createChatSession → chatToQuery 会话模式第一轮（mock LLM tool-calling → 真实
 * IToolManager → 真实 executor → 真实 DB 查询）→ 第二轮追问（断言 LLM 请求携带第一轮历史含真实
 * 数据摘要）→ 结果落库（真实持久层）→ getChatSessionHistory → deleteChatSession → 续接显式报错。</p>
 *
 * <p><b>Anti-Hollow</b>：第一轮结果含真实 DB 数据行（非 stub）；第二轮请求消息列表经捕获断言
 * 历史注入非空壳；全链路经 BizModel 入口。</p>
 */
public class TestNopDatavChatBiSessionE2E extends AbstractNopDatavTest {

    @Inject
    IDaoProvider daoProvider;

    // jdbcTemplate 使用继承自 AbstractNopDatavTest 的同包字段（避免字段遮蔽导致基类注入为 null）

    @Test
    public void testFullSessionLifecycleEndToEnd() {
        // ---------- 准备：真实销售表 + SQL 数据集 ----------
        createSalesTable();
        insertSalesRow("north", "widget", 100);
        insertSalesRow("north", "gadget", 200);

        NopReportDataset ds = newReportDataset("ds-session-e2e", "sql",
                "select REGION as region, AMOUNT as amount "
                        + "from TEST_DATAV_SALES where REGION = ${region} order by AMOUNT desc");
        // P1-03（裁定 D4）：会话 owner alice 同时是数据集 owner（chatToQuery 会话路径传递 alice 身份，
        // 非 owner 数据集经可见性过滤不可查）
        ds.setCreatedBy("alice");
        ds.setUpdatedBy("alice");
        daoProvider.daoFor(NopReportDataset.class).saveEntityDirectly(ds);

        NopDatavChatBiBizModel bizModel = new NopDatavChatBiBizModel();
        bizModel.setSessionManager(newManager());
        bizModel.setToolManager(buildToolManager());

        IServiceContext alice = newContext("alice");

        // ---------- 1. 创建会话 ----------
        NopDatavChatSession session = bizModel.createChatSession(null, alice);
        assertNotNull(session.getSessionId());

        // ---------- 2. 第一轮：tool-calling → 真实 DB 查询 ----------
        AtomicInteger llmCalls1 = new AtomicInteger();
        RoundRobinChatService chat1 = new RoundRobinChatService(Arrays.asList(
                buildToolCallResponse("call-1", "datav-query-dataset", Map.of(
                        "datasetSid", "ds-session-e2e",
                        "params", Map.of("region", "north"))),
                buildFinalAnswerResponse("north 地区有 2 条销售记录，合计 300。")));
        chat1.counter = llmCalls1;
        bizModel.setChatService(chat1);

        ChatBiResult first = bizModel.chatToQuery("查询 north 地区的销售额", session.getSessionId(), alice);

        assertTrue(llmCalls1.get() >= 2, "tool-calling round + final answer");
        assertEquals(2, first.getRows().size(), "real DB rows returned");
        Object firstAmount = findIgnoreCase(first.getRows().get(0), "amount");
        assertEquals(200, ((Number) firstAmount).intValue(), "real DB data (ordered desc)");
        assertEquals(session.getSessionId(), first.getSessionId(), "sessionId echoed");

        // ---------- 3. 第二轮：追问（依赖第一轮上下文）→ 断言历史注入 ----------
        AtomicInteger llmCalls2 = new AtomicInteger();
        CapturingChatService chat2 = new CapturingChatService();
        chat2.counter = llmCalls2;
        chat2.response = buildFinalAnswerResponse("按月份细分：1 月 100，2 月 200。");
        bizModel.setChatService(chat2);

        ChatBiResult second = bizModel.chatToQuery("按月份细分", session.getSessionId(), alice);

        assertEquals(session.getSessionId(), second.getSessionId());
        assertEquals("按月份细分：1 月 100，2 月 200。", second.getAnswer());

        List<ChatMessage> messages = chat2.lastRequest.getMessages();
        assertEquals(4, messages.size(), "system + first-round history(user, assistant) + current user");
        assertTrue(messages.get(0) instanceof ChatSystemMessage);
        assertTrue(messages.get(1) instanceof ChatUserMessage);
        assertEquals("查询 north 地区的销售额", messages.get(1).getContent());
        assertTrue(messages.get(2) instanceof ChatAssistantMessage);
        // 第一轮 assistant 注入内容 = answer + [Result data] 真实数据摘要（引用第一轮结果的关键能力）
        String assistantContent = messages.get(2).getContent();
        assertTrue(assistantContent.contains("north 地区有 2 条销售记录"),
                "first-round answer injected: " + assistantContent);
        assertTrue(assistantContent.contains("[Result data]"), "first-round result data summary injected");
        assertTrue(assistantContent.toLowerCase().contains("amount"),
                "real DB columns in data summary (case-insensitive): " + assistantContent);
        assertTrue(messages.get(3) instanceof ChatUserMessage);
        assertEquals("按月份细分", messages.get(3).getContent());

        // ---------- 4. 落库断言：两轮 4 条消息、seq 严格递增、assistant 带 resultJson ----------
        List<NopDatavChatMessage> persisted = bizModel.getChatSessionHistory(session.getSessionId(), alice);
        assertEquals(4, persisted.size(), "2 rounds x (user + assistant)");
        for (int i = 0; i < 4; i++) {
            assertEquals(i + 1, persisted.get(i).getSeq());
        }
        assertEquals("user", persisted.get(0).getRole());
        assertEquals("assistant", persisted.get(1).getRole());
        assertNotNull(persisted.get(1).getResultJson());
        assertTrue(persisted.get(1).getResultJson().contains("rows"), "first-round real rows persisted");
        assertTrue(persisted.get(1).getResultJson().toUpperCase().contains("AMOUNT"),
                "real DB data values persisted in resultJson");
        assertEquals("user", persisted.get(2).getRole());
        assertEquals("assistant", persisted.get(3).getRole());
        assertEquals("按月份细分：1 月 100，2 月 200。", persisted.get(3).getContent());

        // 会话标题首轮回填
        NopDatavChatSession reloaded = daoProvider.daoFor(NopDatavChatSession.class)
                .getEntityById(session.getSessionId());
        assertEquals("查询 north 地区的销售额", reloaded.getSessionTitle());

        // ---------- 5. 删除会话 → 续接显式报错 ----------
        bizModel.deleteChatSession(session.getSessionId(), alice);

        NopException historyEx = assertThrows(NopException.class, () ->
                bizModel.getChatSessionHistory(session.getSessionId(), alice));
        assertEquals(ERR_DATAV_CHATBI_SESSION_NOT_FOUND.getErrorCode(), historyEx.getErrorCode());

        bizModel.setChatService(chat2);
        NopException continueEx = assertThrows(NopException.class, () ->
                bizModel.chatToQuery("再问一句", session.getSessionId(), alice));
        assertEquals(ERR_DATAV_CHATBI_SESSION_NOT_FOUND.getErrorCode(), continueEx.getErrorCode());
    }

    // ==================== Helpers ====================

    private ChatBiSessionManager newManager() {
        ChatBiSessionManager manager = new ChatBiSessionManager();
        manager.setDaoProvider(daoProvider);
        return manager;
    }

    private IToolManager buildToolManager() {
        DatavListDatasetsExecutor listExec = new DatavListDatasetsExecutor();
        listExec.setDaoProvider(daoProvider);
        DatavDescribeDatasetExecutor descExec = new DatavDescribeDatasetExecutor();
        descExec.setDaoProvider(daoProvider);
        DatavQueryDatasetExecutor queryExec = new DatavQueryDatasetExecutor();
        queryExec.setDaoProvider(daoProvider);
        queryExec.setJdbcTemplate(jdbcTemplate);

        DefaultToolExecutorProvider provider = new DefaultToolExecutorProvider();
        provider.setExecutors(Arrays.asList(listExec, descExec, queryExec));
        return new ToolManagerImpl(provider, new ArrayList<>());
    }

    private IServiceContext newContext(String userName) {
        ServiceContextImpl context = new ServiceContextImpl();
        context.setContext(new TenantProxyContext(context.getContext()));
        if (userName != null) {
            context.getContext().setUserName(userName);
        }
        return context;
    }

    private static ChatResponse buildToolCallResponse(String callId, String toolName, Map<String, Object> arguments) {
        ChatResponse response = new ChatResponse();
        response.addMessage(new ChatToolCallMessage(callId, toolName, new LinkedHashMap<>(arguments)));
        return response;
    }

    private static ChatResponse buildFinalAnswerResponse(String answer) {
        ChatResponse response = new ChatResponse();
        response.addMessage(new ChatAssistantMessage(answer));
        return response;
    }

    private static Object findIgnoreCase(Map<String, Object> map, String key) {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (key.equalsIgnoreCase(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
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

    /** 按队列顺序返回响应的 mock IChatService。 */
    private static final class RoundRobinChatService implements IChatService {
        final List<ChatResponse> responses;
        final AtomicInteger index = new AtomicInteger(0);
        AtomicInteger counter = new AtomicInteger(0);

        RoundRobinChatService(List<ChatResponse> responses) {
            this.responses = new ArrayList<>(responses);
        }

        @Override
        public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
            return CompletableFuture.completedFuture(call(request, cancelToken));
        }

        @Override
        public ChatResponse call(ChatRequest request, ICancelToken cancelToken) {
            if (counter != null) {
                counter.incrementAndGet();
            }
            int idx = index.getAndIncrement();
            return idx < responses.size() ? responses.get(idx) : buildFinalAnswerResponse("");
        }

        @Override
        public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
            throw new UnsupportedOperationException("callStream not supported in mock");
        }
    }

    /** 捕获最后一次 ChatRequest、固定返回单一最终答案的 mock IChatService。 */
    private static final class CapturingChatService implements IChatService {
        volatile ChatRequest lastRequest;
        volatile ChatResponse response;
        AtomicInteger counter = new AtomicInteger(0);

        @Override
        public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
            return CompletableFuture.completedFuture(call(request, cancelToken));
        }

        @Override
        public ChatResponse call(ChatRequest request, ICancelToken cancelToken) {
            lastRequest = request;
            if (counter != null) {
                counter.incrementAndGet();
            }
            return response;
        }

        @Override
        public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
            throw new UnsupportedOperationException("callStream not supported in mock");
        }
    }
}
