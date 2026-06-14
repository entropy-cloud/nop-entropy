package io.nop.ai.agent.message;

/**
 * Distinguishes the delivery semantics of an {@link AgentMessageEnvelope}.
 *
 * <ul>
 *     <li>{@link #REQUEST} — request-response: sender expects a reply on the
 *         shared reply topic, matched by {@code correlationId}.</li>
 *     <li>{@link #RESPONSE} — the reply payload for a prior REQUEST, carried on
 *         the shared reply topic.</li>
 *     <li>{@link #ASYNC} — fire-and-forget: no reply expected.</li>
 * </ul>
 */
public enum AgentMessageKind {
    REQUEST,
    RESPONSE,
    ASYNC
}
