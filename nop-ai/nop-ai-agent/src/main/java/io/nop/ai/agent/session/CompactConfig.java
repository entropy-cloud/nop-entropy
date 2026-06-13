package io.nop.ai.agent.session;

import java.util.Objects;

public class CompactConfig {

    private final int targetTokens;
    private final String strategy;
    private final boolean preserveSystemMessages;

    public CompactConfig(int targetTokens, String strategy, boolean preserveSystemMessages) {
        this.targetTokens = targetTokens;
        this.strategy = strategy;
        this.preserveSystemMessages = preserveSystemMessages;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CompactConfig that = (CompactConfig) o;
        return targetTokens == that.targetTokens
                && preserveSystemMessages == that.preserveSystemMessages
                && Objects.equals(strategy, that.strategy);
    }

    @Override
    public int hashCode() {
        return Objects.hash(targetTokens, strategy, preserveSystemMessages);
    }

    @Override
    public String toString() {
        return "CompactConfig{" +
                "targetTokens=" + targetTokens +
                ", strategy='" + strategy + '\'' +
                ", preserveSystemMessages=" + preserveSystemMessages +
                '}';
    }
}
