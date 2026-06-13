package io.nop.ai.agent.compact;

import io.nop.ai.agent.engine.AgentExecutionContext;
import io.nop.ai.agent.engine.AgentExecutionResult;
import io.nop.ai.agent.engine.ReActAgentExecutor;
import io.nop.ai.agent.hook.AgentLifecyclePoint;
import io.nop.ai.agent.hook.DefaultHookRegistry;
import io.nop.ai.agent.hook.HookContext;
import io.nop.ai.agent.hook.HookResult;
import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.model.AgentModel;
import io.nop.ai.agent.session.CompactionResult;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.messages.ChatMessage;
import io.nop.ai.api.chat.messages.ChatUserMessage;
import io.nop.ai.api.chat.stream.ChatStreamChunk;
import io.nop.ai.core.model.ChatOptionsModel;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestCompactionInReActLoop {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    private AgentModel agentModel;

    @BeforeEach
    void setUp() {
        agentModel = new AgentModel();
        agentModel.setName("test-agent");
        agentModel.setTools(Set.of("test-tool"));
    }

    private AgentExecutionContext buildContext() {
        return AgentExecutionContext.create(agentModel, "test-session");
    }

    private ChatResponse successResponse(String content) {
        ChatAssistantMessage msg = new ChatAssistantMessage();
        msg.setContent(content);
        return ChatResponse.success(msg);
    }

    private IToolManager simpleToolManager() {
        return new IToolManager() {
            @Override
            public CompletableFuture<AiToolCallResult> callTool(String toolName, AiToolCall call, IToolExecuteContext context) {
                return CompletableFuture.completedFuture(AiToolCallResult.successResult(0, "tool-result"));
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
                m.setDescription("Test tool");
                return m;
            }
        };
    }

    @Test
    void compactorNotCalledWhenBelowThresholds() {
        AtomicBoolean compactCalled = new AtomicBoolean(false);

        IContextCompactor trackingCompactor = ctx -> {
            compactCalled.set(true);
            return NoOpContextCompactor.INSTANCE.compact(ctx);
        };

        IChatService chatService = new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                return CompletableFuture.completedFuture(successResponse("done"));
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {};
            }
        };

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatService).toolManager(simpleToolManager())
                .contextCompactor(trackingCompactor).build();

        AgentExecutionResult result = executor.execute(buildContext()).toCompletableFuture().join();
        assertEquals(AgentExecStatus.completed, result.getStatus());
        assertTrue(!compactCalled.get(), "Compactor should not be called when below thresholds");
    }

    @Test
    void compactorCalledWhenTokenThresholdExceeded() {
        AtomicBoolean compactCalled = new AtomicBoolean(false);

        IContextCompactor trackingCompactor = ctx -> {
            compactCalled.set(true);
            return NoOpContextCompactor.INSTANCE.compact(ctx);
        };

        ChatOptionsModel chatOptions = new ChatOptionsModel();
        chatOptions.setMaxTokens(1000);
        agentModel.setChatOptions(chatOptions);

        AgentExecutionContext ctx = buildContext();
        ctx.setTokensUsed(900);

        IChatService chatService = new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                return CompletableFuture.completedFuture(successResponse("done"));
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {};
            }
        };

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatService).toolManager(simpleToolManager())
                .contextCompactor(trackingCompactor).build();

        AgentExecutionResult result = executor.execute(ctx).toCompletableFuture().join();
        assertEquals(AgentExecStatus.completed, result.getStatus());
        assertTrue(compactCalled.get(), "Compactor should be called when tokensUsed > maxTokens * 0.8");
    }

    @Test
    void compactorCalledWhenMessageCountThresholdExceeded() {
        AtomicBoolean compactCalled = new AtomicBoolean(false);

        IContextCompactor trackingCompactor = ctx -> {
            compactCalled.set(true);
            return NoOpContextCompactor.INSTANCE.compact(ctx);
        };

        AgentExecutionContext ctx = buildContext();
        for (int i = 0; i < 35; i++) {
            ctx.addMessage(new ChatUserMessage("msg-" + i));
        }

        IChatService chatService = new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                return CompletableFuture.completedFuture(successResponse("done"));
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {};
            }
        };

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatService).toolManager(simpleToolManager())
                .contextCompactor(trackingCompactor).build();

        AgentExecutionResult result = executor.execute(ctx).toCompletableFuture().join();
        assertEquals(AgentExecStatus.completed, result.getStatus());
        assertTrue(compactCalled.get(), "Compactor should be called when messageCount > 30");
    }

    @Test
    void preCompactHookFiresBeforeCompact() {
        List<AgentLifecyclePoint> hookOrder = Collections.synchronizedList(new ArrayList<>());
        AtomicReference<CompactionContext> capturedCompactCtx = new AtomicReference<>();

        IContextCompactor trackingCompactor = ctx -> {
            capturedCompactCtx.set(ctx);
            return NoOpContextCompactor.INSTANCE.compact(ctx);
        };

        DefaultHookRegistry registry = new DefaultHookRegistry();
        registry.register(AgentLifecyclePoint.PRE_COMPACT, ctx -> {
            hookOrder.add(AgentLifecyclePoint.PRE_COMPACT);
            return HookResult.PassResult.instance();
        });
        registry.register(AgentLifecyclePoint.POST_COMPACT, ctx -> {
            hookOrder.add(AgentLifecyclePoint.POST_COMPACT);
            return HookResult.PassResult.instance();
        });

        AgentExecutionContext ctx = buildContext();
        for (int i = 0; i < 35; i++) {
            ctx.addMessage(new ChatUserMessage("msg-" + i));
        }

        IChatService chatService = new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                return CompletableFuture.completedFuture(successResponse("done"));
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {};
            }
        };

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatService).toolManager(simpleToolManager())
                .hookRegistry(registry)
                .contextCompactor(trackingCompactor).build();

        AgentExecutionResult result = executor.execute(ctx).toCompletableFuture().join();
        assertEquals(AgentExecStatus.completed, result.getStatus());

        assertTrue(hookOrder.contains(AgentLifecyclePoint.PRE_COMPACT), "PRE_COMPACT hook should fire");
        assertTrue(hookOrder.contains(AgentLifecyclePoint.POST_COMPACT), "POST_COMPACT hook should fire");
        int preIdx = hookOrder.indexOf(AgentLifecyclePoint.PRE_COMPACT);
        int postIdx = hookOrder.indexOf(AgentLifecyclePoint.POST_COMPACT);
        assertTrue(preIdx < postIdx, "PRE_COMPACT should fire before POST_COMPACT");
    }

    @Test
    void noRecursiveCompaction() {
        AtomicInteger compactCallCount = new AtomicInteger(0);

        IContextCompactor trackingCompactor = ctx -> {
            compactCallCount.incrementAndGet();
            CompactionResult result = NoOpContextCompactor.INSTANCE.compact(ctx);
            return result;
        };

        ChatOptionsModel chatOptions = new ChatOptionsModel();
        chatOptions.setMaxTokens(100);
        agentModel.setChatOptions(chatOptions);

        AgentExecutionContext ctx = buildContext();
        ctx.setTokensUsed(1000);
        for (int i = 0; i < 40; i++) {
            ctx.addMessage(new ChatUserMessage("msg-" + i));
        }

        IChatService chatService = new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                return CompletableFuture.completedFuture(successResponse("done"));
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {};
            }
        };

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatService).toolManager(simpleToolManager())
                .contextCompactor(trackingCompactor).build();

        AgentExecutionResult result = executor.execute(ctx).toCompletableFuture().join();
        assertEquals(AgentExecStatus.completed, result.getStatus());
        assertEquals(1, compactCallCount.get(), "Compaction should only be triggered once per iteration");
    }

    @Test
    void defaultNoOpCompactorDoesNotModifyMessages() {
        ChatOptionsModel chatOptions = new ChatOptionsModel();
        chatOptions.setMaxTokens(100);
        agentModel.setChatOptions(chatOptions);

        AgentExecutionContext ctx = buildContext();
        ctx.setTokensUsed(1000);

        int initialMsgCount = ctx.getMessages().size();

        IChatService chatService = new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                return CompletableFuture.completedFuture(successResponse("done"));
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {};
            }
        };

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatService).toolManager(simpleToolManager()).build();

        AgentExecutionResult result = executor.execute(ctx).toCompletableFuture().join();
        assertEquals(AgentExecStatus.completed, result.getStatus());
    }
}
