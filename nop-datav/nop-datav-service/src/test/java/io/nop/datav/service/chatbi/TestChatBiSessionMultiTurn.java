package io.nop.datav.service.entity;

import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.messages.ChatMessage;
import io.nop.ai.api.chat.messages.ChatSystemMessage;
import io.nop.ai.api.chat.messages.ChatUserMessage;
import io.nop.ai.api.chat.stream.ChatStreamChunk;
import io.nop.api.core.context.TenantProxyContext;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.ICancelToken;
import io.nop.core.context.IServiceContext;
import io.nop.core.context.ServiceContextImpl;
import io.nop.dao.api.IDaoProvider;
import io.nop.datav.dao.entity.NopDatavChatMessage;
import io.nop.datav.dao.entity.NopDatavChatSession;
import io.nop.datav.service.chatbi.ChatBiResult;
import io.nop.datav.service.chatbi.ChatBiSessionManager;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;

import static io.nop.datav.service.NopDatavConfigs.CFG_DATAV_CHATBI_HISTORY_MAX_CHARS;
import static io.nop.datav.service.NopDatavConfigs.CFG_DATAV_CHATBI_HISTORY_MAX_TURNS;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_CHATBI_NOT_SESSION_OWNER;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_CHATBI_SESSION_NOT_FOUND;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ChatBI 多轮会话 focused 测试（plan 2026-08-15-0004-1 Phase 2，裁定 S1–S4）。
 *
 * <p>覆盖：多轮历史注入 LLM 请求（可观测断言：捕获 ChatRequest.messages）、上界截断（轮数 + 字符预算
 * + 最老优先丢弃 + 单条超预算截断）、无会话参数单轮回归不变、跨「请求边界」持久化续接、
 * 引用不存在/非本人会话显式抛错（无静默降级单轮）。</p>
 *
 * <p><b>Anti-Hollow</b>：会话读写全部经真实持久层（{@link IDaoProvider} + 本地 H2，继承
 * {@link AbstractNopDatavTest} 自动建表），非内存 map 假实现；历史注入经捕获的 LLM 请求消息列表断言
 * （非空壳注入）。</p>
 */
public class TestChatBiSessionMultiTurn extends AbstractNopDatavTest {

    @Inject
    IDaoProvider daoProvider;

    // ==================== 多轮历史注入：第二轮 LLM 请求包含第一轮问答 ====================

    @Test
    public void testSecondTurnLlmRequestContainsFirstTurnHistory() {
        NopDatavChatBiBizModel bizModel = newBizModel();

        NopDatavChatSession session = bizModel.createChatSession(null, newContext("alice"));

        // 第一轮：LLM 直接给最终答案（无 tool call）
        CapturingChatService chat1 = new CapturingChatService();
        chat1.responses.add(finalAnswer("north 地区销售额合计 300。"));
        bizModel.setChatService(chat1);
        ChatBiResult first = bizModel.chatToQuery("查询 north 地区的销售额",
                session.getSessionId(), newContext("alice"));
        assertEquals(session.getSessionId(), first.getSessionId(), "session mode echoes sessionId");

        // 第二轮：捕获 LLM 请求消息列表，断言历史确实注入
        CapturingChatService chat2 = new CapturingChatService();
        chat2.responses.add(finalAnswer("按月份细分结果……"));
        bizModel.setChatService(chat2);
        ChatBiResult second = bizModel.chatToQuery("按月份细分",
                session.getSessionId(), newContext("alice"));

        assertNotNull(second);
        assertEquals(session.getSessionId(), second.getSessionId());

        List<ChatMessage> messages = chat2.lastRequest.getMessages();
        // 结构：system + history(user1, assistant1) + 本轮 user2
        assertEquals(4, messages.size(), "system + 2 history + current user");
        assertTrue(messages.get(0) instanceof ChatSystemMessage, "system prompt first");
        assertTrue(messages.get(1) instanceof ChatUserMessage);
        assertEquals("查询 north 地区的销售额", messages.get(1).getContent());
        assertTrue(messages.get(2) instanceof ChatAssistantMessage);
        assertEquals("north 地区销售额合计 300。", messages.get(2).getContent());
        assertTrue(messages.get(3) instanceof ChatUserMessage);
        assertEquals("按月份细分", messages.get(3).getContent());
    }

    // ==================== 上界截断：轮数上界 + 字符预算（最老优先丢弃） ====================

