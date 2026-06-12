package io.nop.ai.agent.engine;

public class AgentMessageAck {

    private final String sessionId;
    private final String status;

    public AgentMessageAck(String sessionId, String status) {
        this.sessionId = sessionId;
        this.status = status;
    }

    public AgentMessageAck(String sessionId) {
        this(sessionId, "accepted");
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getStatus() {
        return status;
    }
}
