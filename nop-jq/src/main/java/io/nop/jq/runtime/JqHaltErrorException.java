package io.nop.jq.runtime;

/**
 * The catchable error thrown by jq's {@code halt_error}/{@code halt_error(code)}
 * builtin. Carries the process exit code that applies only when the error
 * terminates the program uncaught; when intercepted by jq-level error handlers
 * (try/catch, {@code ?}, {@code ?//}) it behaves as a normal error whose value
 * is the halt_error input, matching jq 1.7.1 semantics.
 *
 * <p>Contrast with {@link JqHaltException}, which models the uncatchable
 * {@code halt} termination and must never be caught by language-level handlers.
 */
public class JqHaltErrorException extends JqRuntimeException {
    private final int exitCode;

    public JqHaltErrorException(JqValue errorValue, int exitCode) {
        super(JqPrinter.tostring(errorValue), errorValue);
        this.exitCode = exitCode;
    }

    public int exitCode() {
        return exitCode;
    }
}
