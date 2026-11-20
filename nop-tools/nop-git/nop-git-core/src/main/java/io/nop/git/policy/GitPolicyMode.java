package io.nop.git.policy;

/**
 * Execution mode of the policy engine (design 03 section 3): observe records
 * the hypothetical enforce verdict without blocking; enforce blocks denies.
 */
public enum GitPolicyMode {
    OBSERVE, ENFORCE
}
