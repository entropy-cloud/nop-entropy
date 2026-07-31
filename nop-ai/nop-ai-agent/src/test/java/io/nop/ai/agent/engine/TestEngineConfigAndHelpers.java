package io.nop.ai.agent.engine;

import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.model.AgentModel;
import io.nop.ai.agent.security.DefaultPermissionMatrix;
import io.nop.ai.agent.security.DefaultSecurityLevelResolver;
import io.nop.ai.agent.security.IPermissionMatrix;
import io.nop.ai.agent.security.ParentPermissionConstraint;
import io.nop.ai.agent.security.ISecurityLevelResolver;
import io.nop.ai.agent.memory.AiMemoryItem;
import io.nop.ai.agent.reliability.NoOpCheckpoint;
import io.nop.ai.agent.router.PassThroughModelRouter;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.toolkit.api.IToolManager;
import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;
import io.nop.ai.toolkit.model.AiToolModel;
import io.nop.api.core.util.ICancelToken;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Focused tests for the MA4.2-05 DefaultAgentEngine extraction classes:
 * {@link DefaultAgentEngineConfig}, {@link AgentStartupWarnings},
 * {@link AgentExecutorResolver}, {@link AgentTeamBinder},
 * {@link AgentCallDelegate} and {@link AgentSessionLifecycle}.
 */
public class TestEngineConfigAndHelpers {

    static class StubChatService implements IChatService {
        @Override
        public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
            return CompletableFuture.completedFuture(new ChatResponse());
        }

