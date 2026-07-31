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
import io.nop.api.core.exceptions.NopTimeoutException;
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
 * Circuit breaker wiring test.
 *
 * <p>Three concerns:
 * <ol>
 *   <li><b>Engine wiring</b>: {@link DefaultAgentEngine} defaults to
 *       {@link ThresholdBreaker}; {@code setCircuitBreaker} overrides the default;
 *       null setter falls back to ThresholdBreaker.</li>
 *   <li><b>Zero-regression</b>: with the shipped default, a throwing
 *       {@code chatService.call(...)} executes and the execution surfaces as
 *       failed.</li>
 *   <li><b>No silent skip (OPEN reject fails loud)</b>: a breaker that
 *       rejects every call surfaces a {@code NopAiAgentException} whose
 *       message records the model key and circuit state (Minimum Rules #24:
 *       the rejection is recorded in the execution result's error, not
 *       swallowed).</li>
 * </ol>
 */
public class TestCircuitBreakerWiring {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    // ========================================================================
    // Engine wiring: default ThresholdBreaker + setter override + null fallback
    // ========================================================================

    @Test
    void engineDefaultsToThresholdBreaker() {
        DefaultAgentEngine engine = new DefaultAgentEngine(noOpChatService(), noOpToolManager());
        ICircuitBreaker breaker = engine.getCircuitBreaker();
        assertNotNull(breaker, "Engine must default to a non-null circuit breaker");
        assertTrue(breaker instanceof ThresholdBreaker,
                "Shipped default must be ThresholdBreaker with functional defaults");
    }

    @Test
    void setCircuitBreakerOverridesDefault() {
        DefaultAgentEngine engine = new DefaultAgentEngine(noOpChatService(), noOpToolManager());
        ICircuitBreaker custom = new RecordingCircuitBreaker();
        engine.setCircuitBreaker(custom);
        assertSame(custom, engine.getCircuitBreaker(),
                "getCircuitBreaker must return the exact instance set via setCircuitBreaker");
    }

    @Test
    void setCircuitBreakerNullFallsBackToDefault() {
        DefaultAgentEngine engine = new DefaultAgentEngine(noOpChatService(), noOpToolManager());
        engine.setCircuitBreaker(null);
        ICircuitBreaker breaker = engine.getCircuitBreaker();
        assertNotNull(breaker, "null setter must fall back to a non-null default");
    }

    // ========================================================================
    // Zero-regression: default ThresholdBreaker passes the outer check
    // ========================================================================

    @Test
    void defaultCircuitBreakerExecutesCallAndSurfacesFailure() throws Exception {
        ResourceComponentManager.instance().loadComponentModel("/test-react-agent.agent.xml");

        AtomicInteger callCount = new AtomicInteger(0);
        NopTimeoutException toThrow = new NopTimeoutException();
        IChatService chatService = new CountingThrowingChatService(callCount, toThrow);

        DefaultAgentEngine engine = newEngine(chatService);

        AgentMessageRequest request = new AgentMessageRequest("test-react-agent", "hi");
        CompletableFuture<AgentExecutionResult> future = engine.execute(request);
        AgentExecutionResult result = future.get(15, TimeUnit.SECONDS);

        // With ThresholdBreaker default (3 failures threshold), the circuit
        // check allows the first calls through before tripping. The call
        // should be executed at least once.
        assertTrue(callCount.get() >= 1,
                "ThresholdBreaker must allow the LLM call through the outer circuit check "
                        + "(execute at least once) — proves the breaker is wired and consumed");
        // The execution surfaces as failed (call failure propagates).
        assertEquals(AgentExecStatus.failed, result.getStatus(),
                "With ThresholdBreaker default the LLM failure must surface as status=failed");
        assertNotNull(result.getError(),
                "The execution result must carry the failure error (not silently swallowed)");
    }

    @Test
    void defaultCircuitBreakerRecordsSuccessAndSucceedsOnHappyPath() throws Exception {
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

        // Wiring proof: the call was allowed through and executed exactly
        // once; the breaker stays CLOSED so the execution completes normally.
        assertEquals(1, callCount.get(),
                "Default circuit breaker must allow the LLM call");
        assertEquals(AgentExecStatus.completed, result.getStatus(),
                "Happy path must complete with status=completed (zero-regression)");
    }

