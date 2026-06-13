package io.nop.ai.agent.engine;

import io.nop.ai.agent.compact.CompactionContext;
import io.nop.ai.agent.compact.IContextCompactor;
import io.nop.ai.agent.compact.MicroCompressionCompactor;
import io.nop.ai.agent.compact.NoOpContextCompactor;
import io.nop.ai.agent.model.AgentModel;
import io.nop.ai.agent.session.CompactionResult;
import io.nop.ai.agent.session.CompactConfig;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.messages.ChatMessage;
import io.nop.ai.api.chat.messages.ChatSystemMessage;
import io.nop.ai.api.chat.messages.ChatToolCall;
import io.nop.ai.api.chat.messages.ChatToolResponseMessage;
import io.nop.ai.api.chat.messages.ChatUsage;
import io.nop.ai.api.chat.messages.ChatUserMessage;
import io.nop.ai.api.chat.stream.ChatStreamChunk;
import io.nop.ai.core.dialect.AbstractLlmDialect;
import io.nop.ai.core.dialect.LlmDialectFactory;
import io.nop.ai.core.model.ApiStyle;
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
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end integration test for token counting (L2-16).
 * <p>
 * Verifies:
 * <ul>
 *   <li>The ReAct loop feeds the calibrator with actual promptTokens</li>
 *   <li>The calibrated factor shifts from 1.0</li>
 *   <li>Compaction's tokensBefore/tokensAfter reflect the calibrated factor (not raw chars/4)</li>
 *   <li>The estimator instance used by the compactor is the same one fed by the ReAct loop</li>
 *   <li>Compaction message invariants still hold (system preserved, pairing intact, list size unchanged)</li>
 * </ul>
 */
public class TestTokenCountingIntegration {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
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
                m.setDescription("test");
                return m;
            }
        };
    }

    private AgentModel simpleAgentModel() {
        AgentModel model = new AgentModel();
        model.setName("test-agent");
        model.setTools(Set.of("test-tool"));
        return model;
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

    @Test
    void calibratedEstimatorAffectsCompactionAccounting() {
        CalibratedTokenEstimator estimator = new CalibratedTokenEstimator(
                LlmDialectFactory.getDialect(ApiStyle.openai), ApiStyle.openai);

        AgentModel agentModel = simpleAgentModel();
        ChatOptionsModel chatOptions = new ChatOptionsModel();
        chatOptions.setMaxTokens(1000);
        agentModel.setChatOptions(chatOptions);

        AgentExecutionContext ctx = AgentExecutionContext.create(agentModel, "itest");
        ctx.setTokensUsed(900);
        ctx.addMessage(new ChatSystemMessage("system prompt"));
        ctx.addMessage(new ChatUserMessage("user message"));

        AtomicReference<ITokenEstimator> capturedEstimator = new AtomicReference<>();
        AtomicReference<Long> capturedTokensBefore = new AtomicReference<>();

        IContextCompactor trackingCompactor = compactionCtx -> {
            capturedEstimator.set(compactionCtx.getTokenEstimator());
            CompactionResult result = NoOpContextCompactor.INSTANCE.compact(compactionCtx);
            capturedTokensBefore.set(result.getTokensBefore());
            return result;
        };

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatServiceWithUsage(500, 50))
                .toolManager(simpleToolManager())
                .contextCompactor(trackingCompactor)
                .tokenEstimator(estimator)
                .build();

        executor.execute(ctx).toCompletableFuture().join();

        assertTrue(estimator.getFactor() != 1.0,
                "Calibration factor should have shifted from 1.0 after LLM response with promptTokens");

        assertSame(estimator, capturedEstimator.get(),
                "The estimator used by the compactor must be the same instance fed by the ReAct loop");

        long rawCharsDiv4 = rawCharsDiv4(ctx.getMessages());
        assertNotEquals(rawCharsDiv4, capturedTokensBefore.get().longValue(),
                "tokensBefore should differ from raw chars/4 (calibrated + per-message overhead)");
        assertTrue(capturedTokensBefore.get() > 0,
                "tokensBefore should be positive");
    }

    @Test
    void compactionWithCalibratedEstimatorPreservesMessageInvariants() {
        CalibratedTokenEstimator estimator = new CalibratedTokenEstimator(
                LlmDialectFactory.getDialect(ApiStyle.openai), ApiStyle.openai);

        estimator.record(List.of(new ChatUserMessage("warmup message to calibrate")), 1000);

        assertTrue(estimator.getFactor() > 1.0,
                "Factor should be > 1.0 after recording actual > base");

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatSystemMessage("system prompt"));
        messages.add(new ChatUserMessage("first user message"));

        for (int i = 0; i < 5; i++) {
            String id = "tc-" + i;
            ChatAssistantMessage asm = new ChatAssistantMessage();
            ChatToolCall call = new ChatToolCall();
            call.setId(id);
            call.setName("bash");
            asm.setToolCalls(List.of(call));
            messages.add(asm);
            messages.add(new ChatToolResponseMessage(id, "bash", "X".repeat(4000)));
        }

        CompactionContext compactCtx = new CompactionContext(
                messages, new CompactConfig(0, null, true, 2, 8000),
                "s1", "agent1", null, estimator);

        MicroCompressionCompactor compactor = new MicroCompressionCompactor();
        CompactionResult result = compactor.compact(compactCtx);

        assertNotNull(result.getCompactedMessages());
        assertEquals(messages.size(), result.getCompactedMessages().size(),
                "Message list size should be unchanged (in-place replacement)");

        ChatMessage firstMsg = result.getCompactedMessages().get(0);
        assertTrue(firstMsg instanceof ChatSystemMessage,
                "System message should be preserved");

        boolean foundFirstUser = false;
        for (ChatMessage msg : result.getCompactedMessages()) {
            if (msg instanceof ChatUserMessage && "first user message".equals(msg.getContent())) {
                foundFirstUser = true;
                break;
            }
        }
        assertTrue(foundFirstUser, "First user message should be preserved");

        Set<String> assistantToolCallIds = new HashSet<>();
        for (ChatMessage msg : result.getCompactedMessages()) {
            if (msg instanceof ChatAssistantMessage) {
                ChatAssistantMessage asm = (ChatAssistantMessage) msg;
                if (asm.getToolCalls() != null) {
                    for (ChatToolCall tc : asm.getToolCalls()) {
                        assistantToolCallIds.add(tc.getId());
                    }
                }
            }
        }
        Set<String> responseToolCallIds = new HashSet<>();
        for (ChatMessage msg : result.getCompactedMessages()) {
            if (msg instanceof ChatToolResponseMessage) {
                responseToolCallIds.add(((ChatToolResponseMessage) msg).getToolCallId());
            }
        }
        assertEquals(assistantToolCallIds, responseToolCallIds,
                "All tool_call IDs should still have matching tool_response IDs");

        long rawEstimate = rawCharsDiv4(messages);
        assertNotEquals(rawEstimate, result.getTokensBefore(),
                "tokensBefore should differ from raw chars/4 due to calibration");
        assertTrue(result.getTokensAfter() < result.getTokensBefore(),
                "tokensAfter should be less than tokensAfter after compression");

        long calibratedBefore = estimator.estimateTokens(messages);
        assertEquals(calibratedBefore, result.getTokensBefore(),
                "tokensBefore should match the calibrated estimator's output");
    }

    private long rawCharsDiv4(List<ChatMessage> messages) {
        long total = 0;
        for (ChatMessage msg : messages) {
            if (msg.getContent() != null) {
                total += msg.getContent().length();
            }
        }
        return total / AbstractLlmDialect.CHARS_PER_TOKEN;
    }
}
