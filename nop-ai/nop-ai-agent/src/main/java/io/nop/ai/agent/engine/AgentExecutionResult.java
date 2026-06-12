package io.nop.ai.agent.engine;

import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.api.chat.messages.ChatMessage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AgentExecutionResult {

    private final AgentExecStatus status;
    private final String finalMessage;
    private final List<ChatMessage> messages;
    private final int totalIterations;
    private final long totalTokensUsed;
    private final long durationMs;
    private final String error;
    private final String sessionId;

    public AgentExecutionResult(AgentExecStatus status, String finalMessage,
                                List<ChatMessage> messages, int totalIterations,
                                long totalTokensUsed, long durationMs, String error) {
        this(status, finalMessage, messages, totalIterations, totalTokensUsed, durationMs, error, null);
    }

    public AgentExecutionResult(AgentExecStatus status, String finalMessage,
                                List<ChatMessage> messages, int totalIterations,
                                long totalTokensUsed, long durationMs, String error,
                                String sessionId) {
        this.status = status;
        this.finalMessage = finalMessage;
        this.messages = messages != null
                ? Collections.unmodifiableList(new ArrayList<>(messages))
                : Collections.emptyList();
        this.totalIterations = totalIterations;
        this.totalTokensUsed = totalTokensUsed;
        this.durationMs = durationMs;
        this.error = error;
        this.sessionId = sessionId;
    }

    public static AgentExecutionResult fromContext(AgentExecutionContext ctx) {
        long durationMs = System.currentTimeMillis() - ctx.getStartTimeMs();
        return new AgentExecutionResult(
                ctx.getStatus(),
                null,
                ctx.getMessages(),
                ctx.getCurrentIteration(),
                ctx.getTokensUsed(),
                durationMs,
                ctx.getLastError(),
                ctx.getSessionId()
        );
    }

    public AgentExecStatus getStatus() {
        return status;
    }

    public String getFinalMessage() {
        return finalMessage;
    }

    public List<ChatMessage> getMessages() {
        return messages;
    }

    public int getTotalIterations() {
        return totalIterations;
    }

    public long getTotalTokensUsed() {
        return totalTokensUsed;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public String getError() {
        return error;
    }

    public String getSessionId() {
        return sessionId;
    }
}
