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
 * End-to-end integration test exercising {@link LlmCompletionJudge} through a real
 * {@link ReActAgentExecutor} loop. Verifies that the LLM Judge is actually invoked at the
 * "no tool calls" branch (wiring verification, Minimum Rule #23) and that the complete path
 * from {@code execute()} entry → main LLM response → {@code LlmCompletionJudge.decide()} →
 * Judge's own {@code IChatService.call()} → verdict parsing → decision dispatch → loop exit
 * is exercised (end-to-end verification, Anti-Hollow Rule #22).
 *
 * <p>The main ReAct loop and the Judge use <b>separate</b> {@link IChatService} instances: the
 * Judge uses its own cheap-model service. The tests verify these are independent (the Judge's
 * service is never confused with the main loop's service).
 */
public class TestLlmJudgeInReActLoop {

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
     * Decorator that wraps an {@link ICompletionJudge} and counts {@code decide()} invocations
     * plus records the decisions seen. Used for wiring verification (Minimum Rule #23).
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

    /**
     * Main-loop {@link IChatService} that returns a configurable sequence of assistant messages.
     * Records invocation count and the messages of the second call (to verify continuation
     * injection).
     */
    private static final class MainChatService implements IChatService {
        final AtomicInteger callCount = new AtomicInteger(0);
        final AtomicReference<List<ChatMessage>> secondCallMessages = new AtomicReference<>();
        private final ChatAssistantMessage first;
        private final ChatAssistantMessage second;

        MainChatService(String firstContent) {
            this(firstContent, null);
        }

        MainChatService(String firstContent, String secondContent) {
            this.first = assistantNoTools(firstContent);
            this.second = secondContent != null ? assistantNoTools(secondContent) : first;
        }

        @Override
        public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
            int n = callCount.incrementAndGet();
            if (n == 2) {
                secondCallMessages.set(new ArrayList<>(request.getMessages()));
            }
            ChatAssistantMessage msg = n == 1 ? first : second;
            return CompletableFuture.completedFuture(ChatResponse.success(msg));
        }

        @Override
        public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
            return subscriber -> {
            };
        }
    }

    /**
     * Judge {@link IChatService} that returns a configurable sequence of verdict strings on
     * {@code call()}. Records invocation count to verify Judge/main independence.
     */
    private static final class JudgeChatService implements IChatService {
        final AtomicInteger callCount = new AtomicInteger(0);
        final AtomicReference<ChatRequest> lastRequest = new AtomicReference<>();
        private final String[] verdicts;
        private final RuntimeException failure;

        JudgeChatService(String... verdicts) {
            this.verdicts = verdicts;
            this.failure = null;
        }

        JudgeChatService(RuntimeException failure) {
            this.verdicts = new String[0];
            this.failure = failure;
        }

        @Override
        public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
            throw new UnsupportedOperationException("callAsync not used by LlmCompletionJudge");
        }

        @Override
        public ChatResponse call(ChatRequest request, ICancelToken cancelToken) {
            callCount.incrementAndGet();
            lastRequest.set(request);
            if (failure != null) {
                throw failure;
            }
            int i = callCount.get() - 1;
            String v = i < verdicts.length ? verdicts[i] : verdicts[verdicts.length - 1];
            return ChatResponse.success(assistantNoTools(v));
        }

        @Override
        public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
            return subscriber -> {
            };
        }
    }

    private static AgentExecutionContext newCtx(int maxIterations) {
        AgentModel model = new AgentModel();
        model.setName("test-agent");
        model.setTools(Collections.emptySet());
        AgentExecutionContext ctx = AgentExecutionContext.create(model, "test-session");
        ctx.setMaxIterations(maxIterations);
        return ctx;
    }

    private static LlmCompletionJudge llmJudge(JudgeChatService judgeSvc) {
        return new LlmCompletionJudge(LlmJudgeConfig.defaults(judgeSvc));
    }

    // --- Scenario 1: LLM says Complete ---

    @Test
    void llmJudgeCompletePathExitsLoopAndPublishesCompletedEvent() {
        AgentExecutionContext ctx = newCtx(10);

        MainChatService mainSvc = new MainChatService(
                "Here is the complete answer to your question.");
        JudgeChatService judgeSvc = new JudgeChatService("COMPLETE");

        CountingJudge counting = new CountingJudge(llmJudge(judgeSvc));

        DefaultAgentEventPublisher publisher = new DefaultAgentEventPublisher();
        List<AgentEvent> events = Collections.synchronizedList(new ArrayList<>());
        publisher.addSubscriber(events::add);

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(mainSvc)
                .toolManager(new StubToolManager() {})
                .completionJudge(counting)
                .eventPublisher(publisher)
                .build();

        AgentExecutionResult result = executor.execute(ctx).toCompletableFuture().join();

        assertEquals(AgentExecStatus.completed, result.getStatus(),
                "COMPLETE verdict should exit the loop with status completed");
        assertEquals(1, mainSvc.callCount.get(),
                "Main LLM should be called exactly once on the Complete path");
        assertEquals(1, judgeSvc.callCount.get(),
                "Judge LLM should be called exactly once (separate from main LLM — independence)");
        assertEquals(1, counting.invocations.get(),
                "LlmCompletionJudge.decide() must be invoked from within execute() at the no-tool-calls branch (wiring)");
        assertTrue(counting.decisionsSeen.get(0).isComplete(),
                "First decision must be Complete (LLM verdict COMPLETE)");
        assertTrue(events.stream().anyMatch(e -> e.getEventType() == AgentEventType.EXECUTION_COMPLETED),
                "EXECUTION_COMPLETED event should be published for normal completion");
        assertTrue(events.stream().anyMatch(e -> e.getEventType() == AgentEventType.EXECUTION_STARTED),
                "EXECUTION_STARTED should be published");
        // Metadata recorded (successful verdict, not fallback)
        assertEquals("COMPLETE", ctx.getMetadata().get(LlmCompletionJudge.META_KEY_VERDICT));
        assertEquals(false, ctx.getMetadata().get(LlmCompletionJudge.META_KEY_FALLBACK));
    }

    // --- Scenario 2: LLM says Continue → re-enter → Complete ---

    @Test
    void llmJudgeContinueThenCompleteReEntersAndInjectsContinuationMessage() {
        AgentExecutionContext ctx = newCtx(10);

        MainChatService mainSvc = new MainChatService(
                "Partial answer addressing some of the task.",
                "Now the answer is complete with all requirements addressed.");
        // First invocation says CONTINUE with a recognizable continuation message; second says COMPLETE.
        JudgeChatService judgeSvc = new JudgeChatService(
                "CONTINUE\nYou haven't addressed requirement Y",
                "COMPLETE");

        CountingJudge counting = new CountingJudge(llmJudge(judgeSvc));

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(mainSvc)
                .toolManager(new StubToolManager() {})
                .completionJudge(counting)
                .build();

        AgentExecutionResult result = executor.execute(ctx).toCompletableFuture().join();

        assertEquals(AgentExecStatus.completed, result.getStatus(),
                "Continue→re-enter→Complete should exit with status completed");
        assertEquals(2, mainSvc.callCount.get(),
                "Main LLM should be called exactly twice: first triggers Continue, second triggers Complete");
        assertEquals(2, judgeSvc.callCount.get(),
                "Judge LLM should be called exactly twice (once per main response)");
        assertEquals(2, counting.invocations.get(),
                "LlmCompletionJudge.decide() should be invoked twice through the loop (wiring)");
        assertTrue(counting.decisionsSeen.get(0).isContinue(),
                "First decision must be Continue (LLM verdict CONTINUE)");
        assertTrue(counting.decisionsSeen.get(1).isComplete(),
                "Second decision must be Complete (LLM verdict COMPLETE)");

        // Wiring/anti-hollow: the continuation message from the Judge must be injected before re-entry.
        assertNotNull(mainSvc.secondCallMessages.get(),
                "Second main LLM call messages should be captured");
        boolean hasContinuationUserMessage = mainSvc.secondCallMessages.get().stream()
                .filter(m -> m instanceof ChatUserMessage)
                .map(m -> (ChatUserMessage) m)
                .anyMatch(m -> m.getContent() != null
                        && m.getContent().contains("requirement Y"));
        assertTrue(hasContinuationUserMessage,
                "LlmCompletionJudge's CONTINUE continuation message should be injected before re-entry LLM call");

        // Independence: main and judge services are separate (main=2, judge=2, never shared)
        assertEquals(2, mainSvc.callCount.get());
        assertEquals(2, judgeSvc.callCount.get());
    }

    // --- Scenario 3: Judge LLM call fails → fallback Complete ---

    @Test
    void llmJudgeCallFailureFallsBackToCompleteWithoutPropagating() {
        AgentExecutionContext ctx = newCtx(10);

        MainChatService mainSvc = new MainChatService("Substantive final answer here.");
        // Judge's chat service always throws.
        JudgeChatService judgeSvc = new JudgeChatService(new RuntimeException("Judge LLM provider down"));

        CountingJudge counting = new CountingJudge(llmJudge(judgeSvc));

        DefaultAgentEventPublisher publisher = new DefaultAgentEventPublisher();
        List<AgentEvent> events = Collections.synchronizedList(new ArrayList<>());
        publisher.addSubscriber(events::add);

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(mainSvc)
                .toolManager(new StubToolManager() {})
                .completionJudge(counting)
                .eventPublisher(publisher)
                .build();

        AgentExecutionResult result = executor.execute(ctx).toCompletableFuture().join();

        assertEquals(AgentExecStatus.completed, result.getStatus(),
                "Judge LLM call failure should fall back to Complete (fail-open), exiting the loop");
        assertEquals(1, mainSvc.callCount.get(),
                "Main LLM should be called exactly once");
        assertEquals(1, judgeSvc.callCount.get(),
                "Judge LLM should have been attempted exactly once (then threw → fallback)");
        assertEquals(1, counting.invocations.get(),
                "decide() invoked once; the exception is internal to the Judge, not the loop");
        assertTrue(counting.decisionsSeen.get(0).isComplete(),
                "Fallback decision must be Complete (fail-open default) — no exception propagates to the ReAct loop");
        assertTrue(events.stream().anyMatch(e -> e.getEventType() == AgentEventType.EXECUTION_COMPLETED),
                "EXECUTION_COMPLETED should be published (fallback Complete behaves like normal completion)");
        // Metadata: fallback flag recorded
        assertEquals(true, ctx.getMetadata().get(LlmCompletionJudge.META_KEY_FALLBACK),
                "completion.llmJudgeFallback must be true on the fallback path");
        assertEquals("FALLBACK", ctx.getMetadata().get(LlmCompletionJudge.META_KEY_VERDICT),
                "completion.llmJudgeVerdict must be FALLBACK on the fallback path");
    }

    // --- Scenario 4: dead-loop protection bounds repeated CONTINUE ---

    @Test
    void repeatedContinueVerdictsAreBoundedByDeadLoopProtection() {
        // maxIterations=100 so the loop guard never fires before dead-loop protection.
        AgentExecutionContext ctx = newCtx(100);

        MainChatService mainSvc = new MainChatService("Substantive answer that never satisfies the judge.");
        // Judge always says CONTINUE → should hit dead-loop protection.
        JudgeChatService judgeSvc = new JudgeChatService("CONTINUE\nkeep going");

        CountingJudge counting = new CountingJudge(llmJudge(judgeSvc));

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(mainSvc)
                .toolManager(new StubToolManager() {})
                .completionJudge(counting)
                .build();

        AgentExecutionResult result = executor.execute(ctx).toCompletableFuture().join();

        assertEquals(AgentExecStatus.completed, result.getStatus(),
                "Dead-loop protection should force-exit with status completed after DEFAULT_MAX_COMPLETION_CONTINUES");
        // DEFAULT_MAX_COMPLETION_CONTINUES = 3. The loop runs: main call 1 → Continue(1) → main call 2
        // → Continue(2) → main call 3 → Continue(3) → main call 4 → Continue(4) hits the guard and
        // force-exits. So main LLM is called 4 times, decide() 4 times.
        assertEquals(4, mainSvc.callCount.get(),
                "Main LLM should be called 4 times: initial + 3 Continues, then dead-loop guard triggers");
        assertEquals(4, counting.invocations.get(),
                "LlmCompletionJudge.decide() should be invoked on every main response, including the guard-triggering one");
        assertTrue(counting.decisionsSeen.stream().allMatch(CompletionDecision::isContinue),
                "All 4 decisions should be Continue (Judge always says CONTINUE)");
    }

    // --- Wiring verification: LlmCompletionJudge (not just any ICompletionJudge) is invoked ---

    @Test
    void llmJudgeSpecificallyIsInvokedFromExecutor() {
        // The LLM Judge's distinct signature is the metadata it records. If NoOp or RuleBased were
        // wired instead, the metadata keys completion.llmJudgeVerdict / completion.llmJudgeFallback
        // would be absent.
        AgentExecutionContext ctx = newCtx(10);

        MainChatService mainSvc = new MainChatService("Final substantive answer.");
        JudgeChatService judgeSvc = new JudgeChatService("COMPLETE");

        LlmCompletionJudge llmJudge = llmJudge(judgeSvc);

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(mainSvc)
                .toolManager(new StubToolManager() {})
                .completionJudge(llmJudge)
                .build();

        AgentExecutionResult result = executor.execute(ctx).toCompletableFuture().join();

        assertEquals(AgentExecStatus.completed, result.getStatus());
        assertEquals(1, judgeSvc.callCount.get(),
                "LlmCompletionJudge must have invoked its own IChatService.call() — proving the LLM Judge "
                        + "(not NoOp/RuleBased) is wired into execute() at runtime");
        // The LLM-Judge-specific metadata keys are only written by LlmCompletionJudge.
        assertNotNull(ctx.getMetadata().get(LlmCompletionJudge.META_KEY_VERDICT),
                "LlmCompletionJudge-specific metadata must be present — proves the LLM Judge ran, not NoOp");
        assertNotNull(ctx.getMetadata().get(LlmCompletionJudge.META_KEY_FALLBACK));
        assertEquals("COMPLETE", ctx.getMetadata().get(LlmCompletionJudge.META_KEY_VERDICT));
        assertEquals(false, ctx.getMetadata().get(LlmCompletionJudge.META_KEY_FALLBACK));
    }

    // --- Independence: Judge service and main service are separate instances ---

    @Test
    void judgeAndMainChatServicesAreIndependent() {
        AgentExecutionContext ctx = newCtx(10);

        MainChatService mainSvc = new MainChatService("Answer.");
        JudgeChatService judgeSvc = new JudgeChatService("COMPLETE");

        CountingJudge counting = new CountingJudge(llmJudge(judgeSvc));

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(mainSvc)
                .toolManager(new StubToolManager() {})
                .completionJudge(counting)
                .build();

        executor.execute(ctx).toCompletableFuture().join();

        // Both are called exactly once, but they are distinct objects with independent counters.
        assertEquals(1, mainSvc.callCount.get(), "main service called once");
        assertEquals(1, judgeSvc.callCount.get(), "judge service called once independently");
        assertFalse((Object) mainSvc == (Object) judgeSvc, "main and judge services must be distinct instances");
    }
}
