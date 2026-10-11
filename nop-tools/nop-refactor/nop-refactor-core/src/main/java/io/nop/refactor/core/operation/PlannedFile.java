package io.nop.refactor.core.operation;

import io.nop.lint.core.fix.Fix;
import io.nop.lint.core.lang.LintLanguage;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * One file's slice of the edit plan (plan 09 adjudication 1): the rewrites
 * the operation's plan phase computed for a prepared target, carried to the
 * framework's single apply point (the WI4 {@code EditPlanApplier} entry).
 * Pure data — landing is framework-owned, never the operation's job.
 */
public record PlannedFile(Path path, byte[] original, List<Fix> edits, LintLanguage language) {

    public PlannedFile {
        Objects.requireNonNull(path, "path must not be null");
        Objects.requireNonNull(original, "original must not be null");
        edits = List.copyOf(edits);
        if (edits.isEmpty()) {
            throw new io.nop.refactor.core.NopRefactorException(
                    "a planned file must carry at least one edit (an empty plan entry is a "
                            + "silent no-op; fail-closed)");
        }
        Objects.requireNonNull(language, "language must not be null");
    }
}
