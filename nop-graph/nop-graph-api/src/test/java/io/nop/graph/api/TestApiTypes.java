package io.nop.graph.api;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TestApiTypes {

    @Test
    void testEdgeConstruction() {
        Edge edge = new Edge("a", "b");
        assertEquals("a", edge.getSourceId());
        assertEquals("b", edge.getTargetId());
        assertEquals(1.0, edge.getWeight());
        assertNull(edge.getType());
        assertTrue(edge.getAttrs().isEmpty());

        Edge edge2 = new Edge("a", "b", 0.5, "CALLS");
        assertEquals(0.5, edge2.getWeight());
        assertEquals("CALLS", edge2.getType());
    }

    @Test
    void testEdgeAttrs() {
        Edge edge = new Edge("a", "b");
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("line", 42);
        edge.setAttrs(attrs);
        assertEquals(42, edge.getAttrs().get("line"));
    }

    @Test
    void testImpactResult() {
        List<ImpactedNode> upstream = Arrays.asList(
                new ImpactedNode("u1", 1),
                new ImpactedNode("u2", 2));
        List<ImpactedNode> downstream = Arrays.asList(
                new ImpactedNode("d1", 1));

        ImpactResult result = new ImpactResult("target", upstream, downstream, "medium", 2);

        assertEquals("target", result.getTargetNodeId());
        assertEquals(2, result.getUpstream().size());
        assertEquals(1, result.getDownstream().size());
        assertEquals(3, result.getTotalImpacted());
        assertEquals("medium", result.getRiskLevel());
        assertEquals(2, result.getMaxDepth());
    }

    @Test
    void testImpactConfig() {
        ImpactConfig config = ImpactConfig.create().setMaxDepth(5).setMaxNodes(50);
        assertEquals(5, config.getMaxDepth());
        assertEquals(50, config.getMaxNodes());
    }

    @Test
    void testCommunityResult() {
        Set<String> nodes = new HashSet<>(Arrays.asList("a", "b", "c"));
        CommunityInfo info = new CommunityInfo(0, nodes, 0.85);
        assertEquals(0, info.getId());
        assertEquals(3, info.getNodeCount());
        assertEquals(0.85, info.getCohesion());

        List<CommunityInfo> communities = Arrays.asList(info);
        CommunityResult result = new CommunityResult(communities, 3, 1, 0.85, 0.42, "LEIDEN", 1500);

        assertEquals(3, result.getTotalSymbols());
        assertEquals(1, result.getTotalCommunities());
        assertEquals(0.42, result.getModularity());
        assertEquals("LEIDEN", result.getAlgorithmUsed());
        assertEquals(1500, result.getProcessingTimeMs());
    }

    @Test
    void testGraphDiff() {
        Set<String> added = new HashSet<>(Arrays.asList("x", "y"));
        Set<String> removed = new HashSet<>();
        Set<Edge> addedEdges = new HashSet<>(Arrays.asList(new Edge("x", "y")));
        GraphDiff diff = new GraphDiff(added, removed, addedEdges, null, null);

        assertEquals(2, diff.getAddedNodes().size());
        assertTrue(diff.getRemovedNodes().isEmpty());
        assertEquals(1, diff.getAddedEdges().size());
        assertTrue(diff.getCommunityChanges().isEmpty());
        assertFalse(diff.isEmpty());
    }

    @Test
    void testBfsResult() {
        Map<String, Integer> depthMap = new HashMap<>();
        depthMap.put("a", 0);
        depthMap.put("b", 1);
        Set<String> reachable = new HashSet<>(Arrays.asList("a", "b"));
        BfsResult result = new BfsResult(reachable, depthMap);

        assertEquals(2, result.size());
        assertEquals(0, result.getDepth("a"));
        assertEquals(1, result.getDepth("b"));
        assertEquals(-1, result.getDepth("nonexistent"));
    }

    @Test
    void testPathQuery() {
        PathQuery query = PathQuery.create()
                .setEdgeType("CALLS")
                .setMaxHops(5)
                .setMinHops(1);
        assertEquals("CALLS", query.getEdgeType());
        assertEquals(5, query.getMaxHops());
        assertEquals(1, query.getMinHops());
    }

    @Test
    void testNodeScore() {
        NodeScore score = new NodeScore("node1", 0.95, 10, 5);
        assertEquals("node1", score.getNodeId());
        assertEquals(0.95, score.getScore());
        assertEquals(10, score.getInDegree());
        assertEquals(5, score.getOutDegree());
        assertEquals(15, score.getTotalDegree());
    }

    @Test
    void testCriticalNodeResult() {
        List<NodeScore> hubs = Arrays.asList(new NodeScore("hub", 0.9, 5, 3));
        List<NodeScore> bridges = Arrays.asList(new NodeScore("bridge", 0.8, 2, 2));
        CriticalNodeResult result = new CriticalNodeResult(10, 1, hubs, bridges);

        assertEquals(10, result.getTotalNodes());
        assertEquals(1, result.getTopN());
        assertEquals(1, result.getHubNodes().size());
        assertEquals(1, result.getBridgeNodes().size());
    }

    @Test
    void testCommunityChange() {
        CommunityChange change = new CommunityChange("node1", 0, 2);
        assertEquals("node1", change.getNodeId());
        assertEquals(0, change.getOldCommunity());
        assertEquals(2, change.getNewCommunity());
    }
}
