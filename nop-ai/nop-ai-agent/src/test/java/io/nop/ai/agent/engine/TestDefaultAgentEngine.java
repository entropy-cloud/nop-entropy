package io.nop.ai.agent.engine;

import io.nop.ai.agent.compact.MicroCompressionCompactor;
import io.nop.ai.agent.router.PassThroughModelRouter;
import io.nop.ai.agent.guardrail.NoOpContentGuardrail;
import io.nop.ai.agent.security.DefaultPathAccessChecker;
import io.nop.ai.agent.security.DefaultToolAccessChecker;
import io.nop.ai.agent.security.AllowAllPermissionProvider;
import io.nop.ai.agent.session.InMemorySessionStore;
import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.stream.ChatStreamChunk;
import io.nop.ai.toolkit.api.IToolExecuteContext;
import io.nop.ai.toolkit.api.IToolManager;
import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;
import io.nop.ai.toolkit.model.AiToolModel;
import io.nop.api.core.util.ICancelToken;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestDefaultAgentEngine {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    private ChatResponse buildSuccessResponse(String content) {
        ChatAssistantMessage msg = new ChatAssistantMessage();
        msg.setContent(content);
        return ChatResponse.success(msg);
    }

    private IChatService createMockChatService(ChatResponse response) {
        return new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                return CompletableFuture.completedFuture(response);
            }

            @Override
            public ChatResponse call(ChatRequest request, ICancelToken cancelToken) {
                return response;
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {};
            }
        };
    }

    /**
     * 时间确定化（plan 2511-WI5）：模拟的 LLM 延迟改为显式 latch 交接——call 阻塞在
     * latch 上（超时仅挂死保护），测试断言 ack 先行返回后 countDown 放行后台执行。
     */
    private IChatService createGatedMockChatService(ChatResponse response, CountDownLatch release) {
        return new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                return CompletableFuture.completedFuture(response);
            }

            @Override
            public ChatResponse call(ChatRequest request, ICancelToken cancelToken) {
                try {
                    release.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return response;
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {};
            }
        };
    }

    private IToolManager createNoOpToolManager() {
        return new IToolManager() {
            @Override
            public CompletableFuture<AiToolCallResult> callTool(String toolName, AiToolCall call, IToolExecuteContext context) {
                return CompletableFuture.completedFuture(AiToolCallResult.successResult(0, ""));
            }

            @Override
            public CompletableFuture<io.nop.ai.toolkit.model.AiToolCallsResponse> callTools(
                    io.nop.ai.toolkit.model.AiToolCalls calls, IToolExecuteContext context) {
                return null;
            }

            @Override
            public List<AiToolModel> listTools() {
                return Collections.emptyList();
            }

            @Override
            public AiToolModel loadTool(String toolName) {
                AiToolModel model = new AiToolModel();
                model.setName(toolName);
                model.setDescription("Mock tool: " + toolName);
                return model;
            }
        };
    }

    @Test
    void testSendMessageReturnsAck() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        IChatService chatService = createGatedMockChatService(
                buildSuccessResponse("Hello!"), release);
        IToolManager toolManager = createNoOpToolManager();
        DefaultAgentEngine engine = new DefaultAgentEngine.Builder(chatService, toolManager).sessionStore(new InMemorySessionStore()).permissionProvider(new AllowAllPermissionProvider()).toolAccessChecker(new DefaultToolAccessChecker()).pathAccessChecker(new DefaultPathAccessChecker()).contentGuardrail(NoOpContentGuardrail.noOp()).modelRouter(PassThroughModelRouter.passThrough()).contextCompactor(new MicroCompressionCompactor()).build();

        AgentMessageRequest request = new AgentMessageRequest("test-agent", "hello");
        AgentMessageAck ack = engine.sendMessage(request);

        assertNotNull(ack);
        assertNotNull(ack.getSessionId());
        assertTrue(!ack.getSessionId().isEmpty());
        assertEquals("accepted", ack.getStatus());
        // ack 已先行返回，放行被 latch 阻塞的后台 LLM 调用
        release.countDown();
    }

    @Test
    void testExecuteReturnsResult() throws Exception {
        IChatService chatService = createMockChatService(buildSuccessResponse("Hello!"));
        IToolManager toolManager = createNoOpToolManager();
        DefaultAgentEngine engine = new DefaultAgentEngine.Builder(chatService, toolManager).sessionStore(new InMemorySessionStore()).permissionProvider(new AllowAllPermissionProvider()).toolAccessChecker(new DefaultToolAccessChecker()).pathAccessChecker(new DefaultPathAccessChecker()).contentGuardrail(NoOpContentGuardrail.noOp()).modelRouter(PassThroughModelRouter.passThrough()).contextCompactor(new MicroCompressionCompactor()).build();

        AgentMessageRequest request = new AgentMessageRequest("test-agent", "hello");
        CompletableFuture<AgentExecutionResult> future = engine.execute(request);
        AgentExecutionResult result = future.get(10, TimeUnit.SECONDS);

        assertEquals(AgentExecStatus.completed, result.getStatus());
        assertNotNull(result.getSessionId());
        assertTrue(!result.getSessionId().isEmpty());
    }

    @Test
    void testNewSessionGeneration() throws Exception {
        IChatService chatService = createMockChatService(buildSuccessResponse("Hi"));
        IToolManager toolManager = createNoOpToolManager();
        DefaultAgentEngine engine = new DefaultAgentEngine.Builder(chatService, toolManager).sessionStore(new InMemorySessionStore()).permissionProvider(new AllowAllPermissionProvider()).toolAccessChecker(new DefaultToolAccessChecker()).pathAccessChecker(new DefaultPathAccessChecker()).contentGuardrail(NoOpContentGuardrail.noOp()).modelRouter(PassThroughModelRouter.passThrough()).contextCompactor(new MicroCompressionCompactor()).build();

        AgentMessageRequest request = new AgentMessageRequest("test-agent", "hello", null, null);
        CompletableFuture<AgentExecutionResult> future = engine.execute(request);
        AgentExecutionResult result = future.get(10, TimeUnit.SECONDS);

        assertNotNull(result.getSessionId());
        assertTrue(!result.getSessionId().isEmpty());
    }

    @Test
    void testExistingSessionReuse() throws Exception {
        IChatService chatService = createMockChatService(buildSuccessResponse("Hi"));
        IToolManager toolManager = createNoOpToolManager();
        DefaultAgentEngine engine = new DefaultAgentEngine.Builder(chatService, toolManager).sessionStore(new InMemorySessionStore()).permissionProvider(new AllowAllPermissionProvider()).toolAccessChecker(new DefaultToolAccessChecker()).pathAccessChecker(new DefaultPathAccessChecker()).contentGuardrail(NoOpContentGuardrail.noOp()).modelRouter(PassThroughModelRouter.passThrough()).contextCompactor(new MicroCompressionCompactor()).build();

        AgentMessageRequest request = new AgentMessageRequest("test-agent", "hello", "my-session", null);
        CompletableFuture<AgentExecutionResult> future = engine.execute(request);
        AgentExecutionResult result = future.get(10, TimeUnit.SECONDS);

        assertEquals("my-session", result.getSessionId());
    }

    @Test
    void testAgentModelLoadingFromDsl() throws Exception {
        IChatService chatService = createMockChatService(buildSuccessResponse("Response"));
        IToolManager toolManager = createNoOpToolManager();
        DefaultAgentEngine engine = new DefaultAgentEngine.Builder(chatService, toolManager).sessionStore(new InMemorySessionStore()).permissionProvider(new AllowAllPermissionProvider()).toolAccessChecker(new DefaultToolAccessChecker()).pathAccessChecker(new DefaultPathAccessChecker()).contentGuardrail(NoOpContentGuardrail.noOp()).modelRouter(PassThroughModelRouter.passThrough()).contextCompactor(new MicroCompressionCompactor()).build();

        AgentMessageRequest request = new AgentMessageRequest("test-agent", "hello");
        CompletableFuture<AgentExecutionResult> future = engine.execute(request);
        AgentExecutionResult result = future.get(10, TimeUnit.SECONDS);

        assertEquals(AgentExecStatus.completed, result.getStatus());

        boolean hasSystemPrompt = result.getMessages().stream()
                .anyMatch(m -> m.getContent() != null && m.getContent().contains("You are a helpful assistant."));
        assertTrue(hasSystemPrompt);
    }

    @Test
    void testAgentModelNotFound() {
        IChatService chatService = createMockChatService(buildSuccessResponse("Response"));
        IToolManager toolManager = createNoOpToolManager();
        DefaultAgentEngine engine = new DefaultAgentEngine.Builder(chatService, toolManager).sessionStore(new InMemorySessionStore()).permissionProvider(new AllowAllPermissionProvider()).toolAccessChecker(new DefaultToolAccessChecker()).pathAccessChecker(new DefaultPathAccessChecker()).contentGuardrail(NoOpContentGuardrail.noOp()).modelRouter(PassThroughModelRouter.passThrough()).contextCompactor(new MicroCompressionCompactor()).build();

        AgentMessageRequest request = new AgentMessageRequest("non-existent-agent", "hello");

        Exception exception = assertThrows(Exception.class, () -> engine.execute(request));
        String message = exception.getMessage();
        assertTrue(message.contains("non-existent-agent"),
                "Error message should contain 'non-existent-agent', got: " + message);
    }

    @Test
    void testSendMessageAsyncExecution() throws Exception {
        AtomicInteger callCount = new AtomicInteger(0);
        AtomicBoolean chatServiceCalled = new AtomicBoolean(false);

        IChatService chatService = new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                return CompletableFuture.completedFuture(buildSuccessResponse("Response"));
            }

            @Override
            public ChatResponse call(ChatRequest request, ICancelToken cancelToken) {
                chatServiceCalled.set(true);
                callCount.incrementAndGet();
                return buildSuccessResponse("Response");
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {};
            }
        };

        IToolManager toolManager = createNoOpToolManager();
        DefaultAgentEngine engine = new DefaultAgentEngine.Builder(chatService, toolManager).sessionStore(new InMemorySessionStore()).permissionProvider(new AllowAllPermissionProvider()).toolAccessChecker(new DefaultToolAccessChecker()).pathAccessChecker(new DefaultPathAccessChecker()).contentGuardrail(NoOpContentGuardrail.noOp()).modelRouter(PassThroughModelRouter.passThrough()).contextCompactor(new MicroCompressionCompactor()).build();

        AgentMessageRequest request = new AgentMessageRequest("test-agent", "hello");
        CompletableFuture<AgentExecutionResult> future = engine.execute(request);
        AgentExecutionResult result = future.get(10, TimeUnit.SECONDS);

        assertEquals(AgentExecStatus.completed, result.getStatus());
        assertTrue(chatServiceCalled.get(), "ChatService.call should have been invoked");
        assertEquals(1, callCount.get());
    }

    @Test
    void testCallAgentTimeoutMsDefaultsAgreeAcrossConstructionPaths() {
        IChatService chatService = createMockChatService(buildSuccessResponse("Response"));
        IToolManager toolManager = createNoOpToolManager();

        DefaultAgentEngine constructorEngine = new DefaultAgentEngine.Builder(chatService, toolManager).sessionStore(new InMemorySessionStore()).permissionProvider(new AllowAllPermissionProvider()).toolAccessChecker(new DefaultToolAccessChecker()).pathAccessChecker(new DefaultPathAccessChecker()).contentGuardrail(NoOpContentGuardrail.noOp()).modelRouter(PassThroughModelRouter.passThrough()).contextCompactor(new MicroCompressionCompactor()).build();
        DefaultAgentEngine builderEngine = DefaultAgentEngine.builder(chatService, toolManager).build();

        assertEquals(constructorEngine.config().getCallAgentTimeoutMs(), builderEngine.config().getCallAgentTimeoutMs(),
                "constructor-chain and Builder construction paths must agree on callAgentTimeoutMs (MA4.5-004)");
        assertEquals(120_000L, constructorEngine.config().getCallAgentTimeoutMs());
    }

    @Test
    void testCallAgentTimeoutMsRejectsNonPositive() {
        IChatService chatService = createMockChatService(buildSuccessResponse("Response"));
        IToolManager toolManager = createNoOpToolManager();
        DefaultAgentEngine engine = new DefaultAgentEngine.Builder(chatService, toolManager).sessionStore(new InMemorySessionStore()).permissionProvider(new AllowAllPermissionProvider()).toolAccessChecker(new DefaultToolAccessChecker()).pathAccessChecker(new DefaultPathAccessChecker()).contentGuardrail(NoOpContentGuardrail.noOp()).modelRouter(PassThroughModelRouter.passThrough()).contextCompactor(new MicroCompressionCompactor()).build();

        assertThrows(Exception.class, () -> engine.config().setCallAgentTimeoutMs(0));
        assertThrows(Exception.class, () -> engine.config().setCallAgentTimeoutMs(-1));
    }
}
