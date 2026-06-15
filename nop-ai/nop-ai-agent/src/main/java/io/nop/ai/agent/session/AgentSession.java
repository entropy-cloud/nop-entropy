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

    private AgentSession(String sessionId, String agentName, long createdAt) {
        this.sessionId = sessionId;
        this.agentName = agentName;
        this.messages = new ArrayList<>();
        this.totalTokensUsed = 0;
        this.totalIterations = 0;
        this.createdAt = createdAt;
        this.updatedAt = this.createdAt;
        this.status = AgentExecStatus.pending;
        this.metadata = new HashMap<>();
    }

    private AgentSession(String sessionId, String agentName) {
        this(sessionId, agentName, System.currentTimeMillis());
    }

    public static AgentSession create(String sessionId, String agentName) {
        return new AgentSession(sessionId, agentName);
    }

    /**
     * Package-private restore factory used by the persistence reader
     * ({@link SessionFileReader}) to reconstruct a session from its persisted
     * state with the original {@code createdAt} / {@code updatedAt} timestamps
     * preserved. The public API continues to use {@link #create} for new
     * sessions; this factory is only for crash/restart recovery (plan 183
     * Phase 1).
     *
     * @param sessionId  the session id; never null
     * @param agentName  the agent name; never null
     * @param createdAt  the original creation timestamp (epoch millis)
     * @param updatedAt  the original last-update timestamp (epoch millis)
     * @return a new session with the supplied identity + timestamps
     */
    static AgentSession restore(String sessionId, String agentName, long createdAt, long updatedAt) {
        AgentSession session = new AgentSession(sessionId, agentName, createdAt);
        session.updatedAt = updatedAt;
        return session;
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

    /**
     * Full-sync replace: clear the current message list and write the supplied
     * list in its place. Idempotent — repeated calls with the same input leave
     * the session in the same terminal state.
     * <p>
     * Used by {@code DefaultAgentEngine.doExecute}/{@code resumeSession}/
     * {@code restoreSession} post-execution sync and by the
     * {@code ReActAgentExecutor} intra-execution persistence path so that both
     * sync paths produce the same terminal state
     * ({@code session.messages == ctx.getMessages()}) without duplicate
     * appends. With the old append-delta sync, intra-execution and
     * post-execution sync would each append the same batch and produce
     * duplicates; the replace-semantic unifies them (plan 183 Phase 1
     * coordination decision).
     *
     * @param newMessages the complete message list to set; null is treated as
     *                    an empty list (clears all messages). The messages are
     *                    copied into the session's internal list, so subsequent
     *                    mutations to the input list do not affect the session.
     */
    public void replaceMessages(List<ChatMessage> newMessages) {
        this.messages.clear();
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

    /**
     * Package-private: restore the {@code updatedAt} timestamp from the
     * persisted value. Used by the persistence reader ({@link SessionFileReader})
     * after mutations (e.g. {@code appendMessages}) that touch
     * {@code updatedAt} to the current time, so the persisted timestamp
     * survives the round-trip.
     */
    void restoreUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
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
