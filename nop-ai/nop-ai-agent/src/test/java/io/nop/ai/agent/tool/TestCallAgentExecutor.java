package io.nop.ai.agent.tool;

import io.nop.ai.agent.engine.AgentExecutionResult;
import io.nop.ai.agent.engine.AgentMessageAck;
import io.nop.ai.agent.engine.AgentMessageRequest;
import io.nop.ai.agent.engine.AgentToolExecuteContext;
import io.nop.ai.agent.engine.DefaultAgentEngine;
import io.nop.ai.agent.engine.IAgentEngine;
import io.nop.ai.agent.message.IAgentMessenger;
import io.nop.ai.agent.message.NoOpAgentMessenger;
import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.session.InMemorySessionStore;
import io.nop.ai.agent.session.AgentSession;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.messages.ChatUserMessage;
import io.nop.ai.api.chat.stream.ChatStreamChunk;
import io.nop.ai.toolkit.api.IToolExecuteContext;
import io.nop.ai.toolkit.api.IToolManager;
import io.nop.ai.toolkit.model.AiAgentCallResult;
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 2 unit tests for the functional {@link CallAgentExecutor}:
 * <ul>
 *     <li>Named agent invocation → sub-agent executes → real response</li>
 *     <li>Self invocation with inheritContext=false → new session</li>
 *     <li>Self invocation with inheritContext=true → fork from current session</li>
 *     <li>Session continuation via sessionId</li>
 *     <li>Unknown agentId → fail-fast error result</li>
 *     <li>Result carries sub-session ID</li>
 *     <li>Timeout → result reflects timeout</li>
 * </ul>
 */
