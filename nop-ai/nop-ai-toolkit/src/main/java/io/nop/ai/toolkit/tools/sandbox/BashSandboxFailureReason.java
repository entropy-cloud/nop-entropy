package io.nop.ai.toolkit.tools.sandbox;

/**
 * Categorisation of why a {@link IBashSandbox} could not complete an execution (plan 335 DR-3a).
 * Carried by {@link BashSandboxException#getReason()} so callers can distinguish "the isolator
 * itself is unreachable" from "the command was killed by the isolator's limits" without
 * pattern-matching on error strings.
 *
 * <p>The set is closed: any failure that does not cleanly fit one of these categories is reported
 * as {@link #CONTAINER_START_FAILED} (conservative fail-closed — never silently swallowed, never
 * falls back to host).
 */
public enum BashSandboxFailureReason {
    BACKEND_UNAVAILABLE,
    CONTAINER_START_FAILED,
    TIMEOUT,
    RESOURCE_LIMIT_EXCEEDED,
    HOST_PATH_NOT_ALLOWED,
    INVALID_ENVIRONMENT_VARIABLE
}
