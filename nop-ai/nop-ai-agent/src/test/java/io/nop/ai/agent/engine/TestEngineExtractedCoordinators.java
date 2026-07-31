package io.nop.ai.agent.engine;

import io.nop.ai.agent.hook.AgentLifecyclePoint;
import io.nop.ai.agent.hook.HookContext;
import io.nop.ai.agent.hook.HookResult;
import io.nop.ai.agent.hook.IAgentLifecycleHook;
import io.nop.ai.agent.hook.DefaultHookRegistry;
import io.nop.ai.agent.hook.IHookRegistry;
import io.nop.ai.agent.middleware.IAgentMiddleware;
import io.nop.ai.agent.middleware.MiddlewareChain;
import io.nop.ai.agent.message.IAgentMessenger;
import io.nop.ai.agent.compact.NoOpContextCompactor;
import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.reliability.NoOpCheckpoint;
import io.nop.ai.agent.reliability.StandardRetryPolicy;
import io.nop.ai.agent.reliability.ThresholdBreaker;
import io.nop.ai.agent.router.PassThroughModelRouter;
import io.nop.ai.agent.security.DefaultDenialLedger;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.ChatOptions;
import io.nop.ai.toolkit.api.IToolManager;
import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;
import io.nop.ai.toolkit.model.AiToolModel;
import io.nop.api.core.util.ICancelToken;
import io.nop.ai.agent.engine.AgentEvent;
import io.nop.ai.agent.engine.AgentEventType;
import io.nop.ai.agent.engine.DefaultAgentEventPublisher;
import io.nop.ai.agent.engine.IAgentEventPublisher;
import io.nop.ai.agent.engine.IAgentEventSubscriber;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Focused unit tests for the MA4.2-05 extracted classes:
 * {@link AgentHookInvoker}, {@link LlmCallCoordinator},
 * {@link AgentLoopGuard}, {@link AgentCompactionCoordinator} and
 * {@link AgentToolPlanResolver}.
 */
public class TestEngineExtractedCoordinators {

    static class StubChatService implements IChatService {
        AtomicInteger calls = new AtomicInteger();
        RuntimeException error;

        @Override
        public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
            calls.incrementAndGet();
            if (error != null) {
                throw error;
            }
            return CompletableFuture.completedFuture(new ChatResponse());
        }

