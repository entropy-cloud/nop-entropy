package io.nop.ai.agent.engine;

import io.nop.ai.agent.security.ChannelKind;
import io.nop.ai.agent.security.Principal;

import java.util.HashMap;
import java.util.Map;

public class AgentMessageRequest {

    private final String agentName;
    private final String userMessage;
    private final String sessionId;
    private final Map<String, Object> metadata;
    private final ChannelKind channelKind;
    private final Principal principal;
    /**
     * Plan 2288 (WI7): caller-supplied stable idempotency key for external
     * input redelivery dedup (session-level). Null/blank = no dedup — the
     * request behaves exactly as before this field existed. The channel
     * connector / orchestrator owns key stability (e.g. Feishu message_id).
     */
    private final String idempotencyKey;

    public AgentMessageRequest(String agentName, String userMessage, String sessionId,
                               Map<String, Object> metadata, ChannelKind channelKind, Principal principal) {
        this(agentName, userMessage, sessionId, metadata, channelKind, principal, null);
    }

    public AgentMessageRequest(String agentName, String userMessage, String sessionId,
                               Map<String, Object> metadata, ChannelKind channelKind, Principal principal,
                               String idempotencyKey) {
        this.agentName = agentName;
        this.userMessage = userMessage;
        this.sessionId = sessionId;
        this.metadata = metadata != null ? new HashMap<>(metadata) : new HashMap<>();
        this.channelKind = channelKind;
        this.principal = principal;
        this.idempotencyKey = idempotencyKey;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public AgentMessageRequest(String agentName, String userMessage, String sessionId,
                               Map<String, Object> metadata) {
        this(agentName, userMessage, sessionId, metadata, null, null);
    }

    public AgentMessageRequest(String agentName, String userMessage) {
        this(agentName, userMessage, null, null, null, null);
    }

    public String getAgentName() {
        return agentName;
    }

    public String getUserMessage() {
        return userMessage;
    }

    public String getSessionId() {
        return sessionId;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public ChannelKind getChannelKind() {
        return channelKind;
    }

    public Principal getPrincipal() {
        return principal;
    }
}
