package io.nop.lint.core.engine;

/**
 * Analyzer capability levels a rule may declare in its {@code requires}
 * field (design 11 §1: the engine aggregates the requirement per profile).
 * The set is intentionally open for later roadmap items to extend (items
 * 14/15/17/25/26 hang further capabilities such as xscript, suppression,
 * fix generation onto the profiles); unknown requirement tokens resolve to
 * nothing, so a rule requiring an unshipped capability is profile-skipped,
 * never silently executed.
 *
 * <p>Vocabulary adjudication (roadmap item 31, plan 2026-09-24-0900-1
 * Decision 1): the deep-only analyzers use the design 06 §4.6 layer names
 * {@link #L3} (dataflow: definition-use chains + constant propagation) and
 * {@link #L4} (semantic analysis: method signatures + type hierarchy),
 * plus the standalone {@link #SCOPE} and {@link #METRICS} analyzers of
 * design 11 §2. The lowercase {@code dataflow} / {@code tsc} tokens from
 * design 11 §1's example are deliberately not introduced: {@code tsc} is a
 * backend implementation detail of L2 (rules declare {@code L2}), and
 * {@code dataflow} would duplicate L3 under a second name. Matching is
 * case-insensitive, so {@code requires: "L3"} and {@code requires: "l3"}
 * are the same token.</p>
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
    L2,

    /**
     * Dataflow analysis (definition-use chains + constant propagation,
     * design 06 §4.6 L3 layer). Deep-profile only (design 11 §2); runs only
     * when the run wires a live availability probe for it (roadmap item 31),
     * otherwise the affected rules degrade. Rules that consume it land with
     * roadmap item 36.
     */
    L3,

    /**
     * Semantic analysis (method signatures + type hierarchy, design 06 §4.6
     * L4 layer). Deep-profile only; same live-probe contract as {@link #L3}.
     */
    L4,

    /**
     * Scope analysis (design 05 §2, roadmap item 33). Deep-profile only;
     * served through the run's {@code ScopeResolver} (the metrics-style
     * provider path — no {@link AnalyzerAvailability} probe), degrading
     * when no live resolver serves a named file.
     */
    SCOPE,

    /**
     * Complexity metrics (design 01 §6, roadmap item 32). Deep-profile only;
     * same live-probe contract as {@link #L3}.
     */
    METRICS;

    /**
     * True for the analyzer-backed capabilities that only the {@code deep}
     * profile declares in its ceiling (roadmap item 31): these never ride
     * the {@code TypeResolver} path — each needs its own availability probe.
     */
    public boolean isDeepAnalyzer() {
        return this == L3 || this == L4 || this == SCOPE || this == METRICS;
    }

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
