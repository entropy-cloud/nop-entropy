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
     * Symbol-solver-level resolution (JavaParser/tsc bridges). The
     * {@code standard} profile declares it in its ceiling (roadmap item 20);
     * it runs only when the run wires a live {@code TypeResolver} —
     * otherwise the affected rules degrade (counted, logged), never run on
     * L1 answers. {@code fast} never provides it.
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