public class TestCallAgentExecutor {

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
                model.setDescription("Mock tool: " + toolName);
                return model;
            }
        };
    }

    private DefaultAgentEngine createEngine(String llmResponse) {
        return new DefaultAgentEngine(
                createMockChatService(llmResponse),
                createNoOpToolManager(),
                new InMemorySessionStore());
    }

    private AgentToolExecuteContext createContext(IAgentEngine engine, String sessionId, String agentName) {
        return new AgentToolExecuteContext(
                new File("."), Collections.emptyMap(), 0L, null, null, null,
                engine, NoOpAgentMessenger.noOp(), sessionId, agentName);
    }

    private AiToolCall createCall(String agentId, String input, String sessionId, Boolean inheritContext) {
        AiToolCall call = new AiToolCall();
        call.setToolName("call-agent");
        call.setId(1);

        StringBuilder json = new StringBuilder("{");
        json.append("\"agentId\":\"").append(agentId).append("\"");
        if (input != null) {
            json.append(",\"input\":\"").append(input).append("\"");
        }
        if (sessionId != null) {
            json.append(",\"sessionId\":\"").append(sessionId).append("\"");
        }
        if (inheritContext != null) {
            json.append(",\"inheritContext\":").append(inheritContext);
        }
        json.append("}");

        call.setInput(json.toString());
        return call;
    }

    @Test
    void namedAgentInvocationReturnsRealSubAgentResponse() throws Exception {
        String subAgentResponse = "42";
        DefaultAgentEngine engine = createEngine(subAgentResponse);
        AgentToolExecuteContext ctx = createContext(engine, "parent-sess-1", "parent-agent");

        AiToolCall call = createCall("test-agent", "what is 6*7?", null, null);

        CallAgentExecutor executor = new CallAgentExecutor();
        AiToolCallResult result = executor.executeAsync(call, ctx).toCompletableFuture().get(30, TimeUnit.SECONDS);

        assertEquals("success", result.getStatus(),
                "Expected success status. Error: " + (result.getError() != null ? result.getError().getBody() : "none"));
        assertNotNull(result.getOutput());
        assertNotNull(result.getOutput().getBody());
        assertEquals(subAgentResponse, result.getOutput().getBody(),
                "Result must be the sub-agent's actual LLM response, not a hardcoded string");
        assertTrue(result instanceof AiAgentCallResult, "Result should be an AiAgentCallResult");
        assertNotNull(((AiAgentCallResult) result).getSessionId());
    }

    @Test
    void selfInvocationWithInheritContextFalseCreatesNewSession() throws Exception {
        InMemorySessionStore store = new InMemorySessionStore();
        AgentSession parent = store.getOrCreate("parent-sess-2", "test-agent");
        parent.appendMessages(List.of(new ChatUserMessage("parent history")));

        DefaultAgentEngine engine = new DefaultAgentEngine(
                createMockChatService("self-reply"), createNoOpToolManager(), store);
        AgentToolExecuteContext ctx = createContext(engine, "parent-sess-2", "test-agent");

        AiToolCall call = createCall("self", "do something", null, false);

        CallAgentExecutor executor = new CallAgentExecutor();
        AiToolCallResult result = executor.executeAsync(call, ctx).toCompletableFuture().get(30, TimeUnit.SECONDS);

        assertEquals("success", result.getStatus());
        String subSessionId = ((AiAgentCallResult) result).getSessionId();
        assertNotNull(subSessionId);
        assertFalse(subSessionId.equals("parent-sess-2"),
                "New session should be created, not the parent session");

        AgentSession subSession = store.get(subSessionId);
        assertNotNull(subSession, "Sub-session must exist in the store");
    }

    @Test
    void selfInvocationWithInheritContextTrueForksFromCurrentSession() throws Exception {
        InMemorySessionStore store = new InMemorySessionStore();
        AgentSession parent = store.getOrCreate("parent-sess-3", "test-agent");
        parent.appendMessages(List.of(
                new ChatUserMessage("inherited message 1"),
                new ChatUserMessage("inherited message 2")));

        DefaultAgentEngine engine = new DefaultAgentEngine(
                createMockChatService("forked-reply"), createNoOpToolManager(), store);
        AgentToolExecuteContext ctx = createContext(engine, "parent-sess-3", "test-agent");

        AiToolCall call = createCall("self", "continue work", null, true);

        CallAgentExecutor executor = new CallAgentExecutor();
        AiToolCallResult result = executor.executeAsync(call, ctx).toCompletableFuture().get(30, TimeUnit.SECONDS);

        assertEquals("success", result.getStatus());
        String childSessionId = ((AiAgentCallResult) result).getSessionId();
        assertNotNull(childSessionId);
        assertFalse(childSessionId.equals("parent-sess-3"),
                "Forked session should have a new ID, not the parent ID");

        AgentSession childSession = store.get(childSessionId);
        assertNotNull(childSession);
        assertEquals("parent-sess-3", childSession.getParentSessionId(),
                "Forked session should have the parent session ID set");
    }

    @Test
    void existingSessionContinuationAppendsToHistory() throws Exception {
        InMemorySessionStore store = new InMemorySessionStore();
        store.getOrCreate("existing-sess-1", "test-agent");

        DefaultAgentEngine engine = new DefaultAgentEngine(
                createMockChatService("continuation-reply"), createNoOpToolManager(), store);
        AgentToolExecuteContext ctx = createContext(engine, "parent-sess-4", "parent-agent");

        AiToolCall call = createCall("test-agent", "follow up", "existing-sess-1", null);

        CallAgentExecutor executor = new CallAgentExecutor();
        AiToolCallResult result = executor.executeAsync(call, ctx).toCompletableFuture().get(30, TimeUnit.SECONDS);

        assertEquals("success", result.getStatus());
        assertEquals("existing-sess-1", ((AiAgentCallResult) result).getSessionId(),
                "Result sessionId should match the provided existing session ID");

        AgentSession session = store.get("existing-sess-1");
        assertNotNull(session);
        assertTrue(session.getMessageCount() > 0,
                "Session should have messages appended after continuation");
    }

    @Test
    void unknownAgentIdReturnsErrorResult() throws Exception {
        DefaultAgentEngine engine = createEngine("should-not-reach");
        AgentToolExecuteContext ctx = createContext(engine, "parent-sess-5", "parent-agent");

        AiToolCall call = createCall("non-existent-agent-xyz", "hello", null, null);

        CallAgentExecutor executor = new CallAgentExecutor();
        AiToolCallResult result = executor.executeAsync(call, ctx).toCompletableFuture().get(30, TimeUnit.SECONDS);

        assertEquals("failure", result.getStatus(),
                "Unknown agentId should produce a failure result, not silent success");
        assertNotNull(result.getError());
        assertTrue(result.getError().getBody().contains("non-existent-agent-xyz")
                        || result.getError().getBody().contains("error")
                        || result.getError().getBody().contains("failed"),
                "Error message should describe the failure: " + result.getError().getBody());
    }

    @Test
    void resultCarriesSubSessionId() throws Exception {
        DefaultAgentEngine engine = createEngine("sub-agent response");
        AgentToolExecuteContext ctx = createContext(engine, "parent-sess-6", "parent-agent");

        AiToolCall call = createCall("test-agent", "hello", null, null);

        CallAgentExecutor executor = new CallAgentExecutor();
        AiToolCallResult result = executor.executeAsync(call, ctx).toCompletableFuture().get(30, TimeUnit.SECONDS);

        assertEquals("success", result.getStatus());
        assertTrue(result instanceof AiAgentCallResult, "Result should be an AiAgentCallResult");
        String subSessionId = ((AiAgentCallResult) result).getSessionId();
        assertNotNull(subSessionId);
        assertFalse(subSessionId.isEmpty());
    }

    @Test
    void missingAgentIdReturnsErrorResult() throws Exception {
        DefaultAgentEngine engine = createEngine("should-not-reach");
        AgentToolExecuteContext ctx = createContext(engine, "parent-sess-7", "parent-agent");

        AiToolCall call = new AiToolCall();
        call.setToolName("call-agent");
        call.setId(1);
        call.setInput("{}");

        CallAgentExecutor executor = new CallAgentExecutor();
        AiToolCallResult result = executor.executeAsync(call, ctx).toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertEquals("failure", result.getStatus());
        assertNotNull(result.getError());
        assertTrue(result.getError().getBody().contains("agentId is required"));
    }

    @Test
    void nullEngineReturnsErrorResult() throws Exception {
        AgentToolExecuteContext ctx = new AgentToolExecuteContext(
                new File("."), Collections.emptyMap(), 0L, null, null, null,
                null, NoOpAgentMessenger.noOp(), "sess", "agent");

        AiToolCall call = createCall("test-agent", "hello", null, null);

        CallAgentExecutor executor = new CallAgentExecutor();
        AiToolCallResult result = executor.executeAsync(call, ctx).toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertEquals("failure", result.getStatus());
        assertNotNull(result.getError());
        assertTrue(result.getError().getBody().contains("no engine"));
    }

    @Test
    void nonAgentContextReturnsErrorResult() throws Exception {
        io.nop.ai.agent.engine.SimpleToolExecuteContext simpleCtx =
                new io.nop.ai.agent.engine.SimpleToolExecuteContext(new File("."), null, null);

        AiToolCall call = createCall("test-agent", "hello", null, null);

        CallAgentExecutor executor = new CallAgentExecutor();
        AiToolCallResult result = executor.executeAsync(call, simpleCtx).toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertEquals("failure", result.getStatus());
        assertNotNull(result.getError());
        assertTrue(result.getError().getBody().contains("AgentToolExecuteContext"));
    }

    // ========================================================================
    // P2 path-injection guard (plan 191, finding [13-16]): defense-in-depth
    // agentId allow-list in CallAgentExecutor (non-"self" branch). A
    // traversal-shaped agentId sourced from LLM tool args must return a clean
    // LLM-facing error result BEFORE engine.execute is invoked, never throw
    // uncaught, and never reach the VFS path concatenation in loadAgentModel.
    // ========================================================================

    /**
     * Minimal recording engine: counts {@code execute} invocations and captures
     * the agentName seen, so tests can assert the engine was (or was not)
     * reached by the call-agent executor.
     */
    private static IAgentEngine recordingEngine(AtomicInteger executeCount, java.util.concurrent.atomic.AtomicReference<String> seenAgentName) {
        return new IAgentEngine() {
            @Override
            public AgentMessageAck sendMessage(AgentMessageRequest request) {
                throw new UnsupportedOperationException();
            }

            @Override
            public CompletableFuture<AgentExecutionResult> execute(AgentMessageRequest request) {
                executeCount.incrementAndGet();
                seenAgentName.set(request.getAgentName());
                return CompletableFuture.completedFuture(new AgentExecutionResult(
                        AgentExecStatus.completed, "mock-reply", Collections.emptyList(),
                        1, 0L, 0L, null, "mock-session"));
            }
        };
    }

    /**
     * (a) A {@code call-agent} tool call with a traversal-shaped
     * {@code agentId} (e.g. {@code "../../../etc/passwd"}) returns an
     * {@link AiToolCallResult} error result — does NOT throw, does NOT reach
     * {@code engine.execute}. The agentId allow-list in CallAgentExecutor
     * (non-"self" branch) rejects it with a clean LLM-facing error before the
     * engine is invoked (defense-in-depth, before the VFS path concatenation
     * in loadAgentModel).
     */
    @Test
    void traversalAgentIdReturnsErrorResultWithoutInvokingEngine() throws Exception {
        AtomicInteger executeCount = new AtomicInteger();
        java.util.concurrent.atomic.AtomicReference<String> seenAgentName =
                new java.util.concurrent.atomic.AtomicReference<>();
        IAgentEngine engine = recordingEngine(executeCount, seenAgentName);
        AgentToolExecuteContext ctx = createContext(engine, "parent-sess-trav", "parent-agent");

        AiToolCall call = createCall("../../../etc/passwd", "hello", null, null);

        CallAgentExecutor executor = new CallAgentExecutor();
        // Must NOT throw — the documented contract is fail-with-error-result.
        AiToolCallResult result = executor.executeAsync(call, ctx)
                .toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertEquals("failure", result.getStatus(),
                "A traversal-shaped agentId must yield a failure result (not silent success)");
        assertNotNull(result.getError());
        assertTrue(result.getError().getBody().contains("agentId")
                        && (result.getError().getBody().contains("path-injection guard")
                        || result.getError().getBody().contains("invalid characters")
                        || result.getError().getBody().contains("[A-Za-z0-9_-]")),
                "Error message must identify the agentId path-injection guard: "
                        + result.getError().getBody());
        assertEquals(0, executeCount.get(),
                "engine.execute must NOT be invoked when the agentId is rejected by the "
                        + "defense-in-depth allow-list (no VFS load path reached)");
        assertEquals(null, seenAgentName.get(),
                "No agentName should have been observed by the engine");
    }

    /**
     * (b) The {@code "self"} branch is unaffected by the new allow-list check:
     * it resolves to the parent agent's already-validated agentName and
     * proceeds to {@code engine.execute}. This proves the guard is scoped to
     * the non-"self" branch only and does not block legitimate self-invocation.
     */
    @Test
    void selfBranchUnaffectedByAgentIdAllowList() throws Exception {
        AtomicInteger executeCount = new AtomicInteger();
        java.util.concurrent.atomic.AtomicReference<String> seenAgentName =
                new java.util.concurrent.atomic.AtomicReference<>();
        IAgentEngine engine = recordingEngine(executeCount, seenAgentName);
        // parent agentName "parent-agent" is a valid allow-list name and is
        // what "self" resolves to.
        AgentToolExecuteContext ctx = createContext(engine, "parent-sess-self", "parent-agent");

        AiToolCall call = createCall("self", "do something", null, null);

        CallAgentExecutor executor = new CallAgentExecutor();
        AiToolCallResult result = executor.executeAsync(call, ctx)
                .toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertEquals("success", result.getStatus(),
                "The 'self' branch must not be blocked by the agentId allow-list "
                        + "(it resolves to the parent's already-validated agentName)");
        assertEquals(1, executeCount.get(),
                "engine.execute must be invoked exactly once for a valid 'self' call-agent");
        assertEquals("parent-agent", seenAgentName.get(),
                "'self' must resolve to the parent agent's agentName, not be rejected");
    }

    /**
     * (c) A valid allow-list {@code agentId} (e.g. {@code "child-agent"}) is
     * NOT over-blocked by the new check: it proceeds to
     * {@code engine.execute} normally. This proves the guard only rejects
     * traversal-shaped values, not legitimate agent names.
     */
    @Test
    void validAgentIdNotOverBlockedProceedsToEngine() throws Exception {
        AtomicInteger executeCount = new AtomicInteger();
        java.util.concurrent.atomic.AtomicReference<String> seenAgentName =
                new java.util.concurrent.atomic.AtomicReference<>();
        IAgentEngine engine = recordingEngine(executeCount, seenAgentName);
        AgentToolExecuteContext ctx = createContext(engine, "parent-sess-valid", "parent-agent");

        AiToolCall call = createCall("child-agent", "hello", null, null);

        CallAgentExecutor executor = new CallAgentExecutor();
        AiToolCallResult result = executor.executeAsync(call, ctx)
                .toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertEquals("success", result.getStatus(),
                "A valid allow-list agentId must not be over-blocked by the guard");
        assertEquals(1, executeCount.get(),
                "engine.execute must be invoked exactly once for a valid agentId");
        assertEquals("child-agent", seenAgentName.get(),
                "The valid agentId must be passed through to engine.execute unchanged");
    }
}
