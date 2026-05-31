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
}
