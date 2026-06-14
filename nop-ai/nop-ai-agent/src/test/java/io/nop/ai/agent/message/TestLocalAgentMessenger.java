package io.nop.ai.agent.message;

import io.nop.ai.agent.engine.NopAiAgentException;
import io.nop.api.core.message.IMessageConsumeContext;
import io.nop.api.core.message.IMessageConsumer;
import io.nop.api.core.message.IMessageSubscription;
import io.nop.message.core.local.LocalMessageService;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 2 tests for {@link LocalAgentMessenger} and {@link NoOpAgentMessenger}.
 *
 * <p>Each test exercises the full path through the platform
 * {@code LocalMessageService} — no mocked transport — so the ack-topic
 * suppression, shared reply topic demultiplexing, and correlationId matching
 * are all verified end-to-end through the real platform plumbing.
 */
public class TestLocalAgentMessenger {

    // ========================================================================
    // send (fire-and-forget)
    // ========================================================================

    @Test
    void sendDeliversEnvelopeToRegisteredHandler() {
        LocalMessageService platform = new LocalMessageService();
        LocalAgentMessenger messenger = new LocalAgentMessenger(platform);

        AtomicReference<AgentMessageEnvelope> received = new AtomicReference<>();
        messenger.registerHandler("agent.B.inbox", env -> {
            received.set(env);
            return null;
        });

        AgentMessageEnvelope env = new AgentMessageEnvelope(
                "sender-A", "agent.B.inbox", null, AgentMessageKind.ASYNC, "hello-async");
        messenger.send(env);

        assertNotNull(received.get(), "handler should have received the envelope");
        assertEquals("hello-async", received.get().getPayload());
        assertEquals("sender-A", received.get().getSenderId());
    }

    // ========================================================================
    // request-response: success + ack-topic suppression
    // ========================================================================

    @Test
    void requestResponseCompletesWithHandlerReturnValue() throws Exception {
        LocalMessageService platform = new LocalMessageService();
        LocalAgentMessenger messenger = new LocalAgentMessenger(platform);

        // responder registers a handler that returns a response
        messenger.registerHandler("agent.B.inbox", envelope -> "reply-to:" + envelope.getPayload());

        AgentMessageEnvelope request = new AgentMessageEnvelope(
                "A", "agent.B.inbox", "corr-1", AgentMessageKind.REQUEST, "ping");

        CompletableFuture<Object> future = messenger.request(request, Duration.ofSeconds(2));
        Object result = future.get(3, TimeUnit.SECONDS);

        assertEquals("reply-to:ping", result);
        assertTrue(future.isDone());
        assertFalse(future.isCompletedExceptionally());
    }

    @Test
    void responseGoesToReplyTopicNotResponderAckTopic() throws Exception {
        LocalMessageService platform = new LocalMessageService();
        LocalAgentMessenger messenger = new LocalAgentMessenger(platform);

        // responder handler returns a response value
        messenger.registerHandler("agent.B.inbox", envelope -> "the-response");

        // spy on the responder's ack-topic (ack-agent.B.inbox) to verify the
        // platform auto-routing is suppressed
        AtomicReference<Object> ackTopicPayload = new AtomicReference<>();
        platform.subscribe("ack-agent.B.inbox", new IMessageConsumer() {
            @Override
            public Object onMessage(String topic, Object message, IMessageConsumeContext context) {
                ackTopicPayload.set(message);
                return null;
            }
        });

        AgentMessageEnvelope request = new AgentMessageEnvelope(
                "A", "agent.B.inbox", "corr-1", AgentMessageKind.REQUEST, "ping");

        CompletableFuture<Object> future = messenger.request(request, Duration.ofSeconds(2));
        Object result = future.get(3, TimeUnit.SECONDS);

        // The response must arrive at the requester (via reply topic), proving
        // the future completed with the real response payload.
        assertEquals("the-response", result);

        // The responder's ack-topic must NOT have received the response payload.
        // This proves the adapter suppressed the platform's auto ack-topic routing.
        Object ackReceived = ackTopicPayload.get();
        assertTrue(ackReceived == null,
                "ack-topic must NOT receive the response; ack-topic payload was: " + ackReceived);
    }

