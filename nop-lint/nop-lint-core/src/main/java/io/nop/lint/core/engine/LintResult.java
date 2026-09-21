package io.nop.lint.core.engine;

import java.util.List;
import java.util.Objects;

/**
 * The outcome of one lint run: the diagnostics in rule/match order and the
 * run's statistics (design 03 §2.3 {@code LintResult} minus the fix surface,
 * which lands with autofix, roadmap item 25).
 */
public record LintResult(List<Diagnostic> diagnostics, LintStats stats) {

    public LintResult {
        Objects.requireNonNull(diagnostics, "diagnostics must not be null");
        Objects.requireNonNull(stats, "stats must not be null");
        diagnostics = List.copyOf(diagnostics);
    }
}
