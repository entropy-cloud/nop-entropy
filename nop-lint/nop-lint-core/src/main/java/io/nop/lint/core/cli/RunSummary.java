package io.nop.lint.core.cli;

import io.nop.lint.core.engine.Diagnostic;
import io.nop.lint.core.engine.LintResult;
import io.nop.lint.core.engine.LintStats;

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
    private long rulesKindFiltered;
    private long suppressedDiagnostics;
    private long xscriptMatchesExecuted;
    private long xscriptFailedMatches;
    private long xscriptCappedMatches;
    private long xscriptTimedOutMatches;
    private final Set<String> skippedRuleIds = new TreeSet<>();
    private final Set<String> disabledRuleIds = new TreeSet<>();

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
        rulesKindFiltered += stats.getRulesKindFiltered();
        suppressedDiagnostics += stats.getSuppressedDiagnostics();
        xscriptMatchesExecuted += stats.getXscriptMatchesExecuted();
        xscriptFailedMatches += stats.getXscriptFailedMatches();
        xscriptCappedMatches += stats.getXscriptCappedMatches();
        xscriptTimedOutMatches += stats.getXscriptTimedOutMatches();
        skippedRuleIds.addAll(stats.getSkippedRuleIds());
        disabledRuleIds.addAll(stats.getDisabledRuleIds());
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
     * True when at least one collected diagnostic carries severity
     * {@code error} — the exit-code-1 condition (design 03 §2.4).
     */
    public boolean hasErrorDiagnostics() {
        return errorCount > 0;
    }
}
