package io.nop.ai.agent.tool;

import io.nop.ai.agent.engine.AgentEvent;
import io.nop.ai.agent.engine.NopAiAgentException;
import io.nop.ai.agent.engine.AgentEventType;
import io.nop.ai.agent.engine.AgentExecutionResult;
import io.nop.ai.agent.engine.AgentMessageRequest;
import io.nop.ai.agent.engine.AgentToolExecuteContext;
import io.nop.ai.agent.engine.DefaultAgentEngine;
import io.nop.ai.agent.message.IAgentMessenger;
import io.nop.ai.agent.message.LocalAgentMessenger;
import io.nop.ai.agent.message.NoOpAgentMessenger;
import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.session.AgentSession;
import io.nop.ai.agent.session.InMemorySessionStore;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.messages.ChatMessage;
import io.nop.ai.api.chat.messages.ChatToolCall;
import io.nop.ai.api.chat.messages.ChatToolResponseMessage;
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
import io.nop.message.core.local.LocalMessageService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
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
 * Phase 2 end-to-end tests for plan 224 (call-agent async mailbox pathway):
 * <ul>
 *     <li><b>E2E</b>: parent Agent {@code engine.execute()} → ReAct loop →
 *         call-agent tool async pathway → messenger request → handler →
 *         sub-agent execute → RESPONSE → tool result → parent ReAct continues
 *         (Minimum Rules #22 Anti-Hollow)</li>
 *     <li>Three sub-session modes via async pathway (continue-existing /
 *         fork-from-parent / create-new)</li>
 *     <li>Handler exception → fast error RESPONSE (not silent timeout)</li>
 *     <li>Timeout behaviour consistent with fork+exec</li>
 *     <li>Fallback regression: NoOp messenger → fork+exec (zero regression)</li>
 * </ul>
 *
 * <p>All async tests wire a functional {@link LocalAgentMessenger} via
 * {@code engine.setMessenger}, which registers the engine-level call-agent
 * handler. The {@code AgentToolExecuteContext} used by direct tests carries
 * the same functional messenger so {@code CallAgentExecutor} takes the async
 * pathway.
 */
public class TestCallAgentAsyncEndToEnd {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    // ========================================================================
    // E2E: parent ReAct → call-agent async → handler → sub-agent → RESPONSE
    // ========================================================================

    /**
     * Full end-to-end: the parent agent's ReAct loop emits a call-agent tool
     * call; the executor takes the async pathway (functional messenger
     * configured); the engine-registered handler executes the sub-agent via
     * {@code engine.execute()}; the sub-agent's reply flows back as a RESPONSE
     * and is incorporated into the parent's tool-response message; the parent
     * then produces its final answer. This proves the complete async path is
     * wired and functional (Anti-Hollow #22).
     */
    @Test
    void asyncCallAgentFullEndToEndThroughReActLoop() throws Exception {
        String subAgentReply = "ASYNC_E2E_SUB_RESULT_123";
        // Shared chatService: n=0 parent emits tool call; n=1 sub-agent replies;
        // n=2 parent final answer.
        IChatService chatService = new IChatService() {
            final AtomicInteger n = new AtomicInteger();

            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                return CompletableFuture.completedFuture(build());
            }

            @Override
            public ChatResponse call(ChatRequest request, ICancelToken cancelToken) {
                return build();
            }

            private ChatResponse build() {
                int i = n.getAndIncrement();
                ChatAssistantMessage msg = new ChatAssistantMessage();
                if (i == 0) {
                    // Parent round 1: emit call-agent tool call.
                    msg.setContent("");
                    ChatToolCall toolCall = new ChatToolCall();
                    toolCall.setId("async-e2e-tool-1");
                    toolCall.setName("call-agent");
                    Map<String, Object> args = new HashMap<>();
                    args.put("agentId", "test-agent");
                    args.put("input", "compute the answer");
                    toolCall.setArguments(args);
                    msg.setToolCalls(List.of(toolCall));
                } else if (i == 1) {
                    // Sub-agent round 1: plain reply.
                    msg.setContent(subAgentReply);
                } else {
                    // Parent round 2: final answer referencing the sub result.
                    msg.setContent("Parent incorporated: " + subAgentReply);
                }
                return ChatResponse.success(msg);
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {
                };
            }
        };

        LocalMessageService platform = new LocalMessageService();
        LocalAgentMessenger messenger = new LocalAgentMessenger(platform);
        IToolManager toolManager = toolManagerWithCallAgent();
        DefaultAgentEngine engine = new DefaultAgentEngine(chatService, toolManager, new InMemorySessionStore());
        engine.setMessenger(messenger);
        assertTrue(engine.getMessenger() instanceof LocalAgentMessenger,
                "Engine must be wired with the functional messenger so CallAgentExecutor takes the async pathway");

        List<AgentEvent> events = Collections.synchronizedList(new ArrayList<>());
        engine.getEventPublisher().addSubscriber(events::add);

        AgentMessageRequest request = new AgentMessageRequest("test-parent-agent", "delegate to sub-agent");
        CompletableFuture<AgentExecutionResult> future = engine.execute(request);
        AgentExecutionResult result = future.get(60, TimeUnit.SECONDS);

        assertEquals(AgentExecStatus.completed, result.getStatus(),
                "Parent must complete. Messages: " + result.getMessages());

        // The sub-agent's reply must flow back as a tool-response message.
        boolean hasSubReply = result.getMessages().stream()
                .filter(m -> m instanceof ChatToolResponseMessage)
                .map(m -> (ChatToolResponseMessage) m)
                .anyMatch(m -> m.getContent() != null && m.getContent().contains(subAgentReply));
        assertTrue(hasSubReply,
                "The sub-agent's actual reply must flow back via the async RESPONSE. Messages: "
                        + result.getMessages());

        // Tool-call lifecycle events must be published (proves the call-agent tool
        // actually ran inside the ReAct loop, not a hollow skip).
        assertTrue(events.stream().anyMatch(e -> e.getEventType() == AgentEventType.TOOL_CALL_STARTED
                        && "call-agent".equals(e.getPayload().get("toolName"))),
                "TOOL_CALL_STARTED for call-agent must be published");
        assertTrue(events.stream().anyMatch(e -> e.getEventType() == AgentEventType.TOOL_CALL_COMPLETED
                        && "call-agent".equals(e.getPayload().get("toolName"))
                        && "success".equals(e.getPayload().get("status"))),
                "TOOL_CALL_COMPLETED for call-agent must be published with success status");
    }

    // ========================================================================
    // Three sub-session modes via async pathway
    // ========================================================================

    /**
     * (a) Continue-existing: a non-empty sessionId is passed; the async handler
     * executes the sub-agent on that existing session. The result session id
     * must match the provided session id.
     */
    @Test
    void asyncContinueExistingSessionMode() throws Exception {
        InMemorySessionStore store = new InMemorySessionStore();
        store.getOrCreate("async-cont-sess", "test-agent");

        DefaultAgentEngine engine = engineWithMessenger("cont-reply", store);
        AgentToolExecuteContext ctx = asyncContext(engine, "async-cont-parent", "parent-agent");

        AiToolCall call = call("test-agent", "follow up", "async-cont-sess", null);
        AiToolCallResult result = new CallAgentExecutor().executeAsync(call, ctx)
                .toCompletableFuture().get(30, TimeUnit.SECONDS);

        assertEquals("success", result.getStatus(),
                "Continue-existing async must succeed. Error: "
                        + (result.getError() != null ? result.getError().getBody() : "none"));
        assertEquals("async-cont-sess", ((AiAgentCallResult) result).getSessionId(),
                "Result session id must match the continued session id");
        assertEquals("cont-reply", ((AiAgentCallResult) result).getOutput().getBody(),
                "Result must carry the sub-agent's reply");
        AgentSession session = store.get("async-cont-sess");
        assertNotNull(session, "Continued session must still exist");
        assertTrue(session.getMessageCount() > 0, "Session must have appended messages");
    }

    /**
     * (b) Fork-from-parent: agentId="self" + inheritContext=true. The executor
     * first calls engine.forkSession() to obtain a child session id, then sends
     * the async REQUEST with that resolved id. The result must be a forked
     * session (new id, parent link set).
     */
    @Test
    void asyncForkFromParentSessionMode() throws Exception {
        InMemorySessionStore store = new InMemorySessionStore();
        AgentSession parent = store.getOrCreate("async-fork-parent", "test-agent");
        parent.appendMessages(List.of(new ChatUserMessage("parent history line")));

        DefaultAgentEngine engine = engineWithMessenger("fork-reply", store);
        AgentToolExecuteContext ctx = asyncContext(engine, "async-fork-parent", "test-agent");

        AiToolCall call = call("self", "continue inherited", null, true);
        AiToolCallResult result = new CallAgentExecutor().executeAsync(call, ctx)
                .toCompletableFuture().get(30, TimeUnit.SECONDS);

        assertEquals("success", result.getStatus(),
                "Fork-from-parent async must succeed. Error: "
                        + (result.getError() != null ? result.getError().getBody() : "none"));
        String childId = ((AiAgentCallResult) result).getSessionId();
        assertNotNull(childId);
        assertFalse(childId.equals("async-fork-parent"),
                "Forked session must have a new id, not the parent id");
        AgentSession child = store.get(childId);
        assertNotNull(child, "Forked child session must exist in the store");
        assertEquals("async-fork-parent", child.getParentSessionId(),
                "Forked session must record the parent session id");
    }

    /**
     * (c) Create-new: sessionId is null. The async handler's engine.execute
     * generates a fresh session id. The result must carry a non-empty new id.
     */
    @Test
    void asyncCreateNewSessionMode() throws Exception {
        InMemorySessionStore store = new InMemorySessionStore();
        DefaultAgentEngine engine = engineWithMessenger("new-reply", store);
        AgentToolExecuteContext ctx = asyncContext(engine, "async-new-parent", "parent-agent");

        AiToolCall call = call("test-agent", "fresh start", null, null);
        AiToolCallResult result = new CallAgentExecutor().executeAsync(call, ctx)
                .toCompletableFuture().get(30, TimeUnit.SECONDS);

        assertEquals("success", result.getStatus(),
                "Create-new async must succeed. Error: "
                        + (result.getError() != null ? result.getError().getBody() : "none"));
        String newId = ((AiAgentCallResult) result).getSessionId();
        assertNotNull(newId);
        assertFalse(newId.isEmpty());
        assertFalse(newId.equals("async-new-parent"),
                "New session must not reuse the parent session id");
        assertNotNull(store.get(newId), "New session must exist in the store");
    }

    // ========================================================================
    // Handler exception → fast error RESPONSE (not silent timeout)
    // ========================================================================

    /**
     * When the sub-agent execution fails (chatService throws), the handler must
     * catch the failure and return a {@code RESPONSE(status="failure")} so the
     * requester receives a fast error result — not a silent timeout. This test
     * asserts the result is a failure AND returns well before the timeoutMs
     * would have elapsed.
     */
    @Test
    void handlerFailureReturnsFastErrorResponseNotTimeout() throws Exception {
        IChatService throwingChat = new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                CompletableFuture<ChatResponse> f = new CompletableFuture<>();
                f.completeExceptionally(new RuntimeException("sub-agent exploded"));
                return f;
            }

            @Override
            public ChatResponse call(ChatRequest request, ICancelToken cancelToken) {
                throw new NopAiAgentException("sub-agent exploded");
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {
                };
            }
        };

        LocalMessageService platform = new LocalMessageService();
        LocalAgentMessenger messenger = new LocalAgentMessenger(platform);
        DefaultAgentEngine engine = new DefaultAgentEngine(
                throwingChat, noOpToolManager(), new InMemorySessionStore());
        engine.setMessenger(messenger);
        AgentToolExecuteContext ctx = asyncContext(engine, "fail-parent", "parent-agent");

        AiToolCall call = call("test-agent", "will fail", null, null);
        long start = System.currentTimeMillis();
        AiToolCallResult result = new CallAgentExecutor().executeAsync(call, ctx)
                .toCompletableFuture().get(30, TimeUnit.SECONDS);
        long elapsed = System.currentTimeMillis() - start;

        assertEquals("failure", result.getStatus(),
                "A failed sub-agent must yield a failure result, not success or a hang");
        assertNotNull(result.getError());
        assertTrue(result.getError().getBody().contains("failed") || result.getError().getBody().contains("error")
                        || result.getError().getBody().contains("exploded"),
                "Error message should describe the failure: " + result.getError().getBody());
        assertTrue(elapsed < 20_000,
                "Failure must be fast (handler returns error RESPONSE, not a timeout): elapsed=" + elapsed + "ms");
    }

    // ========================================================================
    // Timeout: async pathway REQUEST timeout → error result (fast)
    // ========================================================================

    /**
     * When the sub-agent takes longer than timeoutMs, the async pathway must
     * return a timeout error result in roughly timeoutMs (consistent with
     * fork+exec's orTimeout behaviour), not hang until the sub-agent completes.
     */
    @Test
    void asyncTimeoutReturnsErrorResultWithoutHanging() throws Exception {
        IChatService slowChat = new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                return CompletableFuture.supplyAsync(() -> {
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    ChatAssistantMessage msg = new ChatAssistantMessage();
                    msg.setContent("slow-reply");
                    return ChatResponse.success(msg);
                });
            }

            @Override
            public ChatResponse call(ChatRequest request, ICancelToken cancelToken) {
                // The ReAct loop may use the synchronous call() path; it must
                // also be slow so the handler's orTimeout fires.
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                ChatAssistantMessage msg = new ChatAssistantMessage();
                msg.setContent("slow-reply");
                return ChatResponse.success(msg);
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {
                };
            }
        };

        LocalMessageService platform = new LocalMessageService();
        LocalAgentMessenger messenger = new LocalAgentMessenger(platform);
        DefaultAgentEngine engine = new DefaultAgentEngine(slowChat, noOpToolManager(), new InMemorySessionStore());
        engine.setMessenger(messenger);
        AgentToolExecuteContext ctx = asyncContext(engine, "timeout-parent", "parent-agent");

        // timeoutMs=300ms — much shorter than the sub-agent's 5s sleep.
        // Set on the AiToolCall field (resolveTimeoutMs reads call.getTimeoutMs()).
        AiToolCall call = call("test-agent", "slow work", null, null);
        call.setTimeoutMs(300);

        long start = System.currentTimeMillis();
        AiToolCallResult result = new CallAgentExecutor().executeAsync(call, ctx)
                .toCompletableFuture().get(30, TimeUnit.SECONDS);
        long elapsed = System.currentTimeMillis() - start;

        assertEquals("failure", result.getStatus(),
                "A timed-out async request must yield a failure result, not success");
        assertNotNull(result.getError());
        assertTrue(result.getError().getBody().toLowerCase().contains("timeout")
                        || result.getError().getBody().toLowerCase().contains("timed out")
                        || result.getError().getBody().contains("failed"),
                "Error should indicate timeout/failure: " + result.getError().getBody());
        assertTrue(elapsed < 4000,
                "Timeout must fire around timeoutMs (300ms), not wait for the 5s sub-agent: elapsed=" + elapsed + "ms");
    }

    // ========================================================================
    // Fallback regression: NoOp messenger → fork+exec (zero regression)
    // ========================================================================

    /**
     * NoOp messenger (shipped default) → CallAgentExecutor takes the fork+exec
     * fallback. The observable result must be identical to the pre-plan-224
     * baseline: success + sub-agent reply. The existing TestCallAgentExecutor
     * suite (which uses NoOp) remains green — this test is an explicit
     * async-vs-sync shape assertion.
     */
    @Test
    void noOpMessengerFallbackIsForkExecZeroRegression() throws Exception {
        DefaultAgentEngine engine = new DefaultAgentEngine(
                mockChat("fallback-reply"), noOpToolManager(), new InMemorySessionStore());
        // Never call setMessenger → default NoOp → fork+exec pathway.
        assertTrue(engine.getMessenger() instanceof NoOpAgentMessenger);
        AgentToolExecuteContext ctx = asyncContext(engine, "fallback-parent", "parent-agent");

        AiToolCall call = call("test-agent", "hello", null, null);
        AiToolCallResult result = new CallAgentExecutor().executeAsync(call, ctx)
                .toCompletableFuture().get(30, TimeUnit.SECONDS);

        assertEquals("success", result.getStatus());
        assertEquals("fallback-reply", ((AiAgentCallResult) result).getOutput().getBody(),
                "Fork+exec fallback must return the sub-agent's actual reply (zero regression)");
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    private DefaultAgentEngine engineWithMessenger(String reply, InMemorySessionStore store) {
        LocalMessageService platform = new LocalMessageService();
        LocalAgentMessenger messenger = new LocalAgentMessenger(platform);
        DefaultAgentEngine engine = new DefaultAgentEngine(mockChat(reply), noOpToolManager(), store);
        engine.setMessenger(messenger);
        return engine;
    }

    private AgentToolExecuteContext asyncContext(DefaultAgentEngine engine, String sessionId, String agentName) {
        // The context carries the engine's functional messenger so CallAgentExecutor
        // takes the async pathway.
        return new AgentToolExecuteContext(
                new File("."), Collections.emptyMap(), 0L, null, null, null,
                engine, engine.getMessenger(), sessionId, agentName);
    }

    private AiToolCall call(String agentId, String input, String sessionId, Boolean inheritContext) {
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

    private IToolManager toolManagerWithCallAgent() {
        CallAgentExecutor callAgentExecutor = new CallAgentExecutor();
        return new IToolManager() {
            @Override
            public CompletableFuture<AiToolCallResult> callTool(String toolName, AiToolCall call, IToolExecuteContext context) {
                if ("call-agent".equals(toolName)) {
                    return callAgentExecutor.executeAsync(call, context).toCompletableFuture();
                }
                return CompletableFuture.completedFuture(AiToolCallResult.successResult(call.getId(), ""));
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

    private IChatService mockChat(String reply) {
        ChatAssistantMessage msg = new ChatAssistantMessage();
        msg.setContent(reply);
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

    private IToolManager noOpToolManager() {
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
}
