package io.nop.ai.agent.engine;

import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.messages.ChatMessage;
import io.nop.ai.api.chat.messages.ChatUsage;
import io.nop.ai.api.chat.stream.ChatStreamChunk;
import io.nop.ai.core.dialect.LlmDialectFactory;
import io.nop.ai.core.model.ApiStyle;
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
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that the ReAct loop feeds the calibrator after an LLM response
 * (wiring verification for Phase 2).
 */
public class TestTokenEstimatorWiring {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    private IChatService chatServiceWithUsage(int promptTokens, int completionTokens) {
        return new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                ChatAssistantMessage msg = new ChatAssistantMessage();
                msg.setContent("done");
                ChatResponse response = ChatResponse.success(msg);
                ChatUsage usage = new ChatUsage();
                usage.setPromptTokens(promptTokens);
                usage.setCompletionTokens(completionTokens);
                response.setUsage(usage);
                return CompletableFuture.completedFuture(response);
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {
                };
            }
        };
    }

    private IToolManager simpleToolManager() {
        return new IToolManager() {
            @Override
            public CompletableFuture<AiToolCallResult> callTool(String toolName, AiToolCall call, IToolExecuteContext context) {
                return CompletableFuture.completedFuture(AiToolCallResult.successResult(0, "ok"));
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
                m.setDescription("test");
                return m;
            }
        };
    }

    private io.nop.ai.agent.model.AgentModel simpleAgentModel() {
        io.nop.ai.agent.model.AgentModel model = new io.nop.ai.agent.model.AgentModel();
        model.setName("test-agent");
        model.setTools(Set.of("test-tool"));
        return model;
    }

    @Test
    void reactLoopFeedsCalibratorAfterResponse() {
        CalibratedTokenEstimator estimator = new CalibratedTokenEstimator(
                LlmDialectFactory.getDialect(ApiStyle.openai), ApiStyle.openai);

        AgentExecutionContext ctx = AgentExecutionContext.create(simpleAgentModel(), "s1");
        ctx.addMessage(new io.nop.ai.api.chat.messages.ChatUserMessage("hello world test message"));

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatServiceWithUsage(500, 50))
                .toolManager(simpleToolManager())
                .tokenEstimator(estimator)
                .build();

        executor.execute(ctx).toCompletableFuture().join();

        assertTrue(estimator.getFactor() != 1.0,
                "Factor should have shifted from 1.0 after the loop fed actual usage");
    }

    @Test
    void reactLoopDoesNotFeedWhenUsageNull() {
        AtomicInteger recordCount = new AtomicInteger(0);
        ITokenEstimator countingEstimator = new ITokenEstimator() {
            final ITokenEstimator delegate = CalibratedTokenEstimator.defaultInstance();

            @Override
            public long estimateTokens(List<ChatMessage> messages) {
                return delegate.estimateTokens(messages);
            }

            @Override
            public void record(List<ChatMessage> messagesSent, int actualPromptTokens) {
                recordCount.incrementAndGet();
                delegate.record(messagesSent, actualPromptTokens);
            }
        };

        IChatService noUsageService = new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                ChatAssistantMessage msg = new ChatAssistantMessage();
                msg.setContent("done");
                return CompletableFuture.completedFuture(ChatResponse.success(msg));
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {
                };
            }
        };

        AgentExecutionContext ctx = AgentExecutionContext.create(simpleAgentModel(), "s1");
        ctx.addMessage(new io.nop.ai.api.chat.messages.ChatUserMessage("hello world"));

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(noUsageService)
                .toolManager(simpleToolManager())
                .tokenEstimator(countingEstimator)
                .build();

        executor.execute(ctx).toCompletableFuture().join();

        assertEquals(0, recordCount.get(),
                "record() should not be called when usage is null");
    }

    @Test
    void reactLoopFeedsActualPromptTokensToCalibrator() {
        AtomicInteger capturedTokens = new AtomicInteger(0);
        AtomicReference<List<ChatMessage>> capturedMessages = new AtomicReference<>();

        ITokenEstimator capturingEstimator = new ITokenEstimator() {
            final ITokenEstimator delegate = CalibratedTokenEstimator.defaultInstance();

            @Override
            public long estimateTokens(List<ChatMessage> messages) {
                return delegate.estimateTokens(messages);
            }

            @Override
            public void record(List<ChatMessage> messagesSent, int actualPromptTokens) {
                capturedTokens.set(actualPromptTokens);
                capturedMessages.set(messagesSent);
                delegate.record(messagesSent, actualPromptTokens);
            }
        };

        AgentExecutionContext ctx = AgentExecutionContext.create(simpleAgentModel(), "s1");
        ctx.addMessage(new io.nop.ai.api.chat.messages.ChatUserMessage("hello world test"));

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatServiceWithUsage(777, 30))
                .toolManager(simpleToolManager())
                .tokenEstimator(capturingEstimator)
                .build();

        executor.execute(ctx).toCompletableFuture().join();

        assertEquals(777, capturedTokens.get(),
                "record() should receive the actual promptTokens from the response");
        assertTrue(capturedMessages.get() != null && !capturedMessages.get().isEmpty(),
                "record() should receive the non-empty messages sent at call time");
    }
}
