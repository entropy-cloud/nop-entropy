package io.nop.ai.agent.message;

import io.nop.api.core.message.IMessageConsumeContext;
import io.nop.api.core.message.IMessageConsumer;
import io.nop.api.core.message.IMessageService;
import io.nop.api.core.message.IMessageSubscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * In-memory {@link IAgentMessenger} backed by the platform
 * {@code IMessageService} (typically {@code LocalMessageService}). All message
 * routing goes through the platform message service — this class does not
 * maintain its own delivery queue.
 *
 * <p><b>Ack-topic suppression (plan 166 Phase 2 Decision M2):</b> the platform
 * {@code LocalMessageService} auto-routes a consumer's non-null return value to
 * {@code ack-{topic}}. To keep responses on the requester's reply topic instead,
 * this implementation's adapter captures the handler's return value, explicitly
 * sends a RESPONSE envelope to the requester's reply topic, and then returns
 * {@code null} to the platform — suppressing the auto ack-topic routing.
 *
 * <p><b>Shared reply topic (plan 166 Phase 2):</b> request-response shares a
 * single reply topic subscription per reply topic (derived from the request
 * envelope's {@code senderId}). Multiple in-flight requests are demultiplexed by
 * {@code correlationId} via a {@link ConcurrentHashMap}.
 *
 * <p><b>Thread safety:</b> safe for concurrent use. Reply subscriptions are
 * created lazily with double-checked locking.
 */
public class LocalAgentMessenger implements IAgentMessenger {

    private static final Logger LOG = LoggerFactory.getLogger(LocalAgentMessenger.class);

    private final IMessageService messageService;

    /**
     * correlationId → pending request future. Entries are removed when the
     * reply arrives (or the future times out / is cancelled).
     */
    private final Map<String, CompletableFuture<Object>> pendingRequests = new ConcurrentHashMap<>();

    /**
     * replyTopic → subscription. One subscription per reply topic, shared by all
     * in-flight requests to that reply topic.
     */
    private final Map<String, IMessageSubscription> replySubscriptions = new ConcurrentHashMap<>();

    public LocalAgentMessenger(IMessageService messageService) {
        this.messageService = Objects.requireNonNull(messageService, "messageService");
    }

    @Override
    public void send(AgentMessageEnvelope envelope) {
        Objects.requireNonNull(envelope, "envelope");
        String topic = envelope.getTargetTopic();
        messageService.send(topic, envelope);
    }

    @Override
    public CompletableFuture<Object> request(AgentMessageEnvelope requestEnvelope, Duration timeout) {
        Objects.requireNonNull(requestEnvelope, "requestEnvelope");
        Objects.requireNonNull(timeout, "timeout");
        if (requestEnvelope.getKind() != AgentMessageKind.REQUEST) {
            throw new IllegalArgumentException(
                    "LocalAgentMessenger.request: envelope kind must be REQUEST, got " + requestEnvelope.getKind());
        }
        if (requestEnvelope.getCorrelationId() == null || requestEnvelope.getCorrelationId().isEmpty()) {
            throw new IllegalArgumentException(
                    "LocalAgentMessenger.request: envelope correlationId must not be null or empty");
        }
        if (requestEnvelope.getSenderId() == null || requestEnvelope.getSenderId().isEmpty()) {
            throw new IllegalArgumentException(
                    "LocalAgentMessenger.request: envelope senderId must not be null or empty");
        }
        long millis = timeout.toMillis();
        if (millis <= 0) {
            throw new IllegalArgumentException(
                    "LocalAgentMessenger.request: timeout must be positive, got " + millis + "ms");
        }

        String replyTopic = AgentMessageTopics.replyTopic(requestEnvelope.getSenderId());
        ensureReplySubscription(replyTopic);

        String correlationId = requestEnvelope.getCorrelationId();
        CompletableFuture<Object> future = new CompletableFuture<>();
        CompletableFuture<Object> previous = pendingRequests.put(correlationId, future);
        if (previous != null && !previous.isDone()) {
            // An in-flight request already used this correlationId — abort it to
            // avoid corrupting demultiplexing. The caller should use unique ids.
            pendingRequests.put(correlationId, future);
            LOG.warn("LocalAgentMessenger.request: correlationId collision; previous in-flight request aborted: correlationId={}",
                    correlationId);
            previous.completeExceptionally(
                    new IllegalStateException("correlationId reused while request still in-flight: " + correlationId));
        }

        // Clean up the pending entry on terminal completion (success, timeout, cancel).
        future.whenComplete((r, e) -> pendingRequests.remove(correlationId, future));

        // Send the request to its target topic via the platform message service.
        messageService.send(requestEnvelope.getTargetTopic(), requestEnvelope);

        return future.orTimeout(millis, TimeUnit.MILLISECONDS);
    }

    @Override
    public IMessageSubscription registerHandler(String topic, IAgentMessageHandler handler) {
        if (topic == null || topic.isEmpty()) {
            throw new IllegalArgumentException("LocalAgentMessenger.registerHandler: topic must not be null or empty");
        }
        Objects.requireNonNull(handler, "handler");
        HandlerAdapter adapter = new HandlerAdapter(handler, messageService);
        return messageService.subscribe(topic, adapter);
    }

    private void ensureReplySubscription(String replyTopic) {
        IMessageSubscription existing = replySubscriptions.get(replyTopic);
        if (existing != null) {
            return;
        }
        synchronized (replySubscriptions) {
            if (replySubscriptions.get(replyTopic) == null) {
                IMessageSubscription subscription = messageService.subscribe(replyTopic, new ReplyDemultiplexer());
                replySubscriptions.put(replyTopic, subscription);
            }
        }
    }

    /**
     * Adapts an {@link IAgentMessageHandler} to the platform
     * {@link IMessageConsumer} contract, performing ack-topic suppression.
     *
     * <p>When the handler returns a non-null value for a REQUEST envelope, the
     * adapter builds a RESPONSE envelope and sends it to the requester's reply
     * topic (derived from the request's {@code senderId}). It then returns
     * {@code null} to the platform so that
     * {@code LocalMessageService.handleMessageResult} does not auto-route to
     * {@code ack-{topic}}.
     */
    static final class HandlerAdapter implements IMessageConsumer {
        private final IAgentMessageHandler handler;
        private final IMessageService messageService;

        HandlerAdapter(IAgentMessageHandler handler, IMessageService messageService) {
            this.handler = handler;
            this.messageService = messageService;
        }

        @Override
        public Object onMessage(String topic, Object message, IMessageConsumeContext context) {
            if (!(message instanceof AgentMessageEnvelope)) {
                LOG.debug("nop.ai.agent.message.ignore-non-envelope:topic={}, messageClass={}",
                        topic, message == null ? "null" : message.getClass().getName());
                return null;
            }
            AgentMessageEnvelope env = (AgentMessageEnvelope) message;

            Object result;
            try {
                result = handler.onMessage(env);
            } catch (Exception e) {
                // MVP: responder failure → requester times out. Do not propagate
                // to platform (which would only log). No response is sent.
                LOG.error("nop.ai.agent.message.handler-error:topic={}, correlationId={}",
                        topic, env.getCorrelationId(), e);
                return null;
            }

            if (result != null && env.getKind() == AgentMessageKind.REQUEST) {
                String replyTopic = AgentMessageTopics.replyTopic(env.getSenderId());
                AgentMessageEnvelope responseEnvelope = new AgentMessageEnvelope(
                        null,
                        replyTopic,
                        env.getCorrelationId(),
                        AgentMessageKind.RESPONSE,
                        result);
                // Explicitly route the response to the requester's reply topic.
                messageService.send(replyTopic, responseEnvelope);
            }
            // Always return null to suppress the platform's auto ack-topic routing.
            return null;
        }
    }

    /**
     * Receives RESPONSE envelopes on a reply topic and completes the matching
     * pending request future by correlationId. Always returns null to the
     * platform (no ack-topic routing for replies).
     */
    final class ReplyDemultiplexer implements IMessageConsumer {
        @Override
        public Object onMessage(String topic, Object message, IMessageConsumeContext context) {
            if (!(message instanceof AgentMessageEnvelope)) {
                return null;
            }
            AgentMessageEnvelope env = (AgentMessageEnvelope) message;
            if (env.getKind() != AgentMessageKind.RESPONSE) {
                return null;
            }
            String correlationId = env.getCorrelationId();
            CompletableFuture<Object> future = pendingRequests.remove(correlationId);
            if (future != null) {
                future.complete(env.getPayload());
            } else {
                LOG.debug("nop.ai.agent.message.reply-no-pending:topic={}, correlationId={}",
                        topic, correlationId);
            }
            return null;
        }
    }
}