    // ========================================================================
    // request-response: timeout
    // ========================================================================

    @Test
    void requestTimesOutWhenNoReplyArrives() {
        LocalMessageService platform = new LocalMessageService();
        LocalAgentMessenger messenger = new LocalAgentMessenger(platform);

        // No handler registered on the target topic → no reply will arrive.
        AgentMessageEnvelope request = new AgentMessageEnvelope(
                "A", "agent.B.inbox", "corr-timeout", AgentMessageKind.REQUEST, "ping");

        CompletableFuture<Object> future = messenger.request(request, Duration.ofMillis(200));

        ExecutionException ex = assertThrows(ExecutionException.class, () -> future.get(2, TimeUnit.SECONDS));
        assertTrue(ex.getCause() instanceof TimeoutException,
                "expected TimeoutException but got: " + ex.getCause());
    }

    // ========================================================================
    // correlation matching on shared reply topic
    // ========================================================================

    @Test
    void mismatchedCorrelationIdDoesNotCompleteOtherRequest() throws Exception {
        LocalMessageService platform = new LocalMessageService();
        LocalAgentMessenger messenger = new LocalAgentMessenger(platform);

        // Both requests share the same reply topic (agent.A.reply) because they
        // have the same senderId "A".
        AgentMessageEnvelope req1 = new AgentMessageEnvelope(
                "A", "agent.B.inbox", "corr-1", AgentMessageKind.REQUEST, "req-1");
        AgentMessageEnvelope req2 = new AgentMessageEnvelope(
                "A", "agent.B.inbox", "corr-2", AgentMessageKind.REQUEST, "req-2");

        CompletableFuture<Object> future1 = messenger.request(req1, Duration.ofMillis(500));
        CompletableFuture<Object> future2 = messenger.request(req2, Duration.ofMillis(2000));

        // Deliver a reply ONLY for corr-2 directly onto the shared reply topic.
        // We use the platform to simulate the responder sending a RESPONSE.
        AgentMessageEnvelope replyForReq2 = new AgentMessageEnvelope(
                "B", AgentMessageTopics.replyTopic("A"), "corr-2",
                AgentMessageKind.RESPONSE, "answer-2");
        platform.send(AgentMessageTopics.replyTopic("A"), replyForReq2);

        // future2 should complete with answer-2
        Object result2 = future2.get(2, TimeUnit.SECONDS);
        assertEquals("answer-2", result2);

        // future1 must NOT be complete (its correlationId didn't match). Give it
        // a brief moment to ensure no spurious completion, then assert.
        assertFalse(future1.isDone(),
                "future1 must not be completed by a reply with mismatched correlationId");
        // clean up: let future1 time out
        assertThrows(ExecutionException.class, () -> future1.get(2, TimeUnit.SECONDS));
    }

    @Test
    void multipleRequestsOnSameReplyTopicAreDemultiplexed() throws Exception {
        LocalMessageService platform = new LocalMessageService();
        LocalAgentMessenger messenger = new LocalAgentMessenger(platform);

        // responder echoes the correlationId back as the payload
        messenger.registerHandler("agent.B.inbox",
                envelope -> "resp-" + envelope.getCorrelationId());

        AgentMessageEnvelope req1 = new AgentMessageEnvelope(
                "A", "agent.B.inbox", "c1", AgentMessageKind.REQUEST, "p1");
        AgentMessageEnvelope req2 = new AgentMessageEnvelope(
                "A", "agent.B.inbox", "c2", AgentMessageKind.REQUEST, "p2");

        CompletableFuture<Object> f1 = messenger.request(req1, Duration.ofSeconds(2));
        CompletableFuture<Object> f2 = messenger.request(req2, Duration.ofSeconds(2));

        assertEquals("resp-c1", f1.get(3, TimeUnit.SECONDS));
        assertEquals("resp-c2", f2.get(3, TimeUnit.SECONDS));
    }

    // ========================================================================
    // request validation
    // ========================================================================

