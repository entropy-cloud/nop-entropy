package io.nop.ai.agent.security;

/**
 * Categorisation of why a {@link ISandboxBackend} could not complete a
 * sandbox execution (plan 219 Phase 1). Carried by
 * {@link SandboxException#getReason()} so callers can distinguish
 * "the isolator itself is unreachable" from "the command was killed by
 * the isolator's limits" without pattern-matching on error strings.
 *
 * <p>The set is closed: any failure that does not cleanly fit one of these
 * categories is reported as {@link #CONTAINER_START_FAILED} (conservative
 * fail-closed — never silently swallowed, never falls back to host).
 */
public enum SandboxFailureReason {
    /**
     * The isolator is not reachable at all — the {@code docker} binary is
     * not on {@code PATH}, {@code Process.start()} threw
     * {@link java.io.IOException}, or the daemon connection error pattern
     * appeared in stderr. The backend cannot run anything until the
     * operator restores the isolator.
     */
    DOCKER_UNAVAILABLE,

    /**
     * The isolator is reachable but refused to start the container —
     * image missing, name collision, OCI runtime error, permission
     * denied, or an unclassified non-zero exit before the wrapped command
     * could produce observable output. Conservative bucket: when we
     * cannot precisely classify a non-zero exit, it lands here.
     */
    CONTAINER_START_FAILED,

    /**
     * The wall-time budget ({@link SandboxConfig#getWallSeconds()}) was
     * exhausted and the backend forcibly terminated the process /
     * container. This is reported as an exception (not a
     * {@link SandboxResult} with {@code timedOut=true}) when the
     * termination itself fails or the isolator reports timeout via exit
     * code 124.
     */
    TIMEOUT,

    /**
     * The isolator killed the command for exceeding a resource limit —
     * typically exit code 137 (SIGKILL) under a {@code --memory}
     * ceiling (OOM-killer). Distinct from {@link #TIMEOUT} so callers can
     * tell "the command used too much RAM" from "the command ran too long".
     */
    RESOURCE_LIMIT_EXCEEDED
}
