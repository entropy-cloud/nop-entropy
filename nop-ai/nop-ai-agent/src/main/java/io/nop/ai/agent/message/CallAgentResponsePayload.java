package io.nop.ai.agent.message;

import java.util.Objects;

/**
 * Immutable RESPONSE payload for the async {@code call-agent} mailbox pathway
 * (plan 224 / L4-8-call-agent-async). Returned by the engine-registered
 * handler on the {@link AgentMessageTopics#callAgentTopic() call-agent topic}
 * and routed back to the requester's reply topic by the messenger adapter.
 *
 * <p>Field contract:
 * <ul>
 *     <li>{@code status} — {@code "success"} when the sub-agent reached
 *         {@link io.nop.ai.agent.model.AgentExecStatus#completed completed};
 *         {@code "failure"} otherwise (including handler-caught exceptions
 *         and sub-agent non-completed terminal states).</li>
 *     <li>{@code sessionId} — the sub-agent session id (the engine result's
 *         sessionId, or the resolvedSessionId when the result has none);
 *         may be {@code null} on failure before session creation.</li>
 *     <li>{@code finalMessage} — the sub-agent's last assistant message
 *         content; empty string when there is no assistant message.</li>
 *     <li>{@code error} — descriptive error for {@code status="failure"};
 *         {@code null} for {@code status="success"}.</li>
 * </ul>
 *
 * <p>Instances are immutable: all fields are set at construction time and
 * exposed only via accessors.
 */
public final class CallAgentResponsePayload {

    private final String status;
    private final String sessionId;
    private final String finalMessage;
    private final String error;

    public CallAgentResponsePayload(String status, String sessionId, String finalMessage, String error) {
        if (status == null || status.isEmpty()) {
            throw new IllegalArgumentException(
                    "CallAgentResponsePayload: status must not be null or empty");
        }
        this.status = status;
        this.sessionId = sessionId;
        this.finalMessage = finalMessage != null ? finalMessage : "";
        this.error = error;
    }

    public String getStatus() {
        return status;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getFinalMessage() {
        return finalMessage;
    }

    public String getError() {
        return error;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CallAgentResponsePayload)) return false;
        CallAgentResponsePayload that = (CallAgentResponsePayload) o;
        return Objects.equals(status, that.status)
                && Objects.equals(sessionId, that.sessionId)
                && Objects.equals(finalMessage, that.finalMessage)
                && Objects.equals(error, that.error);
    }

    @Override
    public int hashCode() {
        return Objects.hash(status, sessionId, finalMessage, error);
    }

    @Override
    public String toString() {
        return "CallAgentResponsePayload{status='" + status
                + "', sessionId=" + sessionId
                + ", finalMessage.length=" + finalMessage.length()
                + ", error=" + error + '}';
    }
}
