package io.nop.lint.core.engine;

import io.nop.lint.core.fix.Fix;

import java.util.List;
import java.util.Objects;

/**
 * The outcome of one lint run: the diagnostics in rule/match order, the
 * run's statistics, and — since nop-refactor WI3 — the transform edits the
 * run produced (design 03 §2.3 {@code LintResult} minus the fix surface,
 * which lands with autofix, roadmap item 25).
 *
 * <p>{@code transformFixes} is the separate rewrite-only channel (plan
 * 04-wi3-transform-dsl adjudication 1): transform-rule matches rewrite files
 * without ever producing a user-facing diagnostic, so they never enter
 * {@code diagnostics} — the report face, the suppression tail, and the
 * baseline all consume the diagnostics list only and are structurally
 * unaware of rewrites. Only the fix-application flow merges the two channels
 * (by the shared generation ordinal).</p>
 */
public record LintResult(List<Diagnostic> diagnostics, LintStats stats, List<Fix> transformFixes) {

    public LintResult {
        Objects.requireNonNull(diagnostics, "diagnostics must not be null");
        Objects.requireNonNull(stats, "stats must not be null");
        diagnostics = List.copyOf(diagnostics);
        transformFixes = transformFixes == null ? List.of() : List.copyOf(transformFixes);
    }

    /**
     * The two-argument form every pre-transform call site keeps using: a run
     * without transform rules carries an empty rewrite channel.
     */
    public LintResult(List<Diagnostic> diagnostics, LintStats stats) {
        this(diagnostics, stats, List.of());
    }
}
