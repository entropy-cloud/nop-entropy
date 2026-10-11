package io.nop.lint.core;

import io.nop.api.core.exceptions.NopException;

/**
 * Module-level runtime exception for nop-lint. Extends {@code NopException}
 * per the platform error-handling convention (module exception class mode),
 * keeping the free-form English message reachable through the framework's
 * structured error response path.
 */
public class NopLintException extends NopException {

    public NopLintException(String message) {
        super(message, null, true, true);
    }

    public NopLintException(String message, Throwable cause) {
        super(message, cause, true, true);
    }
}