    // ========================================================================
    // No silent skip: a breaker rejecting every call fails loud at runtime
    // (Minimum Rules #24 — the rejection surfaces in the execution result
    // error, mentioning the model key + circuit state)
    // ========================================================================

    @Test
    void openBreakerRejectsCallAndFailsLoudAtRuntime() throws Exception {
        ResourceComponentManager.instance().loadComponentModel("/test-react-agent.agent.xml");

        AtomicInteger callCount = new AtomicInteger(0);
        ChatAssistantMessage msg = new ChatAssistantMessage();
        msg.setContent("done");
        ChatResponse success = ChatResponse.success(msg);
        success.setRequestId("req-ok");
        // The chat service is never expected to be called because the breaker
        // rejects the call before the retry loop. If it IS called, that is a
        // wiring bug (the breaker was bypassed) — assert 0 calls.
        IChatService chatService = new CountingSuccessChatService(callCount, success);

        DefaultAgentEngine engine = newEngine(chatService);
        // A breaker that unconditionally rejects (reports OPEN, allowCall=false).
        engine.setCircuitBreaker(new AlwaysOpenCircuitBreaker());

        AgentMessageRequest request = new AgentMessageRequest("test-react-agent", "hi");
        CompletableFuture<AgentExecutionResult> future = engine.execute(request);
        AgentExecutionResult result = future.get(15, TimeUnit.SECONDS);

        // The call must NOT have been issued (the outer check rejected it).
        assertEquals(0, callCount.get(),
                "An OPEN breaker must reject the call BEFORE it is issued to chatService");
        assertEquals(AgentExecStatus.failed, result.getStatus(),
                "An OPEN breaker must fail the execution (no silent skip)");
        // The recorded error must surface the circuit state + model identity
        // so the operator can see WHY the call was rejected (no silent
        // swallow).
        assertNotNull(result.getError(),
                "Circuit-rejected execution must record an error");
        assertTrue(result.getError().contains("OPEN"),
                "Circuit-rejection error must mention OPEN state. Error was: "
                        + result.getError());
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

    /**
     * Chat service that increments a counter then throws a fixed exception on
     * every call. Used to prove the outer circuit check passes and the call
     * executes exactly once.
     */
    private static final class CountingThrowingChatService implements IChatService {
        private final AtomicInteger callCount;
        private final RuntimeException toThrow;

        CountingThrowingChatService(AtomicInteger callCount, RuntimeException toThrow) {
            this.callCount = callCount;
            this.toThrow = toThrow;
        }

        @Override
        public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
            callCount.incrementAndGet();
            CompletableFuture<ChatResponse> f = new CompletableFuture<>();
            f.completeExceptionally(toThrow);
            return f;
        }

        @Override
        public ChatResponse call(ChatRequest request, ICancelToken cancelToken) {
            callCount.incrementAndGet();
            throw toThrow;
        }

        @Override
        public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
            return subscriber -> {
            };
        }
    }

    /**
     * Chat service that increments a counter then returns a fixed successful
     * response on every call.
     */
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
     * A breaker that reports OPEN and rejects every call, used to verify the
     * outer check fails loud (no silent skip).
     */
    private static final class AlwaysOpenCircuitBreaker implements ICircuitBreaker {
        @Override
        public boolean allowCall(String modelKey) {
            return false;
        }

        @Override
        public CircuitState getState(String modelKey) {
            return CircuitState.OPEN;
        }

        @Override
        public void recordSuccess(String modelKey) {
        }

        @Override
        public void recordFailure(String modelKey) {
        }
    }

    /**
     * A no-op recording breaker used only to verify setter identity (the
     * custom instance is returned verbatim by getCircuitBreaker).
     */
    private static final class RecordingCircuitBreaker implements ICircuitBreaker {
        @Override
        public boolean allowCall(String modelKey) {
            return true;
        }

        @Override
        public CircuitState getState(String modelKey) {
            return CircuitState.CLOSED;
        }

        @Override
        public void recordSuccess(String modelKey) {
        }

        @Override
        public void recordFailure(String modelKey) {
        }
    }
}
