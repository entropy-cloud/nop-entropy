package io.nop.ai.agent.security;

import java.util.Objects;

public class AuditEvent {

    private final String sessionId;
    private final String agentName;
    private final String actorId;
    private final String toolName;
    private final AuditDecision decision;
    private final String reason;
    private final String matchedRule;
    private final String path;
    private final long timestamp;

    public AuditEvent(String sessionId, String agentName, String actorId,
                      String toolName, AuditDecision decision, String reason,
                      String matchedRule, String path, long timestamp) {
        this.sessionId = sessionId;
        this.agentName = agentName;
        this.actorId = actorId;
        this.toolName = toolName;
        this.decision = decision;
        this.reason = reason;
        this.matchedRule = matchedRule;
        this.path = path;
        this.timestamp = timestamp;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getAgentName() {
        return agentName;
    }

    public String getActorId() {
        return actorId;
    }

    public String getToolName() {
        return toolName;
    }

    public AuditDecision getDecision() {
        return decision;
    }

    public String getReason() {
        return reason;
    }

    public String getMatchedRule() {
        return matchedRule;
    }

    public String getPath() {
        return path;
    }

    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AuditEvent that = (AuditEvent) o;
        return timestamp == that.timestamp
                && Objects.equals(sessionId, that.sessionId)
                && Objects.equals(agentName, that.agentName)
                && Objects.equals(actorId, that.actorId)
                && Objects.equals(toolName, that.toolName)
                && decision == that.decision
                && Objects.equals(reason, that.reason)
                && Objects.equals(matchedRule, that.matchedRule)
                && Objects.equals(path, that.path);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sessionId, agentName, actorId, toolName, decision, reason, matchedRule, path, timestamp);
    }

    @Override
    public String toString() {
        return "AuditEvent{" +
                "sessionId='" + sessionId + '\'' +
                ", agentName='" + agentName + '\'' +
                ", actorId='" + actorId + '\'' +
                ", toolName='" + toolName + '\'' +
                ", decision=" + decision +
                ", reason='" + reason + '\'' +
                ", matchedRule='" + matchedRule + '\'' +
                ", path='" + path + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
}
