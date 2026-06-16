package io.nop.ai.agent.contribution;

import io.nop.ai.agent.engine.NopAiAgentException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe in-memory {@link IContributionRegistry} — the functional default
 * a deployer registers via {@code engine.setContributionRegistry(new InMemoryContributionRegistry())}
 * (design {@code nop-ai-agent-hook-skill-engine.md} §8.4, plan 217).
 *
 * <h2>Storage model</h2>
 * <ul>
 *   <li>Outer {@link ConcurrentHashMap} keyed by {@link ContributionType} →
 *       inner map. The outer map is lock-free for reads.</li>
 *   <li>Inner map: {@code LinkedHashMap} keyed by {@code id} (preserves
 *       registration order for stable same-priority sort). Inner-map
 *       compound operations (uniqueness check across sources / replace /
 *       unregister) are synchronized on the inner map instance — this is
 *       the lock granularity required because cross-source uniqueness
 *       spans all sources within a type.</li>
 *   <li>Separate {@code ConcurrentHashMap} keyed by {@code source} → set of
 *       (type, id) keys, used for whole-source {@link #unregisterSource}
 *       teardown. Kept in sync with the per-type inner maps under the same
 *       per-type lock when mutating the type's inner map.</li>
 * </ul>
 *
 * <h2>Identity rules (plan 217 裁定 2)</h2>
 * <ul>
 *   <li>Same {@code (type, id)} from the <em>same</em> source → replace
 *       (idempotent re-register, returns {@code true}).</li>
 *   <li>Same {@code (type, id)} from a <em>different</em> source →
 *       {@link NopAiAgentException} with type/id/source context (no silent
 *       overwrite across sources).</li>
 * </ul>
 *
 * <h2>Query semantics</h2>
 * {@link #getContributions(ContributionType)} and the source-filtered overload
 * return a new {@link ArrayList} snapshot sorted by ascending {@code priority}
 * (stable — same-priority contributions keep registration order). Returned
 * lists are never null.
 */
public class InMemoryContributionRegistry implements IContributionRegistry {

    /**
     * Comparator that sorts by ascending priority, preserving registration
     * order for equal priorities (stable sort — relies on LinkedHashMap
     * iteration order of the values view, which Collection.sort preserves
     * for equal elements).
     */
    private static final Comparator<Contribution> BY_PRIORITY = Comparator.comparingInt(Contribution::getPriority);

    /**
     * Outer map: type → (id → contribution). Inner maps are LinkedHashMap to
     * preserve registration order. Mutations on an inner map are guarded by
     * synchronizing on that inner map instance.
     */
    private final ConcurrentHashMap<ContributionType, Map<String, Contribution>> byType = new ConcurrentHashMap<>();

    /**
     * Reverse index: source → set of (type, id) keys, for whole-source
     * teardown. Each inner Set is guarded by the same lock as the affected
     * per-type inner map when in a compound operation.
     */
    private final ConcurrentHashMap<String, Set<TypeIdKey>> bySource = new ConcurrentHashMap<>();

    public InMemoryContributionRegistry() {
    }

    @Override
    public boolean register(Contribution contribution) {
        if (contribution == null) {
            throw new IllegalArgumentException("register: contribution must not be null");
        }
        Map<String, Contribution> inner = byType.computeIfAbsent(contribution.getType(), k -> new LinkedHashMap<>());

        synchronized (inner) {
            Contribution existing = inner.get(contribution.getId());
            if (existing != null && !existing.getSource().equals(contribution.getSource())) {
                throw new NopAiAgentException(
                        "Contribution register failed: cross-source (type, id) collision"
                                + " — type=" + contribution.getType()
                                + ", id=" + contribution.getId()
                                + ", existingSource=" + existing.getSource()
                                + ", newSource=" + contribution.getSource());
            }
            // Same-source re-register → replace; new key → add. Both are a
            // successful registration (return true).
            inner.put(contribution.getId(), contribution);

            // Keep reverse index in sync under the same per-type lock. The
            // bySource inner set is mutated only here and in unregisterSource
            // (which also acquires the matching per-type locks).
            bySource.computeIfAbsent(contribution.getSource(), k -> ConcurrentHashMap.newKeySet())
                    .add(new TypeIdKey(contribution.getType(), contribution.getId()));
        }
        return true;
    }

    @Override
    public void unregisterSource(String source) {
        if (source == null) {
            return;
        }
        Set<TypeIdKey> keys = bySource.remove(source);
        if (keys == null || keys.isEmpty()) {
            return;
        }
        // For each (type, id) recorded under this source, remove from the
        // matching per-type inner map under that map's own lock. We only
        // remove entries whose source matches (defensive — a same-(type,id)
        // re-register from a different source would have failed fast in
        // register, but we still guard against source mismatch).
        for (TypeIdKey key : keys) {
            Map<String, Contribution> inner = byType.get(key.type);
            if (inner == null) {
                continue;
            }
            synchronized (inner) {
                Contribution c = inner.get(key.id);
                if (c != null && source.equals(c.getSource())) {
                    inner.remove(key.id);
                }
            }
        }
    }

    @Override
    public List<Contribution> getContributions(ContributionType type) {
        if (type == null) {
            return Collections.emptyList();
        }
        Map<String, Contribution> inner = byType.get(type);
        if (inner == null || inner.isEmpty()) {
            return Collections.emptyList();
        }
        List<Contribution> snapshot;
        synchronized (inner) {
            snapshot = new ArrayList<>(inner.values());
        }
        snapshot.sort(BY_PRIORITY);
        return snapshot;
    }

    @Override
    public List<Contribution> getContributions(ContributionType type, String source) {
        if (type == null || source == null) {
            return Collections.emptyList();
        }
        Map<String, Contribution> inner = byType.get(type);
        if (inner == null || inner.isEmpty()) {
            return Collections.emptyList();
        }
        List<Contribution> snapshot;
        synchronized (inner) {
            snapshot = new ArrayList<>();
            for (Contribution c : inner.values()) {
                if (source.equals(c.getSource())) {
                    snapshot.add(c);
                }
            }
        }
        snapshot.sort(BY_PRIORITY);
        return snapshot;
    }

    @Override
    public Set<String> getSources() {
        Set<String> sources = bySource.keySet();
        if (sources.isEmpty()) {
            return Collections.emptySet();
        }
        // Return a snapshot so callers cannot mutate the live key set.
        return Collections.unmodifiableSet(new java.util.HashSet<>(sources));
    }

    /**
     * Compound key used by the reverse index {@link #bySource}.
     */
    private static final class TypeIdKey {
        final ContributionType type;
        final String id;

        TypeIdKey(ContributionType type, String id) {
            this.type = type;
            this.id = id;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof TypeIdKey)) return false;
            TypeIdKey that = (TypeIdKey) o;
            return type == that.type && java.util.Objects.equals(id, that.id);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(type, id);
        }
    }
}
