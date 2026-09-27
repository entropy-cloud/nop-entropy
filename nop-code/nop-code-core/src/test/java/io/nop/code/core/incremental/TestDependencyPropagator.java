package io.nop.code.core.incremental;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.function.Function;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TestDependencyPropagator {

    /** adjacency: bidirectional caller/callee surface */
    private static Function<Set<String>, List<String>> hopQuery(Map<String, Set<String>> adj) {
        return ids -> {
            Set<String> result = new HashSet<>();
            for (String id : ids) {
                for (String neighbour : adj.getOrDefault(id, Collections.emptySet())) {
                    result.add(neighbour);
                    result.add(id); // bidirectional surface includes the queried node itself
                }
            }
            return new ArrayList<>(result);
        };
    }

    private static Map<String, Set<String>> undirected(String... pairs) {
        Map<String, Set<String>> adj = new HashMap<>();
        for (String pair : pairs) {
            String[] parts = pair.split("->");
            adj.computeIfAbsent(parts[0], k -> new HashSet<>()).add(parts[1]);
            adj.computeIfAbsent(parts[1], k -> new HashSet<>()).add(parts[0]);
        }
        return adj;
    }

    @Test
    void testOneHop() {
        Map<String, Set<String>> adj = undirected("a->b", "b->c");
        Set<String> reached = DependencyPropagator.propagate(
                new HashSet<>(List.of("a")), hopQuery(adj), 1);
        // 1 hop from a reaches b only (a excluded as seed)
        assertTrue(reached.contains("b"));
        assertFalse(reached.contains("c"));
    }

    @Test
    void testTwoHopChain() {
        Map<String, Set<String>> adj = undirected("a->b", "b->c");
        Set<String> reached = DependencyPropagator.propagate(
                new HashSet<>(List.of("a")), hopQuery(adj), 2);
        assertEquals(new HashSet<>(List.of("b", "c")), reached);
    }

    @Test
    void testCycleProtection() {
        // a<->b cycle: 10 hops must terminate and not loop infinitely
        Map<String, Set<String>> adj = undirected("a->b", "b->a");
        Set<String> reached = DependencyPropagator.propagate(
                new HashSet<>(List.of("a")), hopQuery(adj), 10);
        assertEquals(new HashSet<>(List.of("b")), reached);
    }

    @Test
    void testDedupAndSortedOutput() {
        // b reachable via two paths; d also
        Map<String, Set<String>> adj = undirected("a->b", "x->b", "b->d", "x->d");
        Set<String> reached = DependencyPropagator.propagate(
                new HashSet<>(List.of("a", "x")), hopQuery(adj), 2);
        // sorted TreeSet output
        List<String> sorted = new ArrayList<>(reached);
        assertEquals(sorted, List.of("b", "d"));
    }

    @Test
    void testEmptySeeds() {
        assertTrue(DependencyPropagator.propagate(
                Collections.emptySet(), hopQuery(undirected("a->b")), 2).isEmpty());
    }

    @Test
    void testZeroHops() {
        assertTrue(DependencyPropagator.propagate(
                new HashSet<>(List.of("a")), hopQuery(undirected("a->b")), 0).isEmpty());
    }
}
