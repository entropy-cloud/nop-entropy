package io.nop.lint.core.engine;

/**
 * Analyzer capability levels a rule may declare in its {@code requires}
 * field (design 11 §1: the engine aggregates the requirement per profile).
 * The set is intentionally open for later roadmap items to extend (items
 * 14/15/17/25/26 hang further capabilities such as xscript, suppression,
 * fix generation onto the profiles); unknown requirement tokens resolve to
 * nothing, so a rule requiring an unshipped capability is profile-skipped,
 * never silently executed.
 */
public enum LintCapability {

    /**
     * Declaration-level type extraction; available in every v1 profile.
     */
    L1,

    /**
     * Symbol-solver-level resolution (JavaParser/tsc bridges); not provided
     * by any v1 profile (design 11 §8 Phase 1).
     */
    L2;

    /**
     * Resolves a {@code requires} token to a capability; null when the token
     * is not a known capability (the engine treats that as unsatisfiable).
     */
    public static LintCapability byToken(String token) {
        if (token == null) {
            return null;
        }
        String trimmed = token.trim();
        for (LintCapability capability : values()) {
            if (capability.name().equalsIgnoreCase(trimmed)) {
                return capability;
            }
        }
        return null;
    }
}
