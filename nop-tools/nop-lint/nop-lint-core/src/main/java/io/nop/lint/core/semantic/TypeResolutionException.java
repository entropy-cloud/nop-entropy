package io.nop.lint.core.semantic;

import io.nop.lint.core.NopLintException;

/**
 * A type query that could not be answered (design 11 §5 degrade ladder,
 * level 2): unavailable backend, failed handshake, deadline exceeded, or an
 * out-of-project position. The engine treats it as the degrade signal for
 * the querying rule — the rule is counted and logged, and never continues on
 * a lower level's results (roadmap hard constraint: no L1-faked L2).
 */
public class TypeResolutionException extends NopLintException {

    public TypeResolutionException(String message) {
        super(message);
    }

    public TypeResolutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
