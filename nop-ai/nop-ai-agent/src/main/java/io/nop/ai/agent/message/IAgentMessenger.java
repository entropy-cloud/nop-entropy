package io.nop.ai.agent.message;

import io.nop.api.core.message.IMessageSubscription;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Agent-domain inter-agent messenger, layered on the platform
 * {@code IMessageService} / {@code LocalMessageService}. Provides three
 * behaviors:
 *
 * <ol>
 *     <li>{@link #send} — fire-and-forget: deliver an envelope to its
 *         {@link AgentMessageEnvelope#getTargetTopic() target topic} with no
 *         reply expected.</li>
 *     <li>{@link #request} — request-response: send a REQUEST envelope and
 *         return a {@link CompletableFuture} that completes with the responder's
 *         reply payload, or completes exceptionally with
 *         {@link java.util.concurrent.TimeoutException} if no reply arrives
 *         within the timeout. Multiple in-flight requests share a single reply
 *         topic subscription per requester; responses are demultiplexed by the
 *         envelope {@code correlationId}.</li>
 *     <li>{@link #registerHandler} — register an {@link IAgentMessageHandler}
 *         for a topic; returns a cancellable {@link IMessageSubscription}.</li>
 * </ol>
 *
 * <p>Implementations adapt Agent-domain handlers to the platform
 * {@code IMessageConsumer} contract internally, and explicitly suppress the
 * platform's auto ack-topic routing (see plan 166 Phase 2 Decision M2:
 * ack-topic suppression).
 */
public interface IAgentMessenger {

    /**
     * Fire-and-forget: deliver an envelope to its target topic. No reply is
     * expected. The envelope's {@link AgentMessageEnvelope#getKind() kind}
     * should typically be {@link AgentMessageKind#ASYNC ASYNC}.
     *
     * @param envelope the envelope to send (must not be null)
     */
    void send(AgentMessageEnvelope envelope);

    /**
     * Request-response: send a REQUEST envelope and return a future that
     * completes with the responder's reply payload. The reply is received on
     * the shared reply topic ({@code agent.{senderId}.reply}) and matched by
     * the envelope's {@code correlationId}.
     *
     * <p>If no reply arrives within {@code timeout}, the future completes
     * exceptionally with {@link java.util.concurrent.TimeoutException}.
     *
     * @param requestEnvelope the REQUEST envelope (must have kind REQUEST and a
     *                        non-null correlationId and senderId)
     * @param timeout         the maximum time to wait for a reply (must be
     *                        positive and non-null)
     * @return a future that completes with the response payload, or completes
     *         exceptionally on timeout
     */
    CompletableFuture<Object> request(AgentMessageEnvelope requestEnvelope, Duration timeout);

    /**
     * Register an {@link IAgentMessageHandler} for a topic. The handler
     * receives every {@link AgentMessageEnvelope} delivered to that topic.
     * When the handler returns a non-null value for a REQUEST envelope, the
     * messenger routes the response to the requester's reply topic.
     *
     * @param topic   the topic to subscribe to (must not be null/empty)
     * @param handler the Agent-domain handler (must not be null)
     * @return a cancellable subscription; call {@link IMessageSubscription#cancel()}
     *         to unregister
     */
    IMessageSubscription registerHandler(String topic, IAgentMessageHandler handler);
}
