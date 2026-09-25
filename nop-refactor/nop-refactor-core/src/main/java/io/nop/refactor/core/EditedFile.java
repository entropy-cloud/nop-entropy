package io.nop.refactor.core;

import java.util.Objects;

/**
 * One file's before/after pair entering verification (WI5 adjudication:
 * the diff face needs the original, so the input is a triple, not just the
 * edited content).
 *
 * @param path      the file's display path; never blank
 * @param original  the pre-edit content
 * @param edited    the post-edit content the verification runs over
 * @param editCount how many edits produced this change (the caller's plan
 *                  knows; verification only aggregates)
 */
public record EditedFile(String path, byte[] original, byte[] edited, int editCount) {

    public EditedFile {
        if (path == null || path.isBlank())
            throw new NopRefactorException("EditedFile.path must be non-blank (fail-closed)");
        Objects.requireNonNull(original, "original must not be null");
        Objects.requireNonNull(edited, "edited must not be null");
        if (editCount < 0)
            throw new NopRefactorException("editCount must not be negative for '" + path + "': "
                    + editCount);
    }

    public boolean changed() {
        return !java.util.Arrays.equals(original, edited);
    }
}
