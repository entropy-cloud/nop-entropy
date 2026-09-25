package io.nop.lint.core.fix;

import io.nop.lint.core.engine.Diagnostic;
import io.nop.lint.core.engine.LintResult;
import io.nop.lint.core.lang.LintLanguage;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * The multipass fix application loop (roadmap item 25, design 03 §3 with the
 * plan 2026-09-22-2225-1 adjudications): each pass lints the current content,
 * collects the fix-bearing candidate diagnostics (suggestion-only rules never
 * carry a fix and suppressed diagnostics were already removed with theirs, so
 * the candidate list is exactly the applicable set), and hands the pass to the
 * single mechanical core {@link EditPlanApplier#apply} — merge, splice, atomic
 * write (unless dry-run), re-parse guard, rollback on a syntax break. This
 * loop owns only what is diagnosis-driven: the re-lint per pass, the
 * convergence guard, and the pass cap.
 *
 * <p>Convergence guard: the candidate count must strictly decrease from one
 * pass to the next (measured on fix-bearing candidates only — suppression
 * meta-diagnostics rise when autofix deletes code next to a suppression
 * comment, so the full count would fake non-convergence). A pass that fails to
 * decrease stops the loop, counts {@code nonConvergent}, and keeps the last
 * successful pass's write on disk — partial progress is real work, not an
 * error. The pass cap ({@value #DEFAULT_MAX_PASSES}) stops the loop the same
 * way. Dry-run runs the identical loop in memory, writes nothing, and its
 * rollback is simply not adopting the broken content.</p>
 *
 * <p>Every abnormal exit is explicit and counted: write failures throw
 * {@link io.nop.lint.core.NopLintException} out of the mechanical core (the
 * temp file is cleaned up, the target keeps its previous content), rollbacks
 * and non-convergence are visible in {@link FixStats} — nothing is dropped
 * silently.</p>
 */
public final class FixApplier {

    /**
     * The plan's multipass bound: after this many fix passes the loop stops
     * and reports non-convergence instead of spinning.
     */
    public static final int DEFAULT_MAX_PASSES = 10;

    /**
     * The lint entry the applier drives: one full engine run over the given
     * content (the CLI wires it to the same {@code LintEngine} and rule set
     * the report path uses).
     */
    @FunctionalInterface
    public interface LintFunction {

        LintResult lint(byte[] source);
    }

    /**
     * The accounting of one file's multipass: {@code applied} counts the
     * rewrites that survived on disk (or, in dry-run, the proposed ones) —
     * a rolled-back pass does not count — {@code conflicts} the candidates
     * dropped as overlapping, {@code nonConvergent} is 1 when the loop
     * stopped on the convergence guard or the pass cap with candidates
     * remaining, and {@code rollbacks} is 1 when a syntax break restored the
     * previous content. {@code passes} counts the passes that actually
     * applied fixes.
     */
    public record FixStats(int passes, int applied, int conflicts, int nonConvergent, int rollbacks) {
    }

    /**
     * One file's outcome: the content the run leaves behind (the original in
     * dry-run and after a rollback), plus the stats.
     */
    public record FixResult(byte[] finalSource, FixStats stats) {
    }

    private final LintFunction lintFunction;
    private final LintLanguage language;
    private final int maxPasses;

    public FixApplier(LintFunction lintFunction, LintLanguage language) {
        this(lintFunction, language, DEFAULT_MAX_PASSES);
    }

    /**
     * The explicit pass bound (tests shrink it to exercise the cap without
     * ten passes).
     */
    public FixApplier(LintFunction lintFunction, LintLanguage language, int maxPasses) {
        this.lintFunction = Objects.requireNonNull(lintFunction, "lintFunction must not be null");
        this.language = Objects.requireNonNull(language, "language must not be null");
        if (maxPasses < 1) {
            throw new IllegalArgumentException("maxPasses must be at least 1: " + maxPasses);
        }
        this.maxPasses = maxPasses;
    }

    /**
     * Runs the multipass loop over the original content. {@code dryRun = true}
     * computes the full loop in memory and never touches {@code file};
     * {@code false} writes every successful pass atomically and restores the
     * previous content on a syntax break.
     */
    public FixResult run(Path file, byte[] original, boolean dryRun) {
        Objects.requireNonNull(file, "file must not be null");
        Objects.requireNonNull(original, "original must not be null");

        byte[] current = original;
        int previousCandidates = -1;
        int passes = 0;
        int applied = 0;
        int conflicts = 0;
        int rollbacks = 0;

        for (int pass = 0; pass < maxPasses; pass++) {
            List<Fix> candidates = candidates(current);
            if (candidates.isEmpty()) {
                return new FixResult(current, new FixStats(passes, applied, conflicts, 0, rollbacks));
            }
            if (previousCandidates >= 0 && candidates.size() >= previousCandidates) {
                // the guard fires before this pass's merge, so `current` — the
                // last successful write — stays on disk untouched
                return new FixResult(current, new FixStats(passes, applied, conflicts, 1, rollbacks));
            }

            passes++;
            EditPlanApplier.EditPlanResult result =
                    EditPlanApplier.apply(file, current, candidates, language, dryRun);
            conflicts += result.skippedConflicts();
            // a rolled-back pass contributes 0 — the same net accounting as
            // the previous `applied += size` / `applied -= size` pair
            applied += result.appliedEdits();
            if (result.rolledBack()) {
                return new FixResult(result.finalSource(),
                        new FixStats(passes, applied, conflicts, 0, rollbacks + 1));
            }
            current = result.finalSource();
            previousCandidates = candidates.size();
        }
        // the pass cap ran out with candidates remaining: the same honest
        // non-convergent stop as the guard, last successful write kept
        return new FixResult(current, new FixStats(passes, applied, conflicts, 1, rollbacks));
    }

    /**
     * The fix-bearing diagnostics of one lint run, in engine order — the
     * priority order the merge consumes (ruleset declaration order, then
     * match order). Diagnostics without a fix (plain rules, suggestion-only
     * rules, suppressed removals) never enter the list.
     */
    private List<Fix> candidates(byte[] source) {
        LintResult result = lintFunction.lint(source);
        return result.diagnostics().stream()
                .map(Diagnostic::fix)
                .filter(Objects::nonNull)
                .toList();
    }
}
