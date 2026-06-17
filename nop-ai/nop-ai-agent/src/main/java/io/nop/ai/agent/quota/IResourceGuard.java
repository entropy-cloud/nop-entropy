package io.nop.ai.agent.quota;

/**
 * Central quota-decision gateway for count-based concurrent quotas
 * (vision §4.2 {@code ResourceGuard} = successor Phase 5 platform component;
 * plan 234 delivers the foundational count-based slice).
 *
 * <p>An enforcement point (e.g. {@code InMemoryTeamManager.bindMemberSession},
 * {@code InMemoryActorRuntime.createActor}) calls {@link #checkConcurrent}
 * <em>before</em> it mutates state, supplying the dimension, the scope key,
 * the projected post-operation count, and an optional per-scope override
 * limit. The guard returns an immutable {@link QuotaDecision}; the
 * enforcement point fail-fast throws
 * {@link io.nop.ai.agent.engine.NopAiAgentException} on a denial (Design
 * Decision §6 — no silent skip).
 *
 * <h2>Limit resolution (Design Decision §3)</h2>
 * <ul>
 *   <li>If {@code overrideLimit > 0}, the override is used (e.g. a team's
 *       {@code maxParallelMembers} per-team override).</li>
 *   <li>Otherwise ({@code overrideLimit <= 0}), the {@link QuotaConfig}
 *       global default for the dimension is used.</li>
 *   <li>A resolved limit of {@code <= 0} means <em>unlimited</em> — the guard
 *       returns allow (consistent with {@code maxParallelMembers}
 *       {@code <= 0} = unlimited semantics).</li>
 * </ul>
 *
 * <h2>Shipped defaults</h2>
 * <ul>
 *   <li>{@link NoOpResourceGuard} — singleton shipped default, always returns
 *       {@code allow}. This is the functional-impls' constructor default so
 *       that {@code InMemoryTeamManager} / {@code DbTeamManager} /
 *       {@code InMemoryActorRuntime} see zero behaviour regression unless an
 *       integrator wires a {@link DefaultResourceGuard} (Design Decision §2).
 *       The NoOp allow is an explicit decision object, not a swallowed null
 *       / silent skip (Minimum Rules #24).</li>
 *   <li>{@link DefaultResourceGuard} — functional implementation holding a
 *       {@link QuotaConfig}; resolves the limit per Design Decision §3 and
 *       denies when {@code projectedCount > limit} (limit {@code > 0}).</li>
 * </ul>
 *
 * <p>No engine-top-level {@code setResourceGuard} field is added in this plan:
 * the in-scope dimensions are all decided inside the functional impls, so the
 * engine's existing call paths (createTeam/addMember/bindMemberSession/
 * createActor) are unchanged. An engine-top-level guard field is reserved for
 * the LLM rate-limit successor (Design Decision §2).
 *
 * <p>See plan 234 (L4-resource-guard-quota), Design Decisions §1 / §2 / §3,
 * and vision §5.2.
 */
public interface IResourceGuard {

    /**
     * Adjudicate a count-based concurrent quota before the enforcement point
     * mutates state.
     *
     * @param dimension      the quota dimension to adjudicate (non-null)
     * @param scopeKey       the scope the count applies to (e.g. teamId for
     *                       team dimensions, tenant for actor dimension); the
     *                       decision carries it back for an accurate denial
     *                       reason
     * @param projectedCount the projected post-operation count (e.g. current
     *                       bound members + 1)
     * @param overrideLimit  a per-scope override limit; {@code > 0} = use this
     *                       override, {@code <= 0} = fall back to the
     *                       {@link QuotaConfig} global default for the
     *                       dimension
     * @return an immutable {@link QuotaDecision}; never {@code null} (Minimum
     *         Rules #24 — a deny is an explicit decision object, not a
     *         swallowed null)
     */
    QuotaDecision checkConcurrent(QuotaDimension dimension, String scopeKey,
                                  int projectedCount, int overrideLimit);
}
