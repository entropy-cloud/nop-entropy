package io.nop.ai.gateway.channel;

import java.sql.Timestamp;

/**
 * Read model returned by {@link IChannelSessionStore#findByChannel}. A
 * disconnected snapshot of an external-channel-to-engine-session mapping.
 *
 * <p>Immutable. The {@code sessionId} carried here always originates from the
 * agent engine's {@code AgentMessageAck} (written via
 * {@link IChannelSessionStore#saveMapping}); the store never invents one.
 */
public class ChannelSession {

    private final String channelType;
    private final String channelId;
    private final String sessionId;
    private final String agentName;
    private final Timestamp createTime;
    private final Timestamp lastActiveAt;

    public ChannelSession(String channelType, String channelId, String sessionId,
                          String agentName, Timestamp createTime, Timestamp lastActiveAt) {
        this.channelType = channelType;
        this.channelId = channelId;
        this.sessionId = sessionId;
        this.agentName = agentName;
        this.createTime = createTime;
        this.lastActiveAt = lastActiveAt;
    }

    public String getChannelType() {
        return channelType;
    }

    public String getChannelId() {
        return channelId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getAgentName() {
        return agentName;
    }

    public Timestamp getCreateTime() {
        return createTime;
    }

    public Timestamp getLastActiveAt() {
        return lastActiveAt;
    }
}