    @Test
    public void testHistoryTruncatedByMaxTurns() {
        ChatBiSessionManager manager = newManager();
        NopDatavChatSession session = manager.createSession(null, "alice");
        appendRounds(manager, session, 5);

        List<ChatMessage> ctx = manager.buildHistoryContext(
                manager.loadMessages(session.getSessionId()), 2, 1_000_000);

        // 最近 2 轮 = user4/assistant4/user5/assistant5（最老 3 轮被丢弃）
        assertEquals(4, ctx.size());
        assertEquals("question-4", ctx.get(0).getContent());
        assertEquals("answer-4", ctx.get(1).getContent());
        assertEquals("question-5", ctx.get(2).getContent());
        assertEquals("answer-5", ctx.get(3).getContent());
    }

    @Test
    public void testHistoryTruncatedByCharBudgetOldestFirst() {
        ChatBiSessionManager manager = newManager();
        NopDatavChatSession session = manager.createSession(null, "alice");
        appendRounds(manager, session, 3);

        // 渲染后每条：user="question-N"(10 字符)、assistant="answer-N"(8 字符，空结果无数据摘要)。
        // 预算 40：从最新累计 a3(8)+u3(10)+a2(8)+u2(10)=36 ≤ 40，a1(8) → 44 > 40 停 → 最老一轮被丢弃
        List<ChatMessage> ctx = manager.buildHistoryContext(
                manager.loadMessages(session.getSessionId()), 10, 40);
        assertEquals(4, ctx.size(), "char budget drops the oldest round");
        assertEquals("question-2", ctx.get(0).getContent(), "round-1 dropped (oldest first)");
        assertEquals("answer-2", ctx.get(1).getContent());

        // 预算仅容纳最新一条（8 = "answer-3" 恰好放下，再老一条即超）
        List<ChatMessage> onlyNewest = manager.buildHistoryContext(
                manager.loadMessages(session.getSessionId()), 10, 8);
        assertEquals(1, onlyNewest.size(), "only the newest message survives");
        assertTrue(onlyNewest.get(0) instanceof ChatAssistantMessage);
        assertEquals("answer-3", onlyNewest.get(0).getContent());

        // 最新单条单独超预算：截断该条至预算长度（保证至少一条历史存活）
        List<ChatMessage> truncated = manager.buildHistoryContext(
                manager.loadMessages(session.getSessionId()), 10, 5);
        assertEquals(1, truncated.size());
        assertEquals(5, truncated.get(0).getContent().length(), "oversized newest truncated to budget");
    }

    @Test
    public void testAssistantHistoryIncludesResultDataSummary() {
        ChatBiSessionManager manager = newManager();
        NopDatavChatSession session = manager.createSession(null, "alice");

        ChatBiResult result = new ChatBiResult();
        result.setAnswer("done");
        result.setColumns(List.of("region", "amount"));
        result.setRows(List.of(java.util.Map.of("region", "north", "amount", 100)));
        result.setIterations(1);
        manager.appendTurn(session, "查销售额", result, "alice");

        List<ChatMessage> ctx = manager.buildHistoryContext(
                manager.loadMessages(session.getSessionId()), 10, 1_000_000);
        assertEquals(2, ctx.size());
        String assistantContent = ctx.get(1).getContent();
        assertTrue(assistantContent.startsWith("done"), "answer text first");
        assertTrue(assistantContent.contains("[Result data]"), "compact result JSON appended");
        assertTrue(assistantContent.contains("region"), "result JSON contains columns/rows");
    }

    // ==================== 单轮回归：无会话参数行为与现状一致 ====================

    @Test
    public void testSingleTurnWithoutSessionUnchanged() {
        NopDatavChatBiBizModel bizModel = newBizModel();
        CapturingChatService chat = new CapturingChatService();
        chat.responses.add(finalAnswer("单轮答案。"));
        bizModel.setChatService(chat);

        ChatBiResult result = bizModel.chatToQuery("单轮问题", null, null);

        assertNull(result.getSessionId(), "single-turn result carries no sessionId");
        // LLM 请求仅 system + 本轮用户消息（无历史注入、无会话痕迹）
        List<ChatMessage> messages = chat.lastRequest.getMessages();
        assertEquals(2, messages.size(), "system + current user only");
        assertTrue(messages.get(0) instanceof ChatSystemMessage);
        assertTrue(messages.get(1) instanceof ChatUserMessage);
        assertEquals("单轮问题", messages.get(1).getContent());
        // 空会话参数（""）同样走单轮路径
        // 未触碰会话表：无任何会话/消息行
        assertTrue(daoProvider.daoFor(NopDatavChatSession.class).findAllByQuery(new io.nop.api.core.beans.query.QueryBean()).isEmpty(),
                "no session rows written in single-turn mode");
        assertTrue(daoProvider.daoFor(NopDatavChatMessage.class).findAllByQuery(new io.nop.api.core.beans.query.QueryBean()).isEmpty(),
                "no message rows written in single-turn mode");
    }

