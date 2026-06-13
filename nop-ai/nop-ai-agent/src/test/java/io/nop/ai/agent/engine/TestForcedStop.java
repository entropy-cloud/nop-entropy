package io.nop.ai.agent.engine;

import io.nop.ai.agent.compact.Layer2TurnPruningStrategy;
import io.nop.ai.agent.compact.Layer3FullSummaryStrategy;
import io.nop.ai.agent.compact.MicroCompressionCompactor;
import io.nop.ai.agent.compact.PipelineCompactor;
import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.model.AgentModel;
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
import java.util.HashSet;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestForcedStop {

    private static final int MAX_CONTEXT_TOKENS = 1000;

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
        ChatOptionsModel opts = new ChatOptionsModel();
        opts.setMaxTokens(MAX_CONTEXT_TOKENS);
        agentModel.setChatOptions(opts);
    }

    private AgentExecutionContext buildContext() {
        return AgentExecutionContext.create(agentModel, "test-session");
    }

    /**
     * Estimator that returns a fixed estimate, recording each estimateTokens call.
     */
    private static final class FixedEstimator implements ITokenEstimator {
        final AtomicInteger estimateCalls = new AtomicInteger(0);
        private final long estimate;

        FixedEstimator(long estimate) {
            this.estimate = estimate;
        }

        @Override
        public long estimateTokens(List<ChatMessage> messages) {
            estimateCalls.incrementAndGet();
            return estimate;
        }

        @Override
        public void record(List<ChatMessage> messagesSent, int actualPromptTokens) {
            // no-op
        }
    }

    private IChatService chatServiceReturningToolCalls(String content) {
        return new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                ChatAssistantMessage msg = new ChatAssistantMessage();
                msg.setContent(content);
                ChatToolCall call = new ChatToolCall();
                call.setId("tc-1");
                call.setName("test-tool");
                msg.setToolCalls(Collections.singletonList(call));
                return CompletableFuture.completedFuture(ChatResponse.success(msg));
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {};
            }
        };
    }

    private IToolManager trackingToolManager(AtomicInteger toolCalls) {
        return new IToolManager() {
            @Override
            public CompletableFuture<AiToolCallResult> callTool(String toolName, AiToolCall call, IToolExecuteContext context) {
                toolCalls.incrementAndGet();
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

    private List<ChatMessage> buildLargeConversation(int turns) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatSystemMessage("system"));
        messages.add(new ChatUserMessage("goal"));
        for (int i = 0; i < turns; i++) {
            ChatAssistantMessage asm = new ChatAssistantMessage();
            ChatToolCall call = new ChatToolCall();
            call.setId("tc-" + i);
            call.setName("test-tool");
            asm.setToolCalls(Collections.singletonList(call));
            messages.add(asm);
            messages.add(new ChatToolResponseMessage("tc-" + i, "test-tool", "result-" + i));
        }
        return messages;
    }

    @Test
    void forcedStopFiresWhenPreCallEstimateExceeds90Percent() {
        DefaultAgentEventPublisher publisher = new DefaultAgentEventPublisher();
        List<AgentEvent> events = new ArrayList<>();
        publisher.addSubscriber(events::add);

        FixedEstimator estimator = new FixedEstimator(950); // 950 > 900 = 90% of 1000

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatServiceReturningToolCalls("thinking"))
                .toolManager(trackingToolManager(new AtomicInteger()))
                .eventPublisher(publisher)
                .tokenEstimator(estimator)
                .build();

        AgentExecutionResult result = executor.execute(buildContext()).toCompletableFuture().join();

        assertEquals(AgentExecStatus.forced_stopped, result.getStatus(),
                "Forced stop must set the distinct terminal status");

        boolean foundForcedStop = events.stream()
                .anyMatch(e -> e.getEventType() == AgentEventType.FORCED_STOP);
        assertTrue(foundForcedStop, "A FORCED_STOP event must be published on forced stop");
    }

    @Test
    void forcedStopDoesNotFireWhenBelow90Percent() {
        DefaultAgentEventPublisher publisher = new DefaultAgentEventPublisher();
        List<AgentEvent> events = new ArrayList<>();
        publisher.addSubscriber(events::add);

        FixedEstimator estimator = new FixedEstimator(500); // 500 < 900

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatServiceReturningToolCalls("done"))
                .toolManager(trackingToolManager(new AtomicInteger()))
                .eventPublisher(publisher)
                .tokenEstimator(estimator)
                .build();

        AgentExecutionResult result = executor.execute(buildContext()).toCompletableFuture().join();

        assertEquals(AgentExecStatus.completed, result.getStatus(),
                "Below 90% the executor must complete normally");
        boolean foundForcedStop = events.stream()
                .anyMatch(e -> e.getEventType() == AgentEventType.FORCED_STOP);
        assertFalse(foundForcedStop, "No FORCED_STOP event when below 90%");
    }

    @Test
    void forcedStopStopsFurtherToolCalls() {
        DefaultAgentEventPublisher publisher = new DefaultAgentEventPublisher();
        publisher.addSubscriber(e -> {});
        AtomicInteger toolCalls = new AtomicInteger(0);
        FixedEstimator estimator = new FixedEstimator(950);

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatServiceReturningToolCalls("thinking"))
                .toolManager(trackingToolManager(toolCalls))
                .eventPublisher(publisher)
                .tokenEstimator(estimator)
                .build();

        AgentExecutionResult result = executor.execute(buildContext()).toCompletableFuture().join();

        assertEquals(AgentExecStatus.forced_stopped, result.getStatus());
        assertEquals(0, toolCalls.get(),
                "No tool call must execute after forced stop fires (loop breaks before reasoning)");
    }

    @Test
    void forcedStopConsumesEstimatorEstimateTokens() {
        DefaultAgentEventPublisher publisher = new DefaultAgentEventPublisher();
        publisher.addSubscriber(e -> {});
        FixedEstimator estimator = new FixedEstimator(950);

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatServiceReturningToolCalls("thinking"))
                .toolManager(trackingToolManager(new AtomicInteger()))
                .eventPublisher(publisher)
                .tokenEstimator(estimator)
                .build();

        executor.execute(buildContext()).toCompletableFuture().join();

        assertTrue(estimator.estimateCalls.get() >= 1,
                "Wiring: forced-stop check must actually consume the estimator's estimateTokens at runtime");
    }

    @Test
    void forcedStopEventCarriesEstimatePayload() {
        DefaultAgentEventPublisher publisher = new DefaultAgentEventPublisher();
        List<AgentEvent> events = new ArrayList<>();
        publisher.addSubscriber(events::add);
        FixedEstimator estimator = new FixedEstimator(950);

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatServiceReturningToolCalls("thinking"))
                .toolManager(trackingToolManager(new AtomicInteger()))
                .eventPublisher(publisher)
                .tokenEstimator(estimator)
                .build();

        executor.execute(buildContext()).toCompletableFuture().join();

        AgentEvent forcedStopEvent = events.stream()
                .filter(e -> e.getEventType() == AgentEventType.FORCED_STOP)
                .findFirst()
                .orElseThrow(() -> new AssertionError("FORCED_STOP event expected"));
        assertNotNull(forcedStopEvent.getPayload());
        assertEquals(950L, ((Number) forcedStopEvent.getPayload().get("estimatedTokens")).longValue());
        assertEquals((long) MAX_CONTEXT_TOKENS, ((Number) forcedStopEvent.getPayload().get("maxContextTokens")).longValue());
    }

    @Test
    void endToEndPipelineEscalationThenForcedStop() {
        DefaultAgentEventPublisher publisher = new DefaultAgentEventPublisher();
        List<AgentEvent> events = new ArrayList<>();
        publisher.addSubscriber(events::add);

        AtomicInteger toolCalls = new AtomicInteger(0);
        AtomicInteger summaryCalls = new AtomicInteger(0);
        AtomicReference<String> capturedSummaryRequest = new AtomicReference<>();

        // chatService serves both main reasoning (returns tool calls) and Layer 3 summary calls.
        IChatService chatService = new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                String systemPrompt = request.getSystemPrompt();
                if (systemPrompt != null && systemPrompt.contains("conversation summarizer")) {
                    summaryCalls.incrementAndGet();
                    capturedSummaryRequest.set(request.getLastUserPrompt());
                    return CompletableFuture.completedFuture(
                            ChatResponse.success(new ChatAssistantMessage("## Goal\ne2e goal\n## Progress\ndone")));
                }
                ChatAssistantMessage msg = new ChatAssistantMessage();
                msg.setContent("thinking");
                ChatToolCall call = new ChatToolCall();
                call.setId("tc-x");
                call.setName("test-tool");
                msg.setToolCalls(Collections.singletonList(call));
                return CompletableFuture.completedFuture(ChatResponse.success(msg));
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {};
            }
        };

        PipelineCompactor pipeline = new PipelineCompactor(
                new MicroCompressionCompactor(),
                new Layer2TurnPruningStrategy(),
                new Layer3FullSummaryStrategy(chatService)
        );

        FixedEstimator estimator = new FixedEstimator(950);

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatService)
                .toolManager(trackingToolManager(toolCalls))
                .eventPublisher(publisher)
                .contextCompactor(pipeline)
                .tokenEstimator(estimator)
                .build();

        AgentExecutionContext ctx = buildContext();
        ctx.getMessages().addAll(buildLargeConversation(40));

        AgentExecutionResult result = executor.execute(ctx).toCompletableFuture().join();

        // Status + event
        assertEquals(AgentExecStatus.forced_stopped, result.getStatus(),
                "End-to-end: status must be forced_stopped");
        AgentEvent forcedStopEvent = events.stream()
                .filter(e -> e.getEventType() == AgentEventType.FORCED_STOP)
                .findFirst()
                .orElseThrow(() -> new AssertionError("FORCED_STOP event expected in end-to-end"));
        assertNotNull(forcedStopEvent);

        // The pipeline (Layer 3) must have run during the forced-stop final summary
        assertTrue(summaryCalls.get() >= 1,
                "End-to-end: the Layer 3 summarization IChatService must be invoked during forced stop");

        // No tool calls after forced stop
        assertEquals(0, toolCalls.get(),
                "End-to-end: no tool calls must execute after forced stop");

        // Invariants: system + first user retained; pairing intact
        List<ChatMessage> out = ctx.getMessages();
        assertFalse(out.isEmpty());
        boolean hasSystem = out.stream().anyMatch(m -> m instanceof ChatSystemMessage);
        boolean hasFirstUser = out.stream().anyMatch(
                m -> m instanceof ChatUserMessage && "goal".equals(m.getContent()));
        assertTrue(hasSystem, "End-to-end: system message retained through pipeline + forced stop");
        assertTrue(hasFirstUser, "End-to-end: first user goal retained");

        Set<String> calledIds = new HashSet<>();
        Set<String> respondedIds = new HashSet<>();
        for (ChatMessage m : out) {
            if (m instanceof ChatAssistantMessage) {
                ChatAssistantMessage a = (ChatAssistantMessage) m;
                if (a.getToolCalls() != null) {
                    for (ChatToolCall tc : a.getToolCalls()) {
                        if (tc.getId() != null) calledIds.add(tc.getId());
                    }
                }
            }
            if (m instanceof ChatToolResponseMessage) {
                respondedIds.add(((ChatToolResponseMessage) m).getToolCallId());
            }
        }
        assertEquals(calledIds, respondedIds,
                "End-to-end: tool_call/tool_response pairing must remain intact through full pipeline + forced stop");
    }

    @Test
    void forcedStopUsesEstimatorNotAccumulatedUsage() {
        DefaultAgentEventPublisher publisher = new DefaultAgentEventPublisher();
        publisher.addSubscriber(e -> {});

        FixedEstimator estimator = new FixedEstimator(950);

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatServiceReturningToolCalls("thinking"))
                .toolManager(trackingToolManager(new AtomicInteger()))
                .eventPublisher(publisher)
                .tokenEstimator(estimator)
                .build();

        AgentExecutionContext ctx = buildContext();
        ctx.setTokensUsed(0); // accumulated usage is ZERO, but estimate is 950 -> must still force stop

        AgentExecutionResult result = executor.execute(ctx).toCompletableFuture().join();

        assertEquals(AgentExecStatus.forced_stopped, result.getStatus(),
                "Forced stop must be driven by the pre-call estimator, not accumulated post-call usage");
    }
}
