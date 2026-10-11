package io.nop.lint.graphql;

import java.util.List;

/**
 * The check face: diagnostics plus the severity totals.
 */
public record LintCheckResult(List<LintDiagnosticView> diagnostics, int errorCount,
                              int warningCount, int infoCount, int hintCount, int otherCount,
                              int total) {
}
