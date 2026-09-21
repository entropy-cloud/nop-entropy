package io.nop.lint.core.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Execution statistics of one lint run (design 03 §2.3): rule accounting
 * (loaded / executed / skipped by profile / kind-filtered), the diagnostic
 * count, and the ids of the profile-skipped rules. Immutable; instances are
 * produced only through {@link Builder}, and every counter is observable —
 * a rule leaves the loaded count through exactly one of the executed,
 * skipped-by-profile, or kind-filtered exits, never by silent drop.
 */
public final class LintStats {

    private final int rulesLoaded;
    private final int rulesExecuted;
    private final int rulesSkippedByProfile;
    private final int rulesKindFiltered;
    private final int diagnostics;
    private final List<String> skippedRuleIds;

    private LintStats(Builder builder) {
        this.rulesLoaded = builder.rulesLoaded;
        this.rulesExecuted = builder.rulesExecuted;
        this.rulesSkippedByProfile = builder.rulesSkippedByProfile;
        this.rulesKindFiltered = builder.rulesKindFiltered;
        this.diagnostics = builder.diagnostics;
        this.skippedRuleIds = List.copyOf(builder.skippedRuleIds);
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
     * The number of diagnostics the run produced.
     */
    public int getDiagnostics() {
        return diagnostics;
    }

    /**
     * The ids of the profile-skipped rules, in rule input order (observable
     * counterpart of {@link #getRulesSkippedByProfile()}).
     */
    public List<String> getSkippedRuleIds() {
        return skippedRuleIds;
    }

    @Override
    public String toString() {
        return "LintStats[loaded=" + rulesLoaded + ", executed=" + rulesExecuted
                + ", skippedByProfile=" + rulesSkippedByProfile
                + ", kindFiltered=" + rulesKindFiltered
                + ", diagnostics=" + diagnostics + "]";
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
        private int rulesKindFiltered;
        private int diagnostics;
        private final List<String> skippedRuleIds = new ArrayList<>();

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

        public Builder incRulesKindFiltered() {
            this.rulesKindFiltered++;
            return this;
        }

        public Builder incDiagnostics(int count) {
            this.diagnostics += count;
            return this;
        }

        public LintStats build() {
            return new LintStats(this);
        }
    }
}