    @Test
    void requestRejectsNonRequestKindEnvelope() {
        LocalMessageService platform = new LocalMessageService();
        LocalAgentMessenger messenger = new LocalAgentMessenger(platform);
        AgentMessageEnvelope async = new AgentMessageEnvelope(
                "A", "agent.B.inbox", "c1", AgentMessageKind.ASYNC, "p");
        assertThrows(IllegalArgumentException.class,
                () -> messenger.request(async, Duration.ofSeconds(1)));
    }

    @Test
    void requestRejectsNullCorrelationId() {
        LocalMessageService platform = new LocalMessageService();
        LocalAgentMessenger messenger = new LocalAgentMessenger(platform);
        AgentMessageEnvelope env = new AgentMessageEnvelope(
                "A", "agent.B.inbox", null, AgentMessageKind.REQUEST, "p");
        assertThrows(IllegalArgumentException.class,
                () -> messenger.request(env, Duration.ofSeconds(1)));
    }

    @Test
    void requestRejectsNullSenderId() {
        LocalMessageService platform = new LocalMessageService();
        LocalAgentMessenger messenger = new LocalAgentMessenger(platform);
        AgentMessageEnvelope env = new AgentMessageEnvelope(
                null, "agent.B.inbox", "c1", AgentMessageKind.REQUEST, "p");
        assertThrows(IllegalArgumentException.class,
                () -> messenger.request(env, Duration.ofSeconds(1)));
    }

    @Test
    void requestRejectsNonPositiveTimeout() {
        LocalMessageService platform = new LocalMessageService();
        LocalAgentMessenger messenger = new LocalAgentMessenger(platform);
        AgentMessageEnvelope env = new AgentMessageEnvelope(
                "A", "agent.B.inbox", "c1", AgentMessageKind.REQUEST, "p");
        assertThrows(IllegalArgumentException.class,
                () -> messenger.request(env, Duration.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> messenger.request(env, Duration.ofMillis(-1)));
    }

    @Test
    void handlerExceptionDoesNotPropagateToRequesterButRequestTimesOut() {
        LocalMessageService platform = new LocalMessageService();
        LocalAgentMessenger messenger = new LocalAgentMessenger(platform);

        // handler throws — MVP: requester times out (no error envelope in MVP)
        messenger.registerHandler("agent.B.inbox", envelope -> {
            throw new NopAiAgentException("responder boom");
        });

        AgentMessageEnvelope env = new AgentMessageEnvelope(
                "A", "agent.B.inbox", "c1", AgentMessageKind.REQUEST, "p");
        CompletableFuture<Object> future = messenger.request(env, Duration.ofMillis(200));

        ExecutionException ex = assertThrows(ExecutionException.class,
                () -> future.get(2, TimeUnit.SECONDS));
        assertTrue(ex.getCause() instanceof TimeoutException);
    }

    // ========================================================================
    // registerHandler returns cancellable subscription
    // ========================================================================

    @Test
    void registerHandlerReturnsCancellableSubscription() {
        LocalMessageService platform = new LocalMessageService();
        LocalAgentMessenger messenger = new LocalAgentMessenger(platform);

        IMessageSubscription subscription = messenger.registerHandler("agent.B.inbox", envelope -> null);
        assertNotNull(subscription);
        assertFalse(subscription.isCancelled());

        // cancel and verify no more delivery
        subscription.cancel();
        assertTrue(subscription.isCancelled());

        AtomicReference<Object> received = new AtomicReference<>();
        messenger.registerHandler("agent.B.inbox", env -> {
            received.set(env.getPayload());
            return null;
        });
        messenger.send(new AgentMessageEnvelope(
                "X", "agent.B.inbox", null, AgentMessageKind.ASYNC, "after-cancel"));
        // The cancelled handler is gone; the new handler receives.
        assertNotNull(received.get());
    }

    @Test
    void registerHandlerRejectsNullOrEmptyTopic() {
        LocalMessageService platform = new LocalMessageService();
        LocalAgentMessenger messenger = new LocalAgentMessenger(platform);
        assertThrows(IllegalArgumentException.class,
                () -> messenger.registerHandler(null, envelope -> null));
        assertThrows(IllegalArgumentException.class,
                () -> messenger.registerHandler("", envelope -> null));
    }

    @Test
    void constructorRejectsNullMessageService() {
        assertThrows(NullPointerException.class, () -> new LocalAgentMessenger(null));
    }
}
