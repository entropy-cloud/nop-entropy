package io.nop.ai.agent.tool;

import io.nop.ai.agent.engine.AgentExecutionResult;
import io.nop.ai.agent.engine.AgentMessageAck;
import io.nop.ai.agent.engine.AgentMessageRequest;
import io.nop.ai.agent.engine.AgentToolExecuteContext;
import io.nop.ai.agent.engine.DefaultAgentEngine;
import io.nop.ai.agent.engine.IAgentEngine;
import io.nop.ai.agent.message.AgentMessageEnvelope;
import io.nop.ai.agent.message.AgentMessageKind;
import io.nop.ai.agent.message.AgentMessageTopics;
import io.nop.ai.agent.message.CallAgentRequestPayload;
import io.nop.ai.agent.message.CallAgentResponsePayload;
import io.nop.ai.agent.message.IAgentMessenger;
import io.nop.ai.agent.message.LocalAgentMessenger;
import io.nop.ai.agent.message.NoOpAgentMessenger;
import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.session.InMemorySessionStore;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
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
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 1 tests for plan 224 (call-agent async mailbox pathway):
 * <ul>
 *     <li>REQUEST/RESPONSE payload data-object immutability + contract</li>
 *     <li>{@link AgentMessageTopics#callAgentTopic()} helper + single-source
 *         literal</li>
 *     <li>{@code DefaultAgentEngine.setMessenger} idempotent handler
 *         registration (functional registers, NoOp does not, double-set does
 *         not break, NoOp cancels)</li>
 *     <li>Handler try/catch returns failure RESPONSE (does not propagate)</li>
 *     <li><b>Wiring verification</b>: {@code CallAgentExecutor.executeAsync} +
 *         functional messenger + engine-registered handler → handler triggered
 *         + RESPONSE returns (complete CallAgentExecutor→messenger→handler
 *         chain)</li>
 * </ul>
 */
public class TestCallAgentAsyncMailbox {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    // ========================================================================
    // Payload immutability + contract
    // ========================================================================

    @Test
    void requestPayloadIsImmutableWithDefensiveMapCopy() {
        Map<String, Object> mutable = new HashMap<>();
        mutable.put("k", "v");
        CallAgentRequestPayload payload = new CallAgentRequestPayload(
                "child-agent", "hello", "sess-1", mutable, 5000L);

        assertEquals("child-agent", payload.getTargetAgentId());
        assertEquals("hello", payload.getInput());
        assertEquals("sess-1", payload.getResolvedSessionId());
        assertEquals(5000L, payload.getTimeoutMs());
        assertEquals("v", payload.getParentConstraintMetadata().get("k"));

        // Mutate the original map after construction — payload must be unaffected.
        mutable.put("k", "CHANGED");
        mutable.put("extra", "leaked");
        assertEquals("v", payload.getParentConstraintMetadata().get("k"),
                "Payload must defensively copy the metadata map");
        assertFalse(payload.getParentConstraintMetadata().containsKey("extra"),
                "Mutations to the source map must not leak into the payload");
    }

    @Test
    void requestPayloadNullInputNormalizesToEmpty() {
        CallAgentRequestPayload payload = new CallAgentRequestPayload(
                "a", null, null, null, 1L);
        assertEquals("", payload.getInput(), "null input must normalize to empty string");
        assertNull(payload.getResolvedSessionId(), "resolvedSessionId may be null (create-new mode)");
        assertNull(payload.getParentConstraintMetadata(), "null metadata map stays null");
    }

    @Test
    void requestPayloadRejectsBlankTargetAgentId() {
        assertThrows(IllegalArgumentException.class,
                () -> new CallAgentRequestPayload(null, "x", null, null, 1L));
        assertThrows(IllegalArgumentException.class,
                () -> new CallAgentRequestPayload("", "x", null, null, 1L));
    }

    @Test
    void responsePayloadContract() {
        CallAgentResponsePayload ok = new CallAgentResponsePayload(
                "success", "sess-2", "done", null);
        assertEquals("success", ok.getStatus());
        assertEquals("sess-2", ok.getSessionId());
        assertEquals("done", ok.getFinalMessage());
        assertNull(ok.getError(), "error must be null for success");

        CallAgentResponsePayload fail = new CallAgentResponsePayload(
                "failure", null, null, "boom");
        assertEquals("failure", fail.getStatus());
        assertEquals("", fail.getFinalMessage(), "null finalMessage must normalize to empty");
        assertEquals("boom", fail.getError());
    }

    @Test
    void responsePayloadRejectsBlankStatus() {
        assertThrows(IllegalArgumentException.class,
                () -> new CallAgentResponsePayload(null, null, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> new CallAgentResponsePayload("", null, null, null));
    }

    // ========================================================================
    // AgentMessageTopics.callAgentTopic() helper
    // ========================================================================

    @Test
    void callAgentTopicReturnsExpectedNameAndMatchesConstant() {
        assertEquals("agent.call-agent", AgentMessageTopics.callAgentTopic());
        assertEquals(AgentMessageTopics.CALL_AGENT_TOPIC, AgentMessageTopics.callAgentTopic());
    }

    // ========================================================================
    // Handler registration lifecycle (idempotent, NoOpt-out)
    // ========================================================================

    /**
     * Functional messenger → handler registered and responds on the call-agent
     * topic. This proves the handler is wired to the messenger at
     * setMessenger time (Wiring Verification, Minimum Rules #23).
     */
    @Test
    void functionalMessengerRegistersRespondingCallAgentHandler() throws Exception {
        LocalMessageService platform = new LocalMessageService();
        LocalAgentMessenger messenger = new LocalAgentMessenger(platform);
        DefaultAgentEngine engine = newEngineWithMockChat("handler-ok-reply");

        engine.setMessenger(messenger);

        CallAgentRequestPayload req = new CallAgentRequestPayload(
                "test-agent", "ping", null, null, 10_000L);
        Object response = sendCallAgentRequest(messenger, req, "caller-1", Duration.ofSeconds(15));

        assertNotNull(response, "call-agent handler must respond (wiring connected)");
        assertTrue(response instanceof CallAgentResponsePayload,
                "Response must be a CallAgentResponsePayload, got: " + response.getClass());
        CallAgentResponsePayload resp = (CallAgentResponsePayload) response;
        assertEquals("success", resp.getStatus(), "error: " + resp.getError());
        assertEquals("handler-ok-reply", resp.getFinalMessage(),
                "Handler must have executed the sub-agent and extracted its assistant reply");
    }

    /**
     * Double setMessenger with the same functional messenger must not
     * double-register: the previous subscription is cancelled and a fresh one
     * registered, so the topic still has exactly one working handler.
     */
    @Test
    void repeatedSetMessengerIsIdempotentNoDoubleRegister() throws Exception {
        LocalMessageService platform = new LocalMessageService();
        LocalAgentMessenger messenger = new LocalAgentMessenger(platform);
        DefaultAgentEngine engine = newEngineWithMockChat("idempotent-reply");

        engine.setMessenger(messenger);
        engine.setMessenger(messenger);

        CallAgentRequestPayload req = new CallAgentRequestPayload(
                "test-agent", "again", null, null, 10_000L);
        Object response = sendCallAgentRequest(messenger, req, "caller-2", Duration.ofSeconds(15));
        assertTrue(response instanceof CallAgentResponsePayload);
        assertEquals("success", ((CallAgentResponsePayload) response).getStatus(),
                "After double setMessenger the handler must still respond exactly once");
    }

    /**
     * Switching the engine messenger to NoOp must cancel the call-agent
     * subscription: a subsequent REQUEST to the topic must time out (no
     * handler responds), proving the subscription was actually cancelled
     * (not silently left dangling).
     */
    @Test
    void settingNoOpMessengerCancelsCallAgentHandler() {
        LocalMessageService platform = new LocalMessageService();
        LocalAgentMessenger messenger = new LocalAgentMessenger(platform);
        DefaultAgentEngine engine = newEngineWithMockChat("should-not-reach");

        engine.setMessenger(messenger);
        // Switch to NoOp → must cancel the existing subscription.
        engine.setMessenger(NoOpAgentMessenger.noOp());

        CallAgentRequestPayload req = new CallAgentRequestPayload(
                "test-agent", "post-cancel", null, null, 10_000L);
        // The request must time out: the handler subscription was cancelled,
        // so nobody is listening on the call-agent topic.
        ExecutionException ex = assertThrows(ExecutionException.class,
                () -> sendCallAgentRequest(messenger, req, "caller-3", Duration.ofMillis(500)));
        assertNotNull(ex.getCause());
    }

    /**
     * NoOp messenger (the shipped default) must NOT register a call-agent
     * handler: the engine constructed without setMessenger leaves
     * callAgentSubscription null and a REQUEST to the topic times out.
     */
    @Test
    void noOpMessengerDefaultDoesNotRegisterHandler() {
        LocalMessageService platform = new LocalMessageService();
        LocalAgentMessenger messenger = new LocalAgentMessenger(platform);
        DefaultAgentEngine engine = newEngineWithMockChat("should-not-reach");
        // Never call setMessenger → default NoOp → no handler registered.
        assertTrue(engine.getMessenger() instanceof NoOpAgentMessenger);

        CallAgentRequestPayload req = new CallAgentRequestPayload(
                "test-agent", "no-handler", null, null, 10_000L);
        assertThrows(ExecutionException.class,
                () -> sendCallAgentRequest(messenger, req, "caller-4", Duration.ofMillis(500)));
    }

    // ========================================================================
    // Handler exception → failure RESPONSE (does not propagate)
    // ========================================================================

    /**
     * When the sub-agent execution fails inside the handler, the handler's
     * try/catch must return a {@code RESPONSE(status="failure")} rather than
     * throwing. If the exception propagated, {@code LocalAgentMessenger} would
     * swallow it and the requester would hang until its own timeout. This test
     * asserts the requester receives a fast failure RESPONSE (not a timeout).
     */
    @Test
    void handlerReturnsFailureResponseWhenSubAgentFails() throws Exception {
        LocalMessageService platform = new LocalMessageService();
        LocalAgentMessenger messenger = new LocalAgentMessenger(platform);
        // Engine whose sub-agent execute throws synchronously.
        IAgentEngine failingEngine = new IAgentEngine() {
            @Override
            public AgentMessageAck sendMessage(AgentMessageRequest request) {
                throw new UnsupportedOperationException();
            }

            @Override
            public CompletableFuture<AgentExecutionResult> execute(AgentMessageRequest request) {
                CompletableFuture<AgentExecutionResult> f = new CompletableFuture<>();
                f.completeExceptionally(new RuntimeException("sub-agent boom"));
                return f;
            }
        };
        DefaultAgentEngine engine = wrapAsDefaultEngine(failingEngine);
        engine.setMessenger(messenger);

        CallAgentRequestPayload req = new CallAgentRequestPayload(
                "test-agent", "will-fail", null, null, 10_000L);
        long start = System.currentTimeMillis();
        Object response = sendCallAgentRequest(messenger, req, "caller-fail", Duration.ofSeconds(15));
        long elapsed = System.currentTimeMillis() - start;

        assertTrue(response instanceof CallAgentResponsePayload,
                "Handler must return a failure RESPONSE, not throw");
        CallAgentResponsePayload resp = (CallAgentResponsePayload) response;
        assertEquals("failure", resp.getStatus(),
                "A failed sub-agent must yield status=failure, not success or a hang");
        assertNotNull(resp.getError(), "Failure RESPONSE must carry an error message");
        assertTrue(resp.getError().contains("boom"),
                "Error message should describe the failure: " + resp.getError());
        assertTrue(elapsed < 15_000,
                "Failure RESPONSE must be fast (not a timeout): elapsed=" + elapsed + "ms");
    }

    // ========================================================================
    // Wiring verification: CallAgentExecutor.executeAsync → messenger → handler
    // ========================================================================

    /**
     * Integration test: {@code CallAgentExecutor.executeAsync} with a functional
     * messenger takes the async pathway, sends a REQUEST to the call-agent
     * topic, the engine-registered handler executes the sub-agent, and the
     * RESPONSE flows back as the tool result. This proves the complete
     * CallAgentExecutor → messenger → handler chain is connected (Anti-Hollow
     * check: the handler is actually invoked at runtime, not just registered).
     *
     * <p>The result carries the sub-agent's actual LLM output (not a hardcoded
     * string), which can only happen if the handler executed the sub-agent via
     * {@code engine.execute()} and extracted the assistant message.
     */
    @Test
    void callAgentExecutorAsyncPathwayInvokesEngineHandler() throws Exception {
        String subAgentReply = "ASYNC_SUB_AGENT_OUTPUT_777";
        LocalMessageService platform = new LocalMessageService();
        LocalAgentMessenger messenger = new LocalAgentMessenger(platform);
        DefaultAgentEngine engine = newEngineWithMockChat(subAgentReply);
        engine.setMessenger(messenger);

        AgentToolExecuteContext ctx = new AgentToolExecuteContext(
                new File("."), Collections.emptyMap(), 0L, null, null, null,
                engine, messenger, "parent-async-sess", "parent-agent");

        AiToolCall call = new AiToolCall();
        call.setToolName("call-agent");
        call.setId(99);
        call.setInput("{\"agentId\":\"test-agent\",\"input\":\"compute answer\"}");

        CallAgentExecutor executor = new CallAgentExecutor();
        AiToolCallResult result = executor.executeAsync(call, ctx)
                .toCompletableFuture().get(30, TimeUnit.SECONDS);

        assertEquals("success", result.getStatus(),
                "Async pathway must succeed. Error: "
                        + (result.getError() != null ? result.getError().getBody() : "none"));
        assertTrue(result instanceof AiAgentCallResult,
                "Async result must be an AiAgentCallResult (same shape as fork+exec)");
        AiAgentCallResult agentResult = (AiAgentCallResult) result;
        assertNotNull(agentResult.getSessionId(), "Async result must carry a sub-session id");
        assertEquals(subAgentReply, agentResult.getOutput().getBody(),
                "Async result must carry the sub-agent's actual LLM output — proving the "
                        + "handler executed the sub-agent and the RESPONSE flowed back");
    }

    /**
     * NoOp messenger → CallAgentExecutor takes the fork+exec fallback (zero
     * regression). The result shape is identical to the pre-plan-224 baseline.
     */
    @Test
    void callAgentExecutorNoOpMessengerUsesForkExecFallback() throws Exception {
        String subAgentReply = "FORK_EXEC_REPLY";
        DefaultAgentEngine engine = newEngineWithMockChat(subAgentReply);
        // Default messenger is NoOp (never call setMessenger) → fork+exec.

        AgentToolExecuteContext ctx = new AgentToolExecuteContext(
                new File("."), Collections.emptyMap(), 0L, null, null, null,
                engine, NoOpAgentMessenger.noOp(), "parent-sync-sess", "parent-agent");

        AiToolCall call = new AiToolCall();
        call.setToolName("call-agent");
        call.setId(100);
        call.setInput("{\"agentId\":\"test-agent\",\"input\":\"hello\"}");

        CallAgentExecutor executor = new CallAgentExecutor();
        AiToolCallResult result = executor.executeAsync(call, ctx)
                .toCompletableFuture().get(30, TimeUnit.SECONDS);

        assertEquals("success", result.getStatus());
        assertEquals(subAgentReply, ((AiAgentCallResult) result).getOutput().getBody(),
                "Fork+exec fallback must return the sub-agent's actual response (zero regression)");
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    private Object sendCallAgentRequest(IAgentMessenger messenger, CallAgentRequestPayload payload,
                                        String senderId, Duration timeout) throws Exception {
        AgentMessageEnvelope env = new AgentMessageEnvelope(
                senderId,
                AgentMessageTopics.callAgentTopic(),
                UUID.randomUUID().toString(),
                AgentMessageKind.REQUEST,
                payload);
        return messenger.request(env, timeout)
                .get(timeout.toMillis() + 2000, TimeUnit.MILLISECONDS);
    }

    private IChatService mockChatService(String reply) {
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

    private DefaultAgentEngine newEngineWithMockChat(String reply) {
        return new DefaultAgentEngine(mockChatService(reply), noOpToolManager(), new InMemorySessionStore());
    }

    /**
     * Wrap a custom {@link IAgentEngine}'s execute() into a DefaultAgentEngine
     * so the engine-level handler registration can be exercised against it.
     * Uses the failing engine's execute for sub-agent execution but the real
     * engine's messenger plumbing.
     */
    private DefaultAgentEngine wrapAsDefaultEngine(IAgentEngine delegate) {
        // The handler is registered on the DefaultAgentEngine instance and calls
        // this.execute() internally. To inject a failing execute, subclass.
        return new DefaultAgentEngine(mockChatService("unused"), noOpToolManager(), new InMemorySessionStore()) {
            @Override
            public CompletableFuture<AgentExecutionResult> execute(AgentMessageRequest request) {
                return delegate.execute(request);
            }
        };
    }
}
