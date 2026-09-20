package io.nop.treesitter;

import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;

/**
 * Module-level runtime exception for nop-treesitter internal errors.
 *
 * <p>Thrown when an arena / symbol-table invariant is violated (invalid node id,
 * uninterned symbol id, ...) instead of silently returning a null / default value,
 * so that broken call paths fail fast. Extends {@link NopException} per the
 * platform error-handling convention (module exception class mode), keeping the
 * free-form English message reachable through the framework's structured error
 * response path.</p>
 */
public class TreeSitterException extends NopException {

    public TreeSitterException(String message) {
        super(message, null, true, true);
    }

    public TreeSitterException(String message, Throwable cause) {
        super(message, cause, true, true);
    }

    public TreeSitterException(ErrorCode errorCode) {
        super(errorCode);
    }

    public TreeSitterException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }
}
