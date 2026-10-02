package io.nop.ai.agent.engine;

import io.nop.api.core.time.CoreMetrics;
import java.util.Map;

public class AgentEvent {

    private final AgentEventType eventType;
    private final String sessionId;
    private final String agentName;
    private final long timestamp;
    private final Map<String, Object> payload;
    private final String error;
    /**
     * Plan 2290 (WI9): payload schema version for forward-compatible
     * consumption — subscribers encountering unknown keys or payload shapes
     * skip them when this version is newer than the version they understand
     * (ignorable contract, design 02-execution-model.md 事件契约节).
     */
    private final int schemaVersion;

    public static final int CURRENT_SCHEMA_VERSION = 1;

    public AgentEvent(AgentEventType eventType, String sessionId, String agentName,
                      Map<String, Object> payload, String error) {
        this(eventType, sessionId, agentName, payload, error, CURRENT_SCHEMA_VERSION);
    }

    public AgentEvent(AgentEventType eventType, String sessionId, String agentName,
                      Map<String, Object> payload, String error, int schemaVersion) {
        this.eventType = eventType;
        this.sessionId = sessionId;
        this.agentName = agentName;
        this.timestamp = CoreMetrics.currentTimeMillis();
        this.payload = payload;
        this.error = error;
        this.schemaVersion = schemaVersion;
    }

    public static AgentEvent create(AgentEventType type, String sessionId, String agentName,
                                    Map<String, Object> payload) {
        return new AgentEvent(type, sessionId, agentName, payload, null);
    }

    /**
     * Plan 2290 (WI9): typed read view over the raw payload map. Keys should
     * come from {@link AgentEventPayloads}; missing keys return null (never
     * throw), typed accessors convert or return null on type mismatch.
     */
    public TypedPayload typedPayload() {
        return new TypedPayload(payload);
    }

    /** Null-safe typed read view (WI9). */
    public static final class TypedPayload {
        private final Map<String, Object> payload;

        TypedPayload(Map<String, Object> payload) {
            this.payload = payload;
        }

        public String getString(String key) {
            Object v = payload == null ? null : payload.get(key);
            return v != null ? String.valueOf(v) : null;
        }

        public Integer getInteger(String key) {
            Object v = payload == null ? null : payload.get(key);
            return v instanceof Number ? ((Number) v).intValue() : null;
        }

        public Long getLong(String key) {
            Object v = payload == null ? null : payload.get(key);
            return v instanceof Number ? ((Number) v).longValue() : null;
        }

        public Boolean getBoolean(String key) {
            Object v = payload == null ? null : payload.get(key);
            return v instanceof Boolean ? (Boolean) v : null;
        }
    }

    public static AgentEvent createError(AgentEventType type, String sessionId, String agentName,
                                         String error) {
        return new AgentEvent(type, sessionId, agentName, null, error);
    }

    public AgentEventType getEventType() {
        return eventType;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getAgentName() {
        return agentName;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public String getError() {
        return error;
    }

    /** Plan 2290 (WI9): payload schema version (ignorable contract). */
    public int getSchemaVersion() {
        return schemaVersion;
    }
}
