package io.nop.ai.agent.compact;

import io.nop.ai.agent.engine.AgentExecutionContext;
import io.nop.ai.agent.engine.ITokenEstimator;
import io.nop.ai.agent.session.CompactConfig;
import io.nop.ai.api.chat.messages.ChatMessage;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class CompactionContext {

    private final List<ChatMessage> messages;
    private final CompactConfig compactConfig;
    private final String sessionId;
    private final String agentName;
    private final AgentExecutionContext executionContext;
    private final ITokenEstimator tokenEstimator;

    public CompactionContext(List<ChatMessage> messages, CompactConfig compactConfig,
                             String sessionId, String agentName,
                             AgentExecutionContext executionContext) {
        this(messages, compactConfig, sessionId, agentName, executionContext, null);
    }

    public CompactionContext(List<ChatMessage> messages, CompactConfig compactConfig,
                             String sessionId, String agentName,
                             AgentExecutionContext executionContext,
                             ITokenEstimator tokenEstimator) {
        this.messages = List.copyOf(Objects.requireNonNull(messages, "messages must not be null"));
        this.compactConfig = compactConfig;
        this.sessionId = sessionId;
        this.agentName = agentName;
        this.executionContext = executionContext;
        this.tokenEstimator = tokenEstimator;
    }

    public List<ChatMessage> getMessages() {
        return messages;
    }

    public CompactConfig getCompactConfig() {
        return compactConfig;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getAgentName() {
        return agentName;
    }

    public AgentExecutionContext getExecutionContext() {
        return executionContext;
    }

    public ITokenEstimator getTokenEstimator() {
        return tokenEstimator;
    }
}
