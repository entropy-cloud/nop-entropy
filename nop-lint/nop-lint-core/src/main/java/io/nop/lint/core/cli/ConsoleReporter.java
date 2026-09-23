package io.nop.lint.core.cli;

import io.nop.lint.core.engine.Diagnostic;
import io.nop.lint.core.node.LineIndex;

import java.io.PrintStream;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The v1 console report (design 03 §2.4 增注, 2026-09-22): one line per
 * diagnostic — {@code <path>:<line>[-<endLine>]: <severity>: <ruleId>:
 * <message>}, with the 1-based line/endLine conversion shared with the
 * RuleTester ({@link LineIndex}, design 03 §4.2) — followed by a summary
 * block that keeps the engine's "no silent skip" counters observable:
 * scanned/skipped file counts (skipped grouped per extension), severity
 * totals, and the {@code LintStats} key counters including
 * {@code skippedByProfile} (with rule ids), {@code suppressedDiagnostics},
 * and {@code disabledRuleIds}.
 *
 * <p>Rendering is deterministic: files in scan order, diagnostics in engine
 * order, counters in fixed field order.</p>
 */
public final class ConsoleReporter {

    private final PrintStream out;

    /**
     * @param out the report sink (stdout for the CLI; a captured stream for
     *            tests)
     */
    public ConsoleReporter(PrintStream out) {
        this.out = Objects.requireNonNull(out, "out must not be null");
    }

    /**
     * Renders one file's diagnostics followed by the run summary. A fix
     * run's dry-run diffs render between the diagnostics and the summary;
     * diagnostics of suggestion-only fix rules carry their fix description
     * as a suggestion annotation.
     */
    public void render(CheckOutcome outcome) {
        Objects.requireNonNull(outcome, "outcome must not be null");
        for (FileFindings finding : outcome.findings()) {
            for (Diagnostic diagnostic : finding.diagnostics()) {
                renderDiagnostic(finding.displayPath(), finding.lines(), diagnostic,
                        outcome.suggestionDescriptions());
            }
        }
        renderDiffs(outcome);
        renderSummary(outcome);
    }

    /**
     * Renders one diagnostic line; a rule whose fix is suggestion-only gets
     * its fix description appended (roadmap item 25: suggestions are listed
     * in the report, never applied by the fix flow).
     */
    void renderDiagnostic(String displayPath, LineIndex lines, Diagnostic diagnostic,
                          Map<String, String> suggestionDescriptions) {
        int startLine = lines.startLine(diagnostic.range());
        int endLine = lines.endLine(diagnostic.range());
        StringBuilder sb = new StringBuilder();
        sb.append(displayPath).append(':').append(startLine);
        if (endLine != startLine) {
            sb.append('-').append(endLine);
        }
        sb.append(": ").append(diagnostic.severity())
                .append(": ").append(diagnostic.ruleId())
                .append(": ").append(diagnostic.message());
        String suggestion = suggestionDescriptions.get(diagnostic.ruleId());
        if (suggestion != null) {
            sb.append(" (suggestion: ").append(suggestion).append(')');
        }
        out.println(sb);
    }

    /**
     * The dry-run diff blocks, verbatim after their header line; absent in a
     * run that proposes no changes.
     */
    void renderDiffs(CheckOutcome outcome) {
        for (FileDiff diff : outcome.diffs()) {
            out.println("fix (dry-run) would change " + diff.displayPath() + ":");
            out.print(diff.unifiedDiff());
            if (!diff.unifiedDiff().endsWith("\n")) {
                out.println();
            }
        }
    }

    /**
     * Renders the summary block (fixed field order, no silent counters).
     */
    void renderSummary(CheckOutcome outcome) {
        RunSummary summary = outcome.summary();
        out.println("check complete: scanned=" + summary.getFilesScanned() + " files, skipped="
                + summary.getSkipped().describe());
        out.println("diagnostics: error=" + summary.getErrorCount()
                + ", warning=" + summary.getWarningCount()
                + ", info=" + summary.getInfoCount()
                + ", hint=" + summary.getHintCount()
                + ", other=" + summary.getOtherCount()
                + " (total=" + summary.getTotalDiagnostics() + ")");
        out.println("rules: loaded=" + summary.getRulesLoaded()
                + ", executed=" + summary.getRulesExecuted()
                + ", skippedByProfile=" + summary.getRulesSkippedByProfile()
                + renderIds(summary.getSkippedRuleIds())
                + ", kindFiltered=" + summary.getRulesKindFiltered());
        out.println("suppressed diagnostics: " + summary.getSuppressedDiagnostics());
        out.println("disabled rules: " + renderDisabled(summary.getDisabledRuleIds()));
        out.println("xscript: executed=" + summary.getXscriptMatchesExecuted()
                + ", failed=" + summary.getXscriptFailedMatches()
                + ", capped=" + summary.getXscriptCappedMatches()
                + ", timedOut=" + summary.getXscriptTimedOutMatches());
        if (outcome.fixMode() != CliOptions.FixMode.NONE) {
            String mode = outcome.fixMode() == CliOptions.FixMode.DRY_RUN
                    ? "fix (dry-run, no files written): "
                    : "fix: ";
            out.println(mode + "applied=" + summary.getFixesApplied()
                    + ", conflicts=" + summary.getFixConflictsSkipped()
                    + ", nonconvergentFiles=" + summary.getFixFilesNonConvergent()
                    + ", rollbacks=" + summary.getFixRollbacks());
        }
    }

    /**
     * The id-list fragment appended to a counter (empty renders as
     * {@code []}, a single id without quotes).
     */
    private String renderIds(Set<String> ids) {
        if (ids.isEmpty()) {
            return "";
        }
        return " (ids: " + String.join(", ", ids) + ")";
    }

    /**
     * The disabled-rules summary line: {@code none} or the id list.
     */
    private String renderDisabled(Set<String> ids) {
        if (ids.isEmpty()) {
            return "none";
        }
        return String.join(", ", ids);
    }
}
