package io.nop.ai.agent.quota;

/**
 * Shipped no-op default for {@link IResourceGuard} (plan 234, Design Decision
 * §2).
 *
 * <p>{@link #checkConcurrent} <strong>always returns {@code allow}</strong>
 * — an explicit allow decision carrying the caller's dimension/scopeKey/limit/
 * projectedCount verbatim. This is the constructor default for the functional
 * impls ({@code InMemoryTeamManager} / {@code DbTeamManager} /
 * {@code InMemoryActorRuntime}), so integrators see zero behaviour regression
 * unless they explicitly wire a {@link DefaultResourceGuard}: every team/actor
 * operation that previously went through with no quota check now still goes
 * through, with no extra overhead.
 *
 * <p><b>Why allow and not deny?</b> The NoOp semantics is "quota enforcement
 * not enabled = do not add any restriction". A functional teamManager /
 * actorRuntime + NoOp guard is the backward-compatible configuration that
 * preserves the pre-quota behaviour (all bindings / creations proceed
 * regardless of count). The {@code allow} return value explicitly encodes
 * "permitted" — it is not a swallowed null / silent skip (Minimum Rules #24).
 *
 * <p>See plan 234 (L4-resource-guard-quota), Design Decision §2.
 */
public final class NoOpResourceGuard implements IResourceGuard {

    private static final NoOpResourceGuard INSTANCE = new NoOpResourceGuard();

    private NoOpResourceGuard() {
    }

    /**
     * @return the singleton NoOp guard instance
     */
    public static NoOpResourceGuard noOp() {
        return INSTANCE;
    }

    @Override
    public QuotaDecision checkConcurrent(QuotaDimension dimension, String scopeKey,
                                         int projectedCount, int overrideLimit) {
        // Explicit allow carrying the caller's context verbatim: "quota not
        // enabled → no restriction added". Not a silent skip — the decision
        // object is returned and the caller proceeds normally (Minimum Rules
        // #24).
        return QuotaDecision.allow(dimension, scopeKey, overrideLimit, projectedCount);
    }
}
