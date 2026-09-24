package io.nop.lint.core.engine;

import io.nop.lint.core.NopLintException;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Execution statistics of one lint run (design 03 §2.3): rule accounting
 * (loaded / executed / skipped by profile / kind-filtered), the diagnostic
 * count, the ids of the profile-skipped rules, and the xscript resource
 * counters (design 07 §3 v1 口径: run-level executed / failed / capped
 * match counts plus the ids of rules disabled by consecutive script
 * failures). Immutable; instances are produced only through
 * {@link Builder}, and every counter is observable — a rule leaves the
 * loaded count through exactly one of the executed, skipped-by-profile,
 * rule-boundary degrade, budget-breaker abort, or kind-filtered exits,
 * never by silent drop (roadmap item 31 added the two budget-driven
 * exits).
 *
 * <p>Budget observability (design 11 §5, roadmap item 31): the ordered
 * {@code degradedAnalyzers} record lists what the engaged ladder closed
 * (only analyzers that were actually open, capability names plus the
 * {@code xscript} and {@code fix} level ids); {@code xscriptBudgetExceeded}
 * counts the matches whose xscript the fast profile's exhausted time slice
 * skipped (design 11 §5 fast 预算闭合 — a pure run-level count, same
 * attribution shape as {@code xscriptTimedOutMatches}), with the affected
 * rule ids listed alongside; {@code fixesDegraded} counts the fix
 * generations the ladder's fix closure skipped; {@code
 * breakerAbortedRuleIds} lists the rules the pattern-stage circuit breaker
 * aborted — a non-empty list marks the file degraded.</p>
 */
public final class LintStats {

    private final int rulesLoaded;
    private final int rulesExecuted;
    private final int rulesSkippedByProfile;
    private final int rulesDegraded;
    private final int rulesKindFiltered;
    private final int constraintFilteredMatches;
    private final int diagnostics;
    private final int suppressedDiagnostics;
    private final int xscriptMatchesExecuted;
    private final int xscriptFailedMatches;
    private final int xscriptCappedMatches;
    private final int xscriptTimedOutMatches;
    private final int xscriptBudgetExceededMatches;
    private final int fixesDegraded;
    private final List<String> skippedRuleIds;
    private final List<String> degradedRuleIds;
    private final List<String> disabledRuleIds;
    private final List<String> degradedAnalyzers;
    private final List<String> xscriptBudgetExceededRuleIds;
    private final List<String> breakerAbortedRuleIds;

    private LintStats(Builder builder) {
        this.rulesLoaded = builder.rulesLoaded;
        this.rulesExecuted = builder.rulesExecuted;
        this.rulesSkippedByProfile = builder.rulesSkippedByProfile;
        this.rulesDegraded = builder.rulesDegraded;
        this.rulesKindFiltered = builder.rulesKindFiltered;
        this.constraintFilteredMatches = builder.constraintFilteredMatches;
        this.diagnostics = builder.diagnostics;
        this.suppressedDiagnostics = builder.suppressedDiagnostics;
        this.xscriptMatchesExecuted = builder.xscriptMatchesExecuted;
        this.xscriptFailedMatches = builder.xscriptFailedMatches;
        this.xscriptCappedMatches = builder.xscriptCappedMatches;
        this.xscriptTimedOutMatches = builder.xscriptTimedOutMatches;
        this.xscriptBudgetExceededMatches = builder.xscriptBudgetExceededMatches;
        this.fixesDegraded = builder.fixesDegraded;
        this.skippedRuleIds = List.copyOf(builder.skippedRuleIds);
        this.degradedRuleIds = List.copyOf(builder.degradedRuleIds);
        this.disabledRuleIds = List.copyOf(builder.disabledRuleIds);
        this.degradedAnalyzers = List.copyOf(builder.degradedAnalyzers);
        this.xscriptBudgetExceededRuleIds = List.copyOf(builder.xscriptBudgetExceededRuleIds);
        this.breakerAbortedRuleIds = List.copyOf(builder.breakerAbortedRuleIds);
    }

    /**
     * The number of rule models the run started from.
     */
    public int getRulesLoaded() {
        return rulesLoaded;
    }

