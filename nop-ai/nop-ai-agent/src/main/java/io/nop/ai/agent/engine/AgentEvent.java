package io.nop.ai.agent.engine;

import java.util.Map;

public class AgentEvent {

    private final AgentEventType eventType;
    private final String sessionId;
    private final String agentName;
    private final long timestamp;
    private final Map<String, Object> payload;
    private final String error;

    public AgentEvent(AgentEventType eventType, String sessionId, String agentName,
                      Map<String, Object> payload, String error) {
        this.eventType = eventType;
        this.sessionId = sessionId;
        this.agentName = agentName;
        this.timestamp = System.currentTimeMillis();
        this.payload = payload;
        this.error = error;
    }

    public static AgentEvent create(AgentEventType type, String sessionId, String agentName,
                                    Map<String, Object> payload) {
        return new AgentEvent(type, sessionId, agentName, payload, null);
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
}