    // ==================== 跨请求边界持久化续接 ====================

    @Test
    public void testSessionHistoryPersistedAcrossRequests() {
        // 两次独立调用（独立 BizModel 实例，共享同一持久层与 manager）= 模拟两次 HTTP 请求
        ChatBiSessionManager manager = newManager();
        NopDatavChatSession session = manager.createSession(null, "alice");

        NopDatavChatBiBizModel firstCall = newBizModel(manager);
        CapturingChatService chat1 = new CapturingChatService();
        chat1.responses.add(finalAnswer("第一轮答案"));
        firstCall.setChatService(chat1);
        firstCall.chatToQuery("第一轮问题", session.getSessionId(), newContext("alice"));

        NopDatavChatBiBizModel secondCall = newBizModel(manager);
        CapturingChatService chat2 = new CapturingChatService();
        chat2.responses.add(finalAnswer("第二轮答案"));
        secondCall.setChatService(chat2);
        secondCall.chatToQuery("第二轮问题", session.getSessionId(), newContext("alice"));

        // DB 断言：4 条消息（2 轮 × user+assistant），seq 严格递增 1..4，assistant 带 resultJson
        List<NopDatavChatMessage> persisted = manager.loadMessages(session.getSessionId());
        assertEquals(4, persisted.size());
        for (int i = 0; i < 4; i++) {
            assertEquals(i + 1, persisted.get(i).getSeq(), "seq strictly increasing from 1");
        }
        assertEquals("user", persisted.get(0).getRole());
        assertEquals("第一轮问题", persisted.get(0).getContent());
        assertNull(persisted.get(0).getResultJson(), "user message has no resultJson");
        assertEquals("assistant", persisted.get(1).getRole());
        assertEquals("第一轮答案", persisted.get(1).getContent());
        assertNotNull(persisted.get(1).getResultJson(), "assistant message persists resultJson");
        assertTrue(persisted.get(1).getResultJson().contains("iterations"));
        assertEquals("user", persisted.get(2).getRole());

        // 会话标题首轮回填（裁定 S1）
        NopDatavChatSession reloaded = daoProvider.daoFor(NopDatavChatSession.class)
                .getEntityById(session.getSessionId());
        assertEquals("第一轮问题", reloaded.getSessionTitle(), "title backfilled from first question");
    }

    // ==================== 显式失败：不存在/非本人会话（无静默降级单轮） ====================

    @Test
    public void testNonexistentSessionThrowsExplicitError() {
        NopDatavChatBiBizModel bizModel = newBizModel();
        CapturingChatService chat = new CapturingChatService();
        chat.responses.add(finalAnswer("never used"));
        bizModel.setChatService(chat);

        NopException ex = assertThrows(NopException.class, () ->
                bizModel.chatToQuery("问题", "no-such-session", newContext("alice")));
        assertEquals(ERR_DATAV_CHATBI_SESSION_NOT_FOUND.getErrorCode(), ex.getErrorCode());

        assertEquals(0, chat.callCount.get(), "LLM must not be invoked when session is invalid");
    }

    @Test
    public void testNonOwnerSessionThrowsExplicitError() {
        NopDatavChatBiBizModel bizModel = newBizModel();
        CapturingChatService chat = new CapturingChatService();
        chat.responses.add(finalAnswer("never used"));
        bizModel.setChatService(chat);
        NopDatavChatSession session = bizModel.createChatSession(null, newContext("alice"));

        NopException ex = assertThrows(NopException.class, () ->
                bizModel.chatToQuery("越权问题", session.getSessionId(), newContext("bob")));
        assertEquals(ERR_DATAV_CHATBI_NOT_SESSION_OWNER.getErrorCode(), ex.getErrorCode());

        assertEquals(0, chat.callCount.get(), "LLM must not be invoked for non-owner");
    }

