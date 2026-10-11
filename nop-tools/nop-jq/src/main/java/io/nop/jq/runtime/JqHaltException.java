package io.nop.jq.runtime;

/**
 * Thrown by {@code halt}/{@code halt_error} with the requested process exit code.
 *
 * <p>Deliberately extends {@link RuntimeException} directly, NOT
 * {@link JqRuntimeException}: halt is jq-level program termination, never a
 * catchable error value. Per jq 1.7.1 semantics {@code try (halt) catch ...}
 * must not intercept it, so it must stay outside the {@link JqRuntimeException}
 * hierarchy that language-level error handlers (try/catch, {@code ?},
 * {@code ?//}, path-eval recovery) match on.
 */
public class JqHaltException extends RuntimeException {
    private final int exitCode;

    public JqHaltException(String message, int exitCode) {
        super(message);
        this.exitCode = exitCode;
    }

    public int exitCode() {
        return exitCode;
    }

    /** Termination text for consumers at system boundaries (e.g. tool executors). */
    public String errorMessage() {
        return getMessage() == null ? "halt" : getMessage();
    }
}
