package io.nop.ai.agent.engine;

import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.model.AgentModel;
import io.nop.ai.agent.security.AllowAllPermissionProvider;
import io.nop.ai.agent.security.AllowAllToolAccessChecker;
import io.nop.ai.agent.security.DefaultPathAccessChecker;
import io.nop.ai.agent.security.IPathAccessChecker;
import io.nop.ai.agent.security.IPermissionProvider;
import io.nop.ai.agent.security.IToolAccessChecker;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestPathAccessCheckerInReActLoop {

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

    private IChatService mockChatService(ChatToolCall toolCall) {
        AtomicInteger callCount = new AtomicInteger(0);
        return new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                int n = callCount.getAndIncrement();
                if (n == 0) {
                    return CompletableFuture.completedFuture(buildSuccessResponseWithToolCalls(List.of(toolCall)));
                }
                return CompletableFuture.completedFuture(buildSuccessResponse("Done."));
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {};
            }
        };
    }

    private IToolManager mockToolManager(AtomicBoolean toolCalled, String output) {
        return new IToolManager() {
            @Override
            public CompletableFuture<AiToolCallResult> callTool(String name, AiToolCall call, IToolExecuteContext context) {
                toolCalled.set(true);
                return CompletableFuture.completedFuture(AiToolCallResult.successResult(0, output));
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
    }

    @Test
    void testSensitivePathBlocked() {
        AgentModel model = new AgentModel();
        model.setTools(Set.of("read_file"));
        AgentExecutionContext ctx = AgentExecutionContext.create(model, "test-session");

        IPermissionProvider allowAll = new AllowAllPermissionProvider();
        IToolAccessChecker toolChecker = new AllowAllToolAccessChecker();
        IPathAccessChecker pathChecker = new DefaultPathAccessChecker();

        ChatToolCall toolCall = new ChatToolCall();
        toolCall.setId("call_1");
        toolCall.setName("read_file");
        toolCall.setArguments(Map.of("path", "/etc/passwd"));

        AtomicBoolean toolCalled = new AtomicBoolean(false);
        IToolManager toolManager = mockToolManager(toolCalled, "should not be called");
        IChatService chatService = mockChatService(toolCall);

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatService).toolManager(toolManager)
                .permissionProvider(allowAll).toolAccessChecker(toolChecker)
                .pathAccessChecker(pathChecker).build();
        AgentExecutionResult result = executor.execute(ctx).toCompletableFuture().join();

        assertEquals(AgentExecStatus.completed, result.getStatus());
        assertFalse(toolCalled.get(), "Tool should NOT have been called — sensitive path should block it");

        ChatToolResponseMessage toolResp = result.getMessages().stream()
                .filter(m -> m instanceof ChatToolResponseMessage)
                .map(m -> (ChatToolResponseMessage) m)
                .findFirst().orElse(null);
        assertNotNull(toolResp);
        assertTrue(toolResp.getContent().contains("Path access denied"));
        assertTrue(toolResp.getContent().contains("/etc/passwd"));
    }

    @Test
    void testPathAccessDeniedEventPublished() {
        AgentModel model = new AgentModel();
        model.setTools(Set.of("write_file"));
        AgentExecutionContext ctx = AgentExecutionContext.create(model, "test-session");

        IPermissionProvider allowAll = new AllowAllPermissionProvider();
        IToolAccessChecker toolChecker = new AllowAllToolAccessChecker();
        IPathAccessChecker pathChecker = new DefaultPathAccessChecker();

        String home = System.getProperty("user.home");
        ChatToolCall toolCall = new ChatToolCall();
        toolCall.setId("call_1");
        toolCall.setName("write_file");
        toolCall.setArguments(Map.of("filePath", home + "/.ssh/authorized_keys"));

        AtomicBoolean toolCalled = new AtomicBoolean(false);
        IToolManager toolManager = mockToolManager(toolCalled, "");
        IChatService chatService = mockChatService(toolCall);

        List<AgentEvent> events = new ArrayList<>();
        DefaultAgentEventPublisher publisher = new DefaultAgentEventPublisher();
        publisher.addSubscriber(event -> events.add(event));

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatService).toolManager(toolManager)
                .eventPublisher(publisher)
                .permissionProvider(allowAll).toolAccessChecker(toolChecker)
                .pathAccessChecker(pathChecker).build();
        executor.execute(ctx).toCompletableFuture().join();

        AgentEvent deniedEvent = events.stream()
                .filter(e -> e.getEventType() == AgentEventType.PATH_ACCESS_DENIED)
                .findFirst().orElse(null);
        assertNotNull(deniedEvent, "PATH_ACCESS_DENIED event should be published");
        String reason = (String) deniedEvent.getPayload().get("reason");
        assertNotNull(reason);
        assertTrue(reason.contains(".ssh"));
    }

    @Test
    void testSafePathExecutesNormally() {
        AgentModel model = new AgentModel();
        model.setTools(Set.of("read_file"));
        AgentExecutionContext ctx = AgentExecutionContext.create(model, "test-session");

        IPermissionProvider allowAll = new AllowAllPermissionProvider();
        IToolAccessChecker toolChecker = new AllowAllToolAccessChecker();
        IPathAccessChecker pathChecker = new DefaultPathAccessChecker();

        ChatToolCall toolCall = new ChatToolCall();
        toolCall.setId("call_1");
        toolCall.setName("read_file");
        toolCall.setArguments(Map.of("path", "/tmp/workdir/output.txt"));

        AtomicBoolean toolCalled = new AtomicBoolean(false);
        IToolManager toolManager = mockToolManager(toolCalled, "file content");
        IChatService chatService = mockChatService(toolCall);

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatService).toolManager(toolManager)
                .permissionProvider(allowAll).toolAccessChecker(toolChecker)
                .pathAccessChecker(pathChecker).build();
        AgentExecutionResult result = executor.execute(ctx).toCompletableFuture().join();

        assertEquals(AgentExecStatus.completed, result.getStatus());
        assertTrue(toolCalled.get(), "Tool should have been called — safe path");

        ChatToolResponseMessage toolResp = result.getMessages().stream()
                .filter(m -> m instanceof ChatToolResponseMessage)
                .map(m -> (ChatToolResponseMessage) m)
                .findFirst().orElse(null);
        assertNotNull(toolResp);
        assertEquals("file content", toolResp.getContent());
    }

    @Test
    void testNoPathArgExecutesNormally() {
        AgentModel model = new AgentModel();
        model.setTools(Set.of("calculator"));
        AgentExecutionContext ctx = AgentExecutionContext.create(model, "test-session");

        IPermissionProvider allowAll = new AllowAllPermissionProvider();
        IToolAccessChecker toolChecker = new AllowAllToolAccessChecker();
        IPathAccessChecker pathChecker = new DefaultPathAccessChecker();

        ChatToolCall toolCall = new ChatToolCall();
        toolCall.setId("call_1");
        toolCall.setName("calculator");
        toolCall.setArguments(Map.of("expr", "2+2"));

        AtomicBoolean toolCalled = new AtomicBoolean(false);
        IToolManager toolManager = mockToolManager(toolCalled, "4");
        IChatService chatService = mockChatService(toolCall);

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatService).toolManager(toolManager)
                .permissionProvider(allowAll).toolAccessChecker(toolChecker)
                .pathAccessChecker(pathChecker).build();
        AgentExecutionResult result = executor.execute(ctx).toCompletableFuture().join();

        assertEquals(AgentExecStatus.completed, result.getStatus());
        assertTrue(toolCalled.get(), "Tool should have been called — no path args");

        ChatToolResponseMessage toolResp = result.getMessages().stream()
                .filter(m -> m instanceof ChatToolResponseMessage)
                .map(m -> (ChatToolResponseMessage) m)
                .findFirst().orElse(null);
        assertNotNull(toolResp);
        assertEquals("4", toolResp.getContent());
    }

    @Test
    void testAllowAllPathAccessCheckerNeverBlocks() {
        AgentModel model = new AgentModel();
        model.setTools(Set.of("read_file"));
        AgentExecutionContext ctx = AgentExecutionContext.create(model, "test-session");

        IPermissionProvider allowAll = new AllowAllPermissionProvider();
        IToolAccessChecker toolChecker = new AllowAllToolAccessChecker();
        IPathAccessChecker pathChecker = new io.nop.ai.agent.security.AllowAllPathAccessChecker();

        ChatToolCall toolCall = new ChatToolCall();
        toolCall.setId("call_1");
        toolCall.setName("read_file");
        toolCall.setArguments(Map.of("path", "/etc/passwd"));

        AtomicBoolean toolCalled = new AtomicBoolean(false);
        IToolManager toolManager = mockToolManager(toolCalled, "root:x:0:0");
        IChatService chatService = mockChatService(toolCall);

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatService).toolManager(toolManager)
                .permissionProvider(allowAll).toolAccessChecker(toolChecker)
                .pathAccessChecker(pathChecker).build();
        AgentExecutionResult result = executor.execute(ctx).toCompletableFuture().join();

        assertEquals(AgentExecStatus.completed, result.getStatus());
        assertTrue(toolCalled.get(), "Tool should have been called — AllowAllPathAccessChecker never blocks");
    }

    @Test
    void testPathTraversalBlocked() {
        AgentModel model = new AgentModel();
        model.setTools(Set.of("read_file"));
        AgentExecutionContext ctx = AgentExecutionContext.create(model, "test-session");

        IPermissionProvider allowAll = new AllowAllPermissionProvider();
        IToolAccessChecker toolChecker = new AllowAllToolAccessChecker();
        IPathAccessChecker pathChecker = new DefaultPathAccessChecker();

        ChatToolCall toolCall = new ChatToolCall();
        toolCall.setId("call_1");
        toolCall.setName("read_file");
        toolCall.setArguments(Map.of("file", "../../../etc/shadow"));

        AtomicBoolean toolCalled = new AtomicBoolean(false);
        IToolManager toolManager = mockToolManager(toolCalled, "");
        IChatService chatService = mockChatService(toolCall);

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatService).toolManager(toolManager)
                .permissionProvider(allowAll).toolAccessChecker(toolChecker)
                .pathAccessChecker(pathChecker).build();
        AgentExecutionResult result = executor.execute(ctx).toCompletableFuture().join();

        assertEquals(AgentExecStatus.completed, result.getStatus());
        assertFalse(toolCalled.get(), "Tool should NOT have been called — path traversal blocked");

        ChatToolResponseMessage toolResp = result.getMessages().stream()
                .filter(m -> m instanceof ChatToolResponseMessage)
                .map(m -> (ChatToolResponseMessage) m)
                .findFirst().orElse(null);
        assertNotNull(toolResp);
        assertTrue(toolResp.getContent().contains("Path access denied"));
    }
}
