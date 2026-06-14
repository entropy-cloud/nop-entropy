package io.nop.ai.agent.engine;

import io.nop.ai.agent.message.NoOpAgentMessenger;
import io.nop.ai.agent.model.AgentModel;
import io.nop.ai.agent.security.DefaultPathAccessChecker;
import io.nop.ai.agent.security.IPathAccessChecker;
import io.nop.ai.agent.security.IToolAccessChecker;
import io.nop.ai.agent.security.ParentConstrainedPathAccessChecker;
import io.nop.ai.agent.security.ParentConstrainedToolAccessChecker;
import io.nop.ai.agent.security.ParentPermissionConstraint;
import io.nop.ai.agent.session.InMemorySessionStore;
import io.nop.ai.agent.tool.CallAgentExecutor;
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
import io.nop.api.core.util.ICancelToken;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 2 integration tests verifying the path-permission constraint wiring
 * between {@link CallAgentExecutor} (path-root capture + propagation via
 * request metadata) and {@link DefaultAgentEngine} (path-checker wrapping via
 * {@link DefaultAgentEngine#resolveEffectivePathAccessChecker}).
 *
 * <p>These tests prove the data flow for design §4.4 path-permission
 * inheritance:
 * <ol>
 *     <li>{@link AgentToolExecuteContext} carries the parent's effective path
 *         roots</li>
 *     <li>{@code CallAgentExecutor} reads the path roots and includes them in
 *         the propagated {@link ParentPermissionConstraint}</li>
 *     <li>{@link DefaultAgentEngine#resolveEffectivePathAccessChecker} reads
 *         the constraint from metadata and wraps the engine's path checker</li>
 *     <li>The wrapped path checker actually denies paths outside the parent's
 *         allowed roots</li>
 * </ol>
 */
public class TestSubAgentPathPermissionWiring {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    private IChatService createMockChatService(String responseContent) {
        ChatAssistantMessage msg = new ChatAssistantMessage();
        msg.setContent(responseContent);
        ChatResponse response = ChatResponse.success(msg);
        return new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                return CompletableFuture.completedFuture(response);
            }

            @Override
            public ChatResponse call(ChatRequest request, ICancelToken cancelToken) {
                return response;
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {
                };
            }
        };
    }

    private IToolManager createNoOpToolManager() {
        return new IToolManager() {
            @Override
            public CompletableFuture<AiToolCallResult> callTool(String toolName, AiToolCall call, IToolExecuteContext context) {
                return CompletableFuture.completedFuture(AiToolCallResult.successResult(0, ""));
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
                AiToolModel model = new AiToolModel();
                model.setName(toolName);
                return model;
            }
        };
    }

    // ---- AgentToolExecuteContext carries path roots ----

    @Test
    void contextCarriesAllowedPathRootsAsAdditiveField() {
        Set<String> pathRoots = Set.of("/workspace/project-a");
        AgentToolExecuteContext ctx = new AgentToolExecuteContext(
                new File("."), Collections.emptyMap(), 0L, null, null, null,
                null, NoOpAgentMessenger.noOp(), "sess", "agent",
                Set.of("read-file"), pathRoots);

        assertEquals(pathRoots, ctx.getAllowedPathRoots(),
                "Context must carry the effective path roots");
    }

    @Test
    void contextTwelveArgConstructorDefaultsPathRootsToAbsent() {
        // Existing 12-arg constructor (with allowedTools, no pathRoots) must
        // default path roots to null (ABSENT) — backward compatible.
        AgentToolExecuteContext ctx = new AgentToolExecuteContext(
                new File("."), Collections.emptyMap(), 0L, null, null, null,
                null, NoOpAgentMessenger.noOp(), "sess", "agent",
                Set.of("read-file"));

        assertEquals(null, ctx.getAllowedPathRoots(),
                "12-arg constructor must default allowedPathRoots to null (ABSENT)");
    }

    @Test
    void contextElevenArgConstructorDefaultsBothToAbsent() {
        AgentToolExecuteContext ctx = new AgentToolExecuteContext(
                new File("."), Collections.emptyMap(), 0L, null, null, null,
                null, NoOpAgentMessenger.noOp(), "sess", "agent");

        assertEquals(null, ctx.getAllowedTools());
        assertEquals(null, ctx.getAllowedPathRoots());
    }

    // ---- CallAgentExecutor propagates path roots ----

    @Test
    void callAgentPropagatesPathRootsInConstraint() throws Exception {
        Set<String> parentEffectiveTools = Set.of("read-file", "call-agent");
        Set<String> parentEffectiveRoots = Set.of("/workspace/project-a");

        AtomicReference<AgentMessageRequest> capturedRequest = new AtomicReference<>();

        IAgentEngine mockEngine = new IAgentEngine() {
            @Override
            public AgentMessageAck sendMessage(AgentMessageRequest request) {
                throw new UnsupportedOperationException();
            }

            @Override
            public CompletableFuture<AgentExecutionResult> execute(AgentMessageRequest request) {
                capturedRequest.set(request);
                AgentExecutionResult result = AgentExecutionResult.fromContext(
                        AgentExecutionContext.create(new AgentModel(), "sub-sess"));
                return CompletableFuture.completedFuture(result);
            }
        };

        AgentToolExecuteContext ctx = new AgentToolExecuteContext(
                new File("."), Collections.emptyMap(), 0L, null, null, null,
                mockEngine, NoOpAgentMessenger.noOp(), "parent-sess", "parent-agent",
                parentEffectiveTools, parentEffectiveRoots);

        AiToolCall call = new AiToolCall();
        call.setToolName("call-agent");
        call.setId(1);
        call.setInput("{\"agentId\":\"test-sub-agent\",\"input\":\"hello\"}");

        CallAgentExecutor executor = new CallAgentExecutor();
        executor.executeAsync(call, ctx).toCompletableFuture().get(10, TimeUnit.SECONDS);

        AgentMessageRequest captured = capturedRequest.get();
        assertNotNull(captured, "engine.execute should have been called");
        Object rawConstraint = captured.getMetadata().get(ParentPermissionConstraint.METADATA_KEY);
        assertNotNull(rawConstraint, "Request metadata must carry the parent permission constraint");
        assertTrue(rawConstraint instanceof ParentPermissionConstraint);
        ParentPermissionConstraint constraint = (ParentPermissionConstraint) rawConstraint;
        assertEquals(parentEffectiveTools, constraint.getAllowedTools());
        assertEquals(parentEffectiveRoots, constraint.getAllowedPathRoots(),
                "Constraint must carry the parent's effective path roots");
        assertEquals("parent-agent", constraint.getParentAgentName());
        assertEquals("parent-sess", constraint.getParentSessionId());
    }

    @Test
    void callAgentWithAbsentPathRootsDoesNotPropagateThem() throws Exception {
        // allowedTools present but allowedPathRoots ABSENT (null) → constraint
        // propagated with ABSENT path roots (backward compatible with plan 169).
        Set<String> parentEffectiveTools = Set.of("read-file", "call-agent");

        AtomicReference<AgentMessageRequest> capturedRequest = new AtomicReference<>();

        IAgentEngine mockEngine = new IAgentEngine() {
            @Override
            public AgentMessageAck sendMessage(AgentMessageRequest request) {
                throw new UnsupportedOperationException();
            }

            @Override
            public CompletableFuture<AgentExecutionResult> execute(AgentMessageRequest request) {
                capturedRequest.set(request);
                AgentExecutionResult result = AgentExecutionResult.fromContext(
                        AgentExecutionContext.create(new AgentModel(), "sub-sess"));
                return CompletableFuture.completedFuture(result);
            }
        };

        // 12-arg constructor: allowedTools present, path roots ABSENT
        AgentToolExecuteContext ctx = new AgentToolExecuteContext(
                new File("."), Collections.emptyMap(), 0L, null, null, null,
                mockEngine, NoOpAgentMessenger.noOp(), "parent-sess", "parent-agent",
                parentEffectiveTools);

        AiToolCall call = new AiToolCall();
        call.setToolName("call-agent");
        call.setId(1);
        call.setInput("{\"agentId\":\"test-sub-agent\",\"input\":\"hello\"}");

        CallAgentExecutor executor = new CallAgentExecutor();
        executor.executeAsync(call, ctx).toCompletableFuture().get(10, TimeUnit.SECONDS);

        AgentMessageRequest captured = capturedRequest.get();
        ParentPermissionConstraint constraint = (ParentPermissionConstraint)
                captured.getMetadata().get(ParentPermissionConstraint.METADATA_KEY);
        assertNotNull(constraint);
        assertEquals(null, constraint.getAllowedPathRoots(),
                "Path roots must be ABSENT when not provided (backward compatible)");
    }

    @Test
    void callAgentWithBothAbsentDoesNotPropagateConstraint() throws Exception {
        // Both allowedTools and allowedPathRoots ABSENT → no constraint at all
        AtomicReference<AgentMessageRequest> capturedRequest = new AtomicReference<>();

        IAgentEngine mockEngine = new IAgentEngine() {
            @Override
            public AgentMessageAck sendMessage(AgentMessageRequest request) {
                throw new UnsupportedOperationException();
            }

            @Override
            public CompletableFuture<AgentExecutionResult> execute(AgentMessageRequest request) {
                capturedRequest.set(request);
                AgentExecutionResult result = AgentExecutionResult.fromContext(
                        AgentExecutionContext.create(new AgentModel(), "sub-sess"));
                return CompletableFuture.completedFuture(result);
            }
        };

        // 11-arg constructor: both ABSENT
        AgentToolExecuteContext ctx = new AgentToolExecuteContext(
                new File("."), Collections.emptyMap(), 0L, null, null, null,
                mockEngine, NoOpAgentMessenger.noOp(), "parent-sess", "parent-agent");

        AiToolCall call = new AiToolCall();
        call.setToolName("call-agent");
        call.setId(1);
        call.setInput("{\"agentId\":\"test-sub-agent\",\"input\":\"hello\"}");

        CallAgentExecutor executor = new CallAgentExecutor();
        executor.executeAsync(call, ctx).toCompletableFuture().get(10, TimeUnit.SECONDS);

        AgentMessageRequest captured = capturedRequest.get();
        assertFalse(captured.getMetadata().containsKey(ParentPermissionConstraint.METADATA_KEY),
                "No constraint should be propagated when both tools and path roots are ABSENT");
    }

    // ---- DefaultAgentEngine path-checker resolution ----

    @Test
    void engineWrapsPathCheckerWhenPathRootsPresent() {
        DefaultAgentEngine engine = new DefaultAgentEngine(
                createMockChatService("ok"), createNoOpToolManager(), new InMemorySessionStore());
        IPathAccessChecker engineChecker = engine.getPathAccessCheckerForTest();

        ParentPermissionConstraint constraint = new ParentPermissionConstraint(
                Set.of("read-file"), Set.of("/workspace/project-a"),
                "parent-agent", "parent-sess");
        Map<String, Object> metadata = new HashMap<>();
        metadata.put(ParentPermissionConstraint.METADATA_KEY, constraint);

        AgentMessageRequest request = new AgentMessageRequest("sub-agent", "input", null, metadata);

        IPathAccessChecker effective = engine.resolveEffectivePathAccessChecker(request);
        assertTrue(effective instanceof ParentConstrainedPathAccessChecker,
                "Engine must wrap the path checker when path roots are PRESENT");
        ParentConstrainedPathAccessChecker wrapper = (ParentConstrainedPathAccessChecker) effective;
        assertSame(constraint, wrapper.getConstraint());
        assertSame(engineChecker, wrapper.getDelegate(),
                "Wrapper must delegate to the engine's own path checker");
    }

    @Test
    void engineDoesNotWrapPathCheckerWhenPathRootsAbsent() {
        DefaultAgentEngine engine = new DefaultAgentEngine(
                createMockChatService("ok"), createNoOpToolManager(), new InMemorySessionStore());

        // No metadata
        AgentMessageRequest request1 = new AgentMessageRequest("sub-agent", "input", null, null);
        assertSame(engine.getPathAccessCheckerForTest(), engine.resolveEffectivePathAccessChecker(request1),
                "No metadata → engine's own path checker");

        // Metadata with constraint but ABSENT path roots (tool-only constraint)
        ParentPermissionConstraint constraint = new ParentPermissionConstraint(
                Set.of("read-file"), "parent-agent", "parent-sess");
        Map<String, Object> metadata = new HashMap<>();
        metadata.put(ParentPermissionConstraint.METADATA_KEY, constraint);
        AgentMessageRequest request2 = new AgentMessageRequest("sub-agent", "input", null, metadata);
        assertSame(engine.getPathAccessCheckerForTest(), engine.resolveEffectivePathAccessChecker(request2),
                "Constraint with ABSENT path roots → engine's own path checker (no path confinement)");
    }

    @Test
    void engineFailsFastOnMalformedPathConstraint() {
        DefaultAgentEngine engine = new DefaultAgentEngine(
                createMockChatService("ok"), createNoOpToolManager(), new InMemorySessionStore());

        Map<String, Object> metadata = new HashMap<>();
        metadata.put(ParentPermissionConstraint.METADATA_KEY, "not-a-constraint");
        AgentMessageRequest request = new AgentMessageRequest("sub-agent", "input", null, metadata);

        assertThrows(RuntimeException.class,
                () -> engine.resolveEffectivePathAccessChecker(request),
                "Malformed constraint must fail fast, not be silently ignored");
    }

    @Test
    void resolveExecutorPassesEffectivePathChecker() {
        DefaultAgentEngine engine = new DefaultAgentEngine(
                createMockChatService("ok"), createNoOpToolManager(), new InMemorySessionStore());

        ParentPermissionConstraint constraint = new ParentPermissionConstraint(
                Set.of("read-file"), Set.of("/workspace/a"),
                "parent-agent", "parent-sess");
        IPathAccessChecker wrapped = new ParentConstrainedPathAccessChecker(
                constraint, engine.getPathAccessCheckerForTest());

        AgentModel model = new AgentModel();
        model.setName("test-agent");
        IAgentExecutor executor = engine.resolveExecutor(model, engine.getToolAccessCheckerForTest(), wrapped);
        assertNotNull(executor,
                "resolveExecutor must accept an overridden pathAccessChecker and return a functional executor");
    }

    @Test
    void fullWiringPathConstraintDeniesOutOfScopePath() {
        DefaultAgentEngine engine = new DefaultAgentEngine(
                createMockChatService("ok"), createNoOpToolManager(), new InMemorySessionStore());

        ParentPermissionConstraint constraint = new ParentPermissionConstraint(
                Set.of("read-file"), Set.of("/workspace/project-a"),
                "parent-agent", "parent-sess");
        Map<String, Object> metadata = new HashMap<>();
        metadata.put(ParentPermissionConstraint.METADATA_KEY, constraint);

        AgentMessageRequest request = new AgentMessageRequest("sub-agent", "input", null, metadata);
        IPathAccessChecker effective = engine.resolveEffectivePathAccessChecker(request);

        AgentExecutionContext ctx = AgentExecutionContext.create(new AgentModel(), "sub-sess");

        // Path outside parent's workDir → denied by parent path constraint
        io.nop.ai.agent.security.PathAccessResult outsideResult =
                effective.checkAccess("/workspace/project-b/secret", ctx);
        assertFalse(outsideResult.isAllowed());
        assertTrue(outsideResult.getReason().contains("parent path permission constraint"),
                "Denial reason must identify parent path permission constraint. Got: " + outsideResult.getReason());
        assertEquals(ParentConstrainedPathAccessChecker.MATCHED_RULE, outsideResult.getMatchedRule());

        // Path inside parent's workDir → passes constraint, then global deny-list applies
        io.nop.ai.agent.security.PathAccessResult insideResult =
                effective.checkAccess("/workspace/project-a/src/Main.java", ctx);
        assertTrue(insideResult.isAllowed(),
                "Path inside parent's workDir should pass constraint then the global checker (AllowAll by default)");
    }

    @Test
    void backwardCompatibilityPlan169ToolCheckerUnchanged() {
        // Plan 169 tool-checker wiring must still work when path roots are ABSENT
        DefaultAgentEngine engine = new DefaultAgentEngine(
                createMockChatService("ok"), createNoOpToolManager(), new InMemorySessionStore());

        ParentPermissionConstraint constraint = new ParentPermissionConstraint(
                Set.of("read-file"), "parent-agent", "parent-sess");
        Map<String, Object> metadata = new HashMap<>();
        metadata.put(ParentPermissionConstraint.METADATA_KEY, constraint);

        AgentMessageRequest request = new AgentMessageRequest("sub-agent", "input", null, metadata);

        IToolAccessChecker toolChecker = engine.resolveEffectiveToolAccessChecker(request);
        assertTrue(toolChecker instanceof ParentConstrainedToolAccessChecker,
                "Plan 169 tool-checker wrapping must still work");

        IPathAccessChecker pathChecker = engine.resolveEffectivePathAccessChecker(request);
        assertSame(engine.getPathAccessCheckerForTest(), pathChecker,
                "Path checker must NOT be wrapped when path roots are ABSENT (backward compatible)");
    }
}
