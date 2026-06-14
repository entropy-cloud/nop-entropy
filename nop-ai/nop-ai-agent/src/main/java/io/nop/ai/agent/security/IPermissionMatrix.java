package io.nop.ai.agent.security;

/**
 * Layer 2 policy-extension contract: the channel × security-level permission
 * matrix (design §5.3). Given the communication {@link ChannelKind}, the
 * {@link Principal} identity, and the {@link SecurityLevel} of the action
 * being considered, the matrix returns an allow/deny {@link MatrixDecision}.
 *
 * <p><b>Channel × level matrix</b> (design §5.3):
 *
 * <table>
 *   <tr><th>Channel</th><th>Allowed levels</th></tr>
 *   <tr><td>WEBUI</td><td>STANDARD + ELEVATED + RESTRICTED</td></tr>
 *   <tr><td>API</td><td>STANDARD + ELEVATED</td></tr>
 *   <tr><td>DM</td><td>STANDARD + ELEVATED</td></tr>
 *   <tr><td>GROUP</td><td>STANDARD</td></tr>
 *   <tr><td>unknown / null</td><td>STANDARD (fail-closed)</td></tr>
 * </table>
 *
 * <p>A {@link PrincipalRole#OPERATOR} may bypass {@link SecurityLevel#RESTRICTED}
 * in a restrictive implementation.
 *
 * <p><b>Default</b>: {@link PassThroughPermissionMatrix} — all channels allow
 * all levels, so engine behaviour is unchanged unless a restrictive matrix is
 * explicitly registered.
 *
 * <p><b>Dispatch-path consultation</b>: this contract surface is landed now;
 * the actual consultation call in the ReAct / tool-dispatch path is deferred to
 * the L2-13 successor ({@code ISecurityLevelResolver} produces the
 * {@link SecurityLevel} input). The pass-through default makes the wiring
 * transparent to runtime behaviour.
 */
public interface IPermissionMatrix {

    /**
     * Decide whether the given security level is permitted for the given
     * channel and principal.
     *
     * @param channel   the communication channel; may be {@code null} (unknown
     *                  channel → fail-closed, implementations should allow only
     *                  {@link SecurityLevel#STANDARD})
     * @param principal the identity; may be {@code null} (treated as
     *                  {@link PrincipalRole#USER} by restrictive implementations)
     * @param level     the security level of the action under consideration
     * @return an allow/deny decision; denials carry an auditable reason
     */
    MatrixDecision check(ChannelKind channel, Principal principal, SecurityLevel level);
}
