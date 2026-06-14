package io.nop.ai.agent.message;

import java.util.Objects;

/**
 * Immutable inter-agent message envelope routed over the platform
 * {@code IMessageService}. It carries Agent-domain routing metadata
 * (sender, target topic, correlation id, kind) plus an opaque payload.
 *
 * <p>This is an Agent-domain envelope layered on top of the platform message
 * service — it is deliberately decoupled from {@code IMessageConsumer} /
 * {@code TopicMessage} so that Agent handlers express Agent semantics, not
 * platform plumbing (see plan 166 Phase 1 Decision: handler contract shape).
 *
 * <p>Instances are immutable: all fields are set at construction time and
 * exposed only via accessors.
 */
public final class AgentMessageEnvelope {

    private final String senderId;
    private final String targetTopic;
    private final String correlationId;
    private final AgentMessageKind kind;
    private final Object payload;
    private final long timestamp;

    public AgentMessageEnvelope(String senderId, String targetTopic, String correlationId,
                                AgentMessageKind kind, Object payload) {
        this(senderId, targetTopic, correlationId, kind, payload, System.currentTimeMillis());
    }

    public AgentMessageEnvelope(String senderId, String targetTopic, String correlationId,
                                AgentMessageKind kind, Object payload, long timestamp) {
        if (kind == null) {
            throw new IllegalArgumentException("AgentMessageEnvelope: kind must not be null");
        }
        this.senderId = senderId;
        this.targetTopic = targetTopic;
        this.correlationId = correlationId;
        this.kind = kind;
        this.payload = payload;
        this.timestamp = timestamp;
    }

    public String getSenderId() {
        return senderId;
    }

    public String getTargetTopic() {
        return targetTopic;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public AgentMessageKind getKind() {
        return kind;
    }

    public Object getPayload() {
        return payload;
    }

    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AgentMessageEnvelope)) return false;
        AgentMessageEnvelope that = (AgentMessageEnvelope) o;
        return timestamp == that.timestamp
                && Objects.equals(senderId, that.senderId)
                && Objects.equals(targetTopic, that.targetTopic)
                && Objects.equals(correlationId, that.correlationId)
                && kind == that.kind
                && Objects.equals(payload, that.payload);
    }

    @Override
    public int hashCode() {
        return Objects.hash(senderId, targetTopic, correlationId, kind, payload, timestamp);
    }

    @Override
    public String toString() {
        return "AgentMessageEnvelope{senderId='" + senderId + "', targetTopic='" + targetTopic
                + "', correlationId='" + correlationId + "', kind=" + kind
                + ", timestamp=" + timestamp + ", payload=" + payload + '}';
    }
}
