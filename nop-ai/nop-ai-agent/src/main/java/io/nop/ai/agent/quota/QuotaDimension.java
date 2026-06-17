package io.nop.ai.agent.quota;

/**
 * Count-based concurrent quota dimensions enforced by the central
 * {@link IResourceGuard} gateway (vision §5.2 / §10 Phase 5 foundational
 * slice, plan 234).
 *
 * <p>Each dimension identifies one count-based concurrent quantity that the
 * guard adjudicates before the enforcement point mutates state. The
 * foundational slice enforces three dimensions; the remaining vision §5.2
 * dimensions (LLM rate-limit, Compaction pool, storage, per-agent token/time,
 * Fencing Token) are explicit successors that extend the same
 * {@link IResourceGuard} contract rather than introducing a new decision
 * gateway (Design Decision §1).
 *
 * <h2>In-scope dimensions</h2>
 * <ul>
 *   <li>{@link #TEAM_PARALLEL_BOUND_MEMBERS} — the number of concurrently
 *       bound ({@code isBound()==true}) members in a team. Enforced at
 *       {@code bindMemberSession}; the limit is the team's per-team override
 *       {@code TeamSpec.maxParallelMembers} ({@code <= 0} = unlimited). This
 *       is the {@code maxParallelMembers} hint→enforced upgrade.</li>
 *   <li>{@link #TEAM_MEMBERS} — the total number of members (bound + unbound)
 *       in a team. Enforced at {@code createTeam} (projected = initial member
 *       count) and {@code addMember} (projected = current + 1); the limit is
 *       the {@link QuotaConfig} global default ({@code teamMaxMembers}).</li>
 *   <li>{@link #CONCURRENT_ACTORS_PER_TENANT} — the number of concurrently
 *       active actors owned by a tenant. Enforced at
 *       {@code InMemoryActorRuntime.createActor}; the scopeKey is the tenant
 *       resolved by {@code ITenantResolver}, the limit is the
 *       {@link QuotaConfig} global default
 *       ({@code tenantMaxConcurrentActors}).</li>
 * </ul>
 *
 * <h2>Successor dimensions (out of scope, vision §5.2)</h2>
 * <ul>
 *   <li>LLM 调用频率 (rate-limit, default 100/min) — time-window semantics,
 *       successor (requires {@code IRateLimiter}).</li>
 *   <li>Compaction LLM 调用 (default 20/min) — independent rate-limit pool,
 *       successor.</li>
 *   <li>storage 配额 — successor (requires storage metrics infra).</li>
 *   <li>单 Agent 最大 Token / 最大时间 (default 200K / 30min) — successor
 *       (requires per-agent token accumulation + time budget).</li>
 *   <li>Fencing Token (monotonic counter) — successor (independent carry-over
 *       {@code L4-fencing-token}).</li>
 * </ul>
 *
 * <p>See plan 234 (L4-resource-guard-quota) and vision §5.2.
 */
public enum QuotaDimension {

    /**
     * The number of concurrently bound members in a team. Enforced at
     * {@code bindMemberSession}; limit comes from the team's
     * {@code maxParallelMembers} per-team override ({@code <= 0} = unlimited).
     */
    TEAM_PARALLEL_BOUND_MEMBERS,

    /**
     * The total number of members (bound + unbound) in a team. Enforced at
     * {@code createTeam} and {@code addMember}; limit comes from the
     * {@link QuotaConfig} global default {@code teamMaxMembers}.
     */
    TEAM_MEMBERS,

    /**
     * The number of concurrently active actors owned by a tenant. Enforced at
     * {@code InMemoryActorRuntime.createActor}; scopeKey is the tenant, limit
     * comes from the {@link QuotaConfig} global default
     * {@code tenantMaxConcurrentActors}.
     */
    CONCURRENT_ACTORS_PER_TENANT
}
