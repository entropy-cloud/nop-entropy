package io.nop.ai.agent.router;

import io.nop.ai.agent.engine.AgentExecutionContext;
import io.nop.ai.agent.engine.AgentExecutionResult;
import io.nop.ai.agent.engine.DefaultAgentEngine;
import io.nop.ai.agent.engine.ReActAgentExecutor;
import io.nop.ai.agent.guardrail.NoOpContentGuardrail;
import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.model.AgentModel;
import io.nop.ai.agent.reliability.IRetryPolicy;
import io.nop.ai.agent.reliability.RetryOutcome;
import io.nop.ai.agent.security.AllowAllPathAccessChecker;
import io.nop.ai.agent.security.AllowAllPermissionProvider;
import io.nop.ai.agent.security.AllowAllToolAccessChecker;
import io.nop.ai.agent.session.InMemorySessionStore;
import io.nop.ai.agent.usage.IUsageRecorder;
import io.nop.ai.agent.usage.UsageRecord;
import io.nop.ai.api.chat.ChatOptions;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.messages.ChatUsage;
import io.nop.ai.api.chat.messages.ChatUserMessage;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 209 Phase 2 tests: model fallback chain consumption by the ReAct retry
 * loop (Minimum Rules #22 end-to-end, #23 wiring, #24 no silent skip, #25 new
 * feature coverage).
 */
public class TestSmartModelRouterFallback {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    private static ChatOptions tier(String provider, String model) {
        ChatOptions o = new ChatOptions();
        o.setProvider(provider);
        o.setModel(model);
        return o;
    }

    private static AgentExecutionContext simpleContext() {
        AgentModel model = new AgentModel();
        model.setTools(Collections.emptySet());
        io.nop.ai.core.model.ChatOptionsModel co = new io.nop.ai.core.model.ChatOptionsModel();
        co.setModel("default");
        co.setProvider("default");
        model.setChatOptions(co);
        AgentExecutionContext ctx = AgentExecutionContext.create(model, "fallback-session");
        ctx.setMaxIterations(10);
        ctx.addMessage(new ChatUserMessage("hi"));
        return ctx;
    }

    /** Always-FALLBACK retry policy. */
    private static IRetryPolicy alwaysFallback() {
        return ctx -> RetryOutcome.fallback();
    }

    /**
     * Chat service that throws a transient error for {@code failModel} and
     * returns a successful response (with usage) for any other model. Records
     * the sequence of models actually sent to the LLM.
     */
    private static final class FailOnModelChatService implements IChatService {
        final AtomicInteger callCount = new AtomicInteger(0);
        final List<String> modelsCalled = Collections.synchronizedList(new ArrayList<>());
        final String failModel;

        FailOnModelChatService(String failModel) {
            this.failModel = failModel;
        }

        @Override
        public ChatResponse call(ChatRequest request, ICancelToken cancelToken) {
            String model = request.getOptions().getModel();
            modelsCalled.add(model);
            callCount.incrementAndGet();
            if (failModel.equals(model)) {
                throw new NopTimeoutException();
            }
            ChatAssistantMessage msg = new ChatAssistantMessage();
            msg.setContent("done.");
            ChatResponse resp = ChatResponse.success(msg);
            resp.setUsage(new ChatUsage(10, 5));
            return resp;
        }

        @Override
        public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
            return CompletableFuture.completedFuture(call(request, cancelToken));
        }

