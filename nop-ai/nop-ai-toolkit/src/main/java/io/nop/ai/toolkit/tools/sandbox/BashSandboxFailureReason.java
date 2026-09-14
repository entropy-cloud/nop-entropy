package io.nop.ai.toolkit.tools.sandbox;

/**
 * Categorisation of why a {@link IBashSandbox} could not complete an execution (plan 335 DR-3a).
 * Carried by {@link BashSandboxException#getReason()} so callers can distinguish "the isolator
 * itself is unreachable" from "the command was killed by the isolator's limits" without
 * pattern-matching on error strings.
 *
 * <p>The set is closed over sandbox/infrastructure failures only: unreachable backend, container
 * start failure, timeout, resource limit, path/env rejection. An ordinary non-zero command exit
 * code is NOT a sandbox failure — {@link DockerBashSandbox#classifyFailure} returns {@code null}
 * for it and the caller receives the real exit code and output through the normal
 * {@link BashSandboxResult} path (never swallowed, never misclassified as container start failure,
 * never falls back to host).
 */
public enum BashSandboxFailureReason {
    BACKEND_UNAVAILABLE,
    CONTAINER_START_FAILED,
    TIMEOUT,
    RESOURCE_LIMIT_EXCEEDED,
    HOST_PATH_NOT_ALLOWED,
    INVALID_ENVIRONMENT_VARIABLE
}
