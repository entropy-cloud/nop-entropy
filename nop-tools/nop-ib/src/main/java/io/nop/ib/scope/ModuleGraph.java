package io.nop.ib.scope;

import io.nop.ib.model.IbModel;
import io.nop.ib.model.IbModule;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * First-level analysis (design 05 §6.1): Maven reactor graph coarse filter.
 * Edges: intra-project dependency edges of any scope (incl. {@code test-jar}),
 * and parent->child edges for pom change propagation. Missing model = no
 * pruning (caller must not ask this class).
 */
public final class ModuleGraph {
    private final IbModel model;
    /** module -> modules that depend on it (reverse dependency edges) */
    private final Map<String, Set<String>> dependents = new HashMap<>();
    /** module -> child modules (parent->child) */
    private final Map<String, Set<String>> children = new HashMap<>();

    public ModuleGraph(IbModel model) {
        this.model = model;
        Map<String, String> gaToModule = new HashMap<>();
        for (IbModule m : model.getModules())
            gaToModule.put(m.getGa(), m.getArtifactId());
        for (IbModule m : model.getModules()) {
            if (m.getParentGa() != null)
                children.computeIfAbsent(gaToModule.getOrDefault(m.getParentGa(), m.getParentGa()),
                        k -> new HashSet<>()).add(m.getArtifactId());
            for (IbModule.DepEdge dep : m.getDependencies()) {
                String target = gaToModule.get(dep.getGa());
                if (target != null)
                    dependents.computeIfAbsent(target, k -> new HashSet<>()).add(m.getArtifactId());
            }
        }
    }

    /**
     * Reverse dependency closure: all modules that (transitively) depend on any of
     * the seeds. Deterministic order (sorted) for stable plans and diffs.
     */
    public List<String> reverseClosure(Iterable<String> seedModules) {
        Set<String> result = new TreeSet<>();
        Deque<String> queue = new ArrayDeque<>();
        for (String seed : seedModules) {
            if (result.add(seed))
                queue.add(seed);
        }
        while (!queue.isEmpty()) {
            String current = queue.poll();
            for (String dependent : dependents.getOrDefault(current, Set.of())) {
                if (result.add(dependent))
                    queue.add(dependent);
            }
        }
        return new ArrayList<>(result);
    }

    /**
     * pom change propagation: reverse closure plus parent->child descendants of
     * every affected module (a parent pom change re-interprets every child).
     */
    public List<String> pomPropagation(Iterable<String> seedModules) {
        Set<String> result = new LinkedHashSet<>(reverseClosure(seedModules));
        Deque<String> queue = new ArrayDeque<>(result);
        while (!queue.isEmpty()) {
            String current = queue.poll();
            for (String child : children.getOrDefault(current, Set.of())) {
                if (result.add(child))
                    queue.add(child);
            }
        }
        return new ArrayList<>(result);
    }

    public Set<String> directDependents(String module) {
        return dependents.getOrDefault(module, Set.of());
    }
}