        @Override
        public Flow.Publisher<io.nop.ai.api.chat.stream.ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
            return subscriber -> subscriber.onComplete();
        }
    }

    static class StubToolManager implements IToolManager {
        final List<AiToolModel> tools = new ArrayList<>();
        final AtomicInteger calls = new AtomicInteger();

        @Override
        public CompletableFuture<AiToolCallResult> callTool(String toolName, AiToolCall call,
                                                            io.nop.ai.toolkit.api.IToolExecuteContext context) {
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

    // ============ DefaultAgentEngineConfig ============

    @Test
    void configDefaultsMatchOriginalFieldInitializers() {
        DefaultAgentEngineConfig config = new DefaultAgentEngineConfig();
        assertTrue(config.getPermissionMatrix() instanceof DefaultPermissionMatrix);
        assertTrue(config.getSecurityLevelResolver() instanceof DefaultSecurityLevelResolver);
        assertNotNull(config.getPermissionMatrix());
        assertEquals(1_800_000L, config.getLockLeaseMs());
        assertEquals(600_000L, config.getLockRenewIntervalMs());
        assertEquals(1024, config.getMemoryInjectionBudgetTokens());
        assertEquals(120_000L, config.getCallAgentTimeoutMs());
        assertEquals(120_000L, config.getLlmTimeoutMs());
        assertEquals(300_000L, config.getToolTimeoutMs());
    }

    @Test
    void configSetterRoundTrips() {
        DefaultAgentEngineConfig config = new DefaultAgentEngineConfig();
        IPermissionMatrix matrix = new DefaultPermissionMatrix();
        config.setPermissionMatrix(matrix);
        assertSame(matrix, config.getPermissionMatrix());
        config.setLockLeaseMs(42);
        assertEquals(42, config.getLockLeaseMs());
        config.setTalents(null);
        assertTrue(config.getTalents().isEmpty());
    }

    @Test
    void configRejectsNonPositiveCallAgentTimeout() {
        DefaultAgentEngineConfig config = new DefaultAgentEngineConfig();
        assertThrows(NopAiAgentException.class, () -> config.setCallAgentTimeoutMs(0));
    }

    // ============ AgentStartupWarnings ============

    @Test
    void startupWarningsEmitsForInsecureDefaults() {
        DefaultAgentEngineConfig config = new DefaultAgentEngineConfig();
        config.setAuditLogger(new io.nop.ai.agent.security.NoOpAuditLogger());
        AgentStartupWarnings warnings = new AgentStartupWarnings();
        // must not throw; log emission verified by engine-level tests
        warnings.warnIfInsecureDefaults(config);
        warnings.warnIfNoOpUsageRecorder(config);
        warnings.resetUsageRecorderNoOpWarned();
    }

    @Test
    void startupWarningsResetAllowsSecondWarn() {
        DefaultAgentEngineConfig config = new DefaultAgentEngineConfig();
        AgentStartupWarnings warnings = new AgentStartupWarnings();
        warnings.warnIfNoOpUsageRecorder(config);
        warnings.resetUsageRecorderNoOpWarned();
        warnings.warnIfNoOpUsageRecorder(config);
    }

    // ============ AgentExecutorResolver ============

    @Test
    void resolverWrapsToolCheckerWithParentConstraint() {
        DefaultAgentEngineConfig config = new DefaultAgentEngineConfig();
        DefaultAgentEngine engine = new DefaultAgentEngine(new StubChatService(), new StubToolManager());
        AgentCallDelegate callDelegate = new AgentCallDelegate(engine);
        AgentStartupWarnings warnings = new AgentStartupWarnings();
        AgentExecutorResolver resolver = new AgentExecutorResolver(
                config, new StubChatService(), new StubToolManager(),
                new io.nop.ai.agent.session.InMemorySessionStore(), new DefaultAgentEventPublisher(),
                engine, callDelegate, () -> null, warnings);

        config.setToolAccessChecker(new io.nop.ai.agent.security.DefaultToolAccessChecker());
        io.nop.ai.agent.engine.AgentMessageRequest req = new io.nop.ai.agent.engine.AgentMessageRequest(
                "agent", "hi", "s1", null);
        io.nop.ai.agent.security.IToolAccessChecker plain = resolver.resolveEffectiveToolAccessChecker(req);
        assertTrue(plain instanceof io.nop.ai.agent.security.DefaultToolAccessChecker);

        ParentPermissionConstraint constraint = new ParentPermissionConstraint(
                java.util.Set.of("tool-a"), null, null);
        req.getMetadata().put(ParentPermissionConstraint.METADATA_KEY, constraint);
        io.nop.ai.agent.security.IToolAccessChecker wrapped = resolver.resolveEffectiveToolAccessChecker(req);
        assertTrue(wrapped instanceof io.nop.ai.agent.security.ParentConstrainedToolAccessChecker);
    }

    @Test
    void resolverBuildsReActExecutorForReactMode() {
        DefaultAgentEngineConfig config = new DefaultAgentEngineConfig();
        StubToolManager tm = new StubToolManager();
        DefaultAgentEngine engine = new DefaultAgentEngine(new StubChatService(), tm);
        AgentExecutorResolver resolver = new AgentExecutorResolver(
                config, new StubChatService(), tm, new io.nop.ai.agent.session.InMemorySessionStore(),
                new DefaultAgentEventPublisher(), engine, new AgentCallDelegate(engine),
                () -> null, new AgentStartupWarnings());
        AgentModel model = new AgentModel();
        model.setName("test-react-agent");
        model.setMode("react");
        io.nop.ai.agent.engine.IAgentExecutor executor = resolver.resolveExecutor(model);
        assertNotNull(executor);
        assertTrue(executor instanceof ReActAgentExecutor);
    }

    // ============ AgentTeamBinder ============

    @Test
    void teamBinderPrecheckFailsFastWithoutFunctionalTeamManager() {
        DefaultAgentEngineConfig config = new DefaultAgentEngineConfig();
        AgentTeamBinder binder = new AgentTeamBinder(config);
        AgentModel model = new AgentModel();
        model.setTeam(new io.nop.ai.agent.model.TeamModel());
        assertThrows(NopAiAgentException.class, () -> binder.precheckTeamDeclarations(model));
    }

    @Test
    void teamBinderResolveActorIdFallsBackToSessionId() {
        DefaultAgentEngineConfig config = new DefaultAgentEngineConfig();
        AgentTeamBinder binder = new AgentTeamBinder(config);
        assertEquals("s1", binder.resolveActorId("s1"));
    }

    // ============ AgentCallDelegate ============

    @Test
    void callDelegateNoOpMessengerRegistersNothing() {
        DefaultAgentEngine engine = new DefaultAgentEngine(new StubChatService(), new StubToolManager());
        AgentCallDelegate delegate = new AgentCallDelegate(engine);
        delegate.setMessenger(null);
        assertTrue(delegate.getMessenger() instanceof io.nop.ai.agent.message.NoOpAgentMessenger);
    }

    @Test
    void callDelegateExtractFinalAssistantMessage() {
        io.nop.ai.agent.engine.AgentExecutionResult result = io.nop.ai.agent.engine.AgentExecutionResult.fromContext(
                new AgentExecutionContext(new AgentModel()));
        assertEquals("", AgentCallDelegate.extractFinalAssistantMessage(result));
        AgentExecutionContext ctx = new AgentExecutionContext(new AgentModel());
        ctx.setStatus(AgentExecStatus.completed);
        assertEquals("", AgentCallDelegate.extractFinalAssistantMessage(
                io.nop.ai.agent.engine.AgentExecutionResult.fromContext(ctx)));
        ctx.addMessage(new io.nop.ai.api.chat.messages.ChatUserMessage("u"));
        assertEquals("", AgentCallDelegate.extractFinalAssistantMessage(
                io.nop.ai.agent.engine.AgentExecutionResult.fromContext(ctx)));
        ctx.addMessage(new io.nop.ai.api.chat.messages.ChatAssistantMessage("final"));
        assertEquals("final", AgentCallDelegate.extractFinalAssistantMessage(
                io.nop.ai.agent.engine.AgentExecutionResult.fromContext(ctx)));
    }

    // ============ AgentSessionLifecycle ============

    @Test
    void lifecycleFormatMemorySectionRendersItems() {
        AiMemoryItem item = new AiMemoryItem();
        item.setContent("hello");
        String out = AgentSessionLifecycle.formatMemorySection(List.of(item));
        assertNotNull(out);
        assertTrue(out.contains("hello"));
    }

    @Test
    void lifecycleIsTerminalStatus() {
        assertTrue(AgentSessionLifecycle.isTerminalStatus(AgentExecStatus.completed));
        assertTrue(AgentSessionLifecycle.isTerminalStatus(AgentExecStatus.escalated));
        assertFalse(AgentSessionLifecycle.isTerminalStatus(AgentExecStatus.running));
    }

    @Test
    void lifecycleReleaseLockQuietlyToleratesNullLock() {
        DefaultAgentEngine engine = new DefaultAgentEngine(new StubChatService(), new StubToolManager());
        DefaultAgentEngineConfig config = new DefaultAgentEngineConfig();
        config.setSessionTakeoverLock(null);
        java.util.concurrent.ConcurrentHashMap<String, AgentSessionLifecycle.CancelHandle> running =
                new java.util.concurrent.ConcurrentHashMap<>();
        AgentCallDelegate callDelegate = new AgentCallDelegate(engine);
        AgentExecutorResolver resolver = new AgentExecutorResolver(
                config, new StubChatService(), new StubToolManager(), new io.nop.ai.agent.session.InMemorySessionStore(),
                new DefaultAgentEventPublisher(), engine, callDelegate, () -> null, new AgentStartupWarnings());
        SessionLockRenewal lockRenewal = new SessionLockRenewal(config, running);
        AgentSessionLifecycle lifecycle = new AgentSessionLifecycle(
                config, new io.nop.ai.agent.session.InMemorySessionStore(), new DefaultAgentEventPublisher(),
                "inst", running, () -> null, resolver,
                new AgentSessionSupport(callDelegate, new java.util.concurrent.ConcurrentHashMap<>(),
                        new java.util.concurrent.ConcurrentHashMap<>()),
                new AgentTeamBinder(config), lockRenewal, new AgentStartupWarnings(), engine);
        lifecycle.releaseLockQuietly("s1", "owner");
    }
}
