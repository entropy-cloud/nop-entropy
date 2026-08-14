package io.nop.ai.agent.compact;

import io.nop.ai.agent.engine.AgentExecutionContext;
import io.nop.ai.agent.engine.ITokenEstimator;
import io.nop.ai.agent.model.AgentModel;
import io.nop.ai.agent.session.CompactionResult;
import io.nop.ai.agent.session.CompactConfig;
import io.nop.ai.agent.support.ChatResponseFixtures;
import io.nop.ai.api.chat.ChatOptions;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.messages.ChatToolCallMessage;
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
import static org.junit.jupiter.api.Assertions.assertSame;
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

    private CompactionContext ctxWith(List<ChatMessage> messages, IChatService chatService,
                                      ITokenEstimator estimator, int maxTokens) {
        AgentModel m = new AgentModel();
        m.setName("test-agent");
        AgentExecutionContext execCtx = new AgentExecutionContext(m);
        if (maxTokens > 0) {
            ChatOptions chatOptions = new ChatOptions();
            chatOptions.setMaxTokens(maxTokens);
            execCtx.setChatOptions(chatOptions);
        }
        return new CompactionContext(messages, config(0.15, 5), "s1", "agent1", execCtx, estimator);
    }

    private ITokenEstimator fixedPerMessageEstimator(long tokensPerMessage) {
        return new ITokenEstimator() {
            @Override
            public long estimateTokens(List<ChatMessage> messages) {
                return tokensPerMessage * messages.size();
            }

            @Override
            public void record(List<ChatMessage> messagesSent, int actualPromptTokens) {
            }
        };
    }

    private int countMiddleMessages(String middleContent) {
        return middleContent.split("\\[(assistant|tool|user)\\]").length - 1;
    }

    private int findInstructionIndex(List<ChatMessage> requestMessages) {
        for (int i = 0; i < requestMessages.size(); i++) {
            String content = requestMessages.get(i).getContent();
            if (content != null && content.startsWith("You are a conversation summarizer")) {
                return i;
            }
        }
        return -1;
    }

    private void assistantWithToolCalls(List<ChatMessage> messages, String... ids) {
        ChatToolCall[] calls = new ChatToolCall[ids.length];
        for (int i = 0; i < ids.length; i++) {
            calls[i] = new ChatToolCall();
            calls[i].setId(ids[i]);
            calls[i].setName("bash");
        }
        messages.addAll(ChatResponseFixtures.foldedAssistantWithToolCalls(null, calls));
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
            assistantWithToolCalls(messages, id);
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
            assistantWithToolCalls(messages, id);
            messages.add(toolResponse(id, "result-" + i + "-" + "X".repeat(30)));
        }

        AtomicReference<ChatRequest> capturedRequest = new AtomicReference<>();

        IChatService chatService = new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                capturedRequest.set(request);
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

        ChatRequest request = capturedRequest.get();
        assertNotNull(request, "Summary request must be captured");
        String instruction = null;
        for (ChatMessage msg : request.getMessages()) {
            if (msg instanceof ChatUserMessage && msg.getContent() != null
                    && msg.getContent().startsWith("You are a conversation summarizer")) {
                instruction = msg.getContent();
                break;
            }
        }
        assertNotNull(instruction, "Instruction message must be present in the summary request");
        assertTrue(instruction.contains("<previous-summary>"),
                "Incremental update must pass the previous summary into the instruction");
        assertTrue(instruction.contains("old goal"),
                "Previous summary content must be present in the instruction");
        assertTrue(instruction.contains("Update the previous summary incrementally"),
                "Instruction must request incremental update");
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
            if (msg instanceof ChatToolCallMessage) {
                String id = ((ChatToolCallMessage) msg).getCallId();
                if (id != null) calledIds.add(id);
            }
            if (msg instanceof ChatToolResponseMessage) {
                respondedIds.add(((ChatToolResponseMessage) msg).getCallId());
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

    @Test
    void headAnchorsPrefixedAsReusedObjects() {
        List<ChatMessage> messages = buildLongConversation(40);
        AtomicReference<ChatRequest> capturedRequest = new AtomicReference<>();

        IChatService chatService = new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                capturedRequest.set(request);
                return CompletableFuture.completedFuture(
                        ChatResponse.success(new ChatAssistantMessage("## Goal\nsummary")));
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {};
            }
        };

        Layer3FullSummaryStrategy strategy = new Layer3FullSummaryStrategy(chatService);
        CompactionContext ctx = ctxWith(messages, chatService, fixedPerMessageEstimator(10), -1);

        CompactionResult result = strategy.compact(ctx);
        assertNotNull(result.getCompactedMessages());

        ChatRequest request = capturedRequest.get();
        List<ChatMessage> requestMessages = request.getMessages();
        int instructionIndex = findInstructionIndex(requestMessages);
        assertTrue(instructionIndex > 0, "Instruction must follow the head prefix");
        assertEquals(2, instructionIndex, "Head prefix must be system + first user goal");
        assertSame(messages.get(0), requestMessages.get(0), "System message object must be reused (KV prefix)");
        assertSame(messages.get(1), requestMessages.get(1), "First user goal object must be reused (KV prefix)");

        ChatMessage last = requestMessages.get(requestMessages.size() - 1);
        assertTrue(last instanceof ChatUserMessage && last.getContent() != null
                        && last.getContent().startsWith("Conversation to summarize:"),
                "Middle content must be the final user message");
        assertEquals(66, countMiddleMessages(last.getContent()),
                "Middle window must carry all messages between head and tail when the budget is not binding "
                        + "(82 total - 2 head - 14 tail)");
    }

    @Test
    void explicitCompressionPromptBudgetTrimsHeadAndMiddleOneToOne() {
        List<ChatMessage> messages = buildLongConversation(40);
        AtomicReference<ChatRequest> capturedRequest = new AtomicReference<>();

        IChatService chatService = new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                capturedRequest.set(request);
                return CompletableFuture.completedFuture(
                        ChatResponse.success(new ChatAssistantMessage("## Goal\nsummary")));
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {};
            }
        };

        CompactConfig config = new CompactConfig(0, null, true,
                CompactConfig.DEFAULT_MAX_RECENT_TOOL_RESULTS,
                CompactConfig.DEFAULT_TRUNCATION_THRESHOLD_CHARS,
                0.05, 0.9, 0.15, 5, "", 100);

        AgentModel m = new AgentModel();
        m.setName("test-agent");
        AgentExecutionContext execCtx = new AgentExecutionContext(m);
        CompactionContext ctx = new CompactionContext(messages, config, "s1", "agent1", execCtx,
                fixedPerMessageEstimator(10));

        Layer3FullSummaryStrategy strategy = new Layer3FullSummaryStrategy(chatService);
        strategy.compact(ctx);

        ChatRequest request = capturedRequest.get();
        List<ChatMessage> requestMessages = request.getMessages();
        int instructionIndex = findInstructionIndex(requestMessages);
        assertTrue(instructionIndex > 0, "Instruction must be present");
        for (int i = 0; i < instructionIndex; i++) {
            assertSame(messages.get(i), requestMessages.get(i),
                    "Head prefix must reuse original objects and stay untrimmed under its 50% share");
        }
        ChatMessage last = requestMessages.get(requestMessages.size() - 1);
        assertTrue(last.getContent().startsWith("Conversation to summarize:"),
                "Middle content must be the final user message");
        int middleMessages = countMiddleMessages(last.getContent());
        assertTrue(middleMessages > 0 && middleMessages <= 4,
                "Middle window must be trimmed to its 50% share (budget 100 -> middle budget 40 -> <=4 msgs), got "
                        + middleMessages);
    }

    @Test
    void tinyCompressionPromptBudgetDegradesToLayer2() {
        List<ChatMessage> messages = buildLongConversation(40);
        AtomicInteger callCount = new AtomicInteger(0);

        IChatService chatService = new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                callCount.incrementAndGet();
                return CompletableFuture.completedFuture(
                        ChatResponse.success(new ChatAssistantMessage("## Goal\nsummary")));
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {};
            }
        };

        CompactConfig config = new CompactConfig(0, null, true,
                CompactConfig.DEFAULT_MAX_RECENT_TOOL_RESULTS,
                CompactConfig.DEFAULT_TRUNCATION_THRESHOLD_CHARS,
                0.05, 0.9, 0.15, 5, "", 30);

        AgentModel m = new AgentModel();
        m.setName("test-agent");
        AgentExecutionContext execCtx = new AgentExecutionContext(m);
        CompactionContext ctx = new CompactionContext(messages, config, "s1", "agent1", execCtx,
                fixedPerMessageEstimator(10));

        Layer3FullSummaryStrategy strategy = new Layer3FullSummaryStrategy(chatService);
        CompactionResult result = strategy.compact(ctx);

        assertEquals(0, callCount.get(),
                "Budget too small to fit instruction + any middle message: no LLM call may happen");
        assertNotNull(result, "Tiny budget must degrade, not fail");
        assertTrue(result.getCompactedMessages() == null
                        || result.getTokensAfter() <= result.getTokensBefore(),
                "Degraded result must not increase tokens");
    }

    @Test
    void dynamicBudgetFromTriggerWatermark() {
        List<ChatMessage> messages = buildLongConversation(40);
        AtomicReference<ChatRequest> capturedRequest = new AtomicReference<>();

        IChatService chatService = new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                capturedRequest.set(request);
                return CompletableFuture.completedFuture(
                        ChatResponse.success(new ChatAssistantMessage("## Goal\nsummary")));
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {};
            }
        };

        Layer3FullSummaryStrategy strategy = new Layer3FullSummaryStrategy(chatService);

        AgentModel m = new AgentModel();
        m.setName("test-agent");
        AgentExecutionContext execCtx = new AgentExecutionContext(m);
        ChatOptions chatOptions = new ChatOptions();
        chatOptions.setMaxTokens(1000);
        execCtx.setChatOptions(chatOptions);
        CompactionContext ctx = new CompactionContext(messages, config(0.15, 5), "s1", "agent1", execCtx,
                fixedPerMessageEstimator(10));

        strategy.compact(ctx);

        assertNull(capturedRequest.get(),
                "Watermark 1000*0.05/2=25 tokens: head gets 12 (1 group), middle gets 3 -> empty -> Layer 2 fallback, "
                        + "no LLM call");
    }
}
