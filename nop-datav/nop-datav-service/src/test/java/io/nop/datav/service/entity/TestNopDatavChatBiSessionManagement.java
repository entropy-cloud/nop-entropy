package io.nop.datav.service.entity;

import io.nop.api.core.context.TenantProxyContext;
import io.nop.api.core.exceptions.NopException;
import io.nop.core.context.IServiceContext;
import io.nop.core.context.ServiceContextImpl;
import io.nop.dao.api.IDaoProvider;
import io.nop.datav.dao.entity.NopDatavChatMessage;
import io.nop.datav.dao.entity.NopDatavChatSession;
import io.nop.datav.service.chatbi.ChatBiResult;
import io.nop.datav.service.chatbi.ChatBiSessionManager;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_CHATBI_NOT_SESSION_OWNER;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_CHATBI_SESSION_NOT_FOUND;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ChatBI 会话管理 action focused 测试（plan 2026-08-15-0004-1 Phase 3，裁定 S4/S5）。
 *
 * <p>覆盖：createChatSession/listChatSessions/getChatSessionHistory/deleteChatSession 四 action 的
 * 正常路径 + 越权路径（非创建者读写显式拒绝）+ 已删会话路径（续接/查历史显式报错，物理删除语义）。
 * 全部经 BizModel 入口操作真实持久层（Anti-Hollow 接线验证）。</p>
 */
public class TestNopDatavChatBiSessionManagement extends AbstractNopDatavTest {

    @Inject
    IDaoProvider daoProvider;

    // ==================== 正常路径 ====================

    @Test
    public void testCreateSessionReturnsSessionOwnedByOperator() {
        NopDatavChatBiBizModel bizModel = newBizModel();

        NopDatavChatSession session = bizModel.createChatSession("销售分析", newContext("alice"));

        assertNotNull(session.getSessionId(), "sessionId generated");
        assertEquals("alice", session.getUserName(), "owner is operator userName");
        assertEquals("销售分析", session.getSessionTitle());
        // 真实落库（非内存返回）
        NopDatavChatSession persisted = daoProvider.daoFor(NopDatavChatSession.class)
                .getEntityById(session.getSessionId());
        assertNotNull(persisted, "session persisted via real DAO");
        assertEquals("alice", persisted.getUserName());
    }

    @Test
    public void testListSessionsReturnsOnlyOwnSessions() {
        NopDatavChatBiBizModel bizModel = newBizModel();
        NopDatavChatSession alice1 = bizModel.createChatSession("a1", newContext("alice"));
        NopDatavChatSession alice2 = bizModel.createChatSession("a2", newContext("alice"));
        bizModel.createChatSession("b1", newContext("bob"));

        List<NopDatavChatSession> aliceSessions = bizModel.listChatSessions(newContext("alice"));
        assertEquals(2, aliceSessions.size(), "alice sees only her own sessions");
        assertTrue(aliceSessions.stream().anyMatch(s -> s.getSessionId().equals(alice1.getSessionId())));
        assertTrue(aliceSessions.stream().anyMatch(s -> s.getSessionId().equals(alice2.getSessionId())));

        List<NopDatavChatSession> bobSessions = bizModel.listChatSessions(newContext("bob"));
        assertEquals(1, bobSessions.size(), "bob sees only his own session");
    }

    @Test
    public void testGetHistoryReturnsRetainedContent() {
        NopDatavChatBiBizModel bizModel = newBizModel();
        NopDatavChatSession session = bizModel.createChatSession(null, newContext("alice"));

        ChatBiSessionManager manager = newManager();
        ChatBiResult result = new ChatBiResult();
        result.setAnswer("合计 300");
        result.setColumns(List.of("region", "amount"));
        result.setRows(List.of(java.util.Map.of("region", "north", "amount", 300)));
        result.setIterations(1);
        manager.appendTurn(session, "查销售额", result, "alice");

        List<NopDatavChatMessage> history = bizModel.getChatSessionHistory(
                session.getSessionId(), newContext("alice"));

        assertEquals(2, history.size(), "user + assistant messages");
        assertEquals("user", history.get(0).getRole());
        assertEquals("查销售额", history.get(0).getContent());
        assertNull(history.get(0).getResultJson());
        assertEquals("assistant", history.get(1).getRole());
        assertEquals("合计 300", history.get(1).getContent());
        assertNotNull(history.get(1).getResultJson(), "retained structured result (S4 data retention)");
        assertTrue(history.get(1).getResultJson().contains("amount"));
    }

    // ==================== 越权路径：非创建者读写显式拒绝 ====================

    @Test
    public void testNonOwnerGetHistoryRejected() {
        NopDatavChatBiBizModel bizModel = newBizModel();
        NopDatavChatSession session = bizModel.createChatSession(null, newContext("alice"));

        NopException ex = assertThrows(NopException.class, () ->
                bizModel.getChatSessionHistory(session.getSessionId(), newContext("bob")));
        assertEquals(ERR_DATAV_CHATBI_NOT_SESSION_OWNER.getErrorCode(), ex.getErrorCode());
    }

    @Test
    public void testNonOwnerDeleteRejectedAndStateUnchanged() {
        NopDatavChatBiBizModel bizModel = newBizModel();
        NopDatavChatSession session = bizModel.createChatSession(null, newContext("alice"));

        NopException ex = assertThrows(NopException.class, () ->
                bizModel.deleteChatSession(session.getSessionId(), newContext("bob")));
        assertEquals(ERR_DATAV_CHATBI_NOT_SESSION_OWNER.getErrorCode(), ex.getErrorCode());

        assertNotNull(daoProvider.daoFor(NopDatavChatSession.class).getEntityById(session.getSessionId()),
                "non-owner delete attempt does not mutate state");
    }

