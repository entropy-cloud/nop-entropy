package io.nop.lint.core.fix;

import io.nop.lint.core.NopLintException;
import io.nop.lint.core.engine.Diagnostic;
import io.nop.lint.core.engine.LintResult;
import io.nop.lint.core.lang.LintLanguage;
import io.nop.lint.core.node.LintNode;
import io.nop.lint.core.node.LintTree;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Objects;

/**
 * The multipass fix application loop (roadmap item 25, design 03 §3 with the
 * plan 2026-09-22-2225-1 adjudications): each pass lints the current content,
 * collects the fix-bearing candidate diagnostics (suggestion-only rules never
 * carry a fix and suppressed diagnostics were already removed with theirs, so
 * the candidate list is exactly the applicable set), merges it through
 * {@link Fixer#merge}, splices the surviving rewrites into the bytes, writes
 * atomically (temp file + atomic move in the same directory), and re-parses —
 * a pass whose result introduces more error-recovery nodes than the content it
 * started from is a syntax break: the file is restored to the previous pass's
 * content, the rollback is counted, and the file's multipass aborts.
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
 * {@link NopLintException} (the temp file is cleaned up, the target keeps its
 * previous content), rollbacks and non-convergence are visible in
 * {@link FixStats} — nothing is dropped silently.</p>
 */
public final class FixApplier {

    private static final Logger LOG = LoggerFactory.getLogger(FixApplier.class);

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
        int currentErrorNodes = countErrorNodes(language.parse(current));
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
            Fixer.MergeResult merge = Fixer.merge(candidates);
            applied += merge.applied().size();
            conflicts += merge.skippedConflicts();
            byte[] next = applyAll(current, merge.applied());
            if (!dryRun) {
                atomicWrite(file, next);
            }

            int nextErrorNodes = countErrorNodes(language.parse(next));
            if (nextErrorNodes > currentErrorNodes) {
                if (!dryRun) {
                    atomicWrite(file, current);
                }
                // the pass's rewrites did not survive — applied counts only
                // what is (or would be) left on disk
                applied -= merge.applied().size();
                return new FixResult(current,
                        new FixStats(passes, applied, conflicts, 0, rollbacks + 1));
            }
            currentErrorNodes = nextErrorNodes;
            current = next;
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

    /**
     * Splices the merged fixes into the source. The merge returns them
     * ascending by range start, so the splices run in reverse and every
     * earlier offset stays valid.
     */
    private static byte[] applyAll(byte[] source, List<Fix> fixes) {
        byte[] out = source;
        for (int i = fixes.size() - 1; i >= 0; i--) {
            Fix fix = fixes.get(i);
            byte[] replacement = fix.replacement().getBytes(StandardCharsets.UTF_8);
            int start = fix.range().startByte();
            int end = fix.range().endByte();
            if (start < 0 || end > out.length || start > end) {
                throw new NopLintException("fix '" + fix.ruleId() + "' targets byte range ["
                        + start + "," + end + ") outside the " + out.length
                        + "-byte source (stale range; fail-closed)");
            }
            out = splice(out, start, end, replacement);
        }
        return out;
    }

    private static byte[] splice(byte[] source, int start, int end, byte[] replacement) {
        byte[] out = new byte[source.length - (end - start) + replacement.length];
        System.arraycopy(source, 0, out, 0, start);
        System.arraycopy(replacement, 0, out, start, replacement.length);
        System.arraycopy(source, end, out, start + replacement.length, source.length - end);
        return out;
    }

    /**
     * The error-recovery node count of the parsed content ({@code ERROR}
     * nodes plus missing-node placeholders): the syntax-break signal is an
     * increase over the pre-pass content, so a source that already carries
     * recovery nodes never false-positives its own fixes.
     */
    private int countErrorNodes(LintTree tree) {
        int count = 0;
        for (LintNode node : tree.root()) {
            if ("ERROR".equals(node.kind()) || node.isMissing()) {
                count++;
            }
        }
        return count;
    }

    /**
     * The atomic content write (design 03 §3): a temp file in the target's
     * directory (same filesystem by construction) followed by an atomic move
     * over the target. A failure at any point deletes the temp file and
     * throws — the target keeps its previous content, never a half-write.
     */
    private static void atomicWrite(Path file, byte[] content) {
        Path target = file.toAbsolutePath().normalize();
        Path temp;
        try {
            temp = Files.createTempFile(target.getParent(), target.getFileName().toString(),
                    ".nop-lint-fix.tmp");
        } catch (IOException e) {
            throw new NopLintException("fix temp file creation failed next to '" + file + "': "
                    + e.getMessage() + " (the file keeps its previous content)", e);
        }
        boolean moved = false;
        try {
            Files.write(temp, content, StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING);
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
            moved = true;
        } catch (AtomicMoveNotSupportedException e) {
            throw new NopLintException("atomic fix write unsupported for file '" + file
                    + "': the filesystem does not support atomic moves (fail-closed, the file "
                    + "keeps its previous content)", e);
        } catch (IOException e) {
            throw new NopLintException("atomic fix write failed for file '" + file + "': "
                    + e.getMessage() + " (the file keeps its previous content)", e);
        } finally {
            if (!moved) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException cleanupFailure) {
                    // the primary write failure is already propagating; a
                    // leftover temp file must not mask it, only be reported
                    LOG.warn("nop.lint.fix.temp-cleanup-failed:file={},temp={}", file, temp,
                            cleanupFailure);
                }
            }
        }
    }
}
