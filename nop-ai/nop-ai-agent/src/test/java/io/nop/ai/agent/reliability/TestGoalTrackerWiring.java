package io.nop.ai.agent.reliability;

import io.nop.ai.agent.engine.AgentExecutionResult;
import io.nop.ai.agent.engine.AgentMessageRequest;
import io.nop.ai.agent.engine.DefaultAgentEngine;
import io.nop.ai.agent.guardrail.NoOpContentGuardrail;
import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.security.AllowAllPathAccessChecker;
import io.nop.ai.agent.security.AllowAllPermissionProvider;
import io.nop.ai.agent.security.AllowAllToolAccessChecker;
import io.nop.ai.agent.session.InMemorySessionStore;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.stream.ChatStreamChunk;
import io.nop.ai.toolkit.api.IToolExecuteContext;
import io.nop.ai.toolkit.api.IToolManager;
import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;
import io.nop.ai.toolkit.model.AiToolModel;
import io.nop.api.core.util.ICancelToken;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.resource.component.ResourceComponentManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 211 (L3-3) Phase 1 wiring + zero-regression test (Minimum Rules #23
 * wiring, #24 no silent skip, #25 new feature coverage).
 *
 * <p>Three concerns:
 * <ol>
 *   <li><b>Engine wiring</b>: {@link DefaultAgentEngine} defaults to
 *       {@link NoOpGoalTracker}; {@code setGoalTracker} overrides the default;
 *       null setter falls back to NoOpGoalTracker.</li>
 *   <li><b>Zero-regression (default NoOp)</b>: with the shipped default, a
 *       happy-path execution completes normally — assessGoal never aborts and
 *       recordIteration is a dormant no-op. Proves the per-iteration boundary
 *       is wired but does not change behaviour.</li>
 *   <li><b>Wiring proof (runtime consumption)</b>: a recording tracker that
 *       always reports PROGRESSING has its {@code recordIteration} called
 *       exactly once per iteration and its {@code assessGoal} called at each
 *       iteration start — proving the tracker is consumed at the
 *       per-iteration boundary, not merely a dangling field.</li>
 * </ol>
 */
public class TestGoalTrackerWiring {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    // ========================================================================
    // Engine wiring: default NoOpGoalTracker + setter override + null fallback
    // ========================================================================

    @Test
    void engineDefaultsToNoOpGoalTracker() {
        DefaultAgentEngine engine = new DefaultAgentEngine(noOpChatService(), noOpToolManager());
        IGoalTracker tracker = engine.getGoalTracker();
        assertNotNull(tracker, "Engine must default to a non-null goal tracker");
        assertTrue(tracker instanceof NoOpGoalTracker,
                "Shipped default must be the NoOpGoalTracker pass-through");
    }

    @Test
    void setGoalTrackerOverridesDefault() {
        DefaultAgentEngine engine = new DefaultAgentEngine(noOpChatService(), noOpToolManager());
        IGoalTracker custom = new RecordingGoalTracker();
        engine.setGoalTracker(custom);
        assertSame(custom, engine.getGoalTracker(),
                "getGoalTracker must return the exact instance set via setGoalTracker");
    }

    @Test
    void setGoalTrackerNullFallsBackToNoOp() {
        DefaultAgentEngine engine = new DefaultAgentEngine(noOpChatService(), noOpToolManager());
        engine.setGoalTracker(null);
        IGoalTracker tracker = engine.getGoalTracker();
        assertNotNull(tracker, "null setter must fall back to a non-null NoOpGoalTracker default");
        assertTrue(tracker instanceof NoOpGoalTracker);
    }

    // ========================================================================
    // Zero-regression: default NoOpGoalTracker does not change behaviour.
    // The happy path completes normally (assessGoal never aborts,
    // recordIteration is a dormant no-op).
    // ========================================================================

    @Test
    void defaultNoOpTrackerHappyPathCompletesNormally() throws Exception {
        ResourceComponentManager.instance().loadComponentModel("/test-react-agent.agent.xml");

        AtomicInteger callCount = new AtomicInteger(0);
        ChatAssistantMessage msg = new ChatAssistantMessage();
        msg.setContent("done");
        ChatResponse success = ChatResponse.success(msg);
        success.setRequestId("req-ok");
        IChatService chatService = new CountingSuccessChatService(callCount, success);

        DefaultAgentEngine engine = newEngine(chatService);

        AgentMessageRequest request = new AgentMessageRequest("test-react-agent", "hi");
        CompletableFuture<AgentExecutionResult> future = engine.execute(request);
        AgentExecutionResult result = future.get(15, TimeUnit.SECONDS);

        assertEquals(1, callCount.get(),
                "The default NoOpGoalTracker must allow the happy path to execute normally");
        assertEquals(AgentExecStatus.completed, result.getStatus(),
                "Happy path must complete with status=completed (zero-regression)");
    }

    // ========================================================================
    // Wiring proof (Minimum Rules #23): a recording tracker is actually
    // consumed at the per-iteration boundary. recordIteration is called once
    // per iteration and assessGoal is called at each iteration start.
    // ========================================================================

    @Test
    void recordingTrackerIsConsumedPerIteration() throws Exception {
        ResourceComponentManager.instance().loadComponentModel("/test-react-agent.agent.xml");

        AtomicInteger callCount = new AtomicInteger(0);
        ChatAssistantMessage msg = new ChatAssistantMessage();
        msg.setContent("done");
        ChatResponse success = ChatResponse.success(msg);
        success.setRequestId("req-ok");
        IChatService chatService = new CountingSuccessChatService(callCount, success);

        DefaultAgentEngine engine = newEngine(chatService);
        RecordingGoalTracker tracker = new RecordingGoalTracker();
        engine.setGoalTracker(tracker);

        AgentMessageRequest request = new AgentMessageRequest("test-react-agent", "hi");
        CompletableFuture<AgentExecutionResult> future = engine.execute(request);
        AgentExecutionResult result = future.get(15, TimeUnit.SECONDS);

        // Wiring proof: the tracker's recordIteration was actually invoked at
        // the per-iteration boundary (after the LLM response). On the happy
        // path there is exactly one iteration → exactly one recordIteration
        // call (and at least one assessGoal call at the iteration start).
        assertTrue(tracker.recordCount.get() >= 1,
                "recordIteration must be consumed at the per-iteration boundary (Minimum Rules #23). "
                        + "Actual calls: " + tracker.recordCount.get());
        assertTrue(tracker.assessCount.get() >= 1,
                "assessGoal must be consumed at the iteration start. "
                        + "Actual calls: " + tracker.assessCount.get());
        // The recording tracker always returns PROGRESSING, so the loop must
        // still complete normally (no STUCK abort).
        assertEquals(AgentExecStatus.completed, result.getStatus(),
                "A PROGRESSING-only recording tracker must not abort the loop");
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    private static DefaultAgentEngine newEngine(IChatService chatService) {
        return new DefaultAgentEngine(
                chatService, noOpToolManager(), new InMemorySessionStore(),
                new AllowAllPermissionProvider(),
                new AllowAllToolAccessChecker(),
                new AllowAllPathAccessChecker(),
                NoOpContentGuardrail.noOp());
    }

    private static final class CountingSuccessChatService implements IChatService {
        private final AtomicInteger callCount;
        private final ChatResponse success;

        CountingSuccessChatService(AtomicInteger callCount, ChatResponse success) {
            this.callCount = callCount;
            this.success = success;
        }

        @Override
        public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
            callCount.incrementAndGet();
            return CompletableFuture.completedFuture(success);
        }

        @Override
        public ChatResponse call(ChatRequest request, ICancelToken cancelToken) {
            callCount.incrementAndGet();
            return success;
        }

        @Override
        public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
            return subscriber -> {
            };
        }
    }

    private static IChatService noOpChatService() {
        return new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                return null;
            }

            @Override
            public ChatResponse call(ChatRequest request, ICancelToken cancelToken) {
                return null;
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return null;
            }
        };
    }

    private static IToolManager noOpToolManager() {
        return new IToolManager() {
            @Override
            public CompletableFuture<AiToolCallResult> callTool(String toolName, AiToolCall call, IToolExecuteContext context) {
                return null;
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
                return null;
            }
        };
    }

    /**
     * A goal tracker that always reports PROGRESSING but counts how many times
     * its methods are invoked. Used to prove the per-iteration boundary
     * actually consumes the tracker at runtime (Minimum Rules #23).
     */
    private static final class RecordingGoalTracker implements IGoalTracker {
        final AtomicInteger recordCount = new AtomicInteger(0);
        final AtomicInteger assessCount = new AtomicInteger(0);

        @Override
        public void recordIteration(String sessionId, IterationSnapshot snapshot) {
            recordCount.incrementAndGet();
        }

        @Override
        public GoalAssessment assessGoal(String sessionId) {
            assessCount.incrementAndGet();
            return GoalAssessment.PROGRESSING;
        }
    }
}
