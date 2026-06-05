package io.nop.code.service;

import io.nop.code.core.util.BfsNode;
import io.nop.code.api.dto.DepEdgeDTO;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class TestBfsReverseTraversalDepth {

    @Test
    void testReverseBfsDepthGreaterThanOne() {
        Map<String, List<DepEdgeDTO>> adj = new HashMap<>();

        DepEdgeDTO ab = makeEdge("A", "B");
        DepEdgeDTO bc = makeEdge("B", "C");
        adj.put("B", List.of(ab));
        adj.put("C", List.of(bc));

        Set<String> visited = new HashSet<>();
        List<DepEdgeDTO> result = new ArrayList<>();
        bfsCollect("C", adj, 10, visited, result, DepEdgeDTO::getSource);

        assertEquals(2, result.size(), "Reverse BFS from C should find 2 edges (C->B, B->A)");
        Set<String> sources = new HashSet<>();
        for (DepEdgeDTO e : result) {
            sources.add(e.getSource());
        }
        assertTrue(sources.contains("B"), "Edge B->C should be found");
        assertTrue(sources.contains("A"), "Edge A->B should be found at depth 2");
    }

    @Test
    void testForwardBfsDepthGreaterThanOne() {
        Map<String, List<DepEdgeDTO>> adj = new HashMap<>();

        DepEdgeDTO ab = makeEdge("A", "B");
        DepEdgeDTO bc = makeEdge("B", "C");
        adj.put("A", List.of(ab));
        adj.put("B", List.of(bc));

        Set<String> visited = new HashSet<>();
        List<DepEdgeDTO> result = new ArrayList<>();
        bfsCollect("A", adj, 10, visited, result, DepEdgeDTO::getTarget);

        assertEquals(2, result.size(), "Forward BFS from A should find 2 edges (A->B, B->C)");
        Set<String> targets = new HashSet<>();
        for (DepEdgeDTO e : result) {
            targets.add(e.getTarget());
        }
        assertTrue(targets.contains("B"), "Edge A->B should be found");
        assertTrue(targets.contains("C"), "Edge B->C should be found at depth 2");
    }

    @Test
    void testReverseBfsRespectsMaxDepth() {
        Map<String, List<DepEdgeDTO>> adj = new HashMap<>();

        DepEdgeDTO ab = makeEdge("A", "B");
        DepEdgeDTO bc = makeEdge("B", "C");
        DepEdgeDTO cd = makeEdge("C", "D");
        adj.put("B", List.of(ab));
        adj.put("C", List.of(bc));
        adj.put("D", List.of(cd));

        Set<String> visited = new HashSet<>();
        List<DepEdgeDTO> result = new ArrayList<>();
        bfsCollect("D", adj, 2, visited, result, DepEdgeDTO::getSource);

        assertEquals(2, result.size(), "Reverse BFS from D with maxDepth=2 should find 2 edges");
        assertEquals("C", result.get(0).getSource(), "First edge should be C->D");
        assertEquals("B", result.get(1).getSource(), "Second edge should be B->C");
    }

    @Test
    void testReverseBfsThreeLevelChain() {
        Map<String, List<DepEdgeDTO>> adj = new HashMap<>();

        adj.put("B", List.of(makeEdge("A", "B")));
        adj.put("C", List.of(makeEdge("B", "C")));
        adj.put("D", List.of(makeEdge("C", "D")));
        adj.put("E", List.of(makeEdge("D", "E")));

        Set<String> visited = new HashSet<>();
        List<DepEdgeDTO> result = new ArrayList<>();
        bfsCollect("E", adj, 10, visited, result, DepEdgeDTO::getSource);

        assertEquals(4, result.size(), "Reverse BFS from E should traverse entire A->B->C->D->E chain");

        List<String> sources = result.stream().map(DepEdgeDTO::getSource).collect(Collectors.toList());
        assertEquals(List.of("D", "C", "B", "A"), sources,
                "Reverse BFS should follow edge sources back to root");
    }

    private DepEdgeDTO makeEdge(String source, String target) {
        DepEdgeDTO edge = new DepEdgeDTO();
        edge.setSource(source);
        edge.setTarget(target);
        return edge;
    }

    private void bfsCollect(String start, Map<String, List<DepEdgeDTO>> adj, int maxDepth,
                            Set<String> visited, List<DepEdgeDTO> result,
                            Function<DepEdgeDTO, String> nextNodeFn) {
        Queue<BfsNode> queue = new LinkedList<>();
        queue.add(new BfsNode(start, 0));
        visited.add(start);
        while (!queue.isEmpty()) {
            BfsNode current = queue.poll();
            if (current.depth() >= maxDepth) continue;
            List<DepEdgeDTO> edges = adj.getOrDefault(current.nodeId(), Collections.emptyList());
            for (DepEdgeDTO edge : edges) {
                result.add(edge);
                String nextNode = nextNodeFn.apply(edge);
                if (!visited.contains(nextNode)) {
                    visited.add(nextNode);
                    queue.add(new BfsNode(nextNode, current.depth() + 1));
                }
            }
        }
    }
}