    /**
     * The number of rules that actually reached their matcher (compiled and
     * not kind-filtered).
     */
    public int getRulesExecuted() {
        return rulesExecuted;
    }

    /**
     * The number of rules whose {@code requires} exceeded the profile's
     * capability set; never executed, never silently dropped.
     */
    public int getRulesSkippedByProfile() {
        return rulesSkippedByProfile;
    }

    /**
     * The number of rules whose target kinds are disjoint from the kinds
     * present in the file; skipped before any match call.
     */
    public int getRulesKindFiltered() {
        return rulesKindFiltered;
    }

    /**
     * The number of matches whose rule carries constraints and failed at
     * least one of them (roadmap item 22): a constraint-filtered match
     * produces no diagnostic, and this counter keeps the removal observable
     * instead of silent.
     */
    public int getConstraintFilteredMatches() {
        return constraintFilteredMatches;
    }

    /**
     * The number of diagnostics the run emitted (design 03 §2.3): the
     * surviving candidates plus the engine-generated suppression
     * meta-diagnostics — exactly what the result carries.
     */
    public int getDiagnostics() {
        return diagnostics;
    }

    /**
     * The number of candidate diagnostics removed by the suppression pass
     * (design 09): suppressed diagnostics never enter the result and
     * produce no fix; this counter keeps the removal observable instead of
     * silent.
     */
    public int getSuppressedDiagnostics() {
        return suppressedDiagnostics;
    }

    /**
     * The ids of the profile-skipped rules, in rule input order (observable
     * counterpart of {@link #getRulesSkippedByProfile()}).
     */
    public List<String> getSkippedRuleIds() {
        return skippedRuleIds;
    }

    /**
     * The number of rules whose L2 requirement the run could not serve even
     * though the profile's capability ceiling declares it (roadmap item 20,
     * design 11 §5 degrade ladder level 2): resolver missing or unavailable
     * at the gate, or a type query that failed mid-evaluation. Degraded
     * rules produce no diagnostics and are never answered from a lower
     * level — this counter keeps the removal observable instead of silent.
     */
    public int getRulesDegraded() {
        return rulesDegraded;
    }

    /**
     * The ids of the degraded rules, in degrade order (observable
     * counterpart of {@link #getRulesDegraded()}).
     */
    public List<String> getDegradedRuleIds() {
        return degradedRuleIds;
    }

    /**
     * The number of matches whose xscript body actually ran (design 07 §3
     * observability).
     */
    public int getXscriptMatchesExecuted() {
        return xscriptMatchesExecuted;
    }

    /**
     * The number of matches skipped because their xscript body failed; the
     * rule, file, and run continue.
     */
    public int getXscriptFailedMatches() {
        return xscriptFailedMatches;
    }

    /**
     * The number of matches aborted because their script exceeded the
     * per-match diagnostic cap; the diagnostics reported before the cap
     * stand.
     */
    public int getXscriptCappedMatches() {
        return xscriptCappedMatches;
    }

    /**
     * The number of matches aborted because their script exceeded its
     * deadline budget (design 07 §3); each is treated as non-matching and
     * never feeds the consecutive-failure disable path.
     */
    public int getXscriptTimedOutMatches() {
        return xscriptTimedOutMatches;
    }

    /**
     * The ids of xscript rules disabled after too many consecutive script
     * failures, in disablement order (observable counterpart of the
     * disable rule, design 07 §3).
     */
    public List<String> getDisabledRuleIds() {
        return disabledRuleIds;
    }

    /**
     * The matches whose xscript body the fast profile's exhausted file time
     * slice skipped (design 11 §5 fast 预算闭合, roadmap item 31): each
     * still reports its pattern-layer result. Pure run-level count — the
     * affected rules are visible in {@link #getXscriptBudgetExceededRuleIds()}.
     */
    public int getXscriptBudgetExceededMatches() {
        return xscriptBudgetExceededMatches;
    }

    /**
     * The ids of rules whose matches the exhausted xscript slice skipped,
     * first-occurrence order (the "标记 degraded" visibility of the slice
     * skip; deliberately not the L2-semantics {@link #getDegradedRuleIds()},
     * because these matches do emit pattern-layer diagnostics).
     */
    public List<String> getXscriptBudgetExceededRuleIds() {
        return xscriptBudgetExceededRuleIds;
    }

