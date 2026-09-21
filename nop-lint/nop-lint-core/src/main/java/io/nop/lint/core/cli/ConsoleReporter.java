package io.nop.lint.core.cli;

import io.nop.lint.core.engine.Diagnostic;
import io.nop.lint.core.node.LineIndex;

import java.io.PrintStream;
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
     * Renders one file's diagnostics followed by the run summary.
     */
    public void render(CheckOutcome outcome) {
        Objects.requireNonNull(outcome, "outcome must not be null");
        for (FileFindings finding : outcome.findings()) {
            for (Diagnostic diagnostic : finding.diagnostics()) {
                renderDiagnostic(finding.displayPath(), finding.lines(), diagnostic);
            }
        }
        renderSummary(outcome.summary());
    }

    /**
     * Renders one diagnostic line.
     */
    void renderDiagnostic(String displayPath, LineIndex lines, Diagnostic diagnostic) {
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
        out.println(sb);
    }

    /**
     * Renders the summary block (fixed field order, no silent counters).
     */
    void renderSummary(RunSummary summary) {
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
