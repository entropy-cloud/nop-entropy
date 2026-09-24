package io.nop.lint.core.cli;

import io.nop.lint.core.engine.Diagnostic;
import io.nop.lint.core.engine.LintResult;
import io.nop.lint.core.engine.LintStats;
import io.nop.lint.core.fix.FixApplier;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Aggregate accounting of one {@code nop-lint check} run over all files
 * (design 03 §2.4 增注, 2026-09-22). Per-file engine runs are summed into
 * one observable summary: the per-file diagnostic severities, the explicit
 * skipped-file counts, and the {@link LintStats} key counters — the
 * "no silent skip" observability contract carries over to the console:
 * {@code skippedByProfile} (with rule ids), {@code suppressedDiagnostics},
 * and {@code disabledRuleIds} are all rendered.
 *
 * <p>Counter semantics: {@code rulesLoaded} is the size of the loaded rule
 * set (the same set applies to every file, so it is reported once);
 * {@code rulesExecuted} / {@code rulesKindFiltered} /
 * {@code rulesSkippedByProfile} and the xscript counters are sums over the
 * per-file engine runs; the id sets are unions over the runs. Severity
 * totals use the xdef severity enum ({@code error/warning/info/hint});
 * any other severity value lands in {@code other} so nothing vanishes.</p>
 */
public final class RunSummary {

    private int filesScanned;
    private final SkippedFiles skipped;
    private int errorCount;
    private int warningCount;
    private int infoCount;
    private int hintCount;
    private int otherCount;

    private int rulesLoaded;
    private long rulesExecuted;
    private long rulesSkippedByProfile;
    private long rulesDegraded;
    private long rulesKindFiltered;
    private long suppressedDiagnostics;
    private long xscriptMatchesExecuted;
    private long xscriptFailedMatches;
    private long xscriptCappedMatches;
    private long xscriptTimedOutMatches;
    private long xscriptBudgetExceededMatches;
    private long fixesDegraded;
    private long filesDegraded;
    private long fixesApplied;
    private long fixConflictsSkipped;
    private long fixFilesNonConvergent;
    private long fixRollbacks;
    private long exemptedDiagnostics;
    private long baselinedDiagnostics;
    private final Set<String> skippedRuleIds = new TreeSet<>();
    private final Set<String> disabledRuleIds = new TreeSet<>();
    private final Set<String> degradedRuleIds = new TreeSet<>();
    private final Set<String> degradedAnalyzers = new LinkedHashSet<>();
    private final Set<String> xscriptBudgetExceededRuleIds = new TreeSet<>();
    private final Set<String> breakerAbortedRuleIds = new TreeSet<>();

    /**
     * A summary over one run; the scan must already be complete — the
     * passed {@link SkippedFiles} accumulator stops changing once
     * {@link TargetScanner#scan} returned, and is shared (not copied).
     */
    public RunSummary(SkippedFiles skipped) {
        this.skipped = Objects.requireNonNull(skipped, "skipped must not be null");
    }

