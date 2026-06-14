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

    /**
     * Build a denial carrying BOTH an explicit descriptive {@code reason} and a
     * {@code matchedRule} token. Used by the parent path-permission constraint
     * wrapper to identify the parent agent (via {@code reason}) and to tag the
     * denial with a stable rule token (e.g.
     * {@code "parent_path_permission_constraint"}) for audit categorization.
     * The existing {@link #deny(String)} leaves {@code matchedRule} null, and
     * {@link #denyByRule(String, String)} auto-formats the reason with no slot
     * for the parent agent name — neither satisfies both requirements.
     */
    public static PathAccessResult deny(String reason, String matchedRule) {
        return new PathAccessResult(false, reason, matchedRule);
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
