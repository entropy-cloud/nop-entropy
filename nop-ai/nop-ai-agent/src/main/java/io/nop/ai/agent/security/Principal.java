package io.nop.ai.agent.security;

import java.util.Objects;

/**
 * Immutable identity value object carried into {@link IPermissionMatrix}
 * decisions (design §5.3).
 *
 * <table>
 *   <tr><th>Field</th><th>Meaning</th></tr>
 *   <tr><td>{@code role}</td><td>{@link PrincipalRole} — {@code OPERATOR} may bypass {@link SecurityLevel#RESTRICTED}</td></tr>
 *   <tr><td>{@code channelId}</td><td>Identifier for per-channel override</td></tr>
 *   <tr><td>{@code tenantId}</td><td>Multi-tenant identifier (Nop {@code IContext} natively supports this)</td></tr>
 * </table>
 *
 * <p>Pure value definition, no behaviour. Forward-compatible with the future
 * dispatch-path consultation (L2-13 successor) which will source these fields
 * from {@code AgentExecutionContext} / {@code IContext}.
 */
public final class Principal {

    private final PrincipalRole role;
    private final String channelId;
    private final String tenantId;

    public Principal(PrincipalRole role, String channelId, String tenantId) {
        this.role = role;
        this.channelId = channelId;
        this.tenantId = tenantId;
    }

    /**
     * Convenience factory for a {@link PrincipalRole#USER} principal with no
     * channel or tenant context.
     */
    public static Principal user() {
        return new Principal(PrincipalRole.USER, null, null);
    }

    /**
     * Convenience factory for an {@link PrincipalRole#OPERATOR} principal with
     * no channel or tenant context.
     */
    public static Principal operator() {
        return new Principal(PrincipalRole.OPERATOR, null, null);
    }

    public PrincipalRole getRole() {
        return role;
    }

    public String getChannelId() {
        return channelId;
    }

    public String getTenantId() {
        return tenantId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Principal principal = (Principal) o;
        return role == principal.role
                && Objects.equals(channelId, principal.channelId)
                && Objects.equals(tenantId, principal.tenantId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(role, channelId, tenantId);
    }

    @Override
    public String toString() {
        return "Principal{" +
                "role=" + role +
                ", channelId='" + channelId + '\'' +
                ", tenantId='" + tenantId + '\'' +
                '}';
    }
}
