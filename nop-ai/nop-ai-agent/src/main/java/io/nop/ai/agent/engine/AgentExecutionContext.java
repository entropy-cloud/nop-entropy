package io.nop.ai.agent.engine;

import io.nop.ai.agent.model.AgentConstraintsModel;
import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.model.AgentModel;
import io.nop.ai.agent.model.AgentPlanModel;
import io.nop.ai.api.chat.messages.ChatMessage;
import io.nop.ai.core.model.ChatOptionsModel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AgentExecutionContext {

    private final AgentModel agentModel;
    private final List<ChatMessage> messages;
    private AgentPlanModel plan;
    private String sessionId;
    private ChatOptionsModel chatOptionsModel;
    private int currentIteration;
    private long tokensUsed;
    private AgentExecStatus status;
    private int maxIterations;
    private Map<String, Object> metadata;
    private String lastError;
    private long startTimeMs;

    public AgentExecutionContext(AgentModel agentModel) {
        this.agentModel = agentModel;
        this.messages = new ArrayList<>();
        this.status = AgentExecStatus.pending;
        this.maxIterations = 10;
        this.currentIteration = 0;
        this.tokensUsed = 0;
        this.metadata = new HashMap<>();
        this.startTimeMs = System.currentTimeMillis();
    }

    public static AgentExecutionContext create(AgentModel agentModel, String sessionId) {
        AgentExecutionContext ctx = new AgentExecutionContext(agentModel);

        if (sessionId != null) {
            ctx.setSessionId(sessionId);
        }

        if (agentModel != null) {
            AgentConstraintsModel constraints = agentModel.getConstraints();
            if (constraints != null && constraints.getMaxIterations() != null) {
                ctx.setMaxIterations(constraints.getMaxIterations());
            }

            ChatOptionsModel chatOptions = agentModel.getChatOptions();
            if (chatOptions != null) {
                ctx.setChatOptionsModel(chatOptions);
            }
        }

        return ctx;
    }

    public AgentModel getAgentModel() {
        return agentModel;
    }

    public List<ChatMessage> getMessages() {
        return messages;
    }

    public void addMessage(ChatMessage message) {
        this.messages.add(message);
    }

    public AgentPlanModel getPlan() {
        return plan;
    }

    public void setPlan(AgentPlanModel plan) {
        this.plan = plan;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public ChatOptionsModel getChatOptionsModel() {
        return chatOptionsModel;
    }

    public void setChatOptionsModel(ChatOptionsModel chatOptionsModel) {
        this.chatOptionsModel = chatOptionsModel;
    }

    public int getCurrentIteration() {
        return currentIteration;
    }

    public void setCurrentIteration(int currentIteration) {
        this.currentIteration = currentIteration;
    }

    public long getTokensUsed() {
        return tokensUsed;
    }

    public void setTokensUsed(long tokensUsed) {
        this.tokensUsed = tokensUsed;
    }

    public AgentExecStatus getStatus() {
        return status;
    }

    public void setStatus(AgentExecStatus status) {
        this.status = status;
    }

    public int getMaxIterations() {
        return maxIterations;
    }

    public void setMaxIterations(int maxIterations) {
        this.maxIterations = maxIterations;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    public long getStartTimeMs() {
        return startTimeMs;
    }

    public void setStartTimeMs(long startTimeMs) {
        this.startTimeMs = startTimeMs;
    }
}
