package io.nop.ai.agent.security;

import java.util.Objects;

public class ToolAccessResult {

    private final boolean allowed;
    private final String reason;
    private final String matchedRule;

    private ToolAccessResult(boolean allowed, String reason, String matchedRule) {
        this.allowed = allowed;
        this.reason = reason;
        this.matchedRule = matchedRule;
    }

    public static ToolAccessResult allow() {
        return new ToolAccessResult(true, null, null);
    }

    public static ToolAccessResult deny(String reason) {
        return new ToolAccessResult(false, reason, null);
    }

    public static ToolAccessResult denyByRule(String ruleName, String toolName) {
        return new ToolAccessResult(false, "Hardcoded deny: " + toolName, ruleName);
    }

    public boolean isAllowed() {
        return allowed;
    }

    public String getReason() {
        return reason;
    }

    public String getMatchedRule() {
        return matchedRule;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ToolAccessResult that = (ToolAccessResult) o;
        return allowed == that.allowed
                && Objects.equals(reason, that.reason)
                && Objects.equals(matchedRule, that.matchedRule);
    }

    @Override
    public int hashCode() {
        return Objects.hash(allowed, reason, matchedRule);
    }

    @Override
    public String toString() {
        return "ToolAccessResult{" +
                "allowed=" + allowed +
                ", reason='" + reason + '\'' +
                ", matchedRule='" + matchedRule + '\'' +
                '}';
    }
}
