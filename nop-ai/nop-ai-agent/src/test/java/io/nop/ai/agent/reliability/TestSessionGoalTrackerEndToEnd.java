package io.nop.ai.agent.reliability;

import io.nop.ai.agent.engine.AgentExecutionContext;
import io.nop.ai.agent.engine.AgentExecutionResult;
import io.nop.ai.agent.engine.AgentMessageRequest;
import io.nop.ai.agent.engine.DefaultAgentEngine;
import io.nop.ai.agent.engine.ReActAgentExecutor;
import io.nop.ai.agent.guardrail.NoOpContentGuardrail;
import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.model.AgentModel;
import io.nop.ai.agent.security.AllowAllPathAccessChecker;
import io.nop.ai.agent.security.AllowAllPermissionProvider;
import io.nop.ai.agent.security.AllowAllToolAccessChecker;
import io.nop.ai.agent.session.InMemorySessionStore;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.messages.ChatToolCall;
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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 211 (L3-3) Phase 2 end-to-end + wiring verification (Minimum Rules #22
 * end-to-end, #23 wiring, #24 no silent skip).
 *
 * <p>Drives the {@link ReActAgentExecutor} ReAct loop from the executor entry
 * point with a chat service that always requests the same tool call with the
 * same arguments. A {@link SessionGoalTracker} (threshold 3) is wired via the
 * executor Builder. The full path runs:
 *
 * <pre>
 *   reactLoop iteration 0: assessGoal → PROGRESSING → LLM → tool call S
 *                          → recordIteration(S) → dispatch
 *   reactLoop iteration 1: assessGoal → PROGRESSING (S x1) → LLM → tool call S
 *                          → recordIteration(S) → dispatch
 *   reactLoop iteration 2: assessGoal → PROGRESSING (S x2) → LLM → tool call S
 *                          → recordIteration(S) → dispatch
 *   reactLoop iteration 3: assessGoal → STUCK (S x3) → abort(escalated)
 * </pre>
 *
 * <p>The loop aborts at iteration 3 having issued only 3 LLM calls — well
 * below maxIterations (10) — and the result status is {@code escalated} with
 * a non-empty error recording the abort cause (no silent skip). This proves
 * the tracker is consumed at the per-iteration boundary at runtime and the
 * end-to-end stuck-detection path is connected (Anti-Hollow).
 */
public class TestSessionGoalTrackerEndToEnd {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    void repeatingSameToolCallAbortsAsEscalatedBeforeMaxIterations() throws Exception {
        AgentModel model = new AgentModel();
        model.setTools(Collections.singleton("read_file"));
        AgentExecutionContext ctx = AgentExecutionContext.create(model, "stuck-session");
        ctx.setMaxIterations(10);

        // The exact same tool call every iteration — the agent is stuck in a
        // loop. Args use a non-trivial map to exercise the signature build.
        ChatToolCall toolCall = new ChatToolCall();
        toolCall.setId("call_loop");
        toolCall.setName("read_file");
        toolCall.setArguments(Map.of("path", "/tmp/loop", "offset", 0));

        AtomicInteger chatCallCount = new AtomicInteger(0);
        IChatService chatService = new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                chatCallCount.incrementAndGet();
                ChatAssistantMessage msg = new ChatAssistantMessage();
                msg.setContent("reading");
                msg.setToolCalls(List.of(toolCall));
                return CompletableFuture.completedFuture(ChatResponse.success(msg));
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {
                };
            }
        };

        IToolManager toolManager = new StubToolManager() {
            @Override
            public CompletableFuture<AiToolCallResult> callTool(String toolName, AiToolCall call, IToolExecuteContext context) {
                return CompletableFuture.completedFuture(AiToolCallResult.successResult(0, "file content"));
            }
        };

        // Wired via the Builder with the same path DefaultAgentEngine uses.
        SessionGoalTracker tracker = new SessionGoalTracker(5, 3);
        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatService)
                .toolManager(toolManager)
                .goalTracker(tracker)
                .build();

        AgentExecutionResult result = executor.execute(ctx).toCompletableFuture().get(30, TimeUnit.SECONDS);

        // End-to-end path: 3 LLM calls (iterations 0,1,2 each produce the
        // same signature S), then at iteration 3 assessGoal returns STUCK and
        // the loop aborts BEFORE issuing a 4th LLM call.
        assertEquals(3, chatCallCount.get(),
                "STUCK must abort the loop at iteration 3 after exactly 3 LLM calls "
                        + "(well below maxIterations=10). Actual calls: " + chatCallCount.get());
        assertTrue(result.getTotalIterations() < ctx.getMaxIterations(),
                "The loop must abort before exhausting the iteration budget. "
                        + "iterations=" + result.getTotalIterations() + ", max=" + ctx.getMaxIterations());
        // No silent skip: the abort surfaces as escalated with a recorded error.
        assertEquals(AgentExecStatus.escalated, result.getStatus(),
                "A STUCK assessment must abort the loop with status=escalated");
        assertNotNull(result.getError(),
                "The escalated abort must record a non-empty error (no silent skip)");
        assertTrue(result.getError().contains("stuck"),
                "The error must describe the stuck/looping cause. Error was: " + result.getError());
        // Wiring proof: the tracker actually assessed STUCK for this session.
        assertEquals(GoalAssessment.STUCK, tracker.assessGoal("stuck-session"),
                "The tracker must report STUCK for the session that repeated the tool call");
    }

    @Test
    void defaultNoOpTrackerLetsLoopRunToCompletionWithoutStuckAbort() throws Exception {
        // Control: with the shipped default, the same repeating tool-call
        // scenario does NOT abort early — proving SessionGoalTracker (not some
        // unrelated guard) is what produced the escalated abort above. The
        // chat service stops emitting tool calls after a few iterations so the
        // loop completes normally.
        AgentModel model = new AgentModel();
        model.setTools(Collections.singleton("read_file"));
        AgentExecutionContext ctx = AgentExecutionContext.create(model, "noop-session");
        ctx.setMaxIterations(10);

        ChatToolCall toolCall = new ChatToolCall();
        toolCall.setId("call_loop");
        toolCall.setName("read_file");
        toolCall.setArguments(Map.of("path", "/tmp/loop"));

        AtomicInteger chatCallCount = new AtomicInteger(0);
        IChatService chatService = new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                int n = chatCallCount.incrementAndGet();
                ChatAssistantMessage msg = new ChatAssistantMessage();
                if (n < 4) {
                    msg.setContent("reading");
                    msg.setToolCalls(List.of(toolCall));
                } else {
                    msg.setContent("done");
                }
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
                // Default goalTracker (NoOpGoalTracker) — no stuck detection.
                .build();

        AgentExecutionResult result = executor.execute(ctx).toCompletableFuture().get(30, TimeUnit.SECONDS);

        assertEquals(AgentExecStatus.completed, result.getStatus(),
                "With the NoOp default the same repeats must NOT abort the loop (zero-regression control)");
    }

    @Test
    void engineWiringAbortsAsEscalatedViaDefaultAgentEngine() throws Exception {
        // Verify the end-to-end path also works when the tracker is wired
        // through DefaultAgentEngine.setGoalTracker (the production wiring).
        ResourceComponentManager_loadTestAgent();

        AtomicInteger chatCallCount = new AtomicInteger(0);
        ChatToolCall toolCall = new ChatToolCall();
        toolCall.setId("call_loop");
        toolCall.setName("read_file");
        toolCall.setArguments(Map.of("path", "/tmp/loop"));
        IChatService chatService = new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                chatCallCount.incrementAndGet();
                ChatAssistantMessage msg = new ChatAssistantMessage();
                msg.setContent("reading");
                msg.setToolCalls(List.of(toolCall));
                return CompletableFuture.completedFuture(ChatResponse.success(msg));
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {
                };
            }
        };

        DefaultAgentEngine engine = new DefaultAgentEngine(
                chatService, new StubToolManager() {}, new InMemorySessionStore(),
                new AllowAllPermissionProvider(),
                new AllowAllToolAccessChecker(),
                new AllowAllPathAccessChecker(),
                NoOpContentGuardrail.noOp());
        engine.setGoalTracker(new SessionGoalTracker(5, 3));

        AgentMessageRequest request = new AgentMessageRequest("test-react-agent", "hi");
        AgentExecutionResult result = engine.execute(request).get(30, TimeUnit.SECONDS);

        assertTrue(chatCallCount.get() < 10,
                "STUCK must abort before maxIterations via the engine wiring. Calls: " + chatCallCount.get());
        assertEquals(AgentExecStatus.escalated, result.getStatus(),
                "Engine-wired SessionGoalTracker must abort with status=escalated");
        assertNotNull(result.getError(),
                "The escalated abort must record an error");
    }

    private static void ResourceComponentManager_loadTestAgent() {
        io.nop.core.resource.component.ResourceComponentManager.instance()
                .loadComponentModel("/test-react-agent.agent.xml");
    }

    /**
     * Minimal stub tool manager that successfully executes every tool.
     */
    private abstract static class StubToolManager implements IToolManager {
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
            return null;
        }
    }
}
