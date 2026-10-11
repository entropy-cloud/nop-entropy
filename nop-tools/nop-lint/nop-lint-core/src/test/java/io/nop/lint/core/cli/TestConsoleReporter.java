package io.nop.lint.core.cli;

import io.nop.lint.core.engine.Diagnostic;
import io.nop.lint.core.engine.LintStats;
import io.nop.lint.core.fix.FixApplier;
import io.nop.lint.core.node.LineIndex;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ConsoleReporter} rendering proofs (Minimum Rules #25): the
 * diagnostic line format (1-based line/endLine, multi-line ranges), the
 * summary block's observable counters (skipped buckets, severity totals,
 * skippedByProfile/suppressed/disabled), and deterministic output for
 * multi-diagnostic files.
 */
public class TestConsoleReporter {

    private final java.io.StringWriter writer = new java.io.StringWriter();
    // the PrintStream face is never exercised directly in this class any
    // more (every proof renders through the Writer face), so the sink is a
    // discarded stream
    private final ConsoleReporter reporter =
            new ConsoleReporter(new PrintStream(new ByteArrayOutputStream(), true,
                    StandardCharsets.UTF_8));

    private String rendered() {
        return writer.toString();
    }

    private static LintStats emptyStats() {
        return LintStats.builder().rulesLoaded(2).build();
    }

    @Test
    public void diagnosticLineUsesOneBasedLineAndEngineFields() throws java.io.IOException {
        String source = "class A {\n    void x() {\n        System.out.println(\"a\");\n    }\n}\n";
        LineIndex lines = new LineIndex(source);
        Diagnostic diagnostic = new Diagnostic("demo/no-print", "warning",
                "Do not use System.out.println for logging", linesRange(lines, source));

        reporter.renderDiagnostic(writer, "src/A.java", lines, diagnostic, Map.of());

        assertTrue(rendered().startsWith("src/A.java:3: warning: demo/no-print: Do not use"),
                rendered());
    }

    private io.nop.lint.core.node.SourceRange linesRange(LineIndex lines, String source) {
        int start = source.indexOf("System.out.println");
        return new io.nop.lint.core.node.SourceRange(start, start + "System.out.println(\"a\")".length());
    }

    @Test
    public void multiLineRangeRendersAsLineEndLine() throws java.io.IOException {
        String source = "class A {\n    void x() {\n        foo();\n        bar();\n    }\n}\n";
        LineIndex lines = new LineIndex(source);
        int start = source.indexOf("foo();");
        int end = source.indexOf("bar();") + "bar();".length();
        Diagnostic diagnostic = new Diagnostic("demo/multi", "info", "spans two lines",
                new io.nop.lint.core.node.SourceRange(start, end));

        reporter.renderDiagnostic(writer, "A.java", lines, diagnostic, Map.of());

        assertTrue(rendered().startsWith("A.java:3-4: info: demo/multi: spans two lines"),
                rendered());
    }

    @Test
    public void summaryRendersAllObservableCounters() throws java.io.IOException {
        LintStats stats = LintStats.builder()
                .rulesLoaded(7)
                .incRulesExecuted()
                .incRulesSkippedByProfile("demo/l2-only")
                .incRulesKindFiltered()
                .incSuppressedDiagnostics(2)
                .addDisabledRuleId("demo/broken-script")
                .incXscriptMatchesExecuted()
                .incXscriptFailedMatches()
                .build();
        RunSummary summary = new RunSummary(new SkippedFiles());
        summary.accumulate(new io.nop.lint.core.engine.LintResult(List.of(
                new Diagnostic("demo/a", "error", "e", new io.nop.lint.core.node.SourceRange(0, 1)),
                new Diagnostic("demo/b", "warning", "w", new io.nop.lint.core.node.SourceRange(0, 1))),
                stats));

        reporter.renderSummary(writer, new CheckOutcome(List.of(), summary));

        String out = rendered();
        assertTrue(out.contains("check complete: scanned=1 files, skipped=0"), out);
        assertTrue(out.contains("diagnostics: error=1, warning=1, info=0, hint=0, other=0 (total=2)"), out);
        assertTrue(out.contains("rules: loaded=7, executed=1, skippedByProfile=1 (ids: demo/l2-only), degraded=0, kindFiltered=1"), out);
        assertTrue(out.contains("suppressed diagnostics: 2"), out);
        assertTrue(out.contains("disabled rules: demo/broken-script"), out);
        assertTrue(out.contains("xscript: executed=1, failed=1, capped=0, timedOut=0, budgetExceeded=0"), out);
    }

    @Test
    public void emptyDisabledRulesRenderAsNoneAndIdsOmittedWhenEmpty() throws java.io.IOException {
        RunSummary summary = new RunSummary(new SkippedFiles());
        summary.accumulate(new io.nop.lint.core.engine.LintResult(List.of(), emptyStats()));

        reporter.renderSummary(writer, new CheckOutcome(List.of(), summary));

        String out = rendered();
        assertTrue(out.contains("disabled rules: none"), out);
        assertTrue(out.contains("rules: loaded=2, executed=0, skippedByProfile=0, degraded=0, kindFiltered=0"), out);
        assertTrue(out.contains("suppressed diagnostics: 0"), out);
    }

