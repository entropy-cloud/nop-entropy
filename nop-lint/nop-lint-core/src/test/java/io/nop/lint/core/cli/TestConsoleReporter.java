package io.nop.lint.core.cli;

import io.nop.lint.core.engine.Diagnostic;
import io.nop.lint.core.engine.LintStats;
import io.nop.lint.core.node.LineIndex;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ConsoleReporter} rendering proofs (Minimum Rules #25): the
 * diagnostic line format (1-based line/endLine, multi-line ranges), the
 * summary block's observable counters (skipped buckets, severity totals,
 * skippedByProfile/suppressed/disabled), and deterministic output for
 * multi-diagnostic files.
 */
public class TestConsoleReporter {

    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    private final ConsoleReporter reporter =
            new ConsoleReporter(new PrintStream(buffer, true, StandardCharsets.UTF_8));

    private String rendered() {
        return buffer.toString(StandardCharsets.UTF_8);
    }

    private static LintStats emptyStats() {
        return LintStats.builder().rulesLoaded(2).build();
    }

    @Test
    public void diagnosticLineUsesOneBasedLineAndEngineFields() {
        String source = "class A {\n    void x() {\n        System.out.println(\"a\");\n    }\n}\n";
        LineIndex lines = new LineIndex(source);
        Diagnostic diagnostic = new Diagnostic("demo/no-print", "warning",
                "Do not use System.out.println for logging", linesRange(lines, source));

        reporter.renderDiagnostic("src/A.java", lines, diagnostic);

        assertTrue(rendered().startsWith("src/A.java:3: warning: demo/no-print: Do not use"),
                rendered());
    }

    private io.nop.lint.core.node.SourceRange linesRange(LineIndex lines, String source) {
        int start = source.indexOf("System.out.println");
        return new io.nop.lint.core.node.SourceRange(start, start + "System.out.println(\"a\")".length());
    }

    @Test
    public void multiLineRangeRendersAsLineEndLine() {
        String source = "class A {\n    void x() {\n        foo();\n        bar();\n    }\n}\n";
        LineIndex lines = new LineIndex(source);
        int start = source.indexOf("foo();");
        int end = source.indexOf("bar();") + "bar();".length();
        Diagnostic diagnostic = new Diagnostic("demo/multi", "info", "spans two lines",
                new io.nop.lint.core.node.SourceRange(start, end));

        reporter.renderDiagnostic("A.java", lines, diagnostic);

        assertTrue(rendered().startsWith("A.java:3-4: info: demo/multi: spans two lines"),
                rendered());
    }

    @Test
    public void summaryRendersAllObservableCounters() {
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

        reporter.renderSummary(summary);

        String out = rendered();
        assertTrue(out.contains("check complete: scanned=1 files, skipped=0"), out);
        assertTrue(out.contains("diagnostics: error=1, warning=1, info=0, hint=0, other=0 (total=2)"), out);
        assertTrue(out.contains("rules: loaded=7, executed=1, skippedByProfile=1 (ids: demo/l2-only), kindFiltered=1"), out);
        assertTrue(out.contains("suppressed diagnostics: 2"), out);
        assertTrue(out.contains("disabled rules: demo/broken-script"), out);
        assertTrue(out.contains("xscript: executed=1, failed=1, capped=0, timedOut=0"), out);
    }

    @Test
    public void emptyDisabledRulesRenderAsNoneAndIdsOmittedWhenEmpty() {
        RunSummary summary = new RunSummary(new SkippedFiles());
        summary.accumulate(new io.nop.lint.core.engine.LintResult(List.of(), emptyStats()));

        reporter.renderSummary(summary);

        String out = rendered();
        assertTrue(out.contains("disabled rules: none"), out);
        assertTrue(out.contains("rules: loaded=2, executed=0, skippedByProfile=0, kindFiltered=0"), out);
        assertTrue(out.contains("suppressed diagnostics: 0"), out);
    }

    @Test
    public void multipleDiagnosticsRenderInOrderWithStablePaths() {
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

        reporter.render(new CheckOutcome(List.of(first, second), summary));

        String out = rendered();
        int firstIndex = out.indexOf("a.java:1: warning: demo/r: first finding");
        int secondIndex = out.indexOf("b.java:1: warning: demo/r: second finding");
        assertTrue(firstIndex >= 0 && secondIndex > firstIndex, out);
    }
}
