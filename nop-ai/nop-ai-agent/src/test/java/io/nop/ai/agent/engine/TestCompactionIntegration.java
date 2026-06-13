package io.nop.ai.agent.engine;

import io.nop.ai.agent.compact.IContextCompactor;
import io.nop.ai.agent.compact.MicroCompressionCompactor;
import io.nop.ai.agent.compact.NoOpContextCompactor;
import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.model.AgentModel;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.messages.ChatMessage;
import io.nop.ai.api.chat.messages.ChatToolCall;
import io.nop.ai.api.chat.messages.ChatToolResponseMessage;
import io.nop.ai.api.chat.messages.ChatUserMessage;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestCompactionIntegration {

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
        agentModel.setTools(Set.of("bash"));
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
    void fullReActLoopWithCompaction() {
        AtomicBoolean compactionOccurred = new AtomicBoolean(false);

        MicroCompressionCompactor realCompactor = new MicroCompressionCompactor();
        IContextCompactor trackingCompactor = ctx -> {
            io.nop.ai.agent.session.CompactionResult result = realCompactor.compact(ctx);
            if (result.getCompactedMessages() != null) {
                compactionOccurred.set(true);
            }
            return result;
        };

        IChatService chatService = new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                ChatAssistantMessage msg = new ChatAssistantMessage();
                msg.setContent("done");
                return CompletableFuture.completedFuture(ChatResponse.success(msg));
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {};
            }
        };

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatService)
                .toolManager(simpleToolManager())
                .contextCompactor(trackingCompactor)
                .build();

        AgentExecutionContext ctx = AgentExecutionContext.create(agentModel, "test-session");
        ctx.addMessage(new ChatUserMessage("hello"));

        for (int i = 0; i < 20; i++) {
            String id = "tc-" + i;
            ChatAssistantMessage assistantMsg = new ChatAssistantMessage();
            ChatToolCall toolCall = new ChatToolCall();
            toolCall.setId(id);
            toolCall.setName("bash");
            assistantMsg.setToolCalls(Collections.singletonList(toolCall));
            ctx.addMessage(assistantMsg);
            ctx.addMessage(new ChatToolResponseMessage(id, "bash", "X".repeat(5000)));
        }

        AgentExecutionResult result = executor.execute(ctx).toCompletableFuture().join();

        assertEquals(AgentExecStatus.completed, result.getStatus());
        assertTrue(compactionOccurred.get(), "Compaction should have occurred during execution");
    }

    @Test
    void defaultAgentEngineUsesRealCompactor() {
        AtomicBoolean compactorUsed = new AtomicBoolean(false);
        IContextCompactor trackingCompactor = new IContextCompactor() {
            private final MicroCompressionCompactor delegate = new MicroCompressionCompactor();

            @Override
            public io.nop.ai.agent.session.CompactionResult compact(io.nop.ai.agent.compact.CompactionContext ctx) {
                compactorUsed.set(true);
                return delegate.compact(ctx);
            }
        };

        IChatService chatService = new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                ChatAssistantMessage msg = new ChatAssistantMessage();
                msg.setContent("done");
                return CompletableFuture.completedFuture(ChatResponse.success(msg));
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {};
            }
        };

        IToolManager toolManager = simpleToolManager();

        DefaultAgentEngine engine = new DefaultAgentEngine(chatService, toolManager,
                new io.nop.ai.agent.session.InMemorySessionStore(),
                new io.nop.ai.agent.security.AllowAllPermissionProvider(),
                new io.nop.ai.agent.security.AllowAllToolAccessChecker(),
                new io.nop.ai.agent.security.AllowAllPathAccessChecker(),
                io.nop.ai.agent.guardrail.NoOpContentGuardrail.noOp(),
                io.nop.ai.agent.router.PassThroughModelRouter.passThrough(),
                trackingCompactor);

        IAgentExecutor executor = engine.resolveExecutor(agentModel);
        assertNotNull(executor);
        assertTrue(executor instanceof ReActAgentExecutor);

        AgentExecutionContext ctx = AgentExecutionContext.create(agentModel, "test-session");
        ctx.addMessage(new ChatUserMessage("hello"));
        for (int i = 0; i < 20; i++) {
            String id = "tc-" + i;
            ChatAssistantMessage assistantMsg = new ChatAssistantMessage();
            ChatToolCall toolCall = new ChatToolCall();
            toolCall.setId(id);
            toolCall.setName("bash");
            assistantMsg.setToolCalls(Collections.singletonList(toolCall));
            ctx.addMessage(assistantMsg);
            ctx.addMessage(new ChatToolResponseMessage(id, "bash", "X".repeat(5000)));
        }

        executor.execute(ctx).toCompletableFuture().join();
        assertTrue(compactorUsed.get(), "Real compactor should have been called via DefaultAgentEngine");
    }

    @Test
    void noOpCompactorCanBeExplicitlySelected() {
        IChatService chatService = new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                ChatAssistantMessage msg = new ChatAssistantMessage();
                msg.setContent("done");
                return CompletableFuture.completedFuture(ChatResponse.success(msg));
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {};
            }
        };

        IToolManager toolManager = simpleToolManager();

        DefaultAgentEngine engine = new DefaultAgentEngine(chatService, toolManager,
                new io.nop.ai.agent.session.InMemorySessionStore(),
                new io.nop.ai.agent.security.AllowAllPermissionProvider(),
                new io.nop.ai.agent.security.AllowAllToolAccessChecker(),
                new io.nop.ai.agent.security.AllowAllPathAccessChecker(),
                io.nop.ai.agent.guardrail.NoOpContentGuardrail.noOp(),
                io.nop.ai.agent.router.PassThroughModelRouter.passThrough(),
                NoOpContextCompactor.INSTANCE);

        IAgentExecutor executor = engine.resolveExecutor(agentModel);
        assertNotNull(executor);
        assertTrue(executor instanceof ReActAgentExecutor);

        AgentExecutionContext ctx = AgentExecutionContext.create(agentModel, "test-session");
        ctx.addMessage(new ChatUserMessage("hello"));
        for (int i = 0; i < 20; i++) {
            String id = "tc-" + i;
            ChatAssistantMessage assistantMsg = new ChatAssistantMessage();
            ChatToolCall toolCall = new ChatToolCall();
            toolCall.setId(id);
            toolCall.setName("bash");
            assistantMsg.setToolCalls(Collections.singletonList(toolCall));
            ctx.addMessage(assistantMsg);
            ctx.addMessage(new ChatToolResponseMessage(id, "bash", "content-" + i));
        }

        int originalSize = ctx.getMessages().size();
        executor.execute(ctx).toCompletableFuture().join();
        assertEquals(originalSize + 1, ctx.getMessages().size(),
                "NoOp compactor should not change message count (plus LLM response)");
    }

    @Test
    void compactedMessagesAppearInSubsequentCalls() {
        MicroCompressionCompactor compactor = new MicroCompressionCompactor();

        IChatService chatService = new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                ChatAssistantMessage msg = new ChatAssistantMessage();
                msg.setContent("done");
                return CompletableFuture.completedFuture(ChatResponse.success(msg));
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {};
            }
        };

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatService)
                .toolManager(simpleToolManager())
                .contextCompactor(compactor)
                .build();

        AgentExecutionContext ctx = AgentExecutionContext.create(agentModel, "test-session");
        ctx.addMessage(new ChatUserMessage("hello"));

        for (int i = 0; i < 20; i++) {
            String id = "tc-" + i;
            ChatAssistantMessage assistantMsg = new ChatAssistantMessage();
            ChatToolCall toolCall = new ChatToolCall();
            toolCall.setId(id);
            toolCall.setName("bash");
            assistantMsg.setToolCalls(Collections.singletonList(toolCall));
            ctx.addMessage(assistantMsg);
            ctx.addMessage(new ChatToolResponseMessage(id, "bash", "X".repeat(5000)));
        }

        int originalSize = ctx.getMessages().size();

        executor.execute(ctx).toCompletableFuture().join();

        assertEquals(originalSize + 1, ctx.getMessages().size(),
                "Message count should be original + LLM response (in-place replacement preserves size)");

        boolean hasCompressed = ctx.getMessages().stream()
                .filter(m -> m instanceof ChatToolResponseMessage)
                .map(m -> (ChatToolResponseMessage) m)
                .anyMatch(m -> m.getContent() != null && m.getContent().contains("COMPRESSED"));
        assertTrue(hasCompressed, "Some tool results should have been compressed in-place");
    }
}
