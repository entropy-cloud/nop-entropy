package io.nop.jq.runtime;

/**
 * Thrown by halt_error with the requested process exit code.
 */
public class JqHaltException extends JqRuntimeException {
    private final int exitCode;

    public JqHaltException(String message, int exitCode) {
        super(message);
        this.exitCode = exitCode;
    }

    public int exitCode() {
        return exitCode;
    }
}
