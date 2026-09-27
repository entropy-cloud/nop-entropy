package io.nop.code.core.incremental;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.Collection;
import java.util.function.Function;

/**
 * Propagates change impact from seed symbols along dependency edges (N3.1).
 *
 * hopQuery maps a set of symbol ids to their direct neighbours (caller AND callee sides —
 * the affected-symbol surface). The propagator runs a BFS for the given number of hops with
 * visited-set cycle protection and returns the sorted union of reached symbol ids (excluding
 * seeds themselves).
 *
 * Pure computation: edge access is injected so the caller can use indexed point queries
 * inside the incremental transaction (no full graph load).
 */
public class DependencyPropagator {

    public static Set<String> propagate(Set<String> seedSymbolIds,
                                        Function<Set<String>, ? extends Collection<String>> hopQuery,
                                        int hops) {
        if (seedSymbolIds == null || seedSymbolIds.isEmpty() || hops <= 0) {
            return new TreeSet<>();
        }
        Set<String> visited = new HashSet<>(seedSymbolIds);
        Set<String> frontier = new HashSet<>(seedSymbolIds);
        Set<String> reached = new TreeSet<>();

        for (int hop = 0; hop < hops; hop++) {
            if (frontier.isEmpty()) {
                break;
            }
            java.util.Collection<String> neighbours = hopQuery.apply(frontier);
            Set<String> nextFrontier = new HashSet<>();
            for (String neighbour : neighbours) {
                if (neighbour != null && visited.add(neighbour)) {
                    reached.add(neighbour);
                    nextFrontier.add(neighbour);
                }
            }
            frontier = nextFrontier;
        }
        return reached;
    }
}
