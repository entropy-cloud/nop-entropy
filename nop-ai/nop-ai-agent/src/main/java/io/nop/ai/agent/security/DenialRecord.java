package io.nop.ai.agent.security;

import java.util.Objects;

/**
 * The structured record of a single per-session denial, produced at every
 * dispatch-path deny checkpoint (Layer 1 / 2 / 3) and handed to
 * {@link IDenialLedger#recordDenial}. Follows the same immutable
 * value-object pattern as {@link AuditEvent} and {@link ApprovalDecision}:
 * factory construction, all-field equals/hashCode, no mutators.
 *
 * <p>A {@code DenialRecord} is scoped to the {@link IDenialLedger} record
 * surface only. It is intentionally distinct from the {@code DenialResult}
 * structured envelope (design §6.3), which belongs to L3-7
 * ({@code IPostDenialGuard}) and carries additional post-denial governance
 * fields (suggestedNextStep, actionFingerprint, retryable).
 */
public final class DenialRecord {

    private final String sessionId;
    private final String toolName;
    private final DenialLayerSource layerSource;
    private final String reason;
    private final String matchedRule;
    private final long timestamp;

    private DenialRecord(String sessionId, String toolName,
                         DenialLayerSource layerSource, String reason,
                         String matchedRule, long timestamp) {
        this.sessionId = sessionId;
        this.toolName = toolName;
        this.layerSource = layerSource;
        this.reason = reason;
        this.matchedRule = matchedRule;
        this.timestamp = timestamp;
    }

    /**
     * Create a denial record capturing the structured context of a single
     * dispatch-path denial.
     *
     * @param sessionId   the session identifier; may be null (anonymous)
     * @param toolName    the tool name / operation category that was denied;
     *                    may be null
     * @param layerSource the dispatch-path layer that produced the denial
     *                    (Layer 1 / 2 / 3); never null
     * @param reason      the human-readable denial reason; may be null
     * @param matchedRule the matched rule identifier (e.g.
     *                    {@code "layer3_approval_gate"}); may be null
     * @param timestamp   the denial timestamp (epoch millis)
     */
    public static DenialRecord of(String sessionId, String toolName,
                                  DenialLayerSource layerSource, String reason,
                                  String matchedRule, long timestamp) {
        if (layerSource == null) {
            throw new IllegalArgumentException(
                    "DenialRecord.layerSource must not be null");
        }
        return new DenialRecord(sessionId, toolName, layerSource, reason, matchedRule, timestamp);
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getToolName() {
        return toolName;
    }

    public DenialLayerSource getLayerSource() {
        return layerSource;
    }

    public String getReason() {
        return reason;
    }

    public String getMatchedRule() {
        return matchedRule;
    }

    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DenialRecord that = (DenialRecord) o;
        return timestamp == that.timestamp
                && Objects.equals(sessionId, that.sessionId)
                && Objects.equals(toolName, that.toolName)
                && layerSource == that.layerSource
                && Objects.equals(reason, that.reason)
                && Objects.equals(matchedRule, that.matchedRule);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sessionId, toolName, layerSource, reason, matchedRule, timestamp);
    }

    @Override
    public String toString() {
        return "DenialRecord{" +
                "sessionId='" + sessionId + '\'' +
                ", toolName='" + toolName + '\'' +
                ", layerSource=" + layerSource +
                ", reason='" + reason + '\'' +
                ", matchedRule='" + matchedRule + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
}
