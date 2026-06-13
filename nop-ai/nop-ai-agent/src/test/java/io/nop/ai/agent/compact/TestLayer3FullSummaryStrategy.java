package io.nop.ai.agent.compact;

import io.nop.ai.agent.engine.AgentExecutionContext;
import io.nop.ai.agent.engine.ITokenEstimator;
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
import io.nop.ai.api.chat.messages.ChatUserMessage;
import io.nop.ai.api.chat.stream.ChatStreamChunk;
import io.nop.api.core.util.ICancelToken;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestLayer3FullSummaryStrategy {

    private CompactConfig config(double keepTailPercent, int triggerMaxMessages) {
        return new CompactConfig(0, null, true,
                CompactConfig.DEFAULT_MAX_RECENT_TOOL_RESULTS,
                CompactConfig.DEFAULT_TRUNCATION_THRESHOLD_CHARS,
                0.05, 0.9, keepTailPercent, triggerMaxMessages, "");
    }

    private CompactionContext ctxWith(List<ChatMessage> messages, IChatService chatService) {
        AgentModel m = new AgentModel();
        m.setName("test-agent");
        AgentExecutionContext execCtx = new AgentExecutionContext(m);
        return new CompactionContext(messages, config(0.15, 5), "s1", "agent1", execCtx, null);
    }

    private ChatAssistantMessage assistantWithToolCalls(String... ids) {
        ChatAssistantMessage msg = new ChatAssistantMessage();
        List<ChatToolCall> calls = new ArrayList<>();
        for (String id : ids) {
            ChatToolCall call = new ChatToolCall();
            call.setId(id);
            call.setName("bash");
            calls.add(call);
        }
        msg.setToolCalls(calls);
        return msg;
    }

    private ChatToolResponseMessage toolResponse(String toolCallId, String content) {
        return new ChatToolResponseMessage(toolCallId, "bash", content);
    }

    private List<ChatMessage> buildLongConversation(int turns) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatSystemMessage("system prompt"));
        messages.add(new ChatUserMessage("initial goal"));
        for (int i = 0; i < turns; i++) {
            String id = "tc-" + i;
            messages.add(assistantWithToolCalls(id));
            messages.add(toolResponse(id, "result-" + i + "-padding-" + "X".repeat(40)));
        }
        return messages;
    }

    private IChatService summarizingChatService(String summaryText) {
        return new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                ChatAssistantMessage msg = new ChatAssistantMessage(summaryText);
                return CompletableFuture.completedFuture(ChatResponse.success(msg));
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {};
            }
        };
    }

    @Test
    void summaryMessageProducedOnTrigger() {
        List<ChatMessage> messages = buildLongConversation(40);
        AtomicReference<String> capturedPrompt = new AtomicReference<>();
        AtomicInteger callCount = new AtomicInteger(0);

        IChatService chatService = new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                callCount.incrementAndGet();
                capturedPrompt.set(request.getLastUserPrompt());
                return CompletableFuture.completedFuture(
                        ChatResponse.success(new ChatAssistantMessage("## Goal\ntest goal")));
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {};
            }
        };

        Layer3FullSummaryStrategy strategy = new Layer3FullSummaryStrategy(chatService);
        CompactionContext ctx = ctxWith(messages, chatService);

        CompactionResult result = strategy.compact(ctx);

        assertEquals(1, callCount.get(), "IChatService must be called exactly once");
        assertNotNull(capturedPrompt.get(), "Prompt must be passed to the LLM");
        assertNotNull(result.getCompactedMessages());

        boolean foundSummary = false;
        for (ChatMessage msg : result.getCompactedMessages()) {
            if (msg instanceof ChatUserMessage && msg.getContent() != null
                    && msg.getContent().startsWith(Layer3FullSummaryStrategy.SUMMARY_MARKER)) {
                foundSummary = true;
                assertTrue(msg.getContent().contains("## Goal"), "Summary content must be retained");
                break;
            }
        }
        assertTrue(foundSummary, "A summary message must be present in the result");
        assertTrue(result.getTokensAfter() < result.getTokensBefore(),
                "Summarization must reduce token count");
    }

    @Test
    void incrementalUpdatePassesPreviousSummary() {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatSystemMessage("system"));
        messages.add(new ChatUserMessage("initial goal"));
        messages.add(new ChatUserMessage(Layer3FullSummaryStrategy.SUMMARY_MARKER + "\n## Goal\nold goal"));
        for (int i = 0; i < 40; i++) {
            String id = "tc-" + i;
            messages.add(assistantWithToolCalls(id));
            messages.add(toolResponse(id, "result-" + i + "-" + "X".repeat(30)));
        }

        AtomicReference<String> capturedPrompt = new AtomicReference<>();

        IChatService chatService = new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                capturedPrompt.set(request.getLastUserPrompt());
                return CompletableFuture.completedFuture(
                        ChatResponse.success(new ChatAssistantMessage("## Goal\nupdated goal")));
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {};
            }
        };

        Layer3FullSummaryStrategy strategy = new Layer3FullSummaryStrategy(chatService);
        CompactionContext ctx = ctxWith(messages, chatService);

        strategy.compact(ctx);

        String prompt = capturedPrompt.get();
        assertNotNull(prompt);
        assertTrue(prompt.contains("<previous-summary>"),
                "Incremental update must pass the previous summary into the prompt");
        assertTrue(prompt.contains("old goal"),
                "Previous summary content must be present in the prompt");
        assertTrue(prompt.contains("Update the previous summary incrementally"),
                "Prompt must instruct incremental update");
    }

    @Test
    void llmFailureDegradesGracefully() {
        List<ChatMessage> messages = buildLongConversation(40);

        IChatService failingChatService = new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                return CompletableFuture.completedFuture(ChatResponse.error("SUMMARY_FAIL", "model unavailable"));
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {};
            }
        };

        Layer3FullSummaryStrategy strategy = new Layer3FullSummaryStrategy(failingChatService);
        CompactionContext ctx = ctxWith(messages, failingChatService);

        CompactionResult result = strategy.compact(ctx);

        assertNotNull(result, "Fallback must return a result, not throw");
        assertEquals("layer3-full-summary", strategy.name());
        assertTrue(result.getCompactedMessages() == null
                        || result.getTokensAfter() <= result.getTokensBefore(),
                "Fallback should not increase tokens");
    }

    @Test
    void absentChatServiceDegradesExplicitly() {
        List<ChatMessage> messages = buildLongConversation(40);

        Layer3FullSummaryStrategy strategy = new Layer3FullSummaryStrategy();
        CompactionContext ctx = ctxWith(messages, null);

        CompactionResult result = strategy.compact(ctx);

        assertNotNull(result, "Absent IChatService must produce an explicit fallback result, not throw");
    }

    @Test
    void systemMessageAndFirstUserGoalRetained() {
        List<ChatMessage> messages = buildLongConversation(40);

        Layer3FullSummaryStrategy strategy = new Layer3FullSummaryStrategy(summarizingChatService("## Goal\ntest"));
        CompactionContext ctx = ctxWith(messages, null);

        CompactionResult result = strategy.compact(ctx);
        List<ChatMessage> out = result.getCompactedMessages();

        assertFalse(out.isEmpty());
        assertTrue(out.get(0) instanceof ChatSystemMessage, "System message must be retained as head anchor");
        assertEquals("system prompt", out.get(0).getContent());

        boolean foundFirstUser = false;
        for (ChatMessage msg : out) {
            if (msg instanceof ChatUserMessage && "initial goal".equals(msg.getContent())) {
                foundFirstUser = true;
                break;
            }
        }
        assertTrue(foundFirstUser, "First user goal must be retained after summarization");
    }

    @Test
    void tailWindowPreservedAfterSummarization() {
        List<ChatMessage> messages = buildLongConversation(40);
        int total = messages.size();

        Layer3FullSummaryStrategy strategy = new Layer3FullSummaryStrategy(summarizingChatService("summary text"));
        CompactionContext ctx = ctxWith(messages, null);

        CompactionResult result = strategy.compact(ctx);
        List<ChatMessage> out = result.getCompactedMessages();

        assertEquals(messages.get(total - 1), out.get(out.size() - 1), "Tail (last) message must be preserved");
        assertEquals(messages.get(total - 2), out.get(out.size() - 2), "Tail (second-to-last) message must be preserved");
    }

    @Test
    void boundaryIntegrityAfterSummarization() {
        List<ChatMessage> messages = buildLongConversation(40);

        Layer3FullSummaryStrategy strategy = new Layer3FullSummaryStrategy(summarizingChatService("summary"));
        CompactionContext ctx = ctxWith(messages, null);

        CompactionResult result = strategy.compact(ctx);
        List<ChatMessage> out = result.getCompactedMessages();

        Set<String> calledIds = new java.util.HashSet<>();
        Set<String> respondedIds = new java.util.HashSet<>();
        for (ChatMessage msg : out) {
            if (msg instanceof ChatAssistantMessage) {
                ChatAssistantMessage asm = (ChatAssistantMessage) msg;
                if (asm.getToolCalls() != null) {
                    for (ChatToolCall tc : asm.getToolCalls()) {
                        if (tc.getId() != null) calledIds.add(tc.getId());
                    }
                }
            }
            if (msg instanceof ChatToolResponseMessage) {
                respondedIds.add(((ChatToolResponseMessage) msg).getToolCallId());
            }
        }
        assertEquals(calledIds, respondedIds, "tool_call/tool_response pairing must stay intact after summarization");
    }

    @Test
    void compressionModelRoutingAppliedWhenConfigured() {
        List<ChatMessage> messages = buildLongConversation(40);
        AtomicReference<String> capturedModel = new AtomicReference<>();

        IChatService chatService = new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                if (request.getOptions() != null) {
                    capturedModel.set(request.getOptions().getModel());
                }
                return CompletableFuture.completedFuture(
                        ChatResponse.success(new ChatAssistantMessage("summary")));
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {};
            }
        };

        Layer3FullSummaryStrategy strategy = new Layer3FullSummaryStrategy(chatService, "cheap-summary-model");
        CompactionContext ctx = ctxWith(messages, chatService);

        strategy.compact(ctx);

        assertEquals("cheap-summary-model", capturedModel.get(),
                "compressionModel must be routed via ChatOptions when configured on the strategy");
    }

    @Test
    void emptyMessagesHandledExplicitly() {
        Layer3FullSummaryStrategy strategy = new Layer3FullSummaryStrategy(summarizingChatService("summary"));
        CompactionContext ctx = ctxWith(Collections.emptyList(), null);

        CompactionResult result = strategy.compact(ctx);
        assertNull(result.getCompactedMessages());
        assertEquals(0, result.getTokensBefore());
    }

    @Test
    void wiringStrategyInvokedFromPipeline() {
        AtomicInteger summaryCalls = new AtomicInteger(0);
        IChatService chatService = new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                summaryCalls.incrementAndGet();
                return CompletableFuture.completedFuture(
                        ChatResponse.success(new ChatAssistantMessage("## Goal\nsummary")));
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {};
            }
        };

        Layer3FullSummaryStrategy layer3 = new Layer3FullSummaryStrategy(chatService);
        PipelineCompactor pipeline = new PipelineCompactor(
                new MicroCompressionCompactor(),
                new Layer2TurnPruningStrategy(),
                layer3
        );

        List<ChatMessage> messages = buildLongConversation(40);
        AgentModel m = new AgentModel();
        m.setName("test-agent");
        AgentExecutionContext execCtx = new AgentExecutionContext(m);
        CompactionContext ctx = new CompactionContext(messages, config(0.1, 5), "s1", "agent1", execCtx, null);

        CompactionResult result = pipeline.compact(ctx);

        assertTrue(summaryCalls.get() >= 1, "Wiring: pipeline must invoke the Layer 3 IChatService at runtime");
        assertNotNull(result.getCompactedMessages());
    }
}
