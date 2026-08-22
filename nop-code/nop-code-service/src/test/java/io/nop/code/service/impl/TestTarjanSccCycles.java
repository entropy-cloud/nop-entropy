package io.nop.code.service.impl;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Regression tests for the iterative Tarjan SCC in CodeGraphService (audit P0).
 *
 * <p>The buggy implementation skipped lowLink propagation from a completed child subtree whenever
 * the parent frame still had unvisited neighbors (only the last child was merged), and frames that
 * never pushed a child (leaves, nodes whose neighbors were all visited) never popped their SCC,
 * leaving nodes stuck on the Tarjan stack to be swallowed by an ancestor's SCC pop. Expected values
 * below follow standard Tarjan SCC semantics (each node appears in exactly one SCC; an SCC has size
 * &gt; 1 iff it lies on a cycle).
 */
class TestTarjanSccCycles {

    @SuppressWarnings("unchecked")
    private List<List<String>> tarjanSCC(Map<String, List<String>> adj) throws Exception {
        CodeGraphService service = new CodeGraphService(null, new CodeCacheManager());
        Method m = CodeGraphService.class.getDeclaredMethod("tarjanSCC", Map.class);
        m.setAccessible(true);
        return (List<List<String>>) m.invoke(service, adj);
    }

    private static Map<String, List<String>> graph(String[][] edges) {
        Map<String, List<String>> adj = new LinkedHashMap<>();
        for (String[] e : edges) {
            adj.computeIfAbsent(e[0], k -> new ArrayList<>()).add(e[1]);
            adj.putIfAbsent(e[1], new ArrayList<>());
        }
        return adj;
    }

    private static Set<Set<String>> asSets(List<List<String>> sccs) {
        return sccs.stream().map(s -> Set.copyOf(s)).collect(Collectors.toSet());
    }

    @Test
    void dagChainYieldsOnlySingletonSCCs() throws Exception {
        // pure DAG A -> B -> C: every SCC must be a singleton, no false cycle may be reported.
        // Buggy code merged leaf C into its parent's SCC ({B,C}) because C's frame never popped.
        List<List<String>> sccs = tarjanSCC(graph(new String[][]{
                {"A", "B"}, {"B", "C"}}));
        assertEquals(Set.of(Set.of("A"), Set.of("B"), Set.of("C")), asSets(sccs));
    }

    @Test
    void parentWithTwoChildrenAndBackEdge() throws Exception {
        // audit counterexample: S -> P, P -> C1, P -> C2, C1 -> S.
        // Correct SCCs: cycle {S,P,C1} and singleton {C2}; C2 must not be reported as a cycle
        // member. Buggy code returned {C2,C1,P} and {S}.
        List<List<String>> sccs = tarjanSCC(graph(new String[][]{
                {"S", "P"}, {"P", "C1"}, {"P", "C2"}, {"C1", "S"}}));
        assertEquals(Set.of(Set.of("S", "P", "C1"), Set.of("C2")), asSets(sccs));
    }

    @Test
    void cycleWithTailNodeKeepsTailOutOfCycleSCC() throws Exception {
        // A -> B, B <-> C, C -> D: SCCs are {B,C}, {A}, {D}.
        // Buggy code absorbed tail D into the cycle SCC ({D,C,B}).
        List<List<String>> sccs = tarjanSCC(graph(new String[][]{
                {"A", "B"}, {"B", "C"}, {"C", "B"}, {"C", "D"}}));
        assertEquals(Set.of(Set.of("B", "C"), Set.of("A"), Set.of("D")), asSets(sccs));
    }

    @Test
    void selfLoopAndIsolatedNodeEachFormOwnSCC() throws Exception {
        // a self edge (A) and an isolated node (B): both frames never push a child, so the buggy
        // implementation returned no SCCs at all and leaked both nodes on the stack.
        Map<String, List<String>> adj = new LinkedHashMap<>();
        adj.put("A", List.of("A"));
        adj.put("B", List.of());
        List<List<String>> sccs = tarjanSCC(adj);
        assertEquals(Set.of(Set.of("A"), Set.of("B")), asSets(sccs));
    }

    @Test
    void twoDisjointCyclesBehindBranchingParent() throws Exception {
        // S -> P, P -> {C1, C2}, C1 -> S (cycle {S,P,C1}) and C2 <-> Q (cycle {C2,Q}).
        // Buggy code split the first cycle into {C1,P} and {S} (a false {C1,P} "cycle").
        List<List<String>> sccs = tarjanSCC(graph(new String[][]{
                {"S", "P"}, {"P", "C1"}, {"P", "C2"}, {"C1", "S"}, {"C2", "Q"}, {"Q", "C2"}}));
        assertEquals(Set.of(Set.of("S", "P", "C1"), Set.of("C2", "Q")), asSets(sccs));
    }

    @Test
    void twoCyclesSharingOneNodeFormSingleSCC() throws Exception {
        // A -> B -> C -> A and C -> D -> E -> C share node C: everything is mutually reachable,
        // so the whole graph is one SCC {A,B,C,D,E}.
        List<List<String>> sccs = tarjanSCC(graph(new String[][]{
                {"A", "B"}, {"B", "C"}, {"C", "A"}, {"C", "D"}, {"D", "E"}, {"E", "C"}}));
        assertEquals(Set.of(Set.of("A", "B", "C", "D", "E")), asSets(sccs));
    }
}
