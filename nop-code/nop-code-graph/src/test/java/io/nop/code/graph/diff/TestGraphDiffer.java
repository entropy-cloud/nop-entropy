package io.nop.code.graph.diff;

import io.nop.code.core.graph.CallGraph;
import io.nop.code.core.graph.SymbolTable;
import io.nop.code.graph.community.CommunityDetector;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TestGraphDiffer {

    @Test
    void testConstructionAndBuildSnapshot() {
        GraphDiffer differ = new GraphDiffer();
        assertNotNull(differ);

        CallGraph callGraph = new CallGraph();
        SymbolTable symbolTable = new SymbolTable();
        CommunityDetector.CommunityDetectionResult communities =
                new CommunityDetector.CommunityDetectionResult();
        GraphSnapshot snapshot = GraphDiffer.buildSnapshot(callGraph, communities);
        assertNotNull(snapshot);
        assertNotNull(snapshot.getNodes());
        assertNotNull(snapshot.getEdges());
    }

    @Test
    void testDiffDetectsAddedNodes() {
        GraphDiffer differ = new GraphDiffer();
        GraphSnapshot baseline = new GraphSnapshot(
                new HashSet<>(), new HashSet<>(), Collections.emptyMap());

        Set<String> targetNodes = new HashSet<>();
        targetNodes.add("A");
        GraphSnapshot target = new GraphSnapshot(
                targetNodes, new HashSet<>(), Collections.emptyMap());

        GraphDiff diff = differ.diff(baseline, target);
        assertTrue(diff.getAddedNodes().contains("A"));
        assertTrue(diff.getRemovedNodes().isEmpty());
    }

    @Test
    void testDiffDetectsRemovedNodes() {
        GraphDiffer differ = new GraphDiffer();
        Set<String> baselineNodes = new HashSet<>();
        baselineNodes.add("A");
        GraphSnapshot baseline = new GraphSnapshot(
                baselineNodes, new HashSet<>(), Collections.emptyMap());

        GraphSnapshot target = new GraphSnapshot(
                new HashSet<>(), new HashSet<>(), Collections.emptyMap());

        GraphDiff diff = differ.diff(baseline, target);
        assertTrue(diff.getRemovedNodes().contains("A"));
        assertTrue(diff.getAddedNodes().isEmpty());
    }

    @Test
    void testGetAddedEdges_returnsCorrectAddedEdges() {
        GraphDiffer differ = new GraphDiffer();

        // Baseline: A -> B
        Set<GraphSnapshot.EdgeKey> baselineEdges = new HashSet<>();
        baselineEdges.add(new GraphSnapshot.EdgeKey("A", "B"));
        GraphSnapshot baseline = new GraphSnapshot(
                new HashSet<>(), baselineEdges, Collections.emptyMap());

        // Target: A -> B, A -> C (added), B -> C (added)
        Set<GraphSnapshot.EdgeKey> targetEdges = new HashSet<>();
        targetEdges.add(new GraphSnapshot.EdgeKey("A", "B"));
        targetEdges.add(new GraphSnapshot.EdgeKey("A", "C"));
        targetEdges.add(new GraphSnapshot.EdgeKey("B", "C"));
        GraphSnapshot target = new GraphSnapshot(
                new HashSet<>(), targetEdges, Collections.emptyMap());

        GraphDiff diff = differ.diff(baseline, target);
        assertEquals(2, diff.getAddedEdges().size());
        assertTrue(diff.getAddedEdges().contains(new GraphSnapshot.EdgeKey("A", "C")));
        assertTrue(diff.getAddedEdges().contains(new GraphSnapshot.EdgeKey("B", "C")));
        assertTrue(diff.getRemovedEdges().isEmpty());
    }

    @Test
    void testGetRemovedEdges_returnsCorrectRemovedEdges() {
        GraphDiffer differ = new GraphDiffer();

        // Baseline: A -> B, A -> C, B -> C
        Set<GraphSnapshot.EdgeKey> baselineEdges = new HashSet<>();
        baselineEdges.add(new GraphSnapshot.EdgeKey("A", "B"));
        baselineEdges.add(new GraphSnapshot.EdgeKey("A", "C"));
        baselineEdges.add(new GraphSnapshot.EdgeKey("B", "C"));
        GraphSnapshot baseline = new GraphSnapshot(
                new HashSet<>(), baselineEdges, Collections.emptyMap());

        // Target: A -> B (only common edge)
        Set<GraphSnapshot.EdgeKey> targetEdges = new HashSet<>();
        targetEdges.add(new GraphSnapshot.EdgeKey("A", "B"));
        GraphSnapshot target = new GraphSnapshot(
                new HashSet<>(), targetEdges, Collections.emptyMap());

        GraphDiff diff = differ.diff(baseline, target);
        assertEquals(2, diff.getRemovedEdges().size());
        assertTrue(diff.getRemovedEdges().contains(new GraphSnapshot.EdgeKey("A", "C")));
        assertTrue(diff.getRemovedEdges().contains(new GraphSnapshot.EdgeKey("B", "C")));
        assertTrue(diff.getAddedEdges().isEmpty());
    }

    @Test
    void testDiffIdenticalSnapshots_noChanges() {
        GraphDiffer differ = new GraphDiffer();

        Set<GraphSnapshot.EdgeKey> edges = new HashSet<>();
        edges.add(new GraphSnapshot.EdgeKey("A", "B"));
        edges.add(new GraphSnapshot.EdgeKey("B", "C"));

        GraphSnapshot baseline = new GraphSnapshot(
                new HashSet<>(), edges, Collections.emptyMap());
        GraphSnapshot target = new GraphSnapshot(
                new HashSet<>(), new HashSet<>(edges), Collections.emptyMap());

        GraphDiff diff = differ.diff(baseline, target);
        assertTrue(diff.getAddedEdges().isEmpty());
        assertTrue(diff.getRemovedEdges().isEmpty());
    }

    @Test
    void testDiffMixedAddedAndRemovedEdges() {
        GraphDiffer differ = new GraphDiffer();

        Set<GraphSnapshot.EdgeKey> baselineEdges = new HashSet<>();
        baselineEdges.add(new GraphSnapshot.EdgeKey("A", "B"));
        baselineEdges.add(new GraphSnapshot.EdgeKey("X", "Y"));

        Set<GraphSnapshot.EdgeKey> targetEdges = new HashSet<>();
        targetEdges.add(new GraphSnapshot.EdgeKey("A", "B"));
        targetEdges.add(new GraphSnapshot.EdgeKey("C", "D"));

        GraphSnapshot baseline = new GraphSnapshot(
                new HashSet<>(), baselineEdges, Collections.emptyMap());
        GraphSnapshot target = new GraphSnapshot(
                new HashSet<>(), targetEdges, Collections.emptyMap());

        GraphDiff diff = differ.diff(baseline, target);
        assertEquals(1, diff.getAddedEdges().size());
        assertTrue(diff.getAddedEdges().contains(new GraphSnapshot.EdgeKey("C", "D")));
        assertEquals(1, diff.getRemovedEdges().size());
        assertTrue(diff.getRemovedEdges().contains(new GraphSnapshot.EdgeKey("X", "Y")));
    }

    @Test
    void testBuildSnapshot_capturesEdgesFromCallGraph() {
        CallGraph callGraph = new CallGraph();
        callGraph.addEdge("A", "B");
        callGraph.addEdge("B", "C");

        CommunityDetector.CommunityDetectionResult communities =
                new CommunityDetector.CommunityDetectionResult();
        GraphSnapshot snapshot = GraphDiffer.buildSnapshot(callGraph, communities);

        assertEquals(3, snapshot.getNodes().size());
        assertTrue(snapshot.getNodes().contains("A"));
        assertTrue(snapshot.getNodes().contains("B"));
        assertTrue(snapshot.getNodes().contains("C"));
        assertEquals(2, snapshot.getEdges().size());
        assertTrue(snapshot.getEdges().contains(new GraphSnapshot.EdgeKey("A", "B")));
        assertTrue(snapshot.getEdges().contains(new GraphSnapshot.EdgeKey("B", "C")));
    }
}
