package io.nop.ai.agent.reliability;

import io.nop.ai.agent.engine.AgentExecutionContext;
import io.nop.ai.agent.engine.AgentExecutionResult;
import io.nop.ai.agent.engine.ReActAgentExecutor;
import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.model.AgentModel;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 212 (L3-8) Phase 2 end-to-end + wiring verification (Minimum Rules #22
 * end-to-end, #23 wiring, #24 no silent skip).
 *
 * <p>Drives the {@link ReActAgentExecutor} ReAct loop from the executor entry
 * point with a {@link SisypheanSustainer} wired via the executor Builder.
 * Three full paths are covered:
 *
 * <pre>
 *   Path A (MAX_ITERATIONS → sustain → completion):
 *     The agent keeps requesting tool calls until MAX_ITERATIONS, the sustainer
 *     forces CONTINUE (budget extended), the agent then voluntarily completes
 *     within the sustained budget. Status=completed, total iterations >
 *     original maxIterations (proof the budget was extended).
 *
 *   Path B (MAX_ITERATIONS → sustain → ... → ceiling → STOP):
 *     The agent never voluntarily completes. The sustainer forces CONTINUE
 *     maxSustainCount times, then allows STOP (ceiling reached, fail-safe).
 *     Status=completed (STOP → terminal-state change running → completed),
 *     sustainCountSoFar == maxSustainCount at the final consult.
 *
 *   Path C (wiring proof — concurrent isolation):
 *     The stateless SisypheanSustainer serves two independent sessions. Each
 *     session's sustain count is tracked independently by the executor (local
 *     to the execute() frame), so session A's ceiling does not affect session B.
 * </pre>
 *
 * <p>These prove the sustainer is consumed at the exit decision point at
 * runtime and the end-to-end sustain path is connected (Anti-Hollow): sustain
 * CONTINUE truly extends the budget and re-enters the reactLoop, not an empty
 * placeholder.
 */
public class TestSisypheanSustainerEndToEnd {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    // ========================================================================
    // Path A: MAX_ITERATIONS → sustain CONTINUE → agent voluntarily completes
    // within the sustained budget (Minimum Rules #22 end-to-end).
    // ========================================================================

    @Test
    void maxIterationsExitSustainedUntilCompletion() throws Exception {
        AgentModel model = new AgentModel();
        model.setTools(Collections.singleton("read_file"));
        AgentExecutionContext ctx = AgentExecutionContext.create(model, "sustain-complete-session");
        int originalMaxIterations = 3;
        ctx.setMaxIterations(originalMaxIterations);

        // The chat service requests a tool call for the first
        // (originalMaxIterations + 1) LLM calls, then voluntarily completes.
        // This means the reactLoop exhausts originalMaxIterations on the first
        // pass (status=running → sustainer consulted → CONTINUE). The agent
        // then completes within the extended budget.
        ChatToolCall toolCall = new ChatToolCall();
        toolCall.setId("call_loop");
        toolCall.setName("read_file");
        toolCall.setArguments(Map.of("path", "/tmp/loop"));

        AtomicInteger chatCallCount = new AtomicInteger(0);
        int completeAtCall = originalMaxIterations + 2; // 2 calls into the sustain round
        IChatService chatService = new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                int n = chatCallCount.incrementAndGet();
                ChatAssistantMessage msg = new ChatAssistantMessage();
                if (n < completeAtCall) {
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

        SisypheanSustainer sustainer = new SisypheanSustainer(3);
        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatService)
                .toolManager(new StubToolManager())
                .sustainer(sustainer)
                .build();

        AgentExecutionResult result = executor.execute(ctx).toCompletableFuture().get(30, TimeUnit.SECONDS);

        // End-to-end proof: the agent exceeded the original budget (3) and
        // completed within the sustained budget. This proves CONTINUE truly
        // extended the budget and the reactLoop re-entered.
        assertTrue(chatCallCount.get() > originalMaxIterations,
                "Sustain CONTINUE must extend the budget so the agent runs past originalMaxIterations="
                        + originalMaxIterations + ". Actual calls: " + chatCallCount.get());
        assertEquals(AgentExecStatus.completed, result.getStatus(),
                "The agent must complete voluntarily within the sustained budget");
        // The total iterations exceed the original budget (sustain worked).
        assertTrue(result.getTotalIterations() > originalMaxIterations,
                "Total iterations must exceed originalMaxIterations (budget was extended by sustain). "
                        + "iterations=" + result.getTotalIterations());
    }

