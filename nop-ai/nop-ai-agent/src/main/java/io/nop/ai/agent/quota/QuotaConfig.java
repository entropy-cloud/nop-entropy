package io.nop.ai.agent.quota;

import java.util.Objects;

/**
 * Immutable configuration object holding the global default limits for the
 * count-based concurrent quota dimensions enforced by
 * {@link DefaultResourceGuard} (plan 234, vision §5.2).
 *
 * <p>Each field is the <em>global default</em> for its dimension. A per-scope
 * override (e.g. a team's {@code maxParallelMembers}) takes precedence over
 * the global default when it is {@code > 0}; a {@code <= 0} value (whether
 * from the config default or an override) means <em>unlimited</em> — the
 * guard returns allow (Design Decision §3).
 *
 * <p>The defaults match vision §5.2:
 * <ul>
 *   <li>{@code teamMaxMembers} = 8 (团队最大成员数).</li>
 *   <li>{@code tenantMaxConcurrentActors} = 10 (并发 Actor 数).</li>
 * </ul>
 *
 * <p>The config is a plain immutable object constructed by the integrator
 * (or injected from Nop config {@code @cfg:ai.agent.quota.*}); it is not
 * itself a config bean. This keeps the guard testable with a low limit
 * without touching the config system.
 *
 * <p>See plan 234 (L4-resource-guard-quota), Design Decision §8.
 */
public final class QuotaConfig {

    /** Vision §5.2 default for the maximum members per team. */
    public static final int DEFAULT_TEAM_MAX_MEMBERS = 8;

    /** Vision §5.2 default for the maximum concurrent actors per tenant. */
    public static final int DEFAULT_TENANT_MAX_CONCURRENT_ACTORS = 10;

    private final int teamMaxMembers;
    private final int tenantMaxConcurrentActors;

    /**
     * Build a config with the vision §5.2 defaults
     * ({@code teamMaxMembers=8}, {@code tenantMaxConcurrentActors=10}).
     */
    public QuotaConfig() {
        this(DEFAULT_TEAM_MAX_MEMBERS, DEFAULT_TENANT_MAX_CONCURRENT_ACTORS);
    }

    /**
     * Build a config with explicit limits. {@code <= 0} means unlimited for
     * that dimension.
     *
     * @param teamMaxMembers            global default for {@link QuotaDimension#TEAM_MEMBERS}
     * @param tenantMaxConcurrentActors global default for
     *                                  {@link QuotaDimension#CONCURRENT_ACTORS_PER_TENANT}
     */
    public QuotaConfig(int teamMaxMembers, int tenantMaxConcurrentActors) {
        this.teamMaxMembers = teamMaxMembers;
        this.tenantMaxConcurrentActors = tenantMaxConcurrentActors;
    }

    /**
     * @return the global default for {@link QuotaDimension#TEAM_MEMBERS}.
     *         {@code <= 0} means unlimited.
     */
    public int getTeamMaxMembers() {
        return teamMaxMembers;
    }

    /**
     * @return the global default for
     *         {@link QuotaDimension#CONCURRENT_ACTORS_PER_TENANT}.
     *         {@code <= 0} means unlimited.
     */
    public int getTenantMaxConcurrentActors() {
        return tenantMaxConcurrentActors;
    }

    /**
     * Resolve the global default limit for a dimension.
     *
     * @param dimension the quota dimension (non-null)
     * @return the configured global default ({@code <= 0} = unlimited)
     */
    public int defaultLimitFor(QuotaDimension dimension) {
        Objects.requireNonNull(dimension, "dimension");
        switch (dimension) {
            case TEAM_MEMBERS:
                return teamMaxMembers;
            case CONCURRENT_ACTORS_PER_TENANT:
                return tenantMaxConcurrentActors;
            // TEAM_PARALLEL_BOUND_MEMBERS has no global default — its limit is
            // always the per-team override (maxParallelMembers). Return 0
            // (unlimited) as the fallback; the enforcement point always
            // supplies the override.
            case TEAM_PARALLEL_BOUND_MEMBERS:
            default:
                return 0;
        }
    }

    @Override
    public String toString() {
        return "QuotaConfig{teamMaxMembers=" + teamMaxMembers
                + ", tenantMaxConcurrentActors=" + tenantMaxConcurrentActors + '}';
    }
}
