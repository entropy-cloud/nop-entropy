package io.nop.ai.agent.security;

/**
 * Three-tier security level for tool/action risk classification (design §5.1).
 *
 * <p>This is a shared value type consumed by {@link IPermissionMatrix} (this
 * plan, L2-14) and produced by the future {@code ISecurityLevelResolver}
 * (L2-13). Defined here because the matrix contract requires it as an input
 * parameter; L2-13 will reuse this enum when landed.
 *
 * <table>
 *   <tr><th>Level</th><th>Semantics</th><th>Typical operations</th></tr>
 *   <tr><td>{@link #STANDARD}</td><td>Normal execution, no extra restrictions</td><td>fs.read, fs.list</td></tr>
 *   <tr><td>{@link #ELEVATED}</td><td>Requires confirmation, tighter resources</td><td>fs.write, shell.exec (trusted source)</td></tr>
 *   <tr><td>{@link #RESTRICTED}</td><td>Requires approval, minimum privileges</td><td>shell.exec (untrusted source), network.fetch</td></tr>
 * </table>
 */
public enum SecurityLevel {
    STANDARD,
    ELEVATED,
    RESTRICTED
}
