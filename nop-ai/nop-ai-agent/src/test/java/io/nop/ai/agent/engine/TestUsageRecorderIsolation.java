package io.nop.ai.agent.engine;

import io.nop.ai.agent.compact.MicroCompressionCompactor;
import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.router.PassThroughModelRouter;
import io.nop.ai.agent.guardrail.NoOpContentGuardrail;
import io.nop.ai.agent.security.AllowAllPermissionProvider;
import io.nop.ai.agent.security.DefaultPathAccessChecker;
import io.nop.ai.agent.security.DefaultToolAccessChecker;
import io.nop.ai.agent.session.InMemorySessionStore;
import io.nop.ai.agent.usage.IUsageRecorder;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.messages.ChatUserMessage;
import io.nop.ai.core.agent.IModelSwitchedMessageWriter;
import io.nop.ai.api.exceptions.NopAiException;
import io.nop.ai.toolkit.api.IToolExecuteContext;
import io.nop.ai.toolkit.api.IToolManager;
import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;
import io.nop.ai.toolkit.model.AiToolModel;
import io.nop.ai.toolkit.model.AiToolCalls;
import io.nop.ai.toolkit.model.AiToolCallsResponse;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 3.1-C3-029（plan 2556 裁定 C）regression carrier: DB side-effect isolation.
 *
 * <ul>
 *   <li>The {@code isolateDbSideEffect} guard at the
 *       {@code usageRecorder.record} / {@code writeModelSwitched} call sites
 *       swallows recorder/writer failures (WARN + failure counter + threshold
 *       escalation) — the ReAct main flow continues.</li>
 *   <li>An end-to-end execution with a throwing usage recorder still
 *       completes successfully (a failed usage INSERT must not fail a
 *       successfully completed LLM turn).</li>
 * </ul>
 */
public class TestUsageRecorderIsolation {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    private static int resetFailureCounters() {
        ReActAgentExecutor.DB_SIDE_EFFECT_ESCALATIONS.set(0);
        return ReActAgentExecutor.DB_SIDE_EFFECT_FAILURES.getAndSet(0);
    }

    /**
     * Guard contract: a throwing side effect never propagates; the failure is
     * counted in the process-level counter.
     */
    @Test
    void guardSwallowsRecorderFailureAndCounts() {
        resetFailureCounters();
        AtomicInteger attempts = new AtomicInteger();
        // No exception escapes:
        ReActAgentExecutor.isolateDbSideEffect("usageRecorder.record", () -> {
            attempts.incrementAndGet();
            throw new IllegalStateException("db down");
        });
        assertEquals(1, attempts.get(), "the guarded action must actually run");
        assertEquals(1, ReActAgentExecutor.DB_SIDE_EFFECT_FAILURES.get(),
                "the failure must be counted in the process-level counter");
        assertEquals(0, ReActAgentExecutor.DB_SIDE_EFFECT_ESCALATIONS.get(),
                "a single failure must NOT escalate");
        // Main flow continued by construction: this assertion line executes.
    }

    /**
     * Escalation contract: once the failure count exceeds
     * {@code DB_SIDE_EFFECT_ESCALATION_THRESHOLD}, the WARN escalates to a
     * recurring-failure warning (counted in the escalation seam).
     */
    @Test
    void guardEscalatesAfterThreshold() {
        resetFailureCounters();
        int threshold = ReActAgentExecutor.DB_SIDE_EFFECT_ESCALATION_THRESHOLD;
        for (int i = 0; i < threshold + 3; i++) {
            ReActAgentExecutor.isolateDbSideEffect("writeModelSwitched", () -> {
                throw new IllegalStateException("db down");
            });
        }
        assertEquals(threshold + 3, ReActAgentExecutor.DB_SIDE_EFFECT_FAILURES.get(),
                "every isolated failure must be counted");
        assertEquals(3, ReActAgentExecutor.DB_SIDE_EFFECT_ESCALATIONS.get(),
                "exactly the failures beyond the threshold must escalate");
    }

    /**
     * End-to-end: a usage recorder that ALWAYS throws must not fail the
     * execution — the LLM turn completed, so the run completes successfully.
     */
    @Test
    void executionCompletesWhenUsageRecorderAlwaysThrows() {
        resetFailureCounters();
        InMemorySessionStore store = new InMemorySessionStore();

        DefaultAgentEngine engine = new DefaultAgentEngine.Builder(
                new AlwaysOkChatService(), stubToolManager())
                .sessionStore(store)
                .permissionProvider(new AllowAllPermissionProvider())
                .toolAccessChecker(new DefaultToolAccessChecker())
                .pathAccessChecker(new DefaultPathAccessChecker())
                .contentGuardrail(NoOpContentGuardrail.noOp())
                .modelRouter(PassThroughModelRouter.passThrough())
                .contextCompactor(new MicroCompressionCompactor())
                .build();
        engine.config().setUsageRecorder(throwingRecorder());
        engine.config().setModelSwitchedMessageWriter(throwingWriter());

        AgentMessageRequest request = new AgentMessageRequest("test-react-agent", "hi");
        AgentExecutionResult result = engine.execute(request)
                .toCompletableFuture().join();

        assertEquals(AgentExecStatus.completed, result.getStatus(),
                "a throwing usage recorder / writer must NOT fail the execution "
                        + "(3.1-C3-029 call-site isolation)");
        assertTrue(ReActAgentExecutor.DB_SIDE_EFFECT_FAILURES.get() >= 1,
                "the recorder failure must be counted");
    }

    // ---- helpers ------------------------------------------------------------

    private static IUsageRecorder throwingRecorder() {
        return record -> {
            throw new NopAiException("simulated DbUsageRecorder SQL failure");
        };
    }

    private static IModelSwitchedMessageWriter throwingWriter() {
        return (sessionId, fromModel, toModel, routingReason, complexity, seq) -> {
            throw new NopAiException("simulated DbModelSwitchedMessageWriter SQL failure");
        };
    }

    private static IToolManager stubToolManager() {
        return new IToolManager() {
            @Override
            public CompletableFuture<AiToolCallResult> callTool(String toolName, AiToolCall call, IToolExecuteContext context) {
                return CompletableFuture.completedFuture(AiToolCallResult.successResult(0, "ok"));
            }

            @Override
            public CompletableFuture<AiToolCallsResponse> callTools(AiToolCalls calls, IToolExecuteContext context) {
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

    /** Always returns a successful assistant response WITH usage (so the usage-record path runs). */
    static final class AlwaysOkChatService implements io.nop.ai.api.chat.IChatService {
        @Override
        public ChatResponse call(ChatRequest request, io.nop.api.core.util.ICancelToken cancelToken) {
            ChatAssistantMessage msg = new ChatAssistantMessage();
            msg.setContent("done");
            ChatResponse response = io.nop.ai.api.chat.ChatResponse.success(msg);
            io.nop.ai.api.chat.messages.ChatUsage usage = new io.nop.ai.api.chat.messages.ChatUsage(11, 7);
            response.setUsage(usage);
            return response;
        }

        @Override
        public CompletionStage<ChatResponse> callAsync(ChatRequest request, io.nop.api.core.util.ICancelToken cancelToken) {
            return CompletableFuture.completedFuture(call(request, cancelToken));
        }

        @Override
        public Flow.Publisher<io.nop.ai.api.chat.stream.ChatStreamChunk> callStream(
                ChatRequest request, io.nop.api.core.util.ICancelToken cancelToken) {
            return subscriber -> subscriber.onComplete();
        }
    }
}
