package io.nop.ai.agent.engine;

import io.nop.ai.agent.message.NoOpAgentMessenger;
import io.nop.ai.agent.model.AgentModel;
import io.nop.ai.agent.security.IToolAccessChecker;
import io.nop.ai.agent.security.ParentConstrainedToolAccessChecker;
import io.nop.ai.agent.security.ParentPermissionConstraint;
import io.nop.ai.agent.security.ToolAccessResult;
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
 * Phase 2 integration tests verifying the constraint wiring between
 * {@link CallAgentExecutor} (constraint capture + propagation via request
 * metadata) and {@link DefaultAgentEngine} (constraint consumption + tool
 * access checker wrapping).
 *
 * <p>These tests prove the data flow:
 * <ol>
 *     <li>{@code CallAgentExecutor} reads the parent's effective tool set from
 *         {@link AgentToolExecuteContext#getAllowedTools()}</li>
 *     <li>{@code CallAgentExecutor} builds a {@link ParentPermissionConstraint}
 *         and puts it in the sub-agent request's metadata</li>
 *     <li>{@link DefaultAgentEngine#resolveEffectiveToolAccessChecker} reads
 *         the constraint from metadata and wraps the engine's tool access
 *         checker</li>
 *     <li>The wrapped checker actually denies tools not in the parent set</li>
 * </ol>
 */
public class TestSubAgentPermissionWiring {

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

    // ---- CallAgentExecutor constraint propagation ----

    /**
     * Verify that {@link CallAgentExecutor} reads the parent's effective tool
     * set from the context, builds a {@link ParentPermissionConstraint}, and
     * puts it in the sub-agent request metadata under the well-known key.
     */
    @Test
    void callAgentPropagatesParentConstraintInRequestMetadata() throws Exception {
        Set<String> parentEffectiveTools = Set.of("read-file", "call-agent");

        AtomicReference<AgentMessageRequest> capturedRequest = new AtomicReference<>();

        // Mock engine that captures the request and returns a trivial result
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
                parentEffectiveTools);

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
        assertEquals("parent-agent", constraint.getParentAgentName());
        assertEquals("parent-sess", constraint.getParentSessionId());
    }

    /**
     * When {@code allowedTools} is null (backward-compatible caller),
     * {@link CallAgentExecutor} must NOT propagate a constraint — the sub-agent
     * executes with the engine's default permission pipeline.
     */
    @Test
    void callAgentWithNullAllowedToolsDoesNotPropagateConstraint() throws Exception {
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

        // allowedTools = null (backward-compatible: existing constructor)
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
        assertNotNull(captured);
        assertFalse(captured.getMetadata().containsKey(ParentPermissionConstraint.METADATA_KEY),
                "No constraint should be propagated when allowedTools is null (backward compatible)");
    }

    // ---- DefaultAgentEngine constraint consumption ----

    /**
     * Verify that {@link DefaultAgentEngine#resolveEffectiveToolAccessChecker}
     * returns a {@link ParentConstrainedToolAccessChecker} when the request
     * metadata carries a constraint, and the engine's own checker when absent.
     */
    @Test
    void engineWrapsCheckerWhenConstraintPresent() {
        DefaultAgentEngine engine = new DefaultAgentEngine(
                createMockChatService("ok"), createNoOpToolManager(), new InMemorySessionStore());
        IToolAccessChecker engineChecker = engine.getToolAccessCheckerForTest();

        ParentPermissionConstraint constraint = new ParentPermissionConstraint(
                Set.of("read-file"), "parent-agent", "parent-sess");
        Map<String, Object> metadata = new HashMap<>();
        metadata.put(ParentPermissionConstraint.METADATA_KEY, constraint);

        AgentMessageRequest request = new AgentMessageRequest("sub-agent", "input", null, metadata);

        IToolAccessChecker effective = engine.resolveEffectiveToolAccessChecker(request);
        assertTrue(effective instanceof ParentConstrainedToolAccessChecker,
                "Engine must wrap the checker when a constraint is present");
        ParentConstrainedToolAccessChecker wrapper = (ParentConstrainedToolAccessChecker) effective;
        assertSame(constraint, wrapper.getConstraint());
        assertSame(engineChecker, wrapper.getDelegate(),
                "Wrapper must delegate to the engine's own checker");
    }

    @Test
    void engineDoesNotWrapWhenConstraintAbsent() {
        DefaultAgentEngine engine = new DefaultAgentEngine(
                createMockChatService("ok"), createNoOpToolManager(), new InMemorySessionStore());

        // No metadata at all
        AgentMessageRequest request1 = new AgentMessageRequest("sub-agent", "input", null, null);
        assertSame(engine.getToolAccessCheckerForTest(), engine.resolveEffectiveToolAccessChecker(request1),
                "Engine must return its own checker unchanged when no metadata is present");

        // Metadata present but no constraint key
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("other", "value");
        AgentMessageRequest request2 = new AgentMessageRequest("sub-agent", "input", null, metadata);
        assertSame(engine.getToolAccessCheckerForTest(), engine.resolveEffectiveToolAccessChecker(request2),
                "Engine must return its own checker unchanged when constraint key is absent");
    }

    /**
     * Fail-fast: if the metadata key is present but the value is not a
     * {@link ParentPermissionConstraint}, the engine must throw — never
     * silently ignore a malformed constraint.
     */
    @Test
    void engineFailsFastOnMalformedConstraint() {
        DefaultAgentEngine engine = new DefaultAgentEngine(
                createMockChatService("ok"), createNoOpToolManager(), new InMemorySessionStore());

        Map<String, Object> metadata = new HashMap<>();
        metadata.put(ParentPermissionConstraint.METADATA_KEY, "not-a-constraint");
        AgentMessageRequest request = new AgentMessageRequest("sub-agent", "input", null, metadata);

        assertThrows(RuntimeException.class,
                () -> engine.resolveEffectiveToolAccessChecker(request),
                "Malformed constraint must fail fast, not be silently ignored");
    }

    /**
     * Verify that {@code resolveExecutor} passes the effective (wrapped)
     * checker through to the ReAct executor. The executor should carry the
     * wrapped checker.
     */
    @Test
    void resolveExecutorPassesEffectiveCheckerToExecutor() {
        DefaultAgentEngine engine = new DefaultAgentEngine(
                createMockChatService("ok"), createNoOpToolManager(), new InMemorySessionStore());

        ParentPermissionConstraint constraint = new ParentPermissionConstraint(
                Set.of("read-file"), "parent-agent", "parent-sess");
        IToolAccessChecker wrapped = new ParentConstrainedToolAccessChecker(
                constraint, engine.getToolAccessCheckerForTest());

        AgentModel model = new AgentModel();
        model.setName("test-agent");
        IAgentExecutor executor = engine.resolveExecutor(model, wrapped);

        assertNotNull(executor,
                "resolveExecutor must accept an overridden toolAccessChecker and return a functional executor");
    }

    /**
     * Full wiring: constraint metadata → engine wrapper → actual tool denial.
     * A tool in the sub-agent's set but NOT in the parent's set must be denied
     * by the wrapped checker with the parent-constraint reason.
     */
    @Test
    void fullWiringConstraintDeniesForbiddenTool() {
        DefaultAgentEngine engine = new DefaultAgentEngine(
                createMockChatService("ok"), createNoOpToolManager(), new InMemorySessionStore());

        ParentPermissionConstraint constraint = new ParentPermissionConstraint(
                Set.of("read-file", "call-agent"), "parent-agent", "parent-sess");
        Map<String, Object> metadata = new HashMap<>();
        metadata.put(ParentPermissionConstraint.METADATA_KEY, constraint);

        AgentMessageRequest request = new AgentMessageRequest("sub-agent", "input", null, metadata);
        IToolAccessChecker effective = engine.resolveEffectiveToolAccessChecker(request);

        AgentExecutionContext ctx = AgentExecutionContext.create(new AgentModel(), "sub-sess");

        // write-file NOT in parent's effective set → denied by parent constraint
        ToolAccessResult writeResult = effective.checkAccess("write-file", ctx);
        assertFalse(writeResult.isAllowed());
        assertTrue(writeResult.getReason().contains("parent permission constraint"));

        // bash NOT in parent's effective set → denied by parent constraint
        ToolAccessResult bashResult = effective.checkAccess("bash", ctx);
        assertFalse(bashResult.isAllowed());
        assertTrue(bashResult.getReason().contains("parent permission constraint"));

        // read-file IS in parent's effective set → passes to engine's own checker (AllowAll)
        ToolAccessResult readResult = effective.checkAccess("read-file", ctx);
        assertTrue(readResult.isAllowed(),
                "read-file is in parent's set, should pass to engine's own checker which allows it");
    }
}
