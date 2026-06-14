package io.nop.ai.agent.message;

/**
 * Agent-domain message handler — the behavioral contract for consuming
 * {@link AgentMessageEnvelope} messages. A handler receives an envelope and
 * may return a response object.
 *
 * <p>This is an Agent-domain handler, deliberately decoupled from the platform
 * {@code IMessageConsumer}. Returning a non-null value means "this is the
 * response for this request" — the messenger adapter routes it to the
 * requester's reply topic and suppresses the platform's auto ack-topic routing
 * (see plan 166 Phase 2 Decision: ack-topic suppression). Returning {@code null}
 * means "no response" (fire-and-forget was sufficient, or no reply applicable).
 *
 * <p>Functional interface so it can be expressed as a lambda or method reference.
 */
@FunctionalInterface
public interface IAgentMessageHandler {

    /**
     * Process an incoming Agent message envelope.
     *
     * @param envelope the received envelope (never null)
     * @return a non-null response payload for a REQUEST, or {@code null} if no
     *         response is applicable (the messenger adapter ignores the return
     *         value for RESPONSE/ASYNC kinds)
     */
    Object onMessage(AgentMessageEnvelope envelope);
}