    @Test
    public void multipleDiagnosticsRenderInOrderWithStablePaths() throws java.io.IOException {
        LintStats stats = LintStats.builder().rulesLoaded(1).build();
        FileFindings first = new FileFindings("a.java", new LineIndex("class A {\n}\n"),
                List.of(new Diagnostic("demo/r", "warning", "first finding",
                        new io.nop.lint.core.node.SourceRange(0, 5))));
        FileFindings second = new FileFindings("b.java", new LineIndex("class B {\n}\n"),
                List.of(new Diagnostic("demo/r", "warning", "second finding",
                        new io.nop.lint.core.node.SourceRange(0, 5))));
        RunSummary summary = new RunSummary(new SkippedFiles());
        summary.accumulate(new io.nop.lint.core.engine.LintResult(List.of(), stats));
        summary.accumulate(new io.nop.lint.core.engine.LintResult(List.of(), stats));

        reporter.render(new CheckOutcome(List.of(first, second), summary), writer);

        String out = rendered();
        int firstIndex = out.indexOf("a.java:1: warning: demo/r: first finding");
        int secondIndex = out.indexOf("b.java:1: warning: demo/r: second finding");
        assertTrue(firstIndex >= 0 && secondIndex > firstIndex, out);
    }

    @Test
    public void suggestionOnlyRuleDiagnosticCarriesFixDescription() throws java.io.IOException {
        String source = "class A {\n}\n";
        LineIndex lines = new LineIndex(source);
        Diagnostic diagnostic = new Diagnostic("demo/suggest", "warning", "raw println",
                new io.nop.lint.core.node.SourceRange(0, 5));

        reporter.renderDiagnostic(writer, "A.java", lines, diagnostic, Map.of("demo/suggest", "use the logger"));

        assertTrue(rendered().contains("demo/suggest: raw println (suggestion: use the logger)"),
                rendered());
    }

    @Test
    public void plainDiagnosticHasNoSuggestionAnnotation() throws java.io.IOException {
        String source = "class A {\n}\n";
        LineIndex lines = new LineIndex(source);
        Diagnostic diagnostic = new Diagnostic("demo/plain", "warning", "plain finding",
                new io.nop.lint.core.node.SourceRange(0, 5));

        reporter.renderDiagnostic(writer, "A.java", lines, diagnostic, Map.of("demo/other", "unrelated"));

        assertFalse(rendered().contains("(suggestion:"), rendered());
    }

    @Test
    public void fixRunSummaryRendersFixCountersAndDryRunMarksNoWrite() throws java.io.IOException {
        RunSummary summary = new RunSummary(new SkippedFiles());
        summary.accumulate(new io.nop.lint.core.engine.LintResult(List.of(),
                LintStats.builder().rulesLoaded(1).build()));
        summary.addFixStats(new FixApplier.FixStats(2, 3, 1, 0, 0));

        reporter.renderSummary(writer, new CheckOutcome(List.of(), summary, CliOptions.FixMode.APPLY,
                List.of(), Map.of()));

        String out = rendered();
        assertTrue(out.contains("fix: applied=3, conflicts=1, nonconvergentFiles=0, rollbacks=0"), out);
        assertFalse(out.contains("no files written"), out);
    }

    @Test
    public void dryRunSummaryMarksThatNoFilesWereWritten() throws java.io.IOException {
        RunSummary summary = new RunSummary(new SkippedFiles());
        summary.accumulate(new io.nop.lint.core.engine.LintResult(List.of(),
                LintStats.builder().rulesLoaded(1).build()));
        summary.addFixStats(new FixApplier.FixStats(1, 1, 0, 1, 0));

        reporter.renderSummary(writer, new CheckOutcome(List.of(), summary, CliOptions.FixMode.DRY_RUN,
                List.of(), Map.of()));

        assertTrue(rendered().contains("fix (dry-run, no files written): applied=1, conflicts=0,"
                + " nonconvergentFiles=1, rollbacks=0"), rendered());
    }

    @Test
    public void dryRunDiffsRenderAfterDiagnosticsWithHeader() throws java.io.IOException {
        RunSummary summary = new RunSummary(new SkippedFiles());
        FileDiff diff = new FileDiff("src/A.java", "--- a/src/A.java\n+++ b/src/A.java\n"
                + "@@ -1,2 +1,1 @@\n-class A {\n-class B {\n+class A {\n");

        reporter.render(new CheckOutcome(List.of(), summary, CliOptions.FixMode.DRY_RUN,
                List.of(diff), Map.of()), writer);

        String out = rendered();
        assertTrue(out.contains("fix (dry-run) would change src/A.java:"), out);
        assertTrue(out.contains("@@ -1,2 +1,1 @@"), out);
        assertTrue(out.contains("-class B {"), out);
        assertTrue(out.indexOf("would change") < out.indexOf("check complete:"),
                "diffs render before the summary block: " + out);
    }
}
