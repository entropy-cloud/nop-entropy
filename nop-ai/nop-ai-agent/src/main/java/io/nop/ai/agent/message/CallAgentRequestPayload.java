package io.nop.ai.agent.message;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable REQUEST payload for the async {@code call-agent} mailbox pathway
 * (plan 224 / L4-8-call-agent-async). Carried inside an
 * {@link AgentMessageEnvelope} with {@link AgentMessageKind#REQUEST REQUEST}
 * kind, delivered to {@link AgentMessageTopics#callAgentTopic() the call-agent
 * topic}, where the engine-registered handler executes the sub-agent and
 * returns a {@link CallAgentResponsePayload}.
 *
 * <p><b>Session-mode resolution</b> is performed entirely on the caller side
 * (in {@code CallAgentExecutor}) before this payload is built:
 * <ul>
 *     <li>{@code resolvedSessionId != null} → continue / fork mode (the
 *         caller already obtained the child session id via
 *         {@code engine.forkSession} for fork mode, or reused the supplied
 *         sessionId for continue mode).</li>
 *     <li>{@code resolvedSessionId == null} → create-new mode (the handler
 *         passes null to {@code engine.execute}, which generates a fresh
 *         session id).</li>
 * </ul>
 *
 * <p>All fields are String/Map/long so the payload is transport-serializable
 * for a future cross-process {@code DBMessageService} deployment (Non-Goal of
 * plan 224). The {@code parentConstraintMetadata} map carries the
 * {@code ParentPermissionConstraint} under its well-known metadata key.
 *
 * <p>Instances are immutable: the metadata map is defensively copied.
 */
public final class CallAgentRequestPayload {

    private final String targetAgentId;
    private final String input;
    private final String resolvedSessionId;
    private final Map<String, Object> parentConstraintMetadata;
    private final long timeoutMs;

    public CallAgentRequestPayload(String targetAgentId, String input, String resolvedSessionId,
                                   Map<String, Object> parentConstraintMetadata, long timeoutMs) {
        if (targetAgentId == null || targetAgentId.isEmpty()) {
            throw new IllegalArgumentException(
                    "CallAgentRequestPayload: targetAgentId must not be null or empty");
        }
        this.targetAgentId = targetAgentId;
        this.input = input != null ? input : "";
        this.resolvedSessionId = resolvedSessionId;
        this.parentConstraintMetadata = parentConstraintMetadata != null
                ? new HashMap<>(parentConstraintMetadata)
                : null;
        this.timeoutMs = timeoutMs;
    }

    public String getTargetAgentId() {
        return targetAgentId;
    }

    public String getInput() {
        return input;
    }

    public String getResolvedSessionId() {
        return resolvedSessionId;
    }

    public Map<String, Object> getParentConstraintMetadata() {
        return parentConstraintMetadata;
    }

    public long getTimeoutMs() {
        return timeoutMs;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CallAgentRequestPayload)) return false;
        CallAgentRequestPayload that = (CallAgentRequestPayload) o;
        return timeoutMs == that.timeoutMs
                && Objects.equals(targetAgentId, that.targetAgentId)
                && Objects.equals(input, that.input)
                && Objects.equals(resolvedSessionId, that.resolvedSessionId)
                && Objects.equals(parentConstraintMetadata, that.parentConstraintMetadata);
    }

    @Override
    public int hashCode() {
        return Objects.hash(targetAgentId, input, resolvedSessionId, parentConstraintMetadata, timeoutMs);
    }

    @Override
    public String toString() {
        return "CallAgentRequestPayload{targetAgentId='" + targetAgentId
                + "', input.length=" + input.length()
                + ", resolvedSessionId=" + resolvedSessionId
                + ", hasConstraint=" + (parentConstraintMetadata != null)
                + ", timeoutMs=" + timeoutMs + '}';
    }
}
