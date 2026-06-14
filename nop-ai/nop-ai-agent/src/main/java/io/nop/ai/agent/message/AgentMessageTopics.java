package io.nop.ai.agent.message;

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
            throw new IllegalArgumentException("AgentMessageTopics.broadcastTopic: scope must not be null or empty");
        }
        return NAMESPACE_PREFIX + BROADCAST_INFIX + scope;
    }

    private static void requireSessionId(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            throw new IllegalArgumentException(
                    "AgentMessageTopics: sessionId must not be null or empty");
        }
    }
}
