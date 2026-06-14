package io.nop.ai.agent.completion;

import io.nop.ai.agent.engine.AgentEvent;
import io.nop.ai.agent.engine.AgentEventType;
import io.nop.ai.agent.engine.AgentExecutionContext;
import io.nop.ai.agent.engine.AgentExecutionResult;
import io.nop.ai.agent.engine.DefaultAgentEventPublisher;
import io.nop.ai.agent.engine.ReActAgentExecutor;
import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.model.AgentModel;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.messages.ChatMessage;
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
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end integration test exercising {@link RuleBasedCompletionJudge} through a real
 * {@link ReActAgentExecutor} loop. Verifies that the Judge is actually invoked at the
 * "no tool calls" branch (wiring verification, Minimum Rule #23) and that the complete
 * path from {@code execute()} entry to loop exit is exercised (end-to-end verification,
 * Anti-Hollow Rule #22).
 */
public class TestRuleBasedJudgeInReActLoop {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    private abstract static class StubToolManager implements IToolManager {
        @Override
        public CompletableFuture<AiToolCallResult> callTool(String toolName, AiToolCall call, IToolExecuteContext context) {
            return CompletableFuture.completedFuture(AiToolCallResult.successResult(0, "result"));
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
            AiToolModel model = new AiToolModel();
            model.setName(toolName);
            model.setDescription("Mock tool: " + toolName);
            return model;
        }
    }

    private static ChatAssistantMessage assistantNoTools(String content) {
        ChatAssistantMessage msg = new ChatAssistantMessage();
        msg.setContent(content);
        return msg;
    }

    /**
     * Decorator that wraps an {@link ICompletionJudge} and counts {@code decide()} invocations.
     * Used for wiring verification (Minimum Rule #23).
     */
    private static final class CountingJudge implements ICompletionJudge {
        final ICompletionJudge delegate;
        final AtomicInteger invocations = new AtomicInteger(0);
        final List<CompletionDecision> decisionsSeen = Collections.synchronizedList(new ArrayList<>());

        CountingJudge(ICompletionJudge delegate) {
            this.delegate = delegate;
        }

        @Override
        public CompletionDecision decide(ChatAssistantMessage assistantMessage, AgentExecutionContext ctx) {
            invocations.incrementAndGet();
            CompletionDecision d = delegate.decide(assistantMessage, ctx);
            decisionsSeen.add(d);
            return d;
        }
    }

    private static IChatService chatServiceReturningSequence(ChatAssistantMessage... messages) {
        AtomicInteger idx = new AtomicInteger(0);
        return new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                int i = idx.getAndIncrement();
                ChatAssistantMessage msg = i < messages.length ? messages[i] : messages[messages.length - 1];
                return CompletableFuture.completedFuture(ChatResponse.success(msg));
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {
                };
            }
        };
    }

    private static AgentExecutionContext newCtx(int maxIterations) {
        AgentModel model = new AgentModel();
        model.setTools(Collections.emptySet());
        AgentExecutionContext ctx = AgentExecutionContext.create(model, "test-session");
        ctx.setMaxIterations(maxIterations);
        return ctx;
    }

    // --- Scenario 1: empty-response Continue → re-enter → substantive Complete ---

    @Test
    void emptyResponseThenSubstantiveResponseCompletesLoop() {
        AgentExecutionContext ctx = newCtx(10);

        CountingJudge counting = new CountingJudge(RuleBasedCompletionJudge.ruleBased());
        AtomicInteger chatCallCount = new AtomicInteger(0);
        AtomicReference<List<ChatMessage>> secondCallMessages = new AtomicReference<>();
        IChatService chatService = new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                int n = chatCallCount.incrementAndGet();
                if (n == 2) {
                    secondCallMessages.set(new ArrayList<>(request.getMessages()));
                }
                ChatAssistantMessage msg = n == 1
                        ? assistantNoTools("") // empty → Continue
                        : assistantNoTools("The task is done. Here is the final answer."); // substantive → Complete
                return CompletableFuture.completedFuture(ChatResponse.success(msg));
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {
                };
            }
        };

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatService)
                .toolManager(new StubToolManager() {})
                .completionJudge(counting)
                .build();

        AgentExecutionResult result = executor.execute(ctx).toCompletableFuture().join();

        assertEquals(AgentExecStatus.completed, result.getStatus(),
                "Empty-then-substantive should complete normally");
        assertEquals(2, chatCallCount.get(),
                "LLM should be called exactly twice: first triggers Continue, second triggers Complete");
        assertEquals(2, counting.invocations.get(),
                "RuleBasedCompletionJudge.decide() must be invoked from within execute() at the no-tool-calls branch (wiring)");
        assertTrue(counting.decisionsSeen.get(0).isContinue(),
                "First decision must be Continue (empty response rule)");
        assertTrue(counting.decisionsSeen.get(1).isComplete(),
                "Second decision must be Complete (substantive response)");

        assertNotNull(secondCallMessages.get(), "Second LLM call messages should be captured");
        boolean hasContinuationUserMessage = secondCallMessages.get().stream()
                .filter(m -> m instanceof ChatUserMessage)
                .map(m -> (ChatUserMessage) m)
                .anyMatch(m -> m.getContent() != null
                        && m.getContent().contains("too short"));
        assertTrue(hasContinuationUserMessage,
                "RuleBasedCompletionJudge's default continuation message should be injected before re-entry LLM call");
    }

    // --- Scenario 2: near-budget Escalate → status escalated, reason recorded, no completion event ---

    @Test
    void nearBudgetSubstantiveResponseEscalatesWithReasonAndNoCompletionEvent() {
        // maxIterations=10, escalationRatio=0.9 (default) → threshold=9.0
        // Pre-set currentIteration=9 so the FIRST substantive response triggers Escalate.
        AgentExecutionContext ctx = newCtx(10);
        ctx.setCurrentIteration(9);

        CountingJudge counting = new CountingJudge(RuleBasedCompletionJudge.ruleBased());

        DefaultAgentEventPublisher publisher = new DefaultAgentEventPublisher();
        List<AgentEvent> events = Collections.synchronizedList(new ArrayList<>());
        publisher.addSubscriber(events::add);

        IChatService chatService = chatServiceReturningSequence(
                assistantNoTools("Substantive final answer that exceeds min length threshold."));

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatService)
                .toolManager(new StubToolManager() {})
                .completionJudge(counting)
                .eventPublisher(publisher)
                .build();

        AgentExecutionResult result = executor.execute(ctx).toCompletableFuture().join();

        assertEquals(AgentExecStatus.escalated, result.getStatus(),
                "Near-budget substantive response should Escalate");
        assertEquals(1, counting.invocations.get(),
                "Judge should be invoked once before Escalate breaks the loop");
        assertTrue(counting.decisionsSeen.get(0).isEscalate(),
                "RuleBasedCompletionJudge must produce Escalate on iteration 9 >= 10*0.9");

        String reason = result.getError();
        assertNotNull(reason, "Escalate reason should be recorded in lastError");
        assertTrue(reason.contains("9"),
                "Reason should reference currentIteration. Got: " + reason);
        assertTrue(reason.contains("10"),
                "Reason should reference maxIterations. Got: " + reason);
        assertEquals(reason, ctx.getMetadata().get("completion.escalateReason"),
                "Escalate reason should be recorded in metadata under completion.escalateReason");

        boolean hasCompletionEvent = events.stream()
                .anyMatch(e -> e.getEventType() == AgentEventType.EXECUTION_COMPLETED);
        assertFalse(hasCompletionEvent,
                "EXECUTION_COMPLETED event should NOT be published for escalated status (plan 159 post-loop exclusion)");
        assertTrue(events.stream().anyMatch(e -> e.getEventType() == AgentEventType.EXECUTION_STARTED),
                "EXECUTION_STARTED should still be published");
    }

    // --- Scenario 3: normal completion (substantive response well below budget) ---

    @Test
    void substantiveResponseBelowBudgetCompletesNormally() {
        AgentExecutionContext ctx = newCtx(10);
        // currentIteration stays at 0 (well below threshold 9.0)

        CountingJudge counting = new CountingJudge(RuleBasedCompletionJudge.ruleBased());

        DefaultAgentEventPublisher publisher = new DefaultAgentEventPublisher();
        List<AgentEvent> events = Collections.synchronizedList(new ArrayList<>());
        publisher.addSubscriber(events::add);

        IChatService chatService = chatServiceReturningSequence(
                assistantNoTools("Here is the complete answer to your question."));

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatService)
                .toolManager(new StubToolManager() {})
                .completionJudge(counting)
                .eventPublisher(publisher)
                .build();

        AgentExecutionResult result = executor.execute(ctx).toCompletableFuture().join();

        assertEquals(AgentExecStatus.completed, result.getStatus(),
                "Substantive response well below budget should Complete normally");
        assertEquals(1, counting.invocations.get(),
                "Judge should be invoked exactly once on the Complete path");
        assertTrue(counting.decisionsSeen.get(0).isComplete(),
                "First decision must be Complete (substantive response, iterations well below threshold)");
        assertTrue(events.stream().anyMatch(e -> e.getEventType() == AgentEventType.EXECUTION_COMPLETED),
                "EXECUTION_COMPLETED should be published for normal completion");
    }

    // --- Scenario 4: dead-loop protection bounds repeated empty-response Continues ---

    @Test
    void repeatedEmptyResponsesAreBoundedByDeadLoopProtection() {
        // maxIterations=100 so the loop guard never fires before dead-loop protection.
        AgentExecutionContext ctx = newCtx(100);

        CountingJudge counting = new CountingJudge(RuleBasedCompletionJudge.ruleBased());

        AtomicInteger chatCallCount = new AtomicInteger(0);
        IChatService chatService = new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                chatCallCount.incrementAndGet();
                // Always empty → RuleBasedCompletionJudge always returns Continue.
                return CompletableFuture.completedFuture(ChatResponse.success(assistantNoTools("")));
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {
                };
            }
        };

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatService)
                .toolManager(new StubToolManager() {})
                .completionJudge(counting)
                .build();

        AgentExecutionResult result = executor.execute(ctx).toCompletableFuture().join();

        assertEquals(AgentExecStatus.completed, result.getStatus(),
                "Dead-loop protection should force-exit with status completed");
        assertEquals(4, chatCallCount.get(),
                "LLM should be called 4 times: initial + 3 Continues, then dead-loop guard triggers before 4th Continue");
        // Each of the 4 LLM responses triggers exactly one judge.decide() call.
        // All 4 decisions are Continue (empty content), but only the first 3 inject continuation
        // messages; the 4th hits the dead-loop guard and force-exits.
        assertEquals(4, counting.invocations.get(),
                "Judge should be invoked on every LLM response, including the one that triggers dead-loop guard");
        assertTrue(counting.decisionsSeen.stream().allMatch(CompletionDecision::isContinue),
                "All 4 decisions from RuleBasedCompletionJudge should be Continue (empty content rule)");
    }

    // --- Wiring verification: RuleBasedCompletionJudge (not just any ICompletionJudge) is invoked ---

    @Test
    void ruleBasedJudgeSpecificallyIsInvokedFromExecutor() {
        // Use a custom config with a recognizable continuation message so we can verify
        // it was produced by RuleBasedCompletionJudge specifically (not NoOp or any other impl).
        String signature = "RULE_BASED_JUDGE_SIGNATURE_MESSAGE";
        CompletionRuleConfig customConfig = new CompletionRuleConfig(
                10, 0.9, signature, CompletionRuleConfig.DEFAULT_ESCALATION_REASON_TEMPLATE);
        RuleBasedCompletionJudge ruleBased = new RuleBasedCompletionJudge(customConfig);

        AgentExecutionContext ctx = newCtx(10);

        AtomicReference<List<ChatMessage>> secondCallMessages = new AtomicReference<>();
        AtomicInteger chatCallCount = new AtomicInteger(0);
        IChatService chatService = new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                int n = chatCallCount.incrementAndGet();
                if (n == 2) {
                    secondCallMessages.set(new ArrayList<>(request.getMessages()));
                }
                ChatAssistantMessage msg = n == 1
                        ? assistantNoTools(" ") // whitespace → Continue (Rule 1)
                        : assistantNoTools("Done with the task, here is the result."); // substantive → Complete
                return CompletableFuture.completedFuture(ChatResponse.success(msg));
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {
                };
            }
        };

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatService)
                .toolManager(new StubToolManager() {})
                .completionJudge(ruleBased)
                .build();

        AgentExecutionResult result = executor.execute(ctx).toCompletableFuture().join();

        assertEquals(AgentExecStatus.completed, result.getStatus());
        assertEquals(2, chatCallCount.get());
        assertNotNull(secondCallMessages.get());
        boolean injectedSignature = secondCallMessages.get().stream()
                .filter(m -> m instanceof ChatUserMessage)
                .map(m -> (ChatUserMessage) m)
                .anyMatch(m -> signature.equals(m.getContent()));
        assertTrue(injectedSignature,
                "RuleBasedCompletionJudge's custom continuation message must be injected, "
                        + "proving RuleBasedCompletionJudge (not NoOp) is wired into execute() at runtime");
    }

    // --- Custom config escalation ratio fires through the loop ---

    @Test
    void customEscalationRatioTriggersEscalateInsideLoop() {
        // escalationRatio=0.5, maxIterations=10 → threshold=5.0
        // Pre-set currentIteration=5 so the FIRST substantive response triggers Escalate.
        CompletionRuleConfig customConfig = new CompletionRuleConfig(10, 0.5,
                CompletionRuleConfig.DEFAULT_CONTINUATION_MESSAGE,
                "custom-escalation-template iter=%d max=%d ratio=%s");
        RuleBasedCompletionJudge judge = new RuleBasedCompletionJudge(customConfig);

        AgentExecutionContext ctx = newCtx(10);
        ctx.setCurrentIteration(5);

        IChatService chatService = chatServiceReturningSequence(
                assistantNoTools("Substantive answer that exceeds the minimum length threshold."));

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatService)
                .toolManager(new StubToolManager() {})
                .completionJudge(judge)
                .build();

        AgentExecutionResult result = executor.execute(ctx).toCompletableFuture().join();

        assertEquals(AgentExecStatus.escalated, result.getStatus(),
                "Custom escalationRatio=0.5 should fire Escalate at iteration 5 >= 10*0.5");
        String reason = result.getError();
        assertNotNull(reason);
        assertTrue(reason.startsWith("custom-escalation-template iter=5 max=10 ratio=0.5"),
                "Custom escalation template should be propagated through the loop. Got: " + reason);
    }
}
