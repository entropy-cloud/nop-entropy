package io.nop.ai.agent.reliability;

import io.nop.ai.agent.engine.AgentExecutionResult;
import io.nop.ai.agent.engine.AgentMessageRequest;
import io.nop.ai.agent.engine.DefaultAgentEngine;
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

import static io.nop.ai.core.AiCoreErrors.ARG_HTTP_STATUS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Plan 207 (L3-2) Phase 2 end-to-end + wiring test (Minimum Rules #22
 * end-to-end, #23 wiring, #24 no silent skip, #25 new feature coverage).
 *
 * <p>Proves the full path: {@code DefaultAgentEngine.execute()} → ReAct loop
 * → single-LLM-call retry loop → {@link StandardRetryPolicy} → RETRY on
 * transient failure → backoff → reissue → success. Three scenarios:
 * <ol>
 *   <li><b>End-to-end retry success</b>: with StandardRetryPolicy(maxAttempts=3),
 *       the chat service throws a transient exception on the first 2 calls and
 *       succeeds on the 3rd. Asserts the LLM call was executed 3 times (retry
 *       happened) and the execution completed with the success response.</li>
 *   <li><b>Max-attempts exhaustion fails loud</b>: with StandardRetryPolicy(2),
 *       every call throws a transient exception. Asserts the LLM call was
 *       executed exactly 2 times (maxAttempts reached, no infinite retry) and
 *       the execution surfaces as failed (no silent skip / null return).</li>
 *   <li><b>Non-transient immediate stop</b>: with StandardRetryPolicy(3), the
 *       chat service throws a NON_TRANSIENT (400) exception. Asserts the LLM
 *       call was executed exactly once (non-transient fails fast, no retry).</li>
 * </ol>
 */
public class TestStandardRetryPolicyEndToEnd {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    // ========================================================================
    // End-to-end: transient failure → retry → success (Minimum Rules #22, #23)
    // ========================================================================

    @Test
    void transientFailureRetriedAndSucceedsEndToEnd() throws Exception {
        ResourceComponentManager.instance().loadComponentModel("/test-react-agent.agent.xml");

        AtomicInteger callCount = new AtomicInteger(0);
        // The success response (final answer, no tool calls → 1 ReAct iteration).
        ChatAssistantMessage successMsg = new ChatAssistantMessage();
        successMsg.setContent("Done after retry.");
        ChatResponse success = ChatResponse.success(successMsg);
        success.setRequestId("req-retry-ok");

        // Chat service: first 2 calls throw a transient exception, 3rd succeeds.
        IChatService chatService = new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                int n = callCount.incrementAndGet();
                if (n <= 2) {
                    CompletableFuture<ChatResponse> f = new CompletableFuture<>();
                    f.completeExceptionally(new NopTimeoutException());
                    return f;
                }
                return CompletableFuture.completedFuture(success);
            }

            @Override
            public ChatResponse call(ChatRequest request, ICancelToken cancelToken) {
                int n = callCount.incrementAndGet();
                if (n <= 2) {
                    throw new NopTimeoutException();
                }
                return success;
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {
                };
            }
        };

        DefaultAgentEngine engine = newEngine(chatService);
        // maxAttempts=3, baseDelay=1ms (test speed), maxDelay=10ms.
        engine.setRetryPolicy(new StandardRetryPolicy(3, 1L, 10L));

        AgentMessageRequest request = new AgentMessageRequest("test-react-agent", "hi");
        CompletableFuture<AgentExecutionResult> future = engine.execute(request);
        AgentExecutionResult result = future.get(30, TimeUnit.SECONDS);

        // Anti-hollow: the LLM call was actually retried (3 calls = 2 retries).
        assertEquals(3, callCount.get(),
                "StandardRetryPolicy(maxAttempts=3) must retry transient failures: "
                        + "expected 3 LLM calls (1 initial + 2 retries), got " + callCount.get());
        // End-to-end: the retry succeeded and the execution completed normally.
        assertEquals(AgentExecStatus.completed, result.getStatus(),
                "After successful retry the execution must complete normally");
    }

    // ========================================================================
    // Max-attempts exhaustion fails loud (Minimum Rules #24: no silent skip)
    // ========================================================================

    @Test
    void maxAttemptsExhaustedFailsLoud() throws Exception {
        ResourceComponentManager.instance().loadComponentModel("/test-react-agent.agent.xml");

        AtomicInteger callCount = new AtomicInteger(0);
        // Chat service: every call throws a transient exception.
        IChatService chatService = new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                callCount.incrementAndGet();
                CompletableFuture<ChatResponse> f = new CompletableFuture<>();
                f.completeExceptionally(new NopTimeoutException());
                return f;
            }

            @Override
            public ChatResponse call(ChatRequest request, ICancelToken cancelToken) {
                callCount.incrementAndGet();
                throw new NopTimeoutException();
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {
                };
            }
        };

        DefaultAgentEngine engine = newEngine(chatService);
        // maxAttempts=2 → 2 total calls then STOP.
        engine.setRetryPolicy(new StandardRetryPolicy(2, 1L, 10L));

        AgentMessageRequest request = new AgentMessageRequest("test-react-agent", "hi");
        CompletableFuture<AgentExecutionResult> future = engine.execute(request);
        AgentExecutionResult result = future.get(30, TimeUnit.SECONDS);

        // Exactly 2 calls (maxAttempts reached, no infinite retry).
        assertEquals(2, callCount.get(),
                "StandardRetryPolicy(maxAttempts=2) must stop after 2 transient failures: "
                        + "expected exactly 2 LLM calls, got " + callCount.get());
        // Fails loud: execution surfaces as failed (not silently returning null/empty).
        assertEquals(AgentExecStatus.failed, result.getStatus(),
                "Max-attempts exhaustion must surface as status=failed, not silent success");
        assertNotNull(result.getError(),
                "The failure must be recorded (no silent swallow)");
    }

    // ========================================================================
    // Non-transient fails fast (no retry even with StandardRetryPolicy)
    // ========================================================================

    @Test
    void nonTransientFailsFastNoRetry() throws Exception {
        ResourceComponentManager.instance().loadComponentModel("/test-react-agent.agent.xml");

        AtomicInteger callCount = new AtomicInteger(0);
        // Chat service: throws a 400 NON_TRANSIENT on every call.
        io.nop.api.core.exceptions.NopException badRequest =
                new io.nop.api.core.exceptions.NopException(io.nop.ai.core.AiCoreErrors.ERR_AI_SERVICE_HTTP_ERROR)
                        .param(ARG_HTTP_STATUS, 400);
        IChatService chatService = new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                callCount.incrementAndGet();
                CompletableFuture<ChatResponse> f = new CompletableFuture<>();
                f.completeExceptionally(badRequest);
                return f;
            }

            @Override
            public ChatResponse call(ChatRequest request, ICancelToken cancelToken) {
                callCount.incrementAndGet();
                throw badRequest;
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {
                };
            }
        };

        DefaultAgentEngine engine = newEngine(chatService);
        // maxAttempts=3, but NON_TRANSIENT must fail fast (1 call only).
        engine.setRetryPolicy(new StandardRetryPolicy(3, 1L, 10L));

        AgentMessageRequest request = new AgentMessageRequest("test-react-agent", "hi");
        CompletableFuture<AgentExecutionResult> future = engine.execute(request);
        AgentExecutionResult result = future.get(30, TimeUnit.SECONDS);

        // Exactly 1 call — NON_TRANSIENT fails fast, no retry.
        assertEquals(1, callCount.get(),
                "NON_TRANSIENT (400) must fail fast: expected exactly 1 LLM call (no retry), got "
                        + callCount.get());
        assertEquals(AgentExecStatus.failed, result.getStatus(),
                "NON_TRANSIENT failure must surface as status=failed");
        assertNotNull(result.getError());
    }

    // ========================================================================
    // 429 RATE_LIMITED is retried by StandardRetryPolicy
    // ========================================================================

    @Test
    void rateLimited429IsRetried() throws Exception {
        ResourceComponentManager.instance().loadComponentModel("/test-react-agent.agent.xml");

        AtomicInteger callCount = new AtomicInteger(0);
        ChatAssistantMessage successMsg = new ChatAssistantMessage();
        successMsg.setContent("Recovered from 429.");
        ChatResponse success = ChatResponse.success(successMsg);
        success.setRequestId("req-429-ok");

        io.nop.api.core.exceptions.NopException rateLimit =
                new io.nop.api.core.exceptions.NopException(io.nop.ai.core.AiCoreErrors.ERR_AI_SERVICE_HTTP_ERROR)
                        .param(ARG_HTTP_STATUS, 429);
        IChatService chatService = new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                int n = callCount.incrementAndGet();
                if (n == 1) {
                    CompletableFuture<ChatResponse> f = new CompletableFuture<>();
                    f.completeExceptionally(rateLimit);
                    return f;
                }
                return CompletableFuture.completedFuture(success);
            }

            @Override
            public ChatResponse call(ChatRequest request, ICancelToken cancelToken) {
                int n = callCount.incrementAndGet();
                if (n == 1) {
                    throw rateLimit;
                }
                return success;
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {
                };
            }
        };

        DefaultAgentEngine engine = newEngine(chatService);
        engine.setRetryPolicy(new StandardRetryPolicy(3, 1L, 10L));

        AgentMessageRequest request = new AgentMessageRequest("test-react-agent", "hi");
        CompletableFuture<AgentExecutionResult> future = engine.execute(request);
        AgentExecutionResult result = future.get(30, TimeUnit.SECONDS);

        // 429 RATE_LIMITED is retried: first call 429, second call succeeds.
        assertEquals(2, callCount.get(),
                "429 RATE_LIMITED must be retried by StandardRetryPolicy: expected 2 LLM calls, got "
                        + callCount.get());
        assertEquals(AgentExecStatus.completed, result.getStatus(),
                "After 429 retry success the execution must complete normally");
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
}
