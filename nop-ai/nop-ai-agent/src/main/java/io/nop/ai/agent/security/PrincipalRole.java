package io.nop.ai.agent.security;

/**
 * The role of a {@link Principal} interacting with the agent (design §5.3).
 *
 * <p>An {@link #OPERATOR} may bypass {@link SecurityLevel#RESTRICTED} actions
 * that would otherwise be denied by a restrictive {@link IPermissionMatrix}
 * implementation. A {@link #USER} is subject to the full channel × level
 * matrix.
 */
public enum PrincipalRole {
    USER,
    OPERATOR
}