    /**
     * Records one scanned, linted file: its diagnostics by severity and the
     * run's engine statistics.
     */
    public void accumulate(LintResult result) {
        filesScanned++;
        for (Diagnostic diagnostic : result.diagnostics()) {
            switch (diagnostic.severity()) {
                case "error" -> errorCount++;
                case "warning" -> warningCount++;
                case "info" -> infoCount++;
                case "hint" -> hintCount++;
                default -> otherCount++;
            }
        }

        LintStats stats = result.stats();
        rulesLoaded = Math.max(rulesLoaded, stats.getRulesLoaded());
        rulesExecuted += stats.getRulesExecuted();
        rulesSkippedByProfile += stats.getRulesSkippedByProfile();
        rulesDegraded += stats.getRulesDegraded();
        rulesKindFiltered += stats.getRulesKindFiltered();
        suppressedDiagnostics += stats.getSuppressedDiagnostics();
        xscriptMatchesExecuted += stats.getXscriptMatchesExecuted();
        xscriptFailedMatches += stats.getXscriptFailedMatches();
        xscriptCappedMatches += stats.getXscriptCappedMatches();
        xscriptTimedOutMatches += stats.getXscriptTimedOutMatches();
        xscriptBudgetExceededMatches += stats.getXscriptBudgetExceededMatches();
        fixesDegraded += stats.getFixesDegraded();
        // a file whose breaker aborted rules is one degraded file (the id
        // list is the file's degraded marker, plan 2026-09-24-0900-1 Minor B)
        if (!stats.getBreakerAbortedRuleIds().isEmpty()) {
            filesDegraded++;
        }
        skippedRuleIds.addAll(stats.getSkippedRuleIds());
        disabledRuleIds.addAll(stats.getDisabledRuleIds());
        degradedRuleIds.addAll(stats.getDegradedRuleIds());
        // analyzer-level closure ids keep first-seen order across files
        // (plan Decision 7); the per-file order lives in the per-file stats
        for (String analyzer : stats.getDegradedAnalyzers()) {
            degradedAnalyzers.add(analyzer);
        }
        xscriptBudgetExceededRuleIds.addAll(stats.getXscriptBudgetExceededRuleIds());
        breakerAbortedRuleIds.addAll(stats.getBreakerAbortedRuleIds());
    }

    /**
     * The number of files linted through the engine.
     */
    public int getFilesScanned() {
        return filesScanned;
    }

    /**
     * The explicit skipped-file accounting.
     */
    public SkippedFiles getSkipped() {
        return skipped;
    }

    /**
     * Diagnostics with severity {@code error}.
     */
    public int getErrorCount() {
        return errorCount;
    }

    /**
     * Diagnostics with severity {@code warning}.
     */
    public int getWarningCount() {
        return warningCount;
    }

    /**
     * Diagnostics with severity {@code info}.
     */
    public int getInfoCount() {
        return infoCount;
    }

    /**
     * Diagnostics with severity {@code hint}.
     */
    public int getHintCount() {
        return hintCount;
    }

    /**
     * Diagnostics with a severity outside the xdef enum; counted, never
     * dropped.
     */
    public int getOtherCount() {
        return otherCount;
    }

    /**
     * The total diagnostic count over all severities.
     */
    public int getTotalDiagnostics() {
        return errorCount + warningCount + infoCount + hintCount + otherCount;
    }

    /**
     * The loaded rule set size (reported once — every file runs the same
     * set).
     */
    public int getRulesLoaded() {
        return rulesLoaded;
    }

    /**
     * Rule executions summed over the per-file engine runs.
     */
    public long getRulesExecuted() {
        return rulesExecuted;
    }

    /**
     * Profile-skipped rule executions summed over the per-file runs.
     */
    public long getRulesSkippedByProfile() {
        return rulesSkippedByProfile;
    }

    /**
     * Degraded rule executions (gate or budget closure) summed over the
     * per-file runs; degraded rules produce no diagnostics and are never
     * answered from a lower level (roadmap items 20/31).
     */
    public long getRulesDegraded() {
        return rulesDegraded;
    }

    /**
     * The ids of the degraded rules, unioned over the per-file runs.
     */
    public Set<String> getDegradedRuleIds() {
        return degradedRuleIds;
    }

    /**
     * Matches whose xscript the exhausted fast slice skipped, summed over
     * the per-file runs (design 11 §5 fast 预算闭合, roadmap item 31).
     */
    public long getXscriptBudgetExceededMatches() {
        return xscriptBudgetExceededMatches;
    }

    /**
     * The ids of rules whose matches the exhausted slice skipped, unioned
     * over the per-file runs.
     */
    public Set<String> getXscriptBudgetExceededRuleIds() {
        return xscriptBudgetExceededRuleIds;
    }

    /**
     * The degrade-ladder closure record, first-seen order across the files
     * of the run (the per-file closure order lives in each file's stats).
     */
    public Set<String> getDegradedAnalyzers() {
        return degradedAnalyzers;
    }

    /**
     * Fix generations skipped by the ladder's fix closure, summed over the
     * per-file runs (roadmap item 31).
     */
    public long getFixesDegraded() {
        return fixesDegraded;
    }