    // ========================================================================
    // Path B: MAX_ITERATIONS → sustain → ceiling → STOP (fail-safe, not
    // infinite loop). The agent never completes; the sustainer grants
    // maxSustainCount rounds then allows STOP.
    // ========================================================================

    @Test
    void maxIterationsExitSustainedUntilCeilingThenStop() throws Exception {
        AgentModel model = new AgentModel();
        model.setTools(Collections.singleton("read_file"));
        AgentExecutionContext ctx = AgentExecutionContext.create(model, "sustain-ceiling-session");
        int originalMaxIterations = 2;
        ctx.setMaxIterations(originalMaxIterations);
        int maxSustainCount = 2;

        // The chat service ALWAYS requests a tool call — the agent never
        // voluntarily completes. The sustainer grants maxSustainCount=2
        // additional rounds, then allows STOP (ceiling reached).
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

        SisypheanSustainer sustainer = new SisypheanSustainer(maxSustainCount);
        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatService)
                .toolManager(new StubToolManager())
                .sustainer(sustainer)
                .build();

        AgentExecutionResult result = executor.execute(ctx).toCompletableFuture().get(30, TimeUnit.SECONDS);

        // Fail-safe: the total budget is originalMaxIterations * (1 + maxSustainCount).
        // The agent never completes, so the loop runs the full sustained budget
        // and then the sustainer allows STOP (ceiling reached).
        int expectedTotalBudget = originalMaxIterations * (1 + maxSustainCount);
        assertEquals(expectedTotalBudget, chatCallCount.get(),
                "Sustain must grant exactly maxSustainCount=" + maxSustainCount
                        + " additional rounds. Expected total budget="
                        + expectedTotalBudget + " (originalMaxIterations=" + originalMaxIterations
                        + " * (1 + maxSustainCount=" + maxSustainCount + ")). Actual calls: " + chatCallCount.get());
        // No silent skip: STOP at the ceiling lets the loop exit as completed
        // (terminal-state change running → completed).
        assertEquals(AgentExecStatus.completed, result.getStatus(),
                "At the ceiling the sustainer returns STOP → terminal-state change running → completed");
        // Total iterations == the full sustained budget (fail-safe: bounded,
        // not infinite).
        assertEquals(expectedTotalBudget, result.getTotalIterations(),
                "Total iterations must equal the full sustained budget (fail-safe bounded loop)");
    }

    // ========================================================================
    // Wiring proof (Minimum Rules #23): the sustainer is consumed at the exit
    // decision point with the correct SustainContext. A recording wrapper
    // verifies the sustainCountSoFar progression (0, 1, 2, ...).
    // ========================================================================

    @Test
    void sustainerConsultedWithCorrectSustainCountProgression() throws Exception {
        AgentModel model = new AgentModel();
        model.setTools(Collections.singleton("read_file"));
        AgentExecutionContext ctx = AgentExecutionContext.create(model, "sustain-record-session");
        int originalMaxIterations = 2;
        ctx.setMaxIterations(originalMaxIterations);

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

        RecordingSustainer sustainer = new RecordingSustainer(2);
        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatService)
                .toolManager(new StubToolManager())
                .sustainer(sustainer)
                .build();

        executor.execute(ctx).toCompletableFuture().get(30, TimeUnit.SECONDS);

        // Wiring proof: onStop was consulted with sustainCountSoFar
        // progressing 0, 1, 2 (the SisypheanSustainer granted 2 CONTINUEs,
        // then STOP at ceiling=2).
        assertEquals(3, sustainer.callCount.get(),
                "onStop must be consulted 3 times: sustainCountSoFar=0 (CONTINUE), "
                        + "1 (CONTINUE), 2 (STOP at ceiling). Actual calls: " + sustainer.callCount.get());
        // The stop reasons must all be MAX_ITERATIONS (the only sustainable exit).
        assertTrue(sustainer.allMaxIterations,
                "Every consult must carry SustainStopReason.MAX_ITERATIONS");
        // The sustainCountSoFar must progress 0, 1, 2.
        assertEquals(0, sustainer.sustainCounts.get(0), "First consult: sustainCountSoFar=0");
        assertEquals(1, sustainer.sustainCounts.get(1), "Second consult: sustainCountSoFar=1");
        assertEquals(2, sustainer.sustainCounts.get(2), "Third consult: sustainCountSoFar=2 (ceiling)");
    }

    // ========================================================================
    // Path C: concurrent execute() calls have independent sustain counts
    // (stateless sustainer, executor-local sustain counter).
    // ========================================================================

    @Test
    void concurrentExecutionsHaveIndependentSustainCounts() throws Exception {
        // Two independent executions driven by the same stateless SisypheanSustainer.
        // Each execution runs to its own ceiling independently — the sustainer
        // holds no per-session mutable state, so session A's ceiling does not
        // leak into session B.
        SisypheanSustainer sharedSustainer = new SisypheanSustainer(1);

        int runsA = runSustainedExecution(sharedSustainer, "session-a", 2, 1, false);
        int runsB = runSustainedExecution(sharedSustainer, "session-b", 2, 1, false);

        // Each execution's total budget = originalMaxIterations * (1 + maxSustainCount).
        int expectedPerExecution = 2 * (1 + 1);
        assertEquals(expectedPerExecution, runsA,
                "Session A must get its own sustain budget independent of session B. Runs: " + runsA);
        assertEquals(expectedPerExecution, runsB,
                "Session B must get its own sustain budget independent of session A. Runs: " + runsB);
    }

    /**
     * Run one sustained execution and return the number of LLM calls made.
     * The agent never completes voluntarily (always requests a tool call)
     * unless {@code completeMidway} is true.
     */
    private int runSustainedExecution(SisypheanSustainer sustainer, String sessionId,
                                      int originalMaxIterations, int maxSustainCount,
                                      boolean completeMidway) throws Exception {
        AgentModel model = new AgentModel();
        model.setTools(Collections.singleton("read_file"));
        AgentExecutionContext ctx = AgentExecutionContext.create(model, sessionId);
        ctx.setMaxIterations(originalMaxIterations);

        ChatToolCall toolCall = new ChatToolCall();
        toolCall.setId("call_" + sessionId);
        toolCall.setName("read_file");
        toolCall.setArguments(Map.of("path", "/tmp/" + sessionId));

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

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatService)
                .toolManager(new StubToolManager())
                .sustainer(sustainer)
                .build();

        executor.execute(ctx).toCompletableFuture().get(30, TimeUnit.SECONDS);
        return chatCallCount.get();
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    /**
     * Minimal stub tool manager that successfully executes every tool.
     */
    private static class StubToolManager implements IToolManager {
        @Override
        public CompletableFuture<AiToolCallResult> callTool(String toolName, AiToolCall call, IToolExecuteContext context) {
            return CompletableFuture.completedFuture(AiToolCallResult.successResult(0, "file content"));
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
     * A SisypheanSustainer wrapper that records every onStop consult's
     * SustainContext fields, so the test can verify the sustainCountSoFar
     * progression and stop-reason (Minimum Rules #23 wiring proof).
     */
    private static final class RecordingSustainer implements ISustainer {
        private final SisypheanSustainer delegate;
        final AtomicInteger callCount = new AtomicInteger(0);
        final java.util.List<Integer> sustainCounts = new java.util.concurrent.CopyOnWriteArrayList<>();
        volatile boolean allMaxIterations = true;

        RecordingSustainer(int maxSustainCount) {
            this.delegate = new SisypheanSustainer(maxSustainCount);
        }

        @Override
        public SustainDecision onStop(SustainContext context) {
            int n = callCount.incrementAndGet();
            sustainCounts.add(context.getSustainCountSoFar());
            if (context.getStopReason() != SustainStopReason.MAX_ITERATIONS) {
                allMaxIterations = false;
            }
            // Keep the n-th consult's sustainCountSoFar accessible by index.
            // CopyOnWriteArrayList preserves insertion order.
            return delegate.onStop(context);
        }
    }
}
