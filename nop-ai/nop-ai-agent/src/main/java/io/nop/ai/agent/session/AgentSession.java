package io.nop.ai.agent.session;

import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.api.chat.messages.ChatMessage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AgentSession {

    private final String sessionId;
    private final String agentName;
    private final List<ChatMessage> messages;
    private long totalTokensUsed;
    private int totalIterations;
    private final long createdAt;
    private long updatedAt;
    private AgentExecStatus status;
    private Map<String, Object> metadata;
    private String parentSessionId;
    private String planId;
    private Long compactedAt;

    private AgentSession(String sessionId, String agentName) {
        this.sessionId = sessionId;
        this.agentName = agentName;
        this.messages = new ArrayList<>();
        this.totalTokensUsed = 0;
        this.totalIterations = 0;
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = this.createdAt;
        this.status = AgentExecStatus.pending;
        this.metadata = new HashMap<>();
    }

    public static AgentSession create(String sessionId, String agentName) {
        return new AgentSession(sessionId, agentName);
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getAgentName() {
        return agentName;
    }

    public List<ChatMessage> getMessages() {
        return Collections.unmodifiableList(new ArrayList<>(messages));
    }

    public void appendMessages(List<ChatMessage> newMessages) {
        if (newMessages != null) {
            this.messages.addAll(newMessages);
        }
        this.updatedAt = System.currentTimeMillis();
    }

    public long getTotalTokensUsed() {
        return totalTokensUsed;
    }

    public void addTokensUsed(long tokens) {
        this.totalTokensUsed += tokens;
    }

    public int getTotalIterations() {
        return totalIterations;
    }

    public void addIterations(int iterations) {
        this.totalIterations += iterations;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public AgentExecStatus getStatus() {
        return status;
    }

    public void setStatus(AgentExecStatus status) {
        this.status = status;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata != null ? new HashMap<>(metadata) : new HashMap<>();
    }

    public int getMessageCount() {
        return messages.size();
    }

    public void touch() {
        this.updatedAt = System.currentTimeMillis();
    }

    public String getParentSessionId() {
        return parentSessionId;
    }

    public void setParentSessionId(String parentSessionId) {
        this.parentSessionId = parentSessionId;
    }

    public String getPlanId() {
        return planId;
    }

    public void setPlanId(String planId) {
        this.planId = planId;
    }

    public Long getCompactedAt() {
        return compactedAt;
    }

    public void setCompactedAt(Long compactedAt) {
        this.compactedAt = compactedAt;
    }

    public void markCompacted() {
        this.compactedAt = System.currentTimeMillis();
        this.touch();
    }
}
