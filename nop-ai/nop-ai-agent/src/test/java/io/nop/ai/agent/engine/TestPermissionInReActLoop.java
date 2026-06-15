package io.nop.ai.agent.engine;

import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.model.AgentModel;
import io.nop.ai.agent.model.AgentPermissionModel;
import io.nop.ai.agent.security.AllowAllToolAccessChecker;
import io.nop.ai.agent.security.DefaultPermissionProvider;
import io.nop.ai.agent.security.IPermissionProvider;
import io.nop.ai.agent.security.Permission;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestPermissionInReActLoop {

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
    void testDeniedToolProducesErrorResponse() {
        AgentModel model = new AgentModel();
        model.setTools(Set.of("bash", "calculator"));
        AgentExecutionContext ctx = AgentExecutionContext.create(model, "test-session");

        AgentPermissionModel denyRule = new AgentPermissionModel();
        denyRule.setId("deny-shell");
        denyRule.setResource("bash");
        denyRule.setAction("deny");

        AgentPermissionModel allowRule = new AgentPermissionModel();
        allowRule.setId("allow-all");
        allowRule.setResource("*");
        allowRule.setAction("allow");

        DefaultPermissionProvider permProvider = new DefaultPermissionProvider();
        permProvider.configure(List.of(denyRule, allowRule), null);

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
                return CompletableFuture.completedFuture(buildSuccessResponse("Tool was denied, I cannot proceed."));
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

        // Test allow path: isolate permission-provider deny behavior by passing
        // AllowAllToolAccessChecker explicitly, so the deny comes from the
        // permission provider (not from DefaultToolAccessChecker's hardcoded
        // deny-list which would also deny "bash" with a different message).
        ReActAgentExecutor executor = ReActAgentExecutor.builder().chatService(chatService).toolManager(toolManager)
                .permissionProvider(permProvider)
                .toolAccessChecker(new AllowAllToolAccessChecker())
                .build();
        AgentExecutionResult result = executor.execute(ctx).toCompletableFuture().join();

        assertEquals(AgentExecStatus.completed, result.getStatus());
        assertTrue(toolCalled.get() == false, "Tool should NOT have been called");

        ChatToolResponseMessage toolResp = result.getMessages().stream()
                .filter(m -> m instanceof ChatToolResponseMessage)
                .map(m -> (ChatToolResponseMessage) m)
                .findFirst().orElse(null);
        assertNotNull(toolResp);
        assertTrue(toolResp.getContent().contains("Permission denied"));
        assertEquals(2, llmCallCount.get());
    }

    @Test
    void testAllowedToolExecutesNormally() {
        AgentModel model = new AgentModel();
        model.setTools(Set.of("calculator"));
        AgentExecutionContext ctx = AgentExecutionContext.create(model, "test-session");

        AgentPermissionModel allowRule = new AgentPermissionModel();
        allowRule.setId("allow-calc");
        allowRule.setResource("calculator");
        allowRule.setAction("allow");

        DefaultPermissionProvider permProvider = new DefaultPermissionProvider();
        permProvider.configure(List.of(allowRule), null);

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
                .permissionProvider(permProvider).build();
        AgentExecutionResult result = executor.execute(ctx).toCompletableFuture().join();

        assertEquals(AgentExecStatus.completed, result.getStatus());

        ChatToolResponseMessage toolResp = result.getMessages().stream()
                .filter(m -> m instanceof ChatToolResponseMessage)
                .map(m -> (ChatToolResponseMessage) m)
                .findFirst().orElse(null);
        assertNotNull(toolResp);
        assertEquals("4", toolResp.getContent());
    }

    @Test
    void testPartialDenialMultipleToolCalls() {
        AgentModel model = new AgentModel();
        model.setTools(Set.of("calculator", "dangerous-tool", "echo"));
        AgentExecutionContext ctx = AgentExecutionContext.create(model, "test-session");

        AgentPermissionModel denyRule = new AgentPermissionModel();
        denyRule.setId("deny-dangerous");
        denyRule.setResource("dangerous-tool");
        denyRule.setAction("deny");

        AgentPermissionModel allowRule = new AgentPermissionModel();
        allowRule.setId("allow-all");
        allowRule.setResource("*");
        allowRule.setAction("allow");

        DefaultPermissionProvider permProvider = new DefaultPermissionProvider();
        permProvider.configure(List.of(denyRule, allowRule), null);

        ChatToolCall call1 = new ChatToolCall();
        call1.setId("call_1");
        call1.setName("calculator");
        call1.setArguments(Map.of("expr", "1+1"));

        ChatToolCall call2 = new ChatToolCall();
        call2.setId("call_2");
        call2.setName("dangerous-tool");
        call2.setArguments(Map.of("cmd", "rm"));

        ChatToolCall call3 = new ChatToolCall();
        call3.setId("call_3");
        call3.setName("echo");
        call3.setArguments(Map.of("msg", "hello"));

        AtomicInteger llmCallCount = new AtomicInteger(0);
        IChatService chatService = new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                int n = llmCallCount.getAndIncrement();
                if (n == 0) {
                    return CompletableFuture.completedFuture(
                            buildSuccessResponseWithToolCalls(List.of(call1, call2, call3)));
                }
                return CompletableFuture.completedFuture(buildSuccessResponse("Done with partial denial"));
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {};
            }
        };

        List<String> calledTools = new ArrayList<>();
        IToolManager toolManager = new IToolManager() {
            @Override
            public CompletableFuture<AiToolCallResult> callTool(String name, AiToolCall call, IToolExecuteContext context) {
                calledTools.add(name);
                return CompletableFuture.completedFuture(AiToolCallResult.successResult(0, "result_" + name));
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
                .permissionProvider(permProvider).build();
        AgentExecutionResult result = executor.execute(ctx).toCompletableFuture().join();

        assertEquals(AgentExecStatus.completed, result.getStatus());
        assertEquals(List.of("calculator", "echo"), calledTools);

        List<ChatToolResponseMessage> toolResponses = result.getMessages().stream()
                .filter(m -> m instanceof ChatToolResponseMessage)
                .map(m -> (ChatToolResponseMessage) m)
                .collect(Collectors.toList());
        assertEquals(3, toolResponses.size());

        ChatToolResponseMessage deniedResp = toolResponses.get(0);
        assertTrue(deniedResp.getContent().contains("Permission denied"));
    }

    @Test
    void testToolCallDeniedEventPublished() {
        AgentModel model = new AgentModel();
        model.setTools(Set.of("dangerous_tool"));
        AgentExecutionContext ctx = AgentExecutionContext.create(model, "test-session");

        IPermissionProvider denyAll = new IPermissionProvider() {
            @Override
            public Permission resolve(String toolName, String agentName, String sessionId) {
                return Permission.deny("test deny");
            }
        };

        ChatToolCall toolCall = new ChatToolCall();
        toolCall.setId("call_1");
        toolCall.setName("dangerous_tool");
        toolCall.setArguments(Map.of());

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
                .permissionProvider(denyAll).build();
        executor.execute(ctx).toCompletableFuture().join();

        boolean hasDeniedEvent = events.stream()
                .anyMatch(e -> e.getEventType() == AgentEventType.TOOL_CALL_DENIED);
        assertTrue(hasDeniedEvent, "TOOL_CALL_DENIED event should be published");
    }
}
