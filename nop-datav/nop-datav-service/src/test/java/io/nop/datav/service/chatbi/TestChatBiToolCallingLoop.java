package io.nop.datav.service.entity;

import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.api.chat.messages.ChatToolCallMessage;
import io.nop.ai.api.chat.stream.ChatStreamChunk;
import io.nop.ai.toolkit.api.IToolExecuteContext;
import io.nop.ai.toolkit.api.IToolManager;
import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;
import io.nop.ai.toolkit.model.AiToolCalls;
import io.nop.ai.toolkit.model.AiToolCallsResponse;
import io.nop.ai.toolkit.model.AiToolModel;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.ICancelToken;
import io.nop.datav.service.chatbi.ChatBiToolCallingLoop;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

import static io.nop.datav.service.NopDatavErrors.ARG_REASON;
import static io.nop.datav.service.NopDatavErrors.ARG_TOOL_NAME;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_CHATBI_TOOL_EXECUTION_FAILED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ChatBiToolCallingLoop} AR-6 focused unit test。
 *
 * <p>mock {@link IChatService}（返回一个 tool call）+ mock {@link IToolManager}（返回失败/取消的
 * {@code CompletableFuture}），验证 {@code .join()} 失败路径被结构化包装为
 * {@code ERR_DATAV_CHATBI_TOOL_EXECUTION_FAILED}（携带 {@code ARG_TOOL_NAME} + {@code ARG_REASON}），
 * 不再让原始 {@code CompletionException}/{@code CancellationException} 泄漏到公共 GraphQL action。</p>
 *
 * <p><b>Anti-Hollow</b>：断言抛出的 {@link NopException} 的 errorCode、{@code toolName}、{@code reason}
 * 三个 param 均被填充（非空壳包装），且 cause 被解包保留。继承 {@link AbstractNopDatavTest} 仅为初始化
 * Nop 平台（JSON provider 等，循环内 {@code getArgumentsText()} 依赖），不接触真实 chat/DB 基础设施。</p>
 */
public class TestChatBiToolCallingLoop extends AbstractNopDatavTest {

    private static final String TOOL_NAME = "datav-query-dataset";

    // ==================== executor 失败（CompletionException）→ 结构化 NopException ====================

    @Test
    public void testJoinFailureWrapsAsStructuredNopException() {
        IChatService chatService = new SingleToolCallChatService();
        IToolManager toolManager = new FailingToolManager(
                CompletableFuture.failedFuture(new RuntimeException("infra")));

        ChatBiToolCallingLoop loop = new ChatBiToolCallingLoop(chatService, toolManager);

        NopException ex = assertThrows(NopException.class, () -> loop.run("any question", 3));

        assertEquals(ERR_DATAV_CHATBI_TOOL_EXECUTION_FAILED.getErrorCode(), ex.getErrorCode(),
                "errorCode must be ERR_DATAV_CHATBI_TOOL_EXECUTION_FAILED (not raw CompletionException)");
        assertEquals(TOOL_NAME, ex.getParam(ARG_TOOL_NAME),
                "ARG_TOOL_NAME must be populated with the failing tool name");
        assertEquals("infra", ex.getParam(ARG_REASON),
                "ARG_REASON must be populated with the underlying cause message");
        assertTrue(ex.getCause() instanceof RuntimeException,
                "underlying cause must be preserved for stacktrace debugging");
    }

    // ==================== 取消的 future（CancellationException）→ 结构化 NopException ====================

