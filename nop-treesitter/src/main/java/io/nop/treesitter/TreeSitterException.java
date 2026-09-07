package io.nop.treesitter;

/**
 * Module-level runtime exception for nop-treesitter internal errors.
 *
 * <p>Thrown when an arena / symbol-table invariant is violated (invalid node id,
 * uninterned symbol id, ...) instead of silently returning a null / default value,
 * so that broken call paths fail fast.</p>
 */
public class TreeSitterException extends RuntimeException {

    public TreeSitterException(String message) {
        super(message);
    }

    public TreeSitterException(String message, Throwable cause) {
        super(message, cause);
    }
}
