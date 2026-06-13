package io.nop.ai.agent.session;

import java.util.Objects;

public class CompactConfig {

    public static final int DEFAULT_MAX_RECENT_TOOL_RESULTS = 6;
    public static final int DEFAULT_TRUNCATION_THRESHOLD_CHARS = 8000;
    public static final double DEFAULT_TRIGGER_TOKEN_PERCENT = 0.8;
    public static final double DEFAULT_FORCED_STOP_PERCENT = 0.9;
    public static final double DEFAULT_KEEP_TAIL_PERCENT = 0.15;
    public static final int DEFAULT_TRIGGER_MAX_MESSAGES = 30;
    public static final String DEFAULT_COMPRESSION_MODEL = "";

    private final int targetTokens;
    private final String strategy;
    private final boolean preserveSystemMessages;
    private final int maxRecentToolResults;
    private final int truncationThresholdChars;
    private final double triggerTokenPercent;
    private final double forcedStopPercent;
    private final double keepTailPercent;
    private final int triggerMaxMessages;
    private final String compressionModel;

    public CompactConfig(int targetTokens, String strategy, boolean preserveSystemMessages) {
        this(targetTokens, strategy, preserveSystemMessages,
                DEFAULT_MAX_RECENT_TOOL_RESULTS, DEFAULT_TRUNCATION_THRESHOLD_CHARS);
    }

    public CompactConfig(int targetTokens, String strategy, boolean preserveSystemMessages,
                         int maxRecentToolResults, int truncationThresholdChars) {
        this(targetTokens, strategy, preserveSystemMessages,
                maxRecentToolResults, truncationThresholdChars,
                DEFAULT_TRIGGER_TOKEN_PERCENT, DEFAULT_FORCED_STOP_PERCENT,
                DEFAULT_KEEP_TAIL_PERCENT, DEFAULT_TRIGGER_MAX_MESSAGES, DEFAULT_COMPRESSION_MODEL);
    }

    public CompactConfig(int targetTokens, String strategy, boolean preserveSystemMessages,
                         int maxRecentToolResults, int truncationThresholdChars,
                         double triggerTokenPercent, double forcedStopPercent,
                         double keepTailPercent, int triggerMaxMessages, String compressionModel) {
        this.targetTokens = targetTokens;
        this.strategy = strategy;
        this.preserveSystemMessages = preserveSystemMessages;
        this.maxRecentToolResults = maxRecentToolResults;
        this.truncationThresholdChars = truncationThresholdChars;
        this.triggerTokenPercent = triggerTokenPercent;
        this.forcedStopPercent = forcedStopPercent;
        this.keepTailPercent = keepTailPercent;
        this.triggerMaxMessages = triggerMaxMessages;
        this.compressionModel = compressionModel != null ? compressionModel : DEFAULT_COMPRESSION_MODEL;
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

    public double getTriggerTokenPercent() {
        return triggerTokenPercent;
    }

    public double getForcedStopPercent() {
        return forcedStopPercent;
    }

    public double getKeepTailPercent() {
        return keepTailPercent;
    }

    public int getTriggerMaxMessages() {
        return triggerMaxMessages;
    }

    public String getCompressionModel() {
        return compressionModel;
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
                && Double.compare(that.triggerTokenPercent, triggerTokenPercent) == 0
                && Double.compare(that.forcedStopPercent, forcedStopPercent) == 0
                && Double.compare(that.keepTailPercent, keepTailPercent) == 0
                && triggerMaxMessages == that.triggerMaxMessages
                && Objects.equals(strategy, that.strategy)
                && Objects.equals(compressionModel, that.compressionModel);
    }

    @Override
    public int hashCode() {
        return Objects.hash(targetTokens, strategy, preserveSystemMessages,
                maxRecentToolResults, truncationThresholdChars,
                triggerTokenPercent, forcedStopPercent, keepTailPercent,
                triggerMaxMessages, compressionModel);
    }

    @Override
    public String toString() {
        return "CompactConfig{" +
                "targetTokens=" + targetTokens +
                ", strategy='" + strategy + '\'' +
                ", preserveSystemMessages=" + preserveSystemMessages +
                ", maxRecentToolResults=" + maxRecentToolResults +
                ", truncationThresholdChars=" + truncationThresholdChars +
                ", triggerTokenPercent=" + triggerTokenPercent +
                ", forcedStopPercent=" + forcedStopPercent +
                ", keepTailPercent=" + keepTailPercent +
                ", triggerMaxMessages=" + triggerMaxMessages +
                ", compressionModel='" + compressionModel + '\'' +
                '}';
    }
}