    /**
     * The degrade-ladder closure record (design 11 §5, roadmap item 31): in
     * ladder order, the analyzers the engaged budget actually closed — deep
     * capabilities by name (only those a live probe served), {@code L2}
     * when a live resolver was cut off, then {@code xscript} (deadline
     * tightening) and {@code fix} (generation closure). Empty when the
     * budget was never exhausted.
     */
    public List<String> getDegradedAnalyzers() {
        return degradedAnalyzers;
    }

    /**
     * The fix generations the ladder's fix closure skipped (design 11 §5
     * ladder level 5, roadmap item 31): each skipped generation's diagnostic
     * still reports, without its fix carrier.
     */
    public int getFixesDegraded() {
        return fixesDegraded;
    }

    /**
     * The rules the pattern-stage circuit breaker aborted (design 11 §5
     * "运行期软预算", roadmap item 31), in abort order. A non-empty list
     * marks the file degraded — matching is the one stage that cannot
     * degrade, so the breaker's only defense is to stop the remaining rules.
     */
    public List<String> getBreakerAbortedRuleIds() {
        return breakerAbortedRuleIds;
    }

    @Override
    public String toString() {
        return "LintStats[loaded=" + rulesLoaded + ", executed=" + rulesExecuted
                + ", skippedByProfile=" + rulesSkippedByProfile
                + ", degraded=" + rulesDegraded
                + ", kindFiltered=" + rulesKindFiltered
                + ", constraintFilteredMatches=" + constraintFilteredMatches
                + ", diagnostics=" + diagnostics
                + ", suppressedDiagnostics=" + suppressedDiagnostics
                + ", xscriptMatches=" + xscriptMatchesExecuted
                + ", xscriptFailed=" + xscriptFailedMatches
                + ", xscriptCapped=" + xscriptCappedMatches
                + ", xscriptTimedOut=" + xscriptTimedOutMatches
                + ", xscriptBudgetExceeded=" + xscriptBudgetExceededMatches
                + ", fixesDegraded=" + fixesDegraded
                + ", degradedAnalyzers=" + degradedAnalyzers
                + ", breakerAbortedRuleIds=" + breakerAbortedRuleIds
                + ", disabledRuleIds=" + disabledRuleIds + "]";
    }

    /**
     * A fresh accumulator with all counters at zero.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Mutable accumulator; not thread-safe. One builder per lint run.
     */
    public static final class Builder {

        private int rulesLoaded;
        private int rulesExecuted;
        private int rulesSkippedByProfile;
        private int rulesDegraded;
        private int rulesKindFiltered;
        private int constraintFilteredMatches;
        private int diagnostics;
        private int suppressedDiagnostics;
        private int xscriptMatchesExecuted;
        private int xscriptFailedMatches;
        private int xscriptCappedMatches;
        private int xscriptTimedOutMatches;
        private int xscriptBudgetExceededMatches;
        private int fixesDegraded;
        private final List<String> skippedRuleIds = new ArrayList<>();
        private final List<String> degradedRuleIds = new ArrayList<>();
        private final List<String> disabledRuleIds = new ArrayList<>();
        private final List<String> degradedAnalyzers = new ArrayList<>();
        private final List<String> xscriptBudgetExceededRuleIds = new ArrayList<>();
        private final List<String> breakerAbortedRuleIds = new ArrayList<>();

        /**
         * Records the number of rule models the run started from.
         */
        public Builder rulesLoaded(int count) {
            this.rulesLoaded = count;
            return this;
        }

        public Builder incRulesExecuted() {
            this.rulesExecuted++;
            return this;
        }

        /**
         * Counts one profile-skipped rule and records its id (no silent
         * skip: the id is part of the stats contract).
         */
        public Builder incRulesSkippedByProfile(String ruleId) {
            this.rulesSkippedByProfile++;
            this.skippedRuleIds.add(Objects.requireNonNull(ruleId, "ruleId must not be null"));
            return this;
        }

