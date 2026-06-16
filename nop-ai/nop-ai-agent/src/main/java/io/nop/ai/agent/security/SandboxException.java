package io.nop.ai.agent.security;

import io.nop.ai.agent.engine.NopAiAgentException;

/**
 * Fail-closed exception raised by {@link ISandboxBackend} when the sandbox
 * cannot launch, observe, or terminate the requested command (plan 219
 * Phase 1). Carries a {@link SandboxFailureReason} so callers can branch
 * on the failure category without inspecting the message.
 *
 * <p><b>Fail-closed contract</b>: a functional backend (e.g.
 * {@code DockerSandboxBackend}) that observes any of the failure modes
 * described in {@link SandboxFailureReason} MUST raise this exception and
 * MUST NOT silently fall back to host execution. The shipped
 * {@link NoOpSandboxBackend} default is not a fallback — it executes on
 * the host by definition (Layer 1 designable baseline, design §7.1) and
 * raises this exception only when host-level constraints (wall-time,
 * output-size) are violated in a way that prevents a clean
 * {@link SandboxResult}.
 */
public class SandboxException extends NopAiAgentException {
    private static final long serialVersionUID = 1L;

    private final SandboxFailureReason reason;

    public SandboxException(SandboxFailureReason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public SandboxException(SandboxFailureReason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public SandboxFailureReason getReason() {
        return reason;
    }
}
