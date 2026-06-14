package io.nop.ai.agent.tool;

import io.nop.ai.agent.engine.AgentToolExecuteContext;
import io.nop.ai.agent.message.AgentMessageEnvelope;
import io.nop.ai.agent.message.AgentMessageKind;
import io.nop.ai.agent.message.IAgentMessageHandler;
import io.nop.ai.agent.message.IAgentMessenger;
import io.nop.ai.agent.message.LocalAgentMessenger;
import io.nop.ai.agent.message.NoOpAgentMessenger;
import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;
import io.nop.api.core.message.IMessageService;
import io.nop.api.core.message.IMessageSubscription;
import io.nop.message.core.local.LocalMessageService;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 3 unit tests for the functional {@link SendMessageExecutor}:
 * <ul>
 *     <li>Delivers a message to a registered inbox handler via the messenger</li>
 *     <li>Honest no-delivery reporting when NoOp messenger is configured</li>
 *     <li>Fail-fast on missing targetSessionId, missing input, non-agent context, null messenger</li>
 * </ul>
 */
public class TestSendMessageExecutor {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    private AgentToolExecuteContext createContext(IAgentMessenger messenger, String sessionId, String agentName) {
        return new AgentToolExecuteContext(
                new File("."), Collections.emptyMap(), 0L, null, null, null,
                null, messenger, sessionId, agentName);
    }

    private AiToolCall createCall(String targetSessionId, String input) {
        AiToolCall call = new AiToolCall();
        call.setToolName("send-message");
        call.setId(1);
        call.setInput("{\"targetSessionId\":\"" + targetSessionId + "\",\"input\":\"" + input + "\"}");
        return call;
    }

    @Test
    void deliversMessageToRegisteredHandler() throws Exception {
        IMessageService messageService = new LocalMessageService();
        LocalAgentMessenger messenger = new LocalAgentMessenger(messageService);

        AtomicReference<AgentMessageEnvelope> received = new AtomicReference<>();
        IMessageSubscription subscription = messenger.registerHandler(
                io.nop.ai.agent.message.AgentMessageTopics.inboxTopic("target-sess-1"),
                new IAgentMessageHandler() {
                    @Override
                    public Object onMessage(AgentMessageEnvelope envelope) {
                        received.set(envelope);
                        return null;
                    }
                });

        AgentToolExecuteContext ctx = createContext(messenger, "sender-sess-1", "sender-agent");
        AiToolCall call = createCall("target-sess-1", "hello from sender");

        SendMessageExecutor executor = new SendMessageExecutor();
        AiToolCallResult result = executor.executeAsync(call, ctx).toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertEquals("success", result.getStatus());
        assertNotNull(result.getOutput());
        assertTrue(result.getOutput().getBody().contains("agent.target-sess-1.inbox"));

        AgentMessageEnvelope envelope = received.get();
        assertNotNull(envelope, "Handler should have received the envelope");
        assertEquals("hello from sender", envelope.getPayload());
        assertEquals(AgentMessageKind.ASYNC, envelope.getKind());
        assertEquals("sender-sess-1", envelope.getSenderId());
        assertEquals("agent.target-sess-1.inbox", envelope.getTargetTopic());
        assertNotNull(envelope.getCorrelationId());

        subscription.cancel();
    }

    @Test
    void noOpMessengerReportsNotDeliveredHonestly() throws Exception {
        AgentToolExecuteContext ctx = createContext(NoOpAgentMessenger.noOp(), "sender-sess-2", "sender-agent");
        AiToolCall call = createCall("target-sess-2", "will not be delivered");

        SendMessageExecutor executor = new SendMessageExecutor();
        AiToolCallResult result = executor.executeAsync(call, ctx).toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertEquals("success", result.getStatus(),
                "send-message with NoOp messenger should still return success (it is fire-and-forget)");
        assertNotNull(result.getOutput());
        String body = result.getOutput().getBody();
        assertTrue(body.contains("not delivered"),
                "NoOp messenger result must honestly report non-delivery: " + body);
        assertTrue(body.contains("agent.target-sess-2.inbox"),
                "Result should mention the target topic for observability: " + body);
    }

    @Test
    void missingTargetSessionIdReturnsError() throws Exception {
        AgentToolExecuteContext ctx = createContext(NoOpAgentMessenger.noOp(), "sender-sess-3", "sender-agent");
        AiToolCall call = new AiToolCall();
        call.setToolName("send-message");
        call.setId(1);
        call.setInput("{\"input\":\"hello\"}");

        SendMessageExecutor executor = new SendMessageExecutor();
        AiToolCallResult result = executor.executeAsync(call, ctx).toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertEquals("failure", result.getStatus());
        assertNotNull(result.getError());
        assertTrue(result.getError().getBody().contains("targetSessionId is required"));
    }

    @Test
    void missingInputReturnsError() throws Exception {
        AgentToolExecuteContext ctx = createContext(NoOpAgentMessenger.noOp(), "sender-sess-4", "sender-agent");
        AiToolCall call = new AiToolCall();
        call.setToolName("send-message");
        call.setId(1);
        call.setInput("{\"targetSessionId\":\"target-sess-4\"}");

        SendMessageExecutor executor = new SendMessageExecutor();
        AiToolCallResult result = executor.executeAsync(call, ctx).toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertEquals("failure", result.getStatus());
        assertNotNull(result.getError());
        assertTrue(result.getError().getBody().contains("input") && result.getError().getBody().contains("required"));
    }

    @Test
    void nonAgentContextReturnsError() throws Exception {
        io.nop.ai.agent.engine.SimpleToolExecuteContext simpleCtx =
                new io.nop.ai.agent.engine.SimpleToolExecuteContext(new File("."), null, null);

        AiToolCall call = createCall("target-sess-5", "hello");

        SendMessageExecutor executor = new SendMessageExecutor();
        AiToolCallResult result = executor.executeAsync(call, simpleCtx).toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertEquals("failure", result.getStatus());
        assertNotNull(result.getError());
        assertTrue(result.getError().getBody().contains("AgentToolExecuteContext"));
    }
}
