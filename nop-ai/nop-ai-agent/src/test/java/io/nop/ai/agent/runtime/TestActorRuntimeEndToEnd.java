package io.nop.ai.agent.runtime;

import io.nop.ai.agent.engine.AgentExecutionResult;
import io.nop.ai.agent.engine.AgentMessageRequest;
import io.nop.ai.agent.engine.DefaultAgentEngine;
import io.nop.ai.agent.message.DeferredAckMailbox;
import io.nop.ai.agent.message.IMailbox;
import io.nop.ai.agent.message.LocalAgentMessenger;
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
import java.util.Optional;
import java.util.Collection;
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
 * End-to-end + wiring-verification tests for the Actor Runtime integration
 * into {@link DefaultAgentEngine} (plan 218 / L4-8).
 *
 * <p><b>End-to-end (Minimum Rules #22)</b>: a full ReAct execution on a real
 * {@link DefaultAgentEngine} with {@link InMemoryActorRuntime} +
 * {@link DeferredAckMailbox} + {@link LocalAgentMessenger} drives the path:
 * <pre>
 *   engine.execute(request)
 *     → supplyAsync lambda entry: createActor (isEnabled()==true)
 *       → Actor registered in registry + mailbox bound + consumption loop started
 *     → ReAct loop: LLM emits send-message → messenger.send(ASYNC) → inbox
 *       → MailboxMessageHandler → mailbox.offer()
 *       → Actor consumption loop: poll → record to receivedMessages → ack
 *     → ReAct loop completes
 *     → finally: getActorBySession → destroyActor → actor unregistered
 * </pre>
 *
 * <p><b>Wiring verification (Minimum Rules #23)</b>: the Actor is created
 * by the engine at supplyAsync-lambda entry (verified via capturing wrapper),
 * consumed the mailbox message (verified via receivedMessages), and was
 * destroyed in the finally block (verified via registry-empty check).
 *
 * <p><b>No-regression verification</b>: shipped default (NoOpActorRuntime,
 * isEnabled()==false) does not create any Actor and the engine walks its
 * existing supplyAsync path unchanged.
 */
public class TestActorRuntimeEndToEnd {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    private static final String SESSION_ID = "actor-e2e-sess";
    private static final long FAST_POLL_MS = 50L;

    /**
     * Full E2E: Actor is created at execution start, consumes the mailbox
     * message during execution, and is destroyed at execution end.
     */
    @Test
    void actorCreatedConsumesMessageAndDestroyedDuringExecution() throws Exception {
        LocalMessageService platform = new LocalMessageService();
        LocalAgentMessenger messenger = new LocalAgentMessenger(platform);

        IChatService chatService = mockChatEmittingSendMessageThenComplete();
        IToolManager toolManager = toolManagerWithSendMessage();
        DefaultAgentEngine engine = new DefaultAgentEngine(chatService, toolManager);
        engine.setMessenger(messenger);
        engine.setMailboxFactory(sid -> new DeferredAckMailbox());

        // Wrap InMemoryActorRuntime to capture the created Actor for
        // post-execution assertions (the Actor is destroyed in the finally
        // block before execute() returns, so we need a captured reference).
        final InMemoryActorRuntime inner = new InMemoryActorRuntime(
                engine::getSessionMailbox, FAST_POLL_MS, 5000L);
        final java.util.concurrent.atomic.AtomicReference<AgentActor> capturedActor =
                new java.util.concurrent.atomic.AtomicReference<>();
        final AtomicInteger createCallCount = new AtomicInteger(0);
        final AtomicInteger destroyCallCount = new AtomicInteger(0);

        IActorRuntime capturingRuntime = new IActorRuntime() {
            @Override
            public boolean isEnabled() {
                return true;
            }

            @Override
            public AgentActor createActor(String sessionId, String agentName) {
                createCallCount.incrementAndGet();
                AgentActor actor = inner.createActor(sessionId, agentName);
                capturedActor.set(actor);
                return actor;
            }

            @Override
            public Optional<AgentActor> getActor(String actorId) {
                return inner.getActor(actorId);
            }

            @Override
            public Optional<AgentActor> getActorBySession(String sessionId) {
                return inner.getActorBySession(sessionId);
            }

            @Override
            public Collection<AgentActor> getActiveActors() {
                return inner.getActiveActors();
            }

            @Override
            public boolean destroyActor(String actorId) {
                destroyCallCount.incrementAndGet();
                return inner.destroyActor(actorId);
            }

            @Override
            public int destroyAll() {
                return inner.destroyAll();
            }
        };
        engine.setActorRuntime(capturingRuntime);

        AgentMessageRequest request = new AgentMessageRequest(
                "test-agent", "send a message to self", SESSION_ID, null);
        CompletableFuture<AgentExecutionResult> future = engine.execute(request);
        AgentExecutionResult result = future.get(60, TimeUnit.SECONDS);

        assertEquals(AgentExecStatus.completed, result.getStatus(),
                "agent must complete normally; messages: " + result.getMessages());

        // Wiring verification: createActor was called exactly once
        assertEquals(1, createCallCount.get(),
                "createActor must be called exactly once during execution");

        // Wiring verification: destroyActor was called exactly once
        assertEquals(1, destroyCallCount.get(),
                "destroyActor must be called exactly once in the finally block");

        // The Actor was captured
        AgentActor actor = capturedActor.get();
        assertNotNull(actor, "Actor must have been created during execution");
        assertEquals(SESSION_ID, actor.getSessionId());

        // The Actor was destroyed (STOPPED status)
        assertEquals(AgentActorStatus.STOPPED, actor.getStatus(),
                "Actor must be STOPPED after destroyActor in finally block");

        // The Actor consumed the mailbox message (observation-only consumption)
        assertFalse(actor.getReceivedMessages().isEmpty(),
                "Actor must have consumed the send-message tool's ASYNC message "
                        + "(receivedMessages must not be empty)");
        assertEquals("async hello from agent",
                actor.getReceivedMessages().get(0).getEnvelope().getPayload(),
                "consumed message payload must match the send-message content");

        // The Actor is no longer in the registry (destroyed)
        assertFalse(inner.getActorBySession(SESSION_ID).isPresent(),
                "Actor must be unregistered from registry after destroyActor");

        // The mailbox was consumed (no pending/in-flight entries from the
        // consumed message — Actor acked it)
        IMailbox mailbox = engine.getSessionMailbox(SESSION_ID);
        assertNotNull(mailbox);
        // The Actor consumed the message → pending + inFlight should be 0
        // for the consumed message. (There may be a brief window if the Actor
        // hasn't acked yet, but the delay in the mock chat service's 2nd call
        // ensures the Actor has had time to consume.)
        assertEquals(0, mailbox.pendingCount(),
                "consumed message must be acked — no pending entries");
        assertEquals(0, mailbox.inFlightCount(),
                "consumed message must be acked — no in-flight entries");
    }

    /**
     * No-regression: shipped default (NoOpActorRuntime, isEnabled()==false)
     * does not create any Actor. The engine walks its existing supplyAsync
     * path unchanged.
     */
    @Test
    void defaultNoOpActorRuntimeDoesNotCreateActor() throws Exception {
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
        // actorRuntime NOT set → default NoOpActorRuntime (isEnabled()==false)

        String sid = "actor-noop-sess";
        engine.execute(new AgentMessageRequest("test-agent", "go", sid, null))
                .get(60, TimeUnit.SECONDS);

        // No Actor created — default NoOp, zero regression
        assertFalse(engine.getActorRuntime().isEnabled());
        assertTrue(engine.getActorRuntime().getActiveActors().isEmpty());
        assertFalse(engine.getActorRuntime().getActorBySession(sid).isPresent());

        // The engine still works (mailbox created, message could be offered)
        assertNotNull(engine.getSessionMailbox(sid));
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    /**
     * Mock chat service: first call emits a send-message tool call targeting
     * the agent's own session; second call sleeps briefly (to give the Actor's
     * consumption loop time to poll + ack) then returns a plain message.
     */
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
                    toolCall.setId("actor-e2e-1");
                    toolCall.setName("send-message");
                    Map<String, Object> args = new HashMap<>();
                    args.put("targetSessionId", SESSION_ID);
                    args.put("input", "async hello from agent");
                    toolCall.setArguments(args);
                    msg.setToolCalls(List.of(toolCall));
                } else {
                    // Give the Actor's consumption loop time to poll + ack
                    // before the execution completes and destroyActor runs.
                    try {
                        Thread.sleep(300);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
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
