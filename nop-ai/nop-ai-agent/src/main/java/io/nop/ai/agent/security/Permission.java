package io.nop.ai.agent.security;

import java.util.Objects;

public class Permission {

    private final boolean allowed;
    private final String reason;
    private final String matchedRuleId;

    private Permission(boolean allowed, String reason, String matchedRuleId) {
        this.allowed = allowed;
        this.reason = reason;
        this.matchedRuleId = matchedRuleId;
    }

    public static Permission allow() {
        return new Permission(true, null, null);
    }

    public static Permission deny(String reason) {
        return new Permission(false, reason, null);
    }

    public static Permission deny(String reason, String ruleId) {
        return new Permission(false, reason, ruleId);
    }

    public boolean isAllowed() {
        return allowed;
    }

    public String getReason() {
        return reason;
    }

    public String getMatchedRuleId() {
        return matchedRuleId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Permission that = (Permission) o;
        return allowed == that.allowed
                && Objects.equals(reason, that.reason)
                && Objects.equals(matchedRuleId, that.matchedRuleId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(allowed, reason, matchedRuleId);
    }

    @Override
    public String toString() {
        return "Permission{" +
                "allowed=" + allowed +
                ", reason='" + reason + '\'' +
                ", matchedRuleId='" + matchedRuleId + '\'' +
                '}';
    }
}
