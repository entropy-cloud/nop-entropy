package io.nop.ai.agent.message;

import io.nop.ai.agent.engine.AgentExecutionResult;
import io.nop.ai.agent.engine.AgentMessageRequest;
import io.nop.ai.agent.engine.DefaultAgentEngine;
import io.nop.ai.agent.engine.IAgentEngine;
import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.messages.ChatToolCall;
import io.nop.ai.api.chat.stream.ChatStreamChunk;
import io.nop.ai.toolkit.api.IToolExecuteContext;
import io.nop.ai.toolkit.api.IToolManager;
import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;
import io.nop.ai.toolkit.model.AiToolModel;
import io.nop.ai.agent.tool.SendMessageExecutor;
import io.nop.api.core.message.IMessageService;
import io.nop.api.core.util.ICancelToken;
import io.nop.message.core.local.LocalMessageService;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 2 end-to-end + wiring-verification tests for the deferred-ack mailbox
 * integration into {@link DefaultAgentEngine} (plan 216 / L4-5).
 *
 * <p><b>End-to-end (Minimum Rules #22)</b>: a full ReAct execution on a real
 * {@link DefaultAgentEngine} (non-null mailboxFactory + LocalAgentMessenger)
 * drives the path:
 * <pre>
 *   LLM emits send-message(targetSessionId=own) → messenger.send(ASYNC)
 *     → agent.{sessionId}.inbox → MailboxMessageHandler.onMessage
 *     → mailbox.offer() → test consumer poll() → ack()
 * </pre>
 *
 * <p><b>Wiring verification (Minimum Rules #23)</b>: the mailbox is created by
 * the engine at session-execution start (ensureSessionMailbox), and the handler
 * is registered on the inbox topic — proven by the message arriving in the
 * mailbox after execution.
 */
public class TestMailboxEndToEnd {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    private static final String SESSION_ID = "mailbox-e2e-sess";

    /**
     * End-to-end: the LLM emits a send-message tool call targeting the agent's
     * own session. Because the engine created a mailbox + registered a
     * MailboxMessageHandler on agent.{sessionId}.inbox at execution start, the
     * ASYNC message is buffered in the mailbox for deferred-ack consumption.
     * The test consumer then polls + acks it.
     */
    @Test
    void sendMessageToolDeliversToMailboxThenPollAck() throws Exception {
        LocalMessageService platform = new LocalMessageService();
        LocalAgentMessenger messenger = new LocalAgentMessenger(platform);

        AtomicInteger factoryInvocations = new AtomicInteger(0);

        IChatService chatService = mockChatEmittingSendMessageThenComplete();
        IToolManager toolManager = toolManagerWithSendMessage();
        DefaultAgentEngine engine = new DefaultAgentEngine(chatService, toolManager);
        engine.setMessenger(messenger);
        engine.setMailboxFactory(sid -> {
            factoryInvocations.incrementAndGet();
            return new DeferredAckMailbox();
        });

        AgentMessageRequest request = new AgentMessageRequest(
                "test-agent", "send a message to self", SESSION_ID, null);
        CompletableFuture<AgentExecutionResult> future = engine.execute(request);
        AgentExecutionResult result = future.get(60, TimeUnit.SECONDS);

        assertEquals(AgentExecStatus.completed, result.getStatus(),
                "agent must complete normally; messages: " + result.getMessages());

        // Wiring verification: mailbox was created for this session
        IMailbox mailbox = engine.getSessionMailbox(SESSION_ID);
        assertNotNull(mailbox, "engine must have created a mailbox for the session");
        assertTrue(factoryInvocations.get() >= 1, "mailboxFactory must be invoked at least once");
        assertTrue(mailbox instanceof DeferredAckMailbox,
                "mailbox must be the DeferredAckMailbox produced by the factory");

        // Anti-hollow: the send-message tool's ASYNC message actually arrived
        // in the mailbox (messenger → handler → offer path is connected).
        assertTrue(mailbox.pendingCount() + mailbox.inFlightCount() > 0,
                "the send-message tool's ASYNC message must have been offered to the mailbox");

        MailboxEntry entry = mailbox.poll();
        assertNotNull(entry, "must be able to poll the buffered message");
        assertEquals(MailboxDeliveryState.IN_FLIGHT, entry.getState());
        assertEquals("async hello from agent", entry.getEnvelope().getPayload());

        assertTrue(mailbox.ack(entry.getDeliveryId()));
        assertEquals(0, mailbox.inFlightCount());
        assertEquals(0, mailbox.pendingCount());
    }

    /**
     * End-to-end redelivery path: after the execution, send an ASYNC message
     * directly via the messenger to the session's inbox topic (still routed
     * through the engine-registered MailboxMessageHandler), then poll →
     * nack(requeue=true) → poll again → verify redelivery with new deliveryId
     * and incremented deliveryCount.
     */
    @Test
    void nackRequeueRedeliversViaRegisteredHandler() throws Exception {
        LocalMessageService platform = new LocalMessageService();
        LocalAgentMessenger messenger = new LocalAgentMessenger(platform);

        IChatService chatService = new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                return CompletableFuture.completedFuture(ChatResponse.success(new ChatAssistantMessage()));
            }

            @Override
            public ChatResponse call(ChatRequest request, ICancelToken cancelToken) {
                return ChatResponse.success(new ChatAssistantMessage());
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {
                };
            }
        };
        IToolManager toolManager = toolManagerWithSendMessage();
        DefaultAgentEngine engine = new DefaultAgentEngine(chatService, toolManager);
        engine.setMessenger(messenger);
        engine.setMailboxFactory(sid -> new DeferredAckMailbox());

        String sid = "mailbox-nack-sess";
        AgentMessageRequest request = new AgentMessageRequest("test-agent", "go", sid, null);
        engine.execute(request).get(60, TimeUnit.SECONDS);

        IMailbox mailbox = engine.getSessionMailbox(sid);
        assertNotNull(mailbox, "mailbox must exist after execution");

        // Send an ASYNC message directly via the messenger to the session's
        // inbox topic. The engine-registered handler must offer it to the mailbox.
        AgentMessageEnvelope env = new AgentMessageEnvelope(
                "external-sender",
                AgentMessageTopics.inboxTopic(sid),
                null,
                AgentMessageKind.ASYNC,
                "redeliver-me");
        messenger.send(env);

        assertTrue(mailbox.pendingCount() > 0, "direct messenger.send must reach the mailbox via the registered handler");

        // poll → nack(requeue=true) → poll again
        MailboxEntry first = mailbox.poll();
        assertNotNull(first);
        assertEquals(1, first.getDeliveryCount());
        assertEquals("redeliver-me", first.getEnvelope().getPayload());

        assertTrue(mailbox.nack(first.getDeliveryId(), true));
        assertEquals(1, mailbox.pendingCount(), "requeued entry must be back in PENDING");

        MailboxEntry redelivered = mailbox.poll();
        assertNotNull(redelivered);
        assertEquals(2, redelivered.getDeliveryCount(), "deliveryCount must increment on redelivery");
        assertNotEquals(first.getDeliveryId(), redelivered.getDeliveryId(),
                "redelivery must assign a new deliveryId");
        assertEquals("redeliver-me", redelivered.getEnvelope().getPayload());
        assertTrue(mailbox.ack(redelivered.getDeliveryId()));
        assertEquals(0, mailbox.pendingCount());
    }

    /**
     * Wiring: default mailboxFactory is null → no mailbox created, no
     * regression (backward compatible).
     */
    @Test
    void defaultNullMailboxFactoryCreatesNoMailbox() throws Exception {
        LocalMessageService platform = new LocalMessageService();
        LocalAgentMessenger messenger = new LocalAgentMessenger(platform);

        IChatService chatService = new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                return CompletableFuture.completedFuture(ChatResponse.success(new ChatAssistantMessage()));
            }

            @Override
            public ChatResponse call(ChatRequest request, ICancelToken cancelToken) {
                return ChatResponse.success(new ChatAssistantMessage());
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {
                };
            }
        };
        IToolManager toolManager = toolManagerWithSendMessage();
        DefaultAgentEngine engine = new DefaultAgentEngine(chatService, toolManager);
        engine.setMessenger(messenger);
        // mailboxFactory NOT set → null default

        String sid = "mailbox-default-sess";
        engine.execute(new AgentMessageRequest("test-agent", "go", sid, null))
                .get(60, TimeUnit.SECONDS);

        assertNull(engine.getSessionMailbox(sid),
                "no mailbox must be created when mailboxFactory is null (zero regression)");
        assertNull(engine.getMailboxFactory());
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    private IChatService mockChatEmittingSendMessageThenComplete() {
        return new IChatService() {
            final AtomicInteger callCount = new AtomicInteger(0);

            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                return CompletableFuture.completedFuture(buildResponse());
            }

            @Override
            public ChatResponse call(ChatRequest request, ICancelToken cancelToken) {
                return buildResponse();
            }

            private ChatResponse buildResponse() {
                int n = callCount.getAndIncrement();
                ChatAssistantMessage msg = new ChatAssistantMessage();
                if (n == 0) {
                    msg.setContent("");
                    ChatToolCall toolCall = new ChatToolCall();
                    toolCall.setId("mb-e2e-1");
                    toolCall.setName("send-message");
                    Map<String, Object> args = new HashMap<>();
                    args.put("targetSessionId", SESSION_ID);
                    args.put("input", "async hello from agent");
                    toolCall.setArguments(args);
                    msg.setToolCalls(List.of(toolCall));
                } else {
                    msg.setContent("done");
                }
                return ChatResponse.success(msg);
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {
                };
            }
        };
    }

    private IToolManager toolManagerWithSendMessage() {
        SendMessageExecutor sendMessageExecutor = new SendMessageExecutor();
        return new IToolManager() {
            @Override
            public CompletableFuture<AiToolCallResult> callTool(String toolName, AiToolCall call, IToolExecuteContext context) {
                if ("send-message".equals(toolName)) {
                    return sendMessageExecutor.executeAsync(call, context).toCompletableFuture();
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
}
