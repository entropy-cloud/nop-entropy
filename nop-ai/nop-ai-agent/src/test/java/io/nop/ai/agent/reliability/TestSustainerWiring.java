package io.nop.ai.agent.reliability;

import io.nop.ai.agent.engine.AgentExecutionContext;
import io.nop.ai.agent.engine.AgentExecutionResult;
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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 212 (L3-8) Phase 1 wiring + zero-regression test (Minimum Rules #23
 * wiring, #24 no silent skip, #25 new feature coverage).
 *
 * <p>Three concerns:
 * <ol>
 *   <li><b>Engine wiring</b>: {@link DefaultAgentEngine} defaults to
 *       {@link NoOpSustainer}; {@code setSustainer} overrides the default;
 *       null setter falls back to NoOpSustainer.</li>
 *   <li><b>Zero-regression (default NoOp)</b>: with the shipped default, an
 *       agent that exhausts its iteration budget (MAX_ITERATIONS truncation)
 *       completes normally with status=completed — the sustainer consult
 *       returns STOP, the budget is not extended, and the post-loop
 *       terminal-state change proceeds as normal. Proves the wiring is
 *       connected and zero-regression.</li>
 *   <li><b>Wiring proof (runtime consumption)</b>: a recording sustainer that
 *       always returns STOP has its {@code onStop} called exactly once when
 *       the reactLoop exits due to MAX_ITERATIONS truncation — proving the
 *       sustainer is consumed at the exit decision point, not merely a
 *       dangling field. The {@link SustainContext} carries the correct
 *       {@link SustainStopReason#MAX_ITERATIONS} stop reason and a zero
 *       {@code sustainCountSoFar}.</li>
 * </ol>
 */
public class TestSustainerWiring {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    // ========================================================================
    // Engine wiring: default NoOpSustainer + setter override + null fallback
    // ========================================================================

    @Test
    void engineDefaultsToNoOpSustainer() {
        DefaultAgentEngine engine = new DefaultAgentEngine(noOpChatService(), noOpToolManager());
        ISustainer sustainer = engine.getSustainer();
        assertNotNull(sustainer, "Engine must default to a non-null sustainer");
        assertTrue(sustainer instanceof NoOpSustainer,
                "Shipped default must be the NoOpSustainer pass-through");
    }

    @Test
    void setSustainerOverridesDefault() {
        DefaultAgentEngine engine = new DefaultAgentEngine(noOpChatService(), noOpToolManager());
        ISustainer custom = new RecordingSustainer();
        engine.setSustainer(custom);
        assertSame(custom, engine.getSustainer(),
                "getSustainer must return the exact instance set via setSustainer");
    }

    @Test
    void setSustainerNullFallsBackToNoOp() {
        DefaultAgentEngine engine = new DefaultAgentEngine(noOpChatService(), noOpToolManager());
        engine.setSustainer(null);
        ISustainer sustainer = engine.getSustainer();
        assertNotNull(sustainer, "null setter must fall back to a non-null NoOpSustainer default");
        assertTrue(sustainer instanceof NoOpSustainer);
    }

    // ========================================================================
    // Zero-regression: default NoOpSustainer does not change behaviour.
    // An agent that exhausts its iteration budget completes normally
    // (sustainer consult returns STOP, budget not extended, status → completed).
    // ========================================================================

    @Test
    void defaultNoOpSustainerMaxIterationsExitCompletesNormally() throws Exception {
        // A chat service that always requests the same tool call — the agent
        // never completes voluntarily, so the reactLoop runs until
        // maxIterations is exhausted and exits with status=running. With the
        // default NoOpSustainer the consult returns STOP and the post-loop
        // terminal-state change sets status=completed (zero-regression: same
        // as pre-plan-212).
        AgentModel model = new AgentModel();
        model.setTools(Collections.singleton("read_file"));
        AgentExecutionContext ctx = AgentExecutionContext.create(model, "noop-sustain-session");
        ctx.setMaxIterations(3);

        ChatToolCall toolCall = new ChatToolCall();
        toolCall.setId("call_loop");
        toolCall.setName("read_file");
        toolCall.setArguments(Map.of("path", "/tmp/loop"));

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

        // Default goalTracker (NoOpGoalTracker) and default sustainer
        // (NoOpSustainer) — both pass-through, zero-regression.
        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatService)
                .toolManager(new StubToolManager() {})
                .build();

        AgentExecutionResult result = executor.execute(ctx).toCompletableFuture().get(30, TimeUnit.SECONDS);

        // The loop must have run the full budget (no early completion) and
        // then exited as completed (zero-regression: NoOpSustainer STOP →
        // terminal-state change running → completed).
        assertEquals(3, chatCallCount.get(),
                "The reactLoop must have run exactly maxIterations=3 iterations before exiting");
        assertEquals(AgentExecStatus.truncated, result.getStatus(),
                "AR-14 (plan 277): With the default NoOpSustainer a MAX_ITERATIONS exit is now reported as "
                        + "truncated (STOP → terminal-state change running → truncated).");
    }

    // ========================================================================
    // Wiring proof (Minimum Rules #23): a recording sustainer is actually
    // consumed at the exit decision point when MAX_ITERATIONS truncation
    // occurs. onStop is called exactly once (STOP = no sustain round) with
    // the correct SustainContext.
    // ========================================================================

    @Test
    void recordingSustainerIsConsumedOnMaxIterationsExit() throws Exception {
        AgentModel model = new AgentModel();
        model.setTools(Collections.singleton("read_file"));
        AgentExecutionContext ctx = AgentExecutionContext.create(model, "sustain-session");
        ctx.setMaxIterations(3);

        ChatToolCall toolCall = new ChatToolCall();
        toolCall.setId("call_loop");
        toolCall.setName("read_file");
        toolCall.setArguments(Map.of("path", "/tmp/loop"));

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

        RecordingSustainer sustainer = new RecordingSustainer();
        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatService)
                .toolManager(toolManager)
                .sustainer(sustainer)
                .build();

        AgentExecutionResult result = executor.execute(ctx).toCompletableFuture().get(30, TimeUnit.SECONDS);

        // Wiring proof: the sustainer's onStop was actually invoked at the
        // exit decision point (MAX_ITERATIONS truncation). The recording
        // sustainer always returns STOP, so exactly one call (no sustain
        // round) and the loop exits as completed.
        assertEquals(1, sustainer.onStopCount.get(),
                "onStop must be consumed exactly once at the MAX_ITERATIONS exit decision point "
                        + "(Minimum Rules #23). A STOP return means no sustain round. "
                        + "Actual calls: " + sustainer.onStopCount.get());
        // The context carried the correct stop reason and a zero sustain count.
        assertEquals(SustainStopReason.MAX_ITERATIONS, sustainer.lastStopReason,
                "The SustainContext must carry MAX_ITERATIONS as the stop reason");
        assertEquals(0, sustainer.lastSustainCountSoFar,
                "The first consult must carry sustainCountSoFar=0 (no prior sustain rounds)");
        assertEquals(3, chatCallCount.get(),
                "The reactLoop must have run exactly maxIterations=3 iterations before exiting");
        // AR-14 (plan 277): STOP → terminal-state change running → truncated.
        assertEquals(AgentExecStatus.truncated, result.getStatus(),
                "A STOP-returning recording sustainer must let the loop exit as truncated");
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

    /**
     * A sustainer that always returns STOP but counts how many times its
     * onStop is invoked and captures the last SustainContext fields. Used to
     * prove the exit decision point actually consumes the sustainer at
     * runtime (Minimum Rules #23).
     */
    private static final class RecordingSustainer implements ISustainer {
        final AtomicInteger onStopCount = new AtomicInteger(0);
        volatile SustainStopReason lastStopReason = null;
        volatile int lastSustainCountSoFar = -1;

        @Override
        public SustainDecision onStop(SustainContext context) {
            onStopCount.incrementAndGet();
            lastStopReason = context.getStopReason();
            lastSustainCountSoFar = context.getSustainCountSoFar();
            return SustainDecision.STOP;
        }
    }
}
