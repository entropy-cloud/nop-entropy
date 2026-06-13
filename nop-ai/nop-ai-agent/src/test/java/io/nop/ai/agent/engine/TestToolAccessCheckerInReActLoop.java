package io.nop.ai.agent.engine;

import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.model.AgentModel;
import io.nop.ai.agent.security.AllowAllPermissionProvider;
import io.nop.ai.agent.security.DefaultToolAccessChecker;
import io.nop.ai.agent.security.IPermissionProvider;
import io.nop.ai.agent.security.IToolAccessChecker;
import io.nop.ai.agent.security.Permission;
import io.nop.ai.agent.security.ToolAccessResult;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.messages.ChatToolCall;
import io.nop.ai.api.chat.messages.ChatToolResponseMessage;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestToolAccessCheckerInReActLoop {

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

    private ChatResponse buildSuccessResponseWithToolCalls(List<ChatToolCall> toolCalls) {
        ChatAssistantMessage msg = new ChatAssistantMessage();
        msg.setContent("");
        msg.setToolCalls(toolCalls);
        return ChatResponse.success(msg);
    }

    @Test
    void testHardcodedDenyBlocksEvenWhenPermissionProviderAllows() {
        AgentModel model = new AgentModel();
        model.setTools(Set.of("bash"));
        AgentExecutionContext ctx = AgentExecutionContext.create(model, "test-session");

        IPermissionProvider allowAll = new AllowAllPermissionProvider();
        IToolAccessChecker checker = new DefaultToolAccessChecker();

        ChatToolCall toolCall = new ChatToolCall();
        toolCall.setId("call_1");
        toolCall.setName("bash");
        toolCall.setArguments(Map.of("cmd", "rm -rf /"));

        AtomicInteger llmCallCount = new AtomicInteger(0);
        IChatService chatService = new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                int n = llmCallCount.getAndIncrement();
                if (n == 0) {
                    return CompletableFuture.completedFuture(buildSuccessResponseWithToolCalls(List.of(toolCall)));
                }
                return CompletableFuture.completedFuture(buildSuccessResponse("Denied by access checker."));
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {};
            }
        };

        AtomicBoolean toolCalled = new AtomicBoolean(false);
        IToolManager toolManager = new IToolManager() {
            @Override
            public CompletableFuture<AiToolCallResult> callTool(String name, AiToolCall call, IToolExecuteContext context) {
                toolCalled.set(true);
                return CompletableFuture.completedFuture(AiToolCallResult.successResult(0, "should not be called"));
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
                AiToolModel m = new AiToolModel();
                m.setName(toolName);
                m.setDescription("Mock: " + toolName);
                return m;
            }
        };

        ReActAgentExecutor executor = ReActAgentExecutor.builder().chatService(chatService).toolManager(toolManager)
                .permissionProvider(allowAll).toolAccessChecker(checker).build();
        AgentExecutionResult result = executor.execute(ctx).toCompletableFuture().join();

        assertEquals(AgentExecStatus.completed, result.getStatus());
        assertTrue(toolCalled.get() == false, "Tool should NOT have been called — hardcoded deny should block it");

        ChatToolResponseMessage toolResp = result.getMessages().stream()
                .filter(m -> m instanceof ChatToolResponseMessage)
                .map(m -> (ChatToolResponseMessage) m)
                .findFirst().orElse(null);
        assertNotNull(toolResp);
        assertTrue(toolResp.getContent().contains("Access denied"));
        assertTrue(toolResp.getContent().contains("bash"));
    }

    @Test
    void testToolCallDeniedEventPublishedWithHardcodedDenyReason() {
        AgentModel model = new AgentModel();
        model.setTools(Set.of("delete-file"));
        AgentExecutionContext ctx = AgentExecutionContext.create(model, "test-session");

        IPermissionProvider allowAll = new AllowAllPermissionProvider();
        IToolAccessChecker checker = new DefaultToolAccessChecker();

        ChatToolCall toolCall = new ChatToolCall();
        toolCall.setId("call_1");
        toolCall.setName("delete-file");
        toolCall.setArguments(Map.of("path", "/etc/passwd"));

        AtomicInteger llmCallCount = new AtomicInteger(0);
        IChatService chatService = new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                int n = llmCallCount.getAndIncrement();
                if (n == 0) {
                    return CompletableFuture.completedFuture(buildSuccessResponseWithToolCalls(List.of(toolCall)));
                }
                return CompletableFuture.completedFuture(buildSuccessResponse("Understood."));
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {};
            }
        };

        IToolManager toolManager = new IToolManager() {
            @Override
            public CompletableFuture<AiToolCallResult> callTool(String name, AiToolCall call, IToolExecuteContext context) {
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
                AiToolModel m = new AiToolModel();
                m.setName(toolName);
                m.setDescription("Mock");
                return m;
            }
        };

        List<AgentEvent> events = new ArrayList<>();
        DefaultAgentEventPublisher publisher = new DefaultAgentEventPublisher();
        publisher.addSubscriber(event -> events.add(event));

        ReActAgentExecutor executor = ReActAgentExecutor.builder().chatService(chatService).toolManager(toolManager)
                .eventPublisher(publisher)
                .permissionProvider(allowAll).toolAccessChecker(checker).build();
        executor.execute(ctx).toCompletableFuture().join();

        AgentEvent deniedEvent = events.stream()
                .filter(e -> e.getEventType() == AgentEventType.TOOL_CALL_DENIED)
                .findFirst().orElse(null);
        assertNotNull(deniedEvent, "TOOL_CALL_DENIED event should be published");
        String reason = (String) deniedEvent.getPayload().get("reason");
        assertNotNull(reason);
        assertTrue(reason.contains("delete-file"));
    }

    @Test
    void testNonDeniedToolProceedsToPermissionProvider() {
        AgentModel model = new AgentModel();
        model.setTools(Set.of("calculator"));
        AgentExecutionContext ctx = AgentExecutionContext.create(model, "test-session");

        IPermissionProvider denyAll = new IPermissionProvider() {
            @Override
            public Permission resolve(String toolName, String agentName, String sessionId) {
                return Permission.deny("Permission provider denied");
            }
        };
        IToolAccessChecker checker = new DefaultToolAccessChecker();

        ChatToolCall toolCall = new ChatToolCall();
        toolCall.setId("call_1");
        toolCall.setName("calculator");
        toolCall.setArguments(Map.of("expr", "2+2"));

        AtomicInteger llmCallCount = new AtomicInteger(0);
        IChatService chatService = new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                int n = llmCallCount.getAndIncrement();
                if (n == 0) {
                    return CompletableFuture.completedFuture(buildSuccessResponseWithToolCalls(List.of(toolCall)));
                }
                return CompletableFuture.completedFuture(buildSuccessResponse("Denied by permission provider."));
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {};
            }
        };

        AtomicBoolean toolCalled = new AtomicBoolean(false);
        IToolManager toolManager = new IToolManager() {
            @Override
            public CompletableFuture<AiToolCallResult> callTool(String name, AiToolCall call, IToolExecuteContext context) {
                toolCalled.set(true);
                return CompletableFuture.completedFuture(AiToolCallResult.successResult(0, "should not be called"));
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
                AiToolModel m = new AiToolModel();
                m.setName(toolName);
                m.setDescription("Mock: " + toolName);
                return m;
            }
        };

        ReActAgentExecutor executor = ReActAgentExecutor.builder().chatService(chatService).toolManager(toolManager)
                .permissionProvider(denyAll).toolAccessChecker(checker).build();
        AgentExecutionResult result = executor.execute(ctx).toCompletableFuture().join();

        assertEquals(AgentExecStatus.completed, result.getStatus());
        assertTrue(toolCalled.get() == false, "Tool should NOT have been called — permission provider denied it");

        ChatToolResponseMessage toolResp = result.getMessages().stream()
                .filter(m -> m instanceof ChatToolResponseMessage)
                .map(m -> (ChatToolResponseMessage) m)
                .findFirst().orElse(null);
        assertNotNull(toolResp);
        assertTrue(toolResp.getContent().contains("Permission denied"),
                "Should be blocked by permission provider, not access checker. Got: " + toolResp.getContent());
    }

    @Test
    void testBothAllowToolExecutes() {
        AgentModel model = new AgentModel();
        model.setTools(Set.of("calculator"));
        AgentExecutionContext ctx = AgentExecutionContext.create(model, "test-session");

        IPermissionProvider allowAll = new AllowAllPermissionProvider();
        IToolAccessChecker checker = new DefaultToolAccessChecker();

        ChatToolCall toolCall = new ChatToolCall();
        toolCall.setId("call_1");
        toolCall.setName("calculator");
        toolCall.setArguments(Map.of("expr", "2+2"));

        AtomicInteger llmCallCount = new AtomicInteger(0);
        IChatService chatService = new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                int n = llmCallCount.getAndIncrement();
                if (n == 0) {
                    return CompletableFuture.completedFuture(buildSuccessResponseWithToolCalls(List.of(toolCall)));
                }
                return CompletableFuture.completedFuture(buildSuccessResponse("Result is 4"));
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {};
            }
        };

        IToolManager toolManager = new IToolManager() {
            @Override
            public CompletableFuture<AiToolCallResult> callTool(String name, AiToolCall call, IToolExecuteContext context) {
                return CompletableFuture.completedFuture(AiToolCallResult.successResult(0, "4"));
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
                AiToolModel m = new AiToolModel();
                m.setName(toolName);
                m.setDescription("Mock: " + toolName);
                return m;
            }
        };

        ReActAgentExecutor executor = ReActAgentExecutor.builder().chatService(chatService).toolManager(toolManager)
                .permissionProvider(allowAll).toolAccessChecker(checker).build();
        AgentExecutionResult result = executor.execute(ctx).toCompletableFuture().join();

        assertEquals(AgentExecStatus.completed, result.getStatus());

        ChatToolResponseMessage toolResp = result.getMessages().stream()
                .filter(m -> m instanceof ChatToolResponseMessage)
                .map(m -> (ChatToolResponseMessage) m)
                .findFirst().orElse(null);
        assertNotNull(toolResp);
        assertEquals("4", toolResp.getContent());
    }
}
