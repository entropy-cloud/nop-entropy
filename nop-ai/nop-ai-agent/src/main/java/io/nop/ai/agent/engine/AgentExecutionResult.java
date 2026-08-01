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
    private final String bailReason;

    public AgentExecutionResult(AgentExecStatus status, String finalMessage,
                                List<ChatMessage> messages, int totalIterations,
                                long totalTokensUsed, long durationMs, String error) {
        this(status, finalMessage, messages, totalIterations, totalTokensUsed, durationMs, error, null, null);
    }

    public AgentExecutionResult(AgentExecStatus status, String finalMessage,
                                List<ChatMessage> messages, int totalIterations,
                                long totalTokensUsed, long durationMs, String error,
                                String sessionId) {
        this(status, finalMessage, messages, totalIterations, totalTokensUsed, durationMs, error, sessionId, null);
    }

    /**
     * W5-3 (BAIL): full constructor including the POST_CALL bail reason.
     *
     * @param bailReason the POST_CALL bail reason, or {@code null} when
     *                  POST_CALL did not bail (normal path). Callers check
     *                  {@link #getBailReason()} {@code != null} to detect a
     *                  guardrail-blocked final response.
     */
    public AgentExecutionResult(AgentExecStatus status, String finalMessage,
                                List<ChatMessage> messages, int totalIterations,
                                long totalTokensUsed, long durationMs, String error,
                                String sessionId, String bailReason) {
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
        this.bailReason = bailReason;
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
                ctx.getSessionId(),
                ctx.getBailReason()
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

    /**
     * W5-3 (BAIL): the POST_CALL bail reason. Non-null when a POST_CALL
     * middleware returned {@code BailResult} (final response guardrail-blocked).
     * Streaming caveat: already-emitted REASONING_CHUNK chunks cannot be
     * revoked — this field marks the result for audit/caller decision only.
     *
     * @return the bail reason, or {@code null} when POST_CALL did not bail
     */
    public String getBailReason() {
        return bailReason;
    }
}