    @Test
    public void testConfigUpperBoundWiredFromNopDatavConfigs() {
        // 配置项真实接入 chatToQuery 路径：maxTurns=1 时第三轮请求只含最近 1 轮（第二轮）历史
        Integer origTurns = CFG_DATAV_CHATBI_HISTORY_MAX_TURNS.get();
        io.nop.api.core.config.AppConfig.getConfigProvider().updateConfigValue(CFG_DATAV_CHATBI_HISTORY_MAX_TURNS, 1);
        try {
            NopDatavChatBiBizModel bizModel = newBizModel();
            NopDatavChatSession session = bizModel.createChatSession(null, newContext("alice"));

            for (int turn = 1; turn <= 3; turn++) {
                CapturingChatService chat = new CapturingChatService();
                chat.responses.add(finalAnswer("第" + turn + "轮答案"));
                bizModel.setChatService(chat);
                bizModel.chatToQuery("第" + turn + "轮问题", session.getSessionId(), newContext("alice"));

                if (turn == 3) {
                    // maxTurns=1：system + 最近一轮(u2,a2) + 本轮 u3 = 4 条；第一轮被丢弃（配置生效证明）
                    List<ChatMessage> messages = chat.lastRequest.getMessages();
                    assertEquals(4, messages.size(), "maxTurns=1 keeps only the latest completed round");
                    assertEquals("第2轮问题", messages.get(1).getContent(),
                            "config maxTurns=1 drops older rounds (wired into chatToQuery)");
                    assertEquals("第2轮答案", messages.get(2).getContent());
                }
            }
        } finally {
            io.nop.api.core.config.AppConfig.getConfigProvider().updateConfigValue(CFG_DATAV_CHATBI_HISTORY_MAX_TURNS, origTurns);
        }
    }

    // ==================== Helpers ====================

    private NopDatavChatBiBizModel newBizModel() {
        return newBizModel(newManager());
    }

    private NopDatavChatBiBizModel newBizModel(ChatBiSessionManager manager) {
        NopDatavChatBiBizModel bizModel = new NopDatavChatBiBizModel();
        bizModel.setSessionManager(manager);
        // toolManager 为 null 时 ai-absent 分支先抛；查询路径注入一个空 tool manager 避免 NPE
        bizModel.setToolManager(new EmptyToolManager());
        return bizModel;
    }

    private ChatBiSessionManager newManager() {
        ChatBiSessionManager manager = new ChatBiSessionManager();
        manager.setDaoProvider(daoProvider);
        return manager;
    }

    private IServiceContext newContext(String userName) {
        ServiceContextImpl context = new ServiceContextImpl();
        context.setContext(new TenantProxyContext(context.getContext()));
        if (userName != null) {
            context.getContext().setUserName(userName);
        }
        return context;
    }

    private void appendRounds(ChatBiSessionManager manager, NopDatavChatSession session, int rounds) {
        for (int i = 1; i <= rounds; i++) {
            ChatBiResult result = new ChatBiResult();
            result.setAnswer("answer-" + i);
            result.setIterations(1);
            manager.appendTurn(session, "question-" + i, result, "alice");
        }
    }

    private static ChatResponse finalAnswer(String answer) {
        ChatResponse response = new ChatResponse();
        response.addMessage(new ChatAssistantMessage(answer));
        return response;
    }

    /**
     * 记录最后一次 ChatRequest 并按队列返回预设最终答案的 mock IChatService。
     */
    private static final class CapturingChatService implements IChatService {
        final List<ChatResponse> responses = new ArrayList<>();
        final AtomicInteger callCount = new AtomicInteger(0);
        volatile ChatRequest lastRequest;

        @Override
        public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
            return CompletableFuture.completedFuture(call(request, cancelToken));
        }

        @Override
        public ChatResponse call(ChatRequest request, ICancelToken cancelToken) {
            lastRequest = request;
            callCount.incrementAndGet();
            int idx = Math.min(callCount.get() - 1, responses.size() - 1);
            return idx >= 0 ? responses.get(idx) : finalAnswer("");
        }

        @Override
        public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
            throw new UnsupportedOperationException("callStream not supported in mock");
        }
    }

    /**
     * 空工具清单的 mock IToolManager（本测试组无 tool-call 路径）。
     */
    private static final class EmptyToolManager implements io.nop.ai.toolkit.api.IToolManager {
        @Override
        public java.util.concurrent.CompletableFuture<io.nop.ai.toolkit.model.AiToolCallResult> callTool(
                String toolName, io.nop.ai.toolkit.model.AiToolCall call,
                io.nop.ai.toolkit.api.IToolExecuteContext context) {
            throw new UnsupportedOperationException("no tool call expected in this test");
        }

        @Override
        public java.util.concurrent.CompletableFuture<io.nop.ai.toolkit.model.AiToolCallsResponse> callTools(
                io.nop.ai.toolkit.model.AiToolCalls calls, io.nop.ai.toolkit.api.IToolExecuteContext context) {
            throw new UnsupportedOperationException("callTools not used in this test");
        }

        @Override
        public List<io.nop.ai.toolkit.model.AiToolModel> listTools() {
            return List.of();
        }

        @Override
        public io.nop.ai.toolkit.model.AiToolModel loadTool(String toolName) {
            return null;
        }
    }
}
