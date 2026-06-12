package io.nop.ai.agent.security;

import java.util.Objects;

public class PathAccessResult {

    private final boolean allowed;
    private final String reason;
    private final String matchedRule;

    private PathAccessResult(boolean allowed, String reason, String matchedRule) {
        this.allowed = allowed;
        this.reason = reason;
        this.matchedRule = matchedRule;
    }

    public static PathAccessResult allow() {
        return new PathAccessResult(true, null, null);
    }

    public static PathAccessResult deny(String reason) {
        return new PathAccessResult(false, reason, null);
    }

    public static PathAccessResult denyByRule(String ruleName, String path) {
        return new PathAccessResult(false, "Path denied by rule '" + ruleName + "': " + path, ruleName);
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
        PathAccessResult that = (PathAccessResult) o;
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
        return "PathAccessResult{" +
                "allowed=" + allowed +
                ", reason='" + reason + '\'' +
                ", matchedRule='" + matchedRule + '\'' +
                '}';
    }
}
