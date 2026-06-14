package io.nop.ai.agent.completion;

import java.util.Objects;

public final class CompletionRuleConfig {

    public static final int DEFAULT_MIN_RESPONSE_LENGTH = 10;
    public static final double DEFAULT_ESCALATION_RATIO = 0.9;
    public static final String DEFAULT_CONTINUATION_MESSAGE =
            "Your previous response was empty or too short. Please provide your full answer or continue working on the task.";
    public static final String DEFAULT_ESCALATION_REASON_TEMPLATE =
            "Iteration budget near exhaustion: currentIteration=%d, maxIterations=%d, escalationRatio=%s. " +
                    "Human review required before force-exit.";

    private final int minResponseLength;
    private final double escalationRatio;
    private final String continuationMessage;
    private final String escalationReasonTemplate;

    public CompletionRuleConfig() {
        this(DEFAULT_MIN_RESPONSE_LENGTH, DEFAULT_ESCALATION_RATIO,
                DEFAULT_CONTINUATION_MESSAGE, DEFAULT_ESCALATION_REASON_TEMPLATE);
    }

    public CompletionRuleConfig(int minResponseLength, double escalationRatio,
                                String continuationMessage, String escalationReasonTemplate) {
        if (minResponseLength < 0) {
            throw new IllegalArgumentException("minResponseLength must not be negative: " + minResponseLength);
        }
        if (Double.isNaN(escalationRatio) || escalationRatio < 0.0 || escalationRatio > 1.0) {
            throw new IllegalArgumentException(
                    "escalationRatio must be in [0.0, 1.0] range, got: " + escalationRatio);
        }
        this.minResponseLength = minResponseLength;
        this.escalationRatio = escalationRatio;
        this.continuationMessage = continuationMessage != null ? continuationMessage : DEFAULT_CONTINUATION_MESSAGE;
        this.escalationReasonTemplate = escalationReasonTemplate != null
                ? escalationReasonTemplate
                : DEFAULT_ESCALATION_REASON_TEMPLATE;
    }

    public static CompletionRuleConfig defaults() {
        return new CompletionRuleConfig();
    }

    public int getMinResponseLength() {
        return minResponseLength;
    }

    public double getEscalationRatio() {
        return escalationRatio;
    }

    public String getContinuationMessage() {
        return continuationMessage;
    }

    public String getEscalationReasonTemplate() {
        return escalationReasonTemplate;
    }

    public String formatEscalationReason(int currentIteration, int maxIterations) {
        return String.format(escalationReasonTemplate, currentIteration, maxIterations, escalationRatio);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CompletionRuleConfig that = (CompletionRuleConfig) o;
        return minResponseLength == that.minResponseLength
                && Double.compare(that.escalationRatio, escalationRatio) == 0
                && Objects.equals(continuationMessage, that.continuationMessage)
                && Objects.equals(escalationReasonTemplate, that.escalationReasonTemplate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(minResponseLength, escalationRatio, continuationMessage, escalationReasonTemplate);
    }

    @Override
    public String toString() {
        return "CompletionRuleConfig{" +
                "minResponseLength=" + minResponseLength +
                ", escalationRatio=" + escalationRatio +
                ", continuationMessage='" + continuationMessage + '\'' +
                ", escalationReasonTemplate='" + escalationReasonTemplate + '\'' +
                '}';
    }
}
