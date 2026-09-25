package io.nop.lint.core.fix;

import io.nop.lint.core.NopLintException;
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
 * The diagnostic-agnostic edit-plan application entry (nop-refactor WI4,
 * design 03 §3 second 2026-09-25 annotation): one explicit, ordered edit
 * list goes in, one per-file outcome comes out — conflict merge through the
 * single {@link Fixer#merge} authority, splice, atomic write (skipped in
 * dry-run), re-parse guard, rollback on a syntax break. No diagnostics are
 * consumed, no lint is run, there is no multipass loop — an edit plan exists
 * before any diagnosis (the refactor face), which is exactly what the
 * diagnosis-driven {@link FixApplier} must not assume.
 *
 * <p>The mechanical core is single-sourced here: {@code FixApplier}'s
 * multipass loop drives the same merge→apply→guard→write sequence through
 * {@link #apply} once per pass and remains the only re-lint driver; the
 * refactor face and the WI9 operation framework consume the same entry, so
 * there is no second application path. The guard outcome is a returned
 * ending, not an exception: a pass whose result introduces more error-recovery
 * nodes than the content it started from restores the input bytes (unless
 * dry-run) and reports {@code rolledBack=true} with a net surviving edit
 * count of 0 — the same accounting the multipass loop's {@code applied -=}
 * produced. Every abnormal exit stays explicit: write failures throw
 * {@link NopLintException} (the temp file is cleaned up, the target keeps
 * its previous content), and an edit targeting a range outside the source is
 * a stale-range fail-closed error, never a silent clip.</p>
 */
public final class EditPlanApplier {

    private static final Logger LOG = LoggerFactory.getLogger(EditPlanApplier.class);

    /**
     * One file's outcome: the content the run leaves behind (the input in
     * dry-run and after a rollback), whether the re-parse guard rolled the
     * edits back, the edits that survived on disk (or, in dry-run, the
     * proposed ones — 0 after a rollback), and the candidates dropped as
     * overlapping conflicts.
     */
    public record EditPlanResult(byte[] finalSource, boolean rolledBack, int appliedEdits,
                                 int skippedConflicts) {
    }

    private EditPlanApplier() {
    }

    /**
     * Applies one explicit edit plan to {@code source}. {@code dryRun = true}
     * computes the merged result in memory and never touches {@code file};
     * {@code false} writes atomically and restores the input content when the
     * re-parse guard detects a syntax break.
     */
    public static EditPlanResult apply(Path file, byte[] source, List<Fix> edits,
                                       LintLanguage language, boolean dryRun) {
        Objects.requireNonNull(file, "file must not be null");
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(edits, "edits must not be null");
        Objects.requireNonNull(language, "language must not be null");

        Fixer.MergeResult merge = Fixer.merge(edits);
        if (merge.applied().isEmpty()) {
            return new EditPlanResult(source, false, 0, merge.skippedConflicts());
        }

        byte[] next = spliceAll(source, merge.applied());
        if (!dryRun) {
            atomicWrite(file, next);
        }

        int currentErrorNodes = countErrorNodes(language.parse(source));
        int nextErrorNodes = countErrorNodes(language.parse(next));
        if (nextErrorNodes > currentErrorNodes) {
            if (!dryRun) {
                atomicWrite(file, source);
            }
            // the edits did not survive — the net surviving count is 0, the
            // same accounting the multipass loop's `applied -=` produced
            return new EditPlanResult(source, true, 0, merge.skippedConflicts());
        }
        return new EditPlanResult(next, false, merge.applied().size(), merge.skippedConflicts());
    }

    /**
     * Splices the merged edits into the source. The merge returns them
     * ascending by range start, so the splices run in reverse and every
     * earlier offset stays valid.
     */
    private static byte[] spliceAll(byte[] source, List<Fix> fixes) {
        byte[] out = source;
        for (int i = fixes.size() - 1; i >= 0; i--) {
            Fix fix = fixes.get(i);
            byte[] replacement = fix.replacement().getBytes(StandardCharsets.UTF_8);
            int start = fix.range().startByte();
            int end = fix.range().endByte();
            if (start < 0 || end > out.length || start > end) {
                throw new NopLintException("edit '" + fix.ruleId() + "' targets byte range ["
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
     * increase over the pre-edit content, so a source that already carries
     * recovery nodes never false-positives its own edits.
     */
    private static int countErrorNodes(LintTree tree) {
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
            throw new NopLintException("edit temp file creation failed next to '" + file + "': "
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
            throw new NopLintException("atomic edit write unsupported for file '" + file
                    + "': the filesystem does not support atomic moves (fail-closed, the file "
                    + "keeps its previous content)", e);
        } catch (IOException e) {
            throw new NopLintException("atomic edit write failed for file '" + file + "': "
                    + e.getMessage() + " (the file keeps its previous content)", e);
        } finally {
            if (!moved) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException cleanupFailure) {
                    // the primary write failure is already propagating; a
                    // leftover temp file must not mask it, only be reported
                    LOG.warn("nop.lint.edit.temp-cleanup-failed:file={},temp={}", file, temp,
                            cleanupFailure);
                }
            }
        }
    }
}
