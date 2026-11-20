package io.nop.git.policy;

import java.util.List;

/**
 * Policy verdict: one of allow / deny / review (design 03 section 1), the
 * matched rule ids and the human-readable reason taken from the rule
 * configuration. Verdict order DENY &gt; REVIEW &gt; ALLOW is the pinned
 * precedence used while accumulating.
 */
public record GitVerdict(Effect effect, List<String> matchedRuleIds, String reason) {

    public enum Effect {
        ALLOW, DENY, REVIEW
    }

    public static GitVerdict allow() {
        return new GitVerdict(Effect.ALLOW, List.of(), null);
    }

    public static GitVerdict deny(String ruleId, String reason) {
        return new GitVerdict(Effect.DENY, List.of(ruleId), reason);
    }

    public GitVerdict reviewAccumulated(String ruleId, String reason) {
        return new GitVerdict(Effect.REVIEW, append(ruleId), reason);
    }

    public GitVerdict withExtraRule(String ruleId) {
        return new GitVerdict(effect, append(ruleId), reason);
    }

    private List<String> append(String ruleId) {
        var ids = new java.util.ArrayList<String>(matchedRuleIds);
        ids.add(ruleId);
        return List.copyOf(ids);
    }
}
