package io.nop.ai.agent.usage;

import io.nop.api.core.annotations.data.DataBean;

/**
 * Snapshot of a single LLM call's usage data, produced at the ReAct loop's
 * token accumulation point and handed to {@link IUsageRecorder#record}.
 *
 * <p>Field population (plan 201 Phase 1 §字段来源裁定):
 * <ul>
 *   <li>{@code sessionId} — from the execution context</li>
 *   <li>{@code agentName} — from the agent model</li>
 *   <li>{@code requestId} — from the LLM response</li>
 *   <li>{@code aiProvider} / {@code aiModel} — from the routed
 *       {@code ChatOptions} returned by {@code IModelRouter.route}</li>
 *   <li>{@code promptTokens} / {@code completionTokens} — from the response
 *       usage (defaulted to 0 when absent)</li>
 *   <li>{@code responseTimestamp} — {@code System.currentTimeMillis()} at the
 *       accumulation point</li>
 *   <li>{@code responseDurationMs} — {@code null} at the agent runtime layer
 *       (LLM-call timing is the L2-18 recorder's responsibility)</li>
 *   <li>{@code modelId} — {@code null} at the agent runtime layer (the
 *       {@code NopAiModel} entity primary key is resolved by the L2-18
 *       recorder at persistence time)</li>
 * </ul>
 */
@DataBean
public class UsageRecord {

    private String sessionId;
    private String agentName;
    private String requestId;

    private String modelId;
    private String aiProvider;
    private String aiModel;

    private int promptTokens;
    private int completionTokens;
    private Long responseDurationMs;
    private long responseTimestamp;

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getAgentName() {
        return agentName;
    }

    public void setAgentName(String agentName) {
        this.agentName = agentName;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getModelId() {
        return modelId;
    }

    public void setModelId(String modelId) {
        this.modelId = modelId;
    }

    public String getAiProvider() {
        return aiProvider;
    }

    public void setAiProvider(String aiProvider) {
        this.aiProvider = aiProvider;
    }

    public String getAiModel() {
        return aiModel;
    }

    public void setAiModel(String aiModel) {
        this.aiModel = aiModel;
    }

    public int getPromptTokens() {
        return promptTokens;
    }

    public void setPromptTokens(int promptTokens) {
        this.promptTokens = promptTokens;
    }

    public int getCompletionTokens() {
        return completionTokens;
    }

    public void setCompletionTokens(int completionTokens) {
        this.completionTokens = completionTokens;
    }

    public Long getResponseDurationMs() {
        return responseDurationMs;
    }

    public void setResponseDurationMs(Long responseDurationMs) {
        this.responseDurationMs = responseDurationMs;
    }

    public long getResponseTimestamp() {
        return responseTimestamp;
    }

    public void setResponseTimestamp(long responseTimestamp) {
        this.responseTimestamp = responseTimestamp;
    }
}
