package io.nop.ai.agent.security;

/**
 * The kind of communication channel through which an agent request arrives
 * (design §5.3). The {@link IPermissionMatrix} uses the channel type together
 * with a {@link SecurityLevel} to decide whether a tool risk level is permitted
 * for that channel.
 *
 * <table>
 *   <tr><th>Channel</th><th>Allowed levels (design §5.3)</th></tr>
 *   <tr><td>{@link #WEBUI}</td><td>STANDARD + ELEVATED + RESTRICTED</td></tr>
 *   <tr><td>{@link #API}</td><td>STANDARD + ELEVATED</td></tr>
 *   <tr><td>{@link #DM}</td><td>STANDARD + ELEVATED</td></tr>
 *   <tr><td>{@link #GROUP}</td><td>STANDARD</td></tr>
 *   <tr><td>unknown / null</td><td>STANDARD (fail-closed)</td></tr>
 * </table>
 *
 * <p>The fail-closed semantics for unknown channels is implemented by concrete
 * {@link IPermissionMatrix} implementations; the {@link PassThroughPermissionMatrix}
 * default allows all levels for all channels.
 */
public enum ChannelKind {
    WEBUI,
    API,
    DM,
    GROUP
}
