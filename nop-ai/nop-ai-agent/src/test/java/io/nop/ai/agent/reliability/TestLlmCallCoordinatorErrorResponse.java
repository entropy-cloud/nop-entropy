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
import io.nop.ai.api.chat.ErrorClassification;
import io.nop.ai.api.chat.IChatService;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan W2e-3 (Phase 3) end-to-end test: the {@code LlmCallCoordinator} retry loop
 * now reads {@code errorClassification} from a non-success {@link ChatResponse} and
 * enters the RETRY decision for {@link ErrorClassification#RATE_LIMITED} / TRANSIENT
 * — whereas the pre-W2e-3 {@code !isSuccess()} branch terminated unconditionally.
 *
 * <p>Uses the full {@link DefaultAgentEngine} (same harness as {@code TestRetryPolicyWiring})
 * with a {@link IChatService} that <b>returns</b> error ChatResponses (does not throw),
 * exercising the response-level error path opened by Phase 2.</p>
 */
public class TestLlmCallCoordinatorErrorResponse {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    void rateLimitedErrorResponseTriggersRetry() throws Exception {
        ResourceComponentManager.instance().loadComponentModel("/test-react-agent.agent.xml");

        AtomicInteger callCount = new AtomicInteger(0);
        IChatService chatService = new ErrorResponseChatService(callCount,
                ChatResponse.error(ErrorClassification.RATE_LIMITED, 429,
                        "rate_limit_exceeded", "slow down", 500L));

        DefaultAgentEngine engine = newEngine(chatService);
        // StandardRetryPolicy default = 3 max attempts.
        engine.setRetryPolicy(new StandardRetryPolicy(3, 10L, 1000L));

        AgentMessageRequest request = new AgentMessageRequest("test-react-agent", "hi");
        AgentExecutionResult result = engine.execute(request).get(30, TimeUnit.SECONDS);

        // 核心：!isSuccess() + RATE_LIMITED 现在触发 RETRY（今天该分支终止）。
        // StandardRetryPolicy(maxAttempts=3) 会重试到耗尽 → callCount == 3。
        assertTrue(callCount.get() >= 2,
                "RATE_LIMITED error response must trigger retry (callCount=" + callCount.get()
                        + "); pre-W2e-3 this branch terminated after 1 call");
        assertEquals(AgentExecStatus.failed, result.getStatus(),
                "exhausting retries on a persistent RATE_LIMITED error must surface failed");
        assertNotNull(result.getError(),
                "the failure must be recorded (no silent skip)");
    }

    @Test
    void transientErrorResponseAlsoRetries() throws Exception {
        ResourceComponentManager.instance().loadComponentModel("/test-react-agent.agent.xml");

        AtomicInteger callCount = new AtomicInteger(0);
        IChatService chatService = new ErrorResponseChatService(callCount,
                ChatResponse.error(ErrorClassification.TRANSIENT, 503,
                        "server_error", "unavailable", null));

        DefaultAgentEngine engine = newEngine(chatService);
        engine.setRetryPolicy(new StandardRetryPolicy(3, 10L, 1000L));

        AgentMessageRequest request = new AgentMessageRequest("test-react-agent", "hi");
        AgentExecutionResult result = engine.execute(request).get(30, TimeUnit.SECONDS);

        assertTrue(callCount.get() >= 2,
                "TRANSIENT error response must trigger retry (callCount=" + callCount.get() + ")");
        assertEquals(AgentExecStatus.failed, result.getStatus());
    }

    @Test
    void quotaExceededErrorResponseStopsImmediately() throws Exception {
        // 零回归：QUOTA_EXCEEDED 仍 STOP（今日行为不变，账号链延期）。
        ResourceComponentManager.instance().loadComponentModel("/test-react-agent.agent.xml");

        AtomicInteger callCount = new AtomicInteger(0);
        IChatService chatService = new ErrorResponseChatService(callCount,
                ChatResponse.error(ErrorClassification.QUOTA_EXCEEDED, 429,
                        "insufficient_quota", "no money", null));

        DefaultAgentEngine engine = newEngine(chatService);
        engine.setRetryPolicy(new StandardRetryPolicy(3, 10L, 1000L));

        AgentMessageRequest request = new AgentMessageRequest("test-react-agent", "hi");
        AgentExecutionResult result = engine.execute(request).get(30, TimeUnit.SECONDS);

        assertEquals(1, callCount.get(),
                "QUOTA_EXCEEDED must STOP immediately (no retry) — zero-regression: callCount=" + callCount.get());
        assertEquals(AgentExecStatus.failed, result.getStatus());
    }

    private static DefaultAgentEngine newEngine(IChatService chatService) {
        return new DefaultAgentEngine(
                chatService, noOpToolManager(), new InMemorySessionStore(),
                new AllowAllPermissionProvider(),
                new AllowAllToolAccessChecker(),
                new AllowAllPathAccessChecker(),
                NoOpContentGuardrail.noOp());
    }

    /**
     * Chat service that returns a fixed error {@link ChatResponse} (does NOT throw),
     * incrementing a counter on each call. Exercises the response-level error path.
     */
    private static final class ErrorResponseChatService implements IChatService {
        private final AtomicInteger callCount;
        private final ChatResponse errorResponse;

        ErrorResponseChatService(AtomicInteger callCount, ChatResponse errorResponse) {
            this.callCount = callCount;
            this.errorResponse = errorResponse;
        }

        @Override
        public ChatResponse call(ChatRequest request, ICancelToken cancelToken) {
            callCount.incrementAndGet();
            return errorResponse;
        }

        @Override
        public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
            callCount.incrementAndGet();
            return CompletableFuture.completedFuture(errorResponse);
        }

        @Override
        public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
            return subscriber -> {
            };
        }
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
