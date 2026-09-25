package io.nop.refactor.core;

import io.nop.api.core.exceptions.NopException;

/**
 * Module-level runtime exception for nop-refactor. Extends {@code NopException}
 * per the platform error-handling convention (module exception class mode),
 * keeping the free-form English message reachable through the framework's
 * structured error response path.
 */
public class NopRefactorException extends NopException {

    public NopRefactorException(String message) {
        super(message, null, true, true);
    }

    public NopRefactorException(String message, Throwable cause) {
        super(message, cause, true, true);
    }
}
