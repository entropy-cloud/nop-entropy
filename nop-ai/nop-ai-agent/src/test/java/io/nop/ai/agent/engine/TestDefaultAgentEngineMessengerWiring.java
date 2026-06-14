package io.nop.ai.agent.engine;

import io.nop.ai.agent.message.AgentMessageEnvelope;
import io.nop.ai.agent.message.AgentMessageKind;
import io.nop.ai.agent.message.AgentMessageTopics;
import io.nop.ai.agent.message.IAgentMessenger;
import io.nop.ai.agent.message.LocalAgentMessenger;
import io.nop.ai.agent.message.NoOpAgentMessenger;
import io.nop.message.core.local.LocalMessageService;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 3 tests: verifies {@link DefaultAgentEngine} messenger wiring, the
 * NoOp default backward-compat, and end-to-end inter-agent messaging through a
 * messenger configured on the engine.
 *
 * <p>These tests do NOT run a full agent execution — they verify the messenger
 * is correctly wired into the engine and can be retrieved by future tools /
 * actor runtime, and that two endpoints sharing the engine's messenger can
 * exchange request-response messages through the platform
 * {@code LocalMessageService}.
 */
public class TestDefaultAgentEngineMessengerWiring {

    // ========================================================================
    // Wiring: setMessenger / getMessenger round-trip
    // ========================================================================

    @Test
    void setMessengerAndGetMessengerReturnSameInstance() {
        DefaultAgentEngine engine = newEngineStub();
        LocalMessageService platform = new LocalMessageService();
        IAgentMessenger messenger = new LocalAgentMessenger(platform);

        engine.setMessenger(messenger);

        assertSame(messenger, engine.getMessenger(),
                "messenger retrieved from engine must be the same instance set via setMessenger");
    }

    @Test
    void setMessengerNullFallsBackToNoOp() {
        DefaultAgentEngine engine = newEngineStub();
        engine.setMessenger(null);
        IAgentMessenger retrieved = engine.getMessenger();
        assertTrue(retrieved instanceof NoOpAgentMessenger,
                "setMessenger(null) must fall back to NoOp, got: " + retrieved.getClass());
    }

    @Test
    void defaultMessengerIsNoOpWhenNeverSet() {
        DefaultAgentEngine engine = newEngineStub();
        IAgentMessenger messenger = engine.getMessenger();
        assertTrue(messenger instanceof NoOpAgentMessenger,
                "engine constructed without setMessenger must default to NoOp, got: "
                        + (messenger == null ? "null" : messenger.getClass()));
        assertSame(NoOpAgentMessenger.noOp(), messenger,
                "default NoOp messenger must be the singleton instance");
    }

    // ========================================================================
    // Backward-compat: default NoOp request fails fast
    // ========================================================================

    @Test
    void defaultNoOpMessengerRequestFailsFastInEngineContext() {
        DefaultAgentEngine engine = newEngineStub();
        IAgentMessenger messenger = engine.getMessenger();

        AgentMessageEnvelope request = new AgentMessageEnvelope(
                "A", "agent.B.inbox", "c1", AgentMessageKind.REQUEST, "ping");

        UnsupportedOperationException ex = assertThrows(UnsupportedOperationException.class,
                () -> messenger.request(request, Duration.ofSeconds(1)));
        assertTrue(ex.getMessage().contains("no message service configured"),
                "default NoOp request must fail fast with a clear message: " + ex.getMessage());
    }

    @Test
    void defaultNoOpMessengerSendDoesNotThrowInEngineContext() {
        DefaultAgentEngine engine = newEngineStub();
        IAgentMessenger messenger = engine.getMessenger();
        AgentMessageEnvelope env = new AgentMessageEnvelope(
                "A", "agent.B.inbox", null, AgentMessageKind.ASYNC, "hello");
        // send on NoOp must be a safe no-op (no exception)
        messenger.send(env);
    }

    // ========================================================================
    // End-to-end: two endpoints via engine-configured messenger
    // ========================================================================

    @Test
    void twoEndpointsRoundTripViaEngineMessenger() throws Exception {
        // Construct the engine with a real LocalMessageService-backed messenger.
        LocalMessageService platform = new LocalMessageService();
        IAgentMessenger messenger = new LocalAgentMessenger(platform);
        DefaultAgentEngine engine = newEngineStub();
        engine.setMessenger(messenger);

        // Verify wiring: the engine's messenger is the one we set.
        IAgentMessenger wired = engine.getMessenger();
        assertSame(messenger, wired, "engine must expose the configured messenger");

        // Endpoint A: requester, registers nothing on its inbox (it only sends).
        // Endpoint B: responder, registers a handler on agent.B.inbox.
        AtomicReference<String> responderSaw = new AtomicReference<>();
        wired.registerHandler(AgentMessageTopics.inboxTopic("B"), envelope -> {
            responderSaw.set((String) envelope.getPayload());
            return "ack-from-B:" + envelope.getPayload();
        });

        // Endpoint A sends a REQUEST to endpoint B's inbox via the engine's messenger.
        AgentMessageEnvelope request = new AgentMessageEnvelope(
                "A",
                AgentMessageTopics.inboxTopic("B"),
                "corr-roundtrip",
                AgentMessageKind.REQUEST,
                "hello-B");

        CompletableFuture<Object> future = wired.request(request, Duration.ofSeconds(2));

        // The response must arrive through the platform LocalMessageService,
        // via B's handler reply → A's reply topic → A's CompletableFuture.
        Object result = future.get(3, TimeUnit.SECONDS);

        assertEquals("ack-from-B:hello-B", result,
                "A's future must complete with B's response payload");
        assertEquals("hello-B", responderSaw.get(),
                "B's handler must have received A's request payload");
        assertTrue(future.isDone());
        assertTrue(!future.isCompletedExceptionally());
    }

    @Test
    void twoEndpointsAsyncSendViaEngineMessenger() {
        LocalMessageService platform = new LocalMessageService();
        IAgentMessenger messenger = new LocalAgentMessenger(platform);
        DefaultAgentEngine engine = newEngineStub();
        engine.setMessenger(messenger);
        IAgentMessenger wired = engine.getMessenger();

        AtomicReference<String> received = new AtomicReference<>();
        wired.registerHandler(AgentMessageTopics.inboxTopic("C"), envelope -> {
            received.set((String) envelope.getPayload());
            return null;
        });

        wired.send(new AgentMessageEnvelope(
                "A",
                AgentMessageTopics.inboxTopic("C"),
                null,
                AgentMessageKind.ASYNC,
                "fire-and-forget"));

        assertEquals("fire-and-forget", received.get(),
                "fire-and-forget message must arrive at endpoint C's handler");
    }

    @Test
    void existingEngineConstructionPathsAreUnchanged() {
        // Engine constructed via the simplest constructor (chatService, toolManager)
        // must still work and default to NoOp messenger — no new required args.
        DefaultAgentEngine engine = new DefaultAgentEngine(null, null);
        IAgentMessenger messenger = engine.getMessenger();
        assertTrue(messenger instanceof NoOpAgentMessenger);
    }

    // ========================================================================
    // Helper
    // ========================================================================

    /**
     * Construct a DefaultAgentEngine without running CoreInitialization — the
     * messenger wiring tests don't need agent model loading. Passing null
     * chatService/toolManager is fine because we never invoke execute().
     */
    private DefaultAgentEngine newEngineStub() {
        return new DefaultAgentEngine(null, null);
    }
}
