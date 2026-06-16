package io.nop.ai.agent.contribution;

import java.util.List;
import java.util.Set;

/**
 * Registry of plugin contributions, keyed by {@link ContributionType}.
 *
 * <p>A contribution is registered by a <em>source</em> (typically a plugin id).
 * The pair {@code (type, id)} is the uniqueness key:
 * <ul>
 *   <li>registering the same {@code (type, id)} from the <em>same</em> source
 *       replaces the prior contribution (idempotent re-register);</li>
 *   <li>registering the same {@code (type, id)} from a <em>different</em>
 *       source fails fast with {@code NopAiAgentException} — no silent
 *       overwrite across sources (plan 217 裁定 2).</li>
 * </ul>
 *
 * <h2>Thread safety</h2>
 * <p>Implementations must be safe for concurrent {@link #register} /
 * {@link #unregisterSource} / query calls from multiple threads.
 * {@link InMemoryContributionRegistry} achieves this via per-type
 * {@code ConcurrentHashMap} plus synchronized compound operations.
 *
 * <h2>Assembly-time resolution</h2>
 * <p>The engine resolves registered contributions <em>once at assembly time</em>
 * (before the first LLM call of an execution, alongside skill consultation).
 * Mutations after an execution has started its assembly do not affect that
 * running execution (plan 217 裁定 3). Only {@link ContributionType#HOOK} and
 * {@link ContributionType#PROMPT} are auto-resolved into existing engine
 * extension points in this version; the other five types are queryable via
 * {@link #getContributions(ContributionType)} for consumers to consume
 * directly.
 */
public interface IContributionRegistry {

    /**
     * Register a contribution. Returns {@code true} if the contribution was
     * added or replaced (same source re-register); returns {@code false} if
     * the registry is a no-op (e.g. {@link NoOpContributionRegistry}). Throws
     * {@code NopAiAgentException} on a cross-source {@code (type, id)}
     * collision.
     */
    boolean register(Contribution contribution);

    /**
     * Remove every contribution whose source equals {@code source}.
     * Supports clean plugin teardown. No-op if the source is unknown.
     */
    void unregisterSource(String source);

    /**
     * Return all contributions of the given type, sorted by ascending
     * {@code priority} (stable order — same-priority contributions keep
     * registration order). Never null; empty when no contributions of that
     * type are registered.
     */
    List<Contribution> getContributions(ContributionType type);

    /**
     * Return all contributions of the given type contributed by {@code source},
     * sorted by ascending {@code priority}. Never null; empty when no matching
     * contributions are registered.
     */
    List<Contribution> getContributions(ContributionType type, String source);

    /**
     * Return the set of all sources that currently have at least one
     * contribution registered. Never null; empty when the registry holds no
     * contributions.
     */
    Set<String> getSources();
}
