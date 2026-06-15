package io.nop.ai.agent.security;

/**
 * Shipped default {@link IPermissionMatrix} implementing the design §5.3
 * channel × security-level matrix with a <b>usability-safe null-channel</b>
 * variant (design doc §4.9 decision 2). This is the engine default,
 * replacing the former {@link PassThroughPermissionMatrix} default
 * (plan 200).
 *
 * <p><b>Channel × level matrix</b> (design §5.3):
 *
 * <table>
 *   <tr><th>Channel</th><th>Allowed levels</th></tr>
 *   <tr><td>WEBUI</td><td>STANDARD + ELEVATED + RESTRICTED</td></tr>
 *   <tr><td>API</td><td>STANDARD + ELEVATED</td></tr>
 *   <tr><td>DM</td><td>STANDARD + ELEVATED</td></tr>
 *   <tr><td>GROUP</td><td>STANDARD</td></tr>
 *   <tr><td>null / unknown</td><td>STANDARD + ELEVATED (deny RESTRICTED only)</td></tr>
 * </table>
 *
 * <p><b>Usability-safe null channel</b>: the design §5.3 table specifies
 * "STANDARD only (fail-closed)" for unknown/null channels. However, as the
 * engine default, a fully fail-closed null channel would deny ELEVATED
 * operations (e.g. trusted shell.exec classified as ELEVATED by
 * {@link DefaultSecurityLevelResolver}) whenever no channel is explicitly
 * set — a common scenario in tests and simple integrations. This default
 * therefore allows STANDARD + ELEVATED for null channels while still
 * denying RESTRICTED (the meaningful security boundary). Integrators who
 * need the stricter "STANDARD only" fail-closed behavior can register a
 * custom matrix implementation.
 *
 * <p><b>OPERATOR bypass</b>: a {@link PrincipalRole#OPERATOR} bypasses
 * {@link SecurityLevel#RESTRICTED} restrictions, consistent with design §5.3.
 *
 * <p>{@link PassThroughPermissionMatrix} is retained as a public opt-in for
 * integrators who need the "allow all" behavior.
 */
public final class DefaultPermissionMatrix implements IPermissionMatrix {

    @Override
    public MatrixDecision check(ChannelKind channel, Principal principal, SecurityLevel level) {
        if (level == SecurityLevel.RESTRICTED) {
            boolean isOperator = principal != null && principal.getRole() == PrincipalRole.OPERATOR;
            if (isOperator) {
                return MatrixDecision.allow();
            }
            if (channel == ChannelKind.WEBUI) {
                return MatrixDecision.allow();
            }
            return MatrixDecision.deny(channel, level,
                    "Security level " + level + " is not permitted for channel "
                            + (channel != null ? channel.name() : "unknown/null"));
        }
        if (level == SecurityLevel.ELEVATED) {
            if (channel == ChannelKind.GROUP) {
                return MatrixDecision.deny(channel, level,
                        "Security level ELEVATED is not permitted for GROUP channel");
            }
            return MatrixDecision.allow();
        }
        return MatrixDecision.allow();
    }
}
