package io.nop.refactor.core;

import io.nop.api.core.exceptions.NopException;

import java.util.List;

/**
 * Module-level runtime exception for nop-refactor. Extends {@code NopException}
 * per the platform error-handling convention (module exception class mode),
 * keeping the free-form English message reachable through the framework's
 * structured error response path.
 *
 * <p>Partial-landing payload (plan 2531 W-08, adjudication 5(b)): when a
 * multi-file apply fails after some files already landed, the exception
 * carries the landed file list and the not-attempted count as machine-readable
 * fields — a caller determines the landed set without parsing the message
 * text. The message stays the human-readable contract; landing is fail-loud
 * per file, never batch-atomic, and crash recovery is undo via VCS.</p>
 */
public class NopRefactorException extends NopException {

    /**
     * The files whose rewritten content already landed when the failure
     * surfaced (empty when nothing had landed before the failure).
     */
    private final List<String> landedFiles;

    /**
     * How many planned files were never attempted because the run aborted
     * before reaching them (-1 when the count is not meaningful for this
     * failure, e.g. a plain message-only construction).
     */
    private final int notAttemptedCount;

    public NopRefactorException(String message) {
        this(message, null, List.of(), -1);
    }

    public NopRefactorException(String message, Throwable cause) {
        this(message, cause, List.of(), -1);
    }

    public NopRefactorException(String message, Throwable cause, List<String> landedFiles,
                                int notAttemptedCount) {
        super(message, cause, true, true);
        this.landedFiles = landedFiles == null ? List.of() : List.copyOf(landedFiles);
        this.notAttemptedCount = notAttemptedCount;
    }

    /**
     * The files whose rewritten content already landed when the failure
     * surfaced — machine-readable, never parsed out of the message.
     */
    public List<String> landedFiles() {
        return landedFiles;
    }

    /**
     * The planned files the run never attempted (see the field contract).
     */
    public int notAttemptedCount() {
        return notAttemptedCount;
    }
}
