package io.nop.ai.agent.session;

import java.util.Objects;

public class CompactConfig {

    public static final int DEFAULT_MAX_RECENT_TOOL_RESULTS = 6;
    public static final int DEFAULT_TRUNCATION_THRESHOLD_CHARS = 8000;

    private final int targetTokens;
    private final String strategy;
    private final boolean preserveSystemMessages;
    private final int maxRecentToolResults;
    private final int truncationThresholdChars;

    public CompactConfig(int targetTokens, String strategy, boolean preserveSystemMessages) {
        this(targetTokens, strategy, preserveSystemMessages,
                DEFAULT_MAX_RECENT_TOOL_RESULTS, DEFAULT_TRUNCATION_THRESHOLD_CHARS);
    }

    public CompactConfig(int targetTokens, String strategy, boolean preserveSystemMessages,
                         int maxRecentToolResults, int truncationThresholdChars) {
        this.targetTokens = targetTokens;
        this.strategy = strategy;
        this.preserveSystemMessages = preserveSystemMessages;
        this.maxRecentToolResults = maxRecentToolResults;
        this.truncationThresholdChars = truncationThresholdChars;
    }

    public static CompactConfig defaults() {
        return new CompactConfig(0, null, true,
                DEFAULT_MAX_RECENT_TOOL_RESULTS, DEFAULT_TRUNCATION_THRESHOLD_CHARS);
    }

    public int getTargetTokens() {
        return targetTokens;
    }

    public String getStrategy() {
        return strategy;
    }

    public boolean isPreserveSystemMessages() {
        return preserveSystemMessages;
    }

    public int getMaxRecentToolResults() {
        return maxRecentToolResults;
    }

    public int getTruncationThresholdChars() {
        return truncationThresholdChars;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CompactConfig that = (CompactConfig) o;
        return targetTokens == that.targetTokens
                && preserveSystemMessages == that.preserveSystemMessages
                && maxRecentToolResults == that.maxRecentToolResults
                && truncationThresholdChars == that.truncationThresholdChars
                && Objects.equals(strategy, that.strategy);
    }

    @Override
    public int hashCode() {
        return Objects.hash(targetTokens, strategy, preserveSystemMessages,
                maxRecentToolResults, truncationThresholdChars);
    }

    @Override
    public String toString() {
        return "CompactConfig{" +
                "targetTokens=" + targetTokens +
                ", strategy='" + strategy + '\'' +
                ", preserveSystemMessages=" + preserveSystemMessages +
                ", maxRecentToolResults=" + maxRecentToolResults +
                ", truncationThresholdChars=" + truncationThresholdChars +
                '}';
    }
}