        @Override
        public Flow.Publisher<io.nop.ai.api.chat.stream.ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
            return subscriber -> subscriber.onComplete();
        }
    }

    static class StubToolManager implements IToolManager {
        final List<AiToolModel> tools = new ArrayList<>();
        AtomicInteger calls = new AtomicInteger();

        @Override
        public CompletableFuture<AiToolCallResult> callTool(String toolName, AiToolCall call, io.nop.ai.toolkit.api.IToolExecuteContext context) {
            calls.incrementAndGet();
            AiToolCallResult r = new AiToolCallResult();
            r.setStatus("success");
            return CompletableFuture.completedFuture(r);
        }

        @Override
        public CompletableFuture<io.nop.ai.toolkit.model.AiToolCallsResponse> callTools(
                io.nop.ai.toolkit.model.AiToolCalls calls, io.nop.ai.toolkit.api.IToolExecuteContext context) {
            return CompletableFuture.completedFuture(new io.nop.ai.toolkit.model.AiToolCallsResponse());
        }

        @Override
        public List<AiToolModel> listTools() {
            return tools;
        }

        @Override
        public AiToolModel loadTool(String toolName) {
            return tools.stream().filter(t -> toolName.equals(t.getName())).findFirst().orElse(null);
        }
    }

    static class RecordingHook implements IAgentLifecycleHook {
        final List<AgentLifecyclePoint> points = new ArrayList<>();
        final HookResult result;

        RecordingHook(HookResult result) {
            this.result = result;
        }

        @Override
        public HookResult onEvent(HookContext ctx) {
            points.add(ctx.getLifecyclePoint());
            return result;
        }
    }

    private static AgentHookInvoker invokerWith(IAgentEventPublisher publisher, IAgentLifecycleHook hook) {
        IHookRegistry registry = new DefaultHookRegistry();
        if (hook != null) {
            registry.register(AgentLifecyclePoint.PRE_REASONING, hook);
        }
        return new AgentHookInvoker(registry, publisher);
    }

    private static AgentExecutionContext ctx() {
        AgentExecutionContext ctx = new AgentExecutionContext(new io.nop.ai.agent.model.AgentModel());
        ctx.setStatus(AgentExecStatus.running);
        return ctx;
    }

    // ============ AgentHookInvoker ============

    @Test
    void invokeHooksPassesThroughAndPublishesEvent() {
        List<AgentEvent> events = new ArrayList<>();
        IAgentEventPublisher publisher = new DefaultAgentEventPublisher();
        publisher.addSubscriber(events::add);
        RecordingHook hook = new RecordingHook(HookResult.PassResult.instance());
        AgentHookInvoker invoker = invokerWith(publisher, hook);

        HookResult result = invoker.executeWithMiddleware(
                AgentLifecyclePoint.PRE_REASONING, ctx(), "agent", null, null);
        assertEquals(HookResult.PassResult.instance().getClass(), result.getClass());
        assertEquals(List.of(AgentLifecyclePoint.PRE_REASONING), hook.points);

        invoker.publishEvent(AgentEventType.EXECUTION_STARTED, "s1", "agent", Map.of("k", "v"));
        assertEquals(1, events.size());
        assertEquals(AgentEventType.EXECUTION_STARTED, events.get(0).getEventType());
        assertEquals("s1", events.get(0).getSessionId());

        invoker.publishErrorEvent(AgentEventType.EXECUTION_FAILED, "s1", "agent", "boom");
        assertEquals(2, events.size());
        assertNotNull(events.get(1).getError());
    }

    @Test
    void vetoResultPropagatesThroughMiddlewareChain() {
        RecordingHook veto = new RecordingHook(new HookResult.VetoResult("nope"));
        AgentHookInvoker invoker = invokerWith(null, veto);
        HookResult r = invoker.executeWithMiddleware(AgentLifecyclePoint.PRE_REASONING, ctx(), "a", null, null);
        assertTrue(r.isVeto());
        assertEquals("nope", invoker.vetoReason(r));
    }

    @Test
    void reenterResultOnlyValidAtReentrantPoints() {
        RecordingHook reenter = new RecordingHook(new HookResult.ReenterResult("again"));
        IHookRegistry registry = new DefaultHookRegistry();
        registry.register(AgentLifecyclePoint.PRE_REASONING, reenter);
        registry.register(AgentLifecyclePoint.BEFORE_TOOL_RESULT_PROCESSED, reenter);
        AgentHookInvoker invoker = new AgentHookInvoker(registry, null);
        assertThrows(NopAiAgentException.class, () ->
                invoker.executeWithMiddleware(AgentLifecyclePoint.PRE_REASONING, ctx(), "a", null, null));
        HookResult r = invoker.executeWithMiddleware(
                AgentLifecyclePoint.BEFORE_TOOL_RESULT_PROCESSED, ctx(), "a", "t", "1");
        assertTrue(r instanceof HookResult.ReenterResult);
    }

    // ============ LlmCallCoordinator ============

    @Test
    void buildModelKeyNormalizesNulls() {
        ChatOptions o = new ChatOptions();
        assertEquals(":", LlmCallCoordinator.buildModelKey(o));
        o.setProvider("openai");
        o.setModel("gpt-4o");
        assertEquals("openai:gpt-4o", LlmCallCoordinator.buildModelKey(o));
    }

    @Test
    void parseToolCallIdHandlesNumericAndNonNumeric() {
        assertEquals(7, LlmCallCoordinator.parseToolCallId("7"));
        assertEquals(0, LlmCallCoordinator.parseToolCallId("abc"));
        assertEquals(0, LlmCallCoordinator.parseToolCallId(null));
    }

    @Test
    void doLlmCallWithRetryStopsOnFatal() {
        StubChatService chat = new StubChatService();
        chat.error = new IllegalStateException("fatal");
        AgentHookInvoker invoker = invokerWith(null, null);
        LlmCallCoordinator coordinator = new LlmCallCoordinator(
                chat, io.nop.ai.agent.reliability.NoRetryPolicy.noRetry(),
                new ThresholdBreaker(), PassThroughModelRouter.passThrough(),
                0, null, invoker);
        AgentExecutionContext c = ctx();
        assertThrows(IllegalStateException.class, () ->
                coordinator.doLlmCallWithRetry(
                        new ChatRequest(new java.util.ArrayList<>()), c, "s1", "a", new ChatOptions()));
        assertEquals(1, chat.calls.get());
    }

    @Test
    void doLlmCallWithRetryRetriesOnTransient() {
        StubChatService chat = new StubChatService();
        AtomicInteger failCount = new AtomicInteger();
        chat.error = new RuntimeException("transient") {
            @Override
            public synchronized Throwable fillInStackTrace() {
                return this;
            }
        };
        // first two calls throw, third succeeds
        AgentHookInvoker invoker = invokerWith(null, null);
        IChatService retryChat = new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                if (failCount.getAndIncrement() < 2) {
                    throw new NopAiAgentException("transient");
                }
                return CompletableFuture.completedFuture(new ChatResponse());
            }

            @Override
            public Flow.Publisher<io.nop.ai.api.chat.stream.ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> subscriber.onComplete();
            }
        };
        LlmCallCoordinator coordinator = new LlmCallCoordinator(
                retryChat, new StandardRetryPolicy(), new ThresholdBreaker(), PassThroughModelRouter.passThrough(),
                0, null, invoker);
        AgentExecutionContext c = ctx();
        LlmCallCoordinator.LlmCallResult r = coordinator.doLlmCallWithRetry(
                new ChatRequest(new java.util.ArrayList<>()), c, "s1", "a", new ChatOptions());
        assertTrue(r.isSuccess());
        assertEquals(3, failCount.get());
    }

    // ============ AgentLoopGuard ============

    @Test
    void handleSessionPausedSetsStatusAndPublishes() {
        List<AgentEvent> events = new ArrayList<>();
        IAgentEventPublisher publisher = new DefaultAgentEventPublisher();
        publisher.addSubscriber(events::add);
        AgentHookInvoker invoker = invokerWith(publisher, null);
        AgentCompactionCoordinator compaction = new AgentCompactionCoordinator(
                new NoOpContextCompactor(), NoOpCheckpoint.noOp(), null,
                TokenEstimators.defaultEstimator(), invoker);
        AgentLoopGuard guard = new AgentLoopGuard(new DefaultDenialLedger(),
                TokenEstimators.defaultEstimator(), invoker, compaction);
        AgentExecutionContext c = ctx();
        guard.handleSessionPaused(c, "s1", "a");
        assertEquals(AgentExecStatus.paused, c.getStatus());
        assertEquals(1, events.stream().filter(e -> e.getEventType() == AgentEventType.SESSION_PAUSED).count());
    }

    @Test
    void handleGoalStuckSetsEscalated() {
        List<AgentEvent> events = new ArrayList<>();
        IAgentEventPublisher publisher = new DefaultAgentEventPublisher();
        publisher.addSubscriber(events::add);
        AgentHookInvoker invoker = invokerWith(publisher, null);
        AgentCompactionCoordinator compaction = new AgentCompactionCoordinator(
                new NoOpContextCompactor(), NoOpCheckpoint.noOp(), null,
                TokenEstimators.defaultEstimator(), invoker);
        AgentLoopGuard guard = new AgentLoopGuard(new DefaultDenialLedger(),
                TokenEstimators.defaultEstimator(), invoker, compaction);
        AgentExecutionContext c = ctx();
        guard.handleGoalStuck(c, "s1", "a");
        assertEquals(AgentExecStatus.escalated, c.getStatus());
        assertNotNull(c.getLastError());
    }

    @Test
    void shouldForceStopFiresOnOversizedContext() {
        AgentHookInvoker invoker = invokerWith(null, null);
        ITokenEstimator hugeEstimator = new ITokenEstimator() {
            @Override
            public long estimateTokens(List<io.nop.ai.api.chat.messages.ChatMessage> messages) {
                return ReActAgentExecutor.DEFAULT_MAX_CONTEXT_TOKENS + 1;
            }

            @Override
            public void record(List<io.nop.ai.api.chat.messages.ChatMessage> messagesSent, int actualPromptTokens) {
            }
        };
        AgentCompactionCoordinator compaction = new AgentCompactionCoordinator(
                new NoOpContextCompactor(), NoOpCheckpoint.noOp(), null, hugeEstimator, invoker);
        AgentLoopGuard guard = new AgentLoopGuard(new DefaultDenialLedger(), hugeEstimator, invoker, compaction);
        AgentExecutionContext c = ctx();
        c.addMessage(new io.nop.ai.api.chat.messages.ChatUserMessage("x"));
        assertTrue(guard.shouldForceStop(c));
    }

    @Test
    void handleForcedStopSetsForcedStoppedAndBestEffortCompacts() {
        List<AgentEvent> events = new ArrayList<>();
        IAgentEventPublisher publisher = new DefaultAgentEventPublisher();
        publisher.addSubscriber(events::add);
        AgentHookInvoker invoker = invokerWith(publisher, null);
        AgentCompactionCoordinator compaction = new AgentCompactionCoordinator(
                new NoOpContextCompactor(), NoOpCheckpoint.noOp(), null,
                TokenEstimators.defaultEstimator(), invoker);
        AgentLoopGuard guard = new AgentLoopGuard(new DefaultDenialLedger(),
                TokenEstimators.defaultEstimator(), invoker, compaction);
        AgentExecutionContext c = ctx();
        c.setTokensUsed(200000);
        c.addMessage(new io.nop.ai.api.chat.messages.ChatUserMessage("hi"));
        guard.handleForcedStop(c, "s1", "a", new int[]{0});
        assertEquals(AgentExecStatus.forced_stopped, c.getStatus());
        assertEquals(1, events.stream().filter(e -> e.getEventType() == AgentEventType.FORCED_STOP).count());
    }

    // ============ AgentCompactionCoordinator ============

    @Test
    void compactionTriggerThresholds() {
        AgentHookInvoker invoker = invokerWith(null, null);
        AgentCompactionCoordinator coordinator = new AgentCompactionCoordinator(
                new NoOpContextCompactor(), NoOpCheckpoint.noOp(), null,
                TokenEstimators.defaultEstimator(), invoker);
        AgentExecutionContext c = ctx();
        c.setTokensUsed((long) (ReActAgentExecutor.DEFAULT_MAX_CONTEXT_TOKENS * 0.8) + 1);
        assertTrue(coordinator.shouldTriggerCompaction(c));
        c.setTokensUsed(100);
        assertFalse(coordinator.shouldTriggerCompaction(c));
        for (int i = 0; i < ReActAgentExecutor.DEFAULT_TRIGGER_MAX_MESSAGES + 1; i++) {
            c.addMessage(new io.nop.ai.api.chat.messages.ChatUserMessage("m" + i));
        }
        assertTrue(coordinator.shouldTriggerCompaction(c));
    }

    @Test
    void resolveMaxContextTokensFallsBackToDefault() {
        AgentHookInvoker invoker = invokerWith(null, null);
        AgentCompactionCoordinator coordinator = new AgentCompactionCoordinator(
                new NoOpContextCompactor(), NoOpCheckpoint.noOp(), null,
                TokenEstimators.defaultEstimator(), invoker);
        AgentExecutionContext c = ctx();
        assertEquals(ReActAgentExecutor.DEFAULT_MAX_CONTEXT_TOKENS, coordinator.resolveMaxContextTokens(c));
        ChatOptions o = new ChatOptions();
        o.setMaxTokens(4096);
        c.setChatOptions(o);
        assertEquals(4096, coordinator.resolveMaxContextTokens(c));
    }

    // ============ AgentToolPlanResolver ============

    private AiToolModel makeTool(String name, String... tags) {
        AiToolModel tool = new AiToolModel();
        tool.setName(name);
        tool.setDescription("Tool: " + name);
        if (tags.length > 0) {
            tool.setTags(java.util.Set.of(tags));
        }
        return tool;
    }

    @Test
    void buildToolDefinitionsAppliesFilters() {
        StubToolManager tm = new StubToolManager();
        tm.tools.add(makeTool("read-file", "readonly"));
        tm.tools.add(makeTool("delete-file", "admin"));
        tm.tools.add(makeTool("meta-tool"));
        tm.tools.get(2).setMeta(true);
        tm.tools.add(makeTool("bash"));

        AgentToolPlanResolver resolver = new AgentToolPlanResolver(tm);
        io.nop.ai.agent.model.AgentModel model = new io.nop.ai.agent.model.AgentModel();
        model.setDenyTools(java.util.Set.of("bash"));
        model.setActiveTags(java.util.Set.of("readonly"));
        List<io.nop.ai.api.chat.messages.ChatToolDefinition> defs =
                resolver.buildToolDefinitions(model, null);
        List<String> names = defs.stream().map(io.nop.ai.api.chat.messages.ChatToolDefinition::getName).collect(java.util.stream.Collectors.toList());
        assertTrue(names.contains("read-file"));
        assertTrue(names.contains("meta-tool"));
        assertFalse(names.contains("bash"));
        assertFalse(names.contains("delete-file"));
    }

    @Test
    void computeEffectiveAllowedToolsClampsWithParentConstraint() {
        AgentToolPlanResolver resolver = new AgentToolPlanResolver(new StubToolManager());
        io.nop.ai.agent.model.AgentModel model = new io.nop.ai.agent.model.AgentModel();
        model.setTools(java.util.Set.of("a", "b", "c"));
        AgentExecutionContext c = ctx();
        assertEquals(java.util.Set.of("a", "b", "c"), resolver.computeEffectiveAllowedTools(model, c));
        io.nop.ai.agent.security.ParentPermissionConstraint parent =
                new io.nop.ai.agent.security.ParentPermissionConstraint(
                        java.util.Set.of("a", "b"), null, null);
        c.getMetadata().put(io.nop.ai.agent.security.ParentPermissionConstraint.METADATA_KEY, parent);
        assertEquals(java.util.Set.of("a", "b"), resolver.computeEffectiveAllowedTools(model, c));
    }

    @Test
    void resolveWorkDirUsesDeclaredPath() {
        AgentToolPlanResolver resolver = new AgentToolPlanResolver(new StubToolManager());
        io.nop.ai.agent.model.AgentModel model = new io.nop.ai.agent.model.AgentModel();
        assertNull(resolver.resolveWorkDir(model));
        model.setWorkDir("/tmp/w");
        assertEquals(new java.io.File("/tmp/w"), resolver.resolveWorkDir(model));
    }
}
