package io.nop.ai.agent.compact;

import io.nop.ai.agent.engine.AgentExecutionContext;
import io.nop.ai.agent.engine.AgentExecutionResult;
import io.nop.ai.agent.engine.ITokenEstimator;
import io.nop.ai.agent.engine.ReActAgentExecutor;
import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.model.AgentModel;
import io.nop.ai.agent.session.CompactionResult;
import io.nop.ai.agent.session.CompactConfig;
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
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestCompactionDataPathway {

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
    void compactedMessagesFlowFromCompactorToContext() {
        AtomicReference<List<ChatMessage>> capturedMessages = new AtomicReference<>();

        IContextCompactor reducingCompactor = ctx -> {
            capturedMessages.set(ctx.getMessages());
            List<ChatMessage> reduced = new ArrayList<>(ctx.getMessages().subList(0, 2));
            ITokenEstimator estimator = NoOpContextCompactor.resolveEstimator(ctx);
            long tokensBefore = estimator.estimateTokens(ctx.getMessages());
            long tokensAfter = estimator.estimateTokens(reduced);
            return new CompactionResult(
                    ctx.getSessionId(), tokensBefore, tokensAfter,
                    reduced.size(), null, reduced
            );
        };

        ChatOptionsModel chatOptions = new ChatOptionsModel();
        chatOptions.setMaxTokens(1000);
        agentModel.setChatOptions(chatOptions);

        AgentExecutionContext ctx = buildContext();
        ctx.setTokensUsed(900);
        for (int i = 0; i < 40; i++) {
            ctx.addMessage(new ChatUserMessage("message-" + i));
        }

        int originalSize = ctx.getMessages().size();

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
                .contextCompactor(reducingCompactor).build();

        AgentExecutionResult result = executor.execute(ctx).toCompletableFuture().join();
        assertEquals(AgentExecStatus.completed, result.getStatus());
        assertTrue(ctx.getMessages().size() < originalSize,
                "Messages should be reduced after compaction. Before: " + originalSize + ", After: " + ctx.getMessages().size());
        assertEquals(2 + 1, ctx.getMessages().size(),
                "2 compacted + 1 LLM response");
    }

    @Test
    void nullCompactedMessagesLeaveContextUnchanged() {
        IContextCompactor nullCompactor = ctx ->
                new CompactionResult(ctx.getSessionId(), 100, 100, ctx.getMessages().size(), null, null);

        ChatOptionsModel chatOptions = new ChatOptionsModel();
        chatOptions.setMaxTokens(1000);
        agentModel.setChatOptions(chatOptions);

        AgentExecutionContext ctx = buildContext();
        ctx.setTokensUsed(900);
        for (int i = 0; i < 40; i++) {
            ctx.addMessage(new ChatUserMessage("message-" + i));
        }

        int originalSize = ctx.getMessages().size();

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
                .contextCompactor(nullCompactor).build();

        AgentExecutionResult result = executor.execute(ctx).toCompletableFuture().join();
        assertEquals(AgentExecStatus.completed, result.getStatus());
        assertEquals(originalSize + 1, ctx.getMessages().size(),
                "Messages should remain unchanged (plus LLM response) when compactedMessages is null");
    }

    @Test
    void emptyCompactedMessagesSkipsReplacement() {
        IContextCompactor emptyCompactor = ctx ->
                new CompactionResult(ctx.getSessionId(), 100, 50, 0, null, Collections.emptyList());

        ChatOptionsModel chatOptions = new ChatOptionsModel();
        chatOptions.setMaxTokens(1000);
        agentModel.setChatOptions(chatOptions);

        AgentExecutionContext ctx = buildContext();
        ctx.setTokensUsed(900);
        for (int i = 0; i < 40; i++) {
            ctx.addMessage(new ChatUserMessage("message-" + i));
        }

        int originalSize = ctx.getMessages().size();

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
                .contextCompactor(emptyCompactor).build();

        AgentExecutionResult result = executor.execute(ctx).toCompletableFuture().join();
        assertEquals(AgentExecStatus.completed, result.getStatus());
        assertEquals(originalSize + 1, ctx.getMessages().size(),
                "Messages should remain unchanged (plus LLM response) when compactedMessages is empty");
    }

    @Test
    void compactConfigPassedToCompactor() {
        AtomicReference<CompactConfig> capturedConfig = new AtomicReference<>();

        IContextCompactor configInspectingCompactor = ctx -> {
            capturedConfig.set(ctx.getCompactConfig());
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
                .contextCompactor(configInspectingCompactor).build();

        executor.execute(ctx).toCompletableFuture().join();

        assertNotNull(capturedConfig.get(), "CompactConfig should not be null");
        assertEquals(CompactConfig.DEFAULT_MAX_RECENT_TOOL_RESULTS, capturedConfig.get().getMaxRecentToolResults());
        assertEquals(CompactConfig.DEFAULT_TRUNCATION_THRESHOLD_CHARS, capturedConfig.get().getTruncationThresholdChars());
    }

    @Test
    void compactedResultCarriesCompactedMessagesField() {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatUserMessage("hello"));
        messages.add(new ChatUserMessage("world"));

        CompactionContext ctx = new CompactionContext(
                messages, null, "s1", "agent1", null
        );

        CompactionResult result = NoOpContextCompactor.INSTANCE.compact(ctx);
        assertNull(result.getCompactedMessages(), "NoOp should return null compactedMessages");
    }

    @Test
    void noOpUsesCalibratedEstimatorForTokens() {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatUserMessage("12345678"));
        messages.add(new ChatUserMessage("12345678"));

        CompactionContext ctx = new CompactionContext(
                messages, null, "s1", "agent1", null
        );

        CompactionResult result = NoOpContextCompactor.INSTANCE.compact(ctx);
        long expectedPerMsg = "12345678".length() / 4
                + io.nop.ai.core.dialect.AbstractLlmDialect.PER_MESSAGE_TOKEN_OVERHEAD;
        assertEquals(expectedPerMsg * 2, result.getTokensBefore(),
                "2 messages: each contributes content/4 + overhead");
        assertEquals(expectedPerMsg * 2, result.getTokensAfter());
    }

    @Test
    void tokenAdjustmentAfterCompaction() {
        IContextCompactor reducingCompactor = ctx -> {
            List<ChatMessage> reduced = new ArrayList<>(ctx.getMessages().subList(0, 2));
            long tokensBefore = 100;
            long tokensAfter = 30;
            return new CompactionResult(
                    ctx.getSessionId(), tokensBefore, tokensAfter,
                    reduced.size(), null, reduced
            );
        };

        ChatOptionsModel chatOptions = new ChatOptionsModel();
        chatOptions.setMaxTokens(1000);
        agentModel.setChatOptions(chatOptions);

        AgentExecutionContext ctx = buildContext();
        ctx.setTokensUsed(900);
        for (int i = 0; i < 40; i++) {
            ctx.addMessage(new ChatUserMessage("message-" + i));
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
                .contextCompactor(reducingCompactor).build();

        executor.execute(ctx).toCompletableFuture().join();
        long expectedTokensUsed = 900 - (100 - 30);
        assertEquals(expectedTokensUsed, ctx.getTokensUsed(),
                "tokensUsed should be reduced by delta (tokensBefore - tokensAfter)");
    }
}
