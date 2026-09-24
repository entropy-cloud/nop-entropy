package io.nop.lint.core.cli;

import io.nop.lint.core.engine.Diagnostic;
import io.nop.lint.core.node.LineIndex;
import io.nop.lint.core.suppress.BaselineFile;

import java.io.IOException;
import java.io.PrintStream;
import java.io.Writer;
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
 * order, counters in fixed field order. Roadmap item 39 unified the five
 * formats behind {@link Reporter}: this class renders through a {@link
 * Writer} with the line ending pinned to {@code \n}, and the {@link
 * #ConsoleReporter(PrintStream)} constructor delegates — the console output
 * is byte-identical to the pre-interface shape.</p>
 */
public final class ConsoleReporter implements Reporter {

    private final PrintStream out;

    /**
     * @param out the report sink (stdout for the CLI; a captured stream for
     *            tests)
     */
    public ConsoleReporter(PrintStream out) {
        this.out = Objects.requireNonNull(out, "out must not be null");
    }

    /**
     * The PrintStream face: delegates to the {@link Reporter} render and
     * flushes (PrintStream writes never fail, so the checked exception is
     * impossible here).
     */
    public void render(CheckOutcome outcome) {
        try {
            render(outcome, new Writer() {
                @Override
                public void write(char[] cbuf, int off, int len) {
                    out.print(new String(cbuf, off, len));
                }

                @Override
                public void flush() {
                    out.flush();
                }

                @Override
                public void close() {
                    out.flush();
                }
            });
        } catch (IOException e) {
            throw new IllegalStateException("console render cannot fail", e);
        }
    }

    /**
     * Renders one file's diagnostics followed by the run summary. A fix
     * run's dry-run diffs render between the diagnostics and the summary;
     * diagnostics of suggestion-only fix rules carry their fix description
     * as a suggestion annotation.
     */
    @Override
    public void render(CheckOutcome outcome, Writer out) throws IOException {
        Objects.requireNonNull(outcome, "outcome must not be null");
        for (FileFindings finding : outcome.findings()) {
            for (Diagnostic diagnostic : finding.diagnostics()) {
                renderDiagnostic(out, finding.displayPath(), finding.lines(), diagnostic,
                        outcome.suggestionDescriptions());
            }
        }
        renderDiffs(out, outcome);
        renderSummary(out, outcome);
    }

    /**
     * Renders one diagnostic line; a rule whose fix is suggestion-only gets
     * its fix description appended (roadmap item 25: suggestions are listed
     * in the report, never applied by the fix flow).
     */
    void renderDiagnostic(Writer out, String displayPath, LineIndex lines, Diagnostic diagnostic,
                          Map<String, String> suggestionDescriptions) throws IOException {
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
        out.write(sb.toString());
        out.write("\n");
    }

    /**
     * The dry-run diff blocks, verbatim after their header line; absent in a
     * run that proposes no changes.
     */
    void renderDiffs(Writer out, CheckOutcome outcome) throws IOException {
        for (FileDiff diff : outcome.diffs()) {
            out.write("fix (dry-run) would change " + diff.displayPath() + ":\n");
            out.write(diff.unifiedDiff());
            if (!diff.unifiedDiff().endsWith("\n")) {
                out.write("\n");
            }
        }
    }

    /**
     * Renders the summary block (fixed field order, no silent counters).
     */
    void renderSummary(Writer out, CheckOutcome outcome) throws IOException {
        RunSummary summary = outcome.summary();
        out.write("check complete: scanned=" + summary.getFilesScanned() + " files, skipped="
                + summary.getSkipped().describe() + "\n");
        out.write("diagnostics: error=" + summary.getErrorCount()
                + ", warning=" + summary.getWarningCount()
                + ", info=" + summary.getInfoCount()
                + ", hint=" + summary.getHintCount()
                + ", other=" + summary.getOtherCount()
                + " (total=" + summary.getTotalDiagnostics() + ")\n");
        out.write("rules: loaded=" + summary.getRulesLoaded()
                + ", executed=" + summary.getRulesExecuted()
                + ", skippedByProfile=" + summary.getRulesSkippedByProfile()
                + renderIds(summary.getSkippedRuleIds())
                + ", degraded=" + summary.getRulesDegraded()
                + renderIds(summary.getDegradedRuleIds())
                + ", kindFiltered=" + summary.getRulesKindFiltered() + "\n");
        out.write("suppressed diagnostics: " + summary.getSuppressedDiagnostics() + "\n");
        if (summary.getCacheHits() > 0) {
            out.write("cache: " + summary.getCacheHits() + " hit(s)\n");
        }
        out.write("exempted diagnostics: " + summary.getExemptedDiagnostics() + "\n");
        if (summary.getBaselinedDiagnostics() > 0) {
            out.write("baseline-suppressed diagnostics: " + summary.getBaselinedDiagnostics() + "\n");
        }
        if (!summary.getDegradedAnalyzers().isEmpty()) {
            out.write("degraded analyzers (file budget exhausted, closure order): "
                    + renderDisabled(summary.getDegradedAnalyzers()) + "\n");
        }
        if (summary.getFilesDegraded() > 0) {
            out.write("budget-breaker: filesDegraded=" + summary.getFilesDegraded()
                    + renderIds(summary.getBreakerAbortedRuleIds())
                    + " (the pattern stage consumed the file budget; those files'"
                    + " remaining rules were aborted)\n");
        }
        out.write("disabled rules: " + renderDisabled(summary.getDisabledRuleIds()) + "\n");
        out.write("xscript: executed=" + summary.getXscriptMatchesExecuted()
                + ", failed=" + summary.getXscriptFailedMatches()
                + ", capped=" + summary.getXscriptCappedMatches()
                + ", timedOut=" + summary.getXscriptTimedOutMatches()
                + ", budgetExceeded=" + summary.getXscriptBudgetExceededMatches()
                + renderIds(summary.getXscriptBudgetExceededRuleIds()) + "\n");
        if (summary.getFixesDegraded() > 0) {
            out.write("fix generations skipped by budget closure: " + summary.getFixesDegraded() + "\n");
        }
        if (outcome.fixMode() != CliOptions.FixMode.NONE) {
            String mode = outcome.fixMode() == CliOptions.FixMode.DRY_RUN
                    ? "fix (dry-run, no files written): "
                    : "fix: ";
            out.write(mode + "applied=" + summary.getFixesApplied()
                    + ", conflicts=" + summary.getFixConflictsSkipped()
                    + ", nonconvergentFiles=" + summary.getFixFilesNonConvergent()
                    + ", rollbacks=" + summary.getFixRollbacks() + "\n");
        }
        renderStaleBaseline(out, outcome);
    }

    /**
     * The run-level stale-baseline section (roadmap item 27): entries the
     * current content no longer fully uses. Stale entries are run-level
     * records, not diagnostics — they never enter the diagnostic stream or
     * the severity counts (plan 2026-09-24-0050-1); in
     * {@code --baseline-check} mode they drive the exit code.
     */
    void renderStaleBaseline(Writer out, CheckOutcome outcome) throws IOException {
        if (outcome.staleBaselineEntries().isEmpty()) {
            return;
        }
        out.write("stale baseline entries: " + outcome.staleBaselineEntries().size()
                + " (the content no longer shows these violations; regenerate with"
                + " --write-baseline)\n");
        for (BaselineFile.Entry entry : outcome.staleBaselineEntries()) {
            out.write("  - rule " + entry.rule() + ", file " + entry.file()
                    + ", remaining=" + entry.count() + "\n");
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
