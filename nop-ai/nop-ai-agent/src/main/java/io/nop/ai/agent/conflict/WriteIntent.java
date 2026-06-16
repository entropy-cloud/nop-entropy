package io.nop.ai.agent.conflict;

import java.util.Objects;

/**
 * Immutable record of a single agent session's intent to write to (or
 * otherwise mutate) a specific file path. Registered into an
 * {@link IWriteIntentRegistry} on the dispatch path so concurrent sessions
 * targeting the same file can be detected and resolved by an
 * {@link IConflictStrategy}.
 *
 * <p>The {@code filePath} is the <b>normalized absolute path</b> (produced
 * by {@code DefaultPathAccessChecker.normalizePathStatic(...)}), so two
 * intents on the same physical file — regardless of how the tool call
 * expressed the path (relative, tilde, trailing slash, etc.) — compare
 * equal by {@code filePath}.
 *
 * <p>Design {@code nop-ai-agent-multi-agent.md} §3.1 / §4.4.
 */
public final class WriteIntent {

    private final String sessionId;
    private final String agentName;
    private final String filePath;
    private final String operation;
    private final long timestamp;

    public WriteIntent(String sessionId, String agentName, String filePath,
                       String operation, long timestamp) {
        this.sessionId = sessionId;
        this.agentName = agentName;
        this.filePath = filePath;
        this.operation = operation;
        this.timestamp = timestamp;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getAgentName() {
        return agentName;
    }

    /**
     * @return the normalized absolute path of the file this intent targets;
     *         never null for a registered intent
     */
    public String getFilePath() {
        return filePath;
    }

    /**
     * @return the tool name that produced this intent (e.g.
     *         {@code "write-file"}, {@code "patch-file"}); used for audit
     *         and human-readable conflict reporting
     */
    public String getOperation() {
        return operation;
    }

    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WriteIntent that = (WriteIntent) o;
        return timestamp == that.timestamp
                && Objects.equals(sessionId, that.sessionId)
                && Objects.equals(agentName, that.agentName)
                && Objects.equals(filePath, that.filePath)
                && Objects.equals(operation, that.operation);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sessionId, agentName, filePath, operation, timestamp);
    }

    @Override
    public String toString() {
        return "WriteIntent{sessionId='" + sessionId + "', agentName='" + agentName
                + "', filePath='" + filePath + "', operation='" + operation
                + "', timestamp=" + timestamp + '}';
    }
}
