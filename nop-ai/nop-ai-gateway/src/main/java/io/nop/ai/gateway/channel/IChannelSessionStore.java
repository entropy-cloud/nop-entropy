package io.nop.ai.gateway.channel;

/**
 * Persistence boundary for the external-channel↔engine-session mapping
 * (design §6). Each external channel conversation ({@code channelType}
 * + {@code channelId}) maps to exactly one agent-engine {@code sessionId}.
 *
 * <p><b>sessionId provenance</b>: the {@code sessionId} stored here always
 * comes from the agent engine's {@code AgentMessageAck.sessionId}. A
 * connector obtains it by calling
 * {@link io.nop.ai.agent.engine.IAgentEngine#sendMessage} and then hands it to
 * {@link #saveMapping}; this store never generates a sessionId. This keeps the
 * engine the single authority for session identity.
 *
 * <p>The mapping flow (design §6):
 * <ol>
 *   <li>on inbound message: {@link #findByChannel} lookup</li>
 *   <li>hit → reuse the returned {@link ChannelSession#getSessionId()} and
 *       {@link #updateLastActive}</li>
 *   <li>miss → connector calls {@code IAgentEngine.sendMessage}, takes the
 *       ack's sessionId, then {@link #saveMapping}</li>
 * </ol>
 *
 * <p>Implementations are injected into connectors via Nop IoC ({@code @Inject}
 * on a non-private field); they are NOT part of
 * {@link ChannelConnectorContext}.
 */
public interface IChannelSessionStore {

    /**
     * Look up the engine session mapped to an external channel conversation.
     *
     * @param channelType channel type identifier (e.g. {@code "feishu"}); never null
     * @param channelId  channel-native conversation id (e.g. Feishu {@code chat_id}); never null
     * @return the mapped session, or {@code null} when no mapping exists
     *         (a precise not-found state — never an exception, never a silent
     *         empty placeholder)
     */
    ChannelSession findByChannel(String channelType, String channelId);

    /**
     * Persist a new mapping. Called by the connector after it has received a
     * fresh {@code sessionId} from {@code IAgentEngine.sendMessage}'s ack.
     * The store does NOT generate the {@code sessionId}; it only persists what
     * the connector passes in.
     *
     * @param channelType channel type identifier; never null
     * @param channelId  channel-native conversation id; never null
     * @param sessionId  engine session id from the agent ack; never null
     * @param agentName  agent the session is routed to; never null
     */
    void saveMapping(String channelType, String channelId, String sessionId, String agentName);

    /**
     * Refresh the channel's last-active timestamp (e.g. on each inbound
     * message). No-op semantics are NOT allowed: if no mapping exists this
     * must surface the miss rather than silently succeeding.
     *
     * @param channelType channel type identifier; never null
     * @param channelId  channel-native conversation id; never null
     */
    void updateLastActive(String channelType, String channelId);
}