        @Override
        public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
            return subscriber -> {
            };
        }
    }

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
            AiToolModel m = new AiToolModel();
            m.setName(toolName);
            m.setDescription("stub");
            return m;
        }
    }

    // ========================================================================
    // (1) getFallback unit behaviour
    // ========================================================================

    @Test
    void getFallbackReturnsNextChainModelMergedWithCurrent() {
        SmartModelRouter router = SmartModelRouter.builder()
                .tierModel(Complexity.COMPLEX, tier("strong", "strong-model"))
                .fallback(Complexity.COMPLEX, tier("mid", "mid-model"))
                .build();

        ChatOptions current = tier("strong", "strong-model");
        current.setTools(List.of(io.nop.ai.api.chat.messages.ChatToolDefinition.of("t", "d")));
        current.autoToolChoice();

        ChatOptions fb = router.getFallback(current);

        assertNotNull(fb, "configured fallback chain must return the next model");
        assertEquals("mid-model", fb.getModel());
        assertEquals("mid", fb.getProvider());
        // Tools must be preserved (merge of current + fallback tier options).
        assertNotNull(fb.getTools());
        assertEquals(1, fb.getTools().size());
        assertEquals("auto", fb.getToolChoice());
    }

    @Test
    void getFallbackReturnsNullWhenChainExhausted() {
        SmartModelRouter router = SmartModelRouter.builder()
                .tierModel(Complexity.COMPLEX, tier("strong", "strong-model"))
                .fallback(Complexity.COMPLEX, tier("mid", "mid-model"))
                .build();

        // Tail of the chain → no further fallback.
        assertNull(router.getFallback(tier("mid", "mid-model")));
    }

    @Test
    void getFallbackReturnsNullForUnknownModel() {
        SmartModelRouter router = SmartModelRouter.builder()
                .tierModel(Complexity.COMPLEX, tier("strong", "strong-model"))
                .build();
        assertNull(router.getFallback(tier("other", "unknown-model")));
    }

    @Test
    void passThroughGetFallbackReturnsNull() {
        assertNull(PassThroughModelRouter.passThrough().getFallback(tier("p", "m")));
    }

    // ========================================================================
    // (2a) FALLBACK with a configured fallback model → switch + retry + success
    // ========================================================================

    @Test
    void fallbackSwitchesToFallbackModelAndRetriesSuccessfully() {
        SmartModelRouter router = SmartModelRouter.builder()
                .tierModel(Complexity.SIMPLE, tier("p", "primary-fail"))
                .fallback(Complexity.SIMPLE, tier("p", "fallback-ok"))
                .build();
        FailOnModelChatService chatService = new FailOnModelChatService("primary-fail");

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatService)
                .toolManager(new StubToolManager() {})
                .modelRouter(router)
                .retryPolicy(alwaysFallback())
                .build();

        AgentExecutionResult result = executor.execute(simpleContext()).toCompletableFuture().join();

        assertEquals(AgentExecStatus.completed, result.getStatus(),
                "fallback model retry must succeed");
        assertTrue(chatService.modelsCalled.contains("primary-fail"),
                "the primary model must have been attempted first");
        assertTrue(chatService.modelsCalled.contains("fallback-ok"),
                "the fallback model must have been used after FALLBACK");
        // The last call must be the fallback model (the one that succeeded).
        assertEquals("fallback-ok", chatService.modelsCalled.get(chatService.modelsCalled.size() - 1),
                "the final (successful) LLM call must use the fallback model");
    }

    // ========================================================================
    // (2b) FALLBACK with no fallback configured → fail loud (Minimum Rules #24)
    // ========================================================================

    @Test
    void fallbackFailsLoudWhenNoFallbackConfigured() {
        SmartModelRouter router = SmartModelRouter.builder()
                .tierModel(Complexity.SIMPLE, tier("p", "primary-fail"))
                .build(); // no fallback chain
        FailOnModelChatService chatService = new FailOnModelChatService("primary-fail");

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatService)
                .toolManager(new StubToolManager() {})
                .modelRouter(router)
                .retryPolicy(alwaysFallback())
                .build();

        AgentExecutionResult result = executor.execute(simpleContext()).toCompletableFuture().join();

        assertEquals(AgentExecStatus.failed, result.getStatus(),
                "FALLBACK with no fallback model must fail the execution");
        assertNotNull(result.getError());
        assertTrue(result.getError().contains("FALLBACK"),
                "fail-loud error must mention FALLBACK (no silent skip): " + result.getError());
        assertFalse(chatService.modelsCalled.contains("fallback-ok"),
                "no fallback model should have been attempted");
    }

    // ========================================================================
    // (2c) PassThroughModelRouter (null fallback) → fail loud (unchanged)
    // ========================================================================

    @Test
    void fallbackFailsLoudWithPassThroughRouter() {
        FailOnModelChatService chatService = new FailOnModelChatService("default");

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatService)
                .toolManager(new StubToolManager() {})
                .retryPolicy(alwaysFallback())
                .build(); // PassThroughModelRouter default

        AgentExecutionResult result = executor.execute(simpleContext()).toCompletableFuture().join();

        assertEquals(AgentExecStatus.failed, result.getStatus());
        assertNotNull(result.getError());
        assertTrue(result.getError().contains("FALLBACK"),
                "PassThrough (null fallback) must fail loud with FALLBACK in the error");
    }

    // ========================================================================
    // (3) Usage record attribution: fallback model recorded, not primary
    // ========================================================================

    @Test
    void usageRecordAttributesTokensToFallbackModel() {
        SmartModelRouter router = SmartModelRouter.builder()
                .tierModel(Complexity.SIMPLE, tier("p", "primary-fail"))
                .fallback(Complexity.SIMPLE, tier("p", "fallback-ok"))
                .build();
        FailOnModelChatService chatService = new FailOnModelChatService("primary-fail");

        List<UsageRecord> recorded = Collections.synchronizedList(new ArrayList<>());
        IUsageRecorder recorder = recorded::add;

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatService)
                .toolManager(new StubToolManager() {})
                .modelRouter(router)
                .retryPolicy(alwaysFallback())
                .usageRecorder(recorder)
                .build();

        AgentExecutionResult result = executor.execute(simpleContext()).toCompletableFuture().join();

        assertEquals(AgentExecStatus.completed, result.getStatus());
        assertEquals(1, recorded.size(),
                "exactly one usage record (the successful fallback call) must be recorded");
        UsageRecord ur = recorded.get(0);
        assertEquals("fallback-ok", ur.getAiModel(),
                "usage must be attributed to the fallback model that actually executed the call");
        assertEquals("p", ur.getAiProvider());
        assertFalse("primary-fail".equals(ur.getAiModel()),
                "usage must NOT be attributed to the failed primary model");
    }

    // ========================================================================
    // (4) End-to-end through DefaultAgentEngine (Minimum Rules #22)
    // ========================================================================

    @Test
    void endToEndFallbackThroughDefaultAgentEngine() throws Exception {
        ResourceComponentManager.instance().loadComponentModel("/test-react-agent.agent.xml");

        // All tiers route to the same primary that fails, with the same
        // fallback that succeeds — robust to any classification outcome.
        SmartModelRouter router = SmartModelRouter.builder()
                .tierModel(Complexity.SIMPLE, tier("p", "primary-fail"))
                .fallback(Complexity.SIMPLE, tier("p", "fallback-ok"))
                .tierModel(Complexity.MEDIUM, tier("p", "primary-fail"))
                .fallback(Complexity.MEDIUM, tier("p", "fallback-ok"))
                .tierModel(Complexity.COMPLEX, tier("p", "primary-fail"))
                .fallback(Complexity.COMPLEX, tier("p", "fallback-ok"))
                .build();

        AtomicReference<String> lastModel = new AtomicReference<>();
        AtomicInteger callCount = new AtomicInteger(0);
        IChatService chatService = new IChatService() {
            @Override
            public ChatResponse call(ChatRequest request, ICancelToken cancelToken) {
                String model = request.getOptions().getModel();
                lastModel.set(model);
                callCount.incrementAndGet();
                if ("primary-fail".equals(model)) {
                    throw new NopTimeoutException();
                }
                ChatAssistantMessage msg = new ChatAssistantMessage();
                msg.setContent("done.");
                ChatResponse resp = ChatResponse.success(msg);
                resp.setUsage(new ChatUsage(10, 5));
                return resp;
            }

            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                return CompletableFuture.completedFuture(call(request, cancelToken));
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {
                };
            }
        };

        DefaultAgentEngine engine = new DefaultAgentEngine(
                chatService, noOpToolManager(), new InMemorySessionStore(),
                new AllowAllPermissionProvider(),
                new AllowAllToolAccessChecker(),
                new AllowAllPathAccessChecker(),
                NoOpContentGuardrail.noOp(),
                router);
        engine.setRetryPolicy(alwaysFallback());

        io.nop.ai.agent.engine.AgentMessageRequest request =
                new io.nop.ai.agent.engine.AgentMessageRequest("test-react-agent", "hello");
        CompletableFuture<AgentExecutionResult> future = engine.execute(request);
        AgentExecutionResult result = future.get(30, TimeUnit.SECONDS);

        assertEquals(AgentExecStatus.completed, result.getStatus(),
                "end-to-end: DefaultAgentEngine -> ReAct -> SmartModelRouter -> LLM fail -> "
                        + "FALLBACK -> fallback model retry -> success");
        assertTrue(callCount.get() >= 2,
                "at least one primary attempt + one fallback attempt must have occurred");
        assertEquals("fallback-ok", lastModel.get(),
                "the final LLM call must use the fallback model");
    }

    private static IToolManager noOpToolManager() {
        return new IToolManager() {
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
                AiToolModel m = new AiToolModel();
                m.setName(toolName);
                m.setDescription("stub");
                return m;
            }
        };
    }
}