    @Test
    public void testCancelledFutureWrapsAsStructuredNopException() {
        IChatService chatService = new SingleToolCallChatService();
        // 取消的 future 上 .join() 直接抛 CancellationException（不包 CompletionException）
        CompletableFuture<AiToolCallResult> cancelled = new CompletableFuture<>();
        cancelled.cancel(true);
        IToolManager toolManager = new FailingToolManager(cancelled);

        ChatBiToolCallingLoop loop = new ChatBiToolCallingLoop(chatService, toolManager);

        NopException ex = assertThrows(NopException.class, () -> loop.run("any question", 3));

        assertEquals(ERR_DATAV_CHATBI_TOOL_EXECUTION_FAILED.getErrorCode(), ex.getErrorCode(),
                "cancelled future must also be wrapped as ERR_DATAV_CHATBI_TOOL_EXECUTION_FAILED");
        assertEquals(TOOL_NAME, ex.getParam(ARG_TOOL_NAME),
                "ARG_TOOL_NAME must be populated");
        // reason 非空：message 有值时取 message（JDK 26 上为 "join"），message 为 null 时回退到类名
        Object reason = ex.getParam(ARG_REASON);
        assertTrue(reason != null && !reason.toString().isEmpty(),
                "ARG_REASON must be populated (message or fallback class name): " + reason);
        assertTrue(ex.getCause() instanceof java.util.concurrent.CancellationException,
                "cause must be preserved as the raw CancellationException (caught via the CancellationException branch)");
    }

    // ==================== 嵌套 CompletionException 解包到真实 cause ====================

    @Test
    public void testChainedCompletionExceptionUnwrappedToRealCause() {
        IChatService chatService = new SingleToolCallChatService();
        RuntimeException rootCause = new IllegalStateException("db-down");
        CompletableFuture<AiToolCallResult> failed = CompletableFuture.failedFuture(rootCause);
        IToolManager toolManager = new FailingToolManager(failed);

        ChatBiToolCallingLoop loop = new ChatBiToolCallingLoop(chatService, toolManager);

        NopException ex = assertThrows(NopException.class, () -> loop.run("any question", 3));

        assertEquals(ERR_DATAV_CHATBI_TOOL_EXECUTION_FAILED.getErrorCode(), ex.getErrorCode());
        assertEquals("db-down", ex.getParam(ARG_REASON),
                "ARG_REASON must reflect the unwrapped root cause message (not CompletionException wrapper)");
        assertEquals(rootCause, ex.getCause(),
                "cause must be the unwrapped root cause (IllegalStateException), not the CompletionException wrapper");
    }

    // ==================== Mocks ====================

    /**
     * 返回恰好一个 tool call 的 mock IChatService。
     */
    private static final class SingleToolCallChatService implements IChatService {
        @Override
        public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
            return CompletableFuture.completedFuture(call(request, cancelToken));
        }

        @Override
        public ChatResponse call(ChatRequest request, ICancelToken cancelToken) {
            Map<String, Object> args = new LinkedHashMap<>();
            args.put("datasetSid", "ds-anything");
            ChatToolCallMessage tcm = new ChatToolCallMessage("call-1", TOOL_NAME, args);
            ChatResponse response = new ChatResponse();
            response.addMessage(tcm);
            return response;
        }

        @Override
        public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
            throw new UnsupportedOperationException("callStream not supported in mock");
        }
    }

    /**
     * {@code callTool} 返回预设（失败/取消）future 的 mock IToolManager。其余方法返回空/null。
     */
    private static final class FailingToolManager implements IToolManager {
        private final CompletableFuture<AiToolCallResult> callToolFuture;

        FailingToolManager(CompletableFuture<AiToolCallResult> callToolFuture) {
            this.callToolFuture = callToolFuture;
        }

        @Override
        public CompletableFuture<AiToolCallResult> callTool(String toolName, AiToolCall call,
                                                              IToolExecuteContext context) {
            return callToolFuture;
        }

        @Override
        public CompletableFuture<AiToolCallsResponse> callTools(AiToolCalls calls, IToolExecuteContext context) {
            throw new UnsupportedOperationException("callTools not used in this test");
        }

        @Override
        public List<AiToolModel> listTools() {
            return Collections.emptyList();
        }

        @Override
        public AiToolModel loadTool(String toolName) {
            return null;
        }
    }
}