    /**
     * Files the pattern-stage circuit breaker degraded (its rule-abort id
     * list is the file's degraded marker), roadmap item 31.
     */
    public long getFilesDegraded() {
        return filesDegraded;
    }

    /**
     * The ids of breaker-aborted rules, unioned over the per-file runs.
     */
    public Set<String> getBreakerAbortedRuleIds() {
        return breakerAbortedRuleIds;
    }

    /**
     * Kind-filtered rule executions summed over the per-file runs.
     */
    public long getRulesKindFiltered() {
        return rulesKindFiltered;
    }

    /**
     * Suppressed candidate diagnostics summed over the per-file runs.
     */
    public long getSuppressedDiagnostics() {
        return suppressedDiagnostics;
    }

    /**
     * Xscript match executions summed over the per-file runs.
     */
    public long getXscriptMatchesExecuted() {
        return xscriptMatchesExecuted;
    }

    /**
     * Xscript match script failures summed over the per-file runs.
     */
    public long getXscriptFailedMatches() {
        return xscriptFailedMatches;
    }

    /**
     * Xscript matches aborted at the diagnostic cap, summed.
     */
    public long getXscriptCappedMatches() {
        return xscriptCappedMatches;
    }

    /**
     * Xscript matches aborted at their deadline, summed.
     */
    public long getXscriptTimedOutMatches() {
        return xscriptTimedOutMatches;
    }

    /**
     * The union of profile-skipped rule ids over all runs.
     */
    public Set<String> getSkippedRuleIds() {
        return new LinkedHashSet<>(skippedRuleIds);
    }

    /**
     * The union of xscript-disabled rule ids over all runs.
     */
    public Set<String> getDisabledRuleIds() {
        return new LinkedHashSet<>(disabledRuleIds);
    }

    /**
     * Folds one file's autofix accounting into the summary (roadmap item 25):
     * applied rewrites, merge conflicts, files stopped by the convergence
     * guard or pass cap, and syntax-break rollbacks — summed over the run,
     * never dropped silently.
     */
    public void addFixStats(FixApplier.FixStats stats) {
        fixesApplied += stats.applied();
        fixConflictsSkipped += stats.conflicts();
        fixFilesNonConvergent += stats.nonConvergent();
        fixRollbacks += stats.rollbacks();
    }

    /**
     * Fix rewrites applied (or, in a dry-run, proposed) summed over all
     * files.
     */
    public long getFixesApplied() {
        return fixesApplied;
    }

    /**
     * Fix candidates dropped as range conflicts by the merge, summed.
     */
    public long getFixConflictsSkipped() {
        return fixConflictsSkipped;
    }

    /**
     * Files whose multipass stopped without converging (guard or pass cap),
     * keeping their last successful pass's write.
     */
    public long getFixFilesNonConvergent() {
        return fixFilesNonConvergent;
    }

    /**
     * Syntax-break rollbacks (content restored to the previous pass), summed.
     */
    public long getFixRollbacks() {
        return fixRollbacks;
    }

    /**
     * Records one file's ruleset-exemption removals (roadmap item 27): the
     * exempted diagnostics are counted, never dropped silently.
     */
    public void addExemptedDiagnostics(int count) {
        exemptedDiagnostics += count;
    }

    /**
     * Diagnostics removed by ruleset exemptions (design 09 §4), summed over
     * all files.
     */
    public long getExemptedDiagnostics() {
        return exemptedDiagnostics;
    }

    /**
     * Records one file's baseline-matched removals (roadmap item 27): the
     * count is measured once against the file's original content, never
     * per fix pass.
     */
    public void addBaselinedDiagnostics(int count) {
        baselinedDiagnostics += count;
    }

    /**
     * Diagnostics suppressed by baseline matches (design 09 §5), summed
     * over all files.
     */
    public long getBaselinedDiagnostics() {
        return baselinedDiagnostics;
    }

    /**
     * True when at least one collected diagnostic carries severity
     * {@code error} — the exit-code-1 condition (design 03 §2.4).
     */
    public boolean hasErrorDiagnostics() {
        return errorCount > 0;
    }
}