    // ==================== 已删会话路径：显式报错 ====================

    @Test
    public void testDeleteThenHistoryAndContinuationExplicitlyFail() {
        NopDatavChatBiBizModel bizModel = newBizModel();
        NopDatavChatSession session = bizModel.createChatSession(null, newContext("alice"));

        ChatBiSessionManager manager = newManager();
        ChatBiResult result = new ChatBiResult();
        result.setAnswer("第一轮答案");
        result.setIterations(1);
        manager.appendTurn(session, "第一轮问题", result, "alice");

        // 删除（owner 本人）→ 会话与消息均物理删除
        bizModel.deleteChatSession(session.getSessionId(), newContext("alice"));
        assertNull(daoProvider.daoFor(NopDatavChatSession.class).getEntityById(session.getSessionId()),
                "session row physically deleted");
        assertTrue(daoProvider.daoFor(NopDatavChatMessage.class).findAllByQuery(
                        queryForSession(session.getSessionId())).isEmpty(),
                "messages of the session physically deleted");

        // 删除后查历史显式报错
        NopException historyEx = assertThrows(NopException.class, () ->
                bizModel.getChatSessionHistory(session.getSessionId(), newContext("alice")));
        assertEquals(ERR_DATAV_CHATBI_SESSION_NOT_FOUND.getErrorCode(), historyEx.getErrorCode());

        // 删除后续接（chatToQuery）显式报错（无静默降级单轮）
        bizModel.setChatService(newChatService("never used"));
        NopException continueEx = assertThrows(NopException.class, () ->
                bizModel.chatToQuery("继续问", session.getSessionId(), newContext("alice")));
        assertEquals(ERR_DATAV_CHATBI_SESSION_NOT_FOUND.getErrorCode(), continueEx.getErrorCode());

        // 重复删除同样显式报错（非幂等静默成功）
        NopException reDeleteEx = assertThrows(NopException.class, () ->
                bizModel.deleteChatSession(session.getSessionId(), newContext("alice")));
        assertEquals(ERR_DATAV_CHATBI_SESSION_NOT_FOUND.getErrorCode(), reDeleteEx.getErrorCode());
    }

    @Test
    public void testNonexistentSessionExplicitlyFailsForAllActions() {
        NopDatavChatBiBizModel bizModel = newBizModel();

        NopException historyEx = assertThrows(NopException.class, () ->
                bizModel.getChatSessionHistory("no-such", newContext("alice")));
        assertEquals(ERR_DATAV_CHATBI_SESSION_NOT_FOUND.getErrorCode(), historyEx.getErrorCode());

        NopException deleteEx = assertThrows(NopException.class, () ->
                bizModel.deleteChatSession("no-such", newContext("alice")));
        assertEquals(ERR_DATAV_CHATBI_SESSION_NOT_FOUND.getErrorCode(), deleteEx.getErrorCode());
    }

    // ==================== Helpers ====================

    private NopDatavChatBiBizModel newBizModel() {
        NopDatavChatBiBizModel bizModel = new NopDatavChatBiBizModel();
        bizModel.setSessionManager(newManager());
        bizModel.setToolManager(new EmptyToolManager());
        return bizModel;
    }

    private ChatBiSessionManager newManager() {
        ChatBiSessionManager manager = new ChatBiSessionManager();
        manager.setDaoProvider(daoProvider);
        return manager;
    }

    private io.nop.api.core.beans.query.QueryBean queryForSession(String sessionId) {
        io.nop.api.core.beans.query.QueryBean query = new io.nop.api.core.beans.query.QueryBean();
        query.addFilter(io.nop.api.core.beans.FilterBeans.eq("sessionId", sessionId));
        return query;
    }

    private IServiceContext newContext(String userName) {
        ServiceContextImpl context = new ServiceContextImpl();
        context.setContext(new TenantProxyContext(context.getContext()));
        if (userName != null) {
            context.getContext().setUserName(userName);
        }
        return context;
    }

    private IChatServiceShim newChatService(String answer) {
        return new IChatServiceShim(answer);
    }

    /** 最小 IChatService mock（本测试组不触发 LLM 循环，仅防 ai-absent 分支）。 */
    private static final class IChatServiceShim implements io.nop.ai.api.chat.IChatService {
        private final String answer;

        IChatServiceShim(String answer) {
            this.answer = answer;
        }

        @Override
        public io.nop.ai.api.chat.ChatResponse call(io.nop.ai.api.chat.ChatRequest request,
                                                    io.nop.api.core.util.ICancelToken cancelToken) {
            io.nop.ai.api.chat.ChatResponse response = new io.nop.ai.api.chat.ChatResponse();
            response.addMessage(new io.nop.ai.api.chat.messages.ChatAssistantMessage(answer));
            return response;
        }

        @Override
        public java.util.concurrent.CompletionStage<io.nop.ai.api.chat.ChatResponse> callAsync(
                io.nop.ai.api.chat.ChatRequest request, io.nop.api.core.util.ICancelToken cancelToken) {
            return java.util.concurrent.CompletableFuture.completedFuture(call(request, cancelToken));
        }

        @Override
        public java.util.concurrent.Flow.Publisher<io.nop.ai.api.chat.stream.ChatStreamChunk> callStream(
                io.nop.ai.api.chat.ChatRequest request, io.nop.api.core.util.ICancelToken cancelToken) {
            throw new UnsupportedOperationException("callStream not supported in shim");
        }
    }

    /** 空工具清单 mock（本测试组无 tool-call 路径）。 */
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
