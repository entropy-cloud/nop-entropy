package io.nop.lint.js;

/**
 * Module-level exception base for nop-lint-js (the module-internal tier of
 * the two-tier error strategy): English messages, never bare
 * {@code RuntimeException}. Failure semantics of the tsc bridge build on the
 * two subtypes {@code TscQueryException} and
 * {@code TscBridgeUnavailableException}; both report explicitly, never as a
 * silent skip.
 */
public class NopLintJsException extends RuntimeException {

    public NopLintJsException(String message) {
        super(message);
    }

    public NopLintJsException(String message, Throwable cause) {
        super(message, cause);
    }
}