        /**
         * Counts one degraded rule and records its id (roadmap item 20, no
         * silent degrade: the id is part of the stats contract).
         */
        public Builder incRulesDegraded(String ruleId) {
            this.rulesDegraded++;
            this.degradedRuleIds.add(Objects.requireNonNull(ruleId, "ruleId must not be null"));
            return this;
        }

        public Builder incRulesKindFiltered() {
            this.rulesKindFiltered++;
            return this;
        }

        /**
         * Counts one match removed by constraint evaluation (roadmap item
         * 22, no silent drop).
         */
        public Builder incConstraintFilteredMatches() {
            this.constraintFilteredMatches++;
            return this;
        }

        public Builder incDiagnostics(int count) {
            this.diagnostics += count;
            return this;
        }

        /**
         * Reconciles the emitted diagnostics count after the suppression
         * tail (design 03 §1.1 pipeline order): the emitted total —
         * surviving candidates plus engine-generated meta-diagnostics —
         * replaces the raw produced count, so {@link #getDiagnostics()}
         * always equals the result size the caller receives.
         */
        public Builder diagnostics(int total) {
            this.diagnostics = total;
            return this;
        }

        /**
         * Counts the candidate diagnostics the suppression pass removed.
         */
        public Builder incSuppressedDiagnostics(int count) {
            this.suppressedDiagnostics += count;
            return this;
        }

        /**
         * Counts one xscript match execution.
         */
        public Builder incXscriptMatchesExecuted() {
            this.xscriptMatchesExecuted++;
            return this;
        }

        /**
         * Counts one xscript match skipped for a script failure.
         */
        public Builder incXscriptFailedMatches() {
            this.xscriptFailedMatches++;
            return this;
        }

        /**
         * Counts one xscript match aborted at the diagnostic cap.
         */
        public Builder incXscriptCappedMatches() {
            this.xscriptCappedMatches++;
            return this;
        }

        /**
         * Counts one xscript match aborted at its deadline budget.
         */
        public Builder incXscriptTimedOutMatches() {
            this.xscriptTimedOutMatches++;
            return this;
        }

        /**
         * Records an xscript rule disabled for consecutive script failures
         * (no silent disable: the id is part of the stats contract).
         */
        public Builder addDisabledRuleId(String ruleId) {
            this.disabledRuleIds.add(Objects.requireNonNull(ruleId, "ruleId must not be null"));
            return this;
        }

        /**
         * Counts one match whose xscript the exhausted fast slice skipped
         * and records the owning rule's id once (design 11 §5 fast 预算闭合,
         * no silent skip).
         */
        public Builder incXscriptBudgetExceeded(String ruleId) {
            this.xscriptBudgetExceededMatches++;
            String id = Objects.requireNonNull(ruleId, "ruleId must not be null");
            if (!this.xscriptBudgetExceededRuleIds.contains(id)) {
                this.xscriptBudgetExceededRuleIds.add(id);
            }
            return this;
        }

        /**
         * Records one degrade-ladder closure step (roadmap item 31): the
         * ladder order is the call order, so callers engage the ladder
         * top-down. Unknown or duplicate ids would corrupt the record and
         * fail here.
         */
        public Builder addDegradedAnalyzer(String analyzerId) {
            String id = Objects.requireNonNull(analyzerId, "analyzerId must not be null");
            if (this.degradedAnalyzers.contains(id)) {
                throw new NopLintException("degrade ladder recorded '" + id
                        + "' twice in one run (invariant broken)");
            }
            this.degradedAnalyzers.add(id);
            return this;
        }

        /**
         * Counts fix generations skipped by the ladder's fix closure
         * (roadmap item 31, no silent fix drop).
         */
        public Builder incFixesDegraded(int count) {
            this.fixesDegraded += count;
            return this;
        }

        /**
         * Records one rule the pattern-stage circuit breaker aborted
         * (design 11 §5 "运行期软预算", no silent abort).
         */
        public Builder addBreakerAbortedRuleId(String ruleId) {
            this.breakerAbortedRuleIds.add(Objects.requireNonNull(ruleId, "ruleId must not be null"));
            return this;
        }

        public LintStats build() {
            return new LintStats(this);
        }
    }
}
