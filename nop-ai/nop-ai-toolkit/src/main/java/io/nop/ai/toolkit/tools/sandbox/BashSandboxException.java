package io.nop.ai.toolkit.tools.sandbox;

/**
 * Fail-closed exception raised by a {@link IBashSandbox} backend when it cannot launch, observe,
 * or terminate the requested command, or when the requested working directory / environment is
 * rejected before launch (plan 335 DR-3a).
 *
 * <p><b>Fail-closed contract</b>: a functional backend (e.g. {@link DockerBashSandbox}) that
 * cannot reach its isolation layer MUST raise this exception and MUST NOT silently fall back to
 * host execution. {@link BashExecutor} treats this exception as a hard error result — it never
 * retries on the host shell.
 */
public class BashSandboxException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final BashSandboxFailureReason reason;

    public BashSandboxException(BashSandboxFailureReason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public BashSandboxException(BashSandboxFailureReason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public BashSandboxFailureReason getReason() {
        return reason;
    }
}
