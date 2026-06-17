package io.nop.ai.agent.message;
import io.nop.ai.agent.engine.NopAiAgentException;


/**
 * Topic naming conventions for Agent-domain inter-agent messaging, layered on
 * the platform {@code IMessageService} / {@code LocalMessageService}.
 *
 * <p>Agent-domain topics use an independent {@code agent.*} namespace. They do
 * <strong>not</strong> reuse the platform {@code MessageCoreConstants} prefixes
 * ({@code bro-} / {@code reply-} / {@code bat-}):
 * <ul>
 *     <li>The platform {@code bro-} prefix triggers special multi-fan-out in
 *         {@code LocalMessageService.getBroadcastTopics()}; Agent messages are
 *         point-to-point inbox delivery plus a shared reply topic, and do not
 *         rely on platform broadcast semantics.</li>
 *     <li>The {@code agent.*} prefix avoids collision with platform
 *         broadcast/reply/batch topics inside the same
 *         {@code ConcurrentHashMap<topic, ...>} namespace and allows
 *         independent introspection/statistics.</li>
 *     <li>Per {@code 01-architecture-baseline.md} §六 and actor-runtime §3.3
 *         the {@code agent.{id}.inbox} convention is normative.</li>
 * </ul>
 *
 * <p>If Agent-domain broadcast is needed, the caller fan-outs across multiple
 * inbox topics explicitly rather than relying on the platform {@code bro-}
 * prefix.
 */
public final class AgentMessageTopics {

    /** Namespace prefix shared by all Agent-domain topics. */
    public static final String NAMESPACE_PREFIX = "agent.";

    /** Infix used for broadcast topic names. */
    public static final String BROADCAST_INFIX = "broadcast.";

    /**
     * Engine-level topic for async {@code call-agent} REQUEST/RESPONSE routing
     * (plan 224 / L4-8-call-agent-async). When a functional messenger is wired,
     * the engine registers a single handler on this topic that executes the
     * requested sub-agent and returns the result as a RESPONSE payload. This is
     * the single source of the {@code agent.call-agent} literal — no other file
     * may hardcode the topic string.
     */
    public static final String CALL_AGENT_TOPIC = "agent.call-agent";

    private AgentMessageTopics() {
    }

    /**
     * Build the inbox topic for a session/agent: {@code agent.{sessionId}.inbox}.
     *
     * @param sessionId the recipient session or agent id (must not be null/empty)
     * @return the inbox topic name
     */
    public static String inboxTopic(String sessionId) {
        requireSessionId(sessionId);
        return NAMESPACE_PREFIX + sessionId + ".inbox";
    }

    /**
     * Build the shared reply topic for a session/agent:
     * {@code agent.{sessionId}.reply}.
     *
     * <p>One session has exactly one reply topic; multiple in-flight requests
     * share it and are demultiplexed by the envelope {@code correlationId}.
     * The correlationId is <strong>not</strong> embedded in the topic name.
     *
     * @param sessionId the requester session or agent id (must not be null/empty)
     * @return the reply topic name
     */
    public static String replyTopic(String sessionId) {
        requireSessionId(sessionId);
        return NAMESPACE_PREFIX + sessionId + ".reply";
    }

    /**
     * Build a broadcast topic for a scope: {@code agent.broadcast.{scope}}.
     *
     * <p>This is the Agent-domain fan-out topic name; the caller is responsible
     * for any fan-out across multiple inbox topics. It does not use the platform
     * {@code bro-} prefix.
     *
     * @param scope the broadcast scope (must not be null/empty)
     * @return the broadcast topic name
     */
    public static String broadcastTopic(String scope) {
        if (scope == null || scope.isEmpty()) {
            throw new NopAiAgentException("AgentMessageTopics.broadcastTopic: scope must not be null or empty");
        }
        return NAMESPACE_PREFIX + BROADCAST_INFIX + scope;
    }

    /**
     * Return the engine-level call-agent topic ({@value #CALL_AGENT_TOPIC}),
     * the routing target for async {@code call-agent} REQUEST envelopes when a
     * functional messenger is configured (plan 224). This is the canonical
     * accessor for the topic name — callers must use this method (or
     * {@link #CALL_AGENT_TOPIC}) rather than hardcoding the string.
     *
     * @return the call-agent topic name
     */
    public static String callAgentTopic() {
        return CALL_AGENT_TOPIC;
    }

    private static void requireSessionId(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            throw new NopAiAgentException(
                    "AgentMessageTopics: sessionId must not be null or empty");
        }
    }
}
