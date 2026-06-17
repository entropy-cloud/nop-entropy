package io.nop.ai.agent.quota;

import java.util.Objects;

/**
 * Functional {@link IResourceGuard} that enforces count-based concurrent
 * quotas against a {@link QuotaConfig} (plan 234, Design Decision §3).
 *
 * <h2>Adjudication flow (per {@code checkConcurrent} call)</h2>
 * <ol>
 *   <li>Resolve the limit:
 *     <ul>
 *       <li>If {@code overrideLimit > 0}, use the override (per-scope, e.g. a
 *           team's {@code maxParallelMembers}).</li>
 *       <li>Otherwise use the {@link QuotaConfig} global default for the
 *           dimension (e.g. {@code teamMaxMembers} / {@code tenantMaxConcurrentActors}).</li>
 *     </ul>
 *   </li>
 *   <li>If the resolved limit is {@code <= 0} (unlimited), return
 *       {@code allow} — consistent with {@code maxParallelMembers <= 0} =
 *       unlimited semantics. This is an explicit allow, not a silent skip
 *       (Minimum Rules #24).</li>
 *   <li>Otherwise compare {@code projectedCount} to the resolved limit:
 *     <ul>
 *       <li>{@code projectedCount <= limit} → {@code allow}.</li>
 *       <li>{@code projectedCount > limit} → {@code deny} with an English
 *           reason citing dimension/scopeKey/limit/projectedCount.</li>
 *     </ul>
 *   </li>
 * </ol>
 *
 * <p>The guard holds an immutable {@link QuotaConfig} (constructed/injected
 * by the integrator, e.g. from {@code @cfg:ai.agent.quota.*}); it is stateless
 * beyond that config — counts are supplied by the enforcement point, not
 * tracked internally. This keeps the guard trivially thread-safe and testable
 * with a low limit.
 *
 * <p>See plan 234 (L4-resource-guard-quota), Design Decisions §3 / §8, vision
 * §5.2.
 */
public final class DefaultResourceGuard implements IResourceGuard {

    private final QuotaConfig config;

    /**
     * Construct a functional guard backed by the given config.
     *
     * @param config the quota configuration (non-null)
     */
    public DefaultResourceGuard(QuotaConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    /**
     * Construct a functional guard with the vision §5.2 default config
     * ({@code teamMaxMembers=8}, {@code tenantMaxConcurrentActors=10}).
     */
    public DefaultResourceGuard() {
        this(new QuotaConfig());
    }

    @Override
    public QuotaDecision checkConcurrent(QuotaDimension dimension, String scopeKey,
                                         int projectedCount, int overrideLimit) {
        Objects.requireNonNull(dimension, "dimension");

        int limit = overrideLimit > 0
                ? overrideLimit
                : config.defaultLimitFor(dimension);

        // <= 0 = unlimited (consistent with maxParallelMembers semantics).
        if (limit <= 0) {
            return QuotaDecision.allow(dimension, scopeKey, limit, projectedCount);
        }

        if (projectedCount <= limit) {
            return QuotaDecision.allow(dimension, scopeKey, limit, projectedCount);
        }

        return QuotaDecision.deny(dimension, scopeKey, limit, projectedCount,
                "quota exceeded: dimension=" + dimension
                        + ", scopeKey=" + scopeKey
                        + ", projectedCount=" + projectedCount
                        + " exceeds limit=" + limit);
    }
}
